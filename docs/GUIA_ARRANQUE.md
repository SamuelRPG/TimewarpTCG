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
2. **Java 21:** *File ▸ Project Structure ▸ Project ▸ SDK ▸ Add SDK ▸ Download JDK…* → versión **21**, vendor *Eclipse Temurin* → Download. Pon *Language level* a 21.
3. Abre `src/main/java/com/saroga/timewarp/TimewarpApplication.java` y pulsa el triángulo verde junto a `main` ▸ *Run*. Fallará por la base de datos: es normal, solo sirve para crear la configuración.
4. *Run ▸ Edit Configurations…* ▸ selecciona **TimewarpApplication** ▸ *Modify options* ▸ marca **Add VM options** ▸ en el campo que aparece escribe exactamente:

   ```
   -Dspring.profiles.active=demo
   ```
5. OK ▸ Run. Listo cuando la consola ponga `Started TimewarpApplication in … seconds`.

### Camino B: con la terminal

Instala Java 21 y Maven (una vez) y **cierra y vuelve a abrir PowerShell**:

```powershell
winget install EclipseAdoptium.Temurin.21.JDK
winget install Apache.Maven
```

Luego, en la carpeta `TimewarpTCG.Back`:

```powershell
mvn -Dmaven.test.skip=true spring-boot:run "-Dspring-boot.run.profiles=demo"
```

(Las comillas son necesarias en PowerShell.)

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
| Bajar una unidad | Clic en la carta de la mano → clic en **TU BASE** |
| Lanzar un hechizo | Clic en la carta → (elige objetivos si los pide) → **LANZAR** |
| Mover una unidad | Clic en una unidad tuya lista → clic en la zona verde. Se ejecuta al pulsar **CONFIRMAR MOVIMIENTOS** |
| Pasar / terminar turno | Botón grande azul |
| Qué hizo el bot | Registro (panel izquierdo inferior) |

El cliente solo da pistas; no bloquea jugadas ilegales: verás el error de tu backend en rojo.

## 5. Si algo falla

| Síntoma | Solución |
|---|---|
| Unity: "No se pudo conectar" | El backend no está arrancado o no usa el puerto 8080. |
| El backend no arranca y menciona `HikariPool`, `Connection` o `postgresql` | Te falta el perfil `demo` (VM option `-Dspring.profiles.active=demo`). |
| `mvn no se reconoce` | Usa el Camino A, o instala Maven (Camino B) y reabre PowerShell. |
| Unity: los clics no hacen nada (Unity 6) | *Edit ▸ Project Settings ▸ Player ▸ Other Settings ▸ Active Input Handling = **Both***, y reinicia Unity. |
| Error de compilación con `ClientWebSocket` | Unity demasiado antiguo: usa 2021.3 LTS+ y *Api Compatibility Level = .NET Standard 2.1*. |
| No aparece nada al pulsar Play | Busca en la consola líneas `[Timewarp]` y pásame el mensaje completo. |

> No he podido ejecutar la interfaz dentro de Unity (no tengo Unity en mi entorno). La red y las reglas están probadas con más de 60 partidas contra tu backend y todo el código compila contra la API real de Unity, pero el aspecto visual puede necesitar ajustes.

## 6. Más adelante: usar Supabase de verdad

1. En el panel de Supabase comprueba que el proyecto **no está pausado** (el plan gratuito pausa los inactivos; pulsa *Restore*).
2. Quita el perfil `demo`. Tu `application.properties` ya apunta a Supabase.
3. `data.sql` **no se ejecuta** con tu configuración (`spring.sql.init.mode=never`): las 6 cartas deben estar ya en `card_templates`; si no, ejecuta `data.sql` en el *SQL Editor* de Supabase.

## 🔐 Seguridad: la contraseña de Supabase está en el historial de git

`TimewarpTCG.Back/src/main/resources/application.properties` está versionado en `develop` **con la contraseña en la URL** (desde el commit `456d63c`). Borrarlo ahora no la quita del historial. Si el repo es o ha sido público, **resetea la contraseña** en Supabase (*Project Settings ▸ Database ▸ Reset database password*). Esta rama no toca ese fichero.
