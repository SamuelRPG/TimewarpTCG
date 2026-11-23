// "Mantener pulsado para ver": avisa cuando se pulsa con el clic izquierdo (OnHold) y cuando se suelta, se sale del elemento o el elemento
// desaparece (OnRelease). Lo de "desaparece" es importante: la interfaz se reconstruye con cada estado nuevo, y un elemento destruido nunca recibe
// OnPointerUp: sin OnDisable la vista grande se quedaria abierta para siempre (el mismo problema que la carta flotante al arrastrar).
using System;
using UnityEngine;
using UnityEngine.EventSystems;

namespace TimewarpTCG
{
    public class HoldPeek : MonoBehaviour, IPointerDownHandler, IPointerUpHandler, IPointerExitHandler
    {
        public Action OnHold;
        public Action OnRelease;
        bool _holding;

        public void OnPointerDown(PointerEventData e)
        {
            if (e.button != PointerEventData.InputButton.Left) return;
            _holding = true;
            if (OnHold != null) OnHold();
        }

        public void OnPointerUp(PointerEventData e) { Release(); }
        public void OnPointerExit(PointerEventData e) { Release(); }
        void OnDisable() { Release(); }

        void Release()
        {
            if (!_holding) return;
            _holding = false;
            if (OnRelease != null) OnRelease();
        }
    }
}
