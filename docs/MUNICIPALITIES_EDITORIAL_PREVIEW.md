# Preview editorial municipal

Ruta: `/admin/ayuntamientos/preview?municipio=<slug>`.

## Objetivo

Permitir comparar el estado editorial que el administrador está preparando con el resultado público que ve actualmente cualquier usuario, sin publicar automáticamente nada ni crear una segunda fuente de contenido.

## Borrador editorial

La columna izquierda usa `adminApi.content()` y el catálogo territorial canónico. Puede mostrar piezas `draft`, `published` o `archived` porque su función es precisamente revisar lo que todavía no está necesariamente online.

Incluye:
- perfil principal explícito (`municipality_role=profile`);
- resumen y hero;
- hasta tres imprescindibles;
- estado real de cada entrada;
- indicador `featured` cuando procede;
- preflight editorial 5/5.

La selección de imprescindibles sigue el contrato público: destacados primero, después diversidad `heritage / nature / tourism`, después el resto hasta un máximo de tres.

## Resultado público actual

La columna derecha no reconstruye la ficha pública: carga directamente `/ayuntamientos/<slug>` en un iframe de mismo origen. Por tanto, esa columna refleja las reglas públicas reales de publicación y sirve como comparación con el borrador.

## Seguridad editorial

- no hay botón de publicación en Preview;
- no se hacen mutaciones desde esta pantalla;
- no se añaden tablas, endpoints ni almacenamiento;
- el contexto municipal viaja por `?municipio=<slug>`;
- no se generan textos, imágenes ni recomendaciones ficticias.
