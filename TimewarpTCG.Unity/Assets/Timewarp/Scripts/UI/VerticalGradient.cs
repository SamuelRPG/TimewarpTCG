// Degradado vertical para cualquier Image o Text de uGUI. Se MULTIPLICA con el color del gráfico:
// arriba = Top, abajo = Bottom (blanco = sin cambio). Así un panel gris azulado pasa a ser un poco más oscuro por abajo
// sin perder el color que le pongamos desde código (selección, resaltes...). La matemática está en GradientMath.
using UnityEngine;
using UnityEngine.UI;

namespace TimewarpTCG
{
    public class VerticalGradient : BaseMeshEffect
    {
        [SerializeField] Color _top = Color.white;
        [SerializeField] Color _bottom = Color.white;

        public Color Top { get { return _top; } set { _top = value; Refresh(); } }
        public Color Bottom { get { return _bottom; } set { _bottom = value; Refresh(); } }

        void Refresh() { if (graphic != null) graphic.SetVerticesDirty(); }

        public override void ModifyMesh(VertexHelper vh)
        {
            if (!IsActive() || vh == null || vh.currentVertCount == 0) return;
            int n = vh.currentVertCount;
            var ys = new float[n];
            var cs = new Color32[n];
            var v = new UIVertex();
            for (int i = 0; i < n; i++)
            {
                vh.PopulateUIVertex(ref v, i);
                ys[i] = v.position.y;
                cs[i] = v.color;
            }
            GradientMath.Apply(ys, cs, n, _top, _bottom);
            for (int i = 0; i < n; i++)
            {
                vh.PopulateUIVertex(ref v, i);
                v.color = cs[i];
                vh.SetUIVertex(v, i);
            }
        }
    }
}
