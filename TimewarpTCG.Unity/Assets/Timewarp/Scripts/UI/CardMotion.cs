// Mueve una carta suavemente hacia una posición/rotación/escala objetivo (se usa en la mano en abanico).
using UnityEngine;

namespace TimewarpTCG
{
    public sealed class CardMotion : MonoBehaviour
    {
        public Vector2 targetPos;
        public float targetRot;
        public float targetScale = 1f;
        public float speed = 14f;

        RectTransform _rt;

        void Awake() { _rt = (RectTransform)transform; }

        public void SnapNow()
        {
            if (_rt == null) _rt = (RectTransform)transform;
            _rt.anchoredPosition = targetPos;
            _rt.localEulerAngles = new Vector3(0, 0, targetRot);
            _rt.localScale = new Vector3(targetScale, targetScale, 1f);
        }

        void Update()
        {
            float a = 1f - Mathf.Exp(-speed * Time.unscaledDeltaTime);
            _rt.anchoredPosition = Vector2.Lerp(_rt.anchoredPosition, targetPos, a);
            float z = Mathf.LerpAngle(_rt.localEulerAngles.z, targetRot, a);
            _rt.localEulerAngles = new Vector3(0, 0, z);
            float s = Mathf.Lerp(_rt.localScale.x, targetScale, a);
            _rt.localScale = new Vector3(s, s, 1f);
        }
    }
}
