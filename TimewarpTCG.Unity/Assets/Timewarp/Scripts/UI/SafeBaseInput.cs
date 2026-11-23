// "Entrada de texto segura" para proyectos que usan SOLO el Input System nuevo (valor por defecto de Unity 6).
//
// PROBLEMA: el InputField clásico de uGUI pregunta en cada fotograma por la composición de texto (IME) a través de BaseInput,
// que por defecto lee UnityEngine.Input, la API ANTIGUA. Con "Active Input Handling = Input System Package (New)" esa API lanza una
// InvalidOperationException y los campos de texto (nombre, contraseña, servidor...) dejan de aceptar texto.
//
// SOLUCIÓN: sustituir esas tres consultas por respuestas neutras. El teclado normal sigue funcionando (InputField lee los eventos de
// texto de IMGUI, no de UnityEngine.Input); solo se pierde la ventana de composición de IME asiático, que aquí no se usa.
using UnityEngine;
using UnityEngine.EventSystems;

namespace TimewarpTCG
{
    public class SafeBaseInput : BaseInput
    {
        public override string compositionString { get { return ""; } }
        public override IMECompositionMode imeCompositionMode { get { return IMECompositionMode.Auto; } set { } }
        public override Vector2 compositionCursorPos { get { return Vector2.zero; } set { } }
    }
}
