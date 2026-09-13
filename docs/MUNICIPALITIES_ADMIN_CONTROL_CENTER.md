# Centro de control municipal

La superficie `/admin/ayuntamientos` funciona como punto de gobierno de los 16 municipios de Sierra Mágina.

## Qué controla

- visibilidad pública de la ficha municipal;
- web oficial, sede electrónica, transparencia y turismo;
- teléfono, email, dirección y código postal;
- fuente y fecha de verificación;
- completitud institucional;
- acceso a cobertura municipal;
- acceso a patrimonio/naturaleza/turismo;
- acceso a vinculación de noticias y eventos;
- vista pública del municipio seleccionado.

## Fuente de verdad

No se crea un modelo paralelo. La edición institucional continúa usando `PATCH /api/v1/admin/territory/municipalities/:id/directory` y el catálogo Admin existente.

Los editores especializados siguen siendo responsables de sus dominios:

- `/admin/ayuntamientos/cobertura`
- `/admin/ayuntamientos/patrimonio`
- `/admin/ayuntamientos/actualidad`

El centro de control los coordina y proporciona navegación contextual, pero no duplica sus datos.

## Completitud

La señal de calidad es objetiva y se calcula únicamente a partir de campos existentes: web oficial, teléfono, email, dirección, sede electrónica, turismo, fuente y fecha de verificación. No es una valoración editorial subjetiva.

## Publicación

`public_enabled` sigue siendo el control canónico para mostrar u ocultar la ficha municipal pública. El cambio se guarda mediante la API Admin auditada.

## Responsive y accesibilidad

El layout pasa de consola de dos columnas en escritorio a una columna en tablet/móvil. Buscador, filtros y selector municipal tienen foco visible y los filtros usan `aria-pressed`.

## Contrato

`scripts/check-municipality-admin-control-center.mjs` protege la presencia del gobierno municipal, controles de visibilidad, completitud, navegación especializada y responsive.
