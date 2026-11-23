# Cómo funciona tu juego (según el código del backend)

Esto es lo que **hace** el código hoy, no lo que quizá pensabas diseñar hace meses. Cada regla lleva la clase donde vive, por si quieres contrastarla con tu diseño. Donde difiere de lo que esperas, probablemente es algo a decidir (ver `02_PROBLEMAS_Y_HOJA_DE_RUTA.md`).

## 1. Mapa del backend (arquitectura hexagonal)

```
domain/            ← reglas puras, sin Spring
  model/entity     GameState, PlayerState, Card, CardTemplate, Deck
  model/vo         Cost, Drive, Resource, Score, Turn, QueuedSpell, TargetingRequirement
  model/effect     Effect (interfaz de eventos), TriggeredEffect + actions/* (ENEMY_DAMAGE, ALLY_BOOST…)
  service          CardPlayService (jugar), GameFlowService (prioridad/turnos), CombatService, DeckValidatorService
application/       ← casos de uso
  service          StartMatch, PlayCard, MoveCard, PassPriority, BotBrainService, DeckService
  factory          DeckFactory (mazos AGGRO/CONTROL en código), EffectFactory + TargetingFactory (texto de BBDD → objetos)
infrastructure/    ← Spring
  web/controller   GameController (WebSocket), UserController y MenuController (REST)
  persistence      InMemoryGameRepository (partidas en RAM), JPA (usuarios, mazos, catálogo de cartas)
```

Las **partidas viven solo en memoria** del servidor (`InMemoryGameRepository`); la base de datos guarda usuarios, mazos y el catálogo de cartas.

## 2. Conceptos del juego

* **Dos jugadores**, hoy siempre *tú* (jugador A) contra el **bot** (jugador B).
* **Carta**: *Unidad* o *Hechizo*. Tiene **coste** y, si es unidad, **Drive** (es a la vez su fuerza de ataque y su vida).
* **Zonas de cada jugador**: Mazo, Mano, Cementerio, **Base** (`MY_BASE`) y dos **carriles** (`FIELD_LEFT`, `FIELD_RIGHT`). Un carril lo ocupan unidades de ambos jugadores a la vez: cuando los dos tenéis unidades en el mismo carril está **disputado** y ahí hay combate.
* Máximo **6 unidades por zona**.
* **Facciones**: Antiguo Egipto, Romanos, Steampunk, Glitchpop. Un mazo válido (personalizado): exactamente **40 cartas, máx. 3 copias de cada una, máx. 2 facciones** (`Deck`, `DeckService`, `DeckValidatorService`). Los mazos de prueba AGGRO/CONTROL (`DeckFactory`) se saltan esas reglas (25 copias de la misma carta).

## 3. La partida paso a paso

**Inicio** (`StartMatchService`): cada uno roba **4 cartas**; una moneda decide quién tiene la prioridad al empezar. Recurso inicial: 1.

**Cada turno N** (`GameState.newTurn`, cuando termina el anterior):
1. **Puntuación**: cada jugador recibe **+1 punto por cada carril en el que tenga al menos una unidad** (aunque el carril esté disputado y la unidad haya sobrevivido al combate).
2. **Victoria**: gana quien tenga **≥ 10 puntos y ≥ 2 de ventaja**. Si nadie, sigue.
3. Turno N+1; empieza con prioridad el jugador contrario al que empezó el turno anterior (se alterna).
4. **Recurso = N** para los dos (máx. 10; no se acumula). Todas las unidades en tablero se **recuperan** (dejan de estar agotadas). Cada jugador **roba 1 carta** (si el mazo está vacío no pasa nada, no hay fatiga).

**Unidades nuevas** entran **agotadas** (no pueden moverse hasta el siguiente turno).

### La prioridad (el corazón del sistema)
Solo actúa el jugador con la **prioridad** (`GameFlowService`). Hay una cuenta de **pases consecutivos** (0, 1, 2):
* **Jugar una unidad o un hechizo SLOW/FAST** → pases a 0 y la **prioridad pasa al rival**. Un hechizo **INSTANT** no cambia ni la prioridad ni la cuenta de pases (sigues actuando tú).
* **Mover** unidades no cambia la prioridad ni cuenta como pase (ver más abajo).
* **Pasar prioridad** → pases +1 y la prioridad pasa al rival. Si llegan a **2 seguidos**:
  * si hay **hechizos en la pila** → se resuelve la pila y sigue el juego;
  * si no, y estáis en **combate** → se resuelve el combate en el carril izquierdo y luego el derecho, y se vuelve a fase principal (la prioridad se queda con quien pasó en segundo lugar);
  * si no, y estáis en **fase principal** → **termina el turno** (puntúa y empieza el siguiente).

### Fases (`GamePhase`)
| Fase | Qué pasa |
|---|---|
| `MAIN_OPEN` | Puedes bajar unidades, lanzar cualquier hechizo y mover unidades. |
| `MAIN_CLOSED` | Hay hechizos en la pila: solo responder con hechizos FAST/INSTANT. **No se pueden bajar unidades.** |
| `COMBAT_OPEN` | Hay un carril disputado tras confirmar movimientos. Se pueden lanzar FAST/INSTANT y mover. **No se pueden bajar unidades.** |
| `COMBAT_CLOSED` | Igual que MAIN_CLOSED, durante el combate. |

### Jugar una unidad (`CardPlayService`)
* Solo en `MAIN_OPEN`, con recurso suficiente, y sin movimientos pendientes.
* Destino normal: **tu Base**. Solo una unidad con la keyword **DISRUPTION** puede entrar directamente a un carril, y solo si ese carril ya está disputado.
* Dispara su efecto `ON_PLACED` (si lo tiene) con los objetivos elegidos.

### Mover unidades (`PlayerState.moveCard`, `GameFlowService`)
* Requiere tener la prioridad **y la fase abierta**: con algo en la **pila** (fases `MAIN_CLOSED` / `COMBAT_CLOSED`) **no se puede mover**; solo responder con hechizos `FAST`/`INSTANT` o pasar. (Antes no se comprobaba y el rival podía mover y confirmar mientras un hechizo esperaba.) La unidad no puede estar agotada.
* Base ⇄ carril siempre; **carril → carril solo con la keyword JUMPER**. Máx. 6 en destino.
* El movimiento **no se ejecuta hasta que pasas prioridad** (“confirmar”). Mientras tienes movimientos pendientes **no puedes jugar cartas**. Puedes deshacer un movimiento devolviendo la unidad a su sitio, pero **sigue contando como pendiente** hasta que pases prioridad.
* Al confirmar: cada unidad movida queda **agotada**, se dispara `ON_MOVEMENT_CONFIRMED`, y si algún carril queda disputado la fase pasa a `COMBAT_OPEN`. La prioridad pasa al rival.

### Combate (`CombatService`)
Por cada carril disputado (izquierdo, luego derecho):
1. Cada bando suma el **Drive efectivo** de sus unidades en ese carril.
2. Cada bando reparte esa suma como **daño** a las unidades del rival, empezando por las de **menor Drive**: mata las que pueda y deja la última herida.
3. Los dos bandos se hacen daño a la vez. **Gana el que sumó más** (se disparan los `ON_COMBAT_WIN` de sus unidades); **si empatan, nadie gana** (ambos se hacen el daño igualmente).

### Hechizos
| Velocidad | Cuándo | Qué hace |
|---|---|---|
| `SLOW` | Solo en tu `MAIN_OPEN` | Va a la **pila**; abre `MAIN_CLOSED` |
| `FAST` | También como respuesta | Va a la **pila**; cierra la fase actual |
| `INSTANT` | Cuando quieras | Se resuelve **al momento** (no usa la pila) |

La pila se resuelve **de arriba abajo** (el último lanzado, primero) cuando los dos pasáis seguidos. Los objetivos se validan por grupo: `ALLY`, `ENEMY`, `ANY_UNIT` y el número exacto que pide la carta.

#### Los efectos "al entrar en juego" también usan la pila
Cuando bajas una **unidad con un efecto `ON_PLACED`** (por ejemplo "da +2 de Drive a un aliado"), la unidad entra **al tablero al momento**, pero su **efecto va a la pila** como una entrada más: la fase pasa a `MAIN_CLOSED`, el rival recibe la prioridad y puede **responder con hechizos `FAST`/`INSTANT`** (por ejemplo, matar al aliado objetivo). El efecto ocurre cuando los dos pasáis seguidos.
* Si el efecto **no haría nada** (necesita objetivo y no hay ninguno), **no se abre pila**: la unidad entra y la prioridad pasa al rival como siempre.
* Una unidad **sin** efecto al entrar tampoco usa la pila.
* Si la unidad muere en respuesta, el efecto **se resuelve igualmente** (ya estaba en la pila).
* Resolver el efecto **no manda la unidad al cementerio** (a diferencia de un hechizo).
* Siguen siendo **inmediatos** los demás disparadores: al morir, al sobrevivir a un daño, al ganar un combate y al confirmar un movimiento.

## 4. Cartas, keywords y efectos

Esto tiene su propia guía: **[`docs/03_EFECTOS_KEYWORDS_Y_BASE_DE_DATOS.md`](03_EFECTOS_KEYWORDS_Y_BASE_DE_DATOS.md)** (qué significa cada disparador, acción, objetivo y keyword, cómo se leen las cadenas de `card_templates` y cómo llegan de Supabase a la partida) y el catálogo de 36 cartas explicado una a una en [`catalogo/CARTAS_EXPLICADAS.md`](catalogo/CARTAS_EXPLICADAS.md).

Resumen del estado actual:

| Pieza | Estado |
|---|---|
| Disparadores `ON_PLACED`, `ON_DESTROYED`, `ON_DAMAGE_TAKEN`, `ON_COMBAT_WIN`, `ON_MOVEMENT_CONFIRMED` | ✅ funcionan |
| Acciones `ENEMY_DAMAGE`, `ALLY_BOOST`, `SELF_BUFF`, `CHARGE_BUFF`, `READY_SELF`, `DRAW`, `HUNTER_MARK:daño:recompensa` | ✅ funcionan |
| Keywords `JUMPER`, `DISRUPTION` | ✅ funcionan |
| Keywords `GLORY`, `CHARGE` | ❌ el motor no las lee (se logra con `READY_SELF` / `CHARGE_BUFF`) |
| Grupos de objetivo `GLOBAL`, `LANE` | ❌ rechazados siempre |
| Eventos `onCombat`, `beforeCombat`, `onOtherUnitPlaced`, `modifyDrive` | ❌ declarados, nadie los dispara |

**Mazos de prueba** (`DeckFactory`, sin efectos):
* **AGGRO**: 25× Infantería Rápida (coste 1, Drive 2) + 15× Vanguardia Pesada (coste 3, Drive 5).
* **CONTROL**: 20× Muro de Energía (coste 1, Drive **4**) + 20× Chispa Temporal (hechizo FAST de coste 1, **sin efecto**).

## 5. El bot (`BotBrainService`)

Una acción por llamada, en este orden (y el servidor lo llama en bucle hasta que le devuelve la prioridad al humano):
1. Si tiene movimientos pendientes → los confirma pasando prioridad.
2. **Defensa:** si tú tienes unidades en un carril y él no → mueve su mejor unidad lista a ese carril (primero el izquierdo, luego el derecho).
3. **Ataque oportunista:** si el carril izquierdo está vacío para los dos → manda allí su mejor unidad lista. *(Nunca ataca el derecho por iniciativa.)*
4. Lanza el primer hechizo que pueda pagar (con objetivos si hacen falta).
5. Baja su unidad más cara que pueda pagar a su base (solo en `MAIN_OPEN`).
6. Si no, pasa.

Con el parche, cualquier jugada ilegal del bot se convierte en “pasar” en vez de colgar la partida.

## 6. Protocolo cliente ⇄ servidor (lo que implementa el cliente Unity)

**WebSocket STOMP 1.2** en `ws://HOST:8080/timewarp-ws` (WebSocket nativo, sin SockJS). Prefijo de envío `/app`, difusión `/topic`.

| Dirección | Destino | Contenido |
|---|---|---|
| Unity → servidor | `/app/game/startLocalMatch` | `{playerId, deckName}` (`"AGGRO"`, `"CONTROL"` o el nombre de un mazo guardado) |
| Unity → servidor | `/app/game/playCard` | `{gameId, playerId, cardId, destination, targetIds[]}` — `destination` = `MY_BASE`, `FIELD_LEFT`, `FIELD_RIGHT` |
| Unity → servidor | `/app/game/moveCard` | `{gameId, playerId, cardId, destination}` |
| Unity → servidor | `/app/game/passPriority` | `{gameId, playerId}` |
| servidor → Unity | `/topic/player/{playerId}` | **Estado completo** de la partida tras cada acción (`GameStateDto`) |
| servidor → Unity | `/topic/player/{playerId}/errors` | `{type:"ERROR", message}` con el texto de la excepción de reglas |

Cosas que conviene saber:
* **Suscríbete antes de enviar `startLocalMatch`**, o pierdes el primer estado. Spring no confirma las suscripciones, así que el cliente espera ~120 ms.
* En el JSON, **`activePlayer` eres siempre tú** (quien mira), no “el jugador con el turno”. El que tiene el turno es el que tiene `hasPriority = true`.
* La mano del rival no viaja (`hand = null`, solo `handSize`).
* Tras un **error** el servidor **no** manda estado nuevo.
* El turno entero del bot ocurre dentro de tu petición; recibes un único estado final.
* Al terminar la partida se envía un último estado y la partida se borra de la RAM. Con el parche, ese estado trae `gameStatus = FINISHED` y `winnerId`.
* **No hay forma de reconectar** a una partida en curso (no existe un endpoint “dame el estado”).
* REST: `POST /api/users/register|login?username=…` → `{id, username}`; `DELETE /api/users/{id}`; `GET /api/menu/cards` y `POST /api/menu/decks`.

## 7. Cómo está hecho el cliente Unity (`Unity/Assets/Timewarp/Scripts`)

| Carpeta / fichero | Qué hace |
|---|---|
| `Net/StompClient.cs` | STOMP 1.2 sobre `ClientWebSocket` (sin paquetes externos). Recibe en otro hilo y entrega los mensajes en `Update()`. |
| `Net/GameConnection.cs` | Habla el protocolo de arriba: `StartLocalMatch`, `PlayCard`, `MoveCard`, `PassPriority` y eventos `StateReceived` / `ErrorReceived` / `Disconnected`. |
| `Net/RestApi.cs` | Login/registro opcional (modo invitado por defecto). |
| `Model/Dtos.cs` | Espejo C# de tus DTO. |
| `Model/GameRules.cs` | Pistas de reglas del lado cliente (qué zonas resaltar, por qué una carta no se puede jugar). **Solo pistas**; manda el servidor. |
| `Model/StateDiff.cs` | Compara dos estados y genera el registro de partida (“Bot juega…”, “Drive 4 → 2”, “Puntos…”). |
| `UI/TimewarpApp.cs` | Punto de entrada automático: cámara, canvas, `EventSystem` (clásico o Input System), menú → conexión → partida. |
| `UI/MenuView.cs`, `UI/GameView.cs`, `UI/CardView.cs`, `UI/HelpView.cs`, `UI/UiKit.cs` | Toda la interfaz uGUI construida por código. |

Para cambiar el aspecto: los colores están en `UiKit.cs`, el tamaño de las cartas en `GameView.cs` (`BoardW`, `BoardH`) y el diseño de una carta en `CardView.cs`.
