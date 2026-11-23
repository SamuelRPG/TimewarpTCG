// PUNTO DE ENTRADA. No hace falta crear ninguna escena ni arrastrar nada:
// al pulsar Play (en cualquier escena) este script se crea solo y construye toda la interfaz por código.
using System;
using System.Collections.Generic;
using UnityEngine;
using UnityEngine.EventSystems;
using UnityEngine.UI;

namespace TimewarpTCG
{
    public sealed class TimewarpApp : MonoBehaviour
    {
        enum AppScreen { Menu, Connecting, Game }

        static TimewarpApp _instance;

        // Para desactivar el arranque automático: Project Settings > Player > Scripting Define Symbols > añade TIMEWARP_NO_AUTOSTART
#if !TIMEWARP_NO_AUTOSTART
        [RuntimeInitializeOnLoadMethod(RuntimeInitializeLoadType.AfterSceneLoad)]
#endif
        static void Boot()
        {
            if (_instance != null) return;
            var go = new GameObject("TimewarpApp");
            DontDestroyOnLoad(go);
            _instance = go.AddComponent<TimewarpApp>();
        }

        AppScreen _screen = AppScreen.Menu;
        Canvas _canvas;
        MenuView _menu;
        GameView _game;
        HelpView _help;
        DeckBuilderView _builder;
        GameConnection _conn;
        string _deckInUse, _botDeckInUse = "RANDOM";
        float _connectDeadline;

        // sesión (cuenta) y datos de la base de datos
        string _userId, _username, _sessionHost;
        List<DeckDto> _decks = new List<DeckDto>();

        // ------------------------------------------------------------ inicio
        void Awake()
        {
            Application.runInBackground = true;      // que la conexión siga viva si el Editor pierde el foco
            Application.targetFrameRate = 60;

            try
            {
                EnsureCamera();
                BuildCanvas();
                EnsureEventSystem();

                _menu = new MenuView(_canvas.transform);
                _game = new GameView(_canvas.transform);

                // Pantallas SECUNDARIAS: si una falla al construirse, se registra el error pero el juego sigue funcionando
                try
                {
                    _builder = new DeckBuilderView(_canvas.transform);
                    _builder.OnClose = decks =>
                    {
                        _decks = decks ?? _decks;
                        _menu.SetDecks(_decks);
                        _menu.Show(true);
                    };
                }
                catch (Exception e)
                {
                    _builder = null;
                    Debug.LogError("[Timewarp] El editor de mazos no se pudo construir (el resto del juego funciona):\n" + e);
                }
                _help = new HelpView(_canvas.transform);

                _menu.OnLogin = name => { var _ = LoginAsync(name, true); };
                _menu.OnEditDecks = () => { var _ = EditDecksAsync(); };
                _menu.OnPlay = OnPlayClicked;
                _menu.OnHelp = () => _help.Show(true);
                _game.OnHelp = () => _help.Show(true);
                _game.OnExitToMenu = ShowMenu;
                _game.OnRematch = Rematch;

                ShowMenu();
            }
            catch (Exception e)
            {
                Debug.LogError("[Timewarp] Error construyendo la interfaz. Copia este mensaje y el de la consola para depurarlo:\n" + e);
                enabled = false;
            }
        }

        static void EnsureCamera()
        {
            if (Camera.allCamerasCount > 0) return;
            var go = new GameObject("TimewarpCamera");
            var cam = go.AddComponent<Camera>();
            cam.clearFlags = CameraClearFlags.SolidColor;
            cam.backgroundColor = UiKit.Bg;
            cam.orthographic = true;
            cam.cullingMask = 0;
        }

        void BuildCanvas()
        {
            var go = new GameObject("TimewarpCanvas", typeof(RectTransform), typeof(Canvas), typeof(CanvasScaler), typeof(GraphicRaycaster));
            go.layer = 5;
            go.transform.SetParent(transform, false);
            _canvas = go.GetComponent<Canvas>();
            _canvas.renderMode = RenderMode.ScreenSpaceOverlay;
            _canvas.sortingOrder = 100;
            var scaler = go.GetComponent<CanvasScaler>();
            scaler.uiScaleMode = CanvasScaler.ScaleMode.ScaleWithScreenSize;
            scaler.referenceResolution = new Vector2(1920, 1080);
            scaler.screenMatchMode = CanvasScaler.ScreenMatchMode.MatchWidthOrHeight;
            scaler.matchWidthOrHeight = 0.5f;
        }

        static void EnsureEventSystem()
        {
            if (EventSystem.current != null) return;
            var go = new GameObject("EventSystem");
            go.AddComponent<EventSystem>();
            AddInputModule(go);
        }

        // Unity 6 crea los proyectos con el "Input System" nuevo; los anteriores con el clásico.
        // Elegimos el módulo de UI adecuado sin depender en compilación de ningún paquete.
        static void AddInputModule(GameObject go)
        {
#if ENABLE_INPUT_SYSTEM && !ENABLE_LEGACY_INPUT_MANAGER
            var t = Type.GetType("UnityEngine.InputSystem.UI.InputSystemUIInputModule, Unity.InputSystem");
            if (t != null)
            {
                var module = go.AddComponent(t);
                var assign = t.GetMethod("AssignDefaultActions");
                if (assign != null) assign.Invoke(module, null);
                return;
            }
            Debug.LogWarning("[Timewarp] Tu proyecto usa solo el Input System nuevo pero no se encontró su módulo de UI. " +
                             "Instala el paquete 'Input System' o pon Edit > Project Settings > Player > Active Input Handling = Both.");
#endif
            go.AddComponent<StandaloneInputModule>();
        }

        // ------------------------------------------------------------ bucle
        void Update()
        {
            if (_conn != null) _conn.Pump();
            if (_screen == AppScreen.Game) _game.Tick();
            if (_builder != null) _builder.Tick();
            if (_screen == AppScreen.Connecting && Time.unscaledTime > _connectDeadline)
                FailConnect("El servidor no respondió a la petición de partida. ¿Está arrancado el backend?");
        }

        void OnDestroy() { CloseConnection(); }
        void OnApplicationQuit() { CloseConnection(); }

        // ------------------------------------------------------------ flujo
        void ShowMenu()
        {
            CloseConnection();
            _game.Show(false);
            _game.Reset();
            _menu.Show(true);
            _menu.SetBusy(false);
            _screen = AppScreen.Menu;
        }

        // ------------------------------------------------------------ cuenta y mazos
        bool HasSession(string host) { return _userId != null && _sessionHost == host && _username == _menu.Username; }

        /// <summary>Entra (o se registra) con el nombre escrito y carga sus mazos guardados. Devuelve true si hay sesión.</summary>
        async System.Threading.Tasks.Task<bool> LoginAsync(string name, bool announce)
        {
            string host = _menu.Host;
            if (string.IsNullOrWhiteSpace(name))
            {
                _userId = null; _username = null; _decks = new List<DeckDto>();
                _menu.SetSession(null); _menu.SetDecks(_decks);
                if (announce) _menu.SetStatus("Escribe un nombre para entrar. Sin nombre juegas como invitado (solo mazos AGGRO / CONTROL).", false);
                return false;
            }
            _menu.SetBusy(true);
            if (announce) _menu.SetStatus("Entrando como «" + name.Trim() + "»…", false);
            try
            {
                var u = await RestApi.LoginOrRegisterAsync(host, name.Trim());
                _userId = u.id; _username = u.username; _sessionHost = host;
                _decks = await RestApi.GetDecksAsync(host, u.id);
                _menu.SetSession(u.username);
                _menu.SetDecks(_decks);
                _menu.Remember();
                if (announce) _menu.SetStatus("Sesión iniciada. Tienes " + _decks.Count + " mazo(s) guardado(s).", false);
                _menu.SetBusy(false);
                return true;
            }
            catch (Exception e)
            {
                _userId = null; _username = null;
                _menu.SetSession(null);
                _menu.SetStatus("No se pudo iniciar sesión: " + e.Message, true);
                _menu.SetBusy(false);
                return false;
            }
        }

        async System.Threading.Tasks.Task EditDecksAsync()
        {
            if (_screen != AppScreen.Menu) return;
            string host = _menu.Host;
            if (_builder == null) { _menu.SetStatus("El editor de mazos no está disponible (mira la consola de Unity).", true); return; }
            if (!HasSession(host))
            {
                if (!await LoginAsync(_menu.Username, false))
                {
                    if (string.IsNullOrWhiteSpace(_menu.Username)) _menu.SetStatus("Para crear mazos escribe tu nombre arriba y pulsa ENTRAR.", true);
                    return;
                }
            }
            _menu.SetBusy(true);
            _menu.SetStatus("Cargando el catálogo de cartas…", false);
            try
            {
                var catalog = await RestApi.GetCardsAsync(host);
                _decks = await RestApi.GetDecksAsync(host, _userId);
                _menu.SetStatus("", false);
                _menu.SetBusy(false);
                _menu.Show(false);
                _builder.Open(host, _userId, _username, catalog, _decks, _menu.IsBuiltinDeck ? null : _menu.SelectedDeck);
            }
            catch (Exception e)
            {
                _menu.SetBusy(false);
                _menu.SetStatus("No se pudo cargar el catálogo: " + e.Message, true);
            }
        }

        async void OnPlayClicked()
        {
            if (_screen != AppScreen.Menu) return;
            _menu.Remember();
            string host = _menu.Host;
            string deck = _menu.SelectedDeck;
            string botDeck = _menu.BotDeck;

            // si has escrito un nombre y aún no hay sesión, entramos; los mazos propios exigen sesión
            if (_menu.Username.Length > 0 && !HasSession(host)) await LoginAsync(_menu.Username, false);
            if (!_menu.IsBuiltinDeck && !HasSession(host))
            {
                _menu.SetStatus("Para jugar con «" + deck + "» necesitas iniciar sesión con tu nombre (pulsa ENTRAR).", true);
                return;
            }

            _menu.SetBusy(true);
            _menu.SetStatus("Conectando con " + host + "…", false);
            _screen = AppScreen.Connecting;
            _connectDeadline = Time.unscaledTime + 30f;
            string playerId = HasSession(host) ? _userId : MenuView.GuestId();

            try
            {
                CloseConnection();
                _conn = new GameConnection();
                _conn.StateReceived += OnState;
                _conn.ErrorReceived += OnError;
                _conn.Disconnected += OnDisconnected;
                _conn.Log += m => Debug.Log("[Timewarp] " + m);
                _game.Conn = _conn;

                await _conn.ConnectAsync(host, playerId);
                _deckInUse = deck; _botDeckInUse = botDeck;
                _menu.SetStatus("Creando partida…", false);
                _connectDeadline = Time.unscaledTime + 12f;
                _conn.StartLocalMatch(deck, botDeck);
            }
            catch (Exception e)
            {
                FailConnect("No se pudo iniciar la partida con " + host + ".\n" + e.Message);
            }
        }

        void Rematch()
        {
            if (_conn == null || !_conn.IsConnected) { ShowMenu(); return; }
            _game.Reset();
            _conn.StartLocalMatch(_deckInUse, _botDeckInUse);
        }

        void FailConnect(string message)
        {
            CloseConnection();
            _menu.Show(true);
            _game.Show(false);
            _menu.SetBusy(false);
            _menu.SetStatus(message, true);
            _screen = AppScreen.Menu;
        }

        void CloseConnection()
        {
            if (_conn == null) return;
            _conn.StateReceived -= OnState;
            _conn.ErrorReceived -= OnError;
            _conn.Disconnected -= OnDisconnected;
            _conn.Dispose();
            _conn = null;
            _game.Conn = null;
        }

        // ------------------------------------------------------------ eventos de red (siempre en el hilo principal)
        void OnState(GameStateDto s)
        {
            if (_screen == AppScreen.Connecting)
            {
                _menu.Show(false);
                _game.Reset();
                _game.Show(true);
                _screen = AppScreen.Game;
            }
            if (_screen == AppScreen.Game) _game.OnState(s);
        }

        void OnError(string message)
        {
            if (_screen == AppScreen.Connecting) FailConnect("El servidor rechazó la petición de partida: " + message);
            else if (_screen == AppScreen.Game) _game.OnError(message);
        }

        void OnDisconnected(string reason)
        {
            if (_screen == AppScreen.Game) _game.ShowDisconnected(reason + "\nVuelve al menú y reconecta (la partida en curso se pierde).");
            else if (_screen == AppScreen.Connecting) FailConnect(reason);
        }
    }
}
