// Mini-biblioteca para construir interfaz uGUI POR CÓDIGO (no hay prefabs ni escenas que montar a mano).
// Solo usa UnityEngine.UI (incluido en cualquier proyecto nuevo): Image, Text, Button, InputField, ScrollRect.
using System;
using UnityEngine;
using UnityEngine.UI;

namespace TimewarpTCG
{
    public static class UiKit
    {
        // ------------------------------------------------------------ paleta
        static Color C(int r, int g, int b, int a = 255) { return new Color32((byte)r, (byte)g, (byte)b, (byte)a); }

        public static readonly Color Bg = C(12, 15, 26);
        public static readonly Color Panel = C(23, 29, 47);
        public static readonly Color Panel2 = C(35, 43, 69);
        public static readonly Color PanelLight = C(52, 63, 98);
        public static readonly Color Gold = C(255, 196, 61);
        public static readonly Color Green = C(70, 185, 115);
        public static readonly Color Red = C(226, 82, 82);
        public static readonly Color Orange = C(240, 150, 60);
        public static readonly Color Blue = C(80, 140, 235);
        public static readonly Color TextCol = C(234, 238, 247);
        public static readonly Color Muted = C(150, 158, 182);
        public static readonly Color Shade = C(0, 0, 0, 150);

        public static Color FactionColor(string faction)
        {
            switch (faction)
            {
                case "ANCIENT_EGYPT": return C(214, 168, 60);
                case "ROMANS": return C(190, 62, 62);
                case "STEAMPUNK": return C(72, 168, 160);
                case "GLITCHPOP": return C(190, 80, 220);
                default: return C(120, 130, 150);
            }
        }

        public static string Hex(Color c) { return ColorUtility.ToHtmlStringRGB(c); }

        // ------------------------------------------------------------ fuente y sprite
        static Font _font;
        public static Font Font
        {
            get
            {
                if (_font == null)
                {
                    // En Windows se prefiere Segoe UI (más moderna que Arial). Si no existe o falla, se usa la fuente integrada de siempre.
                    if (Application.platform == RuntimePlatform.WindowsEditor || Application.platform == RuntimePlatform.WindowsPlayer)
                    {
                        try
                        {
                            if (Array.IndexOf(Font.GetOSInstalledFontNames(), "Segoe UI") >= 0)
                                _font = Font.CreateDynamicFontFromOSFont("Segoe UI", 16);
                        }
                        catch (Exception) { _font = null; }
                    }
                    if (_font == null) { try { _font = Resources.GetBuiltinResource<Font>("LegacyRuntime.ttf"); } catch (Exception) { } }   // Unity 2022.2+
                    if (_font == null) { try { _font = Resources.GetBuiltinResource<Font>("Arial.ttf"); } catch (Exception) { } }   // Unity antiguo
                    if (_font == null) _font = Font.CreateDynamicFontFromOSFont(new[] { "Arial", "Helvetica", "DejaVu Sans", "Liberation Sans" }, 16);
                }
                return _font;
            }
        }

        static Sprite _rounded;
        /// <summary>Rectángulo redondeado generado por código (9-slice). Funciona también en builds.</summary>
        public static Sprite Rounded
        {
            get
            {
                if (_rounded == null)
                {
                    const int S = 32;
                    const float R = 9f;
                    var tex = new Texture2D(S, S, TextureFormat.RGBA32, false);
                    tex.filterMode = FilterMode.Bilinear;
                    tex.wrapMode = TextureWrapMode.Clamp;
                    var px = new Color32[S * S];
                    for (int y = 0; y < S; y++)
                        for (int x = 0; x < S; x++)
                        {
                            float cx = Mathf.Clamp(x + 0.5f, R, S - R);
                            float cy = Mathf.Clamp(y + 0.5f, R, S - R);
                            float dx = x + 0.5f - cx, dy = y + 0.5f - cy;
                            float d = Mathf.Sqrt(dx * dx + dy * dy);
                            float a = Mathf.Clamp01(R - d + 0.5f);
                            px[y * S + x] = new Color32(255, 255, 255, (byte)Mathf.RoundToInt(a * 255f));
                        }
                    tex.SetPixels32(px);
                    tex.Apply();
                    _rounded = Sprite.Create(tex, new Rect(0, 0, S, S), new Vector2(0.5f, 0.5f), 100f, 0, SpriteMeshType.FullRect, new Vector4(R, R, R, R));
                }
                return _rounded;
            }
        }

        static Sprite _circle;
        /// <summary>Círculo generado por código (para gemas de coste / drive).</summary>
        public static Sprite Circle
        {
            get
            {
                if (_circle == null)
                {
                    const int S = 64;
                    var tex = new Texture2D(S, S, TextureFormat.RGBA32, false);
                    tex.filterMode = FilterMode.Bilinear;
                    tex.wrapMode = TextureWrapMode.Clamp;
                    var px = new Color32[S * S];
                    float r = S / 2f - 1f;
                    for (int y = 0; y < S; y++)
                        for (int x = 0; x < S; x++)
                        {
                            float dx = x + 0.5f - S / 2f, dy = y + 0.5f - S / 2f;
                            float d = Mathf.Sqrt(dx * dx + dy * dy);
                            px[y * S + x] = new Color32(255, 255, 255, (byte)Mathf.RoundToInt(Mathf.Clamp01(r - d + 0.5f) * 255f));
                        }
                    tex.SetPixels32(px);
                    tex.Apply();
                    _circle = Sprite.Create(tex, new Rect(0, 0, S, S), new Vector2(0.5f, 0.5f), 100f);
                }
                return _circle;
            }
        }

        /// <summary>Imagen con el sprite circular.</summary>
        public static Image CircleImage(Transform parent, string name, Color color)
        {
            var rt = NewRect(name, parent);
            var img = rt.gameObject.AddComponent<Image>();
            img.sprite = Circle;
            img.color = color;
            img.raycastTarget = false;
            return img;
        }

        // ------------------------------------------------------------ rectángulos y anclajes
        public static RectTransform NewRect(string name, Transform parent)
        {
            var go = new GameObject(name, typeof(RectTransform));
            go.layer = 5; // layer "UI"
            var rt = go.GetComponent<RectTransform>();
            rt.SetParent(parent, false);
            return rt;
        }

        public static void Stretch(RectTransform rt, float l = 0, float t = 0, float r = 0, float b = 0)
        {
            rt.anchorMin = Vector2.zero; rt.anchorMax = Vector2.one;
            rt.offsetMin = new Vector2(l, b); rt.offsetMax = new Vector2(-r, -t);
        }

        /// <summary>Región con fracciones del padre: x0,y0 = esquina superior izquierda (0..1, y hacia abajo).</summary>
        public static RectTransform Region(Transform parent, string name, float x0, float y0, float x1, float y1, float pad = 3f)
        {
            var rt = NewRect(name, parent);
            rt.anchorMin = new Vector2(x0, 1f - y1);
            rt.anchorMax = new Vector2(x1, 1f - y0);
            rt.offsetMin = new Vector2(pad, pad);
            rt.offsetMax = new Vector2(-pad, -pad);
            return rt;
        }

        /// <summary>Fija un rectángulo de tamaño en píxeles anclado a una esquina/borde. (ax, ay) = 0..1 ancla y pivote.</summary>
        public static void Place(RectTransform rt, float ax, float ay, float x, float y, float w, float h)
        {
            rt.anchorMin = rt.anchorMax = rt.pivot = new Vector2(ax, ay);
            rt.anchoredPosition = new Vector2(x, y);
            rt.sizeDelta = new Vector2(w, h);
        }

        // ------------------------------------------------------------ elementos
        public static Image Box(Transform parent, string name, Color color, bool rounded = true, bool raycast = true)
        {
            var rt = NewRect(name, parent);
            var img = rt.gameObject.AddComponent<Image>();
            if (rounded) { img.sprite = Rounded; img.type = Image.Type.Sliced; }
            img.color = color;
            img.raycastTarget = raycast;
            // los paneles grandes (colores Panel / Panel2) reciben el aspecto de la capa de diseño: degradado, borde de luz y sombra
            if (rounded && (color == Panel || color == Panel2)) Deco.Panelize(img);
            return img;
        }

        public static Text Label(Transform parent, string text, int size, Color color, TextAnchor anchor = TextAnchor.MiddleLeft,
                                 FontStyle style = FontStyle.Normal, string name = "Label")
        {
            var rt = NewRect(name, parent);
            var t = rt.gameObject.AddComponent<Text>();
            t.font = Font;
            t.text = text;
            t.fontSize = size;
            t.color = color;
            t.alignment = anchor;
            t.fontStyle = style;
            t.horizontalOverflow = HorizontalWrapMode.Wrap;
            t.verticalOverflow = VerticalWrapMode.Truncate;
            t.supportRichText = true;
            t.raycastTarget = false;
            return t;
        }

        public static void BestFit(Text t, int min, int max)
        {
            t.resizeTextForBestFit = true;
            t.resizeTextMinSize = min;
            t.resizeTextMaxSize = max;
        }

        public static Button MakeButton(Transform parent, string text, Color bg, Action onClick, int fontSize = 20, string name = "Button")
        {
            var img = Box(parent, name, bg);
            Deco.Buttonize(img);
            var btn = img.gameObject.AddComponent<Button>();
            btn.targetGraphic = img;
            // IMPORTANTE: después del Button, para que HoverFx encuentre su Selectable (y respete "interactable")
            Deco.Safe(() => img.gameObject.AddComponent<HoverFx>(), "efecto de botón");
            var cb = btn.colors;
            cb.normalColor = Color.white;
            cb.highlightedColor = new Color(0.88f, 0.9f, 1f, 1f);
            cb.pressedColor = new Color(0.68f, 0.7f, 0.8f, 1f);
            cb.disabledColor = new Color(0.45f, 0.45f, 0.5f, 0.75f);
            cb.fadeDuration = 0.08f;
            btn.colors = cb;
            var lbl = Label(img.transform, text, fontSize, Color.white, TextAnchor.MiddleCenter, FontStyle.Bold, "Text");
            Stretch(lbl.rectTransform, 6, 2, 6, 2);
            if (onClick != null) btn.onClick.AddListener(() => onClick());
            return btn;
        }

        public static Text ButtonLabel(Button b) { return b.GetComponentInChildren<Text>(); }

        public static InputField MakeInput(Transform parent, string placeholder, string initial, int fontSize = 20)
        {
            var bg = Box(parent, "Input", C(10, 13, 22));
            Deco.Safe(() => AddOutline(bg, new Color(1f, 1f, 1f, 0.12f), 1f), "borde de campo");
            var input = bg.gameObject.AddComponent<InputField>();
            input.targetGraphic = bg;

            var text = Label(bg.transform, "", fontSize, TextCol, TextAnchor.MiddleLeft, FontStyle.Normal, "Text");
            text.supportRichText = false;
            text.horizontalOverflow = HorizontalWrapMode.Overflow;
            Stretch(text.rectTransform, 12, 4, 12, 4);

            var ph = Label(bg.transform, placeholder, fontSize, Muted, TextAnchor.MiddleLeft, FontStyle.Italic, "Placeholder");
            ph.horizontalOverflow = HorizontalWrapMode.Overflow;
            Stretch(ph.rectTransform, 12, 4, 12, 4);

            input.textComponent = text;
            input.placeholder = ph;
            input.lineType = InputField.LineType.SingleLine;
            input.text = initial ?? "";
            return input;
        }

        /// <summary>Zona con scroll vertical. Devuelve el ScrollRect y el contenedor donde añadir hijos.</summary>
        public static ScrollRect MakeScroll(Transform parent, Color bg, out RectTransform content, string name = "Scroll", bool verticalList = true)
        {
            var root = Box(parent, name, bg);
            var sr = root.gameObject.AddComponent<ScrollRect>();

            var vp = NewRect("Viewport", root.transform);
            Stretch(vp, 4, 4, 4, 4);
            vp.gameObject.AddComponent<RectMask2D>();

            content = NewRect("Content", vp);
            content.anchorMin = new Vector2(0, 1); content.anchorMax = new Vector2(1, 1); content.pivot = new Vector2(0.5f, 1);
            content.offsetMin = new Vector2(0, 0); content.offsetMax = new Vector2(0, 0);
            // verticalList=false: el llamador añadirá su propio LayoutGroup (un objeto solo admite UNO; y Destroy es diferido,
            // así que "quitar el vertical y añadir uno de cuadrícula" en el mismo fotograma falla).
            if (verticalList)
            {
                var v = content.gameObject.AddComponent<VerticalLayoutGroup>();
                v.childControlWidth = true; v.childControlHeight = true;
                v.childForceExpandWidth = true; v.childForceExpandHeight = false;
                v.padding = new RectOffset(6, 6, 4, 4);
            }
            var fit = content.gameObject.AddComponent<ContentSizeFitter>();
            fit.verticalFit = ContentSizeFitter.FitMode.PreferredSize;

            sr.viewport = vp;
            sr.content = content;
            sr.horizontal = false;
            sr.vertical = true;
            sr.movementType = ScrollRect.MovementType.Clamped;
            sr.scrollSensitivity = 28f;
            return sr;
        }

        public static HorizontalLayoutGroup HRow(RectTransform rt, float spacing, int pad, TextAnchor align)
        {
            var h = rt.gameObject.AddComponent<HorizontalLayoutGroup>();
            h.spacing = spacing;
            h.padding = new RectOffset(pad, pad, pad, pad);
            h.childAlignment = align;
            h.childControlWidth = true; h.childControlHeight = true;
            h.childForceExpandWidth = false; h.childForceExpandHeight = false;
            return h;
        }

        public static LayoutElement Size(GameObject go, float w, float h)
        {
            var le = go.GetComponent<LayoutElement>();
            if (le == null) le = go.AddComponent<LayoutElement>();
            le.minWidth = w; le.preferredWidth = w; le.minHeight = h; le.preferredHeight = h;
            return le;
        }

        /// <summary>Fija solo la altura preferida (para filas dentro de un VerticalLayoutGroup).</summary>
        public static LayoutElement Height(GameObject go, float h)
        {
            var le = go.GetComponent<LayoutElement>();
            if (le == null) le = go.AddComponent<LayoutElement>();
            le.minHeight = h; le.preferredHeight = h;
            return le;
        }

        public static void ClearChildren(Transform t)
        {
            if (t == null) return;
            for (int i = t.childCount - 1; i >= 0; i--)
            {
                var c = t.GetChild(i);
                c.SetParent(null, false);   // sale ya del layout (Destroy es diferido)
                UnityEngine.Object.Destroy(c.gameObject);
            }
        }

        public static Clickable OnClick(GameObject go, Action click, Action enter = null, Action exit = null)
        {
            var c = go.GetComponent<Clickable>();
            if (c == null) c = go.AddComponent<Clickable>();
            c.OnClick = click; c.OnEnter = enter; c.OnExit = exit;
            return c;
        }

        public static Outline AddOutline(Graphic g, Color color, float dist = 3f)
        {
            var o = g.gameObject.GetComponent<Outline>();
            if (o == null) o = g.gameObject.AddComponent<Outline>();
            o.effectColor = color;
            o.effectDistance = new Vector2(dist, -dist);
            return o;
        }
    }
}
