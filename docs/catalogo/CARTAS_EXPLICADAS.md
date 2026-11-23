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
| U_AE_07 | **Escarabajo Sagrado** | 1 | 1 | Unidad | `NONE` | `NONE` | `ON_DESTROYED:DRAW:1` | Al ser destruida: roba 1 carta. |
| U_AE_08 | **Sirviente del Nilo** | 2 | 2 | Unidad | `NONE` | `NONE` | `ON_PLACED:DRAW:1` | Al entrar en juego: roba 1 carta. |
| U_AE_09 | **Embalsamador** | 3 | 2 | Unidad | `1:ALLY` | `NONE` | `ON_PLACED:ALLY_BOOST:2;ON_DESTROYED:DRAW:1` | elige 1 unidad aliada. Al entrar en juego: da +2 de Drive a cada objetivo aliado. Al ser destruida: roba 1 carta. |
| U_AE_10 | **Centinela de la Tumba** | 3 | 4 | Unidad | `NONE` | `NONE` | `ON_DAMAGE_TAKEN:SELF_BUFF:1;ON_DESTROYED:DRAW:1` | Al recibir daño (solo si sobrevive): gana +1 de Drive. Al ser destruida: roba 1 carta. |
| U_AE_11 | **Chacal de Anubis** | 2 | 2 | Unidad | `NONE` | `JUMPER` | `ON_COMBAT_WIN:SELF_BUFF:1` | Salto: puede pasar de un carril a otro. Al ganar un combate: gana +1 de Drive. |
| U_AE_12 | **Sacerdotisa de Isis** | 4 | 3 | Unidad | `1:ALLY` | `NONE` | `ON_PLACED:ALLY_BOOST:3;ON_COMBAT_WIN:SELF_BUFF:1` | elige 1 unidad aliada. Al entrar en juego: da +3 de Drive a cada objetivo aliado. Al ganar un combate: gana +1 de Drive. |
| U_AE_13 | **Cazador del Desierto** | 4 | 3 | Unidad | `1:ENEMY` | `NONE` | `ON_PLACED:HUNTER_MARK:2:3` | elige 1 unidad enemiga. Al entrar en juego: hace 2 de daño al objetivo; cuando ese objetivo muere (ahora o más tarde) gana +3 de Drive. |
| U_AE_14 | **Sacerdote del Sol** | 4 | 4 | Unidad | `NONE` | `DISRUPTION` | `ON_PLACED:DRAW:1` | Disrupción: puede bajar directa a un carril disputado. Al entrar en juego: roba 1 carta. |
| U_AE_15 | **Momia Guardiana** | 5 | 5 | Unidad | `NONE` | `NONE` | `ON_DAMAGE_TAKEN:SELF_BUFF:2;ON_COMBAT_WIN:READY_SELF` | Al recibir daño (solo si sobrevive): gana +2 de Drive. Al ganar un combate: se recupera (vuelve a poder moverse). |
| U_AE_16 | **Heraldo de Osiris** | 5 | 4 | Unidad | `1:ENEMY;1:ALLY` | `NONE` | `ON_PLACED:ENEMY_DAMAGE:2;ON_PLACED:ALLY_BOOST:2` | elige 1 unidad enemiga y 1 unidad aliada. Al entrar en juego: hace 2 de daño a cada objetivo enemigo. Al entrar en juego: da +2 de Drive a cada objetivo aliado. |
| U_AE_17 | **Esfinge Silenciosa** | 6 | 6 | Unidad | `NONE` | `NONE` | `ON_COMBAT_WIN:DRAW:1;ON_DAMAGE_TAKEN:SELF_BUFF:1` | Al ganar un combate: roba 1 carta. Al recibir daño (solo si sobrevive): gana +1 de Drive. |
| U_AE_18 | **Gran Visir** | 6 | 5 | Unidad | `2:ALLY` | `NONE` | `ON_PLACED:ALLY_BOOST:2;ON_PLACED:DRAW:1` | elige 2 unidades aliadas. Al entrar en juego: da +2 de Drive a cada objetivo aliado. Al entrar en juego: roba 1 carta. |
| U_AE_19 | **Coloso de Arena** | 7 | 7 | Unidad | `2:ENEMY` | `NONE` | `ON_PLACED:ENEMY_DAMAGE:2` | elige 2 unidades enemigas. Al entrar en juego: hace 2 de daño a cada objetivo enemigo. |
| U_AE_20 | **Avatar de Ra** | 8 | 8 | Unidad | `NONE` | `NONE` | `ON_COMBAT_WIN:SELF_BUFF:2;ON_COMBAT_WIN:READY_SELF;ON_DESTROYED:DRAW:2` | Al ganar un combate: gana +2 de Drive. Al ganar un combate: se recupera (vuelve a poder moverse). Al ser destruida: roba 2 cartas. |
| S_AE_04 | **Sudario Protector** | 2 | — | Hechizo Instantánea | `1:ALLY` | `NONE` | `ON_PLACED:ALLY_BOOST:2;ON_PLACED:DRAW:1` | elige 1 unidad aliada. Al resolverse: da +2 de Drive a cada objetivo aliado. Al resolverse: roba 1 carta. |
| S_AE_05 | **Maldición del Faraón** | 3 | — | Hechizo Rápida | `1:ENEMY` | `NONE` | `ON_PLACED:ENEMY_DAMAGE:4` | elige 1 unidad enemiga. Al resolverse: hace 4 de daño a cada objetivo enemigo. |
| S_AE_06 | **Ritual de los Muertos** | 5 | — | Hechizo Lenta | `NONE` | `NONE` | `ON_PLACED:DRAW:3` | Al resolverse: roba 3 cartas. |
| S_AE_07 | **Ofrenda a Osiris** | 3 | — | Hechizo Instantánea | `2:ALLY` | `NONE` | `ON_PLACED:ALLY_BOOST:2` | elige 2 unidades aliadas. Al resolverse: da +2 de Drive a cada objetivo aliado. |
| S_AE_08 | **Plaga de Moscas** | 4 | — | Hechizo Lenta | `2:ENEMY` | `NONE` | `ON_PLACED:ENEMY_DAMAGE:2;ON_PLACED:DRAW:1` | elige 2 unidades enemigas. Al resolverse: hace 2 de daño a cada objetivo enemigo. Al resolverse: roba 1 carta. |
| S_AE_09 | **Juicio de Anubis** | 5 | — | Hechizo Rápida | `1:ENEMY;1:ALLY` | `NONE` | `ON_PLACED:ENEMY_DAMAGE:3;ON_PLACED:ALLY_BOOST:3` | elige 1 unidad enemiga y 1 unidad aliada. Al resolverse: hace 3 de daño a cada objetivo enemigo. Al resolverse: da +3 de Drive a cada objetivo aliado. |

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
| U_RM_07 | **Explorador Romano** | 1 | 1 | Unidad | `NONE` | `JUMPER` | `ON_MOVEMENT_CONFIRMED:DRAW:1` | Salto: puede pasar de un carril a otro. Al confirmar su movimiento: roba 1 carta. |
| U_RM_08 | **Portaestandarte** | 2 | 2 | Unidad | `1:ALLY` | `NONE` | `ON_PLACED:ALLY_BOOST:2` | elige 1 unidad aliada. Al entrar en juego: da +2 de Drive a cada objetivo aliado. |
| U_RM_09 | **Príncipe** | 3 | 3 | Unidad | `NONE` | `NONE` | `ON_COMBAT_WIN:SELF_BUFF:1;ON_COMBAT_WIN:READY_SELF` | Al ganar un combate: gana +1 de Drive. Al ganar un combate: se recupera (vuelve a poder moverse). |
| U_RM_10 | **Arquero Auxiliar** | 3 | 3 | Unidad | `1:ENEMY` | `NONE` | `ON_PLACED:ENEMY_DAMAGE:1` | elige 1 unidad enemiga. Al entrar en juego: hace 1 de daño a cada objetivo enemigo. |
| U_RM_11 | **Triario Veterano** | 4 | 4 | Unidad | `1:ALLY` | `DISRUPTION` | `ON_PLACED:ALLY_BOOST:1` | elige 1 unidad aliada. Disrupción: puede bajar directa a un carril disputado. Al entrar en juego: da +1 de Drive a cada objetivo aliado. |
| U_RM_12 | **Legado de Campaña** | 4 | 3 | Unidad | `1:ENEMY;1:ALLY` | `NONE` | `ON_PLACED:ENEMY_DAMAGE:1;ON_PLACED:ALLY_BOOST:1;ON_PLACED:DRAW:1` | elige 1 unidad enemiga y 1 unidad aliada. Al entrar en juego: hace 1 de daño a cada objetivo enemigo. Al entrar en juego: da +1 de Drive a cada objetivo aliado. Al entrar en juego: roba 1 carta. |
| U_RM_13 | **Pretoriano** | 4 | 5 | Unidad | `NONE` | `NONE` | `ON_DAMAGE_TAKEN:SELF_BUFF:1;ON_DAMAGE_TAKEN:READY_SELF` | Al recibir daño (solo si sobrevive): gana +1 de Drive. Al recibir daño (solo si sobrevive): se recupera (vuelve a poder moverse). |
| U_RM_14 | **Campeón de la Arena** | 5 | 5 | Unidad | `NONE` | `NONE` | `ON_COMBAT_WIN:READY_SELF;ON_COMBAT_WIN:DRAW:1` | Al ganar un combate: se recupera (vuelve a poder moverse). Al ganar un combate: roba 1 carta. |
| U_RM_15 | **Catapulta** | 5 | 3 | Unidad | `2:ENEMY` | `NONE` | `ON_PLACED:ENEMY_DAMAGE:2` | elige 2 unidades enemigas. Al entrar en juego: hace 2 de daño a cada objetivo enemigo. |
| U_RM_16 | **Cazarrecompensas Veterano** | 5 | 4 | Unidad | `1:ENEMY` | `NONE` | `ON_PLACED:HUNTER_MARK:3:4` | elige 1 unidad enemiga. Al entrar en juego: hace 3 de daño al objetivo; cuando ese objetivo muere (ahora o más tarde) gana +4 de Drive. |
| U_RM_17 | **Centuria** | 6 | 5 | Unidad | `2:ALLY` | `NONE` | `ON_PLACED:ALLY_BOOST:2;ON_DESTROYED:DRAW:1` | elige 2 unidades aliadas. Al entrar en juego: da +2 de Drive a cada objetivo aliado. Al ser destruida: roba 1 carta. |
| U_RM_18 | **Águila Imperial** | 6 | 5 | Unidad | `NONE` | `JUMPER` | `ON_MOVEMENT_CONFIRMED:CHARGE_BUFF:2;ON_COMBAT_WIN:DRAW:1` | Salto: puede pasar de un carril a otro. Al confirmar su movimiento: gana +2 de Drive si acaba en un carril. Al ganar un combate: roba 1 carta. |
| U_RM_19 | **Cónsul** | 7 | 6 | Unidad | `1:ENEMY;1:ALLY` | `NONE` | `ON_PLACED:ENEMY_DAMAGE:3;ON_PLACED:ALLY_BOOST:3;ON_PLACED:DRAW:1` | elige 1 unidad enemiga y 1 unidad aliada. Al entrar en juego: hace 3 de daño a cada objetivo enemigo. Al entrar en juego: da +3 de Drive a cada objetivo aliado. Al entrar en juego: roba 1 carta. |
| U_RM_20 | **General Invicto** | 8 | 9 | Unidad | `NONE` | `NONE` | `ON_COMBAT_WIN:SELF_BUFF:2;ON_COMBAT_WIN:READY_SELF` | Al ganar un combate: gana +2 de Drive. Al ganar un combate: se recupera (vuelve a poder moverse). |
| S_RM_04 | **Emboscada** | 3 | — | Hechizo Rápida | `1:ENEMY` | `NONE` | `ON_PLACED:ENEMY_DAMAGE:2;ON_PLACED:DRAW:1` | elige 1 unidad enemiga. Al resolverse: hace 2 de daño a cada objetivo enemigo. Al resolverse: roba 1 carta. |
| S_RM_05 | **Carga Furiosa** | 5 | — | Hechizo Lenta | `2:ALLY` | `NONE` | `ON_PLACED:ALLY_BOOST:3` | elige 2 unidades aliadas. Al resolverse: da +3 de Drive a cada objetivo aliado. |
| S_RM_06 | **Lluvia de Pilum** | 4 | — | Hechizo Lenta | `2:ENEMY` | `NONE` | `ON_PLACED:ENEMY_DAMAGE:3` | elige 2 unidades enemigas. Al resolverse: hace 3 de daño a cada objetivo enemigo. |
| S_RM_07 | **Triunfo** | 3 | — | Hechizo Instantánea | `NONE` | `NONE` | `ON_PLACED:DRAW:2` | Al resolverse: roba 2 cartas. |
| S_RM_08 | **Decimatio** | 4 | — | Hechizo Rápida | `1:ENEMY` | `NONE` | `ON_PLACED:ENEMY_DAMAGE:5` | elige 1 unidad enemiga. Al resolverse: hace 5 de daño a cada objetivo enemigo. |
| S_RM_09 | **Ovación del Coliseo** | 4 | — | Hechizo Rápida | `1:ENEMY;1:ALLY` | `NONE` | `ON_PLACED:ENEMY_DAMAGE:3;ON_PLACED:ALLY_BOOST:3` | elige 1 unidad enemiga y 1 unidad aliada. Al resolverse: hace 3 de daño a cada objetivo enemigo. Al resolverse: da +3 de Drive a cada objetivo aliado. |

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
| U_SP_07 | **Pistón Saltarín** | 1 | 1 | Unidad | `NONE` | `JUMPER` | `ON_MOVEMENT_CONFIRMED:CHARGE_BUFF:1` | Salto: puede pasar de un carril a otro. Al confirmar su movimiento: gana +1 de Drive si acaba en un carril. |
| U_SP_08 | **Relojero Explorador** | 2 | 2 | Unidad | `NONE` | `JUMPER` | `ON_MOVEMENT_CONFIRMED:DRAW:1` | Salto: puede pasar de un carril a otro. Al confirmar su movimiento: roba 1 carta. |
| U_SP_09 | **Mecánico de Campo** | 3 | 2 | Unidad | `1:ALLY` | `NONE` | `ON_PLACED:ALLY_BOOST:1;ON_PLACED:DRAW:1` | elige 1 unidad aliada. Al entrar en juego: da +1 de Drive a cada objetivo aliado. Al entrar en juego: roba 1 carta. |
| U_SP_10 | **Dirigible Ligero** | 3 | 2 | Unidad | `NONE` | `DISRUPTION,JUMPER` | `ON_PLACED:DRAW:1` | Disrupción: puede bajar directa a un carril disputado; Salto: puede pasar de un carril a otro. Al entrar en juego: roba 1 carta. |
| U_SP_11 | **Autómata Reparador** | 3 | 3 | Unidad | `NONE` | `NONE` | `ON_DAMAGE_TAKEN:SELF_BUFF:1;ON_DAMAGE_TAKEN:DRAW:1` | Al recibir daño (solo si sobrevive): gana +1 de Drive. Al recibir daño (solo si sobrevive): roba 1 carta. |
| U_SP_12 | **Cañón de Vapor** | 4 | 3 | Unidad | `1:ENEMY` | `NONE` | `ON_PLACED:ENEMY_DAMAGE:3` | elige 1 unidad enemiga. Al entrar en juego: hace 3 de daño a cada objetivo enemigo. |
| U_SP_13 | **Cazador Mecánico** | 4 | 3 | Unidad | `1:ENEMY` | `JUMPER` | `ON_PLACED:HUNTER_MARK:2:3` | elige 1 unidad enemiga. Salto: puede pasar de un carril a otro. Al entrar en juego: hace 2 de daño al objetivo; cuando ese objetivo muere (ahora o más tarde) gana +3 de Drive. |
| U_SP_14 | **Ingeniero Jefe** | 4 | 3 | Unidad | `1:ALLY` | `NONE` | `ON_PLACED:ALLY_BOOST:2;ON_PLACED:DRAW:1` | elige 1 unidad aliada. Al entrar en juego: da +2 de Drive a cada objetivo aliado. Al entrar en juego: roba 1 carta. |
| U_SP_15 | **Locomotora** | 5 | 5 | Unidad | `NONE` | `JUMPER` | `ON_MOVEMENT_CONFIRMED:CHARGE_BUFF:2` | Salto: puede pasar de un carril a otro. Al confirmar su movimiento: gana +2 de Drive si acaba en un carril. |
| U_SP_16 | **Torreta Tesla** | 5 | 3 | Unidad | `1:ENEMY` | `NONE` | `ON_PLACED:ENEMY_DAMAGE:4;ON_PLACED:DRAW:1` | elige 1 unidad enemiga. Al entrar en juego: hace 4 de daño a cada objetivo enemigo. Al entrar en juego: roba 1 carta. |
| U_SP_17 | **Golem de Hierro** | 6 | 6 | Unidad | `NONE` | `NONE` | `ON_DAMAGE_TAKEN:SELF_BUFF:2;ON_DESTROYED:DRAW:2` | Al recibir daño (solo si sobrevive): gana +2 de Drive. Al ser destruida: roba 2 cartas. |
| U_SP_18 | **Tanque Oruga** | 6 | 7 | Unidad | `NONE` | `NONE` | `ON_COMBAT_WIN:SELF_BUFF:2` | Al ganar un combate: gana +2 de Drive. |
| U_SP_19 | **Zepelín Bombardero** | 7 | 5 | Unidad | `2:ENEMY` | `DISRUPTION,JUMPER` | `ON_PLACED:ENEMY_DAMAGE:2;ON_MOVEMENT_CONFIRMED:CHARGE_BUFF:1` | elige 2 unidades enemigas. Disrupción: puede bajar directa a un carril disputado; Salto: puede pasar de un carril a otro. Al entrar en juego: hace 2 de daño a cada objetivo enemigo. Al confirmar su movimiento: gana +1 de Drive si acaba en un carril. |
| U_SP_20 | **Coloso de Vapor** | 8 | 9 | Unidad | `NONE` | `NONE` | `ON_PLACED:DRAW:1;ON_COMBAT_WIN:READY_SELF` | Al entrar en juego: roba 1 carta. Al ganar un combate: se recupera (vuelve a poder moverse). |
| S_SP_04 | **Sobrecarga** | 2 | — | Hechizo Instantánea | `1:ALLY` | `NONE` | `ON_PLACED:ALLY_BOOST:3` | elige 1 unidad aliada. Al resolverse: da +3 de Drive a cada objetivo aliado. |
| S_SP_05 | **Descarga en Cadena** | 4 | — | Hechizo Lenta | `2:ENEMY` | `NONE` | `ON_PLACED:ENEMY_DAMAGE:3` | elige 2 unidades enemigas. Al resolverse: hace 3 de daño a cada objetivo enemigo. |
| S_SP_06 | **Biblioteca de Patentes** | 6 | — | Hechizo Lenta | `NONE` | `NONE` | `ON_PLACED:DRAW:4` | Al resolverse: roba 4 cartas. |
| S_SP_07 | **Cortocircuito Doble** | 3 | — | Hechizo Rápida | `2:ENEMY` | `NONE` | `ON_PLACED:ENEMY_DAMAGE:2` | elige 2 unidades enemigas. Al resolverse: hace 2 de daño a cada objetivo enemigo. |
| S_SP_08 | **Reparación Rápida** | 1 | — | Hechizo Instantánea | `1:ALLY` | `NONE` | `ON_PLACED:ALLY_BOOST:2` | elige 1 unidad aliada. Al resolverse: da +2 de Drive a cada objetivo aliado. |
| S_SP_09 | **Detonación Controlada** | 5 | — | Hechizo Rápida | `1:ENEMY;1:ALLY` | `NONE` | `ON_PLACED:ENEMY_DAMAGE:4;ON_PLACED:ALLY_BOOST:2` | elige 1 unidad enemiga y 1 unidad aliada. Al resolverse: hace 4 de daño a cada objetivo enemigo. Al resolverse: da +2 de Drive a cada objetivo aliado. |

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
| U_GP_07 | **Bit Corrupto** | 1 | 1 | Unidad | `NONE` | `DISRUPTION` | `ON_DESTROYED:DRAW:1` | Disrupción: puede bajar directa a un carril disputado. Al ser destruida: roba 1 carta. |
| U_GP_08 | **Eco Defectuoso** | 2 | 2 | Unidad | `NONE` | `NONE` | `ON_DAMAGE_TAKEN:SELF_BUFF:2;ON_DESTROYED:DRAW:1` | Al recibir daño (solo si sobrevive): gana +2 de Drive. Al ser destruida: roba 1 carta. |
| U_GP_09 | **Streamer** | 2 | 2 | Unidad | `NONE` | `NONE` | `ON_PLACED:DRAW:1` | Al entrar en juego: roba 1 carta. |
| U_GP_10 | **Hacker Novato** | 3 | 3 | Unidad | `1:ENEMY` | `DISRUPTION` | `ON_PLACED:ENEMY_DAMAGE:1` | elige 1 unidad enemiga. Disrupción: puede bajar directa a un carril disputado. Al entrar en juego: hace 1 de daño a cada objetivo enemigo. |
| U_GP_11 | **Saboteador Digital** | 3 | 2 | Unidad | `1:ENEMY` | `NONE` | `ON_PLACED:HUNTER_MARK:1:3;ON_PLACED:DRAW:1` | elige 1 unidad enemiga. Al entrar en juego: hace 1 de daño al objetivo; cuando ese objetivo muere (ahora o más tarde) gana +3 de Drive. Al entrar en juego: roba 1 carta. |
| U_GP_12 | **Dron Fantasma** | 3 | 2 | Unidad | `NONE` | `DISRUPTION,JUMPER` | `ON_MOVEMENT_CONFIRMED:CHARGE_BUFF:1` | Disrupción: puede bajar directa a un carril disputado; Salto: puede pasar de un carril a otro. Al confirmar su movimiento: gana +1 de Drive si acaba en un carril. |
| U_GP_13 | **Virus Replicante** | 4 | 4 | Unidad | `NONE` | `NONE` | `ON_COMBAT_WIN:SELF_BUFF:1;ON_COMBAT_WIN:DRAW:1` | Al ganar un combate: gana +1 de Drive. Al ganar un combate: roba 1 carta. |
| U_GP_14 | **Cantante Holográfica** | 4 | 3 | Unidad | `1:ALLY;1:ENEMY` | `NONE` | `ON_PLACED:ALLY_BOOST:2;ON_PLACED:ENEMY_DAMAGE:2` | elige 1 unidad aliada y 1 unidad enemiga. Al entrar en juego: da +2 de Drive a cada objetivo aliado. Al entrar en juego: hace 2 de daño a cada objetivo enemigo. |
| U_GP_15 | **Bot de Spam** | 5 | 4 | Unidad | `2:ENEMY` | `NONE` | `ON_PLACED:ENEMY_DAMAGE:1;ON_PLACED:DRAW:1` | elige 2 unidades enemigas. Al entrar en juego: hace 1 de daño a cada objetivo enemigo. Al entrar en juego: roba 1 carta. |
| U_GP_16 | **Jugador Pro** | 5 | 5 | Unidad | `NONE` | `NONE` | `ON_COMBAT_WIN:READY_SELF;ON_DAMAGE_TAKEN:SELF_BUFF:1` | Al ganar un combate: se recupera (vuelve a poder moverse). Al recibir daño (solo si sobrevive): gana +1 de Drive. |
| U_GP_17 | **Troll Digital** | 6 | 5 | Unidad | `1:ENEMY` | `DISRUPTION` | `ON_PLACED:ENEMY_DAMAGE:3` | elige 1 unidad enemiga. Disrupción: puede bajar directa a un carril disputado. Al entrar en juego: hace 3 de daño a cada objetivo enemigo. |
| U_GP_18 | **Glitch Mayor** | 6 | 6 | Unidad | `NONE` | `NONE` | `ON_DAMAGE_TAKEN:SELF_BUFF:2;ON_DESTROYED:DRAW:2` | Al recibir daño (solo si sobrevive): gana +2 de Drive. Al ser destruida: roba 2 cartas. |
| U_GP_19 | **Administrador del Servidor** | 7 | 6 | Unidad | `2:ALLY` | `DISRUPTION` | `ON_PLACED:ALLY_BOOST:2;ON_PLACED:DRAW:1` | elige 2 unidades aliadas. Disrupción: puede bajar directa a un carril disputado. Al entrar en juego: da +2 de Drive a cada objetivo aliado. Al entrar en juego: roba 1 carta. |
| U_GP_20 | **Singularidad** | 8 | 8 | Unidad | `2:ENEMY` | `NONE` | `ON_PLACED:ENEMY_DAMAGE:3;ON_COMBAT_WIN:DRAW:2` | elige 2 unidades enemigas. Al entrar en juego: hace 3 de daño a cada objetivo enemigo. Al ganar un combate: roba 2 cartas. |
| S_GP_04 | **Pop-up** | 2 | — | Hechizo Rápida | `1:ENEMY` | `NONE` | `ON_PLACED:ENEMY_DAMAGE:1;ON_PLACED:DRAW:1` | elige 1 unidad enemiga. Al resolverse: hace 1 de daño a cada objetivo enemigo. Al resolverse: roba 1 carta. |
| S_GP_05 | **Lag Spike** | 3 | — | Hechizo Rápida | `1:ENEMY` | `NONE` | `ON_PLACED:ENEMY_DAMAGE:4` | elige 1 unidad enemiga. Al resolverse: hace 4 de daño a cada objetivo enemigo. |
| S_GP_06 | **Sobrescritura** | 3 | — | Hechizo Instantánea | `1:ALLY` | `NONE` | `ON_PLACED:ALLY_BOOST:4` | elige 1 unidad aliada. Al resolverse: da +4 de Drive a cada objetivo aliado. |
| S_GP_07 | **Ráfaga de Datos** | 4 | — | Hechizo Instantánea | `NONE` | `NONE` | `ON_PLACED:DRAW:2` | Al resolverse: roba 2 cartas. |
| S_GP_08 | **DDoS** | 6 | — | Hechizo Lenta | `2:ENEMY` | `NONE` | `ON_PLACED:ENEMY_DAMAGE:4;ON_PLACED:DRAW:1` | elige 2 unidades enemigas. Al resolverse: hace 4 de daño a cada objetivo enemigo. Al resolverse: roba 1 carta. |
| S_GP_09 | **Parche de Emergencia** | 2 | — | Hechizo Instantánea | `1:ALLY;1:ENEMY` | `NONE` | `ON_PLACED:ALLY_BOOST:2;ON_PLACED:ENEMY_DAMAGE:1` | elige 1 unidad aliada y 1 unidad enemiga. Al resolverse: da +2 de Drive a cada objetivo aliado. Al resolverse: hace 1 de daño a cada objetivo enemigo. |
