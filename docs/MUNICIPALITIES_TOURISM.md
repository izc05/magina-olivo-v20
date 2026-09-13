# Mágina Olivo V20 — Patrimonio y turismo municipal

## Objetivo

Extender el hub municipal sin crear un segundo catálogo de lugares ni duplicar contenido editorial.

La solución reutiliza `cms_entries` de tipo `place` y añade una clasificación editorial explícita dentro de `content_json`.

## Taxonomía

Cada entrada `place` puede declarar:

- `municipality_id` — municipio canónico de `territory_municipalities`;
- `municipality_name` — nombre de apoyo para administración;
- `municipality_slug` — slug de apoyo para administración;
- `municipality_role` — función editorial dentro del hub municipal.

Valores permitidos de `municipality_role`:

- `profile` — perfil editorial principal del municipio;
- `heritage` — patrimonio histórico, arquitectónico, arqueológico o cultural;
- `nature` — recurso natural, paisaje, mirador, paraje o elemento ambiental;
- `tourism` — recurso o lugar turístico que no encaja mejor en las categorías anteriores.

No se crea una tabla adicional de patrimonio y no se introduce un nuevo tipo CMS.

## Administración

Ruta:

`/admin/ayuntamientos/patrimonio`

El editor carga exclusivamente entradas CMS de tipo `place` y permite asignar:

1. municipio canónico;
2. función municipal.

Al guardar se conserva íntegro el `content_json` existente y solo se añaden, cambian o eliminan los campos territoriales anteriores.

La opción `Sin municipio` elimina la relación territorial sin borrar el contenido CMS.

## Hub público

Ruta:

`/ayuntamientos/[slug]`

La ficha pública separa dos conceptos:

- **Perfil local**: usa primero la entrada `place` con `municipality_role = profile`. Como compatibilidad con contenido anterior, si no existe usa la primera entrada `place` vinculada que todavía no tenga función municipal.
- **Qué descubrir**: muestra únicamente entradas `place` clasificadas como `heritage`, `nature` o `tourism`.

Esto evita que, al publicar un castillo, una fuente, un mirador o un museo, esa entrada sustituya accidentalmente el perfil principal del municipio.

Las tarjetas distinguen visualmente Patrimonio, Naturaleza y Turismo y reutilizan título, resumen, imagen, enlace externo y demás datos ya presentes en el CMS.

## Reglas de publicación

El hub sigue usando la API pública municipal existente. Por tanto, un lugar solo llega al cliente cuando:

- su entrada CMS está `published`;
- está dentro de su ventana `starts_at` / `ends_at`;
- contiene el `municipality_id` canónico del municipio.

No se asignan lugares por coincidencia de nombre, texto o proximidad inferida.

## Relación con otros módulos

Este trabajo no modifica:

- Rutas;
- Empresas;
- GIS / Catastro / SIGPAC;
- clima / radar;
- modelo canónico de municipios y localidades.

Una futura integración con Rutas o Empresas debe consumir sus fuentes canónicas cuando esos PR se integren, no replicarlas en `place`.

## Contrato

`scripts/check-municipalities-directory.mjs` protege ahora:

- acceso Admin a Patrimonio y turismo;
- reutilización exclusiva de entradas `place`;
- clasificación `profile / heritage / nature / tourism`;
- persistencia explícita de `municipality_id` y `municipality_role`;
- preferencia del perfil explícito;
- sección pública “Patrimonio, naturaleza y lugares para descubrir”.
