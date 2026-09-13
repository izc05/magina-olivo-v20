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

## Visión

Construir una experiencia territorial de rutas, no una simple ficha técnica. Cada ruta combina información validada, cartografía real, perfil altimétrico, puntos de interés, seguridad, fotografía/vídeo y fuentes auditables para ayudar al usuario a entender y recorrer el itinerario.

La capa visual puede evolucionar; los datos técnicos nunca se inventan.

## Alcance de la rama

Rama: `feat/v20-routes-explore`.

Incluye:
- modelo de rutas;
- track geográfico real;
- perfil de elevación;
- puntos de interés;
- media real/IA con procedencia explícita;
- fuentes oficiales/editoriales auditables;
- seguridad y estacionalidad;
- mapa interactivo;
- Admin funcional;
- API pública y Admin;
- importación/validación GPX;
- reglas de publicación en API y base de datos;
- contratos para integración posterior con clima y empresas.

No incluye deliberadamente:
- navegación GPS turn-by-turn;
- generación automática de tracks por IA;
- recomendaciones meteorológicas sin metodología validada;
- vídeos generados automáticamente en producción;
- dependencia directa del módulo Empresas;
- rediseño visual global;
- sincronización avanzada hover/tap entre mapa y gráfica de elevación;
- motor de descarga/distribución GPX pública.

## Arquitectura pública

- `/explorar`: entrada territorial; Rutas figura como módulo disponible.
- `/rutas`: descubrimiento y búsqueda pública sobre rutas publicadas con track validado.
- `/rutas/detalle?slug=<slug>`: ficha completa de ruta.

La ficha utiliza query param de forma intencionada porque `apps/web` funciona con Next.js `output: 'export'`. Así, una ruta creada y publicada después del build puede abrirse desde la misma página estática sin generar una página `[slug]` nueva por cada contenido.

## Estructura de la ficha implementada

1. Resumen editorial.
2. Distancia, duración, desnivel y altitudes.
3. Mapa MapLibre con el track validado.
4. POI georreferenciados sobre el mapa.
5. Perfil de elevación.
6. Acceso y seguridad.
7. Agua y características del recorrido.
8. Fuentes públicas trazables.

La base de datos y API soportan además multimedia y tramos para elevar la composición visual posteriormente sin rehacer el modelo.

## Geometría y GPX

El track canónico se almacena en PostGIS. La importación GPX:

- limita el fichero a 5 MiB;
- limita el track a 100.000 puntos;
- exige al menos dos puntos válidos;
- valida latitud y longitud;
- calcula distancia acumulada con Haversine;
- conserva elevación ausente como `NULL`;
- calcula subida/bajada solo sobre pares de elevación existentes;
- genera `LineString` GeoJSON;
- calcula bounding box;
- calcula checksum SHA-256;
- almacena versiones independientes del track;
- genera hasta aproximadamente 2.000 muestras de perfil para consumo web eficiente.

No se crea un track a partir de imágenes, texto descriptivo o IA.

## Estados e invariantes

Ruta: `draft`, `review`, `published`, `archived`.
Validación editorial: `unverified`, `editorial`, `official`.
Track: `missing`, `uploaded`, `validated`, `rejected`.

Reglas:
- una ruta puede existir sin track mientras sea borrador/revisión;
- la API bloquea `published` si no hay track validado;
- PostgreSQL vuelve a comprobar la misma regla para impedir bypass de API;
- el estado de track de la ruta se deriva de `route_tracks`;
- invalidar/eliminar el único track validado despublica automáticamente la ruta;
- desde Admin solo se mantiene un track validado como canónico por ruta.

## Perfil de elevación

Cada muestra puede contener distancia acumulada, elevación, coordenada y pendiente derivada cuando existen datos suficientes. Si el GPX no aporta elevación, la UI lo indica expresamente y no genera cotas artificiales.

## Puntos de interés

Tipos iniciales:
`start`, `finish`, `viewpoint`, `water`, `parking`, `recreation_area`, `heritage`, `cave`, `bridge`, `rest`, `photo_spot`, `warning`, `other`.

Cada POI guarda posición real, orden/kilómetro aproximado, descripción y nota de seguridad. El Admin permite crearlos y eliminarlos.

## Multimedia y procedencia

Tipos: `photo`, `hero_image`, `real_video`, `drone_video`, `ai_image`, `ai_video`, `map_animation`, `elevation_animation`, `thumbnail`.

Orígenes: `real`, `official`, `licensed`, `ai_generated`.

El API acepta URL externa o ruta interna de la biblioteca. El contenido IA exige que tipo/origen sean coherentes y requiere `ai_disclosure`. Una imagen o vídeo generado por IA nunca se presenta como evidencia visual del estado actual del sendero.

## Fuentes

Cada ruta puede registrar fuentes `official`, `reference`, `track`, `media` o `editorial`, almacenando URL, identificador externo opcional, licencia/notas, fecha de consulta y metadatos para auditoría.

## Admin implementado

Ruta: `/admin/rutas`.

Permite:
- listar y buscar rutas;
- crear/editar ficha;
- pasar a revisión/publicar;
- importar GPX;
- ver métricas derivadas del GPX;
- validar/rechazar tracks versionados;
- crear/eliminar POI;
- crear/eliminar fuentes;
- crear/eliminar multimedia;
- etiquetar procedencia IA;
- bloquear publicación desde la UI mientras no exista track validado.

Las mutaciones relevantes generan entradas en `admin_audit_log`.

## API pública implementada

### `GET /api/v1/public/routes`
Solo devuelve rutas `published` + track `validated`. Soporta filtros por búsqueda, municipio, localidad, tipo, dificultad, circular/familiar y límite.

### `GET /api/v1/public/routes/:slug`
Devuelve ficha, track validado en GeoJSON, perfil de elevación, POI, multimedia, fuentes y tramos.

No existen fixtures ni rutas públicas inventadas cuando la fuente no responde.

## Integraciones posteriores

### Clima
Se consumirá el subsistema meteorológico existente, sin duplicar AEMET/radar. La recomendación de horario o aptitud solo se mostrará cuando exista metodología explícita y datos suficientes.

### Empresas
La relación con restaurantes, alojamientos, AOVE y servicios se hará en una rama de integración posterior mediante consultas espaciales/proximidad. Esta rama no depende de `feat/v20-business-directory`.

## Verificación CI

Workflow dedicado: `.github/workflows/routes-check.yml` — `V20 routes closure check`.

Comprueba:
- instalación frozen-lockfile;
- secuencia de migraciones;
- tests deterministas GPX;
- typecheck API + web;
- build API + web;
- arranque de PostGIS 17;
- aplicación de todas las migraciones del repositorio;
- rechazo de publicación sin track validado;
- publicación correcta tras validar track;
- despublicación automática al rechazar el track validado.

El cierre solo debe promoverse a integración cuando el HEAD final tenga verdes este check y los controles transversales aplicables.

## Handoff

Destino: `integrate/v20-beta-closure`.

No fusionar directamente a `main`.
No mezclar con Empresas en este PR.
El siguiente frente visual puede mejorar hero, galería, interacción mapa/perfil y composición responsive sin cambiar el contrato de datos ni las invariantes ya cerradas.
