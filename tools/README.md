# Tools (opcionales)

Sirven para comprobar el backend **sin abrir Unity**. Necesitan el backend en marcha (`00_EMPIEZA_AQUI.md`, paso 1).

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

## tools/catalog/generar_sql.py

Genera `data.sql` y `docs/supabase/cartas.sql` desde `docs/catalogo/cartas.csv` (ver `docs/GUIA_ARRANQUE.md`, sección "Cómo añadir cartas nuevas").
