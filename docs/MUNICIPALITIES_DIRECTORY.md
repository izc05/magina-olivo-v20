# Mágina Olivo V20 — Directorio y hub municipal

## Alcance

Este módulo mantiene el directorio institucional y la experiencia pública de los 16 municipios incluidos en el ámbito territorial de Sierra Mágina usado por V20:

1. Albanchez de Mágina
2. Bedmar y Garcíez
3. Bélmez de la Moraleda
4. Cabra del Santo Cristo
5. Cambil
6. Campillo de Arenas
7. Cárcheles
8. La Guardia de Jaén
9. Huelma
10. Jimena
11. Jódar
12. Larva
13. Mancha Real
14. Noalejo
15. Pegalajar
16. Torres

## Modelo y fuentes de verdad

`territory_municipalities` sigue siendo la fuente canónica para identidad municipal, INE, AEMET, GIS, clima y relación con fincas.

`territory_municipality_directory` es una relación 1:1 con la entidad canónica y contiene exclusivamente información institucional/publicable:

- web oficial;
- sede electrónica;
- portal de transparencia;
- web turística;
- teléfono;
- email;
- dirección y código postal;
- URL fuente utilizada para verificar los datos;
- fecha de última verificación;
- visibilidad pública.

`territory_places` conserva las localidades y núcleos canónicos del municipio.

`cms_entries` sigue siendo la única fuente editorial/comercial. El hub municipal no duplica cooperativas, empresas, noticias ni eventos: consume entradas publicadas que declaren explícitamente `content_json.municipality_id`.

Esta separación impide que una edición de contacto pueda romper GIS, Catastro, SIGPAC, AEMET o la vinculación de fincas, y evita crear un segundo directorio editorial paralelo.

## Datos iniciales

La migración `0061_municipalities_directory.sql` completa el catálogo hasta 16 municipios, crea las cabeceras municipales que falten y siembra una ficha institucional con web oficial para cada ayuntamiento. Los campos de contacto solo se rellenan cuando hay una referencia institucional suficientemente clara; de lo contrario permanecen nulos para no inventar datos.

Los registros iniciales quedan fechados como verificados el `2026-09-13` y conservan `source_url`.

## API pública

- `GET /api/v1/public/territory/municipalities`
- `GET /api/v1/public/territory/municipalities/:slug`

Solo devuelven municipios activos con ficha institucional visible. La colección incluye localidades públicas y conteos de contenido relacionado. El detalle incluye además `related_content` para las entradas publicadas de tipo:

- `place` — perfil editorial del pueblo/localidad;
- `mill` — cooperativas y almazaras;
- `directory` — empresas y servicios;
- `news` — actualidad vinculada explícitamente;
- `event` — eventos vinculados explícitamente.

El agregado respeta `status = published`, `starts_at` y `ends_at`. No se asigna contenido por coincidencia de nombres, texto libre o proximidad inferida.

## Web pública

- `/ayuntamientos` — buscador y listado de los ayuntamientos.
- `/ayuntamientos/[slug]` — hub municipal individual.
- `/explorar` — incluye acceso directo al directorio.

Cada hub municipal contiene:

1. cabecera territorial con resumen y métricas locales;
2. perfil editorial y localidades/núcleos publicados;
3. información oficial del Ayuntamiento;
4. olivar y economía local con cooperativas, almazaras, empresas y servicios;
5. actualidad local con noticias y eventos relacionados explícitamente;
6. trazabilidad de la fuente institucional y fecha de verificación.

Los estados vacíos son explícitos. Si no existe contenido publicado para una sección, la interfaz lo comunica y no genera ejemplos ficticios.

## Administración

- `/admin/ayuntamientos` — editor dedicado de información institucional.
- `/admin/territorio` — fuente editorial para fichas de pueblo, cooperativas/almazaras y empresas/servicios.
- `GET /api/v1/admin/territory/catalog` — incluye el bloque `directory` de cada municipio.
- `PATCH /api/v1/admin/territory/municipalities/:id/directory` — `editor+`.

El endpoint valida URLs HTTPS, email y fecha ISO. Cada cambio genera el evento de auditoría `territory.municipality_directory_changed` con estado anterior y posterior.

El editor institucional no permite modificar desde esta superficie el INE, AEMET, geometría, centro GIS ni la relación municipio/localidad.

Para que una entrada editorial aparezca en un hub municipal debe conservar el `municipality_id` canónico. Para noticias y eventos este vínculo debe ser explícito; la API no intenta adivinarlo.

## Verificación y mantenimiento

Cada ficha tiene `source_url` y `verified_at`. Al revisar un Ayuntamiento:

1. comprobar la web institucional;
2. actualizar enlaces/contacto si procede;
3. conservar una URL institucional como fuente;
4. actualizar `verified_at`;
5. guardar desde Admin para que quede auditoría.

El contenido editorial sigue el flujo normal de `cms_entries` y solo entra en el hub cuando está publicado y dentro de su ventana de publicación.

## Contrato automatizado

`scripts/check-municipalities-directory.mjs` comprueba:

- los 16 códigos INE esperados;
- nombre y web oficial sembrados;
- fecha de verificación inicial;
- tabla institucional;
- endpoints públicos de colección y detalle;
- endpoint Admin y evento de auditoría;
- entrada desde Explorar;
- agregado CMS por `municipality_id` explícito;
- tipos `place`, `mill`, `directory`, `news` y `event`;
- respeto de ventanas de publicación;
- contrato de cliente `content_counts` + `related_content`;
- secciones principales de la experiencia municipal.

La validación específica se ejecuta además desde el workflow `V20 municipalities directory`.
