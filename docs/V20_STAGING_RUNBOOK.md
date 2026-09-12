# V20 — Staging runbook

Estado: runtime de staging reproducible preparado y validado en CI.

Referencia funcional integrada completamente verificada:

```text
4a04410686eed936b12d359101f1ff2a0faa6fc0
```

En ese SHA están verdes conjuntamente:

- `V20 full candidate check` #2286;
- `V20 beta browser E2E` #598;
- `V20 staging readiness` #199;
- `V20 GIS finca selector check` #789;
- `V20 weather radar map closure` #13;
- Foundation / Runtime / Admin / Planes / Mi Olivo / Avisos / env / lockfile.

Esto no equivale todavía a staging externo aprobado: siguen pendientes host, dominios HTTPS, secretos y proveedores reales.

## Objetivo

Validar la Beta V20 en un entorno externo antes de cualquier promoción al candidate o merge a `main`:

- web estática sin preview servida por nginx;
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

`deploy/staging/` contiene:

```text
.env.example
Dockerfile.api
Dockerfile.worker
Dockerfile.web
docker-compose.yml
deploy-host.sh
backup-postgres.sh
restore-postgres.sh
migrate.sh
OPERATIONS.md
```

Seguridad base:

- PostgreSQL no publica puerto al host;
- web y API solo publican en loopback;
- HTTPS se coloca delante mediante Cloudflare Tunnel o reverse proxy;
- API/worker usan restricciones de contenedor y logs rotados;
- web se compila con `NEXT_PUBLIC_PREVIEW_MODE=false`;
- `NEXT_PUBLIC_API_URL` y `NEXT_PUBLIC_GOOGLE_CLIENT_ID` quedan embebidos en el build;
- API y navegador deben usar el mismo Google OAuth client ID;
- `/ready` valida conexión real a PostgreSQL;
- cabeceras de identidad de desarrollo se rechazan en producción.

## Migraciones

Las migraciones se registran con checksum SHA-256:

1. instalación nueva → aplica y registra cada SQL;
2. despliegue posterior → omite las ya aplicadas;
3. migración histórica modificada → error de checksum;
4. ejecución interrumpida/inconsistente → error antes de continuar.

Staging Readiness #199 valida primera ejecución, repetibilidad, backup y restore sobre el camino real de despliegue.

## Host

Requisitos:

- Docker Engine;
- Docker Compose v2;
- `curl` y `tar`;
- SSH con clave dedicada;
- almacenamiento persistente para PostgreSQL y backups;
- salida HTTPS hacia S3/R2, Google, AEMET y web-push.

Ruta recomendada:

```text
/srv/stacks/magina-olivo-v20-staging
```

El host no necesita clonar Git: GitHub Actions puede subir una release inmutable por SHA.

## GitHub Environment `staging`

Secrets esperados:

```text
STAGING_ENV_FILE
STAGING_SSH_PRIVATE_KEY
STAGING_SSH_KNOWN_HOSTS
```

Variables:

```text
STAGING_HOST
STAGING_USER
STAGING_PORT
STAGING_PATH=/srv/stacks/magina-olivo-v20-staging
```

Mantener aprobación manual mientras V20 siga en Beta.

## `.env` privado

Partir de `deploy/staging/.env.example`, completar todos los placeholders y ejecutar:

```bash
node scripts/staging-env-preflight.mjs deploy/staging/.env
```

El preflight actual exige:

- `NODE_ENV=production`;
- `NEXT_PUBLIC_PREVIEW_MODE=false`;
- `ALLOW_DEV_AUTH_HEADERS=false`;
- web/API en HTTPS y orígenes distintos;
- `GOOGLE_CLIENT_ID === NEXT_PUBLIC_GOOGLE_CLIENT_ID`;
- storage S3-compatible real y bucket aislado de staging;
- `WORKER_MODULES` con `ocr,radar,notifications`;
- OCR Tesseract `spa+eng`;
- VAPID configurado;
- AEMET configurado.

Estas variables son obsoletas y deben **no aparecer** porque el preflight las rechaza:

```text
GOOGLE_CLIENT_SECRET
SESSION_SECRET
PUBLIC_WEB_ORIGIN
OCR_PROCESSOR_MODE
```

Nunca versionar el `.env` real.

## Google Identity

El frontend usa Google Identity Services y obtiene un ID token. La API valida ese token contra el mismo OAuth client ID.

Configurar:

```text
GOOGLE_CLIENT_ID=<id-real>
NEXT_PUBLIC_GOOGLE_CLIENT_ID=<mismo-id-real>
```

No se usa `GOOGLE_CLIENT_SECRET`.

En Google Cloud, registrar el origen web HTTPS real de staging entre los **Authorized JavaScript origins**. Tras el despliegue comprobar login, recarga con sesión persistente y logout.

## Dominios / sesión

Usar web y API como orígenes HTTPS distintos, preferiblemente subdominios del mismo dominio registrable:

```text
https://<web-staging>      → http://127.0.0.1:8080
https://<api-staging>      → http://127.0.0.1:3001
```

Configurar:

```text
CORS_ALLOWED_ORIGINS=https://<web-staging>
NEXT_PUBLIC_API_URL=https://<api-staging>
AUTH_COOKIE_SECURE=true
```

No existe `PUBLIC_WEB_ORIGIN` en el contrato actual.

## Storage S3-compatible

Usar un bucket exclusivo de staging y credenciales exclusivas.

La subida documental es directa desde navegador mediante URL prefirmada. El bucket debe tener CORS restringido al origen web real:

```text
AllowedOrigin: https://<web-staging>
AllowedMethod: PUT
AllowedHeaders: Content-Type, x-amz-checksum-sha256
```

Verificar:

- upload prefirmado;
- confirmación/complete upload;
- checksum SHA-256;
- URL temporal de lectura;
- OCR real de PDF/imagen;
- PDF comercial archivado;
- enlaces públicos revocables;
- lectura del asset radar por el worker/API.

## Backup antes de migrar

`deploy-host.sh` arranca PostgreSQL 17 y espera `pg_isready`. Antes de migrar crea un backup `pg_dump -Fc` cuando corresponde.

Los backups viven fuera de la release inmutable. Validar/restaurar con PostgreSQL **17**.

Inspección portable:

```bash
docker run --rm \
  -v /ruta/backups:/backups:ro \
  postgres:17-bookworm \
  pg_restore -l /backups/<backup>.dump
```

Antes de aprobar Beta debe demostrarse al menos un restore controlado real y comprobar `/ready` después.

## Despliegue remoto

Workflow manual esperado:

```text
V20 staging deploy
```

Inputs:

```text
ref=<rama/tag/SHA>
expected_sha=<SHA completo de 40 caracteres>
confirm=DEPLOY-STAGING
```

Antes de abrir SSH debe exigir `success` para el mismo SHA exacto de los gates de cierre configurados.

Después:

1. valida SHA y gates;
2. valida `.env`;
3. valida SSH/known_hosts y prerequisitos;
4. sube release inmutable por SHA;
5. arranca PostgreSQL;
6. genera backup pre-migración;
7. construye web/API/worker;
8. ejecuta migraciones;
9. arranca stack y comprueba web/API/worker;
10. exige segunda pasada de migraciones no-op;
11. ejecuta smoke HTTPS externo;
12. solo entonces actualiza `current` y `CURRENT_SHA`.

Si falla el smoke externo, la release no se marca como actual.

## Smoke externo automatizado

Ejecutar desde una máquina externa al host:

```bash
STAGING_API_URL=https://<api-staging> \
STAGING_WEB_ORIGIN=https://<web-staging> \
STAGING_REJECTED_ORIGIN=https://untrusted.invalid \
node scripts/staging-postdeploy-smoke.mjs
```

Valida:

- `/health`;
- `/ready` con base viva;
- Google Auth configurado;
- web-push configurado;
- CORS allow/deny;
- headers de seguridad;
- rechazo de dev-auth.

Además comprobar:

```text
GET WEB/healthz → ok
GET WEB/        → 200
```

Y confirmar que PostgreSQL no es accesible externamente.

## Recorrido Agricultor

```text
Google login
→ crear finca
→ seleccionar/vincular geometría GIS real
→ volver a editar y recuperar geometría
→ registrar trabajo
→ registrar cosecha
→ añadir rendimiento
→ Campaña
→ subir documento
→ OCR
→ revisión humana
→ tiempo/radar real
```

Criterios: cero datos demo, persistencia real, documento en bucket real y OCR ejecutado por el worker desplegado.

## Recorrido Profesional

```text
cliente
→ presupuesto
→ PDF
→ compartir
→ abrir enlace público en incógnito
→ aceptar/rechazar
→ convertir a trabajo
→ factura
→ registrar cobro
```

El enlace público no debe exponer sesión privada ni datos ajenos.

## Móvil

Repetir rutas principales en 360 / 390 / 430 px y comprobar teclado, inputs, mapa, upload, OCR, modales, barras inferiores, latencia real y ausencia de scroll horizontal.

## Observabilidad

Antes de aprobar staging:

- conservar/rotar logs API, worker y deploy;
- revisar errores S3/OCR/AEMET/notificaciones;
- usar `X-Request-Id` para correlación;
- no registrar cookies, secretos, tokens completos ni contenido sensible.

## Criterio de salida

Staging externo se considera aprobado solo cuando estén validados:

- deploy remoto completo;
- HTTPS/CORS/headers;
- Google Auth real;
- bucket + upload + OCR real;
- AEMET/radar real;
- VAPID/notificaciones;
- Agricultor real;
- Profesional real;
- móvil real;
- PostgreSQL no expuesto;
- backup + restore;
- observabilidad básica.

Hasta entonces:

> **Beta funcionalmente integrada y preparada para staging externo; staging real pendiente de validación.**

`main` permanece fuera de este proceso hasta la decisión final del candidato.
