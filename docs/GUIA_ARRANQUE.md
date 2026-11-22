# Guía de arranque: backend + Unity + tu primera partida

Rama: `feature/unity-sobre-develop` (sale de `develop`). Carpetas:

| Carpeta | Qué es |
|---|---|
| `TimewarpTCG.Back/` | Tu backend (con los arreglos de esta rama) |
| `TimewarpTCG.Unity/Assets/Timewarp/` | El cliente Unity (se copia a un proyecto Unity) |
| `tools/` | Pruebas opcionales sin Unity |
| `docs/` | Esta guía + reglas de tu juego (`01_…`) + problemas y hoja de ruta (`02_…`) |

## ¿Hay que arrancar Supabase? **No.**

* Supabase es una base de datos PostgreSQL **en la nube**: no se "arranca" en tu PC, solo está accesible o no.
* Para tu **primera partida contra el bot no hace falta ninguna base de datos**: los mazos AGGRO y CONTROL salen del código (`DeckFactory`) y la partida vive en la memoria del servidor.
* **Pero** el backend, tal como está en `develop`, intenta conectar a Supabase al arrancar y **si no puede, no arranca**. Por eso esta rama añade el perfil **`demo`**: usa una base de datos en memoria (H2), carga tus 6 cartas de `data.sql` y **no toca Supabase**. Es lo que debes usar ahora.
* Un proyecto antiguo de Supabase suele fallar por: el plan gratuito **pausa** los proyectos inactivos (hay que pulsar *Restore project* en el panel de Supabase), la contraseña cambió, o tu red bloquea el puerto 6543. Déjalo para más adelante (sección final).

## Paso 1 — Backend (perfil demo)

Necesitas **Java 21** y **Maven** (o IntelliJ).

```bash
git checkout feature/unity-sobre-develop
cd TimewarpTCG.Back
mvn -Dmaven.test.skip=true spring-boot:run -Dspring-boot.run.profiles=demo
```

**Con IntelliJ:** *Run ▸ Edit Configurations ▸ TimewarpApplication ▸ Active profiles = `demo`* y Run (si ya la tenías, edítala; no crees otra).

Listo cuando veas `Started TimewarpApplication in … seconds` (puerto 8080). Prueba rápida, en otra terminal:

```bash
curl -X POST "http://localhost:8080/api/users/register?username=prueba"
```

Debe devolver `{"id":"…","username":"prueba"}`.

Cada vez que reinicies, la base de datos demo empieza vacía (es en memoria). Es lo esperado.

## Paso 2 — Unity

1. **Unity Hub**: instala **Unity 2021.3 LTS o superior** (vale 2022 LTS y Unity 6).
2. *New project* → plantilla **2D (Built-in)** o **Universal 2D**.
3. Copia la carpeta `TimewarpTCG.Unity/Assets/Timewarp` dentro de la carpeta `Assets` de ese proyecto. Espera a que compile; la consola no debe tener errores rojos.
4. Abre la escena por defecto y pulsa **▶ Play**. En la pestaña *Game* pon *Maximize On Play*.
5. Deja `localhost:8080`, el nombre vacío (= invitado), elige **AGGRO** (combates) o **CONTROL** (hechizos y pila) y pulsa **JUGAR CONTRA EL BOT**.

No hay escenas ni prefabs que montar: la interfaz se construye sola al pulsar Play.

**Si los clics no hacen nada** (Unity 6): *Edit ▸ Project Settings ▸ Player ▸ Other Settings ▸ Active Input Handling = **Both***, y reinicia Unity.

### Controles
| Quiero… | Hago… |
|---|---|
| Bajar una unidad | Clic en la carta de la mano → clic en **TU BASE** |
| Lanzar un hechizo | Clic en la carta → (elige objetivos si los pide) → **LANZAR** |
| Mover una unidad | Clic en una unidad tuya lista → clic en la zona verde. Se ejecuta al pulsar **CONFIRMAR MOVIMIENTOS** |
| Pasar / terminar turno | Botón grande azul (cambia de texto según lo que va a pasar) |
| Qué hizo el bot | Registro (panel izquierdo inferior) |

El cliente solo da pistas; **no bloquea jugadas ilegales**: verás el error de tu backend en rojo.

## Problemas típicos

| Síntoma | Solución |
|---|---|
| Unity: "No se pudo conectar" | El backend no está arrancado, o el puerto no es 8080 (cámbialo en el campo *Servidor*). |
| El backend no arranca y menciona `HikariPool`, `Connection` o `postgresql` | No estás usando el perfil `demo`. Añade `-Dspring-boot.run.profiles=demo`. |
| Error de compilación con `ClientWebSocket` | Unity demasiado antiguo: usa 2021.3 LTS+ y *Api Compatibility Level = .NET Standard 2.1*. |
| No aparece nada al pulsar Play | Busca en la consola líneas `[Timewarp]` y pásame el mensaje completo. |
| La partida se queda en "ESPERANDO AL SERVIDOR…" | Mira la consola del backend; pulsa *ABANDONAR PARTIDA* y reinicia. |

> Aviso: no he podido ejecutar la interfaz dentro de Unity (no tengo Unity en mi entorno). La lógica de red y reglas está probada con más de 60 partidas contra tu backend y todo el código compila contra la API real de Unity, pero el aspecto visual puede necesitar ajustes. Si algo falla, mándame el texto de la consola o una captura.

## Más adelante: usar Supabase de verdad

1. En el panel de Supabase comprueba que el proyecto **no está pausado** (si lo está, *Restore*).
2. Crea tu `application-local.properties` (lo ignora git) con tu URL y contraseña, y arranca con `-Dspring.profiles.active=local`. No pongas credenciales en ficheros versionados.
3. `data.sql` **no se ejecuta** con tu configuración (`spring.sql.init.mode=never`): las 6 cartas deben existir ya en la tabla `card_templates` de Supabase; si no, ejecuta el contenido de `data.sql` en el *SQL Editor* de Supabase.
4. Recuerda que con 6 cartas (máx. 3 copias cada una) no se puede armar un mazo de 40: ver `docs/02_PROBLEMAS_Y_HOJA_DE_RUTA.md`.

## 🔐 Seguridad: la contraseña de Supabase está en tu historial de git

`TimewarpTCG.Back/src/main/resources/application.properties` está **versionado en `develop` con la contraseña dentro de la URL** (desde el commit `456d63c`, mayo de 2026). Aunque lo borres ahora, sigue en el historial. Si el repositorio es público, o lo ha sido en algún momento, **cambia la contraseña en Supabase** (*Project Settings ▸ Database ▸ Reset database password*). Esta rama no toca ese fichero.
