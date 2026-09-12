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

El worker, cuando `WORKER_MODULES` incluye `radar`, programa `RADAR_INGEST_QUEUE_NAME` cada 10 minutos (`*/10 * * * *`, UTC). La marca `stale` del radar es solo frescura operativa de la última observación; por defecto se activa al superar 30 minutos y puede configurarse con `RADAR_STALE_AFTER_MINUTES`.

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

- `apps/api/src/routes/weather.ts`
- `apps/api/src/routes/radar.ts`
- `apps/api/src/weather/radar-overlay.ts`
- `apps/worker/src/radar/**`
- `apps/worker/src/server.ts`
- `apps/web/src/components/radar-observation-panel.tsx`
- `apps/web/src/components/farm-map.tsx`
- `e2e/weather-radar-map.spec.ts`
- `.github/workflows/weather-map-closure.yml`

No se añade ninguna migración nueva en este cierre.

## Validación antes de absorber

Gate específico: **V20 weather radar map closure**. Ejecuta typecheck/build, smokes científicos del GeoTIFF/paleta/análisis espacial, renderer PNG, migraciones sobre PostGIS efímero, pruebas API de caché/radar/map-context y Playwright con matriz móvil 360/390/430 px. No usa staging compartido.

Para absorber en `integrate/v20-beta-closure`, integrar la rama completa (no seleccionar solo UI): API, worker, web, pruebas y workflow forman el contrato cerrado. Después de la absorción, volver a ejecutar los gates generales del candidato integrado. No fusionar este frente directamente en `main`.
