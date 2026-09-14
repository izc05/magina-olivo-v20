# Mágina Olivo V20 — Catálogo patrimonial municipal verificado

## Objetivo

Dar a los 16 hubs municipales una primera capa de patrimonio real y trazable sin introducir contenido anónimo en `cms_entries` ni crear una segunda base de datos turística.

El catálogo no escribe directamente en PostgreSQL. Se importa desde `/admin/ayuntamientos/patrimonio` utilizando las rutas autenticadas del CMS, de forma que `created_by` y `updated_by` correspondan al administrador que realiza la operación.

## Cobertura inicial

Existe una propuesta verificada para cada uno de los 16 municipios:

1. Albanchez de Mágina — Castillo de Albanchez de Mágina.
2. Bedmar y Garcíez — Castillos Viejo y Nuevo de Bedmar.
3. Bélmez de la Moraleda — Castillo de Bélmez.
4. Cabra del Santo Cristo — Parroquia-Santuario del Santo Cristo de Burgos.
5. Cambil — Castillo de Mata-Bejid.
6. Campillo de Arenas — Castillo de Arenas.
7. Cárcheles — Ruinas del Castillejo de Cárchel.
8. La Guardia de Jaén — Castillo de La Guardia de Jaén.
9. Huelma — Castillo de Solera.
10. Jimena — Cueva de la Graja.
11. Jódar — Castillo de Jódar.
12. Larva — Cerro de Castellón.
13. Mancha Real — Iglesia Parroquial de San Juan Evangelista.
14. Noalejo — Iglesia de Nuestra Señora de la Asunción.
15. Pegalajar — Fuente de la Reja, Charca y Huerta.
16. Torres — Palacio de los Marqueses de Camarasa.

La fuente primaria de cada propuesta es una página o guía del ayuntamiento correspondiente. La fecha de revisión inicial del catálogo es `2026-09-13`.

## Fuente de verdad

El fichero:

`apps/web/src/lib/municipality-heritage-catalog.ts`

contiene para cada recurso:

- municipio y slug municipal;
- slug CMS estable;
- título;
- clasificación `municipality_role`;
- resumen y cuerpo factual breve;
- `sourceUrl`;
- `sourceLabel`;
- `verifiedAt`.

El catálogo es una fuente de importación, no una fuente pública paralela. Tras importar, la fuente pública sigue siendo `cms_entries`.

## Importación

Ruta:

`/admin/ayuntamientos/patrimonio`

El administrador puede:

- revisar cada propuesta y abrir su fuente oficial;
- importar/publicar un recurso individual;
- importar/actualizar los 16 en una sola operación;
- seguir clasificando manualmente cualquier otro `place` existente.

### Recursos nuevos

Se crean como:

- `type = place`;
- `status = published`;
- `municipality_role = heritage` en este primer catálogo;
- municipio canónico resuelto desde `territory_municipalities`;
- `external_url` hacia la fuente oficial;
- procedencia duplicada de forma explícita dentro de `content_json` mediante `source_url`, `source_label` y `verified_at`.

El CMS asigna la identidad del administrador autenticado a `created_by` y `updated_by`.

### Recursos existentes

La importación busca por el `slug` estable del recurso. Si ya existe:

- no crea un duplicado;
- conserva el estado de publicación existente;
- conserva título/resumen editados;
- conserva `body` editorial si ya fue editado;
- conserva imagen, destacado, ventanas temporales y orden;
- actualiza municipio, clasificación y procedencia oficial;
- completa el cuerpo inicial únicamente cuando no existe cuerpo editorial.

Por tanto, volver a ejecutar la importación es seguro e idempotente.

## Política de datos

No se utiliza IA como fuente factual para nombres, dataciones, declaraciones patrimoniales o descripciones históricas.

La IA puede ayudar posteriormente a redactar piezas promocionales, pero el dato publicable debe poder rastrearse a una fuente institucional o documental explícita.

No se añaden coordenadas inventadas. Cuando una futura fase incorpore geolocalización precisa de estos recursos deberá proceder de una fuente cartográfica verificable y no interferir con la rama de Rutas/GIS.

## Evolución

Después de esta primera capa, el catálogo puede ampliarse municipio por municipio con:

- patrimonio histórico y arqueológico;
- naturaleza y paisajes;
- miradores y espacios interpretativos;
- museos y centros de visitantes;
- patrimonio hidráulico y etnográfico.

La ampliación seguirá usando `cms_entries` y la taxonomía municipal existente, no nuevas tablas redundantes.
