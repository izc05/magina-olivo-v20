# Mágina Olivo V20 — Rutas y senderismo premium

## Estado de implementación

**Cierre funcional alcanzado en `feat/v20-routes-explore`.**

La rama contiene la cadena completa necesaria para gestionar y publicar rutas verificadas:

- modelo PostGIS de rutas, tracks, elevación, POI, multimedia, fuentes y tramos;
- importación GPX con validación de tamaño, coordenadas y longitud mínima;
- distancia calculada sobre coordenadas reales mediante Haversine;
- desnivel positivo/negativo y altitudes solo cuando el GPX aporta elevación;
- checksum SHA-256, versionado, bbox y geometría `LineString` canónica;
- validación/rechazo editorial de tracks;
- invariante de base de datos que impide publicar una ruta sin track validado;
- invalidación automática: si el track publicado deja de ser válido, la ruta vuelve a `review` y pierde `published_at`;
- API pública de listado y ficha;
- API Admin de ficha, GPX, validación, POI, fuentes y multimedia;
- panel `/admin/rutas` para operar el módulo sin SQL manual;
- superficie pública `/rutas`;
- ficha pública compatible con export estático en `/rutas/detalle?slug=<slug>`;
- mapa MapLibre interactivo sobre el track validado y POI reales;
- perfil de elevación derivado de las cotas del GPX;
- integración de Rutas en `/explorar`;
- check CI específico con PostGIS 17, typecheck, build, todas las migraciones y smoke de publicación/invalidation.

## Alcance cerrado

Incluye modelo y geometría real, GPX, elevación, POI, media con procedencia, fuentes auditables, seguridad, mapa interactivo, Admin, APIs, reglas de publicación y CI específico.

No incluye deliberadamente navegación GPS turn-by-turn, generación de tracks por IA, recomendaciones meteorológicas sin metodología, dependencia directa de Empresas, rediseño visual global, sincronización avanzada mapa/perfil ni distribución pública GPX.

## Arquitectura pública

- `/explorar`: entrada territorial; Rutas figura como módulo disponible.
- `/rutas`: descubrimiento y búsqueda sobre rutas publicadas con track validado.
- `/rutas/detalle?slug=<slug>`: ficha completa compatible con Next.js `output: 'export'` y contenido publicado después del build.

## Contrato GPX

La importación limita el fichero a 5 MiB y 100.000 puntos, exige al menos dos coordenadas válidas, calcula distancia con Haversine, conserva elevación ausente como `NULL`, calcula desnivel solo con cotas existentes, genera `LineString`, bbox y SHA-256, versiona tracks y produce hasta aproximadamente 2.000 muestras para el perfil web.

No se crea un track a partir de imágenes, texto o IA.

## Invariantes

Ruta: `draft`, `review`, `published`, `archived`.
Track: `missing`, `uploaded`, `validated`, `rejected`.

- La API bloquea publicación sin track validado.
- PostgreSQL vuelve a comprobar la regla.
- El estado de ruta se deriva del track persistido.
- Invalidar/eliminar el único track validado despublica automáticamente la ruta.
- Desde Admin se mantiene un track validado canónico por ruta.

## Contenido editorial

POI iniciales: `start`, `finish`, `viewpoint`, `water`, `parking`, `recreation_area`, `heritage`, `cave`, `bridge`, `rest`, `photo_spot`, `warning`, `other`.

Multimedia: `photo`, `hero_image`, `real_video`, `drone_video`, `ai_image`, `ai_video`, `map_animation`, `elevation_animation`, `thumbnail`.

Orígenes: `real`, `official`, `licensed`, `ai_generated`. El contenido IA exige `ai_disclosure` y nunca se presenta como prueba visual del estado real del sendero.

Fuentes: `official`, `reference`, `track`, `media`, `editorial`, con URL y metadatos auditables.

## Admin

`/admin/rutas` permite listar/buscar, crear/editar, importar GPX, inspeccionar métricas, validar/rechazar tracks, gestionar POI/fuentes/multimedia y publicar solo cuando existe un track validado. Las mutaciones relevantes se auditan.

## API pública

`GET /api/v1/public/routes` devuelve únicamente rutas `published` + track `validated`, con filtros.

`GET /api/v1/public/routes/:slug` devuelve ficha, GeoJSON, elevación, POI, multimedia, fuentes y tramos.

No se devuelven rutas ficticias ante fallos de fuente.

## CI

`.github/workflows/routes-check.yml` — `V20 routes closure check` verifica frozen-lockfile, migraciones, tests GPX, typecheck API/web, build API/web, PostGIS 17, aplicación de todas las migraciones y smoke de invariantes de publicación/invalidación.

## Handoff

Destino: `integrate/v20-beta-closure`.

No fusionar directamente a `main`.
No mezclar con Empresas en este PR.
Cualquier evolución visual posterior debe respetar este contrato de datos e invariantes.
