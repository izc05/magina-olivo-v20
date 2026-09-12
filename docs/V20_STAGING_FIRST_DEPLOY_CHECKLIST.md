# V20 — Checklist del primer staging real

Objetivo: ejecutar el primer despliegue externo de V20 sin improvisar y sin tocar `main`.

## 1. Elegir el SHA exacto

No usar como candidato un SHA histórico pegado en documentación. El despliegue debe elegir explícitamente un SHA completo de `integrate/v20-beta-closure` y comprobar que para **ese mismo SHA** están verdes:

- `V20 full candidate check`;
- `V20 beta browser E2E`;
- `V20 staging readiness`;
- `V20 visual preview / GitHub Pages`.

El workflow remoto vuelve a exigir los tres primeros antes de tocar el host. PR #58 mantiene el estado vivo de la rama coordinadora.

## 2. GitHub Environment `staging`

Secrets:

```text
STAGING_ENV_FILE
STAGING_SSH_PRIVATE_KEY
STAGING_SSH_KNOWN_HOSTS
```

Variables:

```text
STAGING_WEB_URL=https://<dominio-web-staging>
STAGING_HOST=<host-ssh>
STAGING_USER=<usuario-ssh>
STAGING_PORT=<puerto-ssh>
STAGING_PATH=/srv/stacks/magina-olivo-v20-staging
```

El controlador comprueba primero la presencia de los ocho nombres. Si falta alguno, los enumera en una sola ejecución sin imprimir sus valores.

`STAGING_WEB_URL` debe ser un origen HTTPS limpio incluido en `CORS_ALLOWED_ORIGINS`. La API sale de `NEXT_PUBLIC_API_URL` y debe usar otro origen HTTPS.

No copiar secretos a Git, PRs, issues ni chats.

## 3. Host

Debe existir un host de staging separado de producción con:

- Docker Engine;
- Docker Compose v2;
- `curl`, `tar` y `sha256sum`;
- SSH con clave dedicada;
- almacenamiento persistente para PostgreSQL y backups;
- salida HTTPS hacia S3/R2, Google, AEMET y web-push.

No publicar PostgreSQL a Internet.

Ruta recomendada:

```text
/srv/stacks/magina-olivo-v20-staging
```

## 4. Dominios HTTPS

Usar web y API en orígenes HTTPS distintos:

```text
WEB = https://<dominio-staging>
API = https://<api-dominio-staging>
```

Proxy/túnel:

```text
WEB → http://127.0.0.1:8080
API → http://127.0.0.1:3001
```

No inventar un hostname: el repositorio conserva únicamente ejemplos.

## 5. Bucket de staging

Crear un bucket exclusivo y credenciales exclusivas. Para upload directo mediante URL prefirmada, restringir CORS al origen web real:

```text
AllowedOrigin: https://<dominio-web-staging>
AllowedMethod: PUT
AllowedHeaders: Content-Type, x-amz-checksum-sha256
```

Evitar `*`.

## 6. Google Identity, VAPID y AEMET

Google Identity:

```text
GOOGLE_CLIENT_ID
NEXT_PUBLIC_GOOGLE_CLIENT_ID
```

Ambos deben usar el mismo client ID y el origen web HTTPS de staging debe estar registrado en Google Cloud. No se usa `GOOGLE_CLIENT_SECRET`.

VAPID:

```text
VAPID_PUBLIC_KEY
VAPID_PRIVATE_KEY
VAPID_SUBJECT=mailto:<correo-real>
```

AEMET:

```text
AEMET_API_KEY
```

## 7. `.env` privado

Partir de `deploy/staging/.env.example`. Valores críticos:

```dotenv
NODE_ENV=production
POSTGRES_USER=magina_staging
POSTGRES_PASSWORD=<PASSWORD>
POSTGRES_DB=magina_staging
DATABASE_URL=postgresql://magina_staging:<PASSWORD_URL_ENCODED>@postgres:5432/magina_staging
WEB_PORT=8080
API_PORT=3001
HOST=0.0.0.0
PORT=3001
CORS_ALLOWED_ORIGINS=https://<dominio-staging>
NEXT_PUBLIC_API_URL=https://<api-dominio-staging>
NEXT_PUBLIC_PREVIEW_MODE=false
GOOGLE_CLIENT_ID=<CLIENT_ID_REAL>
NEXT_PUBLIC_GOOGLE_CLIENT_ID=<MISMO_CLIENT_ID_REAL>
AUTH_COOKIE_SECURE=true
ALLOW_DEV_AUTH_HEADERS=false
S3_ENDPOINT=https://<endpoint-real>
S3_REGION=<region-o-auto>
S3_BUCKET=<bucket-exclusivo-staging>
S3_ACCESS_KEY_ID=<REAL>
S3_SECRET_ACCESS_KEY=<REAL>
S3_FORCE_PATH_STYLE=false
S3_PREFIX=private-documents
RADAR_S3_PREFIX=weather/radar
WORKER_MODULES=ocr,radar,notifications
OCR_PROVIDER=tesseract
OCR_TESSERACT_LANGUAGES=spa+eng
VAPID_PUBLIC_KEY=<REAL>
VAPID_PRIVATE_KEY=<REAL>
VAPID_SUBJECT=mailto:<correo-real>
AEMET_API_KEY=<REAL>
```

No añadir variables obsoletas:

```text
GOOGLE_CLIENT_SECRET
SESSION_SECRET
PUBLIC_WEB_ORIGIN
OCR_PROCESSOR_MODE
```

Validar:

```bash
node scripts/staging-env-preflight.mjs deploy/staging/.env
```

Debe terminar con `Staging env preflight passed`.

## 8. SSH / host

Comprobar:

```bash
ssh -p <STAGING_PORT> <STAGING_USER>@<STAGING_HOST>
docker version
docker compose version
curl --version
tar --version
sha256sum --version
```

`STAGING_SSH_KNOWN_HOSTS` debe venir de una huella verificada. No desactivar host-key checking.

## 9. Lanzar `V20 staging deploy`

Inputs:

```text
expected_sha=<SHA completo de 40 caracteres>
confirm=DEPLOY-STAGING
```

También existe una vía controlada por `deploy/staging/STAGING_DEPLOY_REQUEST.json` en la rama coordinadora.

El workflow debe completar:

1. resolver solicitud y checkout exacto;
2. validar ascendencia y gates del SHA;
3. comprobar toda la configuración privada requerida;
4. preflight del `.env` y separación web/API;
5. SSH/known_hosts y prerequisitos;
6. release inmutable con SHA-256 verificado;
7. PostgreSQL aislado;
8. backup obligatorio antes de migrar;
9. build web/API/worker;
10. migraciones y segunda pasada no-op;
11. health local de web/API/worker;
12. smoke HTTPS externo;
13. actualizar `current` y `CURRENT_SHA` **solo si todo pasa**.

Si el smoke externo falla, la release no se marca como actual.

## 10. Backup / restore

El backup usa PostgreSQL 17 y `pg_dump -Fc`. Antes de aprobar Beta ejecutar al menos un restore controlado real con PostgreSQL 17 y confirmar `/ready` después.

## 11. Smoke externo automatizado

```bash
STAGING_API_URL=https://<api-staging> \
STAGING_WEB_ORIGIN=https://<web-staging> \
STAGING_REJECTED_ORIGIN=https://untrusted.invalid \
node scripts/staging-postdeploy-smoke.mjs
```

Debe validar `/health`, `/ready`, DB real, Google Auth, web-push, CORS, headers de seguridad y rechazo de dev-auth. Además:

```text
GET WEB/healthz → ok
GET WEB/        → 200
```

## 12. Recorridos reales

Agricultor:

```text
Google login
→ crear finca
→ seleccionar/vincular límites GIS reales
→ volver a editar y recuperar geometría
→ trabajo
→ cosecha
→ rendimiento
→ Campaña
→ documento real
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
→ convertir a trabajo
→ factura
→ cobro
```

## 13. Móvil y observabilidad

Repetir rutas principales en 360 / 390 / 430 px y escritorio. Comprobar teclado, selects, fechas, mapa, upload, OCR, modales, acciones inferiores, latencia y ausencia de overflow horizontal.

Antes de aprobar staging:

- logs API/worker/deploy retenidos y rotados;
- revisar S3/OCR/AEMET/notificaciones;
- comprobar `X-Request-Id`;
- no registrar cookies, secretos, tokens completos ni documentos sensibles.

## 14. Criterio final

Staging externo queda aprobado únicamente con:

- workflow remoto completo ✅
- HTTPS/CORS/headers ✅
- Google Auth real ✅
- bucket + upload + OCR real ✅
- AEMET/radar real ✅
- VAPID/notificaciones ✅
- Agricultor real ✅
- Profesional real ✅
- móvil real ✅
- PostgreSQL no expuesto ✅
- backup + restore probado ✅
- observabilidad básica ✅

Hasta entonces:

> **Beta funcionalmente integrada y preparada para staging externo; staging real pendiente de validación.**

`main` permanece fuera de este proceso hasta la decisión final del candidato.
