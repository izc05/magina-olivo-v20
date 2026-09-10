# Mágina Olivo V20 · Territorio y tiempo

Estado: arquitectura de implementación en `feat/v20-visual-prototype`.

## Principio

Mágina Olivo separa dos conceptos que en la interfaz pueden parecer iguales pero no lo son técnicamente:

```text
MUNICIPIO OFICIAL
├── código INE
├── código AEMET
├── integraciones administrativas
└── previsión meteorológica municipal

PUEBLO / LOCALIDAD VISIBLE
├── nombre que ve el usuario
├── hero e imágenes
├── noticias y eventos
├── empresas / cooperativas / gastronomía
└── navegación pública
```

Ejemplo importante:

```text
Bedmar y Garcíez · municipio oficial · 23902
├── Bedmar · localidad visible
└── Garcíez · localidad visible
```

No se debe inventar un código AEMET independiente para una localidad que comparte municipio oficial.

## Tablas

### `territory_municipalities`

Fuente de verdad para las integraciones administrativas/meteorológicas:

- `ine_code`
- `aemet_code`
- `name`
- `slug`
- provincia
- centro geográfico futuro
- `active`
- `weather_enabled`

### `territory_places`

Fuente de verdad para la experiencia pública:

- municipio padre
- nombre
- slug
- tipo (`municipal_seat`, `locality`, etc.)
- centro geográfico
- localidad por defecto del municipio
- publicación pública
- `hero_asset_key`

Las fincas pueden enlazar tanto `municipality_id` como `place_id`. Los campos de texto heredados se conservan durante la migración progresiva.

## Catálogo inicial

Municipios oficiales iniciales:

- Albanchez de Mágina · `23001`
- Bedmar y Garcíez · `23902`
- Huelma · `23044`
- Jimena · `23052`
- Jódar · `23053`

Lugares visibles iniciales:

- Albanchez de Mágina
- Bedmar
- Garcíez
- Huelma
- Jimena
- Jódar

Este catálogo no pretende ser todavía el inventario territorial completo de Sierra Mágina; es el núcleo de implementación y se ampliará desde Admin.

## Regla de localización

```text
INICIO / GUÍA
   ↓
ubicación actual del usuario
   ↓
localidad visible más adecuada
   ├── hero
   ├── noticias
   ├── eventos
   ├── empresas
   └── tiempo del municipio oficial asociado

MI CAMPO
   ↓
geometría / municipio de la finca
   ↓
tiempo y alertas asociados a la finca
```

**Inicio sigue al usuario; Mi Campo sigue a las fincas.**

No se utilizará el GPS actual del usuario para decidir el tiempo de una finca cuando el usuario está físicamente en otro lugar.

## Previsión AEMET

Adaptador: `apps/api/src/weather/aemet.ts`.

Semántica V20:

- producto = previsión diaria municipal;
- horizonte máximo normalizado = 7 días;
- probabilidad de precipitación;
- temperatura mínima/máxima;
- viento máximo;
- datos ausentes permanecen `null`;
- nunca se inventa `0` cuando la fuente no aporta valor.

La previsión municipal de AEMET no debe presentarse como microclima exacto de cada finca. La UI mostrará la fuente y el alcance cuando sea relevante.

## Cache compartido

`weather_forecast_cache` evita una petición a AEMET por cada usuario o finca.

```text
Bedmar
     ┐
Garcíez ──> municipio 23902 ──> cache AEMET 23902
     ┘
```

TTL inicial: 30 minutos.

Estados de respuesta:

- `fresh`: cache vigente;
- `refreshed`: se ha consultado AEMET y actualizado cache;
- `stale`: AEMET ha fallado temporalmente y se devuelve el último dato disponible, marcado de forma explícita.

Una respuesta `stale` nunca debe aparentar ser una actualización reciente.

## API

Pública:

```text
GET /api/v1/public/territory/places
GET /api/v1/public/territory/places/:slug
GET /api/v1/public/weather/places/:slug/daily
```

Privada por finca:

```text
GET /api/v1/fields/:fieldId/weather/daily
```

La ruta privada comprueba el workspace de la finca antes de resolver el municipio.

## Forecast, radar y nowcast son productos distintos

La aplicación mantendrá separados:

```text
FORECAST
└── qué condiciones se prevén

RADAR OBSERVATION
└── dónde se está detectando precipitación

NOWCAST
└── extrapolación de corto plazo basada en observaciones
```

V20 no mostrará mensajes como “lloverá en 30 minutos” únicamente a partir de una imagen radar.

Mensajes admitidos inicialmente:

- `Probabilidad de lluvia mañana: 70 % · AEMET`
- `Precipitación detectada cerca de Las Cenillas · radar actualizado 18:10`

Mensaje no admitido sin un motor de nowcast validado:

- `Lloverá en Las Cenillas dentro de 30 minutos`

## Radar: siguiente fase

Antes de implementar radar se debe confirmar:

1. producto AEMET concreto y cobertura útil para Sierra Mágina;
2. georreferenciación / CRS de la imagen;
3. fecha efectiva de observación;
4. estrategia de cache y retención;
5. representación en MapLibre;
6. distancia/dirección respecto a una finca sin convertirla en predicción.

La primera versión de radar será observacional y podrá alimentar alertas del tipo `precipitación detectada cerca`, no estimaciones de llegada.

## Futuro Admin

El Admin deberá gestionar el catálogo territorial sin despliegue:

- activar/desactivar localidad;
- editar hero/galería;
- centro geográfico;
- asociación al municipio oficial;
- código AEMET del municipio;
- orden/destacados;
- fuentes y licencias de imágenes.

Los identificadores oficiales no deben quedar dispersos en componentes del frontend.
