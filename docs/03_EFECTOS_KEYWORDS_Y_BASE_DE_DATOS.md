# Efectos, keywords y objetivos: qué significan y cómo viven en Supabase

Esta guía explica el "mini-lenguaje" de texto que guardas en la tabla `card_templates` (columnas `targets`, `keywords`, `effects`, `spell_speed`) y qué hace el motor con cada palabra. **Todo lo que dice está comprobado con pruebas automáticas** (`EffectsReferenceTest`, `CatalogCheckerTest`) o leyendo el código que lo interpreta (`EffectFactory`, `TargetingFactory`, `TriggeredEffect`, `CardPlayService`, `CombatService`).

> Las cartas, una por una y explicadas en castellano: [`docs/catalogo/CARTAS_EXPLICADAS.md`](catalogo/CARTAS_EXPLICADAS.md).

---

## 1. En un minuto

**Una carta = una fila de la tabla `card_templates`.** Cuatro columnas son texto con un formato fijo que el servidor "traduce" a comportamiento cuando empieza una partida:

| Columna | Pregunta que responde | Ejemplo |
|---|---|---|
| `targets` | ¿**A quién** hay que señalar al jugarla? | `1:ENEMY` |
| `keywords` | ¿Qué **reglas especiales** tiene (moverse, entrar en un carril…)? | `DISRUPTION,JUMPER` |
| `effects` | ¿**Cuándo** y **qué** hace? | `ON_PLACED:ENEMY_DAMAGE:2` |
| `spell_speed` | (hechizos) ¿**Cuándo se puede lanzar**? | `FAST` |

Si lo lees en voz alta, `ON_PLACED:ENEMY_DAMAGE:2` es: **"al entrar en juego: hace 2 de daño"**, y como hace daño a *alguien*, esa carta necesita en `targets` algo como `1:ENEMY` ("elige 1 unidad enemiga").

---

## 2. Qué significan las líneas de tu captura

Son valores de la columna `effects`. Formato: `DISPARADOR:ACCIÓN:valor`.

| Valor en `effects` | Se lee | Carta del catálogo |
|---|---|---|
| `ON_PLACED:ALLY_BOOST:2` | Al entrar en juego: da **+2 de Drive** a las unidades **aliadas** que hayas elegido | Sacerdote de Ra |
| `ON_PLACED:HUNTER_MARK:1:5` | Al entrar: hace **1 de daño** al objetivo; cuando ese objetivo muera (ahora o más adelante), **+5 de Drive** para el cazador | Cazador de Recompensas |
| `NONE` | Sin efecto (unidad "vanilla") | Legionario, Infantería Rápida… |
| `ON_COMBAT_WIN:READY_SELF` | **Gloria**: al ganar un combate, la unidad se **recupera** (deja de estar agotada y puede volver a moverse) | Gladiador |
| `ON_PLACED:ENEMY_DAMAGE:2` | Al entrar: hace **2 de daño** a la unidad **enemiga** elegida | Tribuno de la Legión, Hacker Fantasma |
| `ON_PLACED:ALLY_BOOST:3` | Igual que la primera, con +3 | Testudo (hechizo) |

Varios efectos en la misma carta se separan con **`;`**: `ON_DAMAGE_TAKEN:SELF_BUFF:1;ON_COMBAT_WIN:READY_SELF` (Avatar Corrupto).

---

## 3. Las columnas de `card_templates`

(El nombre de columna en la base de datos es el del campo Java en minúsculas con guiones bajos.)

| Columna | Tipo | Valores / formato | Notas |
|---|---|---|---|
| `id` | texto | único, p. ej. `U_RM_02` | Es la clave primaria; los mazos guardados apuntan a ella (`deck_cards.card_template_id`). **No la cambies** si hay mazos con esa carta. |
| `name` | texto | libre | Se muestra en la carta. |
| `description` | texto | libre | **Es solo texto para mostrar**: no se genera a partir de `effects`. Si cambias un efecto, cambia también la descripción. |
| `base_cost` | número | 0 a 10 | Recurso que cuesta jugarla. El máximo de recurso es 10. |
| `base_drive` | número | ≥ 1 en unidades, `0` en hechizos | **Drive = fuerza de ataque y vida a la vez** (ver §4). |
| `card_type` | texto | `UNIT` o `SPELL` | |
| `spell_speed` | texto | unidades: `NOT_A_SPELL` · hechizos: `SLOW`, `FAST`, `INSTANT` | Ver §8. |
| `faction` | texto | `ANCIENT_EGYPT`, `ROMANS`, `STEAMPUNK`, `GLITCHPOP` | Un mazo puede tener como máximo 2 facciones. |
| `targets` | texto | `NONE` o `cantidad:GRUPO` (varios con `;`) | Ver §6. |
| `keywords` | texto | `NONE` o nombres separados por **coma** | Ver §7. |
| `effects` | texto | `NONE` o `DISPARADOR:ACCIÓN:valor` (varios con `;`) | Ver §5. |

**Tres separadores distintos, no los mezcles:** `:` separa las partes de una regla · `;` separa reglas (en `effects` y `targets`) · `,` separa keywords.

---

## 4. Conceptos del juego que necesitas

* **Drive** es la vez la **fuerza** y la **vida** de una unidad. En combate cada bando suma el Drive de sus unidades del carril y se lo reparte como daño al rival. **Recibir daño baja el Drive** (y por tanto también el ataque) y **no se cura** entre turnos. Un bonus (`+N`) lo sube. Si llega a 0, la unidad muere.
* **Agotada / lista:** mover una unidad la agota y una agotada no puede moverse. Todas se recuperan al empezar cada turno, o antes con `READY_SELF`. (Cada turno también se renueva el recurso y cada jugador roba una carta.)
* **Zonas:** tu Base y dos carriles. Hay combate en un carril cuando ambos jugadores tienen unidades en él ("disputado").
* **Aliado / enemigo** se miran siempre **desde quien juega la carta**.

---

## 5. La columna `effects`

### 5.1 Disparadores (el "cuándo")

| Disparador | Se dispara… | Objetivos | Ejemplo |
|---|---|---|---|
| `ON_PLACED` | **Unidad:** al bajarla de la mano; el efecto **va a la pila** y ocurre cuando los dos pasáis seguidos (el rival puede responder con FAST/INSTANT; si no haría nada, no se abre pila). **Hechizo:** cuando **se resuelve** (en el momento si es `INSTANT`; si es `FAST`/`SLOW`, cuando se vacía la pila) | **Sí**: los elegidos en `targets` | `ON_PLACED:ENEMY_DAMAGE:2` |
| `ON_COMBAT_WIN` | La unidad **gana** un combate en su carril (sumó más Drive que el rival; un empate no cuenta). Solo las unidades **que siguen vivas** | No | `ON_COMBAT_WIN:SELF_BUFF:2` |
| `ON_DAMAGE_TAKEN` | La unidad recibe daño **y sobrevive** | No | `ON_DAMAGE_TAKEN:SELF_BUFF:1` |
| `ON_DESTROYED` | La unidad es destruida | No | (ninguna carta del catálogo lo usa; lo usa por dentro `HUNTER_MARK`) |
| `ON_MOVEMENT_CONFIRMED` | El jugador **confirma** el movimiento de la unidad (al pasar prioridad) | No | `ON_MOVEMENT_CONFIRMED:CHARGE_BUFF:1` |

> ⚠️ **Solo `ON_PLACED` recibe objetivos.** Una acción que necesita objetivos (`ENEMY_DAMAGE`, `ALLY_BOOST`, `HUNTER_MARK`) con otro disparador **no hace nada**.
>
> ⚠️ **Un disparador mal escrito no da error, simplemente no se dispara nunca** (`ON_PLACE` en vez de `ON_PLACED`). El servidor lo avisa al arrancar (§11).
>
> En un hechizo solo funciona `ON_PLACED` (un hechizo no está en el tablero para recibir daño, ganar combates, etc.).

### 5.2 Acciones (el "qué")

| Acción | Valores | Qué hace | Detalles comprobados |
|---|---|---|---|
| `ENEMY_DAMAGE:n` | daño | Hace `n` de daño a **cada objetivo enemigo** | Ignora a los aliados que se le pasen. El daño es permanente. Si el objetivo ya murió cuando se resuelve el hechizo, no hace nada. |
| `ALLY_BOOST:n` | bonus | Suma `n` de Drive a **cada objetivo aliado** | Ignora a los enemigos. El bonus es permanente. |
| `SELF_BUFF:n` | bonus | La propia unidad gana `n` de Drive | No usa objetivos. Típico con `ON_COMBAT_WIN` u `ON_DAMAGE_TAKEN`. |
| `CHARGE_BUFF:n` | bonus | Como `SELF_BUFF`, pero **solo si la unidad acaba en un carril** | Con `ON_MOVEMENT_CONFIRMED`. Volver a la Base no da nada. **Es permanente** (no "mientras dure el combate") y **se repite**: una unidad con `JUMPER` que salta de carril a carril gana +n **cada vez**. |
| `READY_SELF` | — | Quita el estado de "agotada": la unidad puede volver a moverse | Es la "Gloria" cuando va con `ON_COMBAT_WIN`. |
| `DRAW:n` | cartas | El dueño **roba `n` cartas** | Con el mazo vacío no roba (todavía no hay daño por fatiga). |
| `HUNTER_MARK:daño:recompensa` | dos números | Hace `daño` al objetivo. Si **muere en el acto**, la unidad gana `recompensa` de Drive. Si sobrevive, queda **marcado**: cuando muera, **por la causa que sea**, la unidad gana `recompensa` | Es la única acción con **dos** valores. La marca sigue aunque el cazador ya no esté en el tablero (entonces no cobra). |

**Casos límite que conviene tener presentes**

* **Daño letal y `ON_DAMAGE_TAKEN`.** Una unidad que recibe daño suficiente para morir **muere sin disparar** su efecto de "al recibir daño". (Antes de esta versión, un `SELF_BUFF` le devolvía Drive justo antes de comprobar la muerte y la salvaba: el texto "si sobrevive al daño, gana +2" se cumplía sin sobrevivir.)
* **Un efecto en un hechizo se aplica al resolverse**, no al lanzarlo. Los objetivos se validan al lanzar.

---

## 6. La columna `targets`

Formato: `cantidad:GRUPO`. Varios requisitos con `;` (`1:ALLY;1:ENEMY`). `NONE` = no pide objetivos.

| Grupo | Qué unidades vale señalar (según quien juega la carta) |
|---|---|
| `ALLY` | Unidades **tuyas** |
| `ENEMY` | Unidades **del rival** |
| `ANY_UNIT` | Cualquier unidad en juego |
| ~~`GLOBAL`~~, ~~`LANE`~~ | **No soportados**: existen en el código pero el validador los rechaza siempre. Una carta con `GLOBAL` no se podría lanzar. |

**Reglas que el servidor aplica siempre** (un cliente modificado no puede saltárselas):

| | Hechizos | Unidades (`ON_PLACED`) |
|---|---|---|
| Cantidad | **Exacta** (si pide 2, hay que elegir 2) | **Como máximo** lo que pide. Se puede elegir menos, o ninguno: el efecto simplemente no afecta a nadie (así una unidad se puede bajar aunque no haya objetivos) |
| Grupo | Cada objetivo debe cumplir el grupo pedido | Igual |
| Dónde | Solo **unidades en juego** (Base o carriles). No vale una carta de la mano ni del cementerio | Igual |
| Repetidos | No se puede elegir la misma unidad dos veces | Igual |
| Sin `targets` | No se admite ningún objetivo | Igual |

> 🔧 **Corregido en esta versión:** antes las *unidades* no validaban sus objetivos (solo los hechizos). Con un cliente modificado se podían enviar más objetivos de los que dice la carta (p. ej. `ON_PLACED:ENEMY_DAMAGE:2` con `1:ENEMY` dañando a 6 enemigos) o cartas de la mano. Ahora se rechaza (`EffectsReferenceTest`).

**Emparejamiento entre `targets` y `effects`:** el grupo debe tener sentido con la acción. `ENEMY_DAMAGE` y `HUNTER_MARK` necesitan `ENEMY` (o `ANY_UNIT`); `ALLY_BOOST` necesita `ALLY` (o `ANY_UNIT`). Si no, el jugador elige objetivos y el efecto no hace nada.

---

## 7. La columna `keywords`

Separadas por **coma** y en **mayúsculas**: `DISRUPTION,JUMPER`. `NONE` si no tiene.

| Keyword | Estado | Qué hace |
|---|---|---|
| `JUMPER` | ✅ funciona | La unidad puede **moverse de un carril a otro**. Sin ella, solo Base ⇄ carril. |
| `DISRUPTION` | ✅ funciona | La unidad puede **bajarse directamente a un carril** (en vez de a tu Base), y solo si ese carril ya está **disputado**. Sin esta keyword, las unidades solo entran por la Base. |
| `GLORY` | ❌ el motor **no la lee** | Existe en el código, pero ninguna regla la comprueba. Para "Gloria" usa el efecto `ON_COMBAT_WIN:READY_SELF`. |
| `CHARGE` | ❌ el motor **no la lee** | Igual. Para "Carga" usa `ON_MOVEMENT_CONFIRMED:CHARGE_BUFF:n`. |

Las keywords **no tienen valores** ni se combinan con `:`. Una keyword desconocida (p. ej. `JUMPE`) hace **fallar la partida** en cuanto sale esa carta (§11). Los hechizos no usan keywords.

---

## 8. `spell_speed`: cuándo se puede lanzar un hechizo

| Velocidad | Cuándo | Qué hace al lanzarla |
|---|---|---|
| `SLOW` | Solo en tu **fase principal abierta** y con la pila vacía. No se puede lanzar como respuesta | Va a la **pila**; cierra la fase (el rival puede responder) |
| `FAST` | En tu fase principal o **como respuesta** | Va a la pila; cierra la fase en curso |
| `INSTANT` | Cuando tienes prioridad | **Se resuelve al momento**, sin pila: el rival no puede responder |
| `NOT_A_SPELL` | Solo para **unidades** | — |

La pila se resuelve **de arriba abajo** (lo último lanzado se resuelve primero) cuando los dos jugadores pasan seguidos. Cada hechizo que se resuelve ejecuta su `ON_PLACED` y va al cementerio.

---

## 9. Cómo se integra con Supabase (de la celda a la carta en juego)

```
Supabase / PostgreSQL                         Servidor Java                                      Unity
─────────────────────                         ────────────                                       ─────
card_templates (1 fila = 1 carta)
   │  JPA lee la fila
   ▼
CardTemplateEntity  ──►  CardTemplateMapper
   (texto tal cual)         ├─ targets  ──► TargetingFactory ──► [TargetingRequirement(n, grupo)]
                            ├─ keywords ──► Keyword.valueOf    ──► [JUMPER, DISRUPTION…]
                            ├─ effects  ──► EffectFactory      ──► [TriggeredEffect(disparador, Acción)]
                            └─ resto    ──► coste, Drive, tipo, velocidad, facción
                                  │
                                  ▼
                            CardTemplate (modelo de dominio, inmutable)
                                  │  al empezar la partida: Card.fromTemplate(...)  (una copia por carta del mazo)
                                  ▼
                            Card (la carta viva: Drive actual, agotada, zona…)
                                  │  el motor llama a sus efectos: onPlaced, onCombatWin, onDamageTaken…
                                  ▼
                            CardDto  ───── WebSocket ─────►  tu carta en pantalla
                            (nombre, coste, Drive, keywords, velocidad, requisitos de objetivo, descripción)
```

Y los mazos: `decks` (un mazo de un usuario) → `deck_cards` (`card_template_id` + `quantity`) → `card_templates`.

**Qué tienes que saber de esta integración**

* **No hay caché.** El servidor lee `card_templates` al empezar cada partida y cuando el editor pide el catálogo. **Si cambias una carta en Supabase, la ves en la siguiente partida, sin reiniciar.** (El informe de validación del §11 sí se hace solo al arrancar.)
* **Cambiar el `id` de una carta** rompe los mazos guardados que la contienen (clave ajena). Para renombrar, crea otra carta.
* **Dos formas de dar de alta cartas:**
  1. *Recomendada*: editar `docs/catalogo/cartas.csv` (Excel vale, separador `;`), ejecutar `python tools/catalog/generar_sql.py` (valida el CSV y genera `data.sql` + `docs/supabase/cartas.sql`), pegar `cartas.sql` en el SQL Editor de Supabase (es repetible, hace *upsert*), y `python tools/catalog/explicar_catalogo.py` para regenerar el catálogo explicado. Así el CSV es la única fuente de verdad y la base de datos nunca queda a medias.
  2. *A mano* desde el Table Editor de Supabase: cómodo, pero **nada valida lo que escribes**. Reinicia el servidor y mira el informe `[CATALOGO]`.
* **`data.sql` solo se carga contra H2** (la base en memoria de desarrollo). Contra Supabase nunca se ejecuta: las cartas las pones tú con `cartas.sql`.
* **Qué ve el cliente Unity:** `name`, `description`, coste, Drive, tipo, velocidad, facción, keywords (las muestra como insignias) y los requisitos de objetivo (para pedirte los objetivos). **No ve `effects`**: el texto que lees en la carta es `description`, escrito a mano.

---

## 10. Cartas del catálogo agrupadas por receta

> Esta tabla resume los **patrones** con las cartas originales. Las 116 cartas (incluidas las 80 nuevas, con efectos combinados) están, una por una y con su receta traducida, en [`catalogo/CARTAS_EXPLICADAS.md`](catalogo/CARTAS_EXPLICADAS.md).

**Combinaciones nuevas que conviene conocer** (todas están en el catálogo y probadas en `EffectsReferenceTest`):

| Receta | Qué consigue |
|---|---|
| `ON_PLACED:ENEMY_DAMAGE:3;ON_PLACED:ALLY_BOOST:3` con `targets` `1:ENEMY;1:ALLY` | Cada acción solo afecta a los objetivos de su bando: un golpe y un refuerzo con una sola carta |
| `ON_DESTROYED:DRAW:1` | "Al morir, roba": es el único efecto útil al morir con las acciones actuales (las demás necesitan estar vivas o tener objetivos) |
| `ON_COMBAT_WIN:DRAW:1` / `ON_COMBAT_WIN:SELF_BUFF:1;ON_COMBAT_WIN:READY_SELF` | Recompensas por ganar combates, y varias a la vez |
| `ON_DAMAGE_TAKEN:SELF_BUFF:1;ON_DAMAGE_TAKEN:READY_SELF;ON_DAMAGE_TAKEN:DRAW:1` | "Contraataque": solo si **sobrevive** al daño |
| `ON_MOVEMENT_CONFIRMED:DRAW:1` | Explorar da cartas (con `JUMPER` puede repetirse cada turno) |
| `ON_PLACED:HUNTER_MARK:1:3;ON_PLACED:DRAW:1` | Marca y además roba |

> ⚠️ `HUNTER_MARK` no filtra por bando: no lo combines con objetivos `ALLY` (dañaría a tu propia unidad). Úsalo solo con `ENEMY`.

| Receta | Cartas | En palabras |
|---|---|---|
| unidad sin nada | Infantería Rápida, Escriba del Templo, Legionario, Autómata de Vapor… | Solo cuerpo |
| `NONE` + `JUMPER` | Dron Explorador | Puede saltar de carril |
| `NONE` + `DISRUPTION` | Vélite Emboscador | Puede bajar directo a un carril disputado |
| `1:ALLY` · `ON_PLACED:ALLY_BOOST:2` | Sacerdote de Ra, Centurión de Vanguardia | Al entrar, +2 a un aliado |
| `2:ALLY` · `ON_PLACED:ALLY_BOOST:1` | Faraón Eterno | Al entrar, +1 a dos aliados |
| `1:ENEMY` · `ON_PLACED:ENEMY_DAMAGE:2` | Tribuno de la Legión (+`DISRUPTION`: Hacker Fantasma) | Al entrar, 2 de daño a un enemigo |
| `1:ENEMY` · `ON_PLACED:HUNTER_MARK:1:5` | Cazador de Recompensas | 1 de daño; +5 cuando ese enemigo muera |
| `ON_PLACED:DRAW:1` | Ingeniero Chispeante, DJ Sintético | Al entrar, roba 1 |
| `ON_COMBAT_WIN:SELF_BUFF:n` | Píxel Rebelde (+1), Momia Vengativa (+2) | Crece al ganar un combate |
| `ON_COMBAT_WIN:READY_SELF` | Gladiador | Gloria |
| `ON_DAMAGE_TAKEN:SELF_BUFF:n` | Guardián de Arena (+1), Ciborg Mutante (+2) | Crece si sobrevive al daño |
| las dos anteriores juntas | Avatar Corrupto | Crece con el daño y tiene Gloria |
| `JUMPER` + `ON_MOVEMENT_CONFIRMED:CHARGE_BUFF:1` | Carro Blindado | +1 cada vez que acaba su movimiento en un carril |
| hechizo `FAST` · `1:ENEMY` · `ENEMY_DAMAGE:n` | Lluvia de Jabalinas (2), Plaga de Langostas (3), Virus Táctico (3), Rayo de Tesla (4) | Daño directo, con respuesta posible |
| hechizo `INSTANT` · `1:ALLY` · `ALLY_BOOST:n` | Bendición del Nilo (+2), Testudo (+3) | Refuerzo sin respuesta posible |
| hechizo `INSTANT` · `1:ENEMY` · `ENEMY_DAMAGE:1` | Cortocircuito | Daño pequeño sin respuesta |
| hechizo `INSTANT` · `DRAW:1` | Engranaje Extra | Roba 1 |
| hechizo `SLOW` · `2:ENEMY` · `ENEMY_DAMAGE:n` | Tormenta de Arena (3), Desfase Temporal (5) | Daño a dos enemigos (caro y lento) |
| hechizo `SLOW` · `2:ALLY` · `ALLY_BOOST:2` | Orden de Carga | +2 a dos aliados |
| hechizo `SLOW` · `DRAW:2` | Planos Secretos | Roba 2 |

---

## 11. Errores típicos y cómo los avisa el servidor

Al arrancar, el backend revisa todas las filas de `card_templates` y escribe en la consola un informe como este (la partida **no** se bloquea por ello):

```
[CATALOGO] U_XX_01 'Mi carta': AVISO el disparador 'ON_PLACE' NO existe: el efecto no se disparara nunca (validos: [...])
[CATALOGO] 37 cartas revisadas, 1 aviso(s).
```

(Con el catálogo actual sale `116 cartas revisadas, 0 aviso(s).`)

| Lo que escribes | Qué pasa | Lo que verás |
|---|---|---|
| `ON_PLACE:ENEMY_DAMAGE:2` (disparador mal escrito) | **Silencioso:** el efecto no se dispara nunca | `AVISO … el disparador 'ON_PLACE' NO existe` |
| `ON_PLACED:ENEMY_DAMGE:2` (acción mal escrita) | El efecto se ignora (solo escribe en la consola del servidor) | `AVISO … la accion 'ENEMY_DAMGE' no existe` |
| `ON_PLACED:ENEMY_DAMAGE:2` con `targets` = `NONE` | La carta no tiene a quién dañar: no hace nada | `AVISO … necesita 'targets'` |
| `ON_DAMAGE_TAKEN:ENEMY_DAMAGE:2` | Solo `ON_PLACED` recibe objetivos: no hace nada | `AVISO … solo funciona con ON_PLACED` |
| `ON_PLACED:SELF_BUFF:dos` | **La partida falla** al sacar esa carta (`NumberFormatException`) | `ERROR … no es un numero` |
| `ON_PLACED:HUNTER_MARK:1` (falta el 2.º número) | **La partida falla** al sacar esa carta | `ERROR … necesita 2 valor(es)` |
| `keywords` = `JUMPE` | **La partida falla** al sacar esa carta | `ERROR … keyword 'JUMPE' desconocida` |
| `keywords` = `JUMPER;DISRUPTION` | **La partida falla** (se separan con coma) | `ERROR … se separan con COMA` |
| `targets` = `1:GLOBAL` | La carta no se puede lanzar | `ERROR … grupo de objetivo 'GLOBAL' no soportado` |
| `card_type` = `HECHIZO`, `faction` = `ROMANOS` | **La partida falla** (los valores son en inglés y en mayúsculas) | `ERROR … card_type debe ser UNIT o SPELL` |
| unidad con `spell_speed` = `SLOW` | **La partida falla** | `ERROR … debe tener spell_speed NOT_A_SPELL` |

---

## 12. Cómo añadir cosas nuevas

**Una carta nueva con lo que ya existe:** añade una fila al CSV (ver §9) y regenera. Ejemplo — "Arquero": coste 3, Drive 2, hace 1 de daño al entrar a una unidad enemiga:

```
"U_RM_07";"Arquero";"Al entrar, hace 1 de daño a una unidad enemiga.";"3";"2";"UNIT";"NOT_A_SPELL";"1:ENEMY";"NONE";"ON_PLACED:ENEMY_DAMAGE:1";"ROMANS"
```

**Una acción nueva** (p. ej. `HEAL`): 1) clase nueva en `domain/model/effect/action/…` que implemente `Action`; 2) registrarla en `EffectFactory` (`actionRegistry.put("HEAL", v -> new HealAction(...))`); 3) añadirla a `ACTIONS` en `CatalogChecker` y `tools/catalog/generar_sql.py`; 4) un test en `EffectsReferenceTest`.

**Un disparador nuevo** (p. ej. `ON_OTHER_UNIT_PLACED`): `Effect` ya declara el método vacío; hay que **implementarlo en `TriggeredEffect`** *y* **llamarlo desde donde ocurre el evento** (hoy `onOtherUnitPlaced`, `onCombat`, `beforeCombat` y `modifyDrive` están declarados pero nadie los llama), y añadirlo a `TRIGGERS` en `CatalogChecker`.

**Una keyword nueva:** añadirla al enum `Keyword` y consultar `card.hasKeyword(...)` en la regla correspondiente (así funcionan `JUMPER` en `PlayerState.validateMovement` y `DISRUPTION` en `CardPlayService`). Un nombre en el enum sin ninguna regla que lo lea (como `GLORY` o `CHARGE`) no hace nada.

---

## 13. Lo que existe en el código pero aún no funciona

| Pieza | Estado |
|---|---|
| Keywords `GLORY`, `CHARGE` | Sin lógica (usa los efectos equivalentes) |
| Grupos `GLOBAL`, `LANE` | Rechazados siempre |
| Eventos `onCombat`, `beforeCombat`, `onOtherUnitPlaced`, `modifyDrive` | Declarados en `Effect`, no implementados en `TriggeredEffect` ni disparados |
| Daño por fatiga con el mazo vacío | No existe (no roba y ya) |
| Efectos "mientras" (bonus temporal solo durante el combate) | No existen: todos los bonus son permanentes |
