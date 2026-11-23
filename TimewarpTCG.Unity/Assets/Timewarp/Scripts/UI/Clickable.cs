// Componente mínimo para recibir clics (izquierdo y derecho) y "hover" en cualquier elemento de la interfaz.
using System;
using UnityEngine;
using UnityEngine.EventSystems;

namespace TimewarpTCG
{
    public class Clickable : MonoBehaviour, IPointerClickHandler, IPointerEnterHandler, IPointerExitHandler
    {
        public Action OnClick;
        public Action OnRightClick;
        public Action OnEnter;
        public Action OnExit;

        public void OnPointerClick(PointerEventData e)
        {
            if (e.button == PointerEventData.InputButton.Left && OnClick != null) OnClick();
            else if (e.button == PointerEventData.InputButton.Right && OnRightClick != null) OnRightClick();
        }
        public void OnPointerEnter(PointerEventData e) { if (OnEnter != null) OnEnter(); }
        public void OnPointerExit(PointerEventData e) { if (OnExit != null) OnExit(); }
    }
}
