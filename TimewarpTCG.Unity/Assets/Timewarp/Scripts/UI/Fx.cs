// Efectos visuales ligeros hechos con uGUI (texto flotante, partículas, destellos, banners).
// Es el punto de entrada para añadir más VFX en el futuro: cada efecto es una función estática que crea
// elementos en la capa de efectos (Fx.Layer, por encima del tablero) y los anima con Anim.Run.
using UnityEngine;
using UnityEngine.UI;

namespace TimewarpTCG
{
    public static class Fx
    {
        /// <summary>Capa de efectos (a pantalla completa, por encima de todo, sin recibir clics).</summary>
        public static RectTransform Layer;

        public static Vector2 ScreenPos(RectTransform rt)
        {
            return RectTransformUtility.WorldToScreenPoint(null, rt.TransformPoint(rt.rect.center));
        }

        static bool ToLocal(Vector2 screen, out Vector2 local)
        {
            local = Vector2.zero;
            return Layer != null && RectTransformUtility.ScreenPointToLocalPointInRectangle(Layer, screen, null, out local);
        }

        /// <summary>Número/texto que sube y se desvanece (daño, bonificaciones, puntos...).</summary>
        public static void FloatText(Vector2 screen, string text, Color color, int size = 44, float rise = 90f)
        {
            Vector2 p;
            if (!ToLocal(screen, out p)) return;
            var t = UiKit.Label(Layer, text, size, color, TextAnchor.MiddleCenter, FontStyle.Bold, "FloatText");
            t.horizontalOverflow = HorizontalWrapMode.Overflow; t.verticalOverflow = VerticalWrapMode.Overflow;
            var rt = t.rectTransform;
            rt.anchorMin = rt.anchorMax = new Vector2(0.5f, 0.5f);
            rt.sizeDelta = new Vector2(300, 80);
            UiKit.AddOutline(t, new Color(0, 0, 0, 0.85f), 2f);
            Anim.Run(1.1f, k =>
            {
                if (t == null) return false;
                rt.anchoredPosition = p + new Vector2(0, rise * Anim.EaseOut(k));
                float s = k < 0.18f ? Mathf.Lerp(0.4f, 1.25f, Anim.EaseOutBack(k / 0.18f)) : Mathf.Lerp(1.25f, 1f, (k - 0.18f) / 0.3f);
                rt.localScale = new Vector3(s, s, 1);
                var c = color; c.a = k < 0.6f ? 1f : 1f - (k - 0.6f) / 0.4f; t.color = c;
                return true;
            }, () => { if (t != null) Object.Destroy(t.gameObject); });
        }

        /// <summary>Explosión de partículas cuadradas desde un punto.</summary>
        public static void Burst(Vector2 screen, Color color, int count = 16, float radius = 130f, float size = 12f)
        {
            Vector2 p;
            if (!ToLocal(screen, out p)) return;
            for (int i = 0; i < count; i++)
            {
                var img = UiKit.Box(Layer, "Spark", color, true, false);
                var rt = img.rectTransform;
                rt.anchorMin = rt.anchorMax = new Vector2(0.5f, 0.5f);
                float sz = size * Random.Range(0.5f, 1.2f);
                rt.sizeDelta = new Vector2(sz, sz);
                float ang = Random.Range(0f, Mathf.PI * 2f), dist = radius * Random.Range(0.4f, 1f);
                Vector2 dir = new Vector2(Mathf.Cos(ang), Mathf.Sin(ang));
                float dur = Random.Range(0.45f, 0.8f), spin = Random.Range(-300f, 300f);
                Anim.Run(dur, k =>
                {
                    if (img == null) return false;
                    float e = Anim.EaseOut(k);
                    rt.anchoredPosition = p + dir * dist * e + new Vector2(0, -60f * k * k);
                    rt.localEulerAngles = new Vector3(0, 0, spin * k);
                    var c = color; c.a = 1f - k; img.color = c;
                    float s = 1f - 0.6f * k; rt.localScale = new Vector3(s, s, 1);
                    return true;
                }, () => { if (img != null) Object.Destroy(img.gameObject); });
            }
        }

        /// <summary>Destello de color sobre una carta (se añade como hijo de la propia carta).</summary>
        public static void Flash(RectTransform target, Color color, float duration = 0.45f)
        {
            if (target == null) return;
            var img = UiKit.Box(target, "Flash", color, true, false);
            UiKit.Stretch(img.rectTransform);
            Anim.Run(duration, k =>
            {
                if (img == null || target == null) return false;
                var c = color; c.a = color.a * (1f - k) * (1f - k); img.color = c;
                return true;
            }, () => { if (img != null) Object.Destroy(img.gameObject); });
        }

        /// <summary>Aparece con un "pop" (escala + opacidad). Útil al entrar una carta en el tablero.</summary>
        public static void PopIn(RectTransform target, float duration = 0.35f)
        {
            if (target == null) return;
            var cg = target.GetComponent<CanvasGroup>();
            Anim.Run(duration, k =>
            {
                if (target == null) return false;
                float s = Mathf.LerpUnclamped(0.55f, 1f, Anim.EaseOutBack(k));
                target.localScale = new Vector3(s, s, 1);
                if (cg != null) cg.alpha = Mathf.Clamp01(k * 2.5f);
                return true;
            }, () => { if (target != null) { target.localScale = Vector3.one; if (cg != null) cg.alpha = 1f; } });
        }

        /// <summary>Latido de escala (marcador de puntos, recurso...).</summary>
        public static void Pulse(RectTransform target, float scale = 1.25f, float duration = 0.4f)
        {
            if (target == null) return;
            Anim.Run(duration, k =>
            {
                if (target == null) return false;
                float s = 1f + (scale - 1f) * Mathf.Sin(k * Mathf.PI);
                target.localScale = new Vector3(s, s, 1);
                return true;
            }, () => { if (target != null) target.localScale = Vector3.one; });
        }

        /// <summary>Sacudida (para el impacto de un combate). Solo en elementos que no estén dentro de un LayoutGroup.</summary>
        public static void Shake(RectTransform target, float magnitude = 9f, float duration = 0.35f)
        {
            if (target == null) return;
            Vector2 origin = target.anchoredPosition;
            Anim.Run(duration, k =>
            {
                if (target == null) return false;
                float m = magnitude * (1f - k);
                target.anchoredPosition = origin + new Vector2(Random.Range(-m, m), Random.Range(-m, m));
                return true;
            }, () => { if (target != null) target.anchoredPosition = origin; });
        }

        /// <summary>Banda central con título grande (cambio de turno, victoria...).</summary>
        public static void Banner(string title, string subtitle, Color color, float hold = 0.9f)
        {
            if (Layer == null) return;
            var band = UiKit.Box(Layer, "Banner", new Color(0.03f, 0.04f, 0.09f, 0.82f), false, false);
            var rt = band.rectTransform;
            rt.anchorMin = new Vector2(0, 0.5f); rt.anchorMax = new Vector2(1, 0.5f);
            rt.sizeDelta = new Vector2(0, 150); rt.anchoredPosition = new Vector2(0, 40);
            var line = UiKit.Box(rt, "Line", color, false, false);
            line.rectTransform.anchorMin = new Vector2(0, 0); line.rectTransform.anchorMax = new Vector2(1, 0);
            line.rectTransform.sizeDelta = new Vector2(0, 4); line.rectTransform.anchoredPosition = Vector2.zero;
            var t = UiKit.Label(rt, title, 72, color, TextAnchor.MiddleCenter, FontStyle.Bold);
            UiKit.Place(t.rectTransform, 0.5f, 0.5f, 0, 14, 1200, 90);
            var s = UiKit.Label(rt, subtitle ?? "", 26, UiKit.TextCol, TextAnchor.MiddleCenter);
            UiKit.Place(s.rectTransform, 0.5f, 0.5f, 0, -42, 1200, 36);
            float total = 0.35f + hold + 0.35f;
            Anim.Run(total, k =>
            {
                if (band == null) return false;
                float time = k * total;
                float a = time < 0.35f ? time / 0.35f : (time > 0.35f + hold ? 1f - (time - 0.35f - hold) / 0.35f : 1f);
                var bc = band.color; bc.a = 0.82f * a; band.color = bc;
                var tc = t.color; tc.a = a; t.color = tc;
                var sc = s.color; sc.a = a; s.color = sc;
                float x = time < 0.35f ? Mathf.Lerp(-300f, 0f, Anim.EaseOut(time / 0.35f)) : (time > 0.35f + hold ? Mathf.Lerp(0f, 300f, (time - 0.35f - hold) / 0.35f) : 0f);
                t.rectTransform.anchoredPosition = new Vector2(x, 14); s.rectTransform.anchoredPosition = new Vector2(-x * 0.5f, -42);
                return true;
            }, () => { if (band != null) Object.Destroy(band.gameObject); });
        }
    }
}
