# Mágina Olivo V20 · Rutas

## Objetivo
Rutas es una plataforma territorial para descubrir, preparar y disfrutar recorridos de Sierra Mágina con datos técnicos verificables, comunidad moderada, soporte para dispositivos y monetización contextual sin contaminar la información de seguridad.

## Contrato técnico de ruta
- Los tracks públicos deben proceder de geometría real validada.
- Una ruta no puede publicarse sin `track_status = validated`.
- Distancia, desnivel y altitud se calculan desde el track y las cotas disponibles; nunca se inventan.
- PostGIS mantiene la geometría canónica EPSG:4326.
- GPX es el formato universal de salida para dispositivos y apps compatibles.

## Capas de información
La ficha debe distinguir siempre:
1. Información oficial y sus fuentes.
2. Información editorial de Mágina Olivo.
3. Comunidad moderada.
4. Contenido patrocinado, siempre etiquetado.

Una observación comunitaria no equivale a una restricción o cierre oficial.

## Comunidad por ruta
Cada aportación pertenece a una ruta concreta. No existe un muro global que pierda el contexto del recorrido.

Incluye:
- valoraciones y reseñas;
- fecha de visita y dificultad percibida;
- fotos de usuarios;
- condiciones recientes del sendero;
- favoritos y completadas;
- denuncias;
- moderación previa a publicación.

Admin agrega todas las colas en `/admin/rutas/comunidad`, pero el contenido público se consume dentro de su ruta.

## Mapa fotográfico comunitario
Las fotografías pueden incluir ubicación de forma voluntaria.

Reglas:
- la aplicación solo solicita geolocalización cuando el usuario pulsa expresamente `Situar foto en el mapa`;
- denegar la geolocalización no impide subir la fotografía;
- no se inventan coordenadas ni se deducen silenciosamente;
- la posición se almacena como `geometry(Point, 4326)` asociada a `route_review_media`;
- una fotografía y su posición solo aparecen públicamente tras aprobarse tanto la reseña como la fotografía;
- el mapa muestra miniaturas únicamente de fotos aprobadas y geolocalizadas;
- al pulsar una miniatura se abre una ficha contextual sobre el mapa;
- al pulsar una foto geolocalizada en la galería, el mapa centra el recorrido en ese punto;
- una foto sin posición sigue siendo válida y aparece en la galería aprobada sin marcador cartográfico.

La ubicación comunitaria es orientativa y nunca sustituye un POI editorial u oficial.

## Dispositivos
- Exportación GPX 1.1 desde la geometría PostGIS validada.
- Compatible con el flujo de importación de aplicaciones y GPS que aceptan GPX.
- El modelo de completadas contempla `manual`, `recorded_gpx`, `garmin`, `suunto`, `coros`, `apple_watch` y `other`.
- Integraciones directas con proveedores deben utilizar sus APIs/OAuth oficiales.

## Monetización
Las campañas pueden ser globales o estar asociadas a una ruta.

Placements disponibles:
- `route_hero`
- `route_sidebar`
- `after_map`
- `nearby_services`
- `route_download`
- `collection`

Modelos comerciales:
- cuota fija;
- CPM;
- CPC;
- afiliación.

Métricas previstas:
- impresión;
- clic;
- visita web;
- llamada;
- WhatsApp;
- indicaciones;
- reserva;
- conversión de afiliación.

La publicidad utiliza disclosure explícito (`Patrocinado` por defecto). El pago nunca puede modificar track, distancia, desnivel, dificultad, seguridad, restricciones ni fuentes oficiales.

## QA mínimo
`V20 routes closure check` debe permanecer verde para:
- parser GPX;
- TypeScript API/web;
- build API/web;
- todas las migraciones PostGIS;
- invariante de publicación;
- comunidad pendiente de moderación por defecto;
- patrocinios con disclosure;
- superficies públicas de mapa, comunidad y descarga GPX compilables.

## Cierre del objetivo mapa fotográfico
El código funcional del mapa fotográfico quedó validado en el commit `16f0ce128dffec0ab8b75e369669644e1d359efe` con:
- `V20 routes closure check` verde;
- `V20 full candidate check` verde;
- `V20 platform admin check` verde;
- TypeScript y build web/API verdes;
- todas las migraciones y smokes de Rutas verdes.

Los commits posteriores a ese SHA son exclusivamente documentación de este cierre.

El handoff de esta rama es `integrate/v20-beta-closure`. No se fusiona directamente a `main`.
