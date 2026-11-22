// Pantalla inicial: servidor, nombre (opcional), mazo y botón JUGAR.
using System;
using UnityEngine;
using UnityEngine.UI;

namespace TimewarpTCG
{
    public sealed class MenuView
    {
        public RectTransform Root;
        public Action OnPlay;
        public Action OnHelp;

        readonly InputField _host, _user, _custom;
        readonly Button _play, _aggro, _control;
        readonly Text _status;
        string _deck = "AGGRO";

        const string KHost = "tw_host", KUser = "tw_user", KDeck = "tw_deck", KGuest = "tw_guest";

        public string Host { get { return string.IsNullOrWhiteSpace(_host.text) ? "localhost:8080" : _host.text.Trim(); } }
        public string Username { get { return _user.text == null ? "" : _user.text.Trim(); } }
        public string DeckName { get { return string.IsNullOrWhiteSpace(_custom.text) ? _deck : _custom.text.Trim(); } }
        public bool UsingCustomDeck { get { return !string.IsNullOrWhiteSpace(_custom.text); } }

        /// <summary>Identificador estable de "invitado" (el backend acepta cualquier UUID para los mazos de prueba).</summary>
        public static string GuestId()
        {
            string id = PlayerPrefs.GetString(KGuest, "");
            if (string.IsNullOrEmpty(id)) { id = Guid.NewGuid().ToString(); PlayerPrefs.SetString(KGuest, id); PlayerPrefs.Save(); }
            return id;
        }

        public MenuView(Transform canvas)
        {
            var bg = UiKit.Box(canvas, "Menu", UiKit.Bg, false, true);
            Root = bg.rectTransform;
            UiKit.Stretch(Root);

            var panel = UiKit.Box(Root, "Panel", UiKit.Panel);
            UiKit.Place(panel.rectTransform, 0.5f, 0.5f, 0, 0, 840, 900);

            var title = UiKit.Label(panel.transform, "TIMEWARP TCG", 62, UiKit.Gold, TextAnchor.MiddleCenter, FontStyle.Bold);
            UiKit.Place(title.rectTransform, 0.5f, 1, 0, -22, 780, 80);
            var sub = UiKit.Label(panel.transform, "Partida de prueba contra el bot", 24, UiKit.Muted, TextAnchor.MiddleCenter);
            UiKit.Place(sub.rectTransform, 0.5f, 1, 0, -100, 780, 32);

            _host = Field(panel.transform, "Servidor (backend en marcha)", "localhost:8080", PlayerPrefs.GetString(KHost, "localhost:8080"), 150);
            _user = Field(panel.transform, "Tu nombre (opcional; vacío = invitado, sin cuenta)", "invitado", PlayerPrefs.GetString(KUser, ""), 250);

            var deckLabel = UiKit.Label(panel.transform, "Tu mazo", 20, UiKit.Muted, TextAnchor.LowerLeft);
            UiKit.Place(deckLabel.rectTransform, 0.5f, 1, 0, -352, 760, 28);
            _deck = PlayerPrefs.GetString(KDeck, "AGGRO");
            if (_deck != "AGGRO" && _deck != "CONTROL") _deck = "AGGRO";

            _aggro = UiKit.MakeButton(panel.transform, "AGGRO\n25× Infantería 2/1  ·  15× Vanguardia 5/3", UiKit.PanelLight, () => PickDeck("AGGRO"), 18);
            UiKit.Place(_aggro.GetComponent<RectTransform>(), 0.5f, 1, -190, -386, 370, 84);
            _control = UiKit.MakeButton(panel.transform, "CONTROL\n20× Muro 4/1  ·  20× Chispa Temporal (hechizo)", UiKit.PanelLight, () => PickDeck("CONTROL"), 18);
            UiKit.Place(_control.GetComponent<RectTransform>(), 0.5f, 1, 190, -386, 370, 84);

            _custom = Field(panel.transform, "…o el nombre de un mazo guardado en tu cuenta (opcional)", "p. ej. MiMazo", "", 484);

            _play = UiKit.MakeButton(panel.transform, "JUGAR CONTRA EL BOT", UiKit.Green, () => { if (OnPlay != null) OnPlay(); }, 32);
            UiKit.Place(_play.GetComponent<RectTransform>(), 0.5f, 1, 0, -604, 760, 90);

            _status = UiKit.Label(panel.transform, "", 21, UiKit.TextCol, TextAnchor.UpperCenter);
            UiKit.Place(_status.rectTransform, 0.5f, 1, 0, -712, 780, 100);

            var help = UiKit.MakeButton(panel.transform, "¿Cómo se juega?", UiKit.PanelLight, () => { if (OnHelp != null) OnHelp(); }, 20);
            UiKit.Place(help.GetComponent<RectTransform>(), 0.5f, 0, 0, 24, 300, 50);

            PickDeck(_deck);
        }

        InputField Field(Transform parent, string label, string placeholder, string initial, float top)
        {
            var l = UiKit.Label(parent, label, 20, UiKit.Muted, TextAnchor.LowerLeft);
            UiKit.Place(l.rectTransform, 0.5f, 1, 0, -top, 760, 28);
            var input = UiKit.MakeInput(parent, placeholder, initial, 24);
            UiKit.Place(input.GetComponent<RectTransform>(), 0.5f, 1, 0, -(top + 32), 760, 52);
            return input;
        }

        void PickDeck(string d)
        {
            _deck = d;
            _aggro.GetComponent<Image>().color = d == "AGGRO" ? UiKit.Blue : UiKit.PanelLight;
            _control.GetComponent<Image>().color = d == "CONTROL" ? UiKit.Blue : UiKit.PanelLight;
        }

        public void Remember()
        {
            PlayerPrefs.SetString(KHost, _host.text);
            PlayerPrefs.SetString(KUser, _user.text);
            PlayerPrefs.SetString(KDeck, _deck);
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
            UiKit.ButtonLabel(_play).text = busy ? "CONECTANDO…" : "JUGAR CONTRA EL BOT";
        }

        public void Show(bool v) { Root.gameObject.SetActive(v); }
    }
}
