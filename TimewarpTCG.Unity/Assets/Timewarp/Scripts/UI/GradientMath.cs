// Matemática del degradado vertical, sin dependencias de uGUI (solo Color, Color32 y Mathf) para poder probarla fuera de Unity.
using UnityEngine;

namespace TimewarpTCG
{
    public static class GradientMath
    {
        /// <summary>
        /// ys[i] = altura del vértice i (en uGUI, y crece hacia ARRIBA); colors[i] = su color, que se MULTIPLICA por el
        /// degradado: el vértice más alto recibe 'top' y el más bajo 'bottom', y el resto se reparte linealmente.
        /// Multiplicar por blanco no cambia nada; por menos de 1 oscurece; por alfa 0 hace transparente.
        /// </summary>
        public static void Apply(float[] ys, Color32[] colors, int count, Color top, Color bottom)
        {
            if (count <= 0) return;
            float minY = float.MaxValue, maxY = float.MinValue;
            for (int i = 0; i < count; i++)
            {
                if (ys[i] < minY) minY = ys[i];
                if (ys[i] > maxY) maxY = ys[i];
            }
            float h = maxY - minY;
            if (h < 0.0001f) return;
            for (int i = 0; i < count; i++)
            {
                Color g = Color.Lerp(bottom, top, (ys[i] - minY) / h);
                Color32 c = colors[i];
                colors[i] = new Color32(
                    (byte)Mathf.Min(255f, c.r * g.r), (byte)Mathf.Min(255f, c.g * g.g),
                    (byte)Mathf.Min(255f, c.b * g.b), (byte)Mathf.Min(255f, c.a * g.a));
            }
        }
    }
}
