# Mágina Olivo V20 — Directorio de Ayuntamientos

## Alcance

Este módulo mantiene un directorio institucional de los 16 municipios incluidos en el ámbito territorial de Sierra Mágina usado por V20:

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

## Modelo

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

Esta separación impide que una edición de contacto pueda romper GIS, Catastro, SIGPAC, AEMET o la vinculación de fincas.

## Datos iniciales

La migración `0061_municipalities_directory.sql` completa el catálogo hasta 16 municipios, crea las cabeceras municipales que falten y siembra una ficha institucional con web oficial para cada ayuntamiento. Los campos de contacto solo se rellenan cuando hay una referencia institucional suficientemente clara; de lo contrario permanecen nulos para no inventar datos.

Los registros iniciales quedan fechados como verificados el `2026-09-13` y conservan `source_url`.

## API pública

- `GET /api/v1/public/territory/municipalities`
- `GET /api/v1/public/territory/municipalities/:slug`

Solo devuelven municipios activos con ficha institucional visible. Cada respuesta incluye localidades públicas relacionadas.

## Web pública

- `/ayuntamientos` — buscador y listado de los ayuntamientos.
- `/ayuntamientos/[slug]` — ficha institucional individual.
- `/explorar` — incluye acceso directo al directorio.

La interfaz muestra estados reales de carga/error y no sustituye una fuente caída con datos inventados.

## Administración

- `/admin/ayuntamientos` — editor dedicado de información institucional.
- `GET /api/v1/admin/territory/catalog` — incluye el bloque `directory` de cada municipio.
- `PATCH /api/v1/admin/territory/municipalities/:id/directory` — `editor+`.

El endpoint valida URLs HTTPS, email y fecha ISO. Cada cambio genera el evento de auditoría `territory.municipality_directory_changed` con estado anterior y posterior.

El editor no permite modificar desde esta superficie el INE, AEMET, geometría, centro GIS ni la relación municipio/localidad.

## Verificación y mantenimiento

Cada ficha tiene `source_url` y `verified_at`. Al revisar un Ayuntamiento:

1. comprobar la web institucional;
2. actualizar enlaces/contacto si procede;
3. conservar una URL institucional como fuente;
4. actualizar `verified_at`;
5. guardar desde Admin para que quede auditoría.

## Contrato automatizado

`scripts/check-municipalities-directory.mjs` comprueba:

- los 16 códigos INE esperados;
- nombre y web oficial sembrados;
- fecha de verificación inicial;
- tabla institucional;
- endpoints públicos de colección y detalle;
- endpoint Admin y evento de auditoría;
- entrada desde Explorar.

La validación específica se ejecuta además desde el workflow `V20 municipalities directory`.
