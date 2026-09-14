# Experiencia pública de descubrimientos municipales

## Objetivo

Hacer utilizable el catálogo municipal de patrimonio, naturaleza y turismo cuando crezca por encima de una o dos tarjetas por municipio, sin cambiar el modelo CMS ni duplicar datos.

## Superficie

`/ayuntamientos/[slug]` → sección `#descubrir`.

## Comportamiento

La ficha pública lee exclusivamente `related_content` ya publicado por la API municipal. Un descubrimiento sigue siendo una entrada CMS `place` enlazada explícitamente por `municipality_id` y clasificada mediante `content_json.municipality_role`.

Roles admitidos:

- `heritage` → Patrimonio
- `nature` → Naturaleza
- `tourism` → Turismo

No se infiere la categoría por título, resumen, nombre del municipio ni palabras clave.

## Filtros y contadores

La sección ofrece cuatro filtros locales:

- Todo
- Patrimonio
- Naturaleza
- Turismo

Cada filtro muestra el número real de entradas publicadas recibidas para ese municipio. El filtrado ocurre en cliente y no modifica la URL, la API ni el CMS.

Los botones usan `aria-pressed` y el resumen de resultados usa `aria-live="polite"`. Si una categoría no tiene resultados se muestra un estado vacío específico sin ocultar el acceso al catálogo completo.

## Procedencia

Cuando una entrada `place` contiene:

- `source_url`
- `source_label`
- `verified_at`

la tarjeta pública muestra una sección `Fuente verificada`, enlace a la procedencia y fecha de verificación.

La ausencia de alguno de esos campos no inventa información ni bloquea la ficha. La procedencia pública es una proyección del CMS; no se consulta una fuente externa en tiempo real.

## Responsive

- escritorio/tablet: filtros flexibles en una fila o varias según espacio;
- móvil: cuadrícula 2 columnas para que los cuatro filtros sean táctiles y compactos;
- tarjetas de descubrimiento siguen el grid público existente 3 → 2 → 1.

## Separación de responsabilidades

Este frente no modifica:

- los 32 recursos auditados de #90/#91;
- Admin/importador;
- Rutas/GPX;
- Empresas;
- GIS o coordenadas;
- clima/radar;
- base de datos;
- `main`.

## Contrato

`scripts/check-municipality-discovery-experience.mjs` protege:

- los cuatro filtros;
- roles explícitos;
- contadores;
- filtrado local;
- `aria-pressed` y `aria-live`;
- estado vacío por filtro;
- `source_url`, `source_label`, `verified_at`;
- etiqueta de procedencia visible;
- estilos responsive y foco de teclado.

El workflow `V20 municipalities directory` ejecuta este contrato antes del typecheck del workspace.
