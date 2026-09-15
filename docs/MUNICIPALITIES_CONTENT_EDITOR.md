# V20 Ayuntamientos — editor completo de contenido municipal

## Objetivo

Permitir que un administrador gestione contenido municipal real desde Admin sin editar código y sin crear una segunda fuente editorial.

Ruta: `/admin/ayuntamientos/contenido`.

## Fuente de verdad

El editor reutiliza exclusivamente `cms_entries` de tipo `place` y el catálogo territorial canónico. Cada pieza municipal conserva:

- `municipality_id`
- `municipality_name`
- `municipality_slug`
- `municipality_role`: `profile`, `heritage`, `nature` o `tourism`
- `source_url`
- `source_label`
- `verified_at`
- `body`

Los campos editoriales nativos del CMS siguen siendo la fuente de publicación: `title`, `slug`, `summary`, `status`, `featured`, `media_url`, `external_url` y `sort_order`.

## Capacidades

- crear una pieza municipal nueva;
- editar una pieza existente;
- cambiar municipio y categoría;
- escribir título, resumen y cuerpo;
- mantener una fuente oficial y fecha de verificación;
- seleccionar Borrador / Publicado / Archivado;
- elegir una imagen ya subida en la biblioteca Multimedia;
- abrir `/admin/media` para subir nuevas imágenes y refrescar el selector;
- marcar hasta tres descubrimientos como `Lo imprescindible`;
- definir prioridad editorial mediante `sort_order`;
- elegir un único perfil principal por municipio;
- comprobar la ficha pública del municipio.

## Reglas

### Perfil principal

Solo debe existir un `municipality_role=profile` activo por municipio. Al guardar un nuevo perfil, el editor retira el rol `profile` a cualquier perfil anterior sin borrar esa pieza CMS.

### Imprescindibles

El editor impide activar un cuarto `featured` de descubrimiento. La API pública ya entrega contenido por `featured DESC, sort_order DESC`, por lo que la portada refleja estas decisiones sin endpoint adicional.

### Slugs

El alta genera un slug basado en municipio + título y detecta colisiones antes de guardar. Un slug existente no se sobrescribe silenciosamente.

### Multimedia

No se introduce otro almacenamiento. Las imágenes se seleccionan desde `adminApi.media()` y apuntan a las URLs públicas gestionadas por la biblioteca corporativa existente.

## Fuera de alcance

- noticias y eventos, que conservan su editor específico;
- empresas/directorio;
- rutas/GPX;
- GIS, Catastro o SIGPAC;
- clima/radar;
- nuevas migraciones o nuevas tablas.

## Contrato

`scripts/check-municipality-content-editor.mjs` protege creación/edición, identidad municipal canónica, procedencia, multimedia, perfil único, máximo de tres imprescindibles, publicación y orden público.
