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
        bool _online;                       // la partida en curso (o la búsqueda) es contra otra persona
        string _opponentName;
        string _deckInUse, _botDeckInUse = "RANDOM";
        float _connectDeadline;

        // sesión (cuenta) y datos de la base de datos
        string _userId, _username, _token, _sessionHost;
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

                _menu.OnLogin = (name, pass) => { var _ = LoginAsync(name, pass, false); };
                _menu.OnRegister = (name, pass) => { var _ = LoginAsync(name, pass, true); };
                _menu.OnEditDecks = () => { var _ = EditDecksAsync(); };
                _menu.OnPlay = OnPlayClicked;
                _menu.OnOnline = OnOnlineClicked;
                _menu.OnCancelSearch = CancelSearch;
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
            if (EventSystem.current == null)
            {
                var go = new GameObject("EventSystem");
                go.AddComponent<EventSystem>();
                AddInputModule(go);
            }
            MakeTextInputSafe();
        }

        /// <summary>
        /// Con SOLO el Input System nuevo, el InputField clásico lanza una excepción al consultar la API de entrada antigua y
        /// deja de aceptar texto (ver SafeBaseInput). Se corrige en el módulo que haya, sea el nuestro o uno que ya existiera en la escena.
        /// </summary>
        static void MakeTextInputSafe()
        {
#if ENABLE_INPUT_SYSTEM && !ENABLE_LEGACY_INPUT_MANAGER
            try
            {
                var module = EventSystem.current != null ? EventSystem.current.GetComponent<BaseInputModule>() : null;
                if (module == null) return;
                var field = typeof(BaseInputModule).GetField("m_InputOverride", System.Reflection.BindingFlags.Instance | System.Reflection.BindingFlags.NonPublic);
                if (field == null) { Debug.LogWarning("[Timewarp] No se pudo preparar la entrada de texto (campo m_InputOverride no encontrado). " +
                                                      "Si no puedes escribir en los campos, pon Edit > Project Settings > Player > Active Input Handling = Both."); return; }
                if (field.GetValue(module) != null) return;
                field.SetValue(module, module.gameObject.AddComponent<SafeBaseInput>());
            }
            catch (Exception e)
            {
                Debug.LogWarning("[Timewarp] No se pudo preparar la entrada de texto: " + e.Message +
                                 ". Si no puedes escribir, pon Edit > Project Settings > Player > Active Input Handling = Both.");
            }
#endif
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
                FailConnect(_online ? "No se ha encontrado rival a tiempo. Vuelve a intentarlo."
                                    : "El servidor no respondió a la petición de partida. ¿Está arrancado el backend?");
        }

        void OnDestroy() { CloseConnection(); }
        void OnApplicationQuit() { CloseConnection(); }

        // ------------------------------------------------------------ flujo
        void ShowMenu()
        {
            CloseConnection();                  // si estabas en una partida online, el servidor la da por abandonada
            _online = false; _opponentName = null;
            _menu.ShowSearching(false);
            _game.SetOpponent(null);
            _game.Show(false);
            _game.Reset();
            _menu.Show(true);
            _menu.SetBusy(false);
            _screen = AppScreen.Menu;
        }

        // ------------------------------------------------------------ cuenta y mazos
        bool HasSession(string host) { return _userId != null && _token != null && _sessionHost == host; }

        void Logout()
        {
            _userId = null; _username = null; _token = null; _sessionHost = null;
            RestApi.Token = null;
            _decks = new List<DeckDto>();
            _menu.SetSession(null);
            _menu.SetDecks(_decks);
        }

        /// <summary>Entra (o crea la cuenta) con nombre y contraseña y carga sus mazos guardados. Devuelve true si hay sesión.</summary>
        async System.Threading.Tasks.Task<bool> LoginAsync(string name, string password, bool register)
        {
            string host = _menu.Host;
            if (string.IsNullOrWhiteSpace(name))
            {
                Logout();
                _menu.SetStatus("Escribe un nombre y una contraseña. Sin nombre juegas como invitado (solo mazos AGGRO / CONTROL).", true);
                return false;
            }
            _menu.SetBusy(true);
            _menu.SetStatus((register ? "Creando la cuenta «" : "Entrando como «") + name.Trim() + "»…", false);
            try
            {
                UserDto u = register ? await RestApi.RegisterAsync(host, name.Trim(), password)
                                     : await RestApi.LoginAsync(host, name.Trim(), password);
                _userId = u.id; _username = u.username; _token = u.token; _sessionHost = host;
                RestApi.Token = u.token;
                _decks = await RestApi.GetDecksAsync(host);
                _menu.SetSession(u.username);
                _menu.SetDecks(_decks);
                _menu.ClearPassword();                 // la contraseña no se guarda en ninguna parte
                _menu.Remember();
                _menu.SetStatus((register ? "Cuenta creada. " : "Sesión iniciada. ") + "Tienes " + _decks.Count + " mazo(s) guardado(s).", false);
                _menu.SetBusy(false);
                return true;
            }
            catch (Exception e)
            {
                Logout();
                Debug.LogWarning("[Timewarp] " + (register ? "Crear cuenta" : "Iniciar sesión") + " falló en " + host + ": " + e.Message);
                _menu.SetStatus((register ? "No se pudo crear la cuenta: " : "No se pudo iniciar sesión: ") + e.Message, true);
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
                _menu.SetStatus("Para crear mazos escribe tu nombre y contraseña y pulsa ENTRAR (o CREAR CUENTA si es tu primera vez).", true);
                return;
            }
            _menu.SetBusy(true);
            _menu.SetStatus("Cargando el catálogo de cartas…", false);
            try
            {
                var catalog = await RestApi.GetCardsAsync(host);
                _decks = await RestApi.GetDecksAsync(host);
                _menu.SetStatus("", false);
                _menu.SetBusy(false);
                _menu.Show(false);
                _builder.Open(host, _userId, _username, catalog, _decks, _menu.IsBuiltinDeck ? null : _menu.SelectedDeck);
            }
            catch (Exception e)
            {
                _menu.SetBusy(false);
                if (e.Message.Contains("caducada")) Logout();       // el token ha caducado: hay que volver a entrar
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

            if (!_menu.IsBuiltinDeck && !HasSession(host))
            {
                _menu.SetStatus("Para jugar con «" + deck + "» inicia sesión (nombre + contraseña y ENTRAR).", true);
                return;
            }

            _menu.SetBusy(true);
            _menu.SetStatus("Conectando con " + host + "…", false);
            _screen = AppScreen.Connecting;
            _connectDeadline = Time.unscaledTime + 30f;

            try
            {
                // identidad: tu cuenta si has iniciado sesión; si no, un invitado que emite el servidor (token firmado)
                string playerId, token;
                if (HasSession(host)) { playerId = _userId; token = _token; }
                else { var g = await RestApi.GuestAsync(host); playerId = g.id; token = g.token; }

                CloseConnection();
                _conn = new GameConnection();
                _conn.StateReceived += OnState;
                _conn.ErrorReceived += OnError;
                _conn.Disconnected += OnDisconnected;
                _conn.Log += m => Debug.Log("[Timewarp] " + m);
                _game.Conn = _conn;

                await _conn.ConnectAsync(host, playerId, token);
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

        async void OnOnlineClicked()
        {
            if (_screen != AppScreen.Menu) return;
            _menu.Remember();
            string host = _menu.Host;
            string deck = _menu.SelectedDeck;
            if (!_menu.IsBuiltinDeck && !HasSession(host))
            {
                _menu.SetStatus("Para jugar online con «" + deck + "» inicia sesión (nombre + contraseña y ENTRAR). Con AGGRO o CONTROL puedes entrar como invitado.", true);
                return;
            }

            _menu.SetBusy(true);
            _menu.SetStatus("Conectando con " + host + "…", false);
            _screen = AppScreen.Connecting;
            _online = true; _opponentName = null;
            _connectDeadline = Time.unscaledTime + 30f;
            try
            {
                string playerId, token;
                if (HasSession(host)) { playerId = _userId; token = _token; }
                else { var g = await RestApi.GuestAsync(host); playerId = g.id; token = g.token; }

                CloseConnection();
                _conn = new GameConnection();
                _conn.StatusReceived += OnStatus;
                _conn.StateReceived += OnState;
                _conn.ErrorReceived += OnError;
                _conn.Disconnected += OnDisconnected;
                _conn.Log += m => Debug.Log("[Timewarp] " + m);
                _game.Conn = _conn;

                await _conn.ConnectAsync(host, playerId, token);
                _deckInUse = deck;
                _menu.SetStatus("", false);
                _menu.ShowSearching(true, "BUSCANDO RIVAL…", "Conectado. Entrando en la cola…");
                _connectDeadline = Time.unscaledTime + 330f;          // el servidor saca de la cola a los 5 minutos
                _conn.JoinQueue(deck);
            }
            catch (Exception e)
            {
                FailConnect("No se pudo buscar partida online con " + host + ".\n" + e.Message);
            }
        }

        void Rematch()
        {
            if (_online)
            {
                // contra una persona: otra búsqueda con el mismo mazo (el rival puede ser otro)
                ShowMenu();
                OnOnlineClicked();
                return;
            }
            if (_conn == null || !_conn.IsConnected) { ShowMenu(); return; }
            _game.Reset();
            _conn.StartLocalMatch(_deckInUse, _botDeckInUse);
        }

        void FailConnect(string message)
        {
            CloseConnection();
            _online = false; _opponentName = null;
            _menu.ShowSearching(false);
            _menu.Show(true);
            _game.Show(false);
            _menu.SetBusy(false);
            _menu.SetStatus(message, true);
            _screen = AppScreen.Menu;
        }

        void CloseConnection()
        {
            if (_conn == null) return;
            _conn.StatusReceived -= OnStatus;
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
                _menu.ShowSearching(false);
                _menu.Show(false);
                _game.Reset();
                _game.SetOpponent(_online ? _opponentName : null);
                _game.Show(true);
                _screen = AppScreen.Game;
            }
            if (_screen == AppScreen.Game) _game.OnState(s);
        }

        /// <summary>Avisos de la cola de partida online (QUEUED, MATCHED, LEFT, TIMEOUT).</summary>
        void OnStatus(MatchStatusDto st)
        {
            if (st == null || !_online) return;
            switch (st.type)
            {
                case "QUEUED":
                    _menu.ShowSearching(true, "BUSCANDO RIVAL…", "Mazo: " + _deckInUse + "\nEn cuanto otra persona busque partida, empezáis.\nPuedes cancelar cuando quieras.");
                    break;
                case "MATCHED":
                    _opponentName = string.IsNullOrEmpty(st.opponentName) ? "Rival" : st.opponentName;
                    _menu.ShowSearching(true, "¡RIVAL ENCONTRADO!", "Vas a jugar contra  " + _opponentName + "\nPreparando la partida…");
                    _connectDeadline = Time.unscaledTime + 15f;
                    break;
                case "TIMEOUT":
                    FailConnect(string.IsNullOrEmpty(st.message) ? "No se ha encontrado rival. Vuelve a intentarlo." : st.message);
                    break;
            }
        }

        void CancelSearch()
        {
            if (_conn != null && _conn.IsConnected) _conn.LeaveQueue();
            ShowMenu();
            _menu.SetStatus("Búsqueda cancelada.", false);
        }

        void OnError(string message)
        {
            if (_screen == AppScreen.Connecting) FailConnect((_online ? "No se pudo buscar partida: " : "El servidor rechazó la petición de partida: ") + message);
            else if (_screen == AppScreen.Game) _game.OnError(message);
        }

        void OnDisconnected(string reason)
        {
            if (_screen == AppScreen.Game) _game.ShowDisconnected(reason + "\nVuelve al menú y reconecta (la partida en curso se pierde).");
            else if (_screen == AppScreen.Connecting) FailConnect(reason);
        }
    }
}
