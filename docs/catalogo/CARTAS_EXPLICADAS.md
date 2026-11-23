# Catálogo explicado, carta por carta

> Generado con `python tools/catalog/explicar_catalogo.py` a partir de `docs/catalogo/cartas.csv`. **No lo edites a mano.**
> Qué significa cada trozo de las columnas `targets`, `keywords` y `effects`: `docs/03_EFECTOS_KEYWORDS_Y_BASE_DE_DATOS.md`.

## Antiguo Egipto

| id | carta | coste | Drive | tipo | `targets` | `keywords` | `effects` | en palabras |
|---|---|---|---|---|---|---|---|---|
| U_AE_01 | **Infantería Rápida** | 1 | 2 | Unidad | `NONE` | `NONE` | `NONE` | Sin habilidades: solo cuerpo. |
| U_AE_02 | **Escriba del Templo** | 2 | 3 | Unidad | `NONE` | `NONE` | `NONE` | Sin habilidades: solo cuerpo. |
| U_AE_03 | **Guardián de Arena** | 3 | 4 | Unidad | `NONE` | `NONE` | `ON_DAMAGE_TAKEN:SELF_BUFF:1` | Al recibir daño (solo si sobrevive): gana +1 de Drive. |
| U_AE_04 | **Momia Vengativa** | 3 | 3 | Unidad | `NONE` | `NONE` | `ON_COMBAT_WIN:SELF_BUFF:2` | Al ganar un combate: gana +2 de Drive. |
| U_AE_05 | **Sacerdote de Ra** | 3 | 3 | Unidad | `1:ALLY` | `NONE` | `ON_PLACED:ALLY_BOOST:2` | elige 1 unidad aliada. Al entrar en juego: da +2 de Drive a cada objetivo aliado. |
| U_AE_06 | **Faraón Eterno** | 6 | 7 | Unidad | `2:ALLY` | `NONE` | `ON_PLACED:ALLY_BOOST:1` | elige 2 unidades aliadas. Al entrar en juego: da +1 de Drive a cada objetivo aliado. |
| S_AE_01 | **Bendición del Nilo** | 1 | — | Hechizo Instantánea | `1:ALLY` | `NONE` | `ON_PLACED:ALLY_BOOST:2` | elige 1 unidad aliada. Al resolverse: da +2 de Drive a cada objetivo aliado. |
| S_AE_02 | **Plaga de Langostas** | 3 | — | Hechizo Rápida | `1:ENEMY` | `NONE` | `ON_PLACED:ENEMY_DAMAGE:3` | elige 1 unidad enemiga. Al resolverse: hace 3 de daño a cada objetivo enemigo. |
| S_AE_03 | **Tormenta de Arena** | 5 | — | Hechizo Lenta | `2:ENEMY` | `NONE` | `ON_PLACED:ENEMY_DAMAGE:3` | elige 2 unidades enemigas. Al resolverse: hace 3 de daño a cada objetivo enemigo. |

## Romanos

| id | carta | coste | Drive | tipo | `targets` | `keywords` | `effects` | en palabras |
|---|---|---|---|---|---|---|---|---|
| U_RM_01 | **Centurión de Vanguardia** | 3 | 4 | Unidad | `1:ALLY` | `NONE` | `ON_PLACED:ALLY_BOOST:2` | elige 1 unidad aliada. Al entrar en juego: da +2 de Drive a cada objetivo aliado. |
| U_RM_02 | **Cazador de Recompensas** | 4 | 3 | Unidad | `1:ENEMY` | `NONE` | `ON_PLACED:HUNTER_MARK:1:5` | elige 1 unidad enemiga. Al entrar en juego: hace 1 de daño al objetivo; cuando ese objetivo muere (ahora o más tarde) gana +5 de Drive. |
| U_RM_03 | **Legionario** | 2 | 3 | Unidad | `NONE` | `NONE` | `NONE` | Sin habilidades: solo cuerpo. |
| U_RM_04 | **Vélite Emboscador** | 2 | 2 | Unidad | `NONE` | `DISRUPTION` | `NONE` | Disrupción: puede bajar directa a un carril disputado. |
| U_RM_05 | **Gladiador** | 4 | 5 | Unidad | `NONE` | `NONE` | `ON_COMBAT_WIN:READY_SELF` | Al ganar un combate: se recupera (vuelve a poder moverse). |
| U_RM_06 | **Tribuno de la Legión** | 5 | 5 | Unidad | `1:ENEMY` | `NONE` | `ON_PLACED:ENEMY_DAMAGE:2` | elige 1 unidad enemiga. Al entrar en juego: hace 2 de daño a cada objetivo enemigo. |
| S_RM_01 | **Testudo** | 2 | — | Hechizo Instantánea | `1:ALLY` | `NONE` | `ON_PLACED:ALLY_BOOST:3` | elige 1 unidad aliada. Al resolverse: da +3 de Drive a cada objetivo aliado. |
| S_RM_02 | **Lluvia de Jabalinas** | 2 | — | Hechizo Rápida | `1:ENEMY` | `NONE` | `ON_PLACED:ENEMY_DAMAGE:2` | elige 1 unidad enemiga. Al resolverse: hace 2 de daño a cada objetivo enemigo. |
| S_RM_03 | **Orden de Carga** | 4 | — | Hechizo Lenta | `2:ALLY` | `NONE` | `ON_PLACED:ALLY_BOOST:2` | elige 2 unidades aliadas. Al resolverse: da +2 de Drive a cada objetivo aliado. |

## Steampunk

| id | carta | coste | Drive | tipo | `targets` | `keywords` | `effects` | en palabras |
|---|---|---|---|---|---|---|---|---|
| U_SP_01 | **Dron Explorador** | 2 | 1 | Unidad | `NONE` | `JUMPER` | `NONE` | Salto: puede pasar de un carril a otro. |
| U_SP_02 | **Autómata de Vapor** | 2 | 3 | Unidad | `NONE` | `NONE` | `NONE` | Sin habilidades: solo cuerpo. |
| U_SP_03 | **Ingeniero Chispeante** | 3 | 2 | Unidad | `NONE` | `NONE` | `ON_PLACED:DRAW:1` | Al entrar en juego: roba 1 carta. |
| U_SP_04 | **Carro Blindado** | 4 | 4 | Unidad | `NONE` | `JUMPER` | `ON_MOVEMENT_CONFIRMED:CHARGE_BUFF:1` | Salto: puede pasar de un carril a otro. Al confirmar su movimiento: gana +1 de Drive si acaba en un carril. |
| U_SP_05 | **Zepelín de Asalto** | 4 | 3 | Unidad | `NONE` | `DISRUPTION,JUMPER` | `NONE` | Disrupción: puede bajar directa a un carril disputado; Salto: puede pasar de un carril a otro. |
| U_SP_06 | **Gólem de Hierro** | 6 | 8 | Unidad | `NONE` | `NONE` | `NONE` | Sin habilidades: solo cuerpo. |
| S_SP_01 | **Engranaje Extra** | 1 | — | Hechizo Instantánea | `NONE` | `NONE` | `ON_PLACED:DRAW:1` | Al resolverse: roba 1 carta. |
| S_SP_02 | **Rayo de Tesla** | 4 | — | Hechizo Rápida | `1:ENEMY` | `NONE` | `ON_PLACED:ENEMY_DAMAGE:4` | elige 1 unidad enemiga. Al resolverse: hace 4 de daño a cada objetivo enemigo. |
| S_SP_03 | **Planos Secretos** | 3 | — | Hechizo Lenta | `NONE` | `NONE` | `ON_PLACED:DRAW:2` | Al resolverse: roba 2 cartas. |

## Glitchpop

| id | carta | coste | Drive | tipo | `targets` | `keywords` | `effects` | en palabras |
|---|---|---|---|---|---|---|---|---|
| U_GP_01 | **Ciborg Mutante** | 4 | 5 | Unidad | `NONE` | `NONE` | `ON_DAMAGE_TAKEN:SELF_BUFF:2` | Al recibir daño (solo si sobrevive): gana +2 de Drive. |
| U_GP_02 | **Píxel Rebelde** | 1 | 2 | Unidad | `NONE` | `NONE` | `ON_COMBAT_WIN:SELF_BUFF:1` | Al ganar un combate: gana +1 de Drive. |
| U_GP_03 | **DJ Sintético** | 3 | 3 | Unidad | `NONE` | `NONE` | `ON_PLACED:DRAW:1` | Al entrar en juego: roba 1 carta. |
| U_GP_04 | **Hacker Fantasma** | 4 | 3 | Unidad | `1:ENEMY` | `DISRUPTION` | `ON_PLACED:ENEMY_DAMAGE:2` | elige 1 unidad enemiga. Disrupción: puede bajar directa a un carril disputado. Al entrar en juego: hace 2 de daño a cada objetivo enemigo. |
| U_GP_05 | **Avatar Corrupto** | 5 | 6 | Unidad | `NONE` | `NONE` | `ON_DAMAGE_TAKEN:SELF_BUFF:1;ON_COMBAT_WIN:READY_SELF` | Al recibir daño (solo si sobrevive): gana +1 de Drive. Al ganar un combate: se recupera (vuelve a poder moverse). |
| U_GP_06 | **Titán Glitch** | 7 | 10 | Unidad | `NONE` | `NONE` | `NONE` | Sin habilidades: solo cuerpo. |
| S_GP_01 | **Virus Táctico** | 2 | — | Hechizo Rápida | `1:ENEMY` | `NONE` | `ON_PLACED:ENEMY_DAMAGE:3` | elige 1 unidad enemiga. Al resolverse: hace 3 de daño a cada objetivo enemigo. |
| S_GP_02 | **Cortocircuito** | 1 | — | Hechizo Instantánea | `1:ENEMY` | `NONE` | `ON_PLACED:ENEMY_DAMAGE:1` | elige 1 unidad enemiga. Al resolverse: hace 1 de daño a cada objetivo enemigo. |
| S_GP_03 | **Desfase Temporal** | 6 | — | Hechizo Lenta | `2:ENEMY` | `NONE` | `ON_PLACED:ENEMY_DAMAGE:5` | elige 2 unidades enemigas. Al resolverse: hace 5 de daño a cada objetivo enemigo. |
