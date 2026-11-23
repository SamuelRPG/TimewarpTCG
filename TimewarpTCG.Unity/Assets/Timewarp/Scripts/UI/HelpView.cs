// Pantalla de ayuda con las reglas TAL COMO LAS IMPLEMENTA TU BACKEND (resumen de GameState / GameFlowService / CombatService / CardPlayService).
using UnityEngine;
using UnityEngine.UI;

namespace TimewarpTCG
{
    public sealed class HelpView
    {
        public RectTransform Root;

        public const string Rules =
@"<b><size=26>OBJETIVO</size></b>
Gana quien llegue a <b>10 puntos con al menos 2 de ventaja</b>. Cuando termina cada turno recibes <b>1 punto por cada carril</b> (izquierdo y derecho) en el que tengas al menos una unidad.

<b><size=26>TURNO Y RECURSO</size></b>
En el turno N tienes N de recurso (máximo 10); no se acumula. Al empezar el turno tus unidades se recuperan y robas 1 carta. Empiezas con 4 cartas. Quien empieza cada turno con la prioridad se va alternando.

<b><size=26>ZONAS</size></b>
Tienes una <b>Base</b> y dos carriles compartidos (<b>izquierdo</b> y <b>derecho</b>). Máximo 6 unidades por zona. Las unidades que juegas entran <b>agotadas</b> en tu base y se recuperan al empezar el siguiente turno.

<b>PRIORIDAD (lo más importante)</b>
Solo actúa quien tiene la prioridad. Jugar una carta pasa la prioridad al rival. «Pasar prioridad» también. <b>Si los dos pasáis seguidos</b> ocurre algo: se resuelve la pila de hechizos; o, si no hay pila, se resuelve el combate (si lo había); o, en la fase principal, termina el turno.

<b><size=26>JUGAR UNA UNIDAD</size></b>
Arrastra la carta de tu mano a tu Base (o haz clic en la carta y luego en la Base). Solo se puede en fase principal abierta. Una unidad con <b>DISRUPTION</b> puede entrar directamente a un carril, pero solo si ese carril está disputado (los dos tenéis unidades allí).

<b><size=26>MOVER</size></b>
Arrastra una unidad tuya que esté lista (no agotada) a la zona de destino (o clic en ella y clic en la zona). Base → carril siempre; carril → carril solo con <b>JUMPER</b>. Los movimientos <b>no se ejecutan hasta que pulses «Confirmar movimientos»</b> (que pasa la prioridad); entonces las unidades movidas quedan agotadas. Mientras tengas movimientos sin confirmar no puedes jugar cartas.

<b><size=26>COMBATE</size></b>
Si tras confirmar movimientos ambos jugadores tienen unidades en el mismo carril, empieza la fase de <b>combate</b>: podéis lanzar hechizos FAST/INSTANT. Si los dos pasáis seguidos, cada bando suma el Drive de sus unidades en ese carril y lo reparte como daño a las unidades rivales, empezando por las más débiles. Gana el combate quien suma más Drive (se activan los efectos «al ganar»). Si empatan, no gana nadie.

<b><size=26>HECHIZOS</size></b>
<b>SLOW</b>: solo en tu fase principal; va a la pila.  <b>FAST</b>: también en respuesta; va a la pila.  <b>INSTANT</b>: se resuelve al momento. Cuando la pila se resuelve, el último hechizo lanzado va primero. Si el hechizo pide objetivos, arrástralo sobre la unidad objetivo (una flecha dorada te guía) o selecciónalos con clic y pulsa «Lanzar».

<b><size=26>MAZOS</size></b>
Desde el menú, «Editar mis mazos» (necesita tu nombre de cuenta): clic izquierdo añade una copia de una carta y clic derecho la quita. Un mazo válido tiene <b>exactamente 40 cartas, máximo 3 copias de cada una y máximo 2 facciones</b>. El bot juega por defecto con un mazo aleatorio distinto cada partida.

<b><size=26>EL PANEL IZQUIERDO</size></b>
Arriba verás los detalles de la carta bajo el ratón; en medio, la pila de hechizos; abajo, el registro de la partida (qué ha hecho el bot en su turno).

<color=#9AA2B8>El cliente solo te da pistas: es el servidor quien decide si una jugada es legal. Si intentas algo ilegal, verás el mensaje de error del servidor.</color>";

        public HelpView(Transform canvas)
        {
            var shade = UiKit.Box(canvas, "HelpOverlay", UiKit.Shade, false, true);
            Root = shade.rectTransform;
            UiKit.Stretch(Root);

            var panel = UiKit.Box(Root, "Panel", UiKit.Panel);
            UiKit.Place(panel.rectTransform, 0.5f, 0.5f, 0, 0, 1100, 860);
            panel.rectTransform.anchorMin = new Vector2(0.5f, 0.5f);

            var title = UiKit.Label(panel.transform, "CÓMO SE JUEGA (según tu backend)", 30, UiKit.Gold, TextAnchor.MiddleCenter, FontStyle.Bold);
            UiKit.Place(title.rectTransform, 0.5f, 1, 0, -14, 1000, 44);

            RectTransform content;
            var scroll = UiKit.MakeScroll(panel.transform, UiKit.Bg, out content, "HelpScroll");
            var srt = scroll.GetComponent<RectTransform>();
            srt.anchorMin = new Vector2(0, 0); srt.anchorMax = new Vector2(1, 1);
            srt.offsetMin = new Vector2(20, 84); srt.offsetMax = new Vector2(-20, -68);

            var text = UiKit.Label(content, Rules, 20, UiKit.TextCol, TextAnchor.UpperLeft);
            text.verticalOverflow = VerticalWrapMode.Overflow;

            var close = UiKit.MakeButton(panel.transform, "CERRAR", UiKit.Blue, () => Show(false), 22);
            UiKit.Place(close.GetComponent<RectTransform>(), 0.5f, 0, 0, 20, 260, 52);

            Show(false);
        }

        public void Show(bool v) { Root.gameObject.SetActive(v); if (v) Root.SetAsLastSibling(); }
    }
}
