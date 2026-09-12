# V20 — Checklist del primer staging real

Objetivo: ejecutar el primer despliegue externo de V20 sin improvisar y sin tocar `main`.

## Referencia técnica integrada

Último SHA funcional completamente validado:

```text
3a934e979fa6f279316c75a61ac9610432786887
```

Gates verdes de la misma referencia funcional:

- `V20 full candidate check` #2308 ✅
- `V20 beta browser E2E` #620 ✅
- `V20 staging readiness` #221 ✅
- `V20 foundation check` #110 ✅
- `V20 runtime hardening check` #60 ✅
- `V20 platform admin check` #179 ✅
- `V20 notification center check` #39 ✅
- `V20 plans check` #106 ✅
- `V20 Mi Olivo check` #62 ✅
- `V20 environment contract` #127 ✅
- `V20 lockfile guard` #111 ✅
- `V20 visual preview / GitHub Pages` #612 ✅

Los workflows dedicados GIS y Weather/Radar quedaron verdes tras su absorción en la rama coordinadora. Los commits posteriores exclusivamente documentales no modifican producto ni sustituyen esta referencia funcional.

## 1. Host

Debe existir un host de staging separado de producción con:

- Docker Engine;
- Docker Compose v2;
- `curl` y `tar`;
- SSH con clave dedicada;
- almacenamiento persistente para PostgreSQL y backups;
- salida HTTPS hacia S3/R2, Google, AEMET y web-push.

No publicar PostgreSQL a Internet.

Ruta recomendada:

```text
/srv/stacks/magina-olivo-v20-staging
```

## 2. Dominios HTTPS

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

El repositorio no fija aún un hostname real: `deploy/staging/.env.example` conserva dominios de ejemplo.

## 3. Bucket de staging

Crear un bucket exclusivo de staging con credenciales exclusivas.

Para subida documental directa mediante URL prefirmada, configurar CORS para el origen web real:

```text
AllowedOrigin: https://<dominio-web-staging>
AllowedMethod: PUT
AllowedHeaders: Content-Type, x-amz-checksum-sha256
```

Evitar `*`.

## 4. Google Identity

El contrato actual de staging exige Google Identity.

Preparar el mismo client ID en:

```text
GOOGLE_CLIENT_ID
NEXT_PUBLIC_GOOGLE_CLIENT_ID
```

Registrar el dominio web HTTPS de staging entre los **Authorized JavaScript origins**.

No se requiere `GOOGLE_CLIENT_SECRET` en el flujo actual.

## 5. VAPID

El contrato actual exige:

```text
VAPID_PUBLIC_KEY
VAPID_PRIVATE_KEY
VAPID_SUBJECT=mailto:<correo-real>
```

## 6. AEMET

Preparar:

```text
AEMET_API_KEY
```

## 7. `.env` privado

Partir de `deploy/staging/.env.example` y completar todos los placeholders.

Variables críticas actuales:

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

No añadir estas variables obsoletas; el preflight las rechaza:

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

## 8. GitHub Environment `staging`

Secrets usados por `V20 staging deploy`:

```text
STAGING_ENV_FILE
STAGING_SSH_PRIVATE_KEY
STAGING_SSH_KNOWN_HOSTS
```

Variables usadas por el workflow:

```text
STAGING_WEB_URL=https://<dominio-web-staging>
STAGING_HOST=<host-ssh>
STAGING_USER=<usuario-ssh>
STAGING_PORT=<puerto-ssh>
STAGING_PATH=/srv/stacks/magina-olivo-v20-staging
```

`STAGING_WEB_URL` debe ser un origen HTTPS limpio y debe estar incluido en `CORS_ALLOWED_ORIGINS` del `.env`. La API se deriva de `NEXT_PUBLIC_API_URL` y debe usar otro origen HTTPS.

Mantener aprobación manual mientras siga siendo Beta.

## 9. SSH / host

Comprobar:

```bash
ssh -p <STAGING_PORT> <STAGING_USER>@<STAGING_HOST>
docker version
docker compose version
curl --version
tar --version
```

`STAGING_SSH_KNOWN_HOSTS` debe provenir de una huella verificada. No desactivar host-key checking.

## 10. SHA a desplegar

Usar un SHA completo de 40 caracteres cuyos gates requeridos estén verdes. La referencia funcional cerrada es:

```text
3a934e979fa6f279316c75a61ac9610432786887
```

Si se despliega un commit posterior de `integrate/v20-beta-closure`, comprobar que para ese SHA existen en `success` los tres gates que el workflow remoto exige: Full Candidate, Browser E2E y Staging Readiness.

## 11. Lanzar `V20 staging deploy`

El workflow actual tiene exactamente dos inputs:

```text
expected_sha=<SHA completo de 40 caracteres>
confirm=DEPLOY-STAGING
```

No existe input `ref`: el checkout se hace directamente al `expected_sha` y además se comprueba que descienda de `feat/v20-visual-prototype`.

El workflow debe completar:

1. checkout exacto del SHA;
2. validación de Full Candidate + Browser E2E + Staging Readiness para ese SHA;
3. preflight del `.env`;
4. validación de `STAGING_WEB_URL` y separación web/API;
5. SSH/known_hosts;
6. prerequisitos del host;
7. release inmutable con SHA-256 verificado;
8. arranque aislado de PostgreSQL;
9. backup obligatorio **antes de migrar**;
10. build web/API/worker;
11. migraciones;
12. health local de web/API/worker;
13. segunda migración no-op;
14. smoke HTTPS externo;
15. actualización de `current` y `CURRENT_SHA` solo si todo pasa.

Si falla el smoke externo, la release no se marca como actual.

## 12. Backup / restore

El backup se genera con PostgreSQL 17 mediante `pg_dump -Fc`.

Validar/restaurar con cliente PostgreSQL **17**.

Ejemplo:

```bash
docker run --rm \
  -v /ruta/backups:/backups:ro \
  postgres:17-bookworm \
  pg_restore -l /backups/<backup>.dump
```

Antes de aprobar Beta falta ejecutar al menos un **restore controlado real** en staging y confirmar `/ready` después.

## 13. Smoke externo automatizado

Ejecutar desde una máquina externa al host:

```bash
STAGING_API_URL=https://<api-staging> \
STAGING_WEB_ORIGIN=https://<web-staging> \
STAGING_REJECTED_ORIGIN=https://untrusted.invalid \
node scripts/staging-postdeploy-smoke.mjs
```

Valida:

- `/health` y `/ready`;
- base real conectada;
- Google Auth configurado;
- web-push configurado;
- CORS permitido/rechazado;
- headers de seguridad;
- rechazo de cabeceras dev-auth.

Además comprobar:

```text
GET WEB/healthz → ok
GET WEB/        → 200
```

PostgreSQL no debe ser accesible desde Internet.

## 14. Agricultor real

```text
Google login
→ crear finca
→ seleccionar/vincular límites GIS reales
→ volver a editar y recuperar geometría
→ registrar trabajo
→ registrar cosecha
→ añadir rendimiento
→ Campaña
→ subir documento real
→ OCR
→ revisión humana
→ tiempo/radar real
```

Criterios: cero demo, dev-auth desactivado, persistencia tras logout/login, documento en bucket real, OCR por worker y radar/AEMET desde proveedores reales.

## 15. Profesional real

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

El enlace público no debe exponer sesión privada ni datos ajenos.

## 16. Móvil real

Repetir rutas principales en:

```text
360 px
390 px
430 px
```

Comprobar teclado, selects, fechas, mapa, upload, OCR, modales, acciones inferiores, latencia real y ausencia de overflow horizontal.

## 17. Observabilidad

Antes de aprobar staging:

- logs API/worker/deploy retenidos y rotados;
- revisar S3/OCR/AEMET/notificaciones;
- comprobar `X-Request-Id`;
- no registrar cookies, secretos, tokens completos ni documentos sensibles.

## 18. Criterio final

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