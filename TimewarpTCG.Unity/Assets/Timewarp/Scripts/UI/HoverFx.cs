// Respuesta táctil de un botón: crece un poco al pasar el ratón y se hunde un poco al pulsar.
// Solo toca la escala mientras anima (y hasta volver a su tamaño original), para no pelearse con otros efectos
// que escalen el mismo objeto. Los botones desactivados (Selectable.interactable = false) no reaccionan.
using UnityEngine;
using UnityEngine.EventSystems;
using UnityEngine.UI;

namespace TimewarpTCG
{
    public class HoverFx : MonoBehaviour, IPointerEnterHandler, IPointerExitHandler, IPointerDownHandler, IPointerUpHandler
    {
        public float HoverScale = 1.035f;
        public float PressScale = 0.97f;
        /// <summary>Halo opcional (Outline) que solo se ve mientras el botón está activo.</summary>
        public Outline Halo;

        Selectable _sel;
        Vector3 _base = Vector3.one;
        bool _init, _hover, _down, _returning;

        void Init()
        {
            if (_init) return;
            _init = true;
            _base = transform.localScale;
            _sel = GetComponent<Selectable>();
        }

        void Awake() { Init(); }

        void OnDisable()
        {
            _hover = _down = _returning = false;
            if (_init) transform.localScale = _base;
        }

        public void OnPointerEnter(PointerEventData e) { Init(); _hover = true; }
        public void OnPointerExit(PointerEventData e) { _hover = false; _down = false; _returning = true; }
        public void OnPointerDown(PointerEventData e) { Init(); _down = true; }
        public void OnPointerUp(PointerEventData e) { _down = false; _returning = true; }

        void Update()
        {
            if (!_init) Init();
            if (Halo != null)
            {
                bool on = _sel == null || _sel.IsInteractable();
                if (Halo.enabled != on) Halo.enabled = on;
            }
            if (!_hover && !_down && !_returning) return;       // en reposo no se toca la escala

            bool enabledBtn = _sel == null || _sel.IsInteractable();
            float k = !enabledBtn ? 1f : (_down ? PressScale : (_hover ? HoverScale : 1f));
            Vector3 goal = _base * k;
            Vector3 cur = transform.localScale;
            if ((cur - goal).sqrMagnitude < 0.000001f)
            {
                transform.localScale = goal;
                if (!_hover && !_down) _returning = false;
                return;
            }
            transform.localScale = Vector3.Lerp(cur, goal, 1f - Mathf.Exp(-18f * Time.unscaledDeltaTime));
        }
    }
}
