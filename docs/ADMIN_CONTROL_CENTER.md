# Mágina Olivo V20 — Centro de administración

## Objetivo

El Admin es el backoffice corporativo de la plataforma. No forma parte de `Mi Campo` y no hereda permisos de los espacios de trabajo de agricultores o profesionales.

Rutas principales:

- `/admin` — centro de control, usuarios, roles, CMS, ajustes y auditoría;
- `/admin/web` — editor visual de la web pública;
- `/admin/media` — biblioteca multimedia corporativa.

## Acceso corporativo

Mágina reutiliza Google Identity y la sesión HttpOnly de V20. La autorización administrativa se resuelve después, en backend.

### Primer superadministrador

Configurar en el entorno de la API:

```env
ADMIN_BOOTSTRAP_EMAILS=admin@empresa.tld
```

Se admiten varios correos separados por coma. La comparación es exacta y no se concede acceso por dominio completo.

El correo debe iniciar sesión normalmente con Google. Si coincide con `ADMIN_BOOTSTRAP_EMAILS`, la API le concede `super_admin` como acceso bootstrap. Después puede conceder permisos persistentes desde **Admin > Usuarios**, almacenados en `platform_admins`.

## Roles

| Rol | Lectura | CMS / ajustes / media | Suspender usuarios | Conceder roles Admin | Auditoría |
| --- | --- | --- | --- | --- | --- |
| `support` | Sí | No | No | No | No |
| `editor` | Sí | Sí | No | No | No |
| `admin` | Sí | Sí | Sí | No | Sí |
| `super_admin` | Sí | Sí | Sí | Sí | Sí |

Un `owner` o `admin` de un workspace/finca **no es administrador de plataforma**.

## Centro de control `/admin`

Incluye métricas de usuarios, workspaces, fincas activas, contenido gestionado y publicado, actividad administrativa reciente, gestión de usuarios, roles corporativos, CMS genérico, ajustes y auditoría.

Los accesos directos **Editar web** y **Multimedia** llevan a las superficies editoriales especializadas.

## Editor visual `/admin/web`

La segunda versión del editor concentra Inicio, contenido editorial, territorio, publicidad y SEO. Permite modificar la web sin escribir JSON ni desplegar código.

### Selección directa de imágenes

Los campos de imagen de portada, noticias, eventos, cooperativas, promociones y SEO abren directamente la biblioteca corporativa. Ya no es necesario copiar y pegar manualmente la URL desde `/admin/media`.

El selector:

- lista solo activos con estado `uploaded`;
- muestra miniaturas y nombre de archivo;
- conserva la entrada manual de URL como alternativa;
- permite retirar la imagen seleccionada;
- ofrece acceso directo a la biblioteca completa.

### Inicio

Ajustes estructurados:

- `home.hero`: texto superior, título, descripción, botón, destino e imagen;
- `home.territory_banner`: título y llamada a la acción territorial;
- `alerts.banner`: activación, texto y enlace del aviso superior.

La portada consume estos ajustes mediante `/api/v1/public/site-settings`. Si no existe una configuración administrada, mantiene el contenido de respaldo de V20.

### Noticias y eventos

El editor permite crear y modificar noticias y eventos con título, slug, resumen, cuerpo, ubicación, imagen, enlace, destacado y orden.

### Territorio y cooperativas

Permite gestionar pueblos/lugares, almazaras/cooperativas y elementos de directorio con datos como localidad, teléfono, dirección, descripción, imagen y enlace.

### Publicidad y avisos

Las promociones pueden declarar patrocinador, llamada a la acción y una posición pública fija:

- `home_top` — Inicio, parte superior;
- `home_inline` — Inicio, entre bloques;
- `explore_top` — Explorar, parte superior;
- `explore_inline` — Explorar, entre bloques.

Las posiciones solo consumen promociones publicadas y vigentes. Las promociones asignadas a un slot fijo se excluyen de la parrilla editorial genérica de Inicio para evitar duplicidades. Los enlaces y recursos administrados se restringen a rutas relativas seguras o protocolos HTTP/HTTPS antes de renderizarse.

### Vista previa antes de publicar

El formulario dispone de **Vista previa** sin guardar. La previsualización muestra los valores que están actualmente en el formulario, incluidos imagen, título, resumen, texto, ubicación, CTA, patrocinador y posición publicitaria.

También indica si el elemento está en borrador, programado, visible actualmente, caducado o archivado según estado y fechas.

### Programación

Cada entrada puede indicar:

- `starts_at`: fecha/hora desde la que es pública;
- `ends_at`: fecha/hora hasta la que es pública.

El endpoint público filtra por ambas fechas. Un elemento publicado con inicio futuro no aparece antes de tiempo y uno caducado deja de aparecer automáticamente.

### SEO y buscadores

El ajuste público `site.seo` permite administrar:

- título;
- descripción;
- imagen Open Graph seleccionada desde Multimedia;
- permiso de indexación mediante `robots_index`.

El editor incluye contadores de longitud y una previsualización tipo resultado de búsqueda. La aplicación conserva metadatos estáticos de respaldo y, cuando carga la configuración pública, actualiza `title`, description, Open Graph y robots en el navegador. Esta implementación actual es compatible con la arquitectura estática/client de V20; una fase futura puede promover estos valores a metadatos generados en servidor cuando el despliegue público use renderizado dinámico.

## Biblioteca multimedia `/admin/media`

La biblioteca usa el mismo `StoragePort` S3-compatible de V20 (por ejemplo R2/S3), pero con un namespace corporativo separado: `platform-media`.

El servidor ya inicializa ese almacenamiento desde las variables S3 existentes. Para habilitar subidas reales deben estar configuradas, como mínimo:

```env
S3_ENDPOINT=https://...
S3_BUCKET=...
S3_ACCESS_KEY_ID=...
S3_SECRET_ACCESS_KEY=...
```

Opcionales: `S3_REGION`, `S3_FORCE_PATH_STYLE`, `S3_PREFIX`, `S3_UPLOAD_TTL_SECONDS` y `S3_READ_TTL_SECONDS`.

Flujo:

1. el navegador calcula SHA-256 del archivo;
2. la API reserva una subida firmada;
3. el navegador sube directamente al almacenamiento;
4. la API verifica existencia, tamaño, MIME y checksum cuando el proveedor lo expone;
5. el activo pasa a `uploaded`;
6. Mágina expone una ruta estable `/api/v1/public/media/:id`, que redirige a una URL temporal del objeto privado.

Restricciones actuales:

- JPEG;
- PNG;
- WebP;
- AVIF;
- máximo 10 MB;
- SVG no permitido.

No se exponen las claves privadas de almacenamiento ni se hace público el bucket. Reservar, completar y archivar medios queda auditado.

## CMS

Tipos soportados:

- páginas;
- noticias;
- eventos;
- pueblos y lugares;
- almazaras/cooperativas;
- directorio local;
- promociones;
- avisos.

Cada entrada admite slug, título, resumen, contenido JSON estructurado, estado, destacado, orden, imagen/media, enlace externo y ventana temporal.

Estados: `draft`, `published`, `archived`.

`/api/v1/public/content` solo devuelve entradas publicadas y vigentes. `/explorar` y la portada ya consumen contenido gestionado.

## Usuarios

- consultar usuarios y último acceso;
- ver número de workspaces activos;
- suspender/reactivar cuentas;
- ver permisos de plataforma;
- como `super_admin`, conceder o revocar roles administrativos.

La API impide que el administrador actual se suspenda o revoque a sí mismo.

## Auditoría

`admin_audit_log` registra usuario actor, rol efectivo, acción, tipo de objeto, identificador, metadatos y fecha/hora.

Entre otras acciones se auditan:

- creación/edición/archivo de contenido;
- cambios de estado de usuarios;
- cambios de roles de plataforma;
- ajustes globales, incluido SEO;
- reserva/subida/archivo de multimedia.

## Endpoints principales

Privados:

- `GET /api/v1/admin/session`
- `GET /api/v1/admin/overview`
- `GET /api/v1/admin/users`
- `PATCH /api/v1/admin/users/:userId`
- `PUT /api/v1/admin/platform-access/:userId`
- `GET|POST /api/v1/admin/content`
- `PUT|DELETE /api/v1/admin/content/:id`
- `GET /api/v1/admin/settings`
- `PUT /api/v1/admin/settings/:key`
- `GET /api/v1/admin/audit`
- `GET /api/v1/admin/media`
- `POST /api/v1/admin/media/reserve`
- `POST /api/v1/admin/media/:id/complete`
- `DELETE /api/v1/admin/media/:id`

Públicos:

- `GET /api/v1/public/content`
- `GET /api/v1/public/site-settings`
- `GET /api/v1/public/media/:id`

## Base de datos

`0042_platform_admin_cms.sql` crea:

- `platform_admins`;
- `cms_entries`;
- `site_settings`;
- `admin_audit_log`.

`0043_platform_admin_media.sql` crea:

- `platform_media_assets`.

## Validación

El workflow `V20 platform admin check` realiza typecheck/build, aplica todas las migraciones y ejecuta `admin-smoke`.

El smoke cubre, entre otros casos:

1. correo bootstrap → `super_admin`;
2. un propietario de workspace no entra al Admin por ser propietario;
3. CMS publicado visible por API pública;
4. contenido futuro y caducado no visible fuera de su ventana;
5. ajustes privados no filtrados a la API pública;
6. SVG rechazado por la biblioteca multimedia;
7. imagen válida: reserva → verificación → publicación → acceso público;
8. concesión de rol `editor`;
9. editor puede editar contenido pero no suspender usuarios;
10. acciones sensibles y multimedia aparecen en auditoría.

Además, el build web/typecheck cubre el selector multimedia integrado, editor V2, SEO administrado, posiciones publicitarias y previsualización.

## Regla de despliegue

No fusionar este frente en `main` directamente. El PR de Admin se integra primero en la rama candidata V20 cuando los gates específicos y generales estén verdes.
