# V20 · Identidad, sesiones y memberships

Estado: base de autenticación en integración. Mantener el PR como Draft.

## Regla principal

Una cuenta de Mágina Olivo es una entidad propia. Google es un proveedor de identidad, no el ID interno del usuario.

```text
Google Identity Services
        |
        | ID token
        v
GoogleIdentityVerifier
        |
        | claims verificadas
        v
users
  |
  +--> auth_identities (google/sub)
  +--> user_sessions
  +--> workspace_memberships
                |
                v
           workspaces
                |
                v
             Mi Campo
```

## Identidad Google

La clave externa estable es:

```text
(provider = google, provider_subject = Google sub)
```

No usar email como identificador.

El email se conserva como atributo de contacto/identidad y puede cambiar. La aplicación no fusiona automáticamente dos usuarios porque compartan email. Si aparece un conflicto se devuelve `account_link_required` y la futura vinculación deberá ser explícita y auditable.

## Primer acceso

Primer login válido con Google:

1. verifica ID token en servidor contra `GOOGLE_CLIENT_ID`;
2. crea `users`;
3. crea `auth_identities`;
4. crea workspace personal `Mi campo`;
5. crea membership `owner`;
6. crea token de sesión aleatorio;
7. almacena únicamente SHA-256 del token;
8. entrega cookie `magina_session` HttpOnly, SameSite=Lax.

Un login posterior con el mismo Google `sub` reutiliza el mismo usuario y memberships.

## Sesiones

`user_sessions` contiene:
- usuario;
- hash SHA-256 del token;
- caducidad;
- revocación;
- timestamps;
- user-agent informativo.

El token bruto nunca se guarda en PostgreSQL.

Duración inicial: 30 días. Se revisará antes de producción junto con política de rotación y dispositivos.

## Contexto de workspace

La cookie autentica **quién es el usuario**.

`x-workspace-id` indica **qué workspace quiere usar**.

La API no confía en esa cabecera: en cada petición protegida comprueba:

```text
session válida
    AND
user activo
    AND
membership(workspace, user).status = active
```

Solo entonces construye `RequestContext`.

Esto evita que conocer o adivinar un UUID de otra explotación permita leerla.

## Roles agrícolas

Roles dentro de un workspace privado:
- `owner`
- `admin`
- `manager`
- `member`
- `worker`
- `viewer`

El detalle de permisos por rol se cerrará en una matriz RBAC separada.

## Separación obligatoria con Admin/CMS

Los roles de Mi Campo **NO** son los roles de administración global de la plataforma.

Futuro backoffice global:
- `super_admin`
- `admin`
- `editor`
- `moderator`
- `commercial`
- `support`
- `analyst/viewer`

Ejemplo:

```text
Usuario X
├── CMS role: editor
└── Workspace Las Cenillas: SIN membership

Resultado:
- puede editar una noticia;
- NO puede ver kilos, costes, geometría, documentos ni historial privado.
```

No reutilizar `workspace_memberships.role` para permisos editoriales globales.

## Desarrollo y CI

Las antiguas cabeceras:
- `x-user-id`
- `x-workspace-id`

solo se aceptan si:

```text
ALLOW_DEV_AUTH_HEADERS=true
```

Este flag debe estar desactivado en producción.

## Endpoints iniciales

```text
POST /api/v1/auth/google
GET  /api/v1/auth/session
POST /api/v1/auth/logout
```

Tras login, la respuesta devuelve las memberships activas. El cliente selecciona workspace y lo envía mediante `x-workspace-id`; la API vuelve a validar membership.

## Variables

```text
DATABASE_URL
GOOGLE_CLIENT_ID
AUTH_COOKIE_SECURE=true|false
ALLOW_DEV_AUTH_HEADERS=true|false
```

Producción:

```text
AUTH_COOKIE_SECURE=true
ALLOW_DEV_AUTH_HEADERS=false
```

## Pendiente antes de producción

- Google Client ID real y origen autorizado;
- integración del botón Google Identity Services en web;
- CORS/orígenes finales si API y web quedan en hosts distintos;
- política CSRF definitiva si se habilitan escenarios cross-site;
- rotación/gestión de sesiones por dispositivo;
- revocar todas las sesiones;
- email/magic-link alternativo;
- account linking explícito;
- recuperación de cuenta;
- matriz RBAC agrícola;
- roles globales CMS separados;
- auditoría de acciones sensibles;
- rate limiting del login;
- pruebas E2E con navegador real.
