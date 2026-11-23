# Tools (opcionales)

Sirven para comprobar el backend **sin abrir Unity**. Necesitan el backend en marcha (`docs/GUIA_ARRANQUE.md`, punto 2).

## ClientSmokeTest (C#, .NET 8)

Compila **los mismos `.cs` de red y modelo que usa Unity** (`Net/` y `Model/`) y juega N partidas completas contra el bot con un agente
sencillo. Comprueba que no haya bloqueos, que se cumplan invariantes del tablero (≤6 por zona, recurso 0-10, sin cartas duplicadas…),
que las pistas del cliente coincidan con las validaciones del servidor y que el fin de partida sea coherente. Incluye autopruebas
de la lógica del cliente (fin de partida, parseo de objetivos, registro de partida) que no necesitan servidor.

```bash
cd tools/ClientSmokeTest
dotnet run -c Release -- localhost:8080 AGGRO 10        # host  mazo  nº de partidas
dotnet run -c Release -- localhost:8080 CONTROL 3 v     # "v" = imprime el registro de cada jugada
```

El agente entra como **invitado** (o con una cuenta que registra él mismo en el modo `CUSTOM`): el servidor exige token en REST y WebSocket.
Va a un ritmo de unas 18 acciones por segundo, como un jugador muy rápido, porque el servidor limita las ráfagas de mensajes por sesión.

Salida esperada con el backend parcheado: todas las partidas `GAMEOVER` (AGGRO) o `STALLED` (CONTROL contra CONTROL no termina: ver B6 en
`02_PROBLEMAS_Y_HOJA_DE_RUTA.md`), y `RESULTADO: OK`. Si ves `STUCK` o timeouts, el backend se ha colgado.

## python/stomp_probe.py

Veinte líneas que muestran el protocolo STOMP “en crudo” (conectar, suscribirse, pedir partida, recibir el estado). Útil como referencia.

```bash
pip install websockets
python3 tools/python/stomp_probe.py AGGRO
```

## Probar mazos personalizados (flujo del editor de mazos)

```bash
dotnet run -c Release -- localhost:8080 CUSTOM 10            # registra un usuario, lee el catálogo, guarda un mazo legal por REST y juega con él
dotnet run -c Release -- localhost:8080 CUSTOM 5 x CONTROL   # igual, pero el bot usa el mazo CONTROL (4º argumento libre, 5º = mazo del bot)
```

El informe incluye hechizos lanzados (y cuántos con objetivo), la profundidad máxima de la pila y cuántas pistas del cliente discrepan del servidor.

## Comprobar la seguridad con ataques reales

```bash
dotnet run -c Release -- localhost:8080 SECURITY 1
```

Lanza contra el servidor en marcha los ataques típicos y comprueba que **todos** fallan: pedir mazos sin token o con uno falso, borrar cuentas sin
autenticar, conectar al WebSocket sin token, suscribirse al canal de otro jugador, iniciar una partida diciendo ser otro, actuar en la
partida de otro, usar un mazo propio siendo invitado e inundar el servidor de mensajes. Termina con `SEGURIDAD: TODO OK` (código de salida 0)
o lista los fallos. Conviene ejecutarlo tras cualquier cambio en `infrastructure/security`.

## Probar el modo online (dos jugadores)

```bash
dotnet run -c Release -- localhost:8080 PVP 2
```

Hace jugar a **dos clientes reales** entre sí por la cola de emparejamiento, `2` partidas completas, y comprueba: que los dos ven la misma partida desde su lado, que la mano del rival no viaja, que actuar sin prioridad se rechaza, que acaban con el mismo ganador y marcador, y los casos límite: **abandonar**, **desconexión brusca**, **salir de la cola**, invitado con mazo propio y pulsar "buscar" dos veces. Termina con `ONLINE: TODO OK`.

> **El ritmo del bot.** Por defecto el bot juega una acción cada ~0,7 s. Para pruebas rápidas arranca el backend con `TIMEWARP_BOT_DELAY_MS=0` (juega de golpe, como antes); el agente de pruebas funciona con cualquier valor porque espera a que el bot devuelva la prioridad.

## tools/catalog/generar_sql.py

Genera `data.sql` y `docs/supabase/cartas.sql` desde `docs/catalogo/cartas.csv` (ver `docs/GUIA_ARRANQUE.md`, sección "Cómo añadir cartas nuevas").
