// Componente mínimo para recibir clics y "hover" en cualquier elemento de la interfaz.
using System;
using UnityEngine;
using UnityEngine.EventSystems;

namespace TimewarpTCG
{
    public class Clickable : MonoBehaviour, IPointerClickHandler, IPointerEnterHandler, IPointerExitHandler
    {
        public Action OnClick;
        public Action OnEnter;
        public Action OnExit;

        public void OnPointerClick(PointerEventData e)
        {
            if (e.button == PointerEventData.InputButton.Left && OnClick != null) OnClick();
        }
        public void OnPointerEnter(PointerEventData e) { if (OnEnter != null) OnEnter(); }
        public void OnPointerExit(PointerEventData e) { if (OnExit != null) OnExit(); }
    }
}
