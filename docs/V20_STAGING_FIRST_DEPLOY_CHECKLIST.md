# V20 — Checklist del primer staging real

Objetivo: ejecutar el primer despliegue externo de V20 sin improvisar y sin tocar `main`.

Último SHA técnico completamente validado:

```text
76a086717236057027f2b24a06d7942546d5e52c
```

Gates del mismo SHA:

- `V20 full candidate check` #2173 ✅
- `V20 beta browser E2E` #482 ✅
- `V20 staging readiness` #101 ✅

Ese SHA incluye Google Identity embebido correctamente en la web, backup PostgreSQL obligatorio antes de migraciones y validación del dump con cliente PostgreSQL 17.

## 1. Host

Debe existir un host de staging separado de producción con:

- Docker Engine;
- Docker Compose v2;
- `curl` y `tar`;
- SSH con clave dedicada;
- almacenamiento persistente para PostgreSQL y backups;
- salida a S3, Google, AEMET y web-push.

No publicar PostgreSQL a Internet.

Ruta recomendada:

```text
/srv/stacks/magina-olivo-v20-staging
```

## 2. Dominios HTTPS

Usar web y API en orígenes HTTPS distintos, preferiblemente subdominios del mismo dominio registrable:

```text
WEB = https://<dominio-staging>
API = https://<api-dominio-staging>
```

Proxy/túnel:

```text
WEB → http://127.0.0.1:8080
API → http://127.0.0.1:3001
```

Esto mantiene el diseño actual de cookie `Secure` + `SameSite=Lax`.

## 3. Bucket de staging

Crear un bucket exclusivo de staging con credenciales exclusivas.

Como la subida de documentos usa `PUT` directo desde navegador a URL prefirmada, configurar CORS del bucket para el origen web real:

```text
AllowedOrigin: https://<dominio-web-staging>
AllowedMethod: PUT
AllowedHeaders: Content-Type, x-amz-checksum-sha256
```

Evitar `*` si el proveedor permite una política restrictiva.

## 4. Google Identity

Preparar el mismo client ID en:

```text
GOOGLE_CLIENT_ID
NEXT_PUBLIC_GOOGLE_CLIENT_ID
```

Registrar el dominio web HTTPS de staging entre los **Authorized JavaScript origins** del cliente Google.

No se requiere `GOOGLE_CLIENT_SECRET` en el flujo actual.

## 5. VAPID

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

## 7. `.env` privado

Partir de `deploy/staging/.env.example` y completar todos los placeholders.

Variables críticas:

```dotenv
NODE_ENV=production
POSTGRES_USER=magina_staging
POSTGRES_PASSWORD=<PASSWORD>
POSTGRES_DB=magina_staging
DATABASE_URL=postgresql://magina_staging:<PASSWORD_URL_ENCODED>@postgres:5432/magina_staging
WEB_PORT=8080
API_PORT=3001
CORS_ALLOWED_ORIGINS=https://<dominio-staging>
NEXT_PUBLIC_API_URL=https://<api-dominio-staging>
NEXT_PUBLIC_PREVIEW_MODE=false
GOOGLE_CLIENT_ID=<CLIENT_ID_REAL>
NEXT_PUBLIC_GOOGLE_CLIENT_ID=<MISMO_CLIENT_ID_REAL>
SESSION_SECRET=<MINIMO_32_BYTES>
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
VAPID_PUBLIC_KEY=<REAL>
VAPID_PRIVATE_KEY=<REAL>
VAPID_SUBJECT=mailto:<correo-real>
AEMET_API_KEY=<REAL>
PUBLIC_WEB_ORIGIN=https://<dominio-staging>
```

Validar antes de guardarlo como secret:

```bash
node scripts/staging-env-preflight.mjs deploy/staging/.env
```

Debe terminar con `Staging env preflight passed`.

## 8. GitHub Environment `staging`

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

Mantener aprobación manual mientras siga siendo Beta.

## 9. SSH / host

Comprobar antes del primer deploy:

```bash
ssh -p <STAGING_PORT> <STAGING_USER>@<STAGING_HOST>
docker version
docker compose version
curl --version
tar --version
```

`STAGING_SSH_KNOWN_HOSTS` debe provenir de una huella verificada. No desactivar host-key checking.

## 10. SHA a desplegar

Usar un SHA completo de 40 caracteres con los tres gates del mismo SHA en `success`:

```text
V20 full candidate check
V20 beta browser E2E
V20 staging readiness
```

El candidato técnico actual es `76a086717236057027f2b24a06d7942546d5e52c`.

## 11. Lanzar `V20 staging deploy`

Inputs:

```text
ref=<rama/tag/SHA>
expected_sha=<SHA completo>
confirm=DEPLOY-STAGING
```

Debe completar:

1. checkout exacto;
2. validación de los tres gates;
3. preflight del `.env`;
4. SSH/known_hosts;
5. prerequisitos del host;
6. release inmutable;
7. arranque aislado de PostgreSQL;
8. backup obligatorio **antes de migrar**;
9. build web/API/worker;
10. migraciones;
11. health local de web/API y worker;
12. segunda migración no-op;
13. smoke HTTPS externo;
14. actualización de `current` y `CURRENT_SHA`.

Si falla el smoke externo, la release no se marca como actual.

## 12. Backup / restore

El backup se genera con PostgreSQL 17 mediante:

```text
pg_dump -Fc
```

Validar y restaurar con cliente PostgreSQL **17**, no con una major anterior.

Ejemplo de inspección sin depender del cliente instalado en el host:

```bash
docker run --rm \
  -v /ruta/backups:/backups:ro \
  postgres:17-bookworm \
  pg_restore -l /backups/<backup>.dump
```

Readiness #101 ya valida automáticamente que el dump pre-migración existe, no está vacío y PostgreSQL 17 puede leerlo.

Antes de aprobar Beta falta ejecutar al menos un **restore controlado real** en staging.

## 13. Smoke externo

Comprobar:

```text
GET WEB/healthz → ok
GET WEB/        → 200
GET API/health  → 200
```

Además:

- HTTPS válido;
- CORS solo para la web configurada;
- `X-Frame-Options: DENY`;
- `X-Content-Type-Options: nosniff`;
- API privada `Cache-Control: no-store`;
- PostgreSQL no accesible desde Internet.

## 14. Agricultor real

```text
Google login
→ crear finca
→ añadir límites / mapa
→ registrar trabajo
→ registrar cosecha
→ añadir rendimiento
→ Campaña
→ subir documento real
→ OCR
→ revisión humana
→ tiempo/radar
```

Criterios: cero demo, dev-auth desactivado, persistencia tras logout/login, documento en bucket real y OCR ejecutado por el worker desplegado.

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

- logs API/worker/deploy persistentes o retenidos;
- revisar S3/OCR/AEMET/notificaciones;
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
- backup + restore probado ✅
- observabilidad básica ✅

Hasta entonces:

> **Beta técnicamente preparada para staging externo; staging real pendiente de validación.**

`main` permanece fuera de este proceso hasta la decisión final del candidato.
