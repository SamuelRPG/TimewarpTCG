// Permite arrastrar y soltar un elemento de la interfaz. Solo reenvía los eventos; la lógica está en GameView.
using System;
using UnityEngine;
using UnityEngine.EventSystems;

namespace TimewarpTCG
{
    public sealed class Draggable : MonoBehaviour, IBeginDragHandler, IDragHandler, IEndDragHandler
    {
        public Action<PointerEventData> OnBegin, OnMove, OnEnd;

        public void OnBeginDrag(PointerEventData e) { if (e.button == PointerEventData.InputButton.Left && OnBegin != null) OnBegin(e); }
        public void OnDrag(PointerEventData e) { if (e.button == PointerEventData.InputButton.Left && OnMove != null) OnMove(e); }
        public void OnEndDrag(PointerEventData e) { if (e.button == PointerEventData.InputButton.Left && OnEnd != null) OnEnd(e); }
    }
}
