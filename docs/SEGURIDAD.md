# Seguridad: qué revisar para que no se filtre nada y no te hackeen

Ordenado por urgencia. Lo marcado con ✅ ya está bien en tu diseño; lo demás es trabajo pendiente.

## 🔴 Hazlo ya (30 minutos)

1. **Cambia la contraseña de Supabase.** `TimewarpTCG.Back/src/main/resources/application.properties` está versionado en `develop` con la contraseña dentro de la URL JDBC, y quedó en el historial de git desde el commit `456d63c`. Borrar el fichero no la elimina del historial. *Supabase ▸ Project Settings ▸ Database ▸ Reset database password*.
2. **Saca las credenciales del repo.** Usa variables de entorno (`SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`; Spring las lee solas) o un `application-local.properties` ignorado por git. Después `git rm --cached` del `application.properties` real y sube solo una plantilla (`application.properties.example`).
3. **Activa RLS en Supabase** ejecutando `docs/supabase/seguridad_rls.sql`. Sin eso, las tablas que crea Hibernate quedan expuestas a quien tenga la clave pública `anon`.
4. **Activa en GitHub** (*Settings ▸ Code security*): *Secret scanning* y *Push protection*; añade protección a `main`/`develop` (*Branch protection*) y activa la verificación en dos pasos en tu cuenta.
5. **`.idea/` y `data/*.mv.db` están subidos** a `develop`: quítalos (`git rm -r --cached`) y ya están en el `.gitignore` de esta rama. La base de datos H2 puede contener datos.

> Si el repo es o fue público, trata la contraseña como **comprometida** aunque la cambies después: rótala siempre. Para borrar secretos del historial existe `git filter-repo` o BFG, pero reescribe el historial y cambia todos los commits: hazlo solo cuando entiendas las consecuencias (y de todos modos rota la contraseña).

## 🟠 Antes de enseñárselo a nadie

| Riesgo | Situación actual | Qué hacer |
|---|---|---|
| **Identidad** | El cliente manda su `playerId` (un UUID) en cada mensaje y el servidor se lo cree. No hay contraseña ni token. Quien conozca o adivine un UUID puede jugar como esa persona. | Autenticación real: tokens (JWT) o *Supabase Auth*. El servidor debe sacar el jugador **del token**, nunca del cuerpo del mensaje, y comprobar que el `gameId` pertenece a ese jugador. |
| **Endpoints abiertos** | `DELETE /api/users/{id}` borra una cuenta y sus mazos sin comprobar quién llama. `POST/DELETE /api/menu/decks` acepta cualquier `userId`. | Exigir autenticación y que `userId == usuario del token`. |
| **Origen cruzado** | `@CrossOrigin(origins = "*")` y `setAllowedOrigins("*")` en el WebSocket. | Limitar a tus dominios cuando tengas versión web. (Un cliente Unity de escritorio no depende de CORS.) |
| **Tráfico sin cifrar** | `ws://` y `http://`. | Poner el backend detrás de HTTPS/WSS (Caddy, Nginx o la plataforma donde lo alojes). |
| **Abuso / memoria** | Las partidas viven en RAM y no caducan; no hay límite de partidas por jugador ni de peticiones por segundo. | Caducidad por inactividad, tope de partidas simultáneas por usuario y *rate limiting*. (Ya hay tope de 30 mazos por usuario y de 500 acciones del bot por petición.) |
| **Usuario en BBDD** | La aplicación usa el rol dueño (`postgres.xxxx`). | Crear un rol propio solo con los permisos que necesita la app. |
| **Dependencias** | Spring Boot **3.2.0** (nov. 2023) ya no recibe parches. | Subir a una versión con soporte (3.4/3.5) y activar *Dependabot*. |
| **Logs** | `spring.jpa.show-sql=true` vuelca todas las consultas; los mensajes de error del servidor van tal cual al jugador. | Desactivarlo en producción; no incluir datos internos en los mensajes. |

## ✅ Lo que ya haces bien (conserva esto)

* **El servidor es la autoridad.** Todas las reglas se validan en el backend; el cliente Unity solo da pistas. Nunca muevas validaciones al cliente: un cliente se puede modificar.
* **La mano del rival no viaja al cliente** (`hand = null`, solo `handSize`) ni el orden del mazo. Mantenlo: si algún día mandas "por comodidad" la mano del rival, cualquiera lo leería con un depurador de red.
* **Límites en los mazos** (40 cartas, 3 copias, 2 facciones, nombre y nº de mazos) se comprueban en el servidor aunque el editor de Unity ya los aplique.

## En el cliente Unity

* **Nunca** metas en Unity la contraseña de la base de datos, la clave `service_role` de Supabase ni claves de API: lo que va en un build se puede extraer. Unity solo habla con **tu backend**.
* No guardes en `PlayerPrefs` nada sensible (hoy solo guarda el servidor, el nombre, el mazo elegido y un UUID de invitado; `PlayerPrefs` no está cifrado).
* Asume que un jugador puede enviar mensajes inventados (con una herramienta, sin tu cliente). Por eso la regla de oro es la de arriba: **validar todo en el servidor**.
