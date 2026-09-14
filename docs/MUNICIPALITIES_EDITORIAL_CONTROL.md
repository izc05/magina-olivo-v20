# V20 · Control editorial municipal

## Objetivo
Permitir que la portada pública de cada municipio se gobierne desde Admin sin tocar código y sin crear una base editorial paralela.

Ruta: `/admin/ayuntamientos/editorial`.

## Fuente de verdad
La pantalla reutiliza `cms_entries` de tipo `place` y el catálogo municipal canónico. No añade tablas, endpoints ni metadatos alternativos.

Campos existentes utilizados:
- `content_json.municipality_id` y `municipality_role`;
- `featured`;
- `sort_order`;
- `media_url`;
- `summary`;
- `status`.

## Perfil y hero
El lugar con `municipality_role=profile` es el perfil editorial principal. Su `media_url` alimenta el hero público cuando existe y su resumen presenta el municipio.

Admin puede cambiar el perfil principal entre los lugares ya vinculados al municipio. El perfil anterior deja de tener rol `profile`; el nuevo perfil se marca como tal y no se fuerza como destacado.

## Lo imprescindible
Los descubrimientos con rol `heritage`, `nature` o `tourism` pueden marcarse como `featured`.

La pantalla impide seleccionar más de tres destacados simultáneos. Entre destacados, la prioridad se controla con `sort_order`: la API pública ya ordena `featured DESC, sort_order DESC`.

Si hay menos de tres destacados, la portada conserva el fallback público existente para completar con diversidad de patrimonio, naturaleza y turismo.

## Publicación y medios
Cada descubrimiento puede pasar entre `draft`, `published` y `archived`. Solo el contenido publicado y dentro de su ventana temporal llega a la API pública.

La imagen usa `media_url` real del CMS. No se generan ni inventan imágenes para rellenar huecos.

## Auditoría
Todas las mutaciones pasan por `adminApi.updateContent`, reutilizando el CMS administrativo y su autoría/auditoría existentes.

## Límites deliberados
- No se cambia la geometría municipal ni GIS.
- No se toca Rutas, Empresas ni clima.
- No se crea un ranking turístico.
- No se añaden recomendaciones automáticas ni IA editorial.
- No se modifica `main`.

## Contrato
`scripts/check-municipality-editorial-control.mjs` protege:
- uso exclusivo de CMS/territorio canónico;
- rol de perfil;
- hero/media;
- máximo de tres imprescindibles;
- `featured` + `sort_order`;
- estado editorial;
- mutación mediante `adminApi`;
- orden público por destacado/prioridad.
