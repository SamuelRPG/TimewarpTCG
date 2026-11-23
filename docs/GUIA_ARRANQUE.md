# Guía de arranque (Windows): backend + Unity + primera partida

## 0. Qué rama usar y cómo está organizado el repo

**Usa solo `feature/unity-sobre-develop`.** Es `develop` + los cambios nuevos. `develop` y `main` no se han tocado.
(La rama `feature/unity-client-y-fix-bot` fue mi primer intento, sacada de `main` por error: ignórala.)

```
timewarptcg/
├─ TimewarpTCG.Back/            ← tu backend Java (ya existía). Aquí se arranca el servidor.
├─ TimewarpTCG.Unity/           ← el proyecto Unity (nuevo). Dentro ya hay Assets/Timewarp = los scripts del juego.
├─ tools/                       ← pruebas opcionales sin Unity (puedes ignorarla)
└─ docs/                        ← guías (esta, las reglas de tu juego y la hoja de ruta)
```

* **Back** = Java/Spring. No contiene nada de Unity.
* **Unity** = C#. No contiene nada de Java. Se comunican por red (WebSocket al puerto 8080).

Para tener la rama en tu PC, desde la carpeta de tu repo, en PowerShell:

```powershell
git fetch origin
git checkout feature/unity-sobre-develop
```

## 1. ¿Hace falta Supabase? No

Supabase es una base de datos **en la nube**, no se "arranca" en tu PC. Para jugar contra el bot no hace falta: los mazos AGGRO y CONTROL salen del código.

**Por defecto el servidor arranca con una base de datos en memoria (H2)**: sin internet, sin Supabase y sin configurar nada. Las 116 cartas se cargan solas. Lo que se pierde al cerrar el servidor son las cuentas y los mazos que crees. Para guardarlos en Supabase, mira el punto 6.

> Esto cambió respecto a versiones anteriores de esta guía: ya **no** hace falta el perfil `demo` ni tocar `application.properties`. Ese fichero ya no contiene ninguna contraseña.

## 2. Arrancar el backend

> El error `mvn no se reconoce` significa que **Maven no está instalado** en tu Windows (o no está en el PATH). Tienes dos caminos; elige **uno**.

### Camino A (recomendado): con IntelliJ, que ya usas

1. Abre en IntelliJ la carpeta **`TimewarpTCG.Back`** (*File ▸ Open*). Si pregunta por el proyecto Maven, acepta ("Trust" / "Load Maven project").
2. **Java:** vale **Java 21 o superior (incluido el 25 que ya tienes)**. *File ▸ Project Structure ▸ Project ▸ SDK* → elige tu JDK 25 (o *Add SDK ▸ Download JDK…* si no aparece). Deja *Language level* en 21 (así lo pide el `pom.xml`).
3. Abre `src/main/java/com/saroga/timewarp/TimewarpApplication.java` y pulsa el triángulo verde junto a `main` ▸ *Run*. Listo cuando la consola ponga `Started TimewarpApplication in … seconds`.
4. Si IntelliJ se queja de que Lombok no genera nada: *Settings ▸ Build, Execution, Deployment ▸ Compiler ▸ Annotation Processors ▸ Enable annotation processing*.

### Camino B: con la terminal (sin instalar Maven)

El repo incluye el *Maven Wrapper* (`mvnw.cmd`): se descarga Maven él solo la primera vez. Solo necesitas **Java 21 o superior** (vale el 25). Comprueba con `java -version`.

En PowerShell, entra en la carpeta del backend y ejecuta:

```powershell
cd C:\ruta\a\tu\repo\TimewarpTCG.Back
.\mvnw.cmd "-Dmaven.test.skip=true" spring-boot:run
```

**En PowerShell los `-D…` deben ir entre comillas** (si no, parte `-Dmaven.test.skip=true` por el punto y da `Unknown lifecycle phase ".test.skip=true"`). Los avisos `WARNING: … Unsafe` / `native access` del principio son normales.

La primera vez tarda unos minutos (descarga Maven y las librerías). Si dice que no encuentra Java, define `JAVA_HOME` con la carpeta de tu JDK (la que contiene `bin\java.exe`).

**Atajo:** haz doble clic en `arrancar-backend.cmd` (en la raíz del repo): ejecuta ese mismo comando por ti. Para parar el servidor pulsa **Ctrl+C** en la ventana (y confirma con `S`). Hay que arrancarlo cada vez que quieras jugar; no se queda en marcha solo.

> `winget install Apache.Maven` **no funciona**: Maven no está en el catálogo de winget. No lo necesitas gracias al wrapper.

### Comprobar que funciona

Con el servidor en marcha, en otra PowerShell:

```powershell
Invoke-RestMethod -Method Post "http://localhost:8080/api/users/guest"
```

Debe devolver un `id`, un `username` ("Invitado-xxxx") y un `token`.

> **Clave de sesión.** Si no defines `TIMEWARP_JWT_SECRET`, el servidor inventa una clave distinta cada vez que arranca: funciona, pero al reiniciarlo las sesiones abiertas dejan de valer y tendrás que volver a pulsar **ENTRAR** en Unity. Para que no pase, define esa variable de entorno con una frase larga y aleatoria (mínimo 32 caracteres). Para jugar tú en local no hace falta.

## 3. Montar el proyecto Unity (una sola vez)

La carpeta `TimewarpTCG.Unity` del repo solo trae `Assets/Timewarp` (los scripts). Un proyecto Unity necesita además `ProjectSettings/` y `Packages/`, que **genera Unity**. Hay que hacerlo así:

1. **Unity Hub:** instala **Unity 2021.3 LTS o superior** (vale 2022 LTS o Unity 6).
2. *New project* ▸ plantilla **2D (Built-in)** ▸ ponle de nombre `temporal` y guárdalo **fuera del repo** (p. ej. `C:\UnityProjects\temporal`) ▸ Create. Espera a que abra y ciérralo.
3. Con el Explorador, copia estas **3 carpetas** de `C:\UnityProjects\temporal` a `TimewarpTCG.Unity` dentro de tu repo:
   `Assets`, `Packages`, `ProjectSettings`
   (si Windows pregunta "¿combinar carpetas?" di que **sí**: no hay conflictos). **No copies** `Library`, `Temp`, `Logs` ni `UserSettings`.
4. En Unity Hub: *Add ▸ Add project from disk* ▸ elige la carpeta `TimewarpTCG.Unity` del repo ▸ ábrelo. La primera vez tarda unos minutos en importar.
5. Cuando la consola (*Window ▸ General ▸ Console*) no muestre errores rojos, pasa al punto 4.

Ya puedes borrar `C:\UnityProjects\temporal`. A partir de ahora **este es tu proyecto Unity** y vive dentro del repo. `.gitignore` ya excluye lo que Unity genera (`Library`, `Temp`…); sí debes versionar `Assets`, `Packages`, `ProjectSettings` y los ficheros `.meta`.

## 4. Jugar

1. Con el backend en marcha (punto 2), en Unity pulsa **▶ Play**. En la pestaña *Game* activa *Maximize On Play*.
2. Deja `localhost:8080`, el nombre vacío (= invitado) y elige **AGGRO** o **CONTROL** ▸ **JUGAR CONTRA EL BOT**.

No hay escenas ni prefabs que montar: la interfaz se construye sola al pulsar Play.

| Quiero… | Hago… |
|---|---|
| Bajar una unidad | **Arrástrala** de la mano a **TU BASE**, o clic en la carta y clic en la base |
| Lanzar un hechizo sin objetivo | Arrástralo hacia el tablero (fuera de la mano), o clic + **LANZAR** |
| Lanzar un hechizo con objetivo | **Arrástralo sobre la unidad objetivo** (aparece una flecha dorada; se pone verde sobre un objetivo válido). Si pide varios, elige el resto con clic y pulsa **LANZAR** |
| Mover una unidad | Arrastra una unidad tuya **lista** a otra zona (se resalta en verde), o clic en ella y clic en la zona. Se ejecuta al pulsar **CONFIRMAR MOVIMIENTOS** |
| Pasar / terminar turno | Botón grande azul |
| Ver una carta en grande | Pasa el ratón por encima (panel izquierdo) |
| Qué hizo el bot | Registro (panel izquierdo inferior) y efectos en el tablero: números flotantes (daño/bonos), partículas al destruirse, banner de turno |

El cliente solo da pistas; no bloquea jugadas ilegales: verás el error de tu backend en rojo.

**Cómo leer el tablero de un vistazo** (también en el botón *¿Cómo se juega?*):

| Señal | Significa |
|---|---|
| Carta de la mano con **borde verde y etiqueta JUGAR** | Se puede jugar ahora mismo |
| Carta **gris** con un motivo escrito encima (`SIN RECURSO`, `ESPERA`, `AHORA NO`, `BASE LLENA`…) | No se puede jugar, y por qué. Si no te llega el recurso, el coste sale en **rojo** |
| Unidad tuya con **borde verde y etiqueta MOVER** | Está lista: puedes moverla |
| Unidad con **AGOTADA** | Ya se movió; no puede hasta tu próximo turno (o hasta que se recupere por un efecto) |
| Barra superior **TU PRIORIDAD · ACTÚA** (verde) o **JUEGA EL BOT…** (naranja) | Quién puede actuar. Cada tarjeta de jugador también muestra `● PRIORIDAD` / `○ ESPERA` |
| Tarjeta del jugador: **RECURSO 3 / 5** con gemas | Recurso disponible y máximo de este turno (azul brillante = te queda, translúcido = ya gastado). El máximo es `min(turno, 10)` y se renueva entero cada turno |
| **PUNTOS 4 / 10** con marcas doradas | Tu marcador (hay que llegar a 10 con 2 de ventaja) |

**El bot juega con pausas:** hace una acción cada ~0,7 segundos (una partida completa dura así 40-60 s), y tras cada una ves el tablero actualizado, para que puedas seguir qué hace. Se regula en `application.properties` (`timewarp.bot.delay-ms`, en milisegundos; `0` = de golpe).

### Cuenta, mazos y mazo del bot (menú)

* **Invitado:** sin nombre puedes jugar con los mazos de prueba AGGRO y CONTROL.
* **Cuenta:** escribe un nombre y una contraseña (mínimo 8 caracteres). La primera vez pulsa **CREAR CUENTA**; las siguientes, **ENTRAR**. Así puedes guardar tus mazos y verlos en la lista **TU MAZO**. La contraseña se guarda en el servidor cifrada (BCrypt), nunca se almacena en Unity, y tras 5 intentos fallidos seguidos se bloquea unos minutos.
* Si usas la base en memoria, las cuentas desaparecen al apagar el servidor.
* **EDITAR MIS MAZOS** abre el editor: catálogo completo (viene de la base de datos) con filtros por facción, tipo y búsqueda. **Clic izquierdo = añadir una copia, clic derecho = quitar una.** A la derecha ves el contador (`27 / 40`), las facciones, la curva de coste y la lista del mazo. Reglas, igual que en el servidor: **exactamente 40 cartas, máximo 3 copias de cada una, máximo 2 facciones**. El botón GUARDAR solo se activa cuando el mazo es válido; si guardas con el nombre de uno existente, lo sustituye (te pide confirmación).
* **MAZO DEL BOT:** *Aleatorio* (por defecto) genera en cada partida un mazo legal distinto con cartas del catálogo; también puedes forzar AGGRO o CONTROL. El servidor escribe en su consola qué mazo ha elegido el bot (`[BOT] Mazo aleatorio: ...`).

### Partida online contra otra persona

En el menú, **BUSCAR PARTIDA ONLINE** te mete en una cola; en cuanto otra persona hace lo mismo, empezáis (cada uno con el mazo que tenga elegido: AGGRO, CONTROL o uno de los tuyos). Las reglas, las cartas y el servidor son los mismos que contra el bot.

* **Hace falta un servidor al que lleguen los dos.** Tú pones su dirección en el campo *Servidor* del menú, igual que ahora con `localhost:8080`. Tres formas, de más fácil a más seria:
  1. **En tu casa, con un amigo:** arranca el backend en tu PC y usa una VPN privada gratuita (Tailscale, ZeroTier o Hamachi): tu amigo escribe la IP que te da la VPN, por ejemplo `100.101.102.103:8080`. No hay que abrir puertos del router.
  2. **Misma red local (mismo wifi):** tu IP local (`ipconfig` en Windows), por ejemplo `192.168.1.20:8080`. Quizá tengas que permitir Java en el cortafuegos de Windows.
  3. **Publicado en internet:** el backend en un servidor con **HTTPS** (ver `docs/SEGURIDAD.md`, "Antes de enseñárselo a otras personas"). Entonces escribes `https://tu-dominio` y el cliente usa `wss://`.
* **Bases de datos:** si los dos jugáis contra el mismo servidor, las cuentas y mazos son los de ese servidor (con Supabase, los guardados ahí).
* **Invitados:** pueden jugar online con AGGRO o CONTROL. El rival ve tu nombre de cuenta (o `Invitado-xxxx`).
* **Qué pasa si alguien se va:** si cierra el juego o se desconecta, **pierde** y el otro gana por abandono. Si el que tiene que actuar no hace nada en **3 minutos**, también pierde. No hay reconexión a una partida en curso.
* **Qué NO hay todavía:** salas con código para jugar con un amigo concreto, clasificación, ni chat. Es "partida rápida" con quien esté buscando.

Para probarlo tú solo: arranca el backend, abre **dos copias** del juego (el Editor de Unity y una compilación, o dos Editores) y pulsa BUSCAR PARTIDA ONLINE en ambas. Sin Unity: `dotnet run -c Release -- localhost:8080 PVP 1` en `tools/ClientSmokeTest` hace jugar a dos clientes automáticos.

## 5. Si algo falla

| Síntoma | Solución |
|---|---|
| Unity: "No se pudo conectar" | El backend no está arrancado o no usa el puerto 8080. |
| El backend no arranca y menciona `HikariPool`, `Connection` o `postgresql` | Tienes definidas variables `SPRING_DATASOURCE_*` o el perfil `supabase` y Supabase no responde (¿proyecto pausado?). Quítalas para volver a la base en memoria. |
| Unity: "Esta cuenta se creó antes de que existieran las contraseñas…" | Es una cuenta de la versión sin contraseñas. Crea una cuenta nueva (o mira la nota del punto 6). |
| Unity: "Demasiados intentos" / 429 | Frenos anti-abuso: espera unos minutos. |
| No puedo escribir en los campos de texto (nombre, contraseña, servidor) | Casi seguro es el modo de entrada de Unity 6. Ya se corrige solo, pero si sigue pasando: *Edit ▸ Project Settings ▸ Player ▸ Other Settings ▸ Active Input Handling = **Both***, y reinicia Unity. Pulsa el botón **VER** junto a la contraseña para comprobar que lo que escribes entra. Mira también la consola de Unity (`[Timewarp]…`) |
| "Error de base de datos" al CREAR CUENTA | Falta la columna `password_hash` en la tabla `users` de Supabase. La consola del backend lo explica (`[CUENTAS] ATENCION…`). Pega `docs/supabase/anadir_password_hash.sql` en el SQL Editor de Supabase. **No hay ninguna tabla nueva**: solo esa columna en `users` |
| Unity: dejó de responder tras reiniciar el servidor | La sesión se firmó con la clave anterior. Vuelve al menú y pulsa **ENTRAR** (o define `TIMEWARP_JWT_SECRET`). |
| `mvn no se reconoce` | No uses `mvn`: usa `.\\mvnw.cmd` (Camino B) o IntelliJ (Camino A). |
| Unity: los clics no hacen nada (Unity 6) | *Edit ▸ Project Settings ▸ Player ▸ Other Settings ▸ Active Input Handling = **Both***, y reinicia Unity. |
| Error de compilación con `ClientWebSocket` | Unity demasiado antiguo: usa 2021.3 LTS+ y *Api Compatibility Level = .NET Standard 2.1*. |
| No aparece nada al pulsar Play | Busca en la consola líneas `[Timewarp]` y pásame el mensaje completo. |

> No he podido ejecutar la interfaz dentro de Unity (no tengo Unity en mi entorno). La red y las reglas están probadas con más de 60 partidas contra tu backend y todo el código compila contra la API real de Unity, pero el aspecto visual puede necesitar ajustes.

## 6. Usar tu Supabase de verdad (cuentas, mazos y cartas guardados en la nube)

Con la base en memoria todo se pierde al cerrar el servidor. Para que se guarde en Supabase:

1. En el panel de Supabase comprueba que el proyecto **no está pausado** (el plan gratuito pausa los inactivos; pulsa *Restore project*). Si aún no lo hiciste, **cambia la contraseña de la base de datos** (*Project Settings ▸ Database ▸ Reset database password*): la anterior estuvo en el repo.
2. **Tus datos de conexión van en un fichero que NO se sube a git:** copia `supabase.env.cmd.example` como `supabase.env.cmd` (en la raíz del repo; está en `.gitignore`) y rellena la URL, el usuario y la contraseña (*Project Settings ▸ Database ▸ Connection string*, modo *Transaction pooler*), y una frase larga para `TIMEWARP_JWT_SECRET`.
3. Arranca con doble clic en **`arrancar-backend-supabase.cmd`** (carga ese fichero y activa el perfil `supabase`). La primera vez Hibernate crea las tablas solo y añade a `users` la columna `password_hash`.
4. **Carga las cartas:** en Supabase ▸ *SQL Editor*, pega el contenido de **`docs/supabase/cartas.sql`** y pulsa *Run*. Es repetible: inserta las cartas nuevas y actualiza las existentes sin borrar nada (no rompe mazos ya guardados). Con Supabase `data.sql` no se ejecuta, por eso este paso es necesario. Sin él, el editor de mazos mostrará el catálogo vacío.
5. **Seguridad (hazlo una vez):** ejecuta `docs/supabase/seguridad_rls.sql` y después `docs/supabase/rol_app.sql` (cambia antes la contraseña que lleva dentro); las instrucciones para usar ese rol están al final del propio fichero. Más detalle en `docs/SEGURIDAD.md`.
6. Abre Unity, pulsa **CREAR CUENTA** con un nombre y contraseña, y **EDITAR MIS MAZOS**: deben aparecer las 116 cartas (si ves solo 36, vuelve a pegar `docs/supabase/cartas.sql`: las 80 nuevas hay que cargarlas en Supabase una vez).

> **Cuentas antiguas.** Las filas que ya tengas en `users` se crearon cuando solo hacía falta el nombre y no tienen contraseña, así que **nadie puede entrar con ellas** (es lo seguro por defecto: si no, cualquiera podría apropiarse de la cuenta). Crea una cuenta nueva. Si necesitas conservar los mazos de una cuenta antigua, arranca **una sola vez** con `set TIMEWARP_SECURITY_ALLOW_CLAIM_LEGACY_ACCOUNTS=true` en `supabase.env.cmd`, entra tú con ese nombre y una contraseña (la primera que se use queda como definitiva), y quita la línea.

> **¿Qué significa `ON_PLACED:HUNTER_MARK:1:5`, `1:ENEMY`, `JUMPER`…?** Está todo explicado, con ejemplos de tus cartas, en [`docs/03_EFECTOS_KEYWORDS_Y_BASE_DE_DATOS.md`](03_EFECTOS_KEYWORDS_Y_BASE_DE_DATOS.md), y las 36 cartas una a una en [`docs/catalogo/CARTAS_EXPLICADAS.md`](catalogo/CARTAS_EXPLICADAS.md). Al arrancar, el backend revisa el catálogo y avisa de typos (`[CATALOGO] …` en la consola).

### Cómo añadir cartas nuevas
1. Edita `docs/catalogo/cartas.csv` (Excel vale: separador `;`, UTF-8). Las columnas y valores permitidos están explicados en la cabecera de `tools/catalog/generar_sql.py`.
2. Ejecuta `python tools/catalog/generar_sql.py` (valida el CSV y regenera `data.sql` y `docs/supabase/cartas.sql`).
3. Pega el nuevo `cartas.sql` en Supabase. Reinicia el backend si usas la base en memoria.

Hoy hay **116 cartas**: 4 facciones × 29 (20 unidades y 9 hechizos cada una; en total 80 unidades y 36 hechizos). Usan todos los disparadores (`ON_PLACED`, `ON_DESTROYED`, `ON_DAMAGE_TAKEN`, `ON_COMBAT_WIN`, `ON_MOVEMENT_CONFIRMED`), las acciones de daño, bonificación, robar, recuperarse, carga y marca de cazador, las keywords `JUMPER` y `DISRUPTION` y muchas con **varios efectos a la vez**. Recuerda que solo `JUMPER` y `DISRUPTION` tienen lógica como keywords; el resto de efectos se declaran en la columna `effects`.

## 🔐 Seguridad

El backend ya incluye cuentas con contraseña cifrada, sesiones firmadas (JWT), comprobación de que cada jugador solo toca sus partidas y sus mazos, límites anti-abuso y errores genéricos. Lo que **solo puedes hacer tú** (cambiar la contraseña de Supabase, que estuvo en el historial de git desde el commit `456d63c`, activar protecciones en GitHub, HTTPS al publicarlo…) está en **`docs/SEGURIDAD.md`**, con un checklist.
