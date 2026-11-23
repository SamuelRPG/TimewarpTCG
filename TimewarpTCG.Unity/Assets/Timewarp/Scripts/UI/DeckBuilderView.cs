// EDITOR DE MAZOS. Muestra el catálogo real (viene de la base de datos vía GET /api/menu/cards), deja armar un mazo
// respetando las reglas (40 cartas exactas, máx. 3 copias, máx. 2 facciones: ver DeckDraft) y lo guarda en tu cuenta
// (POST /api/menu/decks). Clic izquierdo en una carta = añadir una copia; clic derecho = quitar una.
using System;
using System.Collections.Generic;
using System.Linq;
using UnityEngine;
using UnityEngine.UI;

namespace TimewarpTCG
{
    public sealed class DeckBuilderView
    {
        public RectTransform Root;
        /// <summary>Se llama al salir, con la lista de mazos actualizada.</summary>
        public Action<List<DeckDto>> OnClose;

        // datos
        string _host, _userId;
        List<CardDto> _catalog = new List<CardDto>();
        List<DeckDto> _decks = new List<DeckDto>();
        DeckDraft _draft;
        bool _busy, _dirty;
        string _pendingConfirm; float _confirmUntil;

        // filtros
        string _factionFilter = "";     // "" = todas
        string _typeFilter = "";        // "" | UNIT | SPELL
        string _search = "";

        // widgets
        InputField _nameInput, _searchInput;
        Text _counter, _stats, _validation, _inspector, _toastText;
        RectTransform _grid, _deckList, _myDecks, _curve, _toast;
        Button _saveBtn, _deleteBtn, _exitBtn;
        readonly Dictionary<string, RectTransform> _cells = new Dictionary<string, RectTransform>();
        readonly Dictionary<string, Button> _factionBtns = new Dictionary<string, Button>();
        readonly Dictionary<string, Button> _typeBtns = new Dictionary<string, Button>();
        float _toastUntil;
        bool _suppressName;

        static readonly string[] Factions = { "", "ANCIENT_EGYPT", "ROMANS", "STEAMPUNK", "GLITCHPOP" };
        static readonly string[] FactionLabels = { "TODAS", "EGIPTO", "ROMANOS", "STEAMPUNK", "GLITCHPOP" };

        public DeckBuilderView(Transform canvas)
        {
            var bg = UiKit.Box(canvas, "DeckBuilder", UiKit.Bg, false, true);
            Root = bg.rectTransform;
            UiKit.Stretch(Root);
            Deco.Backdrop(Root, new Color(0.10f, 0.14f, 0.28f), new Color(0.02f, 0.03f, 0.07f), new Color(0.25f, 0.42f, 1f, 0.22f), new Color(0.70f, 0.35f, 1f, 0.16f));
            Root.gameObject.SetActive(false);   // oculta desde el principio: si algo fallase al construir, no queda una pantalla a medias
            BuildHeader();
            BuildFilters();
            BuildGrid();
            BuildRightColumn();
            BuildToast();
            Root.gameObject.SetActive(false);
        }

        // ============================================================ construcción
        void BuildHeader()
        {
            var bar = UiKit.Box(UiKit.Region(Root, "BuilderHeader", 0, 0, 1, 0.09f, 2), "Header", UiKit.Panel);
            UiKit.Stretch(bar.rectTransform);
            var title = UiKit.Label(bar.transform, "EDITOR DE MAZOS", 34, UiKit.Gold, TextAnchor.MiddleLeft, FontStyle.Bold);
            UiKit.Place(title.rectTransform, 0, 0.5f, 20, 0, 420, 50);
            Deco.GoldTitle(title);

            var lbl = UiKit.Label(bar.transform, "Nombre:", 20, UiKit.Muted, TextAnchor.MiddleRight);
            UiKit.Place(lbl.rectTransform, 0, 0.5f, 450, 0, 100, 40);
            _nameInput = UiKit.MakeInput(bar.transform, "Nombre del mazo", "", 22);
            UiKit.Place(_nameInput.GetComponent<RectTransform>(), 0, 0.5f, 560, 0, 380, 48);
            _nameInput.onValueChanged.AddListener(v => { if (_suppressName || _draft == null) return; _draft.Name = v; _dirty = true; RefreshStatus(); });

            var nu = UiKit.MakeButton(bar.transform, "NUEVO", UiKit.PanelLight, OnNew, 20);
            UiKit.Place(nu.GetComponent<RectTransform>(), 1, 0.5f, -700, 0, 140, 50);
            _saveBtn = UiKit.MakeButton(bar.transform, "GUARDAR", UiKit.Green, OnSave, 22);
            UiKit.Place(_saveBtn.GetComponent<RectTransform>(), 1, 0.5f, -550, 0, 180, 50);
            _deleteBtn = UiKit.MakeButton(bar.transform, "BORRAR", new Color32(150, 55, 55, 255), OnDelete, 20);
            UiKit.Place(_deleteBtn.GetComponent<RectTransform>(), 1, 0.5f, -350, 0, 150, 50);
            _exitBtn = UiKit.MakeButton(bar.transform, "SALIR", UiKit.Blue, OnExit, 20);
            UiKit.Place(_exitBtn.GetComponent<RectTransform>(), 1, 0.5f, -180, 0, 150, 50);
        }

        void BuildFilters()
        {
            var bar = UiKit.Box(UiKit.Region(Root, "FiltersR", 0, 0.09f, 0.645f, 0.165f, 2), "Filters", UiKit.Panel);
            UiKit.Stretch(bar.rectTransform);
            for (int i = 0; i < Factions.Length; i++)
            {
                string f = Factions[i];
                var b = UiKit.MakeButton(bar.transform, FactionLabels[i], UiKit.PanelLight, () => { _factionFilter = f; RefreshFilterButtons(); RebuildGrid(); }, 16);
                UiKit.Place(b.GetComponent<RectTransform>(), 0, 0.5f, 10 + i * 124, 0, 118, 44);
                if (f.Length > 0) b.GetComponent<Image>().color = UiKit.FactionColor(f);
                _factionBtns[f] = b;
            }
            string[] ts = { "", "UNIT", "SPELL" }; string[] tl = { "TODO", "UNIDADES", "HECHIZOS" };
            for (int i = 0; i < ts.Length; i++)
            {
                string t = ts[i];
                var b = UiKit.MakeButton(bar.transform, tl[i], UiKit.PanelLight, () => { _typeFilter = t; RefreshFilterButtons(); RebuildGrid(); }, 16);
                UiKit.Place(b.GetComponent<RectTransform>(), 0, 0.5f, 650 + i * 118, 0, 112, 44);
                _typeBtns[t] = b;
            }
            _searchInput = UiKit.MakeInput(bar.transform, "Buscar…", "", 18);
            UiKit.Place(_searchInput.GetComponent<RectTransform>(), 1, 0.5f, -10, 0, 210, 44);
            _searchInput.onValueChanged.AddListener(v => { _search = (v ?? "").Trim(); RebuildGrid(); });
        }

        void BuildGrid()
        {
            RectTransform content;
            var scroll = UiKit.MakeScroll(UiKit.Region(Root, "GridR", 0, 0.165f, 0.645f, 1f, 2), UiKit.Panel, out content, "CardGrid", false);
            UiKit.Stretch(scroll.GetComponent<RectTransform>());
            var gl = content.gameObject.AddComponent<GridLayoutGroup>();
            gl.cellSize = new Vector2(150, 210);
            gl.spacing = new Vector2(12, 14);
            gl.padding = new RectOffset(14, 14, 12, 12);
            gl.constraint = GridLayoutGroup.Constraint.FixedColumnCount;
            gl.constraintCount = 7;
            gl.childAlignment = TextAnchor.UpperLeft;
            _grid = content;
        }

        void BuildRightColumn()
        {
            // mis mazos
            var my = UiKit.Box(UiKit.Region(Root, "MyDecksR", 0.645f, 0.09f, 1f, 0.215f, 2), "MyDecks", UiKit.Panel);
            UiKit.Stretch(my.rectTransform);
            var myTitle = UiKit.Label(my.transform, "MIS MAZOS (clic para editar)", 14, UiKit.Muted, TextAnchor.UpperLeft, FontStyle.Bold);
            UiKit.Place(myTitle.rectTransform, 0, 1, 10, -4, 500, 18);
            RectTransform myContent;
            var myScroll = UiKit.MakeScroll(my.transform, new Color(0, 0, 0, 0.25f), out myContent, "MyDecksScroll");
            var mrt = myScroll.GetComponent<RectTransform>();
            mrt.anchorMin = Vector2.zero; mrt.anchorMax = Vector2.one; mrt.offsetMin = new Vector2(6, 6); mrt.offsetMax = new Vector2(-6, -24);
            _myDecks = myContent;

            // contador y estado
            var st = UiKit.Box(UiKit.Region(Root, "StatsR", 0.645f, 0.215f, 1f, 0.355f, 2), "Stats", UiKit.Panel);
            UiKit.Stretch(st.rectTransform);
            _counter = UiKit.Label(st.transform, "0 / 40", 60, UiKit.Gold, TextAnchor.MiddleLeft, FontStyle.Bold);
            UiKit.Place(_counter.rectTransform, 0, 1, 16, -6, 280, 76);
            _stats = UiKit.Label(st.transform, "", 17, UiKit.TextCol, TextAnchor.UpperLeft);
            UiKit.Place(_stats.rectTransform, 1, 1, -10, -8, 480, 70);
            _validation = UiKit.Label(st.transform, "", 17, UiKit.Red, TextAnchor.UpperLeft, FontStyle.Bold);
            UiKit.Place(_validation.rectTransform, 0, 0, 16, 6, 760, 44);

            // curva de coste
            var cv = UiKit.Box(UiKit.Region(Root, "CurveR", 0.645f, 0.355f, 1f, 0.455f, 2), "Curve", UiKit.Panel);
            UiKit.Stretch(cv.rectTransform);
            var cvTitle = UiKit.Label(cv.transform, "CURVA DE COSTE", 13, UiKit.Muted, TextAnchor.UpperLeft, FontStyle.Bold);
            UiKit.Place(cvTitle.rectTransform, 0, 1, 10, -3, 300, 16);
            _curve = UiKit.NewRect("Bars", cv.transform);
            UiKit.Stretch(_curve, 10, 20, 10, 4);

            // lista del mazo
            var dl = UiKit.Box(UiKit.Region(Root, "DeckListR", 0.645f, 0.455f, 1f, 0.86f, 2), "DeckList", UiKit.Panel);
            UiKit.Stretch(dl.rectTransform);
            RectTransform lc;
            var ls = UiKit.MakeScroll(dl.transform, new Color(0, 0, 0, 0.2f), out lc, "DeckScroll");
            var lrt = ls.GetComponent<RectTransform>();
            lrt.anchorMin = Vector2.zero; lrt.anchorMax = Vector2.one; lrt.offsetMin = new Vector2(6, 6); lrt.offsetMax = new Vector2(-6, -6);
            _deckList = lc;

            // inspector
            var insp = UiKit.Box(UiKit.Region(Root, "InspR", 0.645f, 0.86f, 1f, 1f, 2), "Inspector", UiKit.Panel);
            UiKit.Stretch(insp.rectTransform);
            _inspector = UiKit.Label(insp.transform, CardView.Describe(null), 14, UiKit.TextCol, TextAnchor.UpperLeft);
            UiKit.Stretch(_inspector.rectTransform, 10, 6, 10, 6);
        }

        void BuildToast()
        {
            _toast = UiKit.Box(UiKit.Region(Root, "BuilderToast", 0.22f, 0.095f, 0.62f, 0.15f), "Toast", new Color32(120, 30, 30, 240)).rectTransform;
            UiKit.Stretch(_toast);
            _toastText = UiKit.Label(_toast, "", 20, Color.white, TextAnchor.MiddleCenter, FontStyle.Bold);
            UiKit.Stretch(_toastText.rectTransform, 12, 4, 12, 4);
            _toast.gameObject.SetActive(false);
        }

        // ============================================================ apertura
        public void Open(string host, string userId, string username, List<CardDto> catalog, List<DeckDto> decks, string deckToLoad)
        {
            _host = host; _userId = userId;
            _catalog = catalog ?? new List<CardDto>();
            _decks = decks ?? new List<DeckDto>();
            _draft = new DeckDraft(_catalog);
            _factionFilter = ""; _typeFilter = ""; _search = "";
            _suppressName = true; _searchInput.text = ""; _nameInput.text = ""; _suppressName = false;
            _dirty = false; _pendingConfirm = null; _busy = false;

            var d = deckToLoad == null ? null : _decks.FirstOrDefault(x => string.Equals(x.name, deckToLoad, StringComparison.OrdinalIgnoreCase));
            if (d != null) LoadDeck(d);

            Root.gameObject.SetActive(true);
            Root.SetAsLastSibling();
            RefreshFilterButtons();
            RebuildGrid();
            RefreshAll();
            if (_catalog.Count == 0) ShowToast("El catálogo está vacío. Carga las cartas en la base de datos (docs/supabase/cartas.sql).", true);
        }

        public void Tick()
        {
            if (_toast.gameObject.activeSelf && Time.unscaledTime > _toastUntil) _toast.gameObject.SetActive(false);
            if (_pendingConfirm != null && Time.unscaledTime > _confirmUntil) { _pendingConfirm = null; RefreshButtons(); }
        }

        // ============================================================ acciones
        void LoadDeck(DeckDto d)
        {
            int skipped = _draft.Load(d);
            _suppressName = true; _nameInput.text = _draft.Name; _suppressName = false;
            _dirty = false;
            if (skipped > 0) ShowToast("Se han omitido " + skipped + " carta(s) que ya no existen en el catálogo.", true);
        }

        void OnCardAdd(CardDto c)
        {
            string reason;
            bool beforeMulti = _draft.Factions().Count >= DeckDraft.MaxFactions;
            if (!_draft.Add(c, out reason)) { ShowToast(reason, true); return; }
            _dirty = true;
            bool allDim = beforeMulti != (_draft.Factions().Count >= DeckDraft.MaxFactions) || _draft.Total >= DeckDraft.Size;
            RefreshCells(allDim, c.id);
            RefreshAll();
        }

        void OnCardRemove(string id)
        {
            bool beforeMulti = _draft.Factions().Count >= DeckDraft.MaxFactions;
            bool wasFull = _draft.Total >= DeckDraft.Size;
            if (!_draft.Remove(id)) return;
            _dirty = true;
            bool allDim = beforeMulti != (_draft.Factions().Count >= DeckDraft.MaxFactions) || wasFull;
            RefreshCells(allDim, id);
            RefreshAll();
        }

        void OnNew()
        {
            if (_dirty && !Confirm("new", "Tienes cambios sin guardar. Pulsa NUEVO otra vez para descartarlos.")) return;
            _draft.Clear();
            _suppressName = true; _nameInput.text = ""; _suppressName = false;
            _dirty = false;
            RebuildGrid(); RefreshAll();
        }

        async void OnSave()
        {
            if (_busy) return;
            string problem = _draft.Validate();
            if (problem != null) { ShowToast(problem, true); return; }
            string name = _draft.Name.Trim();
            bool existsOther = _decks.Any(d => string.Equals(d.name, name, StringComparison.OrdinalIgnoreCase)) &&
                               !string.Equals(_draft.SavedAs, name, StringComparison.OrdinalIgnoreCase);
            if (existsOther && !Confirm("overwrite", "Ya tienes un mazo llamado «" + name + "». Pulsa GUARDAR otra vez para sustituirlo.")) return;

            _busy = true; RefreshButtons();
            try
            {
                var saved = await RestApi.SaveDeckAsync(_host, name, _draft.ToEntries());
                _decks = await RestApi.GetDecksAsync(_host);
                _draft.SavedAs = saved.name; _draft.Name = saved.name;
                _dirty = false;
                ShowToast("Mazo «" + saved.name + "» guardado (" + saved.total + " cartas).", false);
            }
            catch (Exception e) { ShowToast(e.Message, true); }
            _busy = false;
            RefreshAll();
        }

        async void OnDelete()
        {
            if (_busy || string.IsNullOrEmpty(_draft.SavedAs)) { ShowToast("Este mazo aún no está guardado.", true); return; }
            if (!Confirm("delete", "¿Borrar «" + _draft.SavedAs + "»? Pulsa BORRAR otra vez para confirmar.")) return;
            _busy = true; RefreshButtons();
            try
            {
                string n = _draft.SavedAs;
                await RestApi.DeleteDeckAsync(_host, n);
                _decks = await RestApi.GetDecksAsync(_host);
                _draft.Clear();
                _suppressName = true; _nameInput.text = ""; _suppressName = false;
                _dirty = false;
                ShowToast("Mazo «" + n + "» borrado.", false);
            }
            catch (Exception e) { ShowToast(e.Message, true); }
            _busy = false;
            RebuildGrid(); RefreshAll();
        }

        void OnExit()
        {
            if (_dirty && !Confirm("exit", "Tienes cambios sin guardar. Pulsa SALIR otra vez para descartarlos.")) return;
            Root.gameObject.SetActive(false);
            if (OnClose != null) OnClose(_decks);
        }

        /// <summary>Confirmación en dos pasos: la primera pulsación avisa, la segunda (en 4 s) ejecuta.</summary>
        bool Confirm(string key, string message)
        {
            if (_pendingConfirm == key && Time.unscaledTime <= _confirmUntil) { _pendingConfirm = null; RefreshButtons(); return true; }
            _pendingConfirm = key; _confirmUntil = Time.unscaledTime + 4f;
            ShowToast(message, true);
            RefreshButtons();
            return false;
        }

        // ============================================================ dibujo
        void RefreshFilterButtons()
        {
            foreach (var kv in _factionBtns)
            {
                var img = kv.Value.GetComponent<Image>();
                Color baseC = kv.Key.Length == 0 ? UiKit.PanelLight : UiKit.FactionColor(kv.Key);
                img.color = kv.Key == _factionFilter ? baseC : Color.Lerp(baseC, new Color(0.1f, 0.12f, 0.2f), 0.6f);
            }
            foreach (var kv in _typeBtns)
                kv.Value.GetComponent<Image>().color = kv.Key == _typeFilter ? UiKit.Blue : UiKit.PanelLight;
        }

        bool Passes(CardDto c)
        {
            if (_factionFilter.Length > 0 && c.faction != _factionFilter) return false;
            if (_typeFilter.Length > 0 && c.cardType != _typeFilter) return false;
            if (_search.Length > 0)
            {
                string q = _search.ToLowerInvariant();
                string hay = ((c.name ?? "") + " " + (c.description ?? "") + " " + string.Join(" ", (c.keywords ?? new List<string>()).ToArray())).ToLowerInvariant();
                if (!hay.Contains(q)) return false;
            }
            return true;
        }

        void RebuildGrid()
        {
            if (_grid == null) return;
            UiKit.ClearChildren(_grid);
            _cells.Clear();
            var list = _catalog.Where(Passes).OrderBy(c => c.faction).ThenBy(c => c.currentCost).ThenBy(c => c.name).ToList();
            foreach (var c in list)
            {
                var cell = UiKit.NewRect("Cell " + c.id, _grid);
                _cells[c.id] = cell;
                DrawCell(c, cell);
            }
        }

        void RefreshCells(bool all, string id)
        {
            foreach (var kv in _cells)
            {
                if (!all && kv.Key != id) continue;
                var c = _catalog.FirstOrDefault(x => x.id == kv.Key);
                if (c != null) DrawCell(c, kv.Value);
            }
        }

        void DrawCell(CardDto c, RectTransform cell)
        {
            UiKit.ClearChildren(cell);
            string reason;
            var st = new CardStyle();
            st.count = _draft != null ? _draft.CountOf(c.id) : 0;
            st.dim = _draft != null && !_draft.CanAdd(c, out reason);
            var view = CardView.Create(cell, c, 150, 210, st, () => OnCardAdd(c), () => Inspect(c), null);
            UiKit.Stretch(view.Root);
            var cl = view.Root.GetComponent<Clickable>();
            if (cl != null) cl.OnRightClick = () => OnCardRemove(c.id);
        }

        void RefreshAll()
        {
            RefreshStatus();
            RefreshDeckList();
            RefreshCurve();
            RefreshMyDecks();
            RefreshButtons();
        }

        void RefreshStatus()
        {
            if (_draft == null) return;
            int total = _draft.Total;
            _counter.text = total + " / " + DeckDraft.Size;
            _counter.color = total == DeckDraft.Size ? UiKit.Green : (total > DeckDraft.Size ? UiKit.Red : UiKit.Gold);
            var fs = _draft.Factions();
            _stats.text = "Unidades: <b>" + _draft.Units() + "</b>   Hechizos: <b>" + _draft.Spells() + "</b>\n"
                        + "Facciones (" + fs.Count + "/" + DeckDraft.MaxFactions + "): <b>" + (fs.Count == 0 ? "—" : string.Join(" + ", fs.Select(GameRules.FactionName).ToArray())) + "</b>\n"
                        + "<color=#9AA2B8>Máx. " + DeckDraft.MaxCopies + " copias por carta</color>";
            string problem = _draft.Validate();
            _validation.text = problem ?? "Mazo válido: ya puedes guardarlo.";
            _validation.color = problem == null ? UiKit.Green : UiKit.Red;
        }

        void RefreshButtons()
        {
            if (_draft == null) return;
            bool valid = _draft.Validate() == null;
            _saveBtn.interactable = valid && !_busy;
            UiKit.ButtonLabel(_saveBtn).text = _pendingConfirm == "overwrite" ? "¿SUSTITUIR?" : (_busy ? "GUARDANDO…" : "GUARDAR");
            _deleteBtn.interactable = !_busy && !string.IsNullOrEmpty(_draft.SavedAs);
            UiKit.ButtonLabel(_deleteBtn).text = _pendingConfirm == "delete" ? "¿SEGURO?" : "BORRAR";
            UiKit.ButtonLabel(_exitBtn).text = _pendingConfirm == "exit" ? "¿DESCARTAR?" : "SALIR";
        }

        void RefreshDeckList()
        {
            UiKit.ClearChildren(_deckList);
            if (_draft == null) return;
            foreach (var kv in _draft.Cards())
            {
                var c = kv.Key; int n = kv.Value;
                var row = UiKit.Box(_deckList, "Row " + c.name, new Color(1, 1, 1, 0.07f));
                UiKit.Height(row.gameObject, 42);
                UiKit.OnClick(row.gameObject, null, () => Inspect(c), null);

                var strip = UiKit.Box(row.transform, "Strip", UiKit.FactionColor(c.faction), false, false);
                strip.rectTransform.anchorMin = new Vector2(0, 0); strip.rectTransform.anchorMax = new Vector2(0, 1);
                strip.rectTransform.sizeDelta = new Vector2(6, 0); strip.rectTransform.pivot = new Vector2(0, 0.5f);
                strip.rectTransform.anchoredPosition = Vector2.zero;

                var cost = UiKit.CircleImage(row.transform, "Cost", new Color32(60, 120, 230, 255));
                UiKit.Place(cost.rectTransform, 0, 0.5f, 18, 0, 28, 28);
                var ct = UiKit.Label(cost.transform, c.currentCost.ToString(), 16, Color.white, TextAnchor.MiddleCenter, FontStyle.Bold);
                UiKit.Stretch(ct.rectTransform);

                var nm = UiKit.Label(row.transform, c.name + (GameRules.IsSpell(c) ? "  <color=#46B973>(" + c.spellSpeed + ")</color>" : "  <color=#EB7D28>" + c.currentDrive + "</color>"), 18, UiKit.TextCol, TextAnchor.MiddleLeft);
                UiKit.Place(nm.rectTransform, 0, 0.5f, 56, 0, 300, 36);

                var cnt = UiKit.Label(row.transform, "x" + n, 22, UiKit.Gold, TextAnchor.MiddleRight, FontStyle.Bold);
                UiKit.Place(cnt.rectTransform, 1, 0.5f, -108, 0, 50, 36);

                var minus = UiKit.MakeButton(row.transform, "−", UiKit.PanelLight, () => OnCardRemove(c.id), 22);
                UiKit.Place(minus.GetComponent<RectTransform>(), 1, 0.5f, -62, 0, 40, 34);
                var plus = UiKit.MakeButton(row.transform, "+", UiKit.PanelLight, () => OnCardAdd(c), 22);
                UiKit.Place(plus.GetComponent<RectTransform>(), 1, 0.5f, -16, 0, 40, 34);
            }
        }

        void RefreshCurve()
        {
            UiKit.ClearChildren(_curve);
            if (_draft == null) return;
            var curve = _draft.CostCurve();
            int max = Mathf.Max(1, curve.Max());
            for (int i = 0; i < curve.Length; i++)
            {
                float x0 = i / 8f, x1 = (i + 1) / 8f;
                var col = UiKit.NewRect("Col" + i, _curve);
                col.anchorMin = new Vector2(x0, 0); col.anchorMax = new Vector2(x1, 1); col.offsetMin = new Vector2(4, 0); col.offsetMax = new Vector2(-4, 0);
                float h = curve[i] / (float)max;
                var bar = UiKit.Box(col, "Bar", UiKit.Blue, true, false);
                bar.rectTransform.anchorMin = new Vector2(0, 0); bar.rectTransform.anchorMax = new Vector2(1, 0);
                bar.rectTransform.pivot = new Vector2(0.5f, 0);
                bar.rectTransform.anchoredPosition = new Vector2(0, 16);
                bar.rectTransform.sizeDelta = new Vector2(0, Mathf.Max(2, h * 46));
                var n = UiKit.Label(col, curve[i] > 0 ? curve[i].ToString() : "", 13, UiKit.TextCol, TextAnchor.LowerCenter, FontStyle.Bold);
                n.rectTransform.anchorMin = Vector2.zero; n.rectTransform.anchorMax = Vector2.one;
                n.rectTransform.offsetMin = new Vector2(0, 16 + Mathf.Max(2, h * 46)); n.rectTransform.offsetMax = new Vector2(0, 0);
                var lbl = UiKit.Label(col, i == 7 ? "7+" : i.ToString(), 13, UiKit.Muted, TextAnchor.LowerCenter);
                lbl.rectTransform.anchorMin = new Vector2(0, 0); lbl.rectTransform.anchorMax = new Vector2(1, 0);
                lbl.rectTransform.pivot = new Vector2(0.5f, 0); lbl.rectTransform.sizeDelta = new Vector2(0, 16); lbl.rectTransform.anchoredPosition = Vector2.zero;
            }
        }

        void RefreshMyDecks()
        {
            UiKit.ClearChildren(_myDecks);
            if (_decks.Count == 0)
            {
                var none = UiKit.Label(_myDecks, "Aún no tienes mazos guardados.", 15, UiKit.Muted, TextAnchor.MiddleLeft);
                UiKit.Height(none.gameObject, 30);
                return;
            }
            foreach (var d in _decks)
            {
                var deck = d;
                bool current = _draft != null && string.Equals(_draft.SavedAs, d.name, StringComparison.OrdinalIgnoreCase);
                var b = UiKit.MakeButton(_myDecks, d.name + "   <size=14>" + d.total + " cartas · " + string.Join(" + ", d.factions.Select(GameRules.FactionName).ToArray()) + "</size>",
                    current ? UiKit.Blue : UiKit.PanelLight, () =>
                    {
                        if (_dirty && !Confirm("load", "Tienes cambios sin guardar. Pulsa otra vez el mazo para descartarlos y cargarlo.")) return;
                        LoadDeck(deck); RebuildGrid(); RefreshAll();
                    }, 17);
                UiKit.Height(b.gameObject, 34);
                UiKit.ButtonLabel(b).alignment = TextAnchor.MiddleLeft;
                UiKit.ButtonLabel(b).horizontalOverflow = HorizontalWrapMode.Overflow;
            }
        }

        void Inspect(CardDto c) { _inspector.text = CardView.Describe(c); }

        void ShowToast(string msg, bool error)
        {
            _toastText.text = msg;
            _toast.GetComponent<Image>().color = error ? new Color32(130, 32, 32, 245) : new Color32(30, 100, 60, 245);
            _toast.gameObject.SetActive(true);
            _toast.SetAsLastSibling();
            _toastUntil = Time.unscaledTime + (error ? 5f : 3f);
        }
    }
}
