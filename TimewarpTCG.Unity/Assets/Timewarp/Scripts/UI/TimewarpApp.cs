// PUNTO DE ENTRADA. No hace falta crear ninguna escena ni arrastrar nada:
// al pulsar Play (en cualquier escena) este script se crea solo y construye toda la interfaz por código.
using System;
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
        GameConnection _conn;
        string _deckInUse;
        float _connectDeadline;

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
                _help = new HelpView(_canvas.transform);

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

        async void OnPlayClicked()
        {
            if (_screen != AppScreen.Menu) return;
            _menu.Remember();
            _menu.SetBusy(true);
            _menu.SetStatus("Conectando con " + _menu.Host + "…", false);
            _screen = AppScreen.Connecting;
            _connectDeadline = Time.unscaledTime + 30f;

            string host = _menu.Host;
            string deck = _menu.DeckName;
            string playerId = MenuView.GuestId();
            string warn = "";

            try
            {
                if (_menu.Username.Length > 0)
                {
                    try
                    {
                        var u = await RestApi.LoginOrRegisterAsync(host, _menu.Username);
                        playerId = u.id;
                    }
                    catch (Exception e)
                    {
                        warn = "No se pudo iniciar sesión (" + e.Message + "). Juego como invitado.\n";
                        if (_menu.UsingCustomDeck) throw new Exception("Los mazos guardados requieren una cuenta válida. " + e.Message);
                    }
                }

                CloseConnection();
                _conn = new GameConnection();
                _conn.StateReceived += OnState;
                _conn.ErrorReceived += OnError;
                _conn.Disconnected += OnDisconnected;
                _conn.Log += m => Debug.Log("[Timewarp] " + m);
                _game.Conn = _conn;

                await _conn.ConnectAsync(host, playerId);
                _deckInUse = deck;
                _menu.SetStatus(warn + "Creando partida…", false);
                _connectDeadline = Time.unscaledTime + 12f;
                _conn.StartLocalMatch(deck);
            }
            catch (Exception e)
            {
                FailConnect(warn + "No se pudo iniciar la partida con " + host + ".\n" + e.Message);
            }
        }

        void Rematch()
        {
            if (_conn == null || !_conn.IsConnected) { ShowMenu(); return; }
            _game.Reset();
            _conn.StartLocalMatch(_deckInUse);
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
