// Pantalla de partida. Se puede jugar con CLIC o ARRASTRANDO (estilo juego de cartas digital):
//   - Carta de la mano  -> clic o arrastrar a tu Base / carril (unidades) | hechizos: arrastrar sobre el objetivo, o clic + "LANZAR"
//   - Unidad tuya lista -> clic o arrastrar a la zona de destino (mover) | los movimientos se confirman con "CONFIRMAR MOVIMIENTOS"
//   - Botón grande      -> pasar prioridad
// Los efectos visuales (números flotantes, partículas, banner de turno...) salen de los eventos que calcula StateDiff.Events.
// El cliente solo da pistas visuales: el servidor valida y devuelve el error en español si algo es ilegal.
using System;
using System.Collections.Generic;
using UnityEngine;
using UnityEngine.EventSystems;
using UnityEngine.UI;

namespace TimewarpTCG
{
    public sealed class GameView
    {
        enum Mode { None, PlaceUnit, MoveUnit, PickTargets }

        sealed class ZoneUi
        {
            public Zone zone;
            public bool mine;
            public Image bg;
            public Text title;
            public RectTransform content;
            public Color baseColor;
        }

        // ------------------------------------------------------------ enlaces con la app
        public GameConnection Conn;
        public Action OnExitToMenu, OnRematch, OnHelp;
        public RectTransform Root;

        // ------------------------------------------------------------ widgets
        Text _hudTurn, _hudPhase, _hudHelp, _hudScore, _hudGoal;
        Image _phaseBadge;
        ZoneUi _oppBase, _oppLeft, _oppRight, _myBase, _myLeft, _myRight;
        Text _laneLeftHdr, _laneRightHdr, _handTitle;
        PlayerPanel _oppPanel, _myPanel;                   // recursos, puntos y prioridad de cada jugador
        Image _turnPillBg; Text _turnPill;                 // "TU PRIORIDAD" / "JUEGA EL BOT…" en la barra superior
        bool? _lastHadPriority;
        string _oppName = "Bot";

        /// <summary>Tarjeta de un jugador: nombre, prioridad, recurso (n / máx. del turno), puntos y contadores.</summary>
        sealed class PlayerPanel
        {
            public Image frame, pillBg;
            public Text name, pill, resText, ptsText, counts;
            public Deco.GemBar res, pts;
        }
        RectTransform _hand;
        Text _inspector, _stackText, _log;
        ScrollRect _logScroll;
        Text _whatNow, _ctxText;
        Button _primaryBtn, _cancelBtn, _passBtn;
        RectTransform _toast; Text _toastText; float _toastUntil;
        RectTransform _gameOver; Text _goTitle, _goSub;
        RectTransform _disc; Text _discText;

        // ------------------------------------------------------------ estado
        GameStateDto _state;
        readonly List<string> _logLines = new List<string>();
        Mode _mode = Mode.None;
        CardDto _selCard;
        Zone _selZone;
        bool _selFromHand;
        string _selDest = "MY_BASE";
        List<string> _picked = new List<string>();
        readonly HashSet<string> _localPending = new HashSet<string>();
        bool _busy; float _busySince; string _lastKind, _lastCardId;

        const float BoardW = 98f, BoardH = 136f;          // algo mas pequenas para que 6 unidades quepan en un carril aunque haya panel de pila
        const float BoardRight = 0.865f;                   // el tablero llega hasta aqui; de BoardRight a 1 va la PILA
        const float BoardMid = 0.525f;                     // frontera entre el carril izquierdo y el derecho ((0.185 + BoardRight) / 2)
        const float HandW = 150f, HandH = 210f;

        // --- mano en abanico, arrastre y efectos
        sealed class HandCard { public CardDto card; public RectTransform rt; public CardMotion mot; public bool fly; }
        RectTransform _fxLayer, _dragLayer, _handBox;
        Image _arrow, _arrowHead;
        readonly List<HandCard> _handCards = new List<HandCard>();
        int _hoverIdx = -1;
        readonly Dictionary<string, RectTransform> _rects = new Dictionary<string, RectTransform>();      // id de carta -> su vista actual
        readonly Dictionary<string, Vector2> _prevScreen = new Dictionary<string, Vector2>();             // id -> posición en pantalla del render anterior
        readonly HashSet<string> _prevHandIds = new HashSet<string>();
        RectTransform _ghost, _dragSourceRt;
        CanvasGroup _dragSource;
        bool _dragging, _dragFromHand;
        CardDto _dragCard;
        Zone _dragZone;
        Zone? _dropHover;

        // ============================================================ construcción
        public GameView(Transform canvas)
        {
            var bg = UiKit.Box(canvas, "Game", UiKit.Bg, false, true);
            Root = bg.rectTransform;
            UiKit.Stretch(Root);
            Deco.Backdrop(Root, new Color(0.10f, 0.14f, 0.28f), new Color(0.02f, 0.03f, 0.07f), new Color(0.25f, 0.42f, 1f, 0.22f), new Color(0.70f, 0.35f, 1f, 0.16f));

            BuildAmbient();
            BuildHud();
            BuildLeftColumn();
            BuildBoard();
            BuildStackPanel();
            BuildHandAndActions();
            BuildOverlays();
            BuildFxLayers();
            Show(false);
        }

        // ------------------------------------------------------------ ambiente y capas de efectos
        void BuildAmbient()
        {
            // motas de luz que flotan despacio por el fondo (decoración barata; se anima con Anim)
            var layer = UiKit.NewRect("Ambient", Root);
            UiKit.Stretch(layer);
            Color[] tints = { new Color32(120, 150, 255, 255), new Color32(255, 190, 90, 255), new Color32(190, 120, 255, 255) };
            for (int i = 0; i < 16; i++)
            {
                var m = UiKit.CircleImage(layer, "Mote", tints[i % tints.Length]);
                var rt = m.rectTransform;
                rt.anchorMin = rt.anchorMax = new Vector2(0.5f, 0.5f);
                float sz = UnityEngine.Random.Range(50f, 190f);
                rt.sizeDelta = new Vector2(sz, sz);
                LoopMote(m);
            }
        }

        void LoopMote(Image m)
        {
            if (m == null) return;
            var rt = m.rectTransform;
            Vector2 a = new Vector2(UnityEngine.Random.Range(-900f, 900f), UnityEngine.Random.Range(-620f, -150f));
            Vector2 b = a + new Vector2(UnityEngine.Random.Range(-140f, 140f), UnityEngine.Random.Range(450f, 900f));
            float dur = UnityEngine.Random.Range(14f, 28f), peak = UnityEngine.Random.Range(0.04f, 0.10f);
            Anim.Run(dur, k =>
            {
                if (m == null) return false;
                rt.anchoredPosition = Vector2.Lerp(a, b, k);
                var c = m.color; c.a = peak * Mathf.Sin(k * Mathf.PI); m.color = c;
                return true;
            }, () => LoopMote(m), UnityEngine.Random.Range(0f, 6f));
        }

        void BuildFxLayers()
        {
            _dragLayer = UiKit.NewRect("DragLayer", Root);
            UiKit.Stretch(_dragLayer);
            _arrow = UiKit.Box(_dragLayer, "Arrow", UiKit.Gold, false, false);
            _arrow.rectTransform.anchorMin = _arrow.rectTransform.anchorMax = new Vector2(0.5f, 0.5f);
            _arrow.rectTransform.pivot = new Vector2(0f, 0.5f);
            _arrowHead = UiKit.CircleImage(_dragLayer, "ArrowHead", UiKit.Gold);
            _arrowHead.rectTransform.anchorMin = _arrowHead.rectTransform.anchorMax = new Vector2(0.5f, 0.5f);
            _arrowHead.rectTransform.sizeDelta = new Vector2(34, 34);
            _arrow.gameObject.SetActive(false); _arrowHead.gameObject.SetActive(false);

            _fxLayer = UiKit.NewRect("FxLayer", Root);
            UiKit.Stretch(_fxLayer);
            Fx.Layer = _fxLayer;
        }

        void BuildHud()
        {
            var hud = UiKit.Box(UiKit.Region(Root, "HudRegion", 0, 0, 1, 0.07f, 2), "Hud", UiKit.Panel);
            UiKit.Stretch(hud.rectTransform);

            _hudTurn = UiKit.Label(hud.transform, "TURNO 1", 34, UiKit.Gold, TextAnchor.MiddleLeft, FontStyle.Bold);
            UiKit.Place(_hudTurn.rectTransform, 0, 0.5f, 16, 0, 230, 50);
            Deco.TextShadow(_hudTurn);

            _phaseBadge = UiKit.Box(hud.transform, "PhaseBadge", UiKit.Blue, true, false);
            UiKit.Place(_phaseBadge.rectTransform, 0, 0.5f, 250, 0, 300, 40);
            _hudPhase = UiKit.Label(_phaseBadge.transform, "FASE", 20, Color.white, TextAnchor.MiddleCenter, FontStyle.Bold);
            UiKit.Stretch(_hudPhase.rectTransform);

            _hudHelp = UiKit.Label(hud.transform, "", 15, UiKit.Muted, TextAnchor.MiddleLeft);
            _hudHelp.rectTransform.anchorMin = new Vector2(0, 0); _hudHelp.rectTransform.anchorMax = new Vector2(1, 1);
            _hudHelp.rectTransform.offsetMin = new Vector2(900, 2); _hudHelp.rectTransform.offsetMax = new Vector2(-420, -2);

            // quién tiene la prioridad, bien grande: es lo que más se pierde de vista
            _turnPillBg = UiKit.Box(hud.transform, "TurnPill", UiKit.Green, true, false);
            UiKit.Place(_turnPillBg.rectTransform, 0, 0.5f, 560, 0, 330, 40);
            _turnPill = UiKit.Label(_turnPillBg.transform, "", 20, Color.white, TextAnchor.MiddleCenter, FontStyle.Bold);
            UiKit.Stretch(_turnPill.rectTransform);
            Deco.TextShadow(_turnPill, 0.5f, 1.4f);

            _hudScore = UiKit.Label(hud.transform, "", 34, UiKit.TextCol, TextAnchor.MiddleRight, FontStyle.Bold);
            UiKit.Place(_hudScore.rectTransform, 1, 0.5f, -16, 8, 400, 40);
            Deco.TextShadow(_hudScore);
            Deco.Divider(hud.transform, new Color(UiKit.Gold.r, UiKit.Gold.g, UiKit.Gold.b, 0.45f), 0, 0, 1500, 0.5f, 0f);   // filo dorado bajo la barra
            _hudGoal = UiKit.Label(hud.transform, "Meta: 10 puntos y 2 de ventaja", 14, UiKit.Muted, TextAnchor.MiddleRight);
            UiKit.Place(_hudGoal.rectTransform, 1, 0.5f, -16, -18, 400, 20);
        }

        void BuildLeftColumn()
        {
            var insp = UiKit.Box(UiKit.Region(Root, "InspRegion", 0, 0.07f, 0.185f, 0.40f), "Inspector", UiKit.Panel);
            UiKit.Stretch(insp.rectTransform);
            _inspector = UiKit.Label(insp.transform, CardView.Describe(null), 16, UiKit.TextCol, TextAnchor.UpperLeft);
            UiKit.Stretch(_inspector.rectTransform, 10, 8, 10, 8);

            RectTransform content;
            _logScroll = UiKit.MakeScroll(UiKit.Region(Root, "LogRegion", 0, 0.40f, 0.185f, 0.76f), UiKit.Panel, out content, "Log");
            UiKit.Stretch(_logScroll.GetComponent<RectTransform>());
            _log = UiKit.Label(content, "", 14, UiKit.TextCol, TextAnchor.UpperLeft);
            _log.verticalOverflow = VerticalWrapMode.Overflow;

            // botones inferiores izquierdos
            var bl = UiKit.Region(Root, "LeftButtons", 0, 0.765f, 0.185f, 1f);
            var help = UiKit.MakeButton(bl, "AYUDA / REGLAS", UiKit.PanelLight, () => { if (OnHelp != null) OnHelp(); }, 18);
            UiKit.Place(help.GetComponent<RectTransform>(), 0.5f, 1, 0, -6, 330, 52);
            var exit = UiKit.MakeButton(bl, "ABANDONAR PARTIDA", new Color32(120, 50, 50, 255), () => { if (OnExitToMenu != null) OnExitToMenu(); }, 18);
            UiKit.Place(exit.GetComponent<RectTransform>(), 0.5f, 1, 0, -66, 330, 52);
        }

        /// <summary>Motivo corto (para escribir sobre la carta) a partir de la pista larga de GameRules.PlayHint.</summary>
        static string HintShort(string hint)
        {
            if (hint == null) return null;
            if (hint.StartsWith("No tienes la prioridad")) return "ESPERA";
            if (hint.StartsWith("Recurso insuficiente")) return "SIN RECURSO";
            if (hint.StartsWith("Has movido")) return "CONFIRMA MOV.";
            if (hint.Contains("base está llena")) return "BASE LLENA";
            if (hint.Contains("terminado")) return "FIN";
            return "AHORA NO";                                  // fase incorrecta (unidades o hechizos SLOW fuera de la fase principal)
        }

        ZoneUi MakeZone(Transform region, string title, bool mine, Zone zone, bool titleVisible = true)
        {
            var z = new ZoneUi { zone = zone, mine = mine };
            z.baseColor = mine ? new Color32(24, 38, 70, 255) : new Color32(46, 28, 38, 255);
            z.bg = UiKit.Box(region, (mine ? "My" : "Opp") + zone, z.baseColor);
            UiKit.Stretch(z.bg.rectTransform);
            Deco.Panelize(z.bg, 0.24f, true, false);
            if (titleVisible)
            {
                var t = UiKit.Label(z.bg.transform, title, 13, UiKit.Muted, TextAnchor.UpperLeft, FontStyle.Bold);
                UiKit.Place(t.rectTransform, 0, 1, 8, -3, 420, 18);
                z.title = t;
            }
            z.content = UiKit.NewRect("Cards", z.bg.transform);
            UiKit.Stretch(z.content, 4, 20, 4, 4);
            UiKit.HRow(z.content, 6, 2, TextAnchor.MiddleCenter);
            var zz = z;
            UiKit.OnClick(z.bg.gameObject, () => OnZoneClicked(zz));
            return z;
        }

        /// <summary>
        /// Tarjeta de jugador. De arriba abajo: nombre y prioridad; RECURSO (disponible / máximo de este turno) y PUNTOS (de la meta de 10);
        /// una fila de gemas (azul brillante = disponible, translúcido = ya gastado este turno, apagado = aún no desbloqueado)
        /// y una fila de marcas doradas para los puntos; y los contadores de mazo, mano y cementerio.
        /// </summary>
        PlayerPanel BuildPlayerPanel(Transform region)
        {
            var p = new PlayerPanel();
            p.frame = UiKit.Box(region, "Info", UiKit.Panel);
            UiKit.Stretch(p.frame.rectTransform);
            Transform box = p.frame.transform;

            p.name = UiKit.Label(box, "", 22, UiKit.TextCol, TextAnchor.UpperLeft, FontStyle.Bold);
            UiKit.Place(p.name.rectTransform, 0, 1, 12, -7, 150, 30);
            p.pillBg = UiKit.Box(box, "Pill", new Color32(30, 38, 62, 255), true, false);
            UiKit.Place(p.pillBg.rectTransform, 1, 1, -10, -10, 118, 24);
            p.pill = UiKit.Label(p.pillBg.transform, "", 12, Color.white, TextAnchor.MiddleCenter, FontStyle.Bold);
            UiKit.Stretch(p.pill.rectTransform);

            var l1 = UiKit.Label(box, "RECURSO", 11, UiKit.Muted, TextAnchor.LowerLeft, FontStyle.Bold);
            UiKit.Place(l1.rectTransform, 0, 1, 14, -38, 110, 16);
            var l2 = UiKit.Label(box, "PUNTOS", 11, UiKit.Muted, TextAnchor.LowerLeft, FontStyle.Bold);
            UiKit.Place(l2.rectTransform, 0, 1, 150, -38, 110, 16);
            p.resText = UiKit.Label(box, "", 20, UiKit.TextCol, TextAnchor.MiddleLeft);
            UiKit.Place(p.resText.rectTransform, 0, 1, 12, -52, 132, 46);
            p.ptsText = UiKit.Label(box, "", 20, UiKit.TextCol, TextAnchor.MiddleLeft);
            UiKit.Place(p.ptsText.rectTransform, 0, 1, 148, -52, 120, 46);

            Color off = new Color32(30, 38, 62, 255);
            p.res = Deco.GemBar.Create(box, 10, 14, 3, new Color32(80, 150, 245, 255), off, true);
            UiKit.Place(p.res.Root, 0, 1, 13, -104, p.res.Width, 14);
            p.pts = Deco.GemBar.Create(box, 10, 22, 3, UiKit.Gold, off, false);
            UiKit.Place(p.pts.Root, 0, 1, 13, -124, p.pts.Width, 10);

            p.counts = UiKit.Label(box, "", 13, UiKit.Muted, TextAnchor.UpperLeft);
            UiKit.Place(p.counts.rectTransform, 0, 1, 12, -142, 256, 26);
            return p;
        }

        void UpdatePanel(PlayerPanel p, PlayerDto d, string label, Color accent, int turn)
        {
            if (p == null || d == null) return;
            int max = Mathf.Min(10, Mathf.Max(turn, d.resource));      // cada turno el recurso máximo es min(turno, 10) y se renueva entero
            p.name.text = label; p.name.color = accent;
            p.resText.text = "<size=40><b><color=#7FB2FF>" + d.resource + "</color></b></size><size=20><color=#9AA2B8>  / " + max + "</color></size>";
            p.ptsText.text = "<size=40><b><color=#FFC43D>" + d.score + "</color></b></size><size=20><color=#9AA2B8>  / 10</color></size>";
            p.res.Set(d.resource, max);
            p.pts.Set(d.score);
            p.counts.text = "Mazo " + d.deckSize + "  ·  Mano " + d.handSize + "  ·  Cementerio " + d.graveyardSize;
            p.pillBg.color = d.hasPriority ? (Color)new Color32(46, 150, 92, 255) : (Color)new Color32(30, 38, 62, 255);
            p.pill.text = d.hasPriority ? "● PRIORIDAD" : "○ ESPERA";
            p.pill.color = d.hasPriority ? Color.white : UiKit.Muted;
            // marco de luz del color del jugador mientras tiene la prioridad
            Deco.Safe(() => UiKit.AddOutline(p.frame, d.hasPriority ? new Color(accent.r, accent.g, accent.b, 0.85f) : new Color(1f, 1f, 1f, 0.07f), d.hasPriority ? 2f : 1f), "marco de prioridad");
        }

        /// <summary>Nombre del rival: "Bot" contra la máquina o el nombre de la persona contra la que juegas online.</summary>
        public void SetOpponent(string name)
        {
            _oppName = string.IsNullOrWhiteSpace(name) ? "Bot" : name.Trim();
            StateDiff.OpponentName = _oppName;
            string up = _oppName.ToUpperInvariant();
            bool bot = _oppName == "Bot";
            if (_oppBase != null && _oppBase.title != null) _oppBase.title.text = bot ? "BASE DEL BOT" : "BASE DE " + up;
            if (_oppLeft != null && _oppLeft.title != null) _oppLeft.title.text = up + " · carril izquierdo";
            if (_oppRight != null && _oppRight.title != null) _oppRight.title.text = up + " · carril derecho";
        }

        void BuildBoard()
        {
            // fila del rival
            _oppPanel = BuildPlayerPanel(UiKit.Region(Root, "OppInfoR", 0.185f, 0.07f, 0.33f, 0.235f));
            _oppBase = MakeZone(UiKit.Region(Root, "OppBaseR", 0.33f, 0.07f, BoardRight, 0.235f), "BASE DEL BOT", false, Zone.Base);

            // carriles: cabecera + zona rival + zona propia
            _laneLeftHdr = LaneHeader(UiKit.Region(Root, "LaneLH", 0.185f, 0.235f, BoardMid, 0.265f, 2));
            _oppLeft = MakeZone(UiKit.Region(Root, "OppLeftR", 0.185f, 0.265f, BoardMid, 0.43f), "BOT · carril izquierdo", false, Zone.Left);
            _myLeft = MakeZone(UiKit.Region(Root, "MyLeftR", 0.185f, 0.43f, BoardMid, 0.595f), "TÚ · carril izquierdo", true, Zone.Left);

            _laneRightHdr = LaneHeader(UiKit.Region(Root, "LaneRH", BoardMid, 0.235f, BoardRight, 0.265f, 2));
            _oppRight = MakeZone(UiKit.Region(Root, "OppRightR", BoardMid, 0.265f, BoardRight, 0.43f), "BOT · carril derecho", false, Zone.Right);
            _myRight = MakeZone(UiKit.Region(Root, "MyRightR", BoardMid, 0.43f, BoardRight, 0.595f), "TÚ · carril derecho", true, Zone.Right);

            // fila propia
            _myPanel = BuildPlayerPanel(UiKit.Region(Root, "MyInfoR", 0.185f, 0.595f, 0.33f, 0.76f));
            _myBase = MakeZone(UiKit.Region(Root, "MyBaseR", 0.33f, 0.595f, BoardRight, 0.76f), "TU BASE", true, Zone.Base);
        }

        Text LaneHeader(Transform region)
        {
            var t = UiKit.Label(region, "", 16, UiKit.TextCol, TextAnchor.MiddleCenter, FontStyle.Bold);
            UiKit.Stretch(t.rectTransform);
            return t;
        }

        void BuildHandAndActions()
        {
            var handBox = UiKit.Box(UiKit.Region(Root, "HandR", 0.185f, 0.765f, 0.8f, 1f), "Hand", new Color(0.09f, 0.11f, 0.18f, 0.55f));
            UiKit.Stretch(handBox.rectTransform);
            Deco.Panelize(handBox, 0.30f, true, false);
            _handBox = handBox.rectTransform;
            _handTitle = UiKit.Label(handBox.transform, "TU MANO", 13, UiKit.Muted, TextAnchor.UpperLeft, FontStyle.Bold);
            UiKit.Place(_handTitle.rectTransform, 0, 1, 8, -3, 400, 18);
            _hand = UiKit.NewRect("HandCards", handBox.transform);
            UiKit.Stretch(_hand, 0, 0, 0, 0);   // las cartas se colocan a mano en abanico (LayoutHand), sin LayoutGroup

            var act = UiKit.Box(UiKit.Region(Root, "ActR", 0.8f, 0.765f, 1f, 1f), "Actions", UiKit.Panel);
            UiKit.Stretch(act.rectTransform);

            _whatNow = UiKit.Label(act.transform, "", 15, UiKit.Muted, TextAnchor.UpperLeft);
            UiKit.Place(_whatNow.rectTransform, 0.5f, 1, 0, -4, 368, 50);

            _ctxText = UiKit.Label(act.transform, "", 16, UiKit.TextCol, TextAnchor.UpperLeft);
            UiKit.Place(_ctxText.rectTransform, 0.5f, 1, 0, -54, 368, 58);

            _primaryBtn = UiKit.MakeButton(act.transform, "CONFIRMAR", UiKit.Green, OnPrimary, 18);
            UiKit.Place(_primaryBtn.GetComponent<RectTransform>(), 0, 1, 10, -114, 178, 40);
            Deco.Halo(_primaryBtn.targetGraphic, new Color(0.27f, 0.73f, 0.45f, 0.30f), 3f);
            _cancelBtn = UiKit.MakeButton(act.transform, "CANCELAR", UiKit.PanelLight, () => { ClearSelection(); Render(); }, 18);
            UiKit.Place(_cancelBtn.GetComponent<RectTransform>(), 1, 1, -10, -114, 178, 40);

            _passBtn = UiKit.MakeButton(act.transform, "PASAR PRIORIDAD", UiKit.Blue, DoPass, 22);
            UiKit.Place(_passBtn.GetComponent<RectTransform>(), 0.5f, 0, 0, 10, 368, 66);
            Deco.Halo(_passBtn.targetGraphic, new Color(0.31f, 0.55f, 0.92f, 0.30f), 4f);
        }

        void BuildOverlays()
        {
            // aviso temporal (errores del servidor, pistas)
            _toast = UiKit.Box(UiKit.Region(Root, "ToastR", 0.25f, 0.075f, 0.75f, 0.13f), "Toast", new Color32(120, 30, 30, 240)).rectTransform;
            UiKit.Stretch(_toast);
            _toastText = UiKit.Label(_toast, "", 19, Color.white, TextAnchor.MiddleCenter, FontStyle.Bold);
            UiKit.Stretch(_toastText.rectTransform, 12, 4, 12, 4);
            _toast.gameObject.SetActive(false);

            // fin de partida
            var go = UiKit.Box(Root, "GameOver", UiKit.Shade, false, true);
            _gameOver = go.rectTransform; UiKit.Stretch(_gameOver);
            var gp = UiKit.Box(_gameOver, "Panel", UiKit.Panel);
            UiKit.Place(gp.rectTransform, 0.5f, 0.5f, 0, 0, 760, 420);
            _goTitle = UiKit.Label(gp.transform, "", 64, UiKit.Gold, TextAnchor.MiddleCenter, FontStyle.Bold);
            UiKit.Place(_goTitle.rectTransform, 0.5f, 1, 0, -36, 700, 90);
            _goSub = UiKit.Label(gp.transform, "", 26, UiKit.TextCol, TextAnchor.MiddleCenter);
            UiKit.Place(_goSub.rectTransform, 0.5f, 1, 0, -140, 700, 100);
            var again = UiKit.MakeButton(gp.transform, "REVANCHA", UiKit.Green, () => { if (OnRematch != null) OnRematch(); }, 28);
            UiKit.Place(again.GetComponent<RectTransform>(), 0.5f, 0, -190, 30, 340, 72);
            var menu = UiKit.MakeButton(gp.transform, "VOLVER AL MENÚ", UiKit.PanelLight, () => { if (OnExitToMenu != null) OnExitToMenu(); }, 28);
            UiKit.Place(menu.GetComponent<RectTransform>(), 0.5f, 0, 190, 30, 340, 72);
            _gameOver.gameObject.SetActive(false);

            // conexión perdida
            var dc = UiKit.Box(Root, "Disconnected", UiKit.Shade, false, true);
            _disc = dc.rectTransform; UiKit.Stretch(_disc);
            var dp = UiKit.Box(_disc, "Panel", UiKit.Panel);
            UiKit.Place(dp.rectTransform, 0.5f, 0.5f, 0, 0, 760, 300);
            var dt = UiKit.Label(dp.transform, "CONEXIÓN PERDIDA", 44, UiKit.Red, TextAnchor.MiddleCenter, FontStyle.Bold);
            UiKit.Place(dt.rectTransform, 0.5f, 1, 0, -26, 700, 60);
            _discText = UiKit.Label(dp.transform, "", 20, UiKit.TextCol, TextAnchor.UpperCenter);
            UiKit.Place(_discText.rectTransform, 0.5f, 1, 0, -96, 700, 100);
            var back = UiKit.MakeButton(dp.transform, "VOLVER AL MENÚ", UiKit.Blue, () => { if (OnExitToMenu != null) OnExitToMenu(); }, 26);
            UiKit.Place(back.GetComponent<RectTransform>(), 0.5f, 0, 0, 24, 340, 66);
            _disc.gameObject.SetActive(false);
        }

        // ============================================================ ciclo de vida
        public void Show(bool v) { Root.gameObject.SetActive(v); }

        public void Reset()
        {
            _state = null;
            _logLines.Clear();
            _localPending.Clear();
            _busy = false;
            _handCards.Clear(); _rects.Clear(); _prevScreen.Clear(); _prevHandIds.Clear();
            _hoverIdx = -1;
            AbortDrag();
            HidePeek();
            ClearSelection();
            _gameOver.gameObject.SetActive(false);
            _disc.gameObject.SetActive(false);
            _toast.gameObject.SetActive(false);
            _log.text = "";
            _inspector.text = CardView.Describe(null);
        }

        public void Tick()
        {
            // red de seguridad: un arrastre cuya carta de origen ya no existe nunca recibira OnEndDrag; un fantasma sin arrastre sobra
            if (_peek != null && _peek.gameObject.activeSelf && _peekSource == null) HidePeek();   // la entrada que se mantenia ya no existe
            if (_dragging && _dragSourceRt == null) AbortDrag();
            else if (!_dragging && _ghost != null) EndDragVisuals();
            if (_toast.gameObject.activeSelf && Time.unscaledTime > _toastUntil) _toast.gameObject.SetActive(false);
            if (_busy && Time.unscaledTime - _busySince > 10f)
            {
                _busy = false;
                ShowToast("El servidor tarda en responder. Si no avanza, pulsa PASAR o reinicia la partida.", true);
                Render();
            }
        }

        public void ShowDisconnected(string reason)
        {
            _discText.text = reason;
            _disc.gameObject.SetActive(true);
            _disc.SetAsLastSibling();
        }

        // ============================================================ mensajes del servidor
        public void OnState(GameStateDto s)
        {
            _busy = false;
            var prev = _state;

            // registro de lo ocurrido (incluye el turno entero del bot)
            AddLog(StateDiff.Describe(prev, s));

            // movimientos pendientes (solo hace falta rastrearlos si el backend no manda pendingMoveIds)
            if (!GameRules.Patched(s))
            {
                // El servidor (PlayerState.pendingMoves) recuerda toda unidad movida desde la última confirmación,
                // aunque la devuelvas a su sitio; se vacía al pasar prioridad / cambiar de turno.
                if (_lastKind == "move" && _lastCardId != null && prev != null && prev.turn == s.turn)
                    _localPending.Add(_lastCardId);
                else
                    _localPending.Clear();
            }
            _lastKind = null; _lastCardId = null;

            // posiciones previas (para poder dibujar efectos donde estaba una carta que ya no existe) y eventos del cambio
            _prevScreen.Clear();
            foreach (var kv in _rects) if (kv.Value != null) _prevScreen[kv.Key] = Fx.ScreenPos(kv.Value);
            var events = StateDiff.Events(prev, s);

            _state = s;
            ClearSelection();
            Render();
            PlayFx(prev, s, events);
        }

        public void OnError(string message)
        {
            _busy = false;
            _lastKind = null; _lastCardId = null;
            ShowToast(message, true);
            AddLog(new List<string> { "<color=#FF8A80>El servidor rechaza la jugada:</color> " + message });
            ClearSelection();
            if (_state != null) Render();
        }

        // ============================================================ acciones del jugador
        bool Begin(string kind, string cardId)
        {
            if (_state == null) return false;
            if (GameRules.IsOver(_state)) { ShowToast("La partida ha terminado.", true); return false; }
            if (_busy) { ShowToast("Esperando respuesta del servidor…", false); return false; }
            _busy = true; _busySince = Time.unscaledTime; _lastKind = kind; _lastCardId = cardId;
            return true;
        }

        void DoPlay(CardDto c, string dest, List<string> targets)
        {
            if (!Begin("play", c.id)) return;
            Conn.PlayCard(c.id, dest, targets);
            ClearSelection(); Render();
        }

        void DoMove(CardDto c, Zone to)
        {
            if (!Begin("move", c.id)) return;
            Conn.MoveCard(c.id, GameRules.ToDestination(to));
            ClearSelection(); Render();
        }

        void DoPass()
        {
            if (!Begin("pass", null)) return;
            Conn.PassPriority();
            ClearSelection(); Render();
        }

        void OnPrimary()
        {
            if (_mode == Mode.PickTargets && _selCard != null)
                DoPlay(_selCard, _selDest ?? "MY_BASE", new List<string>(_picked));
        }

        // ============================================================ selección y clics
        void ClearSelection()
        {
            _mode = Mode.None; _selCard = null; _selFromHand = false; _selDest = "MY_BASE"; _picked = new List<string>();
        }

        HashSet<string> Pending() { return GameRules.PendingIds(_state, _localPending); }

        void Inspect(CardDto c) { _inspector.text = CardView.Describe(c); }

        void OnHandClicked(CardDto c)
        {
            if (_state == null || GameRules.IsOver(_state)) return;
            if (_selCard != null && _selFromHand && _selCard.id == c.id) { ClearSelection(); Render(); return; }
            ClearSelection();
            _selCard = c; _selFromHand = true;
            if (GameRules.IsUnit(c)) _mode = Mode.PlaceUnit;
            else { _mode = Mode.PickTargets; _selDest = "MY_BASE"; }
            string hint = GameRules.PlayHint(_state, c, Pending());
            if (hint != null) ShowToast("Pista: " + hint, false);
            Render();
        }

        void OnBoardCardClicked(CardDto c, ZoneUi z)
        {
            if (_state == null) return;
            Inspect(c);
            switch (_mode)
            {
                case Mode.PickTargets:
                    ToggleTarget(c, z.mine);
                    return;
                case Mode.PlaceUnit:
                    if (z.mine) OnZoneClicked(z);
                    return;
                case Mode.MoveUnit:
                    if (z.mine && z.zone != _selZone) { OnZoneClicked(z); return; }
                    if (z.mine && _selCard != null && _selCard.id == c.id) { ClearSelection(); Render(); return; }
                    if (z.mine) { SelectForMove(c, z.zone); return; }
                    return;
                default:
                    if (z.mine) SelectForMove(c, z.zone);
                    return;
            }
        }

        void SelectForMove(CardDto c, Zone zone)
        {
            ClearSelection();
            _selCard = c; _selZone = zone; _selFromHand = false; _mode = Mode.MoveUnit;
            string hint = GameRules.MoveHint(_state, c);
            if (hint != null) ShowToast("Pista: " + hint, false);
            Render();
        }

        void OnZoneClicked(ZoneUi z)
        {
            if (!z.mine || _state == null) return;
            if (_mode == Mode.PlaceUnit && _selCard != null)
            {
                if (GameRules.TotalTargets(_selCard) > 0)
                {
                    _selDest = GameRules.ToDestination(z.zone);
                    _picked = new List<string>();
                    _mode = Mode.PickTargets;
                    Render();
                }
                else DoPlay(_selCard, GameRules.ToDestination(z.zone), new List<string>());
            }
            else if (_mode == Mode.MoveUnit && _selCard != null)
            {
                if (z.zone == _selZone) { ClearSelection(); Render(); }
                else DoMove(_selCard, z.zone);
            }
        }

        void ToggleTarget(CardDto c, bool mine)
        {
            if (_selCard == null) return;
            bool valid = false;
            foreach (var req in GameRules.Requirements(_selCard)) if (GameRules.IsValidTarget(req.group, mine)) valid = true;
            if (!valid) { ShowToast("«" + c.name + "» no es un objetivo válido (" + GameRules.RequirementsText(_selCard) + ").", false); return; }

            if (_picked.Contains(c.id)) _picked.Remove(c.id);
            else
            {
                if (_picked.Count >= GameRules.TotalTargets(_selCard)) { ShowToast("Ya has elegido todos los objetivos (" + GameRules.TotalTargets(_selCard) + ").", false); return; }
                _picked.Add(c.id);
            }
            Render();
        }

        bool IsCandidate(CardDto c, bool mine)
        {
            if (_selCard == null) return false;
            foreach (var req in GameRules.Requirements(_selCard)) if (GameRules.IsValidTarget(req.group, mine)) return true;
            return false;
        }

        // ============================================================ dibujo
        public void Render()
        {
            if (_state == null) return;
            try { RenderInner(); }
            catch (Exception e)
            {
                Debug.LogException(e);
                ShowToast("Error de interfaz: " + e.Message, true);
            }
        }

        void RenderInner()
        {
            if (_dragging || _ghost != null) AbortDrag();      // la carta que se arrastraba va a ser reconstruida: no dejar el fantasma
            var s = _state; var me = s.activePlayer; var op = s.opponent;
            var pending = Pending();
            bool over = GameRules.IsOver(s);
            _rects.Clear();
            _hoverIdx = -1;

            // --- HUD
            _hudTurn.text = "TURNO " + s.turn;
            _hudPhase.text = GameRules.PhaseName(s.gamePhase);
            _phaseBadge.color = PhaseColor(s.gamePhase);
            _hudHelp.text = GameRules.PhaseHelp(s.gamePhase);
            _hudScore.text = "<color=#7FB2FF>TÚ " + me.score + "</color>  —  <color=#FF8A80>" + op.score + " " + _oppName.ToUpperInvariant() + "</color>";

            // --- quién tiene la prioridad
            _turnPillBg.color = over ? UiKit.PanelLight : (me.hasPriority ? (Color)new Color32(46, 150, 92, 255) : (Color)new Color32(200, 96, 56, 255));
            _turnPill.text = over ? "FIN DE LA PARTIDA" : (me.hasPriority ? "TU PRIORIDAD · ACTÚA" : "JUEGA " + _oppName.ToUpperInvariant() + "…");
            if (_lastHadPriority.HasValue && _lastHadPriority.Value != me.hasPriority && !over) Fx.Pulse(_turnPillBg.rectTransform, 1.16f);
            _lastHadPriority = me.hasPriority;

            // --- info jugadores
            UpdatePanel(_oppPanel, op, _oppName.ToUpperInvariant(), UiKit.Red, s.turn);
            UpdatePanel(_myPanel, me, "TÚ", UiKit.Blue, s.turn);

            // --- carriles
            _laneLeftHdr.text = LaneTitle("CARRIL IZQUIERDO", GameRules.Contested(s, Zone.Left));
            _laneRightHdr.text = LaneTitle("CARRIL DERECHO", GameRules.Contested(s, Zone.Right));

            // --- zonas
            Fill(_oppBase, GameRules.CardsIn(op, Zone.Base), pending);
            Fill(_oppLeft, GameRules.CardsIn(op, Zone.Left), pending);
            Fill(_oppRight, GameRules.CardsIn(op, Zone.Right), pending);
            Fill(_myBase, GameRules.CardsIn(me, Zone.Base), pending);
            Fill(_myLeft, GameRules.CardsIn(me, Zone.Left), pending);
            Fill(_myRight, GameRules.CardsIn(me, Zone.Right), pending);
            ColorZones();

            // --- mano
            RenderHand(me, pending);

            // --- pila
            RenderStack(s);

            // --- acciones
            RenderActions(s, pending, over);

            // --- fin de partida
            if (over)
            {
                bool won = GameRules.IWon(s);
                _goTitle.text = won ? "¡VICTORIA!" : "DERROTA";
                _goTitle.color = won ? UiKit.Green : UiKit.Red;
                _goSub.text = "Puntuación final:  TÚ " + me.score + "  —  " + op.score + "  " + _oppName.ToUpperInvariant() + "\nTurno " + s.turn
                            + (Mathf.Max(me.score, op.score) < 10 ? "\n<color=#FFC43D>" + (won ? _oppName + " ha abandonado la partida." : "La partida terminó por abandono.") + "</color>" : "");
                _gameOver.gameObject.SetActive(true);
                _gameOver.SetAsLastSibling();
            }
            else _gameOver.gameObject.SetActive(false);
        }

        static string LaneTitle(string name, bool contested)
        {
            return contested ? name + "   <color=#FF8A80>[DISPUTADO]</color>" : name;
        }

        static Color PhaseColor(string phase)
        {
            switch (phase)
            {
                case "MAIN_CLOSED": return UiKit.Orange;
                case "COMBAT_OPEN": return UiKit.Red;
                case "COMBAT_CLOSED": return new Color32(150, 40, 40, 255);
                default: return UiKit.Blue;
            }
        }

        static string Info(PlayerDto p, string name, Color c)
        {
            return "<b><size=22><color=#" + UiKit.Hex(c) + ">" + name + "</color></size></b>\n"
                 + "♦ Recurso: <b>" + p.resource + "</b>     Puntos: <b>" + p.score + "</b>\n"
                 + "Mazo " + p.deckSize + " · Mano " + p.handSize + " · Cementerio " + p.graveyardSize + "\n"
                 + (p.hasPriority ? "<color=#46B973>● Tiene la prioridad</color>" : "<color=#9AA2B8>○ Sin prioridad</color>");
        }

        void Fill(ZoneUi z, List<CardDto> cards, HashSet<string> pending)
        {
            UiKit.ClearChildren(z.content);
            foreach (var c in cards)
            {
                var card = c;
                var st = new CardStyle();
                st.selected = _selCard != null && !_selFromHand && _selCard.id == c.id;
                st.pending = z.mine && pending.Contains(c.id);
                if (z.mine && !st.pending && !GameRules.IsOver(_state) && GameRules.IsUnit(c) && _mode != Mode.PickTargets)
                {
                    // unidad tuya que se puede mover ahora mismo (lista, con prioridad): borde verde y etiqueta
                    if (!c.isExhausted && GameRules.MoveHint(_state, c) == null)
                    {
                        st.hasOutline = true; st.outline = UiKit.Green;
                        st.badge = "MOVER"; st.badgeColor = UiKit.Green;
                    }
                }
                if (_mode == Mode.PickTargets)
                {
                    if (_picked.Contains(c.id)) { st.selected = true; st.badge = "OBJ"; }
                    else if (IsCandidate(c, z.mine)) { st.hasOutline = true; st.outline = UiKit.Green; }
                }
                var view = CardView.Create(z.content, card, BoardW, BoardH, st,
                    () => OnBoardCardClicked(card, z), () => Inspect(card), null);
                _rects[c.id] = view.Root;
                if (z.mine)
                {
                    var rt = view.Root; var zone = z.zone;
                    var d = rt.gameObject.AddComponent<Draggable>();
                    d.OnBegin = e => BeginDrag(card, false, zone, rt, e);
                    d.OnMove = OnDragMove;
                    d.OnEnd = EndDrag;
                }
            }
        }

        void ColorZones()
        {
            foreach (var z in new[] { _oppBase, _oppLeft, _oppRight, _myBase, _myLeft, _myRight })
            {
                bool hl = false;
                if (z.mine && _selCard != null)
                {
                    if (_mode == Mode.PlaceUnit)
                        hl = z.zone == Zone.Base ? GameRules.PlayHint(_state, _selCard, Pending()) == null
                                                 : GameRules.LaneLooksPlayable(_state, _selCard, z.zone);
                    else if (_mode == Mode.MoveUnit)
                        hl = GameRules.MoveLooksLegal(_state, _selCard, _selZone, z.zone);
                }
                bool over = hl && z.mine && _dropHover.HasValue && _dropHover.Value == z.zone;
                z.bg.color = hl ? (over ? (Color)new Color32(48, 150, 96, 255) : (Color)new Color32(30, 96, 62, 255)) : z.baseColor;
            }
        }

        void RenderHand(PlayerDto me, HashSet<string> pending)
        {
            UiKit.ClearChildren(_hand);
            _handCards.Clear();
            var hand = me.hand ?? new List<CardDto>();
            _handTitle.text = "TU MANO (" + hand.Count + ")   ·   <color=#46B973>borde verde = se puede jugar ahora</color>   ·   cartas grises = no (el motivo está sobre la carta)";
            float W = _hand.rect.width > 100 ? _hand.rect.width : 1180f;
            bool first = _prevHandIds.Count == 0;
            var ids = new HashSet<string>();

            for (int i = 0; i < hand.Count; i++)
            {
                var card = hand[i];
                int idx = i;
                var st = new CardStyle();
                st.selected = _selCard != null && _selFromHand && _selCard.id == card.id;
                string playHint = GameRules.PlayHint(_state, card, pending);
                st.dim = playHint != null;
                if (playHint == null)
                {
                    // se puede jugar AHORA: borde verde y etiqueta
                    st.hasOutline = true; st.outline = UiKit.Green;
                    st.badge = "JUGAR"; st.badgeColor = UiKit.Green;
                }
                else
                {
                    st.reason = HintShort(playHint);
                    st.costRed = card.currentCost > me.resource;      // el coste se pone rojo si no te llega el recurso
                }
                var view = CardView.Create(_hand, card, HandW, HandH, st,
                    () => OnHandClicked(card),
                    () => { if (_dragging) return; _hoverIdx = idx; Inspect(card); LayoutHand(false); },
                    () => { if (_hoverIdx == idx) { _hoverIdx = -1; if (!_dragging) LayoutHand(false); } });
                var rt = view.Root;
                rt.anchorMin = rt.anchorMax = new Vector2(0.5f, 0.5f);
                rt.pivot = new Vector2(0.5f, 0.5f);
                rt.sizeDelta = new Vector2(HandW, HandH);
                var mot = rt.gameObject.AddComponent<CardMotion>();
                var d = rt.gameObject.AddComponent<Draggable>();
                d.OnBegin = e => BeginDrag(card, true, Zone.Base, rt, e);
                d.OnMove = OnDragMove;
                d.OnEnd = EndDrag;
                bool isNew = !first && !_prevHandIds.Contains(card.id);
                _handCards.Add(new HandCard { card = card, rt = rt, mot = mot, fly = isNew });
                ids.Add(card.id);
                _rects[card.id] = rt;
                if (isNew) rt.anchoredPosition = new Vector2(W / 2f + 260f, 0f);   // las cartas robadas entran volando desde la derecha
            }
            _prevHandIds.Clear();
            foreach (var id in ids) _prevHandIds.Add(id);
            if (_prevHandIds.Count == 0) _prevHandIds.Add("(vacia)");   // marca que ya hubo un primer render
            LayoutHand(true);
        }

        /// <summary>Coloca la mano en abanico. La carta bajo el ratón sube y crece; las vecinas se apartan.</summary>
        void LayoutHand(bool snap)
        {
            int n = _handCards.Count;
            if (n == 0) return;
            float W = _hand.rect.width > 100 ? _hand.rect.width : 1180f;
            float H = _hand.rect.height > 60 ? _hand.rect.height : 230f;
            float spacing = n > 1 ? Mathf.Min(HandW * 0.80f, (W - HandW - 30f) / (n - 1)) : 0f;
            float baseY = -H / 2f + HandH / 2f - 62f;            // las cartas asoman por debajo del borde, como un abanico
            float angle = n > 1 ? Mathf.Min(5f, 26f / (n - 1)) : 0f;
            float drop = Mathf.Min(3.4f, 16f / n);
            bool hovering = _hoverIdx >= 0 && !_dragging;

            for (int i = 0; i < n; i++) _handCards[i].rt.SetSiblingIndex(i);
            for (int i = 0; i < n; i++)
            {
                var h = _handCards[i];
                float k = i - (n - 1) / 2f;
                float x = k * spacing, y = baseY - k * k * drop, rot = -k * angle, sc = 1f;
                if (hovering)
                {
                    if (i == _hoverIdx) { y = baseY + 124f; rot = 0f; sc = 1.28f; h.rt.SetAsLastSibling(); }
                    else x += Mathf.Sign(i - _hoverIdx) * (HandW * 0.62f) / (1f + Mathf.Abs(i - _hoverIdx) * 0.6f);
                }
                h.mot.targetPos = new Vector2(x, y); h.mot.targetRot = rot; h.mot.targetScale = sc;
                if (snap && !h.fly) h.mot.SnapNow();
                if (snap) h.fly = false;
            }
        }

        // ============================================================ la PILA (panel a la derecha del tablero)
        RectTransform _stackList, _peek, _peekHolder, _peekSource;
        Text _peekCaption;
        static readonly Color StackMine = new Color32(80, 140, 235, 255), StackTheirs = new Color32(226, 82, 82, 255);

        void BuildStackPanel()
        {
            var panel = UiKit.Box(UiKit.Region(Root, "StackR", BoardRight, 0.07f, 1f, 0.76f, 2), "StackPanel", UiKit.Panel);
            UiKit.Stretch(panel.rectTransform);
            _stackText = UiKit.Label(panel.transform, "PILA", 24, UiKit.Gold, TextAnchor.UpperLeft, FontStyle.Bold);
            UiKit.Place(_stackText.rectTransform, 0, 1, 12, -8, 230, 32);
            Deco.GoldTitle(_stackText);
            var sub = UiKit.Label(panel.transform, "Lo de ARRIBA se resuelve primero", 12, UiKit.Muted, TextAnchor.UpperLeft);
            UiKit.Place(sub.rectTransform, 0, 1, 12, -40, 236, 18);
            _stackList = UiKit.NewRect("StackList", panel.transform);
            UiKit.Stretch(_stackList, 8, 66, 8, 62);
            var foot = UiKit.Label(panel.transform, "Mantén pulsado el clic izquierdo sobre una entrada para ver la carta en grande.", 12, UiKit.Muted, TextAnchor.LowerLeft);
            UiKit.Place(foot.rectTransform, 0, 0, 12, 8, 236, 48);

            // vista grande: aparece MIENTRAS se mantiene el clic y desaparece al soltar (no bloquea el raton)
            _peek = UiKit.NewRect("StackPeek", Root);
            UiKit.Place(_peek, 0.5f, 0.5f, 40, 10, 360, 590);
            var back = UiKit.Box(_peek, "PeekBack", new Color(0.04f, 0.05f, 0.09f, 0.94f), true, false);
            UiKit.Stretch(back.rectTransform);
            Deco.Halo(back, new Color(UiKit.Gold.r, UiKit.Gold.g, UiKit.Gold.b, 0.55f), 3f);
            _peekHolder = UiKit.NewRect("PeekCard", _peek);
            UiKit.Place(_peekHolder, 0.5f, 1, 0, -14, 300, 420);
            _peekCaption = UiKit.Label(_peek, "", 15, UiKit.TextCol, TextAnchor.UpperCenter);
            UiKit.Place(_peekCaption.rectTransform, 0.5f, 0, 0, 10, 336, 138);
            _peek.gameObject.SetActive(false);
        }

        void HidePeek()
        {
            if (_peek != null) _peek.gameObject.SetActive(false);
            _peekSource = null;
        }

        void ShowPeek(StackEntryDto e, int order, RectTransform source)
        {
            if (_peek == null || e == null) return;
            _peekSource = source;
            UiKit.ClearChildren(_peekHolder);
            var card = GameRules.StackEntryToCard(e);
            var v = CardView.Create(_peekHolder, card, 300, 420, new CardStyle(), null, null, null);
            UiKit.Stretch(v.Root);
            var cg = v.Root.GetComponent<CanvasGroup>();
            if (cg != null) cg.blocksRaycasts = false;
            bool mine = e.ownerId == _state.activePlayer.id;
            string who = mine ? "<color=#7FB2FF>Tú</color>" : "<color=#FF8A80>" + _oppName + "</color>";
            string what = e.kind == "TRIGGER" ? "Efecto <b>al entrar en juego</b> de esta unidad" : "Hechizo <b>" + (e.spellSpeed ?? "") + "</b>";
            string targets = e.targetNames != null && e.targetNames.Count > 0 ? "\nObjetivos: <b>" + string.Join(", ", e.targetNames.ToArray()) + "</b>" : "";
            _peekCaption.text = who + " · " + what + targets + "\n<color=#FFC43D>Se resuelve en " + ordinal(order) + " lugar</color>";
            _peek.gameObject.SetActive(true);
        }

        static string ordinal(int n) { return n == 1 ? "1.er" : n + ".º"; }

        /// <summary>Marca en el tablero la unidad que origina una entrada (dorado) y sus objetivos (naranja).</summary>
        void FlashStackEntry(StackEntryDto e)
        {
            RectTransform rt;
            if (_rects.TryGetValue(e.cardId, out rt) && rt != null) Fx.Flash(rt, UiKit.Gold);
            if (e.targetIds != null)
                foreach (var id in e.targetIds)
                    if (_rects.TryGetValue(id, out rt) && rt != null) Fx.Flash(rt, UiKit.Orange);
        }

        void RenderStack(GameStateDto s)
        {
            HidePeek();                                   // la lista se reconstruye: cualquier vista grande abierta pierde su origen
            UiKit.ClearChildren(_stackList);
            int n = s.spellStack == null ? 0 : s.spellStack.Count;
            _stackText.text = n == 0 ? "PILA" : "PILA  (" + n + ")";
            if (n == 0)
            {
                var empty = UiKit.Label(_stackList, "Vacía.\n\nCuando alguien lance un hechizo rápido o lento, o juegue una unidad con efecto «al entrar», aparecerá aquí y el rival podrá responder.", 14, UiKit.Muted, TextAnchor.UpperLeft);
                UiKit.Stretch(empty.rectTransform, 4, 4, 4, 0);
                return;
            }

            const float EntryH = 86f, CaptionH = 18f, Gap = 6f;
            float y = 0f;
            int shown = Mathf.Min(n, 5);
            for (int k = 0; k < shown; k++)
            {
                var e = s.spellStack[n - 1 - k];          // el ULTIMO de la lista es el primero en resolverse
                int order = k + 1;
                bool mine = e.ownerId == s.activePlayer.id;
                bool trigger = e.kind == "TRIGGER";
                Color side = mine ? StackMine : StackTheirs;

                // etiqueta de orden
                var cap = UiKit.Label(_stackList, k == 0 ? "SE RESUELVE PRIMERO" : "LUEGO", k == 0 ? 12 : 11, k == 0 ? UiKit.Gold : UiKit.Muted, TextAnchor.LowerLeft, FontStyle.Bold);
                SetTopStretch(cap.rectTransform, y, CaptionH);
                y += CaptionH;

                // la entrada
                var box = UiKit.Box(_stackList, "StackEntry", new Color32(34, 42, 68, 255));
                SetTopStretch(box.rectTransform, y, EntryH);
                if (k == 0) UiKit.AddOutline(box, UiKit.Gold, 2f);
                var bar = UiKit.Box(box.transform, "Side", side, false, false);
                bar.rectTransform.anchorMin = new Vector2(0, 0); bar.rectTransform.anchorMax = new Vector2(0, 1);
                bar.rectTransform.pivot = new Vector2(0, 0.5f); bar.rectTransform.sizeDelta = new Vector2(6, 0);

                var num = UiKit.CircleImage(box.transform, "Num", k == 0 ? UiKit.Gold : UiKit.PanelLight);
                UiKit.Place(num.rectTransform, 0, 0.5f, 14, 0, 32, 32);
                var numT = UiKit.Label(num.transform, order.ToString(), 18, k == 0 ? Color.black : Color.white, TextAnchor.MiddleCenter, FontStyle.Bold);
                UiKit.Stretch(numT.rectTransform);

                var name = UiKit.Label(box.transform, e.cardName, 16, Color.white, TextAnchor.MiddleLeft, FontStyle.Bold);
                UiKit.BestFit(name, 10, 16);
                name.rectTransform.anchorMin = new Vector2(0, 1); name.rectTransform.anchorMax = new Vector2(1, 1);
                name.rectTransform.pivot = new Vector2(0, 1); name.rectTransform.offsetMin = new Vector2(54, -28); name.rectTransform.offsetMax = new Vector2(-6, -4);

                string kind = trigger ? "AL ENTRAR" : "HECHIZO " + (e.spellSpeed ?? "");
                string owner = mine ? "<color=#7FB2FF>TÚ</color>" : "<color=#FF8A80>" + _oppName.ToUpperInvariant() + "</color>";
                var tag = UiKit.Label(box.transform, owner + "  ·  <color=" + (trigger ? "#C9A0FF" : "#7FE0A8") + ">" + kind + "</color>", 12, UiKit.TextCol, TextAnchor.MiddleLeft, FontStyle.Bold);
                tag.rectTransform.anchorMin = new Vector2(0, 1); tag.rectTransform.anchorMax = new Vector2(1, 1);
                tag.rectTransform.pivot = new Vector2(0, 1); tag.rectTransform.offsetMin = new Vector2(54, -48); tag.rectTransform.offsetMax = new Vector2(-6, -30);

                string tgt = e.targetNames != null && e.targetNames.Count > 0 ? "→ " + string.Join(", ", e.targetNames.ToArray()) : (trigger ? "sin objetivo" : "sin objetivos");
                var tl = UiKit.Label(box.transform, tgt, 12, UiKit.Muted, TextAnchor.UpperLeft);
                tl.rectTransform.anchorMin = new Vector2(0, 0); tl.rectTransform.anchorMax = new Vector2(1, 0);
                tl.rectTransform.pivot = new Vector2(0, 0); tl.rectTransform.offsetMin = new Vector2(54, 4); tl.rectTransform.offsetMax = new Vector2(-6, 36);

                // interaccion: pasar el raton = detalles a la izquierda y resalte en el tablero; MANTENER el clic = carta en grande
                var entry = e; int ord = order; var rt = box.rectTransform;
                UiKit.OnClick(box.gameObject, null, () => { Inspect(GameRules.StackEntryToCard(entry)); FlashStackEntry(entry); }, null);
                var hp = box.gameObject.AddComponent<HoldPeek>();
                hp.OnHold = () => ShowPeek(entry, ord, rt);
                hp.OnRelease = HidePeek;

                y += EntryH + Gap;
            }
            if (n > shown)
            {
                var more = UiKit.Label(_stackList, "… y " + (n - shown) + " más debajo", 13, UiKit.Muted, TextAnchor.UpperLeft);
                SetTopStretch(more.rectTransform, y, 20);
            }
        }

        /// <summary>Coloca un rectangulo a todo el ancho de su padre, a 'y' px desde arriba.</summary>
        static void SetTopStretch(RectTransform rt, float y, float h)
        {
            rt.anchorMin = new Vector2(0, 1); rt.anchorMax = new Vector2(1, 1); rt.pivot = new Vector2(0.5f, 1);
            rt.anchoredPosition = new Vector2(0, -y); rt.sizeDelta = new Vector2(0, h);
        }

        void RenderActions(GameStateDto s, HashSet<string> pending, bool over)
        {
            var me = s.activePlayer;
            _whatNow.text = GameRules.WhatNow(s, pending);

            // etiqueta del botón de pasar
            string label;
            bool rivalPassed = s.consecutivePasses == "ONE";
            bool stack = s.spellStack != null && s.spellStack.Count > 0;
            if (over) label = "FIN DE PARTIDA";
            else if (!me.hasPriority) label = "ESPERANDO AL RIVAL…";
            else if (pending.Count > 0) label = "CONFIRMAR MOVIMIENTOS";
            else if (stack) label = rivalPassed ? "PASAR Y RESOLVER PILA" : "PASAR PRIORIDAD";
            else if (s.gamePhase.StartsWith("COMBAT")) label = rivalPassed ? "PASAR Y COMBATIR" : "PASAR PRIORIDAD";
            else label = rivalPassed ? "TERMINAR TURNO" : "PASAR PRIORIDAD";
            UiKit.ButtonLabel(_passBtn).text = _busy ? "ESPERANDO AL SERVIDOR…" : label;
            _passBtn.interactable = !over && !_busy;
            _passBtn.GetComponent<Image>().color = pending.Count > 0 ? UiKit.Gold : UiKit.Blue;
            UiKit.ButtonLabel(_passBtn).color = pending.Count > 0 ? Color.black : Color.white;

            // contexto de la selección
            bool showPrimary = false, showCancel = _mode != Mode.None;
            switch (_mode)
            {
                case Mode.None:
                    _ctxText.text = "<color=#9AA2B8>Haz clic en una carta de tu mano o en una unidad tuya lista.</color>";
                    break;
                case Mode.PlaceUnit:
                    _ctxText.text = "Jugar <b>" + _selCard.name + "</b>: haz clic en <b>TU BASE</b>" +
                        (HasLaneOption() ? " (o en un carril resaltado)." : ".");
                    break;
                case Mode.MoveUnit:
                    _ctxText.text = "Mover <b>" + _selCard.name + "</b>: haz clic en la zona de destino resaltada.";
                    break;
                case Mode.PickTargets:
                    int total = GameRules.TotalTargets(_selCard);
                    showPrimary = true;
                    UiKit.ButtonLabel(_primaryBtn).text = GameRules.IsSpell(_selCard) ? "LANZAR" : "JUGAR";
                    _ctxText.text = total == 0
                        ? "<b>" + _selCard.name + "</b> no necesita objetivos."
                        : "<b>" + _selCard.name + "</b>: elige objetivos (<b>" + _picked.Count + "/" + total + "</b>) — " + GameRules.RequirementsText(_selCard) + "."
                          + (GameRules.IsUnit(_selCard) ? " <color=#9AA2B8>Si no hay objetivos, puedes jugarla igualmente.</color>" : "");
                    break;
            }
            _primaryBtn.gameObject.SetActive(showPrimary);
            _cancelBtn.gameObject.SetActive(showCancel);
            _primaryBtn.interactable = !_busy;
        }

        bool HasLaneOption()
        {
            return _selCard != null && (GameRules.LaneLooksPlayable(_state, _selCard, Zone.Left) || GameRules.LaneLooksPlayable(_state, _selCard, Zone.Right));
        }

        // ============================================================ arrastrar y soltar
        void BeginDrag(CardDto c, bool fromHand, Zone zone, RectTransform source, PointerEventData e)
        {
            if (_state == null || GameRules.IsOver(_state) || _busy) return;
            if (_ghost != null) EndDragVisuals();               // un fantasma antiguo que nadie limpio: se borra antes de crear el nuevo
            _dragging = true; _dragCard = c; _dragFromHand = fromHand; _dragZone = zone;
            _dragSourceRt = source; _hoverIdx = -1;

            // misma selección que con un clic, pero SIN repintar (repintar destruiría la carta que se está arrastrando)
            ClearSelection();
            _selCard = c; _selFromHand = fromHand; _selZone = zone; _selDest = "MY_BASE"; _picked = new List<string>();
            _mode = !fromHand ? Mode.MoveUnit : (GameRules.IsUnit(c) ? Mode.PlaceUnit : Mode.PickTargets);
            string hint = fromHand ? GameRules.PlayHint(_state, c, Pending()) : GameRules.MoveHint(_state, c);
            if (hint != null) ShowToast("Pista: " + hint, false);

            float gw = fromHand ? HandW * 1.05f : BoardW * 1.3f, gh = fromHand ? HandH * 1.05f : BoardH * 1.3f;
            var gv = CardView.Create(_dragLayer, c, gw, gh, new CardStyle(), null, null, null);
            _ghost = gv.Root;
            _ghost.anchorMin = _ghost.anchorMax = new Vector2(0.5f, 0.5f);
            _ghost.sizeDelta = new Vector2(gw, gh);
            var cg = _ghost.GetComponent<CanvasGroup>();
            if (cg != null) { cg.blocksRaycasts = false; cg.alpha = 0.93f; }
            _dragSource = source.GetComponent<CanvasGroup>();
            if (_dragSource != null) _dragSource.alpha = 0.3f;

            _dropHover = null;
            ColorZones();
            GlowTargets(true);
            Inspect(c);
            MoveGhost(e.position);
        }

        void OnDragMove(PointerEventData e)
        {
            if (!_dragging) return;
            MoveGhost(e.position);
            var z = ZoneAt(e.position);
            Zone? h = z != null ? (Zone?)z.zone : null;
            if (!Nullable.Equals(h, _dropHover)) { _dropHover = h; ColorZones(); }
            UpdateArrow(e.position);
        }

        void EndDrag(PointerEventData e)
        {
            if (!_dragging) return;
            _dragging = false;
            Vector2 pos = e.position;
            var c = _dragCard; bool fromHand = _dragFromHand; Zone from = _dragZone;
            EndDragVisuals();

            var zone = ZoneAt(pos);
            if (fromHand && GameRules.IsUnit(c))
            {
                if (zone != null) OnZoneClicked(zone); else CancelDrag();
            }
            else if (fromHand)
            {
                int total = GameRules.TotalTargets(c);
                if (total == 0)
                {
                    if (!Over(_handBox, pos)) DoPlay(c, "MY_BASE", new List<string>()); else CancelDrag();
                }
                else
                {
                    CardDto tgt; bool tMine; Zone tz;
                    if (TargetAt(pos, out tgt, out tMine, out tz))
                    {
                        ToggleTarget(tgt, tMine);                       // repinta y marca el objetivo
                        if (_picked.Count >= total) OnPrimary();         // si ya están todos, se lanza
                    }
                    else CancelDrag();
                }
            }
            else
            {
                if (zone != null && zone.zone != from) DoMove(c, zone.zone); else CancelDrag();
            }
        }

        void CancelDrag() { ClearSelection(); Render(); }

        /// <summary>
        /// Cancela un arrastre y borra el "fantasma" de la carta. Hace falta porque Unity NO llama a OnEndDrag si la carta de origen se
        /// destruye durante el arrastre (p. ej. llega un estado nuevo y se repinta el tablero): sin esto, el fantasma se quedaba flotando.
        /// </summary>
        void AbortDrag()
        {
            _dragging = false;
            EndDragVisuals();
        }

        void EndDragVisuals()
        {
            if (_ghost != null) UnityEngine.Object.Destroy(_ghost.gameObject);
            _ghost = null;
            if (_dragSource != null) _dragSource.alpha = 1f;
            _dragSource = null;
            _arrow.gameObject.SetActive(false); _arrowHead.gameObject.SetActive(false);
            _dropHover = null;
            GlowTargets(false);
            ColorZones();
        }

        void MoveGhost(Vector2 screen)
        {
            Vector2 local;
            if (_ghost != null && RectTransformUtility.ScreenPointToLocalPointInRectangle(_dragLayer, screen, null, out local))
                _ghost.anchoredPosition = local;
        }

        /// <summary>Flecha dorada desde la carta hasta el puntero cuando se arrastra un hechizo que pide objetivo.</summary>
        void UpdateArrow(Vector2 screen)
        {
            bool needs = _dragFromHand && _dragCard != null && GameRules.IsSpell(_dragCard) && GameRules.TotalTargets(_dragCard) > 0;
            _arrow.gameObject.SetActive(needs); _arrowHead.gameObject.SetActive(needs);
            if (!needs || _dragSourceRt == null) return;
            Vector2 a, b;
            if (!RectTransformUtility.ScreenPointToLocalPointInRectangle(_dragLayer, Fx.ScreenPos(_dragSourceRt), null, out a)) return;
            if (!RectTransformUtility.ScreenPointToLocalPointInRectangle(_dragLayer, screen, null, out b)) return;
            Vector2 d = b - a;
            _arrow.rectTransform.anchoredPosition = a;
            _arrow.rectTransform.sizeDelta = new Vector2(d.magnitude, 8f);
            _arrow.rectTransform.localEulerAngles = new Vector3(0, 0, Mathf.Atan2(d.y, d.x) * Mathf.Rad2Deg);
            _arrowHead.rectTransform.anchoredPosition = b;
            CardDto tgt; bool tm; Zone tz;
            bool good = TargetAt(screen, out tgt, out tm, out tz) && IsCandidate(tgt, tm);
            _arrow.color = _arrowHead.color = good ? UiKit.Green : UiKit.Gold;
        }

        /// <summary>Resalta en verde las cartas del tablero que pueden ser objetivo del hechizo arrastrado.</summary>
        void GlowTargets(bool on)
        {
            if (_state == null) return;
            foreach (var kv in _rects)
            {
                if (kv.Value == null) continue;
                var img = kv.Value.GetComponent<Image>();
                if (img == null) continue;
                CardDto c; bool mine; Zone z;
                if (!GameRules.FindOnBoard(_state, kv.Key, out c, out mine, out z)) continue;
                var o = img.GetComponent<Outline>();
                if (on && _mode == Mode.PickTargets && IsCandidate(c, mine)) UiKit.AddOutline(img, UiKit.Green, 3f);
                else if (!on && o != null) UnityEngine.Object.Destroy(o);
            }
        }

        ZoneUi ZoneAt(Vector2 screen)
        {
            foreach (var z in new[] { _myBase, _myLeft, _myRight })
                if (Over(z.bg.rectTransform, screen)) return z;
            return null;
        }

        bool TargetAt(Vector2 screen, out CardDto card, out bool mine, out Zone zone)
        {
            foreach (var kv in _rects)
            {
                if (kv.Value == null || !Over(kv.Value, screen)) continue;
                if (GameRules.FindOnBoard(_state, kv.Key, out card, out mine, out zone)) return true;
            }
            card = null; mine = false; zone = Zone.Base;
            return false;
        }

        static bool Over(RectTransform rt, Vector2 screen) { return rt != null && RectTransformUtility.RectangleContainsScreenPoint(rt, screen, null); }

        // ============================================================ efectos visuales según lo ocurrido
        void PlayFx(GameStateDto prev, GameStateDto s, List<StateDiff.GameEvent> events)
        {
            try
            {
                if (prev == null)
                {
                    Fx.Banner("¡EMPIEZA LA PARTIDA!", "Turno " + s.turn + " · recurso " + s.activePlayer.resource, UiKit.Gold);
                    return;
                }
                RectTransform rt;
                foreach (var e in events)
                {
                    switch (e.kind)
                    {
                        case StateDiff.EventKind.Played:
                        case StateDiff.EventKind.Moved:
                            if (_rects.TryGetValue(e.cardId, out rt))
                            {
                                Fx.PopIn(rt);
                                if (e.kind == StateDiff.EventKind.Played) Fx.Burst(Fx.ScreenPos(rt), e.mine ? UiKit.Blue : UiKit.Red, 10, 80f, 9f);
                            }
                            break;
                        case StateDiff.EventKind.DriveChanged:
                            if (_rects.TryGetValue(e.cardId, out rt))
                            {
                                int delta = e.b - e.a;
                                Color col = delta > 0 ? UiKit.Green : UiKit.Red;
                                Fx.FloatText(Fx.ScreenPos(rt), (delta > 0 ? "+" : "") + delta, col, 46);
                                Fx.Flash(rt, col);
                            }
                            break;
                        case StateDiff.EventKind.Destroyed:
                            Vector2 p;
                            if (_prevScreen.TryGetValue(e.cardId, out p))
                            {
                                Fx.Burst(p, UiKit.Orange, 20, 160f, 12f);
                                Fx.Burst(p, UiKit.Red, 10, 100f, 8f);
                                Fx.FloatText(p + new Vector2(0, 30), "DESTRUIDA", new Color32(255, 150, 90, 255), 28, 60f);
                            }
                            break;
                        case StateDiff.EventKind.SpellOnStack:
                            Fx.FloatText(Fx.ScreenPos(_stackText.rectTransform) + new Vector2(180, 0), e.cardName, e.mine ? UiKit.Blue : (Color)new Color32(230, 100, 220, 255), 34, 70f);
                            break;
                        case StateDiff.EventKind.CombatResolved:
                            foreach (var zz in new[] { _oppLeft, _myLeft, _oppRight, _myRight }) Fx.Shake(zz.bg.rectTransform, 7f, 0.4f);
                            break;
                        case StateDiff.EventKind.Score:
                            if (e.a > 0) { Fx.FloatText(Fx.ScreenPos(_hudScore.rectTransform) + new Vector2(-150, -30), "+" + e.a, UiKit.Blue, 40); Fx.Pulse(_hudScore.rectTransform, 1.2f); }
                            if (e.b > 0) { Fx.FloatText(Fx.ScreenPos(_hudScore.rectTransform) + new Vector2(110, -30), "+" + e.b, UiKit.Red, 40); Fx.Pulse(_hudScore.rectTransform, 1.2f); }
                            break;
                        case StateDiff.EventKind.Turn:
                            Fx.Banner("TURNO " + e.a, "Recurso " + s.activePlayer.resource + (s.activePlayer.hasPriority ? " · tienes la prioridad" : ""), UiKit.Gold, 0.6f);
                            break;
                        case StateDiff.EventKind.GameOver:
                            Fx.Banner(e.mine ? "¡VICTORIA!" : "DERROTA", "Puntuación final " + s.activePlayer.score + " - " + s.opponent.score, e.mine ? UiKit.Green : UiKit.Red, 1.4f);
                            break;
                    }
                }
            }
            catch (Exception ex) { Debug.LogException(ex); }   // los efectos nunca deben romper la partida
        }

        // ============================================================ registro y avisos
        void AddLog(List<string> lines)
        {
            if (lines == null || lines.Count == 0) return;
            _logLines.AddRange(lines);
            if (_logLines.Count > 150) _logLines.RemoveRange(0, _logLines.Count - 150);
            _log.text = string.Join("\n", _logLines.ToArray());
            Canvas.ForceUpdateCanvases();
            _logScroll.verticalNormalizedPosition = 0f;
        }

        void ShowToast(string msg, bool error)
        {
            _toastText.text = msg;
            _toast.GetComponent<Image>().color = error ? new Color32(130, 32, 32, 245) : new Color32(52, 62, 100, 245);
            _toast.gameObject.SetActive(true);
            _toast.SetAsLastSibling();
            _toastUntil = Time.unscaledTime + (error ? 6f : 3.5f);
        }
    }
}
