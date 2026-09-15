# V20 — Staging runbook

Estado: runtime de staging reproducible preparado y validado en CI; staging externo pendiente de configuración/validación real.

## Fuente de verdad del SHA

No fijar aquí un “último SHA” ni números de CI que caduquen. El candidato se selecciona explícitamente en cada ejecución mediante `expected_sha` y debe pertenecer a `integrate/v20-beta-closure`.

Para ese mismo SHA deben existir en `success`:

```text
V20 full candidate check
V20 beta browser E2E
V20 staging readiness
```

Antes de promover también debe estar verde `V20 visual preview / GitHub Pages` y el resto de checks del PR. PR #58 mantiene el estado vivo.

## Objetivo

Validar la Beta V20 en un entorno externo antes de cualquier promoción o merge a `main`:

- web sin preview servida por nginx;
- API Fastify real;
- PostgreSQL 17 + PostGIS;
- worker OCR/radar/notificaciones;
- storage S3-compatible;
- Google Identity;
- AEMET/radar;
- documentos/OCR;
- web push;
- enlaces comerciales públicos.

Staging no usa datos demo ni `ALLOW_DEV_AUTH_HEADERS`.

## Runtime

`deploy/staging/` contiene los Dockerfiles, `docker-compose.yml`, `.env.example`, scripts de deploy/migración/backup/restore y documentación de operaciones.

Seguridad base:

- PostgreSQL no publica puerto al host;
- web/API solo publican en loopback;
- HTTPS se sitúa delante mediante túnel/reverse proxy;
- API/worker usan restricciones de contenedor y logs rotados;
- web compila con `NEXT_PUBLIC_PREVIEW_MODE=false`;
- API y navegador comparten Google OAuth client ID;
- `/ready` valida PostgreSQL real;
- cabeceras de identidad de desarrollo se rechazan en producción.

## GitHub Environment `staging`

Secrets:

```text
STAGING_ENV_FILE
STAGING_SSH_PRIVATE_KEY
STAGING_SSH_KNOWN_HOSTS
```

Variables:

```text
STAGING_WEB_URL=https://<web-staging>
STAGING_HOST=<host-ssh>
STAGING_USER=<usuario-ssh>
STAGING_PORT=<puerto-ssh>
STAGING_PATH=/srv/stacks/magina-olivo-v20-staging
```

El workflow ejecuta primero `Validate staging configuration presence`: revisa los ocho nombres y enumera todos los ausentes sin imprimir valores. Esto evita descubrir configuración faltante de una en una.

`STAGING_WEB_URL` debe ser un origen HTTPS limpio incluido en `CORS_ALLOWED_ORIGINS`. La API se obtiene de `NEXT_PUBLIC_API_URL` y debe usar otro origen HTTPS.

Mantener aprobación manual mientras V20 siga en Beta.

## `.env` privado

Partir de `deploy/staging/.env.example` y ejecutar:

```bash
node scripts/staging-env-preflight.mjs deploy/staging/.env
```

El preflight exige producción real, preview/dev-auth desactivados, web/API HTTPS separados, Google Identity, storage S3-compatible, worker `ocr,radar,notifications`, OCR Tesseract `spa+eng`, VAPID y AEMET.

No usar variables obsoletas:

```text
GOOGLE_CLIENT_SECRET
SESSION_SECRET
PUBLIC_WEB_ORIGIN
OCR_PROCESSOR_MODE
```

Nunca versionar el `.env` real.

## Host

Requisitos:

- Docker Engine;
- Docker Compose v2;
- `curl`, `tar`, `sha256sum`;
- SSH con clave dedicada y known_hosts verificado;
- almacenamiento persistente para PostgreSQL/backups;
- salida HTTPS hacia S3/R2, Google, AEMET y web-push.

Ruta recomendada:

```text
/srv/stacks/magina-olivo-v20-staging
```

El host no necesita clonar Git: GitHub Actions sube una release inmutable por SHA.

## Despliegue remoto

Workflow:

```text
V20 staging deploy
```

Inputs:

```text
expected_sha=<SHA completo de 40 caracteres>
confirm=DEPLOY-STAGING
```

También puede activarse de forma controlada mediante `deploy/staging/STAGING_DEPLOY_REQUEST.json` en la rama coordinadora.

Flujo:

1. resolver solicitud y checkout exacto;
2. comprobar ascendencia del SHA;
3. exigir Full Candidate + Browser E2E + Staging Readiness del SHA;
4. comprobar presencia de toda la configuración privada;
5. validar `.env`, CORS y separación web/API;
6. validar SSH/known_hosts y prerequisitos;
7. crear y subir release inmutable;
8. verificar SHA-256 del archive en host;
9. arrancar PostgreSQL;
10. crear backup pre-migración;
11. construir web/API/worker;
12. ejecutar migraciones y segunda pasada no-op;
13. comprobar salud local;
14. ejecutar smoke HTTPS externo;
15. solo entonces actualizar `current` y `CURRENT_SHA`.

Si el smoke externo falla, la release no se marca como actual.

## Migraciones y backup/restore

Las migraciones se registran con checksum SHA-256. Una migración histórica modificada o una ejecución inconsistente debe fallar antes de continuar.

Los backups viven fuera de la release y usan PostgreSQL 17 (`pg_dump -Fc`). Antes de aprobar Beta debe demostrarse un restore controlado real y comprobar `/ready` después.

## Google Identity

Configurar el mismo ID en:

```text
GOOGLE_CLIENT_ID
NEXT_PUBLIC_GOOGLE_CLIENT_ID
```

No se usa `GOOGLE_CLIENT_SECRET`. Registrar el origen HTTPS real entre los Authorized JavaScript origins y comprobar login, recarga y logout.

## Storage S3-compatible

Usar bucket y credenciales exclusivos de staging. Restringir CORS al origen web real y validar upload prefirmado, checksum, lectura temporal, OCR, PDF comercial, enlaces públicos y asset radar.

## Smoke externo automatizado

```bash
STAGING_API_URL=https://<api-staging> \
STAGING_WEB_ORIGIN=https://<web-staging> \
STAGING_REJECTED_ORIGIN=https://untrusted.invalid \
node scripts/staging-postdeploy-smoke.mjs
```

Valida `/health`, `/ready`, base viva, Google Auth, web-push, CORS allow/deny, headers de seguridad y rechazo de dev-auth.

También comprobar:

```text
GET WEB/healthz → ok
GET WEB/        → 200
```

PostgreSQL no debe ser accesible externamente.

## Recorridos de aceptación

Agricultor:

```text
Google login
→ crear finca
→ geometría GIS real
→ editar y recuperar geometría
→ trabajo
→ cosecha
→ rendimiento
→ Campaña
→ documento
→ OCR
→ revisión humana
→ tiempo/radar real
```

Profesional:

```text
cliente
→ presupuesto
→ PDF
→ compartir
→ abrir enlace público en incógnito
→ aceptar/rechazar
→ trabajo
→ factura
→ cobro
```

## Móvil y observabilidad

Repetir rutas principales en 360 / 390 / 430 px y escritorio. Revisar overflow, teclado, inputs, mapa, upload/OCR, modales y navegación.

Conservar/rotar logs API, worker y deploy; correlacionar con `X-Request-Id`; no registrar cookies, secretos, tokens completos ni documentos sensibles.

## Criterio de salida

Staging externo se considera aprobado solo cuando estén validados deploy remoto, HTTPS/CORS/headers, Google Auth, bucket/OCR, AEMET/radar, VAPID, recorridos Agricultor/Profesional, móvil, PostgreSQL no expuesto, backup/restore y observabilidad básica.

Hasta entonces:

> **Beta funcionalmente integrada y preparada para staging externo; staging real pendiente de validación.**

`main` permanece fuera de este proceso hasta la decisión final del candidato.
