# Mágina Olivo V20 — Rutas como plataforma territorial

## Visión

Rutas no es un catálogo de senderos. Es una plataforma territorial que combina track validado, cartografía, desnivel, puntos de interés, seguridad, fuentes, comunidad, fotografía, dispositivos y monetización contextual sin mezclar información oficial con opiniones o publicidad.

Los datos técnicos nunca se inventan. El pago nunca altera seguridad, track, dificultad oficial ni fuentes.

## Arquitectura pública

- `/rutas`: descubrimiento de rutas publicadas.
- `/rutas/detalle?slug=...`: ficha completa compatible con el export estático de V20.
- cada ficha concentra mapa, perfil, datos, fuentes, comunidad, galería, estado reciente, dispositivo y patrocinios relacionados.
- una futura portada `Comunidad` será un agregado de contenido ya moderado; la conversación original vive siempre ligada a su ruta.

## Núcleo técnico

- PostGIS como geometría canónica.
- GPX validado como fuente del track.
- distancia y desnivel calculados solo desde datos presentes.
- perfil de elevación sin fabricar cotas ausentes.
- POI georreferenciados.
- fuentes auditables.
- procedencia de multimedia explícita: real, oficial, licenciada o IA.
- una ruta publicada necesita track validado.
- invalidar el track despublica la ruta automáticamente.

## Comunidad por ruta

Cada ruta puede contener:
- valoración de 1 a 5;
- reseña del usuario;
- fecha de visita;
- dificultad percibida;
- fotografías reales;
- avisos sobre barro, nieve, hielo, bloqueo, daños, inundación, riesgo de incendio u otras condiciones;
- favoritos;
- historial de completadas;
- denuncias de contenido.

La ficha distingue siempre:
1. información oficial;
2. información editorial de Mágina Olivo;
3. información aportada por la comunidad.

Una observación comunitaria nunca se presenta como cierre o restricción oficial.

## Moderación

Todo el contenido comunitario nace en estado `pending`.

Admin dispone de `/admin/rutas/comunidad` con colas separadas para:
- reseñas;
- fotos;
- estado del sendero;
- denuncias.

Acciones:
- aprobar;
- rechazar;
- ocultar;
- resolver o descartar denuncias.

La web pública solo consume aportaciones aprobadas.

## Fotografías de usuarios

Se reutiliza `platform_media_assets` y el StoragePort existente:
- reserva de subida;
- tamaño máximo;
- MIME permitido;
- SHA-256;
- comprobación del objeto almacenado;
- publicación solo tras completar subida y moderación.

Las fotos pueden guardar fecha, pie y coordenada opcional para una futura capa fotográfica sobre el mapa.

## Dispositivos

Endpoint público:
- `GET /api/v1/public/routes/:slug/gpx`

Genera GPX 1.1 desde la geometría PostGIS validada y preserva segmentos de MultiLineString. No inventa elevación.

Esto permite un flujo universal hacia aplicaciones y dispositivos compatibles con GPX. Integraciones directas con proveedores como Garmin deben usar sus programas/API oficiales y OAuth cuando exista autorización.

Las completadas ya admiten procedencia:
- manual;
- recorded_gpx;
- garmin;
- suunto;
- coros;
- apple_watch;
- other.

## Monetización

El patrocinio es contextual y visible, no publicidad encubierta.

Ubicaciones soportadas:
- `route_hero`;
- `route_sidebar`;
- `after_map`;
- `nearby_services`;
- `route_download`;
- `collection`.

Modelos comerciales:
- cuota fija;
- CPM;
- CPC;
- afiliación.

Cada campaña puede definir:
- ruta concreta o ámbito global;
- patrocinador;
- logo y web;
- titular y descripción;
- CTA;
- código promocional;
- prioridad;
- periodo de actividad;
- precio y moneda;
- etiqueta de disclosure, por defecto `Patrocinado`.

Eventos medibles:
- impresión;
- clic;
- visita web;
- llamada;
- WhatsApp;
- indicaciones;
- reserva;
- conversión de afiliación (servidor/integración futura).

Admin dispone de `/admin/rutas/patrocinios` para gestionar campañas y consultar impresiones, clics y acciones.

## Seguridad comercial

El patrocinio nunca puede:
- modificar track o GPX;
- modificar desnivel/distancia;
- esconder restricciones;
- desplazar una fuente oficial;
- convertir una observación comunitaria en información oficial.

Los espacios pagados deben renderizar disclosure explícito.

## Evolución recomendada

Siguientes capas compatibles con este contrato:
- mapa de fotografías geolocalizadas;
- sincronización mapa ↔ perfil de elevación;
- colecciones y retos de rutas;
- rutas guardadas/planificadas/completadas en el perfil;
- navegación offline PWA;
- seguimiento en vivo compartible;
- QR en inicio de senderos;
- notificaciones cuando cambie el estado oficial de una ruta;
- empresas cercanas mediante integración espacial con el directorio comercial;
- integración oficial Garmin Courses cuando se apruebe acceso;
- reputación de colaboradores/guías locales;
- recorridos 3D/flyover como capa editorial, nunca como sustituto del track real.

## Gate de calidad

`V20 routes closure check` cubre:
- secuencia de migraciones;
- parser GPX;
- typecheck API + web;
- build API + web;
- PostGIS 17;
- todas las migraciones;
- invariante publicación/track;
- defaults de moderación de comunidad;
- disclosure obligatorio de patrocinio.

## Límites deliberados

No se implementa en esta fase:
- navegación turn-by-turn propia;
- generación de tracks por IA;
- recomendaciones meteorológicas inventadas;
- integración directa con la rama Empresas;
- merge directo a `main`.
