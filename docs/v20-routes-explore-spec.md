# Mágina Olivo V20 — Rutas y senderismo premium

## Visión

Construir una experiencia territorial de rutas, no una simple ficha técnica. Cada ruta debe combinar información oficial/validada, cartografía real, perfil altimétrico, puntos de interés, seguridad, clima, fotografía y vídeo para ayudar al usuario a decidir, entender y recorrer el itinerario.

La capa visual puede ser espectacular; los datos técnicos nunca se inventan.

## Alcance de la rama

Rama: `feat/v20-routes-explore`.

Incluye:
- modelo de rutas;
- track geográfico real;
- perfil de elevación;
- puntos de interés;
- media real/IA con procedencia explícita;
- fuentes oficiales;
- seguridad y estacionalidad;
- base para mapa interactivo y sincronización mapa/desnivel;
- estructura de Admin;
- contratos para integración posterior con clima y empresas.

No incluye todavía:
- navegación GPS turn-by-turn;
- generación automática de tracks;
- recomendaciones meteorológicas inventadas;
- vídeos generados automáticamente en producción;
- dependencia directa del módulo Empresas;
- rediseño visual global.

## Arquitectura pública

- `/explorar/rutas`: descubrimiento, búsqueda y filtros.
- `/rutas/[slug]`: experiencia completa de ruta.
- colecciones futuras: familiares, miradores, agua, patrimonio, olivares, MTB, trail, imprescindibles.

## Estructura de una ficha premium

1. Hero con imagen o vídeo.
2. Resumen técnico: distancia, duración, dificultad, desnivel, tipo y época.
3. Mapa interactivo grande.
4. Perfil de elevación sincronizable con el mapa.
5. Galería fotográfica y vídeo.
6. Descripción editorial.
7. Itinerario por tramos.
8. Puntos de interés y servicios del recorrido.
9. Seguridad, acceso, agua, sombra, cobertura y restricciones.
10. Condiciones meteorológicas procedentes del sistema V20 cuando se integre.
11. Empresas y servicios cercanos mediante integración posterior.
12. Rutas relacionadas.
13. Descarga GPX / fuentes / avisos.

## Tipos de ruta

- hiking
- mtb
- cycling
- trail
- family
- mixed

Una ruta puede tener un tipo principal y etiquetas editoriales complementarias.

## Geometría y track

V20 ya dispone de PostGIS. El track canónico se almacena como `geometry(LineString, 4326)` o `geometry(MultiLineString, 4326)` según el caso y se acompaña de:
- GeoJSON derivable;
- GPX original cuando exista y pueda distribuirse;
- checksum/versionado;
- bounding box;
- distancia calculada/validada;
- perfil de elevación como muestras ordenadas por distancia.

No se debe crear un track a partir de una imagen o de texto descriptivo.

## Perfil de elevación

Cada muestra puede contener:
- distancia acumulada en metros;
- elevación en metros;
- coordenada opcional;
- pendiente derivada opcional.

La UI futura debe permitir que hover/tap sobre el gráfico resalte el punto equivalente del mapa.

## Puntos de interés

Tipos iniciales:
- start
- finish
- viewpoint
- water
- parking
- recreation_area
- heritage
- cave
- bridge
- rest
- photo_spot
- warning
- other

Cada POI guarda posición real, orden/kilómetro aproximado, descripción y opcionalmente media asociada.

## Media

Tipos:
- photo
- hero_image
- real_video
- drone_video
- ai_image
- ai_video
- map_animation
- elevation_animation
- thumbnail

Orígenes:
- `real`
- `official`
- `licensed`
- `ai_generated`

Para IA se almacenan `ai_generated=true`, modelo/proveedor si procede y texto de disclosure. Una imagen o vídeo generado por IA no puede presentarse como prueba visual exacta del estado actual del sendero.

## Vídeo

La plataforma debe poder alojar/referenciar:
- teaser de 10–20 s;
- pieza narrativa de 30–60 s;
- vuelo animado sobre track/mapa;
- animación de perfil altimétrico;
- vídeo real/dron si existe licencia.

Los vídeos son recursos editoriales; no forman parte del contrato técnico de seguridad de la ruta.

## Seguridad

Campos previstos:
- access_notes;
- safety_notes;
- water_notes;
- shade_level;
- mobile_coverage;
- recommended_seasons;
- restrictions;
- official_status;
- last_verified_at.

La interfaz debe distinguir claramente información oficial, editorial y no verificada.

## Fuentes

Candidatas:
- Diputación Provincial de Jaén / portal turístico de Sierra Mágina;
- Junta de Andalucía / Ventana del Visitante;
- ayuntamientos;
- entidades gestoras de senderos;
- tracks propios validados y fuentes con licencia compatible.

Cada fuente guarda URL, identificador externo, licencia/notas de uso, fecha de consulta y payload/metadatos necesarios para auditoría.

## Admin

Secciones previstas:
- Rutas
- Borradores
- Publicadas
- Pendientes de validación
- Tracks
- POI
- Multimedia
- Fuentes
- Colecciones (fase posterior)

Editor de ruta:
- identidad y SEO;
- datos técnicos;
- mapa/track;
- perfil de elevación;
- tramos/itinerario;
- POI;
- media;
- seguridad;
- fuente y validación;
- publicación.

## Estados

Ruta: `draft`, `review`, `published`, `archived`.
Validación: `unverified`, `editorial`, `official`.
Track: `missing`, `uploaded`, `validated`, `rejected`.

Una ruta sin track válido puede existir como borrador, pero no debe publicarse como experiencia navegable.

## Integraciones posteriores

### Clima
Se consumirá el subsistema meteorológico existente, sin duplicar AEMET/radar. La recomendación de horario o aptitud solo se mostrará cuando exista metodología explícita y datos suficientes.

### Empresas
La relación con restaurantes, alojamientos, AOVE y servicios se hará en una rama de integración posterior por consultas espaciales/proximidad. Esta rama no depende del directorio comercial.

## Fases

### Fase 1 — Foundation
- tablas de rutas, tracks, elevación, POI, media y fuentes;
- índices PostGIS;
- estados y validaciones.

### Fase 2 — API + Admin
- CRUD;
- subida/validación GPX;
- generación segura de geometría;
- editor POI/media.

### Fase 3 — Público
- listado y ficha;
- mapa y desnivel;
- galería y vídeo;
- SEO y responsive.

### Fase 4 — Enriquecimiento
- sincronización mapa/desnivel;
- clima contextual;
- colecciones;
- recorridos animados.

### Fase 5 — Explorar Mágina
- empresas cercanas;
- pueblos, patrimonio, eventos y experiencias.

## Criterios de aceptación Foundation

- Reutiliza `territory_municipalities` y `territory_places`.
- Track real en PostGIS con índice espacial.
- Perfil de elevación versionable.
- POI georreferenciados.
- Media con procedencia y disclosure IA.
- Fuentes auditables.
- Ningún dato técnico generado por IA.
- Ninguna dependencia con `main` ni con la rama Empresas.
