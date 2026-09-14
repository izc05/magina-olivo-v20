# Mágina Olivo V20 — Directorio de empresas

## Objetivo

Crear un directorio territorial de empresas y servicios de Sierra Mágina que sea útil aunque ninguna empresa pague, y que pueda evolucionar a monetización transparente mediante fichas destacadas, campañas y patrocinio contextual.

La presencia básica y la relevancia orgánica no dependen del pago. Toda promoción comercial activa se identifica públicamente como `Patrocinado` o `Destacado`.

## Rama y estado

Rama: `feat/v20-business-directory`.
PR: `#80`, contra `integrate/v20-beta-closure`.

Estado funcional de esta rama:
- Foundation de datos: completada.
- API pública: completada.
- API Admin: completada.
- búsqueda y filtros geográficos: completados.
- ficha pública: completada.
- mapa interactivo: completado.
- reclamación y verificación: completadas.
- administración visual de empresas: completada.
- administración visual de categorías: completada.
- fuentes y multimedia por empresa: completadas.
- preparación comercial y disclosure: completados.
- check CI específico con PostGIS: incluido.

Queda deliberadamente fuera de esta rama:
- cobros reales;
- facturación de campañas;
- ranking oculto por pago, que está prohibido por diseño;
- importaciones masivas automáticas de fuentes externas;
- integración directa con Rutas;
- fusión a `main`;
- rediseño visual global de toda V20.

## Arquitectura pública implementada

### Explorar empresas

Ruta: `/explorar/empresas`.

Incluye:
- búsqueda textual;
- categorías;
- filtro por municipio a partir de los resultados;
- geolocalización opcional `Cerca de mí`;
- consulta por radio usando PostGIS;
- ordenación que conserva el disclosure comercial;
- tarjetas con estado de verificación;
- mapa MapLibre sobre cartografía OpenStreetMap;
- estados loading, vacío y error sin inventar contenido.

La tarjeta `Empresas y servicios` de `/explorar` enlaza ya con este directorio estructurado. El módulo histórico `/servicios` se conserva como superficie editorial heredada y no es la fuente canónica del nuevo directorio.

### Ficha pública

Por la configuración actual de exportación estática de Next.js, la ficha se expone como:

`/empresas?slug=<slug>`

No se usa todavía `/empresas/[slug]`, porque esa ruta dinámica requeriría conocer los slugs en build o cambiar la estrategia de runtime/exportación.

La ficha puede mostrar, cuando existen datos reales:
- nombre y marca;
- categorías;
- municipio/localidad;
- descripción corta y ampliada;
- dirección y coordenadas;
- teléfono, WhatsApp, email y web;
- horario estructurado;
- logo, portada y galería;
- procedencia de medios y disclosure de IA cuando corresponda;
- fuentes y estado de sincronización;
- estado de verificación;
- etiqueta comercial `Patrocinado` / `Destacado`;
- mapa de ubicación;
- CTA para reclamar la ficha.

No se inventan horarios, servicios, imágenes ni datos de contacto ausentes.

## API pública implementada

- `GET /api/v1/public/business-categories`
- `GET /api/v1/public/businesses`
- `GET /api/v1/public/businesses/:slug`
- `POST /api/v1/public/businesses/:slug/claims`

Filtros soportados por listado:
- `q`
- `municipalityId`
- `placeId`
- `category`
- `featured`
- `sponsored`
- `lat` + `lng`
- `radiusKm`
- `limit`
- `offset`

Las búsquedas por proximidad usan PostGIS y nunca calculan una distancia ficticia en cliente.

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

Las categorías son jerárquicas y una empresa puede pertenecer a varias, con una categoría principal opcional.

## Modelo de datos

La migración `0070_business_directory_foundation.sql` crea el subsistema canónico:
- `business_categories`
- `businesses`
- `business_category_links`
- `business_media`
- `business_sources`
- `business_claims`

Las empresas reutilizan `territory_municipalities` y `territory_places`. La ubicación usa `geometry(Point, 4326)` e índice GIST.

## Modelo comercial preparado, no cobrado

Planes:
- `free`
- `featured`
- `premium`
- `sponsor`

Campos:
- `commercial_plan`
- `featured`
- `sponsored`
- `priority`
- `campaign_start`
- `campaign_end`

Reglas implementadas:
1. una ventaja comercial activa nunca se presenta como orgánica;
2. `sponsor` / `sponsored` se muestran como `Patrocinado`;
3. `featured` se muestra como `Destacado`;
4. inicio y fin de campaña limitan la ventaja de ordenación y el distintivo activo;
5. la verificación de la empresa es independiente del plan comercial;
6. los cambios comerciales desde Admin quedan auditados.

No hay pago real ni facturación en esta rama.

## Fuentes, medios y trazabilidad

Fuentes candidatas futuras:
- OpenRTA / Registro de Turismo de Andalucía;
- Diputación Provincial de Jaén / Jaén Paraíso Interior;
- ayuntamientos y organismos públicos;
- alta directa por la empresa;
- edición editorial propia.

Los datos externos se registran en `business_sources` con origen, identificador externo cuando existe, URL, payload, fecha y estado de sincronización.

Los medios viven en `business_media` y permiten distinguir contenido:
- propio;
- oficial;
- licenciado;
- referencia externa;
- generado con IA.

Si un medio está marcado como generado con IA, el Admin exige disclosure asociado. La UI pública muestra ese disclosure cuando procede.

## Reclamación y verificación

Flujo implementado:

`publicada sin reclamar -> solicitud -> revisión Admin -> aprobada/verificada | necesita información | rechazada | cancelada`

La API evita una segunda reclamación activa de la misma dirección de correo para la misma empresa.

La aprobación en Admin cambia la ficha a `verified` en la misma transacción. Pagar o tener un plan comercial no verifica una empresa.

## Admin implementado

### `/admin/empresas`

Incluye:
- métricas de total, publicadas, verificadas, comerciales y reclamaciones pendientes;
- alta y edición;
- publicación, borrador y archivo;
- asignación territorial;
- coordenadas;
- contacto;
- categorías múltiples y categoría principal;
- verificación manual;
- plan comercial;
- destacado/patrocinado;
- prioridad;
- vigencia de campaña;
- alta de multimedia;
- alta/actualización de fuentes;
- revisión de reclamaciones;
- enlace a vista pública.

### `/admin/empresas/categorias`

Incluye:
- alta de categorías;
- edición de nombre, slug y descripción;
- jerarquía padre/hija;
- orden;
- activación/desactivación;
- contador de empresas vinculadas.

Las operaciones Admin pasan por control de acceso de plataforma y generan auditoría donde corresponde.

## Privacidad y calidad

- No publicar datos personales no destinados a contacto empresarial.
- No inferir horarios ni servicios.
- Mantener URL de procedencia cuando exista.
- No copiar imágenes externas sin licencia/permiso compatible.
- Admitir fichas incompletas sin rellenarlas con datos ficticios.
- No confundir verificación editorial con patrocinio.
- No solicitar geolocalización del visitante hasta que pulse `Cerca de mí`.

## Calidad y pruebas

La rama incluye `.github/workflows/business-directory-check.yml`, que valida específicamente:
- instalación reproducible;
- typecheck de API y web;
- build del API;
- PostgreSQL 17 + PostGIS;
- aplicación completa de migraciones;
- fixture de empresa publicada y geolocalizada;
- categorías públicas;
- búsqueda pública;
- filtro de radio/distancia PostGIS;
- disclosure `Destacado`;
- ficha individual;
- estado verificado;
- creación y persistencia de una reclamación.

Este check debe quedar verde antes del handoff de la rama.

## Integraciones posteriores

La unión con Rutas debe realizarse en una rama de integración posterior, sin acoplar `feat/v20-business-directory` con `feat/v20-routes-explore`. La relación futura puede cubrir:
- empresas cerca de una ruta;
- comer/dormir/servicios próximos;
- experiencias y turismo activo;
- patrocinio contextual claramente identificado.

## Criterios de aceptación

- Ninguna modificación de `main`.
- Modelo territorial reutilizado, no duplicado.
- Coordenadas geográficas nativas PostGIS.
- Empresa con múltiples categorías.
- Fuente externa separada del contenido editorial.
- Flujo de reclamación operativo.
- Admin operativo para empresas, categorías y reclamaciones.
- Listado, ficha y mapa públicos operativos.
- Monetización preparada pero sin cobros reales.
- Ninguna promoción puede representarse como orgánica sin disclosure.
- CI específico de Empresas verde antes de considerar el módulo cerrado.
