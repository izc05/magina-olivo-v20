# V20 — Staging runbook

Estado: runtime de staging reproducible preparado y validado en CI. El último SHA técnico completamente verificado es `76a086717236057027f2b24a06d7942546d5e52c`.

En ese SHA están verdes conjuntamente:

- `V20 full candidate check` #2173;
- `V20 beta browser E2E` #482;
- `V20 staging readiness` #101.

El gate de readiness construye las imágenes Docker de **web + API + worker**, valida Google Identity en el bundle web, Tesseract/Poppler dentro del worker, PostGIS 17, migraciones persistentes, seguridad en producción, health del stack y el camino real de `deploy-host.sh`. También comprueba que el backup pre-migración es un dump PostgreSQL 17 legible y que una segunda ejecución de migraciones es no-op.

Esto no equivale todavía a un staging externo aprobado: siguen pendientes host, dominios HTTPS, secretos y proveedores reales.

## Objetivo

Validar la Beta V20 en un entorno externo antes de cualquier merge a `main`:

- web estática sin preview servida por nginx no-root;
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
```

Seguridad base:

- PostgreSQL no publica puerto al host;
- web y API solo publican en loopback;
- HTTPS se coloca delante mediante Cloudflare Tunnel o reverse proxy;
- API/worker usan filesystem de solo lectura, `tmpfs`, `cap_drop: ALL` y `no-new-privileges`;
- web se compila con `NEXT_PUBLIC_PREVIEW_MODE=false`;
- `NEXT_PUBLIC_API_URL` y `NEXT_PUBLIC_GOOGLE_CLIENT_ID` quedan embebidos en el build;
- API y navegador deben usar el mismo Google OAuth client ID.

## Migraciones

Las migraciones se registran con checksum SHA-256:

1. instalación nueva → aplica y registra cada SQL;
2. despliegue posterior → omite las ya aplicadas;
3. migración histórica modificada → error de checksum;
4. ejecución interrumpida/inconsistente → error antes de continuar.

Readiness #101 valida la primera ejecución y una segunda pasada no-op usando el camino real de despliegue.

## Host

Requisitos:

- Docker Engine;
- Docker Compose v2;
- `curl` y `tar`;
- SSH con clave dedicada;
- almacenamiento persistente para PostgreSQL y backups;
- salida HTTPS hacia S3, Google, AEMET y proveedores web-push.

Ruta recomendada:

```text
/srv/stacks/magina-olivo-v20-staging
```

El host no necesita clonar Git: GitHub Actions sube una release inmutable por SHA.

## GitHub Environment `staging`

Secrets:

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

El preflight exige, entre otros controles:

- `NODE_ENV=production`;
- `NEXT_PUBLIC_PREVIEW_MODE=false`;
- `ALLOW_DEV_AUTH_HEADERS=false`;
- `SESSION_SECRET` de al menos 32 bytes;
- web/API en HTTPS y orígenes distintos;
- `PUBLIC_WEB_ORIGIN` incluido en `CORS_ALLOWED_ORIGINS`;
- `GOOGLE_CLIENT_ID === NEXT_PUBLIC_GOOGLE_CLIENT_ID`;
- bucket exclusivo de staging;
- `WORKER_MODULES=ocr,radar,notifications`;
- OCR `spa+eng`;
- VAPID y AEMET configurados.

Nunca versionar el `.env` real.

## Google Identity

El frontend usa Google Identity Services y obtiene un ID token. La API valida ese token contra el mismo OAuth client ID.

Configurar:

```text
GOOGLE_CLIENT_ID=<id-real>
NEXT_PUBLIC_GOOGLE_CLIENT_ID=<mismo-id-real>
```

No se usa `GOOGLE_CLIENT_SECRET` en el flujo actual.

En Google Cloud, registrar el origen web HTTPS real de staging entre los **Authorized JavaScript origins** del cliente usado por staging. Tras el despliegue comprobar login, recarga con sesión persistente y logout.

## Dominios / sesión

Usar preferiblemente web y API como subdominios del mismo sitio registrable, por ejemplo:

```text
https://staging.example.com      → http://127.0.0.1:8080
https://api-staging.example.com  → http://127.0.0.1:3001
```

Esto conserva el diseño actual de cookie `Secure` + `SameSite=Lax` y `credentials: include` sin relajarla a `SameSite=None`.

`PUBLIC_WEB_ORIGIN`, `NEXT_PUBLIC_API_URL` y `CORS_ALLOWED_ORIGINS` deben coincidir con los orígenes definitivos.

## Storage S3-compatible

Usar un bucket exclusivo de staging y credenciales exclusivas.

La subida documental es directa desde el navegador mediante URL prefirmada. El bucket debe tener una regla CORS restringida al origen web real de staging con:

```text
AllowedOrigin: https://<dominio-web-staging>
AllowedMethod: PUT
AllowedHeaders: Content-Type, x-amz-checksum-sha256
```

No abrir el bucket con `*` salvo que el proveedor lo obligue y se haya revisado el impacto. Verificar además:

- upload prefirmado;
- confirmación/complete upload;
- checksum SHA-256;
- URL temporal de lectura;
- OCR real de PDF/imagen;
- PDF comercial archivado;
- enlaces públicos revocables.

## Backup antes de migrar

`deploy-host.sh` arranca primero PostgreSQL 17 y espera `pg_isready`. **Antes de cualquier migración** crea siempre:

```text
pg_dump -Fc
```

El dump se guarda fuera de la release inmutable en `STAGING_BACKUP_DIR`. Esto protege también una base persistente cuyo contenedor estuviera detenido. En una instalación nueva el backup corresponde al estado inicial vacío, de forma intencionada.

Readiness #101 ejecuta el propio script de host y valida el dump con **`pg_restore` PostgreSQL 17**, la misma major que genera el backup. No validar/restaurar estos dumps con un cliente PostgreSQL más antiguo; CI detectó correctamente que un `pg_restore` 16 no puede leer el header generado por PostgreSQL 17.

Para inspección portable:

```bash
docker run --rm \
  -v /ruta/backups:/backups:ro \
  postgres:17-bookworm \
  pg_restore -l /backups/<backup>.dump
```

No existe restore destructivo automático. Antes de aprobar Beta debe demostrarse al menos un restore controlado de staging usando cliente PostgreSQL 17.

## Despliegue remoto

Workflow manual:

```text
V20 staging deploy
```

Inputs:

```text
ref=<rama/tag/SHA>
expected_sha=<SHA completo de 40 caracteres>
confirm=DEPLOY-STAGING
```

Antes de abrir SSH exige `success` para el mismo SHA exacto de:

```text
V20 full candidate check
V20 beta browser E2E
V20 staging readiness
```

Después:

1. valida SHA y gates;
2. valida `.env`;
3. valida SSH/known_hosts y prerequisitos;
4. sube release inmutable por SHA;
5. arranca PostgreSQL;
6. genera backup pre-migración obligatorio;
7. construye web/API/worker;
8. ejecuta migraciones;
9. arranca stack y comprueba web/API/worker;
10. exige segunda pasada de migraciones no-op;
11. ejecuta smoke HTTPS externo;
12. solo entonces actualiza `current` y `CURRENT_SHA`.

Si falla el smoke externo, la release no se marca como actual.

## Smoke externo

Comprobar desde Internet:

```text
GET WEB/healthz → ok
GET WEB/        → 200
GET API/health  → 200
```

Y verificar:

- certificado HTTPS válido;
- CORS solo para la web configurada;
- `X-Frame-Options: DENY`;
- `X-Content-Type-Options: nosniff`;
- API privada `Cache-Control: no-store`;
- PostgreSQL no accesible externamente.

## Recorrido Agricultor

```text
Google login
→ crear finca
→ añadir/gestionar límites
→ registrar trabajo
→ registrar cosecha
→ añadir rendimiento
→ comprobar Campaña
→ subir documento
→ OCR
→ revisión humana
→ tiempo/radar
```

Criterios: cero datos demo, persistencia real, documento en bucket de staging y OCR ejecutado por el worker desplegado.

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

Repetir las rutas principales en 360 / 390 / 430 px y comprobar teclado, inputs, mapa, upload, OCR, modales, barras inferiores, latencia de red y ausencia de scroll horizontal.

## Observabilidad

Antes de aprobar staging:

- conservar/rotar logs API, worker y deploy;
- revisar errores S3/OCR/AEMET/notificaciones;
- no registrar cookies, secretos, tokens completos ni contenido sensible.

## Criterio de salida

Staging externo se considera aprobado solo cuando estén validados:

- deploy remoto completo;
- HTTPS/CORS/headers;
- Google Auth real;
- bucket + upload + OCR real;
- AEMET/radar;
- VAPID/notificaciones;
- Agricultor real;
- Profesional real;
- móvil real;
- PostgreSQL no expuesto;
- backup + restore;
- observabilidad básica.

Hasta entonces:

> **Beta técnicamente preparada para staging externo; staging real pendiente de validación.**

`main` permanece fuera de este proceso hasta la decisión final del candidato.
