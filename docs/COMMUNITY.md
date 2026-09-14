# Comunidad Mágina — V20

## Objetivo

Comunidad Mágina es el feed social público de Mágina Olivo. Une conversaciones de campo y territorio sin convertir datos privados de `Mi Campo` en contenido público.

## Principios

1. Leer el feed es público.
2. Publicar, comentar, reaccionar, guardar y reportar exige una cuenta autenticada.
3. Una publicación pertenece al usuario, no a un `workspace` ni a una finca.
4. La geometría exacta de fincas, documentos privados, costes, cosechas y clientes nunca se copian automáticamente al feed.
5. Los contenidos pueden asociarse únicamente a un municipio público de Sierra Mágina.
6. La moderación y la auditoría existen antes de abrir la comunidad a usuarios reales.
7. El avatar solo se expone cuando el perfil del usuario está marcado como público.

## Categorías V1

- Campo
- Preguntas
- Plagas
- Maquinaria
- Cosecha
- Pueblos
- Gastronomía
- Rutas

## Capacidades V1

### Feed

- `GET /api/v1/public/community`
- filtro por categoría y municipio;
- paginación temporal;
- autor, fecha y municipio público opcional;
- recuento de me gusta y comentarios;
- estado personal de `like` y guardado cuando existe sesión;
- enlaces compartibles mediante Web Share API o copia al portapapeles.

### Participación autenticada

- crear y borrar publicaciones propias;
- asociar opcionalmente la publicación a un municipio público, nunca a una finca;
- comentar y borrar comentarios propios;
- marcar/desmarcar `like`;
- guardar/quitar de guardados;
- recuperar la colección privada de guardados mediante `GET /api/v1/community/bookmarks`;
- filtrar los guardados por temática y municipio;
- reportar publicaciones y comentarios.

### Moderación

- módulo Admin registrado como `Comunidad Mágina` en `/admin/comunidad`;
- cola de reportes en `/api/v1/admin/community/reports`;
- filtros por estado de revisión;
- ocultar, restaurar o eliminar posts/comentarios;
- roles de plataforma obligatorios;
- acciones registradas en `admin_audit_log`;
- reportes resueltos como `reviewed`, `dismissed` o `actioned`.

## Modelo de datos

Migración: `database/migrations/0063_community.sql`.

Entidades:

- `community_posts`
- `community_comments`
- `community_reactions`
- `community_bookmarks`
- `community_reports`

Los borrados de usuario son lógicos (`status=deleted`) para preservar integridad de moderación y evitar reapariciones accidentales. Las relaciones dependientes se eliminan por cascada si la cuenta se elimina físicamente.

## Privacidad

La comunidad no acepta `workspace_id`, `field_id` ni geometrías como parte del contrato público V1. Una futura función explícita de «Compartir desde Mi Campo» deberá crear una representación pública nueva y pedir confirmación del usuario; nunca expondrá automáticamente polígonos, coordenadas exactas ni documentos privados.

El avatar solo se devuelve en las consultas públicas cuando `user_profiles.visibility = 'public'`. Ningún email se devuelve desde los endpoints de comunidad.

La colección de guardados es privada, exige autenticación y se vincula únicamente a la cuenta del usuario; tampoco depende de ningún workspace.

## Media

V1 permite únicamente rutas internas `/media/...` ya gestionadas por la plataforma. No se aceptan URLs de imagen arbitrarias enviadas por usuarios. La subida de imágenes deberá reutilizar el storage/media de V20 y sus controles de seguridad antes de habilitarse desde el compositor.

## Integración web

Superficie pública: `/comunidad`.

La entrada se añade a `Explorar`. La pantalla tiene estados reales de carga, vacío y error y no utiliza publicaciones ficticias como fallback. Puede filtrar por temática y por municipio oficial; además permite compartir publicaciones, reportar tanto publicaciones como comentarios y alternar entre `Recientes` y la colección privada `Guardados`.

Superficie administrativa: `/admin/comunidad`.

El módulo está incluido en `admin/modulos/admin-modules.json`, por lo que queda gobernado por el Admin unificado y por su `AdminRouteGate` compartido.

## Checks

`pnpm check:community`

El contrato verifica tablas, endpoints, autorización, moderación, auditoría, privacidad del avatar, superficie pública, filtros territoriales, compartición, reportes de comentarios, colección de guardados, superficie Admin y registro en el centro unificado. Además impide introducir identificadores privados de finca/workspace en las APIs de comunidad y guardados.

## Siguiente evolución

- subida de imágenes desde el media manager;
- perfiles públicos y logros de Mi Olivo/Mágina Aventura compartibles de forma explícita;
- notificaciones de respuestas/reacciones con un modelo que no dependa de workspace;
- herramientas anti-spam y límites específicos por usuario;
- contenido destacado/editorial sin mezclarlo con publicidad encubierta.
