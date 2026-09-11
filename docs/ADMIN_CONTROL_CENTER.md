# Mágina Olivo V20 — Centro de administración

## Objetivo

El Admin es el backoffice corporativo de la plataforma. No forma parte de `Mi Campo` y no hereda permisos de los espacios de trabajo de agricultores o profesionales.

Ruta web: `/admin`

## Acceso corporativo

Mágina reutiliza Google Identity y la sesión HttpOnly de V20. La autorización administrativa se resuelve después, en backend.

### Primer superadministrador

Configurar en el entorno de la API:

```env
ADMIN_BOOTSTRAP_EMAILS=admin@empresa.tld
```

Se admiten varios correos separados por coma. La comparación es exacta y no se concede acceso por dominio completo.

El correo debe iniciar sesión normalmente con Google. Si coincide con `ADMIN_BOOTSTRAP_EMAILS`, la API le concede `super_admin` como acceso bootstrap.

Después puede conceder permisos persistentes desde **Admin > Usuarios**. Esos permisos se guardan en `platform_admins`.

## Roles

| Rol | Lectura | CMS / ajustes | Suspender usuarios | Conceder roles Admin | Auditoría |
| --- | --- | --- | --- | --- | --- |
| `support` | Sí | No | No | No | No |
| `editor` | Sí | Sí | No | No | No |
| `admin` | Sí | Sí | Sí | No | Sí |
| `super_admin` | Sí | Sí | Sí | Sí | Sí |

Un `owner` o `admin` de un workspace/finca **no es administrador de plataforma**.

## Módulos del panel

### Resumen

Métricas de usuarios, workspaces, fincas activas, contenido gestionado y contenido publicado. Muestra también actividad administrativa reciente.

### Contenido

Tipos soportados:

- páginas;
- noticias;
- eventos;
- pueblos y lugares;
- almazaras/cooperativas;
- directorio local;
- promociones;
- avisos.

Cada entrada admite slug, título, resumen, contenido JSON estructurado, estado, destacado, orden, imagen/media y enlace externo.

Estados: `draft`, `published`, `archived`.

El endpoint `/api/v1/public/content` solo devuelve entradas publicadas y dentro de su ventana temporal.

La pantalla `/explorar` ya consume esta fuente; por tanto, una entrada publicada desde Admin puede aparecer en la web sin un despliegue de código.

### Usuarios

- consultar usuarios y último acceso;
- ver número de workspaces activos;
- suspender/reactivar cuentas;
- ver permisos de plataforma;
- como `super_admin`, conceder o revocar roles administrativos.

La API impide que el administrador actual se suspenda o revoque a sí mismo.

### Ajustes

`site_settings` permite almacenar configuración JSON con una clave estable, por ejemplo:

- `home.hero`;
- `contact.phone`;
- `explore.banner`;
- `alerts.banner`.

Cada ajuste se marca explícitamente como público o privado. `/api/v1/public/site-settings` solo expone los marcados como públicos.

### Auditoría

Los cambios administrativos se registran en `admin_audit_log` con:

- usuario actor;
- rol efectivo;
- acción;
- tipo de objeto;
- identificador;
- metadatos;
- fecha/hora.

Actualmente se auditan creación/edición/archivo de contenido, cambios de estado de usuarios, cambios de roles de plataforma y ajustes globales.

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

Públicos:

- `GET /api/v1/public/content`
- `GET /api/v1/public/site-settings`

## Base de datos

La migración `0042_platform_admin_cms.sql` crea:

- `platform_admins`;
- `cms_entries`;
- `site_settings`;
- `admin_audit_log`.

## Validación

El workflow `V20 platform admin check` realiza typecheck/build, aplica todas las migraciones y ejecuta `admin-smoke`, que comprueba:

1. el correo bootstrap entra como `super_admin`;
2. un propietario de workspace no entra al Admin por ser propietario;
3. el CMS publica contenido consumible por la API pública;
4. un ajuste privado no aparece en la API pública;
5. el superadministrador puede conceder `editor`;
6. el editor puede editar contenido pero no suspender usuarios;
7. las acciones sensibles aparecen en auditoría.

## Regla de despliegue

No fusionar este frente en `main` directamente. El PR de Admin se integra primero en la rama candidata V20 cuando los gates específicos y generales estén verdes.
