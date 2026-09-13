# Mágina Olivo V20 — Directorio de empresas

## Objetivo

Crear un directorio territorial de empresas y servicios de Sierra Mágina que sea útil aunque ninguna empresa pague, y que pueda evolucionar a monetización transparente mediante fichas destacadas, campañas y patrocinio contextual.

La presencia básica y la relevancia orgánica no deben depender del pago. Toda promoción comercial debe identificarse como `Patrocinado` o `Destacado`.

## Alcance de la rama

Rama: `feat/v20-business-directory`.

Incluye:
- modelo de datos del directorio;
- categorías jerárquicas y multietiqueta;
- empresa, ubicación, contacto y medios;
- fuentes externas y trazabilidad de importación;
- reclamación/verificación de fichas;
- preparación de planes comerciales futuros;
- contratos de administración y publicación;
- especificación de páginas públicas y Admin.

No incluye todavía:
- cobros reales;
- facturación;
- ranking oculto por pago;
- importaciones masivas automáticas;
- integración directa con Rutas;
- rediseño visual global.

## Arquitectura de información

### Público

- `/explorar/empresas`: buscador, categorías, municipio/localidad, mapa y filtros.
- `/empresas/[slug]`: ficha completa de empresa.
- colecciones contextuales futuras: comer y beber, alojamientos, AOVE, agricultura, servicios, turismo activo.

### Ficha pública

Debe poder mostrar:
- nombre y marca;
- categoría/s;
- municipio y localidad;
- descripción corta y ampliada;
- dirección y coordenadas;
- teléfono, WhatsApp, email y web;
- redes sociales;
- horario estructurado;
- logo, portada y galería;
- servicios/productos;
- procedencia y fecha de actualización cuando sea relevante;
- estado de verificación;
- CTA para reclamar la ficha;
- distintivo `Patrocinado` cuando proceda.

## Categorías iniciales

- AOVE y productores
- Cooperativas y almazaras
- Agricultura y servicios agrícolas
- Maquinaria y talleres
- Riego, viveros, fertilización y suministros
- Restaurantes, bares y cafeterías
- Alojamientos y casas rurales
- Turismo activo y guías
- Comercio local
- Construcción y mantenimiento
- Servicios profesionales

Las categorías son jerárquicas y una empresa puede pertenecer a varias.

## Modelo comercial preparado, no activado

Planes:
- `free`
- `featured`
- `premium`
- `sponsor`

Campos preparados:
- `commercial_plan`
- `featured`
- `sponsored`
- `priority`
- `campaign_start`
- `campaign_end`

Reglas:
1. `priority` nunca debe ocultar que una posición es patrocinada.
2. La ordenación orgánica debe seguir funcionando sin campañas.
3. Una campaña caducada no debe seguir obteniendo ventajas.
4. El Admin debe poder ver historial/auditoría de cambios comerciales.

## Fuentes y sincronización

Fuentes candidatas:
- OpenRTA / Registro de Turismo de Andalucía;
- Diputación Provincial de Jaén / Jaén Paraíso Interior;
- ayuntamientos y organismos públicos;
- alta directa por la empresa;
- edición editorial propia.

Los datos externos se almacenan en `business_sources`; no deben sobrescribir silenciosamente datos revisados manualmente. Cada registro conserva `source_name`, `external_id`, URL de origen, payload original y fecha de sincronización.

## Verificación y reclamación

Flujo:

`importada -> sin reclamar -> solicitud -> revisión Admin -> verificada/rechazada`

Una reclamación guarda identidad del solicitante, canal de contacto, evidencia, notas internas y resolución.

## Admin

Secciones previstas:
- Todas
- Pendientes de revisión
- Verificadas
- Destacadas
- Patrocinadas
- Reclamaciones
- Categorías
- Fuentes / sincronización

Operaciones:
- alta, edición, publicación y archivo;
- asignación de categorías;
- moderación de medios;
- verificación;
- revisión de fuente externa;
- activación manual de campaña futura;
- auditoría.

## Privacidad y calidad

- No publicar datos personales no destinados a contacto empresarial.
- No inferir horarios ni servicios.
- Mantener URL de procedencia de datos públicos.
- Las imágenes importadas requieren licencia/permiso compatible; de lo contrario solo se conserva la referencia, no una copia.
- Las fichas deben admitir estados incompletos sin inventar contenido.

## Fases

### Fase 1 — Foundation
- tablas y contratos de datos;
- categorías;
- trazabilidad de fuentes;
- reclamaciones;
- tipos/servicios básicos.

### Fase 2 — API + Admin
- CRUD Admin;
- filtros;
- revisión y verificación;
- importación controlada.

### Fase 3 — Público
- listado, ficha, mapa, SEO y datos estructurados;
- estados vacío/error/loading.

### Fase 4 — Monetización
- campañas, métricas y planes;
- pagos solo cuando el negocio lo requiera.

### Fase 5 — Integración Explorar
- empresas cercanas a rutas, pueblos, eventos y patrimonio mediante una rama de integración posterior.

## Criterios de aceptación Foundation

- Ninguna dependencia con `main`.
- El modelo reutiliza `territory_municipalities` y `territory_places`.
- Coordenadas geográficas nativas PostGIS.
- Empresa con múltiples categorías.
- Fuente externa separada de contenido editorial.
- Flujo de reclamación modelado.
- Monetización preparada pero inactiva.
- Ninguna promoción puede representarse como orgánica sin disclosure.
