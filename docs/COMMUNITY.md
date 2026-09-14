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
- estado personal de `like` y guardado cuando existe sesión.

### Participación autenticada

- crear y borrar publicaciones propias;
- comentar y borrar comentarios propios;
- marcar/desmarcar `like`;
- guardar/quitar de guardados;
- reportar publicaciones o comentarios.

### Moderación

- cola de reportes en `/api/v1/admin/community/reports`;
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

La foto/avatar solo debe tratarse como identidad pública cuando la política de perfil correspondiente lo permita. Ningún email se devuelve desde los endpoints de comunidad.

## Media

V1 permite únicamente rutas internas `/media/...` ya gestionadas por la plataforma. No se aceptan URLs de imagen arbitrarias enviadas por usuarios. La subida de imágenes deberá reutilizar el storage/media de V20 y sus controles de seguridad antes de habilitarse desde el compositor.

## Integración web

Superficie pública: `/comunidad`.

La entrada se añade a `Explorar`. La pantalla tiene estados reales de carga, vacío y error y no utiliza publicaciones ficticias como fallback.

## Checks

`pnpm check:community`

El contrato verifica tablas, endpoints, autorización, moderación, auditoría, superficie web y la ausencia de identificadores privados de finca/workspace en el API de comunidad.

## Siguiente evolución

- subida de imágenes desde el media manager;
- reportes sobre comentarios desde la UI;
- perfiles públicos y logros de Mi Olivo/Mágina Aventura compartibles de forma explícita;
- notificaciones de respuestas/reacciones;
- panel visual de moderación integrado en el Admin unificado;
- herramientas anti-spam y límites específicos por usuario;
- contenido destacado/editorial sin mezclarlo con publicidad encubierta.
