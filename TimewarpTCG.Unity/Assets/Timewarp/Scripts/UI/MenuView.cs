// Pantalla inicial: servidor, cuenta (opcional), selector de MAZO (de prueba o tus mazos guardados), MAZO DEL BOT y JUGAR.
using System;
using System.Collections.Generic;
using System.Linq;
using UnityEngine;
using UnityEngine.UI;

namespace TimewarpTCG
{
    public sealed class MenuView
    {
        public RectTransform Root;
        public Action OnPlay, OnHelp, OnEditDecks;
        public Action<string, string> OnLogin;       // (nombre, contraseña)
        public Action<string, string> OnRegister;

        readonly InputField _host, _user, _pass;
        readonly Button _play, _login, _register, _edit;
        readonly Text _status, _session;
        readonly RectTransform _deckList;
        readonly Dictionary<string, Button> _botBtns = new Dictionary<string, Button>();
        string _selectedDeck = "AGGRO";
        string _botDeck = "RANDOM";
        List<DeckDto> _decks = new List<DeckDto>();

        const string KHost = "tw_host", KUser = "tw_user", KDeck = "tw_deck", KBot = "tw_bot";

        public string Host { get { return string.IsNullOrWhiteSpace(_host.text) ? "localhost:8080" : _host.text.Trim(); } }
        public string Username { get { return _user.text == null ? "" : _user.text.Trim(); } }
        public string Password { get { return _pass.text ?? ""; } }
        public void ClearPassword() { _pass.text = ""; }
        public string SelectedDeck { get { return _selectedDeck; } }
        public string BotDeck { get { return _botDeck; } }
        public bool IsBuiltinDeck { get { return _selectedDeck == "AGGRO" || _selectedDeck == "CONTROL"; } }

        public MenuView(Transform canvas)
        {
            var bg = UiKit.Box(canvas, "Menu", UiKit.Bg, false, true);
            Root = bg.rectTransform;
            UiKit.Stretch(Root);
            Deco.Backdrop(Root, new Color(0.10f, 0.14f, 0.28f), new Color(0.02f, 0.03f, 0.07f), new Color(0.25f, 0.42f, 1f, 0.22f), new Color(0.70f, 0.35f, 1f, 0.16f));

            var panel = UiKit.Box(Root, "Panel", UiKit.Panel);
            UiKit.Place(panel.rectTransform, 0.5f, 0.5f, 0, 0, 1000, 980);

            var title = UiKit.Label(panel.transform, "TIMEWARP TCG", 62, UiKit.Gold, TextAnchor.MiddleCenter, FontStyle.Bold);
            UiKit.Place(title.rectTransform, 0.5f, 1, 0, -14, 940, 80);
            Deco.GoldTitle(title);
            var sub = UiKit.Label(panel.transform, "Partida de prueba contra el bot", 22, UiKit.Muted, TextAnchor.MiddleCenter);
            UiKit.Place(sub.rectTransform, 0.5f, 1, 0, -90, 940, 30);
            Deco.Divider(panel.transform, new Color(UiKit.Gold.r, UiKit.Gold.g, UiKit.Gold.b, 0.65f), 0, -124, 760);

            // ---- servidor y cuenta
            Caption(panel.transform, "Servidor (backend en marcha)", -134, -260);
            _host = UiKit.MakeInput(panel.transform, "localhost:8080", PlayerPrefs.GetString(KHost, "localhost:8080"), 22);
            UiKit.Place(_host.GetComponent<RectTransform>(), 0.5f, 1, -260, -162, 440, 48);

            Caption(panel.transform, "Cuenta: nombre (vacío = invitado)", -134, 130);
            _user = UiKit.MakeInput(panel.transform, "invitado", PlayerPrefs.GetString(KUser, ""), 22);
            UiKit.Place(_user.GetComponent<RectTransform>(), 0.5f, 1, 120, -162, 240, 48);

            Caption(panel.transform, "Contraseña (mín. 8 caracteres)", -134, 370);
            _pass = UiKit.MakeInput(panel.transform, "contraseña", "", 22);
            _pass.contentType = InputField.ContentType.Password;       // se muestra como ••••, y nunca se guarda en PlayerPrefs
            _pass.ForceLabelUpdate();
            UiKit.Place(_pass.GetComponent<RectTransform>(), 0.5f, 1, 370, -162, 220, 48);

            _session = UiKit.Label(panel.transform, "", 16, UiKit.Muted, TextAnchor.MiddleLeft);
            UiKit.Place(_session.rectTransform, 0.5f, 1, -230, -222, 500, 44);
            _login = UiKit.MakeButton(panel.transform, "ENTRAR", UiKit.Blue, () => { if (OnLogin != null) OnLogin(Username, Password); }, 20);
            UiKit.Place(_login.GetComponent<RectTransform>(), 0.5f, 1, 300, -222, 170, 44);
            _register = UiKit.MakeButton(panel.transform, "CREAR CUENTA", UiKit.PanelLight, () => { if (OnRegister != null) OnRegister(Username, Password); }, 18);
            UiKit.Place(_register.GetComponent<RectTransform>(), 0.5f, 1, 450, -222, 180, 44);

            // ---- tu mazo
            var deckLabel = UiKit.Label(panel.transform, "TU MAZO", 18, UiKit.Muted, TextAnchor.LowerLeft, FontStyle.Bold);
            UiKit.Place(deckLabel.rectTransform, 0.5f, 1, -450, -276, 400, 26);
            RectTransform content;
            var scroll = UiKit.MakeScroll(panel.transform, new Color32(12, 15, 26, 255), out content, "DeckScroll");
            UiKit.Place(scroll.GetComponent<RectTransform>(), 0.5f, 1, -160, -306, 640, 262);
            _deckList = content;

            _edit = UiKit.MakeButton(panel.transform, "EDITAR MIS MAZOS", UiKit.PanelLight, () => { if (OnEditDecks != null) OnEditDecks(); }, 22);
            UiKit.Place(_edit.GetComponent<RectTransform>(), 0.5f, 1, 340, -306, 280, 70);
            var tip = UiKit.Label(panel.transform, "Crea mazos de 40 cartas con las cartas de tu base de datos (máx. 3 copias y 2 facciones). Necesitas una cuenta (nombre + contraseña) para guardarlos.", 15, UiKit.Muted, TextAnchor.UpperLeft);
            UiKit.Place(tip.rectTransform, 0.5f, 1, 340, -386, 280, 180);

            // ---- mazo del bot
            var botLabel = UiKit.Label(panel.transform, "MAZO DEL BOT", 18, UiKit.Muted, TextAnchor.LowerLeft, FontStyle.Bold);
            UiKit.Place(botLabel.rectTransform, 0.5f, 1, -450, -594, 400, 26);
            string[] keys = { "RANDOM", "AGGRO", "CONTROL" };
            string[] labels = { "ALEATORIO\n(cada partida uno distinto)", "AGGRO\n(25× 2/1 + 15× 5/3)", "CONTROL\n(muros 4/1 + hechizos)" };
            _botDeck = PlayerPrefs.GetString(KBot, "RANDOM");
            if (Array.IndexOf(keys, _botDeck) < 0) _botDeck = "RANDOM";
            for (int i = 0; i < keys.Length; i++)
            {
                string k = keys[i];
                var b = UiKit.MakeButton(panel.transform, labels[i], UiKit.PanelLight, () => { _botDeck = k; RefreshBot(); }, 16);
                UiKit.Place(b.GetComponent<RectTransform>(), 0.5f, 1, -315 + i * 315, -624, 300, 64);
                _botBtns[k] = b;
            }

            _play = UiKit.MakeButton(panel.transform, "JUGAR CONTRA EL BOT", UiKit.Green, () => { if (OnPlay != null) OnPlay(); }, 32);
            UiKit.Place(_play.GetComponent<RectTransform>(), 0.5f, 1, 0, -706, 940, 90);
            Deco.Halo(_play.targetGraphic, new Color(0.27f, 0.73f, 0.45f, 0.35f), 5f);   // el botón principal destaca

            _status = UiKit.Label(panel.transform, "", 20, UiKit.TextCol, TextAnchor.UpperCenter);
            UiKit.Place(_status.rectTransform, 0.5f, 1, 0, -806, 940, 90);

            var help = UiKit.MakeButton(panel.transform, "¿Cómo se juega?", UiKit.PanelLight, () => { if (OnHelp != null) OnHelp(); }, 20);
            UiKit.Place(help.GetComponent<RectTransform>(), 0.5f, 0, 0, 20, 300, 50);

            _selectedDeck = PlayerPrefs.GetString(KDeck, "AGGRO");
            SetSession(null);
            SetDecks(new List<DeckDto>());
            RefreshBot();
        }

        /// <summary>Etiqueta pequeña encima de un campo. x = centro horizontal respecto al panel, y = distancia desde arriba (negativa).</summary>
        void Caption(Transform parent, string text, float y, float x)
        {
            var l = UiKit.Label(parent, text, 17, UiKit.Muted, TextAnchor.LowerLeft);
            UiKit.Place(l.rectTransform, 0.5f, 1, x, y, 440, 26);
        }

        // ------------------------------------------------------------ sesión y mazos
        /// <summary>username == null: sin sesión (invitado).</summary>
        public void SetSession(string username)
        {
            _session.text = username == null ? "Invitado: solo AGGRO / CONTROL. Para tus mazos, pulsa ENTRAR (o CREAR CUENTA la primera vez)."
                                              : "Sesión iniciada como <b>" + username + "</b>";
            _session.color = username == null ? UiKit.Muted : UiKit.Green;
            _edit.interactable = true;   // si no hay sesión, al pulsar se intenta entrar con el nombre escrito
        }

        public void SetDecks(List<DeckDto> decks)
        {
            _decks = decks ?? new List<DeckDto>();
            if (_selectedDeck != "AGGRO" && _selectedDeck != "CONTROL" && !_decks.Any(d => d.name == _selectedDeck)) _selectedDeck = "AGGRO";
            UiKit.ClearChildren(_deckList);
            AddRow("AGGRO", "AGGRO", "mazo de prueba · 25× Infantería 2/1 + 15× Vanguardia 5/3");
            AddRow("CONTROL", "CONTROL", "mazo de prueba · 20× Muro 4/1 + 20× Chispa Temporal");
            foreach (var d in _decks)
                AddRow(d.name, d.name, d.total + " cartas · " + string.Join(" + ", d.factions.Select(GameRules.FactionName).ToArray()));
            RefreshDeckRows();
        }

        readonly Dictionary<string, Button> _rows = new Dictionary<string, Button>();

        void AddRow(string key, string title, string detail)
        {
            var b = UiKit.MakeButton(_deckList, "<b>" + title + "</b>   <size=15><color=#9AA2B8>" + detail + "</color></size>", UiKit.PanelLight, () => { _selectedDeck = key; RefreshDeckRows(); }, 20);
            UiKit.Height(b.gameObject, 54);
            var t = UiKit.ButtonLabel(b);
            t.alignment = TextAnchor.MiddleLeft;
            t.fontStyle = FontStyle.Normal;
            t.horizontalOverflow = HorizontalWrapMode.Overflow;
            UiKit.Stretch(t.rectTransform, 16, 2, 6, 2);
            _rows[key] = b;
        }

        void RefreshDeckRows()
        {
            var live = new List<string>();
            foreach (var kv in _rows)
            {
                if (kv.Value == null) continue;
                kv.Value.GetComponent<Image>().color = kv.Key == _selectedDeck ? UiKit.Blue : UiKit.PanelLight;
                live.Add(kv.Key);
            }
        }

        void RefreshBot()
        {
            foreach (var kv in _botBtns)
                kv.Value.GetComponent<Image>().color = kv.Key == _botDeck ? UiKit.Blue : UiKit.PanelLight;
        }

        // ------------------------------------------------------------ varios
        public void Remember()
        {
            PlayerPrefs.SetString(KHost, _host.text);
            PlayerPrefs.SetString(KUser, _user.text);
            PlayerPrefs.SetString(KDeck, _selectedDeck);
            PlayerPrefs.SetString(KBot, _botDeck);
            PlayerPrefs.Save();
        }

        public void SetStatus(string text, bool error)
        {
            _status.text = text;
            _status.color = error ? UiKit.Red : UiKit.TextCol;
        }

        public void SetBusy(bool busy)
        {
            _play.interactable = !busy;
            _login.interactable = !busy;
            _register.interactable = !busy;
            _edit.interactable = !busy;
            UiKit.ButtonLabel(_play).text = busy ? "CONECTANDO…" : "JUGAR CONTRA EL BOT";
        }

        public void Show(bool v) { Root.gameObject.SetActive(v); }
    }
}
