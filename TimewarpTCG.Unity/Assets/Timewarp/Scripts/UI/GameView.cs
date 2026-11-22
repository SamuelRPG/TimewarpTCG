// Pantalla de partida. Interacción por CLIC (sin arrastrar):
//   - Carta de la mano  -> clic en tu Base / carril (unidades)  |  elegir objetivos y "LANZAR" (hechizos)
//   - Unidad tuya lista -> clic en la zona de destino (mover)   |  los movimientos se confirman con "CONFIRMAR MOVIMIENTOS"
//   - Botón grande      -> pasar prioridad
// El cliente solo da pistas visuales: el servidor valida y devuelve el error en español si algo es ilegal.
using System;
using System.Collections.Generic;
using UnityEngine;
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
        Text _oppInfo, _myInfo, _laneLeftHdr, _laneRightHdr, _handTitle;
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

        const float BoardW = 104f, BoardH = 144f;

        // ============================================================ construcción
        public GameView(Transform canvas)
        {
            var bg = UiKit.Box(canvas, "Game", UiKit.Bg, false, true);
            Root = bg.rectTransform;
            UiKit.Stretch(Root);

            BuildHud();
            BuildLeftColumn();
            BuildBoard();
            BuildHandAndActions();
            BuildOverlays();
            Show(false);
        }

        void BuildHud()
        {
            var hud = UiKit.Box(UiKit.Region(Root, "HudRegion", 0, 0, 1, 0.07f, 2), "Hud", UiKit.Panel);
            UiKit.Stretch(hud.rectTransform);

            _hudTurn = UiKit.Label(hud.transform, "TURNO 1", 34, UiKit.Gold, TextAnchor.MiddleLeft, FontStyle.Bold);
            UiKit.Place(_hudTurn.rectTransform, 0, 0.5f, 16, 0, 230, 50);

            _phaseBadge = UiKit.Box(hud.transform, "PhaseBadge", UiKit.Blue, true, false);
            UiKit.Place(_phaseBadge.rectTransform, 0, 0.5f, 250, 0, 300, 40);
            _hudPhase = UiKit.Label(_phaseBadge.transform, "FASE", 20, Color.white, TextAnchor.MiddleCenter, FontStyle.Bold);
            UiKit.Stretch(_hudPhase.rectTransform);

            _hudHelp = UiKit.Label(hud.transform, "", 15, UiKit.Muted, TextAnchor.MiddleLeft);
            _hudHelp.rectTransform.anchorMin = new Vector2(0, 0); _hudHelp.rectTransform.anchorMax = new Vector2(1, 1);
            _hudHelp.rectTransform.offsetMin = new Vector2(570, 2); _hudHelp.rectTransform.offsetMax = new Vector2(-420, -2);

            _hudScore = UiKit.Label(hud.transform, "", 34, UiKit.TextCol, TextAnchor.MiddleRight, FontStyle.Bold);
            UiKit.Place(_hudScore.rectTransform, 1, 0.5f, -16, 8, 400, 40);
            _hudGoal = UiKit.Label(hud.transform, "Meta: 10 puntos y 2 de ventaja", 14, UiKit.Muted, TextAnchor.MiddleRight);
            UiKit.Place(_hudGoal.rectTransform, 1, 0.5f, -16, -18, 400, 20);
        }

        void BuildLeftColumn()
        {
            var insp = UiKit.Box(UiKit.Region(Root, "InspRegion", 0, 0.07f, 0.185f, 0.34f), "Inspector", UiKit.Panel);
            UiKit.Stretch(insp.rectTransform);
            _inspector = UiKit.Label(insp.transform, CardView.Describe(null), 16, UiKit.TextCol, TextAnchor.UpperLeft);
            UiKit.Stretch(_inspector.rectTransform, 10, 8, 10, 8);

            var stack = UiKit.Box(UiKit.Region(Root, "StackRegion", 0, 0.34f, 0.185f, 0.45f), "Stack", UiKit.Panel);
            UiKit.Stretch(stack.rectTransform);
            _stackText = UiKit.Label(stack.transform, "", 14, UiKit.TextCol, TextAnchor.UpperLeft);
            UiKit.Stretch(_stackText.rectTransform, 10, 6, 10, 6);

            RectTransform content;
            _logScroll = UiKit.MakeScroll(UiKit.Region(Root, "LogRegion", 0, 0.45f, 0.185f, 0.76f), UiKit.Panel, out content, "Log");
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

        ZoneUi MakeZone(Transform region, string title, bool mine, Zone zone, bool titleVisible = true)
        {
            var z = new ZoneUi { zone = zone, mine = mine };
            z.baseColor = mine ? new Color32(24, 38, 70, 255) : new Color32(46, 28, 38, 255);
            z.bg = UiKit.Box(region, (mine ? "My" : "Opp") + zone, z.baseColor);
            UiKit.Stretch(z.bg.rectTransform);
            if (titleVisible)
            {
                var t = UiKit.Label(z.bg.transform, title, 13, UiKit.Muted, TextAnchor.UpperLeft, FontStyle.Bold);
                UiKit.Place(t.rectTransform, 0, 1, 8, -3, 420, 18);
            }
            z.content = UiKit.NewRect("Cards", z.bg.transform);
            UiKit.Stretch(z.content, 4, 20, 4, 4);
            UiKit.HRow(z.content, 6, 2, TextAnchor.MiddleCenter);
            var zz = z;
            UiKit.OnClick(z.bg.gameObject, () => OnZoneClicked(zz));
            return z;
        }

        Text InfoBox(Transform region)
        {
            var b = UiKit.Box(region, "Info", UiKit.Panel);
            UiKit.Stretch(b.rectTransform);
            var t = UiKit.Label(b.transform, "", 16, UiKit.TextCol, TextAnchor.MiddleLeft);
            UiKit.Stretch(t.rectTransform, 10, 4, 6, 4);
            return t;
        }

        void BuildBoard()
        {
            // fila del rival
            _oppInfo = InfoBox(UiKit.Region(Root, "OppInfoR", 0.185f, 0.07f, 0.33f, 0.235f));
            _oppBase = MakeZone(UiKit.Region(Root, "OppBaseR", 0.33f, 0.07f, 1f, 0.235f), "BASE DEL BOT", false, Zone.Base);

            // carriles: cabecera + zona rival + zona propia
            _laneLeftHdr = LaneHeader(UiKit.Region(Root, "LaneLH", 0.185f, 0.235f, 0.5925f, 0.265f, 2));
            _oppLeft = MakeZone(UiKit.Region(Root, "OppLeftR", 0.185f, 0.265f, 0.5925f, 0.43f), "BOT · carril izquierdo", false, Zone.Left);
            _myLeft = MakeZone(UiKit.Region(Root, "MyLeftR", 0.185f, 0.43f, 0.5925f, 0.595f), "TÚ · carril izquierdo", true, Zone.Left);

            _laneRightHdr = LaneHeader(UiKit.Region(Root, "LaneRH", 0.5925f, 0.235f, 1f, 0.265f, 2));
            _oppRight = MakeZone(UiKit.Region(Root, "OppRightR", 0.5925f, 0.265f, 1f, 0.43f), "BOT · carril derecho", false, Zone.Right);
            _myRight = MakeZone(UiKit.Region(Root, "MyRightR", 0.5925f, 0.43f, 1f, 0.595f), "TÚ · carril derecho", true, Zone.Right);

            // fila propia
            _myInfo = InfoBox(UiKit.Region(Root, "MyInfoR", 0.185f, 0.595f, 0.33f, 0.76f));
            _myBase = MakeZone(UiKit.Region(Root, "MyBaseR", 0.33f, 0.595f, 1f, 0.76f), "TU BASE", true, Zone.Base);
        }

        Text LaneHeader(Transform region)
        {
            var t = UiKit.Label(region, "", 16, UiKit.TextCol, TextAnchor.MiddleCenter, FontStyle.Bold);
            UiKit.Stretch(t.rectTransform);
            return t;
        }

        void BuildHandAndActions()
        {
            var handBox = UiKit.Box(UiKit.Region(Root, "HandR", 0.185f, 0.765f, 0.8f, 1f), "Hand", UiKit.Panel);
            UiKit.Stretch(handBox.rectTransform);
            _handTitle = UiKit.Label(handBox.transform, "TU MANO", 13, UiKit.Muted, TextAnchor.UpperLeft, FontStyle.Bold);
            UiKit.Place(_handTitle.rectTransform, 0, 1, 8, -3, 400, 18);
            _hand = UiKit.NewRect("HandCards", handBox.transform);
            UiKit.Stretch(_hand, 4, 22, 4, 4);
            UiKit.HRow(_hand, 6, 2, TextAnchor.MiddleCenter);

            var act = UiKit.Box(UiKit.Region(Root, "ActR", 0.8f, 0.765f, 1f, 1f), "Actions", UiKit.Panel);
            UiKit.Stretch(act.rectTransform);

            _whatNow = UiKit.Label(act.transform, "", 15, UiKit.Muted, TextAnchor.UpperLeft);
            UiKit.Place(_whatNow.rectTransform, 0.5f, 1, 0, -4, 368, 50);

            _ctxText = UiKit.Label(act.transform, "", 16, UiKit.TextCol, TextAnchor.UpperLeft);
            UiKit.Place(_ctxText.rectTransform, 0.5f, 1, 0, -54, 368, 58);

            _primaryBtn = UiKit.MakeButton(act.transform, "CONFIRMAR", UiKit.Green, OnPrimary, 18);
            UiKit.Place(_primaryBtn.GetComponent<RectTransform>(), 0, 1, 10, -114, 178, 40);
            _cancelBtn = UiKit.MakeButton(act.transform, "CANCELAR", UiKit.PanelLight, () => { ClearSelection(); Render(); }, 18);
            UiKit.Place(_cancelBtn.GetComponent<RectTransform>(), 1, 1, -10, -114, 178, 40);

            _passBtn = UiKit.MakeButton(act.transform, "PASAR PRIORIDAD", UiKit.Blue, DoPass, 22);
            UiKit.Place(_passBtn.GetComponent<RectTransform>(), 0.5f, 0, 0, 10, 368, 66);
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
            ClearSelection();
            _gameOver.gameObject.SetActive(false);
            _disc.gameObject.SetActive(false);
            _toast.gameObject.SetActive(false);
            _log.text = "";
            _inspector.text = CardView.Describe(null);
        }

        public void Tick()
        {
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

            _state = s;
            ClearSelection();
            Render();
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
            var s = _state; var me = s.activePlayer; var op = s.opponent;
            var pending = Pending();
            bool over = GameRules.IsOver(s);

            // --- HUD
            _hudTurn.text = "TURNO " + s.turn;
            _hudPhase.text = GameRules.PhaseName(s.gamePhase);
            _phaseBadge.color = PhaseColor(s.gamePhase);
            _hudHelp.text = GameRules.PhaseHelp(s.gamePhase);
            _hudScore.text = "<color=#7FB2FF>TÚ " + me.score + "</color>  —  <color=#FF8A80>" + op.score + " BOT</color>";

            // --- info jugadores
            _oppInfo.text = Info(op, "BOT", UiKit.Red);
            _myInfo.text = Info(me, "TÚ", UiKit.Blue);

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
                _goSub.text = "Puntuación final:  TÚ " + me.score + "  —  " + op.score + "  BOT\nTurno " + s.turn;
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
                if (_mode == Mode.PickTargets)
                {
                    if (_picked.Contains(c.id)) { st.selected = true; st.badge = "OBJ"; }
                    else if (IsCandidate(c, z.mine)) { st.hasOutline = true; st.outline = UiKit.Green; }
                }
                CardView.Create(z.content, card, BoardW, BoardH, st,
                    () => OnBoardCardClicked(card, z), () => Inspect(card), null);
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
                z.bg.color = hl ? (Color)new Color32(30, 96, 62, 255) : z.baseColor;
            }
        }

        void RenderHand(PlayerDto me, HashSet<string> pending)
        {
            UiKit.ClearChildren(_hand);
            var hand = me.hand ?? new List<CardDto>();
            _handTitle.text = "TU MANO (" + hand.Count + ")";
            int n = Mathf.Max(1, hand.Count);
            float avail = _hand.rect.width > 100 ? _hand.rect.width : 1180f;
            float w = Mathf.Clamp((avail - 16 - (n - 1) * 6) / n, 68f, 122f);
            float h = Mathf.Min(w / 122f * 170f, 176f);
            foreach (var c in hand)
            {
                var card = c;
                var st = new CardStyle();
                st.selected = _selCard != null && _selFromHand && _selCard.id == c.id;
                st.dim = GameRules.PlayHint(_state, c, pending) != null;
                CardView.Create(_hand, card, w, h, st, () => OnHandClicked(card), () => Inspect(card), null);
            }
        }

        void RenderStack(GameStateDto s)
        {
            if (s.spellStack == null || s.spellStack.Count == 0)
            {
                _stackText.text = "<b>PILA DE HECHIZOS</b>\n<color=#9AA2B8>(vacía)</color>";
                return;
            }
            var sb = new System.Text.StringBuilder("<b>PILA DE HECHIZOS</b> <color=#9AA2B8>(arriba = se resuelve primero)</color>\n");
            for (int i = s.spellStack.Count - 1; i >= 0; i--)
            {
                var e = s.spellStack[i];
                bool mine = e.ownerId == s.activePlayer.id;
                sb.Append(mine ? "<color=#7FB2FF>Tú</color>" : "<color=#FF8A80>Bot</color>").Append(": <b>").Append(e.cardName).Append("</b> (").Append(e.spellSpeed).Append(")\n");
            }
            _stackText.text = sb.ToString();
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
                        : "<b>" + _selCard.name + "</b>: elige objetivos (<b>" + _picked.Count + "/" + total + "</b>) — " + GameRules.RequirementsText(_selCard) + ".";
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
