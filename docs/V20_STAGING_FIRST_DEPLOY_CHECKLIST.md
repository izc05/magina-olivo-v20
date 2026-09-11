# V20 — Checklist del primer staging real

Objetivo: ejecutar el primer despliegue externo de V20 sin improvisar y sin tocar `main`.

Estado técnico de referencia: el runtime de Foundation `7d663b6e747abe201a5ffe6ca65e07dd8fa35ba5` quedó validado conjuntamente por:

- `V20 environment contract` #49 ✅
- `V20 lockfile guard` #41 ✅
- `V20 foundation check` #32 ✅
- `V20 full candidate check` #2129 ✅
- `V20 beta browser E2E` #435 ✅
- `V20 staging readiness` #70 ✅

Para desplegar un SHA, el workflow exige que ese **SHA exacto** pertenezca al historial actual de `feat/v20-visual-prototype` y tenga verdes candidate, browser E2E y staging readiness.

## 1. Host

Debe existir un host de staging separado de producción con:

- Docker Engine;
- Docker Compose v2;
- `curl`;
- `tar`;
- SSH con clave dedicada;
- espacio persistente para PostgreSQL y backups;
- acceso saliente a S3, Google y AEMET;
- web/API publicables mediante Cloudflare Tunnel o reverse proxy HTTPS.

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
WEB → http://127.0.0.1:<WEB_PORT>
API → http://127.0.0.1:<API_PORT>
```

No ejecutar el deploy real mientras los dominios sigan siendo placeholders.

## 3. Bucket de staging

Crear un bucket exclusivo de staging:

- no reutilizar producción;
- credenciales exclusivas;
- endpoint HTTPS;
- permisos mínimos para upload/read temporal/OCR;
- política de retención definida antes de usar documentos reales.

## 4. Google Auth

Crear/configurar un OAuth Web Client ID para staging y usar el mismo valor en:

```text
GOOGLE_CLIENT_ID
NEXT_PUBLIC_GOOGLE_CLIENT_ID
```

El runtime actual valida Google mediante ID token. **No usa `GOOGLE_CLIENT_SECRET` ni `SESSION_SECRET` y esas claves están prohibidas por el contrato de staging.**

Después del deploy probar login, persistencia de sesión y logout.

## 5. Web Push / VAPID

Preparar:

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

Después del deploy comprobar tiempo/radar con red real y revisar errores/latencia del worker.

## 7. `.env` privado de staging

Partir de `deploy/staging/.env.example` y completar todos los placeholders. Como mínimo revisar:

```dotenv
NODE_ENV=production
POSTGRES_USER=magina_staging
POSTGRES_PASSWORD=<PASSWORD_STAGING>
POSTGRES_DB=magina_staging
DATABASE_URL=postgresql://magina_staging:<PASSWORD_URL_ENCODED>@postgres:5432/magina_staging

WEB_PORT=8080
API_PORT=3001
HOST=0.0.0.0
PORT=3001

CORS_ALLOWED_ORIGINS=https://<dominio-staging>
NEXT_PUBLIC_API_URL=https://<api-dominio-staging>
NEXT_PUBLIC_PREVIEW_MODE=false

GOOGLE_CLIENT_ID=<REAL>
NEXT_PUBLIC_GOOGLE_CLIENT_ID=<MISMO_REAL>
AUTH_COOKIE_SECURE=true
ALLOW_DEV_AUTH_HEADERS=false

S3_ENDPOINT=https://<endpoint-real>
S3_REGION=<region-o-auto>
S3_BUCKET=<bucket-exclusivo-staging>
S3_ACCESS_KEY_ID=<REAL>
S3_SECRET_ACCESS_KEY=<REAL>
S3_FORCE_PATH_STYLE=false
S3_PREFIX=private-documents

WORKER_MODULES=ocr,radar,notifications
OCR_PROVIDER=tesseract
OCR_TESSERACT_LANGUAGES=spa+eng
OCR_MAX_BYTES=20971520
OCR_MAX_PDF_PAGES=12
OCR_TESSERACT_TIMEOUT_MS=120000
OCR_TESSERACT_DPI=200
OCR_TESSERACT_PSM=3
TESSERACT_BIN=tesseract
PDFINFO_BIN=pdfinfo
PDFTOPPM_BIN=pdftoppm

VAPID_PUBLIC_KEY=<REAL>
VAPID_PRIVATE_KEY=<REAL>
VAPID_SUBJECT=mailto:<correo-real>
AEMET_API_KEY=<REAL>
```

No añadir:

```text
GOOGLE_CLIENT_SECRET
SESSION_SECRET
PUBLIC_WEB_ORIGIN
OCR_PROCESSOR_MODE
```

Antes de cargar el archivo como secret:

```bash
node scripts/staging-env-preflight.mjs deploy/staging/.env
```

Debe terminar con `Staging env preflight passed`.

## 8. GitHub Environment `staging`

Crear/proteger el Environment `staging`.

### Secrets requeridos

```text
STAGING_ENV_FILE
STAGING_SSH_PRIVATE_KEY
STAGING_SSH_KNOWN_HOSTS
```

### Variables requeridas

```text
STAGING_HOST
STAGING_USER
STAGING_PORT
STAGING_PATH=/srv/stacks/magina-olivo-v20-staging
STAGING_WEB_URL=https://<dominio-staging>
```

`STAGING_WEB_URL` es una variable de **orquestación del deploy**, no una variable runtime de la app. El workflow exige que ese origen esté incluido en `CORS_ALLOWED_ORIGINS` y que sea distinto del origen API.

Mientras siga siendo Beta, mantener aprobación manual del Environment.

## 9. SSH

Comprobar manualmente:

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

`STAGING_SSH_KNOWN_HOSTS` debe proceder de una huella verificada. No usar `StrictHostKeyChecking=no`.

## 10. SHA a desplegar

Usar un SHA completo de 40 caracteres que pertenezca al candidate actual y tenga simultáneamente en verde:

```text
V20 full candidate check
V20 beta browser E2E
V20 staging readiness
```

El workflow rechaza un SHA ajeno al historial del candidate aunque tenga runs previos.

## 11. Lanzar `V20 staging deploy`

Inputs:

```text
expected_sha=<SHA completo>
confirm=DEPLOY-STAGING
```

El workflow debe pasar, en orden:

1. validar formato del SHA;
2. checkout exacto;
3. comprobar que es ancestro del candidate actual;
4. exigir los tres gates verdes para ese SHA;
5. validar `.env` privado;
6. comprobar coherencia `STAGING_WEB_URL` ↔ CORS ↔ API;
7. validar SSH y `known_hosts`;
8. comprobar prerequisitos del host;
9. subir release inmutable;
10. instalar `.env` con permisos restrictivos;
11. ejecutar `deploy-host.sh`;
12. arrancar PostgreSQL y esperar readiness;
13. crear **siempre** backup `pg_dump -Fc` antes de migrar;
14. build web/API/worker;
15. migraciones + health local;
16. segunda ejecución de migraciones no-op + registry coherente;
17. smoke HTTPS externo;
18. solo entonces actualizar `current` y `CURRENT_SHA`.

Si el smoke externo falla, la release **no debe marcarse como actual**.

## 12. Smoke externo obligatorio

```text
GET WEB/healthz → ok
GET WEB/        → 200
GET API/health  → 200
```

Verificar desde Internet:

- HTTPS válido;
- `X-Frame-Options: DENY`;
- `X-Content-Type-Options: nosniff`;
- `/documento-publico` con `Cache-Control: no-store` y `X-Robots-Tag`;
- CORS permite únicamente la web configurada;
- API privada usa `Cache-Control: no-store`;
- dev-auth no funciona en producción;
- PostgreSQL no es accesible externamente.

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

Criterios:

- cero datos demo;
- cero dev-auth;
- finca/registros sobreviven a logout/login;
- documento persiste en bucket de staging;
- OCR usa el worker desplegado.

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

Repetir rutas principales en:

```text
360 px
390 px
430 px
```

Comprobar teclado móvil, inputs, mapa, upload cámara/archivo, OCR/revisión, botones inferiores, latencia y ausencia de overflow horizontal.

## 16. Backup y restore

El deploy crea **siempre un dump previo a las migraciones** después de arrancar PostgreSQL y antes de levantar el resto del stack.

Antes de aprobar Beta hay que demostrar también un **restore** al menos una vez usando un dump de staging verificado.

Nunca probar restore destructivo sobre producción.

## 17. Observabilidad

Antes de declarar staging aprobado:

- conservar logs API/worker/deploy;
- configurar rotación/persistencia;
- revisar errores repetitivos del worker;
- revisar fallos S3/OCR/AEMET/notificaciones;
- no registrar cookies, secretos, tokens completos ni contenido sensible.

## 18. Criterio final

Staging real queda aprobado únicamente si:

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

Hasta entonces el estado correcto es:

> **Beta técnicamente preparada para staging externo; staging real pendiente de validación.**

`main` permanece fuera de este proceso hasta la decisión final del candidate.
