# Problemas encontrados y hoja de ruta hacia una demo de verdad

Todo lo de aquí está comprobado ejecutando tu código (no solo leyéndolo), salvo donde diga lo contrario.

## A. Arreglado en los commits de esta rama (12 ficheros modificados, 3 nuevos)

| # | Problema | Evidencia | Arreglo |
|---|---|---|---|
| A1 | **El bot cuelga la partida.** Si el humano mueve una unidad a un carril donde el bot ya tiene tropas, se abre `COMBAT_OPEN` y el bot recibe la prioridad. Su regla 3 intenta *bajar una unidad* sin mirar la fase → `CardPlayService.validateTiming` lanza excepción → sube hasta `GameController`, que **se la manda al humano** y el bot se queda con la prioridad para siempre. | Con tu backend original: **4 de 10 partidas** colgadas. Con el parche: 0 de más de 60. | `BotBrainService`: solo baja unidades en `MAIN_OPEN`, no lanza SLOW fuera de fase, y cualquier acción ilegal se convierte en “pasar prioridad”. `GameController`: tope de 500 iteraciones en el bucle del bot. |
| A2 | `POST /api/users/register` y `/login` devuelven **500**. Spring 6.1 ya no deduce el nombre de `@RequestParam String username` sin compilar con `-parameters`. | Reproducido. | Nombres explícitos en las anotaciones + `<parameters>true</parameters>` en el pom. |
| A3 | `TriggeredEffectsIntegrationTest` (el único test activo) **no compilaba** (el constructor de `Card` ganó el parámetro `description`), así que `mvn test` / `mvn package` fallaban. | Reproducido. | Añadido el argumento. Ahora **pasa**, y confirma que `ON_PLACED` y `ON_DAMAGE_TAKEN` funcionan de BBDD a combate. |
| A4 | El estado que recibe el cliente **no dice quién ha ganado**, ni las keywords, ni la velocidad de los hechizos, ni la pila, ni qué movimientos están pendientes. Además `targetingRequirements` viaja como `TargetingRequirement[requiredCount=1, targetGroup=ENEMY]` (el `toString` de un record). | Visto en el JSON real. | Campos **añadidos** (nada se quita ni se renombra): `gameStatus`, `winnerId`, `consecutivePasses`, `spellStack[]`, `pendingMoveIds[]`, `keywords[]`, `spellSpeed`, `requirements[]`. El cliente funciona también sin ellos. |
| A5 | El pom no tenía el plugin de Spring Boot (no se podía arrancar con Maven) y no había forma de probar sin Supabase. | — | Plugin añadido + `application-demo.properties` (H2 en memoria, carga `data.sql`). |

## ✅ Actualización: lo que ya está resuelto desde que se escribió este documento

| Antes | Estado | Dónde |
|---|---|---|
| B1 Unidad matada por hechizo/efecto seguía en el tablero | ✅ Arreglado (`GameState.cleanupDestroyed`) + tests | `EngineFixesTest` |
| B2 Hechizo SLOW fuera de fase se "tragaba" la carta | ✅ Arreglado (validación antes de gastar recurso) + tests | `EngineFixesTest` |
| B3 `GET /api/menu/cards` devolvía 500 | ✅ Devuelve un DTO de catálogo | `MenuController` |
| B4 No se podía armar ningún mazo (6 cartas) | ✅ Catálogo de **116 cartas** + editor de mazos en Unity + API de mazos | `docs/catalogo/cartas.csv` |
| B5 `DrawCardAction` vacía | ✅ Implementada y registrada como `DRAW` | `EffectFactory` |
| **Nuevo:** los mazos **no se barajaban nunca** (se robaban en el orden de la lista: 25 infanterías seguidas; el bot CONTROL no veía un hechizo hasta el turno ~20) | ✅ Se barajan al empezar | `StartMatchShuffleTest` |
| **Nuevo:** los mazos guardados fallaban al iniciar partida (`LazyInitializationException`) | ✅ `@Transactional` en el repositorio | verificado con Postgres real |
| B7 Bot básico | 🟡 Mejorado: elige objetivos para hechizos y para unidades con "al entrar"; mazo aleatorio por partida. Sigue sin atacar por la derecha ni responder con inteligencia. | `BotBrainService` |

**Siguen abiertos:** B5 (keywords `GLORY`/`CHARGE`, objetivos `GLOBAL`/`LANE`, eventos de efecto sin implementar), B6 (equilibrio de AGGRO/CONTROL; con el bot aleatorio ya no es tan visible), B8 (reconexión), B9, B10, B11, B12 y todo lo de seguridad (`docs/SEGURIDAD.md`).

## B. Detectado y **sin arreglar** (decisiones tuyas, ordenado por importancia)

| # | Problema | Evidencia | Cómo arreglarlo |
|---|---|---|---|
| B1 | **Una unidad destruida por un hechizo/efecto no sale del tablero.** `Card.takeDamage` la marca `destroyed` con Drive 0, pero solo `PlayerState.destroyCard` (usado en combate) la retira. Sigue contando para puntuar y para “carril disputado”. Afecta a `ENEMY_DAMAGE` (Virus Táctico) y `HUNTER_MARK` (Cazador). | Test: `destroyed=true, onBoard=true, inGraveyard=false`. | Barrer unidades `isDestroyed()` (a cementerio) tras resolver la pila, un INSTANT o un trigger; p. ej. `GameState.cleanupDestroyed()`. |
| B2 | **Un hechizo SLOW lanzado en `COMBAT_OPEN` con la pila vacía se “traga” la carta**: `validateTiming` solo lo prohíbe si la pila no está vacía; luego `executePlay` gasta el recurso, quita la carta de la mano, la mete en la pila **y después** lanza `"Slow solo en Main Open"`. | Test: recurso 1→0, carta fuera de la mano, 1 hechizo en pila tras el error. | Comprobar `SLOW ⇒ fase == MAIN_OPEN` en `validateTiming`, antes de gastar nada. (El cliente Unity ya avisa y el bot ya no lo intenta.) |
| B3 | **`GET /api/menu/cards` devuelve 500** al serializar los `Effect` (Jackson no tiene getters). | Reproducido. | Devolver un DTO de carta (id, nombre, coste, Drive, tipo, keywords, descripción…) en vez de `CardTemplate`. |
| B4 | **No se puede armar ningún mazo personalizado válido**: 40 cartas con máx. 3 copias exige ≥ 14 cartas distintas y el catálogo tiene 6. Además `spring.sql.init.mode=never` hace que `data.sql` **no se ejecute**: en Supabase las cartas tienen que estar insertadas a mano. | Código + `application.properties`. | Más cartas; o un mazo “DEMO” en `DeckFactory` hecho con tus cartas reales de `data.sql`. |
| B5 | **Mecánicas sin conectar:** keywords `GLORY` y `CHARGE` (nadie las lee), `DrawCardAction` (vacía y sin registrar), objetivos `GLOBAL`/`LANE` (siempre rechazados), eventos `onCombat`/`beforeCombat`/`onOtherUnitPlaced`/`modifyDrive` (sin implementar en `TriggeredEffect`). | Búsqueda en el código. | Implementarlas cuando entren cartas que las usen. |
| B6 | **Balance:** el Muro de Energía (coste 1, Drive 4) supera a la Infantería Rápida (coste 1, Drive 2). Con AGGRO el bot gana 0-10 casi siempre, y en el espejo CONTROL los muros se destruyen 4 contra 4 y **la partida no termina nunca** (probado: turno 170 con 0-0). | Partidas jugadas. | Retocar números / meter efectos. Plantéate qué debe pasar si nadie puede puntuar (¿límite de turnos?). |
| B7 | **Bot muy básico:** solo ataca por el carril izquierdo; elige objetivos asumiendo que el humano es el jugador A; no usa la pila ni responde a hechizos. | Código. | Cuando el motor esté estable. |
| B8 | **Sin reconexión:** no hay endpoint “dame el estado”; si el cliente se cae, la partida queda huérfana en RAM para siempre; no hay tiempo límite por turno. | Código. | `GET`/mensaje de estado por `gameId`+`playerId`; limpieza por inactividad. |
| B9 | `GameController` solo captura `IllegalStateException`/`IllegalArgumentException`; cualquier otra excepción (p. ej. NPE) no responde nada. Tampoco hay sincronización por partida. | Código. | `@ControllerAdvice`/captura general y un lock por `gameId`. (El cliente Unity serializa sus acciones y desbloquea a los 10 s.) |
| B10 | Tras resolver un combate la prioridad se queda con quien pasó en segundo lugar (no se hace `switchPriority`). Puede ser intencionado. | Código. | Decidirlo y documentarlo. |
| B11 | Sin autenticación: la identidad es un UUID que manda el cliente. Vale para un MVP, no para público. | — | Tokens/sesión cuando haya multijugador. |
| B12 | `System.out.println` de depuración en el dominio (`Card.onDestroyed`, acciones…). | — | Un logger. |

## C. 🔐 Seguridad: contraseña en el repositorio

Tu `application.properties` tiene la **contraseña de la base de datos de Supabase en texto plano** dentro de la URL JDBC, y el zip que me pasaste la incluye (también `.idea/` y `data/*.db`). **No la he usado ni copiado** a ningún sitio (todas mis pruebas fueron con H2 en memoria).
* Si el proyecto ha estado alguna vez en un repositorio (aunque sea privado) o lo has compartido, **cambia esa contraseña en Supabase** hoy.
* Pásala por variable de entorno (`SPRING_DATASOURCE_URL=…`) o con un `application-local.properties` ignorado por git, y añade `.idea/`, `data/` y `target/` a `.gitignore`.

## D. Hoja de ruta sugerida

**Hoy (30 min)** — *Ver tu juego funcionando.*
1. Aplica el parche y arranca con el perfil `demo` (`00_EMPIEZA_AQUI.md`).
2. Juega 3 partidas con AGGRO y 3 con CONTROL. Fíjate en el registro del panel izquierdo y apunta todo lo que **no** coincida con lo que diseñaste (te sorprenderá algo: la puntuación por carril, el orden de prioridad, que el combate se dispare solo tras dos pases…).

**Semana 1 — Una demo “justa” (que dé gusto probar)**
1. Arreglar **B1** y **B2** (son bugs de reglas que falsean cualquier partida con hechizos).
2. Resolver **B6**: números de las cartas de prueba y una condición de final si nadie puntúa.
3. Resolver **B4**: crear un mazo demo con tus 6 cartas reales (o más) en `DeckFactory`, usando `EffectFactory`/`TargetingFactory` como el catálogo. Así podrás probar keywords, objetivos y efectos desde Unity (el cliente ya los soporta: objetivos por clic, insignias de keyword, pila visible).
4. Reactivar y actualizar los 5 tests que tienes comentados (`GameFlowTest`, `FullGameFlowAndStackTest`…): son tu red de seguridad y describen justo los flujos de prioridad/pila.
5. Mejorar el bot lo mínimo (también atacar por la derecha).

**Semana 2 — Motor**
* Conectar lo de **B5** según las cartas que necesites; **B3** (`/api/menu/cards`) y **B8** (reconexión).

**Después — Producto**
* Unity: arte de cartas (hace falta que `CardDto` mande el id de plantilla), arrastrar y soltar, animaciones de combate, sonido, constructor de mazos (necesita B3 y B4), pantalla de resultados.
* Multijugador humano contra humano: hoy `StartMatchService` solo crea partidas “locales contra bot”; habría que añadir salas/emparejamiento.

## E. Cómo comprobar el backend sin abrir Unity

Con el backend en marcha (`tools/README.md`):
* `tools/ClientSmokeTest` juega N partidas completas con el mismo código de red del cliente y chequea invariantes (`dotnet run -c Release -- localhost:8080 AGGRO 10`).
* Es la forma más rápida de saber si un cambio tuyo en las reglas rompe el flujo de prioridad.
