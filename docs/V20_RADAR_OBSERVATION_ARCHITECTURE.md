# Mágina Olivo V20 — Radar y observación de precipitación

## Estado

Arquitectura de ingestión en implementación y separada de previsión/nowcast.

## Regla principal

```text
AEMET previsión municipal  !=  radar observado  !=  nowcast
```

- **Previsión**: probabilidad, temperatura y viento por municipio.
- **Radar observado**: precipitación detectada en una fecha/hora concreta.
- **Nowcast**: movimiento/llegada futura. No se publicará hasta disponer de algoritmo y validación propios.

## Fuentes AEMET

### Producto estándar OpenData

`/opendata/api/red/radar/nacional`

Uso: visual/referencia y comprobación de disponibilidad. No se utilizará para mediciones espaciales si el recurso obtenido no es un raster georreferenciado verificable.

### Distribución georreferenciada oficial

Fuente operativa para análisis:

```text
https://www.aemet.es/es/api-eltiempo/radar/download/compo
```

El catálogo oficial de datos identifica esta distribución para la composición nacional de radar. La descarga responde como paquete `tar+gzip` y contiene las imágenes GeoTIFF recientes de la composición.

Mágina no asume que el Swagger OpenData publique esta ruta: se trata como una distribución oficial separada.

## Ingestión V20

```text
AEMET GeoTIFF bundle
       ↓
validar HTTPS + host + ruta exacta
       ↓
validar MIME/tamaño del tar.gz
       ↓
gunzip + tar streaming
       ↓
aceptar solo .tif/.tiff
       ↓
validar firma TIFF
       ↓
RadarBinaryAsset[]
       ↓
SHA-256 por raster
       ↓
RadarSnapshot
       ↓
S3: weather/radar/...
```

Límites iniciales de defensa:
- paquete comprimido: 50 MB;
- contenido descomprimido: 100 MB;
- GeoTIFF individual: 35 MB;
- máximo de entradas TIFF: 6;
- sin extracción al filesystem;
- entradas no TIFF se ignoran;
- TIFF con firma inválida se rechaza.

## Idempotencia

El bundle contiene varias imágenes recientes. Cada raster se deduplica por:

```text
source + product + sha256
```

Ejemplo:

```text
job 1 → A B        => guarda A + B
job 2 → A B        => 0 subidas
job 3 → A B C      => guarda solo C
```

Por tanto la frecuencia del job no multiplica almacenamiento mientras AEMET siga devolviendo los mismos raster.

## Persistencia

`radar_snapshots` conserva:
- fuente/producto/CRS declarado;
- `observed_at` cuando puede resolverse;
- `fetched_at`;
- formato/MIME/tamaño;
- SHA-256;
- URL oficial de origen;
- nombre de entrada del bundle;
- `storage_key` privado;
- estado `fetched | stored | processed | failed`;
- flags de análisis y error.

Los objetos radar no usan el flujo de subida de documentos de usuario. Comparten infraestructura S3-compatible, pero bajo el prefijo independiente `weather/radar`.

## Cola

```text
magina-radar-ingest-v1
└── DLQ: magina-radar-ingest-dlq-v1
```

El worker puede arrancar como:
- `WORKER_MODULES=ocr`
- `WORKER_MODULES=radar`
- `WORKER_MODULES=ocr,radar`

El modo radar requiere almacenamiento S3; la descarga GeoTIFF oficial directa no requiere `AEMET_API_KEY` en el worker.

## Nivel de confianza actual

Un archivo del bundle se marca candidato analítico tras validar paquete, extensión y firma TIFF. Antes de usar valores meteorológicos se añadirá una segunda validación interna con lector GeoTIFF:
- comprobar GeoKeys/CRS;
- bounding box/resolución;
- fecha/hora real;
- NoData;
- metadatos `ESCALA`/equivalencia de reflectividad;
- dimensiones y bandas esperadas.

Hasta superar esa validación, **no se calcularán dBZ ni alertas por distancia** basadas en píxeles.

## Estado de lluvia por finca — siguiente fase

```text
FarmRadarObservation
├── field_id
├── radar_snapshot_id
├── observed_at
├── precipitation_detected
├── nearest_echo_distance_km
├── direction_from_field
├── reflectivity_band
├── coverage_status
└── quality_flags
```

Mensajes permitidos cuando exista el análisis validado:
- `Precipitación detectada sobre la finca.`
- `Precipitación detectada a unos 8 km al oeste.`
- `No se detecta precipitación en el radio analizado.`
- `Radar sin cobertura suficiente o dato no disponible.`

Mensajes prohibidos todavía:
- `Lloverá en 20 minutos.`
- `La tormenta llegará a las 18:40.`
- `Caerán 12 mm en tu finca.`

## Mapa

`MapPlatform` mantendrá capas independientes:

```text
base cartográfica
PNOA opcional
SIGPAC/Catastro opcional
fincas
radar reflectividad
alertas/observaciones
```

La capa radar mostrará siempre la hora de observación y podrá activarse/desactivarse.

## Privacidad

Radar = dato público. La operación `radar -> finca` usa geometría privada y solo se realiza dentro del workspace autorizado. Nunca se publican coordenadas privadas en alertas públicas.

## Próximos pasos

1. CI verde de parser + worker + candidato completo;
2. integrar lector `geotiff` para verificar metadatos internos;
3. determinar `ESCALA`/reflectividad real del producto;
4. marcar snapshots como `processed` solo tras validación;
5. generar recorte/overlay MapLibre;
6. calcular observación segura por finca;
7. crear alertas de precipitación observada;
8. estudiar nowcast como módulo independiente.
