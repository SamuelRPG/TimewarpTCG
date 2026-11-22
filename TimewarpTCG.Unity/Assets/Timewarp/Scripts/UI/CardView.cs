// Dibuja una carta (unidad o hechizo) con uGUI. Todo por código; el tamaño de las fuentes escala con el de la carta.
using System;
using System.Collections.Generic;
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
        public string badge;       // etiqueta pequeña arriba a la derecha ("OBJ", "MOV")
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
            UiKit.OnClick(border.gameObject, onClick, onEnter, onExit);

            if (st.selected) UiKit.AddOutline(border, UiKit.Gold, 4f);
            else if (st.hasOutline) UiKit.AddOutline(border, st.outline, 3f);

            if (st.faceDown || c == null)
            {
                var q = UiKit.Label(border.transform, "?", Mathf.RoundToInt(36 * h / 170f), UiKit.Muted, TextAnchor.MiddleCenter, FontStyle.Bold);
                UiKit.Stretch(q.rectTransform);
                return view;
            }

            float k = Mathf.Min(h / 170f, w / 122f);
            int F(float s) { return Mathf.Max(8, Mathf.RoundToInt(s * k)); }

            var inner = UiKit.Box(border.transform, "Inner", UiKit.Panel2, true, false);
            UiKit.Stretch(inner.rectTransform, 3, 3, 3, 3);
            bool unit = GameRules.IsUnit(c);

            // coste
            var cost = UiKit.Box(inner.transform, "Cost", UiKit.Blue, true, false);
            UiKit.Place(cost.rectTransform, 0, 1, 4 * k, -4 * k, 28 * k, 28 * k);
            var costTxt = UiKit.Label(cost.transform, c.currentCost.ToString(), F(17), Color.white, TextAnchor.MiddleCenter, FontStyle.Bold);
            UiKit.Stretch(costTxt.rectTransform);

            // nombre
            var name = UiKit.Label(inner.transform, c.name, F(13), UiKit.TextCol, TextAnchor.MiddleLeft, FontStyle.Bold);
            UiKit.Place(name.rectTransform, 0, 1, 36 * k, -3 * k, w - 6 - 42 * k, 30 * k);
            UiKit.BestFit(name, 8, F(13));

            // tipo / facción
            string typeLine = unit ? "UNIDAD" : "HECHIZO " + (c.spellSpeed ?? "");
            var type = UiKit.Label(inner.transform, typeLine.Trim() + "  " + GameRules.FactionName(c.faction), F(9), UiKit.Muted, TextAnchor.MiddleLeft);
            UiKit.Place(type.rectTransform, 0, 1, 6 * k, -34 * k, w - 12, 14 * k);

            // descripción
            var desc = UiKit.Label(inner.transform, c.description ?? "", F(10), new Color(0.85f, 0.88f, 0.95f), TextAnchor.UpperLeft);
            UiKit.Place(desc.rectTransform, 0, 1, 6 * k, -50 * k, w - 12, Mathf.Max(10, h - 50 * k - 34 * k));

            // keywords
            if (c.keywords != null && c.keywords.Count > 0)
            {
                var kw = UiKit.Label(inner.transform, string.Join(" · ", c.keywords.ToArray()), F(9), UiKit.Gold, TextAnchor.LowerLeft, FontStyle.Bold);
                UiKit.Place(kw.rectTransform, 0, 0, 6 * k, 5 * k, w - 12 - 44 * k, 24 * k);
            }

            // Drive (fuerza = vida) o velocidad del hechizo
            if (unit)
            {
                var d = UiKit.Box(inner.transform, "Drive", UiKit.Orange, true, false);
                UiKit.Place(d.rectTransform, 1, 0, -4 * k, 4 * k, 40 * k, 28 * k);
                var dt = UiKit.Label(d.transform, c.currentDrive.ToString(), F(17), Color.white, TextAnchor.MiddleCenter, FontStyle.Bold);
                UiKit.Stretch(dt.rectTransform);
            }
            else if (!string.IsNullOrEmpty(c.spellSpeed))
            {
                var sp = UiKit.Box(inner.transform, "Speed", UiKit.Green, true, false);
                UiKit.Place(sp.rectTransform, 1, 0, -4 * k, 4 * k, 52 * k, 20 * k);
                var spt = UiKit.Label(sp.transform, c.spellSpeed, F(10), Color.white, TextAnchor.MiddleCenter, FontStyle.Bold);
                UiKit.Stretch(spt.rectTransform);
            }

            // agotada
            if (c.isExhausted && unit)
            {
                var ov = UiKit.Box(inner.transform, "Exhausted", new Color(0, 0, 0, 0.5f), true, false);
                UiKit.Stretch(ov.rectTransform);
                var z = UiKit.Label(ov.transform, "AGOTADA", F(11), new Color(1f, 1f, 1f, 0.8f), TextAnchor.MiddleCenter, FontStyle.Bold);
                UiKit.Stretch(z.rectTransform);
            }

            // insignia
            string badge = st.badge;
            if (st.pending) badge = "MOV";
            if (!string.IsNullOrEmpty(badge))
            {
                var b = UiKit.Box(inner.transform, "Badge", UiKit.Gold, true, false);
                UiKit.Place(b.rectTransform, 1, 1, -3 * k, -3 * k, 36 * k, 16 * k);
                var bt = UiKit.Label(b.transform, badge, F(10), Color.black, TextAnchor.MiddleCenter, FontStyle.Bold);
                UiKit.Stretch(bt.rectTransform);
            }

            // "no jugable ahora"
            if (st.dim)
            {
                var dim = UiKit.Box(inner.transform, "Dim", new Color(0.05f, 0.06f, 0.1f, 0.55f), true, false);
                UiKit.Stretch(dim.rectTransform);
            }

            return view;
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
