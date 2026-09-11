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

Permite modificar la web sin escribir JSON ni desplegar código.

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

Permite crear promociones y avisos diferenciados del contenido editorial.

### Programación

Cada entrada puede indicar:

- `starts_at`: fecha/hora desde la que es pública;
- `ends_at`: fecha/hora hasta la que es pública.

El endpoint público filtra por ambas fechas. Un elemento publicado con inicio futuro no aparece antes de tiempo y uno caducado deja de aparecer automáticamente.

### Configuración pública

El editor dispone de formularios para:

- identidad pública (`site.identity`);
- contacto (`site.contact`);
- redes sociales (`site.social`).

Los ajustes privados continúan visibles únicamente en el centro de control.

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

Desde la biblioteca se copia una URL pública estable de Mágina y se pega en el campo **Imagen** del editor de portada, noticias, eventos, cooperativas o promociones. El editor ofrece acceso directo a **Multimedia** para que este flujo no dependa de servicios externos.

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
- ajustes globales;
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

## Regla de despliegue

No fusionar este frente en `main` directamente. El PR de Admin se integra primero en la rama candidata V20 cuando los gates específicos y generales estén verdes.
