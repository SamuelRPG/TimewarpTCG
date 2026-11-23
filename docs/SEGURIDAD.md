# Seguridad: qué está protegido, qué debes hacer tú y qué queda por mejorar

Tres bloques: **(1)** lo que ya hace el código y cómo se ha comprobado, **(2)** lo que solo puedes hacer tú (no se puede arreglar desde el repositorio), **(3)** límites conocidos.

---

## 1. Lo que el código ya hace

| Riesgo | Antes | Ahora |
|---|---|---|
| **Suplantación** | El cliente mandaba su `playerId` en cada mensaje y el servidor se lo creía: bastaba conocer un UUID para jugar como otra persona. | Cuentas con **contraseña** (BCrypt) y **tokens firmados** (JWT HS256, caducan a las 12 h). La identidad sale **solo del token**, nunca del cuerpo del mensaje. Si el `playerId` que llega no coincide con el del token, se rechaza. |
| **Partidas ajenas** | Quien conociera un `gameId` podía actuar en la partida de otro. | Cada partida pertenece a su jugador; actuar en la de otro devuelve error y no cambia nada. Un jugador solo puede tener una partida a la vez. |
| **Espiar canales** | Cualquiera podía suscribirse a `/topic/player/{id}` de otro. | El servidor solo deja suscribirse a **tu propio** canal; si lo intentas, corta la sesión. Solo se aceptan mensajes a `/app/game/**`. |
| **Endpoints abiertos** | `DELETE /api/users/{id}` borraba cualquier cuenta; `POST/DELETE /api/menu/decks` aceptaba cualquier `userId`. | Mazos y borrado de cuenta exigen token y actúan **siempre sobre tu propio usuario** (`DELETE /api/users/me`). El `userId` en la URL ya no existe. |
| **Invitados** | Cualquier UUID valía como invitado. | El servidor emite la identidad de invitado (`POST /api/users/guest`); un invitado solo puede usar los mazos AGGRO y CONTROL y no guarda nada en la base de datos. |
| **Fuerza bruta** | Sin contraseña, no aplicaba. | 5 fallos seguidos por IP+usuario bloquean 5 minutos; el tiempo de respuesta no revela si un usuario existe; límite de ráfagas por IP en registro / login / invitado. |
| **Inundación** | Sin límites. | Por sesión WebSocket: ráfaga de 60 mensajes y 20/s sostenidos (configurable: `timewarp.security.ws-burst`, `ws-per-second`); si te pasas, el servidor corta la sesión. Mensajes de más de 64 KB se rechazan. |
| **Memoria** | Las partidas vivían en RAM para siempre. | Caducan a los 30 minutos sin actividad (`timewarp.games.idle-minutes`) y hay un tope de 1000 simultáneas (`timewarp.games.max-concurrent`). |
| **Origen cruzado** | `@CrossOrigin("*")` y WebSocket abierto a cualquier origen. | Por defecto solo `localhost`. Para tu web: `timewarp.cors.allowed-origins=https://tu-dominio` (o la variable `TIMEWARP_CORS_ALLOWED_ORIGINS`). Un cliente Unity de escritorio no envía `Origin` y no le afecta. |
| **Datos internos en errores** | Los mensajes de excepción iban tal cual al jugador. | Errores genéricos hacia fuera; el detalle queda solo en el log del servidor. Las reglas del juego sí se explican ("no tienes recurso suficiente"). |
| **Credenciales en el repo** | La contraseña de Supabase estaba en `application.properties`, versionado. | El fichero ya no tiene secretos y arranca con H2 en memoria. Tus datos van en variables de entorno / `supabase.env.cmd` (en `.gitignore`). |
| **Mazos** | — | 40 cartas, 3 copias, 2 facciones, nombre y número de mazos se validan **en el servidor**, aunque el editor de Unity ya los aplique. |
| **Cifrado** | `ws://` y `http://`. | El cliente Unity usa `wss://` cuando escribes `https://…` como servidor (nunca degrada a `ws://`). El cifrado en sí lo pone el proxy HTTPS del apartado 2.4. |
| **Dependencias** | Spring Boot 3.2.0 (sin parches). | Spring Boot 3.5.16; no hay SQL escrito a mano (todo JPA con parámetros). |
| **Base de datos** | El backend usaba el rol dueño de todo. | Script `docs/supabase/rol_app.sql`: rol que solo puede leer/escribir las 4 tablas (sin crear, borrar ni alterar tablas) y `docs/supabase/seguridad_rls.sql`: la API pública de Supabase no ve nada. |

### Cómo se ha comprobado (repítelo cuando cambies algo)

* `mvnw test` (en `TimewarpTCG.Back`): 36 pruebas, entre ellas contraseñas, tokens manipulados/caducados, límites y los endpoints REST.
* `dotnet run -c Release -- localhost:8080 SECURITY 1` (en `tools/ClientSmokeTest`, con el backend en marcha): **16 ataques reales** contra el servidor (mazos sin token o con uno falso, borrar cuentas sin autenticar, conectar sin token, espiar el canal de otro, iniciar partida como otro, actuar en la partida de otro, invitado con mazo propio, inundación). Debe terminar con `SEGURIDAD: TODO OK`.
* Partidas completas como invitado y con cuenta y mazo propio, contra H2 **y contra PostgreSQL con el rol restringido** (que además intentó `DROP`, `CREATE`, `ALTER`, `TRUNCATE`, desactivar RLS y borrar cartas: todo rechazado; y el rol de la API pública vio 0 filas).

---

## 2. Lo que solo puedes hacer tú

Ordenado por urgencia.

### 🔴 Hoy

1. **Cambia la contraseña de la base de datos de Supabase.** Estuvo en `application.properties` y quedó en el historial de git de `develop` desde el commit `456d63c`; borrar el fichero no la elimina del historial. *Supabase ▸ Project Settings ▸ Database ▸ Reset database password*. Hazlo aunque el repo sea privado. Esta rama **no** contiene ninguna contraseña, pero tu `develop` sí.
2. **Guarda la nueva contraseña solo en `supabase.env.cmd`** (ya está en `.gitignore`; no lo subas) o en variables de entorno.
3. **Cuidado al traerte esta rama:** sustituye tu `application.properties` local por uno sin contraseña. **Haz copia de tu contraseña antes** (solo para pasarla a `supabase.env.cmd`; recuerda que debes cambiarla igualmente).
4. **Ejecuta en Supabase** (SQL Editor), en este orden, una vez: `docs/supabase/seguridad_rls.sql` y después `docs/supabase/rol_app.sql` (cambia antes la contraseña que lleva el script). Sigue las indicaciones del final de `rol_app.sql` para usar ese rol desde el backend.

### 🟠 Esta semana

5. **GitHub ▸ Settings ▸ Code security:** activa *Secret scanning* y *Push protection*. Añade *Branch protection* a `main` y `develop` y la **verificación en dos pasos** en tu cuenta.
6. **Quita del repo lo que no debe estar:** `.idea/` y `data/*.mv.db` están subidos a `develop` (`git rm -r --cached .idea data`); el `.gitignore` de esta rama ya los excluye.
7. **Historial:** si el repositorio es o fue público, da la contraseña antigua por comprometida (ya la habrás cambiado en el paso 1). Borrarla del historial es posible (`git filter-repo` o BFG) pero reescribe todos los commits; hazlo solo si entiendes las consecuencias, y siempre *después* de cambiarla.
8. **Borra la rama obsoleta** `feature/unity-client-y-fix-bot` en GitHub (fue un primer intento; la buena es `feature/unity-sobre-develop`).

### 🟡 Antes de enseñárselo a otras personas (publicarlo en internet)

Un servidor accesible desde internet necesita:

* **HTTPS/WSS.** No expongas el puerto 8080 directamente. Pon delante un proxy que ponga el certificado; con **Caddy** es un fichero de tres líneas (gestiona el certificado y el WebSocket solo):

  ```
  juego.tu-dominio.com {
      reverse_proxy localhost:8080
  }
  ```

  En Unity escribe `https://juego.tu-dominio.com` como servidor. Con el proxy en la misma máquina, define también `SERVER_FORWARD_HEADERS_STRATEGY=native` para que los límites por IP vean la IP real del jugador y no la del proxy (hazlo **solo** detrás de tu propio proxy).
* **`TIMEWARP_JWT_SECRET`** fija, aleatoria y larga (≥ 32 caracteres), distinta en cada entorno. Si cambias la clave, todas las sesiones se cierran.
* **`TIMEWARP_CORS_ALLOWED_ORIGINS`** solo si tienes cliente web.
* No publiques la consola H2 ni ningún endpoint de depuración (`spring.h2.console.enabled=false` ya viene así) y no actives `spring.jpa.show-sql` en producción.
* Activa **Dependabot** en GitHub y actualiza Spring Boot cuando salga un parche (conviene revisar también la rama 4.x cuando madure).
* Las copias de seguridad de la base de datos, y pensar si guardarás datos personales (si la gente se registra con un nombre y una contraseña, el nombre ya lo es: informa en tu política de privacidad cuando publiques).

---

## 3. Límites conocidos (no están arreglados)

* **Los tokens no se pueden revocar** antes de caducar (12 h): no hay "cerrar sesión en todos los sitios". (Si borras la cuenta, su token sigue siendo válido hasta caducar, pero ya no sirve para nada: el servidor responde "Usuario no encontrado".) Para revocarlos todos, cambia `TIMEWARP_JWT_SECRET`.
* **No hay recuperación de contraseña ni correo electrónico:** si alguien la olvida, hay que cambiarla a mano en la base de datos.
* **Los contadores anti-fuerza-bruta y los límites viven en la memoria del servidor:** se reinician al apagarlo y no se comparten entre varias instancias (con una sola, como ahora, no es problema).
* **No hay límite de ritmo en los endpoints de mazos** (solo el tope de 30 mazos por usuario y el de tamaño de mensaje); el de registro/login/invitado sí lo tiene.
* **No hay temporizador de turno** ni detección de trampas más allá de que el servidor valida todas las reglas.
* **Las partidas siguen en RAM:** si se reinicia el servidor, se pierden las partidas en curso.
* **Cuentas antiguas** (creadas cuando solo hacía falta el nombre): no pueden entrar. Ver `docs/GUIA_ARRANQUE.md`, punto 6, para recuperarlas.

## Lo que ya hacías bien (conserva esto)

* **El servidor es la autoridad.** Todas las reglas se validan en el backend; el cliente Unity solo da pistas. Nunca muevas validaciones al cliente: un cliente se puede modificar.
* **La mano del rival no viaja al cliente** (`hand = null`, solo `handSize`) ni el orden del mazo. Mantenlo: si algún día mandas "por comodidad" la mano del rival, cualquiera la leería con un depurador de red.

## En el cliente Unity

* **Nunca** metas en Unity la contraseña de la base de datos, la clave `service_role` de Supabase ni claves de API: lo que va en un build se puede extraer. Unity solo habla con **tu backend**.
* `PlayerPrefs` (no cifrado) solo guarda el servidor, el nombre de usuario, el mazo elegido y el mazo del bot. **La contraseña y el token no se guardan nunca**: viven en memoria mientras la app está abierta.
* Asume que un jugador puede enviar mensajes inventados (con una herramienta, sin tu cliente). Por eso la regla de oro: **validar todo en el servidor**.
