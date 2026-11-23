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

Supabase es una base de datos **en la nube**, no se "arranca" en tu PC. Para jugar contra el bot no hace falta: los mazos AGGRO y CONTROL salen del código. Pero tu backend intenta conectarse a Supabase al arrancar y si falla no arranca, así que **arráncalo siempre con el perfil `demo`** (base de datos en memoria, sin internet, sin tocar Supabase).

## 2. Arrancar el backend

> El error `mvn no se reconoce` significa que **Maven no está instalado** en tu Windows (o no está en el PATH). Tienes dos caminos; elige **uno**.

### Camino A (recomendado): con IntelliJ, que ya usas

1. Abre en IntelliJ la carpeta **`TimewarpTCG.Back`** (*File ▸ Open*). Si pregunta por el proyecto Maven, acepta ("Trust" / "Load Maven project").
2. **Java:** vale **Java 21 o superior (incluido el 25 que ya tienes)**. *File ▸ Project Structure ▸ Project ▸ SDK* → elige tu JDK 25 (o *Add SDK ▸ Download JDK…* si no aparece). Deja *Language level* en 21 (así lo pide el `pom.xml`).
3. Abre `src/main/java/com/saroga/timewarp/TimewarpApplication.java` y pulsa el triángulo verde junto a `main` ▸ *Run*. Fallará por la base de datos: es normal, solo sirve para crear la configuración.
4. *Run ▸ Edit Configurations…* ▸ selecciona **TimewarpApplication** ▸ *Modify options* ▸ marca **Add VM options** ▸ en el campo que aparece escribe exactamente:

   ```
   -Dspring.profiles.active=demo
   ```
5. OK ▸ Run. Listo cuando la consola ponga `Started TimewarpApplication in … seconds`.

### Camino B: con la terminal (sin instalar Maven)

El repo incluye el *Maven Wrapper* (`mvnw.cmd`): se descarga Maven él solo la primera vez. Solo necesitas **Java 21 o superior** (vale el 25). Comprueba con `java -version`.

En PowerShell, entra en la carpeta del backend y ejecuta:

```powershell
cd C:\ruta\a\tu\repo\TimewarpTCG.Back
.\mvnw.cmd "-Dmaven.test.skip=true" spring-boot:run "-Dspring-boot.run.profiles=demo"
```

**En PowerShell los `-D…` deben ir entre comillas** (si no, parte `-Dmaven.test.skip=true` por el punto y da `Unknown lifecycle phase ".test.skip=true"`). Los avisos `WARNING: … Unsafe` / `native access` del principio son normales.

La primera vez tarda unos minutos (descarga Maven y las librerías). Si dice que no encuentra Java, define `JAVA_HOME` con la carpeta de tu JDK (la que contiene `bin\java.exe`).

**Atajo:** haz doble clic en `arrancar-backend.cmd` (en la raíz del repo): ejecuta ese mismo comando por ti. Para parar el servidor pulsa **Ctrl+C** en la ventana (y confirma con `S`). Hay que arrancarlo cada vez que quieras jugar; no se queda en marcha solo.

> `winget install Apache.Maven` **no funciona**: Maven no está en el catálogo de winget. No lo necesitas gracias al wrapper.

### Comprobar que funciona

Con el servidor en marcha, en otra PowerShell:

```powershell
Invoke-RestMethod -Method Post "http://localhost:8080/api/users/register?username=prueba"
```

Debe devolver un `id` y `username`.

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

### Cuenta, mazos y mazo del bot (menú)

* **Invitado:** sin nombre puedes jugar con los mazos de prueba AGGRO y CONTROL.
* **Cuenta:** escribe un nombre y pulsa **ENTRAR** (si no existe, se crea). Así puedes guardar tus mazos y verlos en la lista **TU MAZO**.
* **EDITAR MIS MAZOS** abre el editor: catálogo completo (viene de la base de datos) con filtros por facción, tipo y búsqueda. **Clic izquierdo = añadir una copia, clic derecho = quitar una.** A la derecha ves el contador (`27 / 40`), las facciones, la curva de coste y la lista del mazo. Reglas, igual que en el servidor: **exactamente 40 cartas, máximo 3 copias de cada una, máximo 2 facciones**. El botón GUARDAR solo se activa cuando el mazo es válido; si guardas con el nombre de uno existente, lo sustituye (te pide confirmación).
* **MAZO DEL BOT:** *Aleatorio* (por defecto) genera en cada partida un mazo legal distinto con cartas del catálogo; también puedes forzar AGGRO o CONTROL. El servidor escribe en su consola qué mazo ha elegido el bot (`[BOT] Mazo aleatorio: ...`).

## 5. Si algo falla

| Síntoma | Solución |
|---|---|
| Unity: "No se pudo conectar" | El backend no está arrancado o no usa el puerto 8080. |
| El backend no arranca y menciona `HikariPool`, `Connection` o `postgresql` | Te falta el perfil `demo` (VM option `-Dspring.profiles.active=demo`). |
| `mvn no se reconoce` | No uses `mvn`: usa `.\\mvnw.cmd` (Camino B) o IntelliJ (Camino A). |
| Unity: los clics no hacen nada (Unity 6) | *Edit ▸ Project Settings ▸ Player ▸ Other Settings ▸ Active Input Handling = **Both***, y reinicia Unity. |
| Error de compilación con `ClientWebSocket` | Unity demasiado antiguo: usa 2021.3 LTS+ y *Api Compatibility Level = .NET Standard 2.1*. |
| No aparece nada al pulsar Play | Busca en la consola líneas `[Timewarp]` y pásame el mensaje completo. |

> No he podido ejecutar la interfaz dentro de Unity (no tengo Unity en mi entorno). La red y las reglas están probadas con más de 60 partidas contra tu backend y todo el código compila contra la API real de Unity, pero el aspecto visual puede necesitar ajustes.

## 6. Usar tu Supabase de verdad (mazos y cartas guardados en la nube)

Con el perfil `demo` todo se pierde al cerrar el servidor. Para que cartas y mazos se guarden en Supabase:

1. En el panel de Supabase comprueba que el proyecto **no está pausado** (el plan gratuito pausa los inactivos; pulsa *Restore project*).
2. Arranca el backend **sin** el perfil `demo` (tu `application.properties` ya apunta a Supabase). La primera vez, Hibernate crea las tablas solo (`ddl-auto=update`).
3. **Carga las cartas:** en Supabase ▸ *SQL Editor*, pega el contenido de **`docs/supabase/cartas.sql`** y pulsa *Run*. Es repetible: inserta las cartas nuevas y actualiza las existentes sin borrar nada (no rompe mazos ya guardados). Con tu configuración `data.sql` no se ejecuta, por eso este paso es necesario. Sin él, el editor de mazos mostrará el catálogo vacío.
4. **Seguridad:** ejecuta también `docs/supabase/seguridad_rls.sql` (ver `docs/SEGURIDAD.md`).
5. Abre Unity, entra con tu nombre, pulsa **EDITAR MIS MAZOS**: deben aparecer las 36 cartas.

### Cómo añadir cartas nuevas
1. Edita `docs/catalogo/cartas.csv` (Excel vale: separador `;`, UTF-8). Las columnas y valores permitidos están explicados en la cabecera de `tools/catalog/generar_sql.py`.
2. Ejecuta `python tools/catalog/generar_sql.py` (valida el CSV y regenera `data.sql` y `docs/supabase/cartas.sql`).
3. Pega el nuevo `cartas.sql` en Supabase. Reinicia el backend si usas el perfil `demo`.

Hoy hay 36 cartas (4 facciones × 9: 6 unidades y 3 hechizos cada una), con keywords `JUMPER` y `DISRUPTION`, robar cartas, daño, bonificaciones, "gloria" y hechizos lentos/rápidos/instantáneos. Recuerda que solo `JUMPER` y `DISRUPTION` tienen lógica como keywords; el resto de efectos se declaran en la columna `effects`.

## 🔐 Seguridad: la contraseña de Supabase está en el historial de git

`TimewarpTCG.Back/src/main/resources/application.properties` está versionado en `develop` **con la contraseña en la URL** (desde el commit `456d63c`). Borrarlo ahora no la quita del historial. Si el repo es o ha sido público, **resetea la contraseña** en Supabase (*Project Settings ▸ Database ▸ Reset database password*). Esta rama no toca ese fichero.
