# Mágina Olivo V20 — Radar y observación de precipitación

## Estado

Arquitectura aprobada para la primera fase de radar. No confundir este módulo con previsión meteorológica ni con nowcast.

## Regla principal

```text
AEMET previsión municipal  !=  radar observado  !=  nowcast
```

- **Previsión**: responde a probabilidad/temperatura/viento por municipio y horizonte temporal.
- **Radar observado**: responde a precipitación detectada por radar en una fecha/hora concreta.
- **Nowcast**: estimaría movimiento y posible llegada futura. No se publicará hasta tener un algoritmo y validación propios.

## Fuente inicial

Primera fuente: **mosaico nacional AEMET de reflectividad**.

Motivos:

1. evita seleccionar manualmente un radar regional para Sierra Mágina;
2. AEMET compone el mosaico teniendo en cuenta distancia y bloqueo orográfico en zonas de solape;
3. la reflectividad nacional está disponible en EPSG:4326;
4. existe descarga georreferenciada GeoTIFF para análisis GIS;
5. el endpoint OpenData `/api/red/radar/nacional` sirve como puerta de entrada al producto estándar actual.

## Dos clases de asset

### Imagen estándar OpenData

Puede llegar como GIF/PNG u otro formato gráfico.

Uso permitido:
- referencia visual;
- diagnóstico de disponibilidad de la fuente;
- visualización aislada si se conserva atribución.

Uso no permitido:
- medir distancia desde una finca;
- inferir dBZ por coordenada;
- crear alertas espaciales;
- afirmar llegada futura de lluvia.

En código se marca:

```text
analysis_ready = false
```

### GeoTIFF georreferenciado

Uso futuro para:
- reproyección/lectura GIS;
- recorte a Sierra Mágina;
- consulta de píxel o vecindad alrededor de una finca;
- detección de precipitación observada;
- generación de teselas/overlay MapLibre;
- histórico corto de observaciones.

En código se marca:

```text
analysis_ready = true
```

solo cuando el tipo de asset ha sido verificado como GeoTIFF.

## Contrato conceptual

```text
RadarSnapshot
├── source = aemet_national_mosaic
├── product = reflectivity
├── crs = EPSG:4326
├── observed_at
├── fetched_at
├── asset_format
├── analysis_ready
├── storage_key          [fase GeoTIFF]
├── checksum             [fase GeoTIFF]
└── processing_status    [fase GeoTIFF]
```

## Flujo futuro de ingestión

```text
AEMET
  ↓
resolver recurso oficial
  ↓
descargar GeoTIFF
  ↓
validar host + MIME + tamaño + CRS
  ↓
objeto S3-compatible
  ↓
registrar snapshot
  ↓
worker raster
  ├── metadata
  ├── escala dBZ
  ├── recorte Sierra Mágina
  ├── tiles/overlay
  └── índice espacial de precipitación observada
```

El proceso será un job idempotente. Una misma imagen no debe procesarse dos veces.

## Estado de lluvia por finca

Primera versión segura:

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
└── confidence/quality flags
```

Mensajes permitidos:

- `Precipitación detectada sobre la finca.`
- `Precipitación detectada a unos 8 km al oeste.`
- `No se detecta precipitación en el radio analizado.`
- `Radar sin cobertura suficiente o dato no disponible.`

Mensajes no permitidos todavía:

- `Lloverá en 20 minutos.`
- `La tormenta llegará a las 18:40.`
- `Caerán 12 mm en tu finca.`

Estos mensajes requieren nowcast o modelos adicionales.

## Mapa

`MapPlatform` consumirá una capa radar independiente de la geometría de la finca.

Orden conceptual:

```text
base cartográfica
PNOA opcional
SIGPAC/Catastro opcional
fincas
radar reflectividad
alertas/observaciones
```

La capa radar se podrá activar/desactivar y tendrá hora de observación siempre visible.

## Privacidad

El radar es dato público. La asociación `radar -> finca` sí utiliza geometría privada del usuario y solo se calcula dentro del workspace autorizado. Nunca se publicarán coordenadas privadas como parte de una alerta pública.

## Próximos pasos

1. cerrar CI general después de las migraciones territoriales;
2. localizar y documentar el recurso operativo GeoTIFF de AEMET;
3. crear `RadarSnapshot` persistente y almacenamiento de raster;
4. implementar worker de metadata/recorte;
5. generar overlay MapLibre;
6. implementar observación por finca;
7. diseñar alertas de precipitación observada;
8. estudiar nowcast como módulo separado.
