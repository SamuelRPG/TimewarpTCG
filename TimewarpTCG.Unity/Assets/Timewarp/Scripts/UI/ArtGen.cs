// Ilustraciones PROCEDURALES por facción (no hay arte real todavía). Cada carta obtiene una imagen distinta a partir
// de su nombre. Cuando tengas arte de verdad, sustituye ArtGen.Get(...) por un Sprite cargado de tus assets.
using System.Collections.Generic;
using UnityEngine;

namespace TimewarpTCG
{
    public static class ArtGen
    {
        const int W = 72, H = 56;
        static readonly Dictionary<string, Sprite> Cache = new Dictionary<string, Sprite>();

        public static Sprite Get(string faction, string key)
        {
            string k = faction + "|" + key;
            Sprite s;
            if (Cache.TryGetValue(k, out s) && s != null) return s;

            uint seed = Hash(k);
            var px = new Color[W * H];
            Color top, bottom, accent;
            Palette(faction, out top, out bottom, out accent);

            // fondo: degradado vertical + resplandor
            float gx = W * (0.3f + 0.4f * Rnd(ref seed)), gy = H * (0.35f + 0.3f * Rnd(ref seed));
            for (int y = 0; y < H; y++)
                for (int x = 0; x < W; x++)
                {
                    float t = y / (float)(H - 1);
                    Color c = Color.Lerp(bottom, top, t);
                    float d = Mathf.Sqrt((x - gx) * (x - gx) + (y - gy) * (y - gy)) / (W * 0.65f);
                    c = Color.Lerp(c, accent, Mathf.Clamp01(1f - d) * 0.45f);
                    px[y * W + x] = c;
                }

            switch (faction)
            {
                case "ANCIENT_EGYPT": Egypt(px, ref seed, accent); break;
                case "ROMANS": Rome(px, ref seed, accent); break;
                case "STEAMPUNK": Steam(px, ref seed, accent); break;
                case "GLITCHPOP": Glitch(px, ref seed, accent); break;
                default: Disc(px, W / 2f, H / 2f, 12, accent, 0.6f); break;
            }

            // viñeta
            for (int y = 0; y < H; y++)
                for (int x = 0; x < W; x++)
                {
                    float dx = (x - W / 2f) / (W / 2f), dy = (y - H / 2f) / (H / 2f);
                    float v = Mathf.Clamp01((dx * dx + dy * dy) - 0.5f) * 0.55f;
                    px[y * W + x] = Color.Lerp(px[y * W + x], Color.black, v);
                }

            var tex = new Texture2D(W, H, TextureFormat.RGBA32, false);
            tex.filterMode = FilterMode.Bilinear;
            tex.wrapMode = TextureWrapMode.Clamp;
            tex.SetPixels(px);
            tex.Apply();
            s = Sprite.Create(tex, new Rect(0, 0, W, H), new Vector2(0.5f, 0.5f), 100f);
            Cache[k] = s;
            return s;
        }

        // ------------------------------------------------------------ motivos
        static void Egypt(Color[] px, ref uint seed, Color accent)
        {
            float sunX = W * (0.3f + 0.4f * Rnd(ref seed));
            Disc(px, sunX, H * 0.68f, 9 + 3 * Rnd(ref seed), new Color(1f, 0.85f, 0.4f), 0.95f);
            Disc(px, sunX, H * 0.68f, 15, new Color(1f, 0.75f, 0.3f), 0.25f);
            for (int i = 0; i < 3; i++)
            {
                float cx = W * (0.15f + 0.35f * i) + 6 * Rnd(ref seed), w = 22 + 14 * Rnd(ref seed), h = 18 + 12 * Rnd(ref seed);
                Tri(px, cx, 0, w, h, new Color(0.35f, 0.22f, 0.08f), 0.95f - 0.15f * i);
            }
            Rect(px, 0, 0, W, 6, new Color(0.25f, 0.15f, 0.05f), 0.9f);
        }

        static void Rome(Color[] px, ref uint seed, Color accent)
        {
            int n = 4;
            for (int i = 0; i < n; i++)
            {
                float x = W * (0.12f + 0.76f * i / (n - 1)) - 3;
                float h = 30 + 6 * Rnd(ref seed);
                Rect(px, x, 6, 7, h, new Color(0.85f, 0.8f, 0.72f), 0.9f);
                Rect(px, x - 2, 6 + h, 11, 3, new Color(0.9f, 0.85f, 0.75f), 0.95f);
            }
            Rect(px, 0, 0, W, 6, new Color(0.3f, 0.12f, 0.1f), 0.95f);
            Disc(px, W * (0.3f + 0.4f * Rnd(ref seed)), H * 0.8f, 7, new Color(0.95f, 0.3f, 0.25f), 0.8f);
        }

        static void Steam(Color[] px, ref uint seed, Color accent)
        {
            Gear(px, W * 0.38f, H * 0.5f, 16, 8, new Color(0.85f, 0.6f, 0.3f), 0.9f);
            Gear(px, W * 0.68f, H * 0.38f, 10, 6, new Color(0.6f, 0.75f, 0.8f), 0.85f);
            for (int i = 0; i < 4; i++)
                Disc(px, W * (0.2f + 0.6f * Rnd(ref seed)), H * (0.6f + 0.3f * Rnd(ref seed)), 6 + 5 * Rnd(ref seed), Color.white, 0.18f);
        }

        static void Glitch(Color[] px, ref uint seed, Color accent)
        {
            Color[] neon = { new Color(1f, 0.2f, 0.7f), new Color(0.2f, 0.95f, 1f), new Color(0.7f, 0.4f, 1f), new Color(1f, 0.9f, 0.3f) };
            float cx = W * (0.35f + 0.3f * Rnd(ref seed)), cy = H * 0.5f, r = 14;
            for (int y = 0; y < H; y++)
                for (int x = 0; x < W; x++)
                    if (Mathf.Abs(x - cx) + Mathf.Abs(y - cy) < r) px[y * W + x] = Color.Lerp(px[y * W + x], neon[1], 0.55f);
            for (int i = 0; i < 7; i++)
            {
                float y = H * Rnd(ref seed), w = 12 + 34 * Rnd(ref seed), x = (W - w) * Rnd(ref seed);
                Rect(px, x, y, w, 2 + 2 * Rnd(ref seed), neon[(int)(Rnd(ref seed) * 4) % 4], 0.8f);
            }
            for (int y = 0; y < H; y += 3) for (int x = 0; x < W; x++) px[y * W + x] = Color.Lerp(px[y * W + x], Color.black, 0.22f);
        }

        // ------------------------------------------------------------ primitivas
        static void Disc(Color[] px, float cx, float cy, float r, Color c, float a)
        {
            int x0 = Mathf.Max(0, (int)(cx - r - 2)), x1 = Mathf.Min(W - 1, (int)(cx + r + 2));
            int y0 = Mathf.Max(0, (int)(cy - r - 2)), y1 = Mathf.Min(H - 1, (int)(cy + r + 2));
            for (int y = y0; y <= y1; y++)
                for (int x = x0; x <= x1; x++)
                {
                    float d = Mathf.Sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy));
                    float k = Mathf.Clamp01(r - d + 0.5f) * a;
                    if (k > 0f) px[y * W + x] = Color.Lerp(px[y * W + x], c, k);
                }
        }

        static void Rect(Color[] px, float x, float y, float w, float h, Color c, float a)
        {
            for (int yy = Mathf.Max(0, (int)y); yy < Mathf.Min(H, (int)(y + h)); yy++)
                for (int xx = Mathf.Max(0, (int)x); xx < Mathf.Min(W, (int)(x + w)); xx++)
                    px[yy * W + xx] = Color.Lerp(px[yy * W + xx], c, a);
        }

        static void Tri(Color[] px, float cx, float baseY, float w, float h, Color c, float a)
        {
            for (int y = (int)baseY; y < Mathf.Min(H, (int)(baseY + h)); y++)
            {
                float half = (1f - (y - baseY) / h) * w / 2f;
                for (int x = Mathf.Max(0, (int)(cx - half)); x < Mathf.Min(W, (int)(cx + half)); x++)
                    px[y * W + x] = Color.Lerp(px[y * W + x], c, a);
            }
        }

        static void Gear(Color[] px, float cx, float cy, float r, int teeth, Color c, float a)
        {
            for (int i = 0; i < teeth; i++)
            {
                float ang = i * Mathf.PI * 2f / teeth;
                Disc(px, cx + Mathf.Cos(ang) * (r + 1.5f), cy + Mathf.Sin(ang) * (r + 1.5f), 2.6f, c, a);
            }
            Disc(px, cx, cy, r, c, a);
            Disc(px, cx, cy, r * 0.45f, new Color(0.12f, 0.08f, 0.05f), 0.9f);
        }

        static void Palette(string faction, out Color top, out Color bottom, out Color accent)
        {
            switch (faction)
            {
                case "ANCIENT_EGYPT": top = new Color(0.25f, 0.12f, 0.35f); bottom = new Color(0.95f, 0.62f, 0.25f); accent = new Color(1f, 0.8f, 0.4f); break;
                case "ROMANS": top = new Color(0.2f, 0.06f, 0.08f); bottom = new Color(0.7f, 0.22f, 0.2f); accent = new Color(1f, 0.5f, 0.35f); break;
                case "STEAMPUNK": top = new Color(0.05f, 0.18f, 0.2f); bottom = new Color(0.25f, 0.55f, 0.52f); accent = new Color(0.9f, 0.75f, 0.45f); break;
                case "GLITCHPOP": top = new Color(0.12f, 0.05f, 0.25f); bottom = new Color(0.65f, 0.2f, 0.7f); accent = new Color(0.3f, 0.9f, 1f); break;
                default: top = new Color(0.1f, 0.1f, 0.15f); bottom = new Color(0.4f, 0.4f, 0.5f); accent = Color.white; break;
            }
        }

        static uint Hash(string s)
        {
            uint h = 2166136261u;
            foreach (char ch in s) { h ^= ch; h *= 16777619u; }
            return h == 0 ? 1u : h;
        }

        static float Rnd(ref uint s)
        {
            s ^= s << 13; s ^= s >> 17; s ^= s << 5;       // xorshift
            return (s & 0xFFFFFF) / (float)0x1000000;
        }
    }
}
