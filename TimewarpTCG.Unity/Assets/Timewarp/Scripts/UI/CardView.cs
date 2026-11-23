// Dibuja una carta (unidad o hechizo) con uGUI, con estilo de juego de cartas digital:
// ilustración procedural arriba, banda con el nombre, gema de coste (azul) y de Drive (naranja), texto y keywords.
// El tamaño de las fuentes escala con el de la carta. En tamaños pequeños (tablero) se usa un modo COMPACTO sin descripción:
// los detalles salen en el panel "Inspector" al pasar el ratón.
using System;
using UnityEngine;
using UnityEngine.UI;

namespace TimewarpTCG
{
    public struct CardStyle
    {
        public bool selected;      // carta seleccionada (borde dorado)
        public bool dim;           // pista visual: ahora mismo no parece jugable
        public bool pending;       // unidad movida este turno, sin confirmar
        public bool faceDown;      // dorso (mano del rival)
        public bool hasOutline;    // borde de resalte (p. ej. posible objetivo)
        public Color outline;
        public string badge;       // etiqueta pequeña arriba a la derecha ("OBJ", "MOV", "JUGAR", "MOVER")
        public Color badgeColor;   // color de la etiqueta (alfa 0 = dorado por defecto)
        public string reason;      // si dim: motivo corto que se escribe sobre la carta ("SIN RECURSO", "ESPERA"...)
        public bool costRed;       // el coste se pinta en rojo: no te llega el recurso
        public int count;          // si > 0, muestra "x N" (editor de mazos)
    }

    public sealed class CardView
    {
        public RectTransform Root;
        public CardDto Card;

        public static CardView Create(Transform parent, CardDto c, float w, float h, CardStyle st,
                                      Action onClick, Action onEnter, Action onExit)
        {
            var view = new CardView { Card = c };

            Color faction = c != null ? UiKit.FactionColor(c.faction) : UiKit.PanelLight;
            var border = UiKit.Box(parent, c != null ? "Card " + c.name : "CardBack", st.faceDown ? UiKit.PanelLight : faction);
            view.Root = border.rectTransform;
            UiKit.Size(border.gameObject, w, h);
            border.gameObject.AddComponent<CanvasGroup>();
            UiKit.OnClick(border.gameObject, onClick, onEnter, onExit);
            Deco.DropShadow(border, 0.45f, 5f);      // da relieve: la carta "se despega" del tablero

            if (st.selected) UiKit.AddOutline(border, UiKit.Gold, 4f);
            else if (st.hasOutline) UiKit.AddOutline(border, st.outline, 3f);

            if (st.faceDown || c == null)
            {
                var q = UiKit.Label(border.transform, "?", Mathf.RoundToInt(36 * h / 170f), UiKit.Muted, TextAnchor.MiddleCenter, FontStyle.Bold);
                UiKit.Stretch(q.rectTransform);
                return view;
            }

            float k = Mathf.Min(w / 140f, h / 196f);
            bool compact = w < 124f;
            int F(float s) { return Mathf.Max(8, Mathf.RoundToInt(s * k)); }
            bool unit = GameRules.IsUnit(c);

            var inner = UiKit.Box(border.transform, "Inner", new Color32(17, 21, 36, 255), true, false);
            UiKit.Stretch(inner.rectTransform, 3, 3, 3, 3);
            float innerW = w - 6, pad = 5 * k;

            // ---- ilustración
            float artH = compact ? (h - 6) * 0.50f : (h - 6) * 0.43f;
            var art = UiKit.Box(inner.transform, "Art", Color.white, false, false);
            art.sprite = ArtGen.Get(c.faction, c.name);
            art.type = Image.Type.Simple;
            art.preserveAspect = false;
            UiKit.Place(art.rectTransform, 0, 1, pad, -pad, innerW - 2 * pad, artH);
            Deco.Safe(() =>
            {
                // brillo suave sobre la mitad alta de la ilustración (acabado "carta brillante")
                var gloss = UiKit.Box(inner.transform, "Gloss", new Color(1f, 1f, 1f, 0.20f), false, false);
                UiKit.Place(gloss.rectTransform, 0, 1, pad, -pad, innerW - 2 * pad, artH * 0.55f);
                Deco.Gradient(gloss, Color.white, new Color(1f, 1f, 1f, 0f));
            }, "brillo de carta");

            // ---- banda del nombre (sobre el borde inferior de la ilustración)
            float bandH = 24 * k;
            var band = UiKit.Box(inner.transform, "NameBand", new Color(0.04f, 0.05f, 0.1f, 0.82f), false, false);
            UiKit.Place(band.rectTransform, 0, 1, pad, -(pad + artH - bandH), innerW - 2 * pad, bandH);
            var name = UiKit.Label(band.transform, c.name, F(13), Color.white, TextAnchor.MiddleCenter, FontStyle.Bold);
            UiKit.Stretch(name.rectTransform, 22 * k, 0, 22 * k, 0);
            UiKit.BestFit(name, 8, F(13));

            // ---- gema de coste
            float gem = 36 * k;
            var cost = UiKit.CircleImage(inner.transform, "Cost", st.costRed ? new Color32(205, 60, 60, 255) : new Color32(60, 120, 230, 255));
            UiKit.Place(cost.rectTransform, 0, 1, 1 * k, -1 * k, gem, gem);
            UiKit.AddOutline(cost, new Color(1, 1, 1, 0.55f), 1.5f);
            var costTxt = UiKit.Label(cost.transform, c.currentCost.ToString(), F(19), Color.white, TextAnchor.MiddleCenter, FontStyle.Bold);
            UiKit.Stretch(costTxt.rectTransform);

            // ---- tipo y facción
            float y = pad + artH + 3 * k;
            string typeLine = unit ? "UNIDAD" : "HECHIZO" + (string.IsNullOrEmpty(c.spellSpeed) || c.spellSpeed == "NOT_A_SPELL" ? "" : " " + c.spellSpeed);
            if (!compact) typeLine += "  ·  " + GameRules.FactionName(c.faction);
            var type = UiKit.Label(inner.transform, typeLine, F(9), UiKit.Muted, TextAnchor.MiddleLeft, FontStyle.Bold);
            UiKit.Place(type.rectTransform, 0, 1, pad + 2 * k, -y, innerW - 2 * pad, 13 * k);
            y += 14 * k;

            // ---- descripción (solo en tamaño normal)
            float bottomReserve = 40 * k;
            if (!compact)
            {
                var box = UiKit.Box(inner.transform, "DescBox", new Color(1, 1, 1, 0.06f), true, false);
                float descH = Mathf.Max(12, h - 6 - y - bottomReserve);
                UiKit.Place(box.rectTransform, 0, 1, pad, -y, innerW - 2 * pad, descH);
                var desc = UiKit.Label(box.transform, c.description ?? "", F(10.5f), new Color(0.88f, 0.9f, 0.96f), TextAnchor.UpperLeft);
                UiKit.Stretch(desc.rectTransform, 4 * k, 3 * k, 4 * k, 3 * k);
            }

            // ---- keywords como etiquetas
            if (c.keywords != null && c.keywords.Count > 0)
            {
                float x = pad;
                int shown = 0;
                foreach (var kw in c.keywords)
                {
                    if (compact && shown >= 1) break;
                    string label = KeywordShort(kw, compact);
                    float tw = (label.Length * 6.3f + 10) * k;
                    var tag = UiKit.Box(inner.transform, "Tag", new Color32(255, 196, 61, 235), true, false);
                    UiKit.Place(tag.rectTransform, 0, 0, x, 6 * k, tw, 15 * k);
                    var tt = UiKit.Label(tag.transform, label, F(8.5f), Color.black, TextAnchor.MiddleCenter, FontStyle.Bold);
                    UiKit.Stretch(tt.rectTransform);
                    x += tw + 3 * k;
                    shown++;
                }
            }

            // ---- Drive (unidades) o velocidad (hechizos)
            if (unit)
            {
                float dg = 42 * k;
                var d = UiKit.CircleImage(inner.transform, "Drive", new Color32(235, 125, 40, 255));
                UiKit.Place(d.rectTransform, 1, 0, -1 * k, 1 * k, dg, dg);
                UiKit.AddOutline(d, new Color(1, 1, 1, 0.55f), 1.5f);
                var dt = UiKit.Label(d.transform, c.currentDrive.ToString(), F(21), Color.white, TextAnchor.MiddleCenter, FontStyle.Bold);
                UiKit.Stretch(dt.rectTransform);
            }
            else if (!string.IsNullOrEmpty(c.spellSpeed) && c.spellSpeed != "NOT_A_SPELL")
            {
                var sp = UiKit.Box(inner.transform, "Speed", SpeedColor(c.spellSpeed), true, false);
                UiKit.Place(sp.rectTransform, 1, 0, -pad, 6 * k, 56 * k, 18 * k);
                var spt = UiKit.Label(sp.transform, c.spellSpeed, F(10), Color.white, TextAnchor.MiddleCenter, FontStyle.Bold);
                UiKit.Stretch(spt.rectTransform);
            }

            // ---- estados
            if (c.isExhausted && unit)
            {
                var ov = UiKit.Box(inner.transform, "Exhausted", new Color(0, 0, 0, 0.5f), true, false);
                UiKit.Stretch(ov.rectTransform);
                var z = UiKit.Label(ov.transform, "AGOTADA", F(11), new Color(1f, 1f, 1f, 0.85f), TextAnchor.MiddleCenter, FontStyle.Bold);
                UiKit.Stretch(z.rectTransform);
            }

            string badge = st.badge;
            if (st.pending) badge = "MOV";
            if (!string.IsNullOrEmpty(badge))
            {
                bool colored = st.badgeColor.a > 0.01f && !st.pending;
                var b = UiKit.Box(inner.transform, "Badge", colored ? st.badgeColor : UiKit.Gold, true, false);
                float bw = Mathf.Max(38f, badge.Length * 7.2f + 12f) * k;
                UiKit.Place(b.rectTransform, 1, 1, -3 * k, -3 * k, bw, 17 * k);
                var bt = UiKit.Label(b.transform, badge, F(10), colored ? Color.white : Color.black, TextAnchor.MiddleCenter, FontStyle.Bold);
                UiKit.Stretch(bt.rectTransform);
            }

            if (st.count > 0)
            {
                var b = UiKit.Box(inner.transform, "Count", new Color32(20, 24, 40, 235), true, false);
                UiKit.Place(b.rectTransform, 1, 1, -4 * k, -4 * k, 40 * k, 24 * k);
                UiKit.AddOutline(b, UiKit.Gold, 1.5f);
                var bt = UiKit.Label(b.transform, "x" + st.count, F(15), UiKit.Gold, TextAnchor.MiddleCenter, FontStyle.Bold);
                UiKit.Stretch(bt.rectTransform);
            }

            if (st.dim)
            {
                var dim = UiKit.Box(inner.transform, "Dim", new Color(0.05f, 0.06f, 0.1f, 0.55f), true, false);
                UiKit.Stretch(dim.rectTransform);
                if (!string.IsNullOrEmpty(st.reason))
                {
                    // motivo sobre la carta: de un vistazo se sabe POR QUÉ no se puede jugar
                    var rb = UiKit.Box(dim.transform, "Reason", new Color(0f, 0f, 0f, 0.62f), true, false);
                    float rw = Mathf.Min(innerW - 2 * pad, Mathf.Max(60f, st.reason.Length * 8.4f + 14f) * k);
                    UiKit.Place(rb.rectTransform, 0.5f, 0.5f, 0, 0, rw, 20 * k);
                    var rt2 = UiKit.Label(rb.transform, st.reason, F(10.5f), new Color(1f, 0.82f, 0.82f), TextAnchor.MiddleCenter, FontStyle.Bold);
                    UiKit.Stretch(rt2.rectTransform);
                }
            }

            return view;
        }

        static string KeywordShort(string kw, bool compact)
        {
            switch (kw)
            {
                case "DISRUPTION": return compact ? "DISR." : "DISRUPCIÓN";
                case "JUMPER": return compact ? "SALTO" : "SALTO";
                case "GLORY": return "GLORIA";
                case "CHARGE": return "CARGA";
                default: return kw;
            }
        }

        static Color SpeedColor(string speed)
        {
            switch (speed)
            {
                case "INSTANT": return new Color32(200, 70, 190, 255);
                case "FAST": return new Color32(60, 160, 90, 255);
                default: return new Color32(190, 120, 40, 255);
            }
        }

        /// <summary>Texto largo para el panel "Inspector" (al pasar el ratón por una carta).</summary>
        public static string Describe(CardDto c)
        {
            if (c == null) return "<color=#9AA2B8>Pasa el ratón sobre una carta para ver sus detalles.</color>";
            var sb = new System.Text.StringBuilder();
            bool unit = GameRules.IsUnit(c);
            sb.Append("<b><size=22>").Append(c.name).Append("</size></b>\n");
            sb.Append("<color=#").Append(UiKit.Hex(UiKit.FactionColor(c.faction))).Append(">").Append(GameRules.FactionName(c.faction)).Append("</color>  ·  ");
            sb.Append(unit ? "Unidad" : "Hechizo").Append("\n\n");
            sb.Append("Coste: <b>").Append(c.currentCost).Append("</b>");
            if (unit) sb.Append("    Drive: <b>").Append(c.currentDrive).Append("</b>  (fuerza y vida)");
            sb.Append("\n");
            if (c.keywords != null && c.keywords.Count > 0)
                sb.Append("<color=#FFC43D>").Append(string.Join(" · ", c.keywords.ToArray())).Append("</color>\n");
            if (!unit && !string.IsNullOrEmpty(c.spellSpeed) && c.spellSpeed != "NOT_A_SPELL")
                sb.Append("<color=#46B973>").Append(GameRules.SpeedName(c.spellSpeed)).Append("</color>\n");
            if (!string.IsNullOrEmpty(c.description)) sb.Append("\n").Append(c.description).Append("\n");
            string req = GameRules.RequirementsText(c);
            if (req.Length > 0) sb.Append("\n<color=#9AA2B8>Objetivos:</color> ").Append(req);
            if (unit && c.isExhausted) sb.Append("\n<color=#FF8A80>Agotada: no puede moverse hasta el próximo turno.</color>");
            return sb.ToString();
        }
    }
}
