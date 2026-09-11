# V20 — Checklist del primer staging real

Objetivo: ejecutar el primer despliegue externo de V20 sin improvisar y sin tocar `main`.

Estado de partida técnico validado: `a88dde53069b5508eaba4ffbbd0ab9dcc6dd34d9` con:

- `V20 full candidate check` #2122 ✅
- `V20 beta browser E2E` #426 ✅
- `V20 staging readiness` #63 ✅

Los commits posteriores que solo actualicen documentación no cambian ese runtime. Para desplegar otro SHA distinto, el workflow exige que ese SHA tenga sus tres gates verdes.

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

Ruta recomendada del stack:

```text
/srv/stacks/magina-olivo-v20-staging
```

Estructura creada por el deploy:

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
WEB  = https://<dominio-staging>
API  = https://<api-dominio-staging>
```

El proxy/túnel debe resolver:

```text
WEB → http://127.0.0.1:8080
API → http://127.0.0.1:3001
```

No ejecutar el deploy real mientras los dos dominios sigan siendo placeholders.

## 3. Bucket de staging

Crear un bucket exclusivo para staging.

Requisitos:

- no reutilizar producción;
- credenciales exclusivas de staging;
- endpoint HTTPS;
- permisos mínimos necesarios para uploads, reads temporales y OCR;
- política de retención/revisión definida antes de usar documentos reales.

## 4. Google Auth

Crear/configurar credenciales de staging con los dominios definitivos.

Preparar:

```text
GOOGLE_CLIENT_ID
GOOGLE_CLIENT_SECRET
```

Después del deploy probar login, persistencia de sesión y logout.

## 5. Web Push / VAPID

Preparar:

```text
VAPID_PUBLIC_KEY
VAPID_PRIVATE_KEY
VAPID_SUBJECT=mailto:<correo-real>
```

El preflight rechaza un `VAPID_SUBJECT` que no sea `mailto:` válido.

## 6. AEMET

Preparar una clave real de staging/desarrollo:

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

GOOGLE_CLIENT_ID=<REAL>
GOOGLE_CLIENT_SECRET=<REAL>
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

Antes de subirlo como secret, ejecutar:

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

`STAGING_ENV_FILE` contiene el `.env` completo; nunca se versiona.

### Variables requeridas

```text
STAGING_HOST
STAGING_USER
STAGING_PORT
STAGING_PATH=/srv/stacks/magina-olivo-v20-staging
```

Recomendación mientras siga siendo Beta: approval manual obligatorio para el Environment.

## 9. SSH

Antes del primer deploy comprobar manualmente:

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

`STAGING_SSH_KNOWN_HOSTS` debe proceder de una huella verificada, no de aceptar `StrictHostKeyChecking=no`.

## 10. SHA a desplegar

Usar un SHA completo de 40 caracteres que tenga simultáneamente en verde:

```text
V20 full candidate check
V20 beta browser E2E
V20 staging readiness
```

El workflow rechazará cualquier SHA sin los tres resultados `success`.

## 11. Lanzar `V20 staging deploy`

Inputs:

```text
ref=<rama/tag/SHA>
expected_sha=<SHA completo>
confirm=DEPLOY-STAGING
```

El workflow debe pasar, en orden:

1. checkout exacto;
2. comprobación del SHA;
3. comprobación de los tres gates;
4. preflight del `.env`;
5. validación SSH;
6. prerequisitos del host;
7. subida de release inmutable;
8. backup previo si PostgreSQL ya está activo;
9. build web/API/worker;
10. migraciones;
11. health local web/API;
12. segunda ejecución de migraciones no-op;
13. smoke HTTPS externo;
14. actualización de `current` y `CURRENT_SHA`.

Si el smoke externo falla, la release **no debe marcarse como actual**.

## 12. Smoke externo obligatorio

### Web/API

```text
GET WEB/healthz → ok
GET WEB/        → 200
GET API/health  → 200
```

Verificar desde Internet:

- HTTPS válido;
- `X-Frame-Options: DENY`;
- `X-Content-Type-Options: nosniff`;
- CORS permite únicamente la web configurada;
- API privada usa `Cache-Control: no-store`;
- no existe acceso externo a PostgreSQL.

## 13. Recorrido Agricultor real

Ejecutar con usuario de staging:

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
- cero `ALLOW_DEV_AUTH_HEADERS`;
- finca y registros sobreviven a logout/login;
- documento se persiste en el bucket de staging;
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

Repetir las rutas principales en al menos:

```text
360 px
390 px
430 px
```

Comprobar especialmente:

- teclado móvil;
- selects/date inputs;
- mapa;
- upload cámara/archivo;
- OCR/revisión;
- modales y botones inferiores;
- latencia con red móvil real;
- ausencia de scroll horizontal.

## 16. Backup y restore

El deploy crea backup previo cuando detecta PostgreSQL de staging activo.

Antes de aprobar Beta hay que demostrar también **restore**, al menos una vez, usando un dump de staging verificado.

Nunca probar restore destructivo sobre producción.

## 17. Observabilidad

Antes de declarar staging aprobado:

- conservar logs API/worker/deploy;
- rotación/persistencia de logs;
- comprobar errores repetitivos del worker;
- revisar fallos S3/OCR/AEMET/notificaciones;
- no registrar cookies, secrets, tokens completos ni contenido sensible.

## 18. Criterio final

Staging real queda aprobado únicamente si:

- workflow remoto completo ✅
- HTTPS/CORS/headers ✅
- Google Auth real ✅
- bucket + upload + OCR real ✅
- AEMET/radar real ✅
- VAPID/notificaciones configuradas ✅
- Agricultor real ✅
- Profesional real ✅
- móvil real ✅
- PostgreSQL no expuesto ✅
- backup + restore probado ✅
- observabilidad básica ✅

Hasta entonces el estado correcto es:

> **Beta técnicamente preparada para staging externo; staging real pendiente de validación.**

`main` permanece fuera de este proceso hasta la decisión final del candidato.
