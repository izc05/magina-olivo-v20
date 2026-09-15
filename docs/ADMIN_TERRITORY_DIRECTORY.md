# Mágina Olivo V20 — Administración de territorio y directorio

## Objetivo

`/admin/territorio` administra la presencia pública de pueblos, cooperativas/almazaras y servicios locales sin duplicar el catálogo territorial ya existente en V20.

La regla es:

- **territorio canónico** → `territory_municipalities` + `territory_places`;
- **contenido editorial/comercial** → `cms_entries` enlazado por `content_json.territory_place_id`.

Así Catastro, clima, fincas y futuras integraciones pueden seguir usando el mismo lugar canónico mientras el equipo editorial amplía la información pública sin modificar GIS.

## Catálogo canónico

La migración histórica `0013_territory_catalog.sql` ya contiene los municipios y localidades iniciales de Mágina, incluidos Albanchez de Mágina, Bedmar/Garcíez, Huelma, Jimena y Jódar.

El Admin muestra:

- municipio oficial e INE;
- localidad visible;
- tipo (`municipal_seat`, `locality`, `hamlet`, `other`);
- estado público;
- número de fincas activas vinculadas;
- número de fichas editoriales vinculadas.

Un rol `editor` o superior puede modificar únicamente:

- `public_enabled`;
- `kind`.

No se modifican desde este panel:

- INE/AEMET;
- geometría/centro GIS;
- configuración meteorológica;
- relación estructural municipio/localidad.

Esto evita que el backoffice editorial pueda romper dependencias del mapa, clima o las fincas.

## Fichas públicas

Los tipos CMS `place`, `mill` y `directory` se vinculan a una localidad canónica y admiten:

- nombre y slug;
- resumen y descripción extensa;
- teléfono y email;
- dirección;
- servicios (lista estructurada);
- horario/atención;
- información de campaña para cooperativas/almazaras;
- Instagram y Facebook;
- coordenadas editoriales opcionales;
- imagen desde la biblioteca multimedia;
- web/enlace principal;
- texto de CTA;
- estado borrador/publicado/archivado;
- destacado y orden.

Para una ficha de tipo `place`, el editor reutiliza la ficha existente del mismo `territory_place_id` en vez de crear otra ficha paralela.

## API privada

- `GET /api/v1/admin/territory/catalog`
  - `support` o superior;
  - devuelve municipios y localidades, incluidas las ocultas.
- `PATCH /api/v1/admin/territory/places/:id`
  - `editor` o superior;
  - permite `public_enabled` y `kind`;
  - registra `territory.place_changed` en `admin_audit_log`.

La API pública de territorio sigue siendo la existente:

- `GET /api/v1/public/territory/places`
- `GET /api/v1/public/territory/places/:slug`

Solo expone lugares con `public_enabled=true` pertenecientes a municipios activos.

## Integración con Explorar

`/explorar` separa el contenido en:

- **Pueblos y servicios de Mágina** — fichas `place`, `mill` y `directory`;
- **Publicado en Mágina** — noticias, eventos y páginas editoriales.

Las fichas territoriales pueden mostrar imagen, localidad, teléfono y primeros servicios. Los enlaces y recursos administrados se validan antes de renderizarse.

## Validación

`admin-territory-smoke` comprueba con PostgreSQL/PostGIS real:

1. el catálogo canónico está disponible para un administrador;
2. Huelma existe como lugar canónico sembrado;
3. un propietario de workspace sin rol de plataforma obtiene `403`;
4. ocultar Huelma desde Admin la retira de la API pública;
5. restaurarla la devuelve a la API pública;
6. un `editor` de plataforma puede realizar cambios permitidos;
7. el cambio queda registrado en auditoría.

El workflow `V20 platform admin check` ejecuta este smoke además del smoke general de Administración.
