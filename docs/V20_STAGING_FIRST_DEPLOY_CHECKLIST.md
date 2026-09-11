# V20 — Checklist del primer staging real

Objetivo: ejecutar el primer despliegue externo de V20 sin improvisar y sin tocar `main`.

Estado de partida técnico validado: `a88dde53069b5508eaba4ffbbd0ab9dcc6dd34d9` con:

- `V20 full candidate check` #2122 ✅
- `V20 beta browser E2E` #426 ✅
- `V20 staging readiness` #63 ✅

Los commits posteriores deben volver a tener sus tres gates verdes antes de desplegarse.

## 1. Host

Debe existir un host de staging separado de producción con Docker Engine, Docker Compose v2, `curl`, `tar`, SSH con clave dedicada, almacenamiento persistente para PostgreSQL/backups y salida a S3, Google y AEMET.

No abrir PostgreSQL a Internet.

Ruta recomendada:

```text
/srv/stacks/magina-olivo-v20-staging
```

Estructura de releases:

```text
/srv/stacks/magina-olivo-v20-staging/
├── releases/<SHA>/
├── backups/
├── current -> releases/<SHA>
└── CURRENT_SHA
```

## 2. Dominios HTTPS

Definir dos orígenes reales y distintos:

```text
WEB = https://<dominio-staging>
API = https://<api-dominio-staging>
```

El proxy/túnel debe resolver:

```text
WEB → http://127.0.0.1:8080
API → http://127.0.0.1:3001
```

## 3. Bucket de staging

Crear un bucket exclusivo para staging, con credenciales exclusivas y permisos mínimos para uploads, lecturas temporales y OCR. No reutilizar producción.

## 4. Google Identity

El flujo actual usa Google Identity Services: el navegador obtiene un ID token y la API lo valida contra el mismo OAuth client ID.

Preparar estas dos variables con **el mismo valor**:

```text
GOOGLE_CLIENT_ID
NEXT_PUBLIC_GOOGLE_CLIENT_ID
```

`GOOGLE_CLIENT_ID` llega a la API. `NEXT_PUBLIC_GOOGLE_CLIENT_ID` se embebe en la web durante el build y es necesariamente público. El preflight rechaza que sean distintos.

No se usa `GOOGLE_CLIENT_SECRET` en el flujo actual.

Después del deploy probar login, persistencia de sesión y logout.

## 5. Web Push / VAPID

```text
VAPID_PUBLIC_KEY
VAPID_PRIVATE_KEY
VAPID_SUBJECT=mailto:<correo-real>
```

## 6. AEMET

```text
AEMET_API_KEY
```

Después del deploy comprobar tiempo/radar con red real y revisar errores/latencia del worker.

## 7. `.env` privado de staging

Partir de `deploy/staging/.env.example` y completar:

```dotenv
NODE_ENV=production

POSTGRES_USER=magina_staging
POSTGRES_PASSWORD=<PASSWORD_STAGING>
POSTGRES_DB=magina_staging
DATABASE_URL=postgresql://magina_staging:<PASSWORD_URL_ENCODED>@postgres:5432/magina_staging

WEB_PORT=8080
API_PORT=3001

CORS_ALLOWED_ORIGINS=https://<dominio-staging>
NEXT_PUBLIC_API_URL=https://<api-dominio-staging>
NEXT_PUBLIC_PREVIEW_MODE=false

GOOGLE_CLIENT_ID=<CLIENT_ID_REAL>
NEXT_PUBLIC_GOOGLE_CLIENT_ID=<MISMO_CLIENT_ID_REAL>
SESSION_SECRET=<MINIMO_32_BYTES_ALEATORIOS>
ALLOW_DEV_AUTH_HEADERS=false

S3_ENDPOINT=https://<endpoint-real>
S3_REGION=<region-o-auto>
S3_BUCKET=<bucket-exclusivo-staging>
S3_ACCESS_KEY_ID=<REAL>
S3_SECRET_ACCESS_KEY=<REAL>
S3_FORCE_PATH_STYLE=false

WORKER_MODULES=ocr,radar,notifications

OCR_PROVIDER=tesseract
OCR_TESSERACT_LANGUAGES=spa+eng
OCR_MAX_BYTES=20971520
OCR_MAX_PDF_PAGES=12
OCR_TESSERACT_TIMEOUT_MS=120000
OCR_TESSERACT_DPI=200
OCR_TESSERACT_PSM=3

VAPID_PUBLIC_KEY=<REAL>
VAPID_PRIVATE_KEY=<REAL>
VAPID_SUBJECT=mailto:<correo-real>

AEMET_API_KEY=<REAL>
PUBLIC_WEB_ORIGIN=https://<dominio-staging>
```

Antes de subirlo como secret:

```bash
node scripts/staging-env-preflight.mjs deploy/staging/.env
```

Debe terminar con `Staging env preflight passed`.

## 8. GitHub Environment `staging`

### Secrets

```text
STAGING_ENV_FILE
STAGING_SSH_PRIVATE_KEY
STAGING_SSH_KNOWN_HOSTS
```

### Variables

```text
STAGING_HOST
STAGING_USER
STAGING_PORT
STAGING_PATH=/srv/stacks/magina-olivo-v20-staging
```

Mientras V20 siga en Beta, mantener aprobación manual del Environment.

## 9. SSH

Comprobar:

```bash
ssh -p <STAGING_PORT> <STAGING_USER>@<STAGING_HOST>
```

En el host:

```bash
docker version
docker compose version
curl --version
tar --version
```

`STAGING_SSH_KNOWN_HOSTS` debe proceder de una huella verificada; no desactivar la comprobación de host.

## 10. SHA a desplegar

Usar un SHA completo de 40 caracteres que tenga simultáneamente en verde:

```text
V20 full candidate check
V20 beta browser E2E
V20 staging readiness
```

## 11. Lanzar `V20 staging deploy`

Inputs:

```text
ref=<rama/tag/SHA>
expected_sha=<SHA completo>
confirm=DEPLOY-STAGING
```

El workflow debe completar:

1. checkout y SHA exacto;
2. tres gates verdes;
3. preflight del `.env`;
4. validación SSH;
5. prerequisitos del host;
6. release inmutable;
7. backup previo si PostgreSQL ya está activo;
8. build web/API/worker;
9. migraciones;
10. health local;
11. segunda migración no-op;
12. smoke HTTPS externo;
13. actualización de `current` y `CURRENT_SHA`.

Si el smoke externo falla, la release no debe marcarse como actual.

## 12. Smoke externo

```text
GET WEB/healthz → ok
GET WEB/        → 200
GET API/health  → 200
```

Verificar HTTPS, headers de seguridad, CORS restringido, API privada `no-store` y PostgreSQL no accesible desde Internet.

## 13. Recorrido Agricultor real

```text
Google login
→ crear finca
→ añadir límites / mapa
→ registrar trabajo
→ registrar cosecha
→ añadir rendimiento
→ comprobar Campaña
→ subir documento real
→ OCR
→ revisión humana
→ comprobar tiempo/radar
```

Criterios: cero demo, dev-auth desactivado, persistencia tras logout/login, documento en bucket real de staging y OCR ejecutado por el worker desplegado.

## 14. Recorrido Profesional real

```text
crear/abrir cliente
→ presupuesto
→ PDF
→ compartir
→ abrir enlace público en incógnito
→ aceptar/rechazar
→ convertir a trabajo
→ factura
→ registrar cobro
```

Verificar que el enlace público no expone sesión privada ni datos ajenos.

## 15. Móvil real

Repetir rutas principales en 360 / 390 / 430 px y comprobar teclado, inputs, mapa, upload, OCR, modales, botones inferiores, latencia real y ausencia de overflow horizontal.

## 16. Backup y restore

El deploy crea backup previo cuando detecta PostgreSQL de staging activo. Antes de aprobar Beta hay que demostrar también un **restore** de staging al menos una vez.

## 17. Observabilidad

Antes de declarar staging aprobado: logs persistentes/rotados para API, worker y deploy; revisión de errores S3/OCR/AEMET/notificaciones; nunca registrar cookies, secretos, tokens completos ni documentos sensibles.

## 18. Criterio final

Staging real queda aprobado únicamente con workflow remoto, HTTPS/CORS/headers, Google Auth real, bucket+OCR, AEMET/radar, VAPID, recorridos agricultor/profesional, móvil real, PostgreSQL no expuesto, backup+restore y observabilidad básica validados.

Hasta entonces:

> **Beta técnicamente preparada para staging externo; staging real pendiente de validación.**

`main` permanece fuera de este proceso hasta la decisión final del candidato.
