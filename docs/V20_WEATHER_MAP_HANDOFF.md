# V20 clima / radar / mapa — handoff a `integrate/v20-beta-closure`

## Alcance cerrado

Rama propietaria: `feat/v20-weather-map`.

Este frente cierra la funcionalidad previa al rediseño visual de clima, radar y mapa de finca. No modifica `main`, GIS, Admin, Foundation ni staging compartido.

La pantalla `/radar?fieldId=<uuid>` trabaja con tres fuentes independientes:

1. geometría canónica de la finca mediante `GET /api/v1/fields/:fieldId/map-context`;
2. previsión municipal AEMET mediante `GET /api/v1/fields/:fieldId/weather/daily`;
3. observación radar AEMET mediante `GET /api/v1/fields/:fieldId/radar/latest` y, cuando existe un snapshot validado, `GET /api/v1/fields/:fieldId/radar/latest/overlay.png`.

Una fuente puede degradarse sin inutilizar las otras. La UI distingue `loading`, `error`, `stale` y ausencia de datos, y permite reconsultar con **Actualizar**.

## Radar y semántica meteorológica

El pipeline usa el mosaico nacional AEMET de reflectividad observado en GeoTIFF EPSG:4326. La ingestión valida CRS, bbox y paleta antes de proyectar observaciones a las fincas. El overlay PNG se genera únicamente desde un GeoTIFF que vuelve a pasar esa validación y conserva los colores de las bandas de reflectividad; los píxeles de cielo despejado/sin cobertura quedan transparentes.

No existe conversión automática dBZ → mm/h, nowcast ni ETA. `nearest_echo_distance_km` y dirección son una descripción espacial de la observación, no una predicción de movimiento o llegada.

El worker, cuando `WORKER_MODULES` incluye `radar`, programa `RADAR_INGEST_QUEUE_NAME` cada 10 minutos (`*/10 * * * *`, UTC). Cada ejecución sustituye la marca estática del cron por su hora real antes de ingerir el producto. La marca `stale` del radar es solo frescura operativa de la última observación; por defecto se activa al superar 30 minutos y puede configurarse con `RADAR_STALE_AFTER_MINUTES`.

## Dependencias operativas

Para radar real hacen falta las mismas credenciales S3 usadas por el worker y la API, de modo que el worker pueda escribir el GeoTIFF y la API pueda generar una URL de lectura para el mismo `storage_key`. El worker radar requiere acceso saliente al producto público de AEMET.

La previsión diaria mantiene la caché existente: una respuesta vigente se sirve como `fresh`; si caduca se intenta refrescar; si AEMET falla y existe una entrada anterior se devuelve `stale`; si no existe caché utilizable y falla el proveedor, la API responde error sin inventar valores.

## Fallos parciales

- AEMET previsión falla: mapa y radar observado siguen disponibles.
- Radar/latest falla o no tiene observación: previsión AEMET y geometría siguen disponibles.
- El almacenamiento/overlay falla: la observación radar textual sigue disponible; solo se omite el raster.
- Finca sin geometría: previsión y observación textual pueden seguir mostrándose; no se superpone raster sobre una geometría inventada.
- Snapshot no validado, sin bbox, no procesado o no `analysis_ready`: no se publica overlay.

## Superficies y archivos clave

- `apps/api/src/app.ts`
- `apps/api/src/routes/radar.ts`
- `apps/api/src/weather/radar-overlay.ts`
- `apps/worker/src/radar/**`
- `apps/worker/src/server.ts`
- `apps/web/src/components/radar-observation-panel.tsx`
- `apps/web/src/components/farm-map.tsx`
- `apps/web/src/lib/api-client.ts`
- `apps/api/src/testing/radar-latest-smoke.ts`
- `apps/api/src/testing/radar-overlay-smoke.ts`
- `e2e/weather-radar-map.spec.ts`
- `.github/workflows/weather-map-closure.yml`

No se añade ninguna migración nueva en este cierre.

## Validación antes de absorber

Gate específico: **V20 weather radar map closure**. Ejecuta typecheck/build, smokes científicos del GeoTIFF/paleta/análisis espacial, renderer PNG, migraciones sobre PostGIS efímero, pruebas API de caché/radar/map-context y Playwright con matriz móvil 360/390/430 px. No usa staging compartido.

El último commit funcional de código que debe quedar validado por este gate es `fff1915ae1aaf5382af41f1b082c7e8ec379e29f` (`fix(weather): stamp radar ingest execution time`).

## Absorción en `integrate/v20-beta-closure`

**No hacer un merge ciego de esta rama completa sobre integración.** `integrate/v20-beta-closure` ha avanzado en paralelo y ambas ramas divergen desde el ancestro común `28942aad9d0ae500061cdf9ca648da72619c7106`.

El contrato de este frente es el delta de **18 commits** comprendido entre:

- base común: `28942aad9d0ae500061cdf9ca648da72619c7106` (excluido);
- último commit funcional: `fff1915ae1aaf5382af41f1b082c7e8ec379e29f` (incluido).

Al absorber, preservar siempre la versión más reciente de integración en archivos compartidos y aplicar sobre ella únicamente el comportamiento de clima/radar/mapa descrito aquí. Zonas con mayor probabilidad de conflicto: `apps/api/src/app.ts`, `apps/worker/src/server.ts`, `apps/web/src/components/farm-map.tsx`, `apps/web/src/components/radar-observation-panel.tsx` y `apps/web/src/lib/api-client.ts`.

En `apps/api/src/app.ts`, el cambio de este frente es únicamente que `registerRadarRoutes` recibe `storage`; no debe revertirse trabajo ajeno en otras rutas. En el worker, conservar cualquier módulo añadido por integración y mantener además el schedule/consumer de radar. En el mapa y panel radar, preservar el overlay real, geometría canónica y degradación independiente por fuente.

Después de absorber el delta, ejecutar de nuevo los gates generales del candidato integrado además del gate weather/map. No fusionar este frente directamente en `main`.
