// Capa de diseño: degradados, sombras, halos, fondo con viñeta y barras de gemas.
//
// REGLA DE ORO: la decoración NUNCA puede romper la interfaz. Todo lo que se añade pasa por Safe(): si algo falla
// (versión de Unity distinta, componente no disponible...) se avisa UNA vez en la consola y la pantalla se construye igual,
// solo que sin ese adorno.
using System;
using System.Collections.Generic;
using UnityEngine;
using UnityEngine.UI;

namespace TimewarpTCG
{
    public static class Deco
    {
        // ------------------------------------------------------------ seguridad
        static readonly HashSet<string> Warned = new HashSet<string>();

        public static void Safe(Action action, string what)
        {
            try { action(); }
            catch (Exception e)
            {
                if (Warned.Add(what)) Debug.LogWarning("[Timewarp] Decoración omitida (" + what + "): " + e.Message);
            }
        }

        // ------------------------------------------------------------ degradados, sombras y halos
        /// <summary>Degradado vertical que se multiplica con el color del gráfico (blanco = sin cambio).</summary>
        public static void Gradient(Graphic g, Color top, Color bottom)
        {
            if (g == null) return;
            Safe(() =>
            {
                var vg = g.GetComponent<VerticalGradient>();
                if (vg == null) vg = g.gameObject.AddComponent<VerticalGradient>();
                vg.Top = top; vg.Bottom = bottom;
            }, "degradado");
        }

        public static Color Grey(float v, float a = 1f) { return new Color(v, v, v, a); }

        /// <summary>Sombra proyectada hacia abajo (el gráfico original queda encima).</summary>
        public static void DropShadow(Graphic g, float alpha = 0.45f, float dist = 4f)
        {
            if (g == null) return;
            Safe(() =>
            {
                var s = g.gameObject.AddComponent<Shadow>();
                s.effectColor = new Color(0f, 0f, 0f, alpha);
                s.effectDistance = new Vector2(0f, -dist);
                s.useGraphicAlpha = true;
            }, "sombra");
        }

        /// <summary>Halo de color alrededor del gráfico (botones principales, elementos activos).</summary>
        public static void Halo(Graphic g, Color color, float dist = 4f)
        {
            if (g == null) return;
            Safe(() =>
            {
                var o = g.gameObject.AddComponent<Outline>();
                o.effectColor = color;
                o.effectDistance = new Vector2(dist, -dist);
                var fx = g.GetComponent<HoverFx>();      // en un botón, el halo se apaga cuando el botón está desactivado
                if (fx != null) fx.Halo = o;
            }, "halo");
        }

        /// <summary>Sombra suave para que un texto se lea sobre cualquier fondo.</summary>
        public static void TextShadow(Text t, float alpha = 0.6f, float dist = 1.6f)
        {
            if (t == null) return;
            Safe(() =>
            {
                var s = t.gameObject.AddComponent<Shadow>();
                s.effectColor = new Color(0f, 0f, 0f, alpha);
                s.effectDistance = new Vector2(dist, -dist);
            }, "sombra de texto");
        }

        /// <summary>Título dorado con degradado y sombra.</summary>
        public static void GoldTitle(Text t)
        {
            if (t == null) return;
            Gradient(t, new Color(1f, 0.93f, 0.62f), new Color(0.95f, 0.62f, 0.16f));
            TextShadow(t, 0.7f, 2.4f);
        }

        /// <summary>Aspecto "panel": degradado sutil (más oscuro abajo), borde de luz y sombra. Pensado para contenedores grandes.</summary>
        public static void Panelize(Image img, float darken = 0.16f, bool rim = true, bool shadow = true)
        {
            if (img == null) return;
            Gradient(img, Color.white, Grey(1f - darken));
            if (rim) Safe(() => UiKit.AddOutline(img, new Color(1f, 1f, 1f, 0.07f), 1f), "borde de panel");
            if (shadow) DropShadow(img, 0.30f, 3f);
        }

        /// <summary>Aspecto "botón": luz arriba, sombra abajo.</summary>
        public static void Buttonize(Image img)
        {
            if (img == null) return;
            Gradient(img, Color.white, Grey(0.78f));
            DropShadow(img, 0.45f, 3f);
        }

        // ------------------------------------------------------------ texturas generadas por código
        static Sprite _glow, _vignette;

        /// <summary>Gradiente radial: vignette=false → centro opaco que se difumina (luz); true → centro transparente y bordes oscuros.</summary>
        static Sprite Radial(bool vignette)
        {
            if (vignette && _vignette != null) return _vignette;
            if (!vignette && _glow != null) return _glow;
            const int S = 128;
            var tex = new Texture2D(S, S, TextureFormat.RGBA32, false);
            tex.filterMode = FilterMode.Bilinear;
            tex.wrapMode = TextureWrapMode.Clamp;
            var px = new Color32[S * S];
            for (int y = 0; y < S; y++)
                for (int x = 0; x < S; x++)
                {
                    float dx = (x + 0.5f) / S * 2f - 1f, dy = (y + 0.5f) / S * 2f - 1f;
                    float d = Mathf.Sqrt(dx * dx + dy * dy);
                    float a = vignette ? Mathf.SmoothStep(0f, 1f, Mathf.InverseLerp(0.55f, 1.35f, d))
                                       : Mathf.Pow(Mathf.Clamp01(1f - d), 2.2f);
                    px[y * S + x] = new Color32(255, 255, 255, (byte)Mathf.RoundToInt(Mathf.Clamp01(a) * 255f));
                }
            tex.SetPixels32(px);
            tex.Apply();
            var sp = Sprite.Create(tex, new Rect(0, 0, S, S), new Vector2(0.5f, 0.5f), 100f);
            if (vignette) _vignette = sp; else _glow = sp;
            return sp;
        }

        // ------------------------------------------------------------ fondo
        /// <summary>
        /// Fondo de pantalla: degradado vertical, dos luces grandes y suaves que "respiran" y una viñeta en los bordes.
        /// Se coloca como primer hijo (detrás de todo) y no recibe clics.
        /// </summary>
        public static RectTransform Backdrop(Transform parent, Color top, Color bottom, Color glowA, Color glowB)
        {
            var root = UiKit.NewRect("Backdrop", parent);
            UiKit.Stretch(root);
            root.SetAsFirstSibling();
            Safe(() =>
            {
                var fill = UiKit.Box(root, "Fill", Color.white, false, false);
                UiKit.Stretch(fill.rectTransform);
                Gradient(fill, top, bottom);

                var a = SoftLight(root, "GlowA", glowA, 0.18f, 0.88f, 1500f, 1100f);
                var b = SoftLight(root, "GlowB", glowB, 0.84f, 0.16f, 1400f, 1000f);
                Breathe(a, glowA.a, 9f, 0f);
                Breathe(b, glowB.a, 11f, 0.5f);

                var vig = UiKit.Box(root, "Vignette", new Color(0f, 0f, 0f, 0.62f), false, false);
                vig.sprite = Radial(true);
                vig.type = Image.Type.Simple;
                UiKit.Stretch(vig.rectTransform);
            }, "fondo");
            return root;
        }

        static Image SoftLight(Transform parent, string name, Color color, float ax, float ay, float w, float h)
        {
            var img = UiKit.Box(parent, name, color, false, false);
            img.sprite = Radial(false);
            img.type = Image.Type.Simple;
            UiKit.Place(img.rectTransform, ax, ay, 0, 0, w, h);
            return img;
        }

        static void Breathe(Image img, float baseAlpha, float period, float phase)
        {
            if (img == null) return;
            Anim.Run(period, k =>
            {
                if (img == null) return false;
                var c = img.color;
                c.a = baseAlpha * (0.72f + 0.28f * Mathf.Sin((k + phase) * Mathf.PI * 2f));
                img.color = c;
                return true;
            }, () => Breathe(img, baseAlpha, period, phase), 0f);
        }

        // ------------------------------------------------------------ separadores
        /// <summary>Línea fina con degradado horizontal (transparente en los extremos) para separar secciones.</summary>
        public static Image Divider(Transform parent, Color color, float x, float y, float w, float ax = 0.5f, float ay = 1f)
        {
            var d = UiKit.Box(parent, "Divider", color, false, false);
            UiKit.Place(d.rectTransform, ax, ay, x, y, w, 2f);
            Safe(() => { d.sprite = HLine(); d.type = Image.Type.Simple; }, "separador");
            return d;
        }

        static Sprite _hline;
        static Sprite HLine()
        {
            if (_hline != null) return _hline;
            const int W = 128;
            var tex = new Texture2D(W, 2, TextureFormat.RGBA32, false);
            tex.filterMode = FilterMode.Bilinear;
            tex.wrapMode = TextureWrapMode.Clamp;
            var px = new Color32[W * 2];
            for (int x = 0; x < W; x++)
            {
                float t = x / (W - 1f);
                float a = Mathf.Clamp01(Mathf.Sin(t * Mathf.PI) * 1.6f);
                for (int y = 0; y < 2; y++) px[y * W + x] = new Color32(255, 255, 255, (byte)Mathf.RoundToInt(a * 255f));
            }
            tex.SetPixels32(px);
            tex.Apply();
            _hline = Sprite.Create(tex, new Rect(0, 0, W, 2), new Vector2(0.5f, 0.5f), 100f);
            return _hline;
        }

        // ------------------------------------------------------------ barra de gemas (recurso, puntos)
        public sealed class GemBar
        {
            public RectTransform Root;
            public float Width;
            Image[] _gems;
            Color _on, _off, _spent;

            public static GemBar Create(Transform parent, int count, float size, float gap, Color on, Color off, bool round)
            {
                var bar = new GemBar { _on = on, _off = off, _spent = new Color(on.r, on.g, on.b, 0.28f), _gems = new Image[count] };
                bar.Width = count * (size + gap) - gap;
                bar.Root = UiKit.NewRect("GemBar", parent);
                for (int i = 0; i < count; i++)
                {
                    var g = round ? UiKit.CircleImage(bar.Root, "Gem", off) : UiKit.Box(bar.Root, "Pip", off, true, false);
                    UiKit.Place(g.rectTransform, 0, 0.5f, i * (size + gap), 0, size, size);
                    bar._gems[i] = g;
                    Gradient(g, Color.white, Grey(0.62f));
                }
                return bar;
            }

            public void Set(int filled)
            {
                for (int i = 0; i < _gems.Length; i++)
                    if (_gems[i] != null) _gems[i].color = i < filled ? _on : _off;
            }

            /// <summary>filled = disponibles ahora (brillantes); max = total de este turno: los que ya gastaste quedan translúcidos y el resto, apagados.</summary>
            public void Set(int filled, int max)
            {
                for (int i = 0; i < _gems.Length; i++)
                    if (_gems[i] != null) _gems[i].color = i < filled ? _on : (i < max ? _spent : _off);
            }
        }
    }
}
