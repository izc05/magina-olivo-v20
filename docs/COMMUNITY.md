# Comunidad Mágina — V20

## Objetivo

Comunidad Mágina es el feed social público de Mágina Olivo. Une conversaciones de campo y territorio sin convertir datos privados de `Mi Campo` en contenido público.

## Principios

1. Leer el feed es público.
2. Publicar, comentar, responder, reaccionar, guardar y reportar exige una cuenta autenticada.
3. Una publicación pertenece al usuario, no a un `workspace` ni a una finca.
4. La geometría exacta de fincas, documentos privados, costes, cosechas y clientes nunca se copian automáticamente al feed.
5. Los contenidos pueden asociarse únicamente a un municipio público de Sierra Mágina.
6. La moderación y la auditoría existen antes de abrir la comunidad a usuarios reales.
7. El avatar y el identificador de autor solo se exponen cuando el perfil del usuario está marcado como público.
8. La actividad social usa un marcador de lectura propio de la cuenta y no reutiliza el centro de notificaciones agrícola ligado a workspace.

## Categorías V1

- Campo
- Preguntas
- Plagas
- Maquinaria
- Cosecha
- Pueblos
- Gastronomía
- Rutas

## Capacidades

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
- responder a comentarios raíz con un nivel de conversación;
- marcar/desmarcar `like`;
- guardar/quitar de guardados;
- recuperar la colección privada de guardados mediante `GET /api/v1/community/bookmarks`;
- filtrar los guardados por temática y municipio;
- reportar publicaciones y comentarios.

### Mi actividad

Superficie web: `/comunidad/actividad`.

Endpoints:

- `GET /api/v1/community/activity`
- `POST /api/v1/community/activity/read`

La bandeja deriva eventos reales de:

- likes de otras personas sobre publicaciones propias;
- comentarios de otras personas sobre publicaciones propias;
- respuestas directas a comentarios propios.

El estado de lectura se guarda en `community_activity_state` con un único `last_seen_at` por cuenta. No contiene `workspace_id`, finca, parcela, geometría ni datos agrícolas privados.

### Destacados territoriales

Endpoint: `GET /api/v1/public/community/highlights`.

- puede filtrarse por municipio;
- usa únicamente publicaciones publicadas de los últimos 30 días;
- pondera conversación y reacciones para ordenar contenido destacado;
- la interfaz muestra hasta tres tarjetas y cambia al seleccionar un municipio;
- no sustituye la moderación ni constituye publicidad patrocinada.

### Moderación

- módulo Admin registrado como `Comunidad Mágina` en `/admin/comunidad`;
- cola de reportes en `/api/v1/admin/community/reports`;
- filtros por estado de revisión;
- ocultar, restaurar o eliminar posts/comentarios;
- roles de plataforma obligatorios;
- acciones registradas en `admin_audit_log`;
- reportes resueltos como `reviewed`, `dismissed` o `actioned`.

## Modelo de datos

Migraciones:

- `database/migrations/0063_community.sql`
- `database/migrations/0064_community_activity_replies.sql`

Entidades:

- `community_posts`
- `community_comments`
- `community_reactions`
- `community_bookmarks`
- `community_reports`
- `community_activity_state`

`community_comments.parent_comment_id` implementa respuestas de un solo nivel. La API valida que el comentario padre pertenezca a la misma publicación, esté publicado y no sea ya una respuesta.

Los borrados de usuario son lógicos (`status=deleted`) para preservar integridad de moderación y evitar reapariciones accidentales. Las relaciones dependientes se eliminan por cascada si la cuenta se elimina físicamente.

## Privacidad

La comunidad no acepta `workspace_id`, `field_id` ni geometrías como parte del contrato público. Una futura función explícita de «Compartir desde Mi Campo» deberá crear una representación pública nueva y pedir confirmación del usuario; nunca expondrá automáticamente polígonos, coordenadas exactas ni documentos privados.

El avatar y el identificador de autor solo se devuelven en las consultas públicas cuando `user_profiles.visibility = 'public'`. Ningún email se devuelve desde los endpoints de comunidad. Si un perfil no es público, la identidad visible se reduce a `Miembro de Mágina`.

La colección de guardados y la bandeja de actividad son privadas, exigen autenticación y se vinculan únicamente a la cuenta del usuario; tampoco dependen de ningún workspace.

## Media

La versión actual permite únicamente rutas internas `/media/...` ya gestionadas por la plataforma. No se aceptan URLs de imagen arbitrarias enviadas por usuarios. La subida de imágenes deberá reutilizar el storage/media de V20 y sus controles de seguridad antes de habilitarse desde el compositor.

## Integración web

Superficies:

- `/comunidad`: feed, publicación, guardados, respuestas y destacados territoriales.
- `/comunidad/actividad`: actividad social de la cuenta.
- `/comunidad/descubrir`: búsqueda y rankings.
- `/comunidad/persona?id=<uuid>`: perfil público compatible con exportación estática.
- `/admin/comunidad`: moderación.

La entrada pública se añade a `Explorar`. Las pantallas tienen estados reales de carga, vacío y error y no utilizan publicaciones ficticias como fallback.

El módulo administrativo está incluido en `admin/modulos/admin-modules.json`, por lo que queda gobernado por el Admin unificado y por su `AdminRouteGate` compartido.

## Checks

`pnpm check:community`

El contrato verifica tablas, endpoints, autorización, moderación, auditoría, privacidad de identidad/avatar, superficie pública, filtros territoriales, compartición, reportes de comentarios, guardados, respuestas, actividad social, destacados municipales, superficie Admin y registro en el centro unificado. Además impide introducir identificadores privados de finca/workspace en las APIs sociales.

## Siguiente evolución

- subida de imágenes desde el media manager;
- perfiles públicos y logros de Mi Olivo/Mágina Aventura compartibles de forma explícita;
- menciones mediante identificadores públicos inequívocos, sin búsqueda ambigua por nombre;
- herramientas anti-spam y límites específicos por usuario;
- contenido destacado editorial explícito y separado de publicidad/patrocinio.
