# Admin · Fuentes y datos

`/admin/fuentes` es una superficie corporativa de solo lectura para saber qué puede afirmar realmente Mágina Olivo sobre sus fuentes externas y pipelines internos.

## Principio

El panel no convierte ausencia de telemetría en un estado verde. Cada fuente declara cómo se obtiene su estado:

- `cache_health`: éxito/error/caducidad persistidos por la caché compartida.
- `pipeline_status`: estado de la última ejecución interna almacenada.
- `usage_only`: Mágina conserva referencias o verificaciones usadas, pero no monitoriza la disponibilidad del proveedor.

Estados posibles:

- `ok`: los últimos datos persistidos soportan una lectura correcta.
- `attention`: hay una situación observable que merece revisión, como caché caducada o trabajo todavía sin procesar.
- `error`: el último estado persistido registra un fallo.
- `unknown`: no hay datos suficientes todavía.
- `unmonitored`: la aplicación no persiste telemetría del proveedor y por tanto no afirma que esté operativo.

## Fuentes

### AEMET · previsión municipal

Lee `weather_forecast_cache` y el catálogo territorial. Expone municipios habilitados, entradas de caché, entradas caducadas, última descarga correcta y último error registrado.

No realiza una llamada en vivo a AEMET desde Administración.

### AEMET · radar

Lee `radar_snapshots`. Expone snapshots, fallos, assets validados para análisis, última observación/captura y último error.

No existe una cadencia productiva continua garantizada en el repositorio; por ello el panel **no inventa un umbral de frescura**. El estado representa el pipeline del último snapshot persistido, no la disponibilidad continua de AEMET.

### OCR

Lee `ocr_runs`. Expone la última ejecución y agregados de los últimos siete días: correctas, fallidas, en cola y procesando.

### Catastro y SIGPAC

Lee `field_land_refs`: referencias, vinculadas/verificadas y última comprobación usada.

Ambas aparecen con `state=unmonitored`. Que exista una referencia verificada no demuestra que el proveedor externo esté disponible ahora.

## API

`GET /api/v1/admin/sources`

- requiere sesión autenticada;
- requiere rol corporativo `support` o superior;
- es solo lectura;
- devuelve `generated_at` y cinco entradas de fuente;
- no expone credenciales, URLs privadas ni secretos de proveedor.

## Validación

`apps/api/src/testing/admin-sources-smoke.ts` crea datos controlados y verifica:

- AEMET vigente → `ok`;
- radar procesado → `ok`;
- OCR exitoso → `ok`;
- Catastro/SIGPAC → `unmonitored` aunque existan referencias;
- un owner de workspace sin rol de plataforma recibe `403 platform_admin_required`.

El smoke forma parte de `.github/workflows/admin-check.yml`.
