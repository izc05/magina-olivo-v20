# V20 · Navegación Admin municipal

## Objetivo

Mantener un municipio activo mientras el administrador recorre todas las herramientas de Ayuntamientos.

El contexto se expresa de forma canónica en la URL:

`?municipio=<slug>`

Ejemplo:

`/admin/ayuntamientos/contenido?municipio=bedmar-y-garciez`

## Superficies conectadas

- `/admin/ayuntamientos` — ficha institucional.
- `/admin/ayuntamientos/contenido` — creación/edición de contenido municipal.
- `/admin/ayuntamientos/editorial` — hero, perfil e imprescindibles.
- `/admin/ayuntamientos/patrimonio` — clasificación e importador oficial.
- `/admin/ayuntamientos/actualidad` — noticias y eventos.
- `/admin/ayuntamientos/cobertura` — cobertura operativa.
- `/ayuntamientos/[slug]` — vista pública.

## Comportamiento

1. Al entrar con `?municipio=slug`, cada pantalla restaura ese municipio si existe y está activo.
2. Si se cambia el municipio en cualquier selector contextual, la URL se actualiza con `history.replaceState`.
3. Todos los enlaces de la barra municipal conservan el slug actual.
4. Los enlaces antiguos basados en `#slug` siguen funcionando como fallback; al cargar se normalizan al parámetro canónico.
5. Patrimonio y Cobertura enfocan su lista de trabajo al municipio actual.
6. Actualidad muestra los contenidos ya vinculados al municipio actual y también los de ámbito general, para poder asignarlos sin abandonar el contexto.

## Barra de trabajo

La barra compartida `MunicipalityAdminNav` expone:

`Ficha · Contenido · Portada · Patrimonio · Actualidad · Cobertura · Ver público`

Se reutiliza en todas las superficies municipales para evitar navegación divergente.

## Arquitectura

No se crean tablas, endpoints ni estado global persistente nuevo. El contexto de navegación es URL-driven y usa el catálogo territorial canónico para resolver identidad, nombre y slug.

## Compatibilidad

- No modifica el modelo `territory_municipalities`.
- No modifica `cms_entries`.
- No modifica Rutas, Empresas, GIS ni Clima.
- No requiere migración.

## Contrato

`scripts/check-municipality-admin-navigation.mjs` protege:

- clave canónica `municipio`;
- compatibilidad con hash legacy;
- barra compartida completa;
- persistencia de contexto en las seis pantallas;
- enfoque por municipio en Patrimonio, Actualidad y Cobertura.
