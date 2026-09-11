# V20 — Staging runbook

Estado: runtime de staging reproducible y gateado preparado. El último HEAD de código completamente validado es `7d663b6e747abe201a5ffe6ca65e07dd8fa35ba5`.

En ese HEAD quedaron verdes conjuntamente:

- `V20 environment contract` #49;
- `V20 lockfile guard` #41;
- `V20 foundation check` #32;
- `V20 full candidate check` #2129;
- `V20 beta browser E2E` #435;
- `V20 staging readiness` #70.

`staging readiness` construye las imágenes Docker de **web + API + worker**, valida Tesseract/Poppler dentro de la imagen final, arranca PostGIS 17, aplica las migraciones registradas, levanta el stack y comprueba health. También ejecuta una segunda pasada de migraciones sobre la misma base y exige que sea un no-op.

Esto **no significa que exista ya un staging externo aprobado**. Para desplegar fuera de CI siguen siendo necesarios un host autorizado, los secretos del Environment `staging`, dominios HTTPS y servicios reales de storage, Google Auth, AEMET/radar y VAPID.

## Objetivo

Validar la Beta V20 en un entorno real antes de cualquier merge a `main`:

- web estática sin preview servida por nginx no-root;
- API Fastify real;
- PostgreSQL 17 + PostGIS;
- worker/jobs;
- OCR Tesseract + Poppler;
- storage S3-compatible aislado;
- Google Auth por ID token;
- AEMET/radar;
- documentos/OCR;
- notificaciones configurables;
- enlaces comerciales públicos.

Staging no usa fallback demo ni `ALLOW_DEV_AUTH_HEADERS`.

## Runtime preparado

`deploy/staging/` contiene, entre otros:

```text
.env.example
Dockerfile.api
Dockerfile.worker
Dockerfile.web
docker-compose.yml
deploy-host.sh
migrate.sh
nginx.conf
```

Arquitectura:

```text
                         ┌── web nginx no-root ───── 127.0.0.1:WEB_PORT
                         │
PostGIS 17 ── migrate ───┴── API Fastify ────────── 127.0.0.1:API_PORT
              │                 │
              │                 └── worker
              │                     ├─ OCR Tesseract + Poppler
              │                     ├─ radar
              │                     └─ notificaciones
              └── registry + SHA-256
```

Decisiones de seguridad:

- PostgreSQL no publica puerto al host;
- web y API publican únicamente en loopback para situar HTTPS/Cloudflare/reverse proxy delante;
- API y worker usan filesystem de solo lectura donde corresponde;
- `/tmp` usa `tmpfs` limitado;
- se eliminan capabilities Linux y se activa `no-new-privileges`;
- secretos de aplicación no se inyectan en PostgreSQL ni en el migrador;
- API/worker no arrancan si las migraciones fallan;
- web se compila con `NEXT_PUBLIC_PREVIEW_MODE=false`;
- `/documento-publico` se sirve con `no-store` y `noindex`;
- dev-auth falla cerrado en producción.

## Contrato de entorno

Partir de:

```bash
cp deploy/staging/.env.example deploy/staging/.env
chmod 600 deploy/staging/.env
```

Editar todos los placeholders y comprobar:

- `POSTGRES_PASSWORD` coincide con `DATABASE_URL`;
- `CORS_ALLOWED_ORIGINS` contiene el origen HTTPS real de la web;
- `NEXT_PUBLIC_API_URL` apunta al origen HTTPS real de la API;
- `GOOGLE_CLIENT_ID` y `NEXT_PUBLIC_GOOGLE_CLIENT_ID` son idénticos;
- `NEXT_PUBLIC_PREVIEW_MODE=false`;
- `AUTH_COOKIE_SECURE=true`;
- `ALLOW_DEV_AUTH_HEADERS=false`;
- bucket y credenciales S3 pertenecen solo a staging;
- AEMET y VAPID pertenecen a staging;
- límites OCR son positivos y acotados.

No añadir `GOOGLE_CLIENT_SECRET`, `SESSION_SECRET`, `PUBLIC_WEB_ORIGIN` ni `OCR_PROCESSOR_MODE`: no pertenecen al contrato runtime actual.

Validación obligatoria:

```bash
node scripts/staging-env-preflight.mjs deploy/staging/.env
```

El preflight valida flags de producción, coherencia Postgres, HTTPS/CORS, Google ID-token, storage aislado, worker/OCR y rechaza placeholders o claves obsoletas.

Nunca versionar `deploy/staging/.env`.

## Migraciones persistentes y repetibles

Las migraciones históricas no se vuelven a ejecutar ciegamente. `deploy/staging/migrate.sh` mantiene `public.schema_migrations` con:

- nombre de migración;
- SHA-256;
- estado `applying` / `applied`;
- timestamps de inicio/aplicación.

Comportamiento esperado:

1. instalación nueva → aplica cada SQL una sola vez y lo registra;
2. siguiente despliegue → omite las ya aplicadas;
3. migración histórica editada → falla por checksum distinto;
4. ejecución previa interrumpida (`applying`) → falla antes de continuar;
5. registro final debe contener exactamente una fila `applied` por migración.

`V20 staging readiness` #70 valida explícitamente primera pasada + segunda pasada no-op sobre la misma base.

## Preparación del host

Requisitos mínimos:

- Docker Engine;
- Docker Compose v2;
- `curl` y `tar`;
- salida HTTPS mediante Cloudflare Tunnel o reverse proxy;
- usuario SSH dedicado con acceso limitado a Docker y al directorio de staging;
- almacenamiento protegido para `.env` y backups.

El despliegue automatizado no requiere clonar Git en el host: GitHub Actions sube una release inmutable identificada por SHA.

## Environment de GitHub `staging`

El workflow `.github/workflows/staging-deploy.yml` es exclusivamente manual (`workflow_dispatch`) y usa el Environment `staging`.

Secrets esperados:

- `STAGING_ENV_FILE`: contenido completo del `.env` privado;
- `STAGING_SSH_PRIVATE_KEY`: clave SSH dedicada al deploy;
- `STAGING_SSH_KNOWN_HOSTS`: entrada `known_hosts` verificada.

Variables esperadas:

- `STAGING_HOST`;
- `STAGING_USER`;
- `STAGING_PORT`;
- `STAGING_PATH`, por ejemplo `/srv/stacks/magina-olivo-v20-staging`;
- `STAGING_WEB_URL`: origen HTTPS público de la web.

`STAGING_WEB_URL` es una variable de orquestación de GitHub, no una variable runtime de la aplicación y no sustituye a `CORS_ALLOWED_ORIGINS`.

Mientras V20 siga en Beta, conviene mantener aprobación manual del Environment.

## Gating del despliegue remoto

El workflow requiere:

- `expected_sha`: SHA completo de 40 caracteres;
- `confirm`: exactamente `DEPLOY-STAGING`.

Antes de usar secretos o abrir SSH:

1. checkout exacto del SHA solicitado;
2. verifica que ese SHA pertenece al historial actual de `feat/v20-visual-prototype`;
3. exige `success` para ese mismo SHA en:
   - `V20 full candidate check`;
   - `V20 beta browser E2E`;
   - `V20 staging readiness`;
4. valida el `.env` privado con el preflight;
5. comprueba que `STAGING_WEB_URL` sea un origen HTTPS limpio y esté permitido por CORS;
6. valida host, usuario, puerto, ruta y `known_hosts`.

Las Actions usadas por CI/deploy están fijadas a SHAs inmutables y Node se obtiene desde `.nvmrc`.

## Release inmutable y backup

Cada despliegue se sube a:

```text
$STAGING_PATH/releases/<SHA>
```

El `.env` se copia con permisos restrictivos y queda fuera del repositorio.

`deploy-host.sh`:

1. vuelve a validar el `.env` privado;
2. construye web/API/worker;
3. arranca solo PostgreSQL;
4. espera a que esté listo;
5. crea **siempre** un `pg_dump -Fc` antes de ejecutar migraciones;
6. comprueba que el dump no esté vacío;
7. levanta migraciones, API, worker y web;
8. espera health de API/web;
9. ejecuta nuevamente el migrador y exige no-op;
10. comprueba el número de filas `applied` del registry.

Los backups se guardan fuera de la release, por defecto en el directorio indicado por `STAGING_BACKUP_DIR`.

La existencia de un backup no autoriza rollback automático de datos.

## Ejecución manual en el host

Para operar sin Actions, con una release ya presente:

```bash
STAGING_BACKUP_DIR=/srv/stacks/magina-olivo-v20-staging/backups \
  ./deploy/staging/deploy-host.sh deploy/staging/.env
```

Si falla, el script imprime estado y logs recientes de Postgres, migrador, API, worker y web.

Health local esperado:

```bash
curl -fsS http://127.0.0.1:$API_PORT/health
curl -fsS http://127.0.0.1:$WEB_PORT/healthz
```

No publicar PostgreSQL directamente.

## Cloudflare / reverse proxy

Exponer solo los servicios loopback:

```text
https://staging.example.com      → http://127.0.0.1:<WEB_PORT>
https://api-staging.example.com  → http://127.0.0.1:<API_PORT>
```

`CORS_ALLOWED_ORIGINS`, `NEXT_PUBLIC_API_URL` y la variable de deploy `STAGING_WEB_URL` deben describir esos orígenes coherentemente.

## Smoke externo

Después del arranque local, el workflow comprueba por HTTPS:

- `/healthz` web responde `ok`;
- la portada es accesible;
- `X-Frame-Options: DENY`;
- `X-Content-Type-Options: nosniff`;
- `/documento-publico` mantiene `Cache-Control: no-store` y `X-Robots-Tag`;
- `/health` API responde 200;
- origen web permitido por CORS;
- origen no autorizado rechazado;
- API privada mantiene cabeceras `no-store`;
- headers de autenticación de desarrollo no son aceptados en producción.

Solo si todo lo anterior pasa se actualizan `$STAGING_PATH/current` y `CURRENT_SHA`.

## OCR de staging

El worker usa `OCR_PROVIDER=tesseract` con:

- Tesseract `spa+eng`;
- Poppler (`pdfinfo`, `pdftoppm`);
- límite de bytes;
- límite de páginas;
- timeout;
- SHA-256 del objeto;
- directorio temporal limpiado al finalizar;
- revisión humana obligatoria antes de convertir extracción en datos agrícolas.

CI valida binarios e inicialización dentro de la imagen. El staging externo debe probar además un documento real contra el bucket real.

## Pruebas funcionales antes de aprobar staging

Agricultor:

```text
Crear finca
→ límites/mapa
→ trabajo
→ cosecha
→ rendimiento
→ Campaña
→ documento + OCR + revisión
→ tiempo/radar
```

Profesional:

```text
Cliente
→ presupuesto
→ PDF
→ compartir
→ envío
→ enlace público en incógnito
→ aceptar/rechazar
→ convertir a trabajo
→ factura
→ cobro
```

También deben comprobarse Google Auth real, notificaciones, AEMET/radar, storage y enlaces revocables.

## Observabilidad y rollback

No registrar cookies, secretos, tokens públicos completos ni contenido sensible de documentos.

Antes de aprobar staging debe existir persistencia/rotación de logs o un colector equivalente. `docker compose logs` es diagnóstico inicial, no observabilidad definitiva.

Ante fallo post-deploy:

1. no marcar la release como `current`;
2. volver a servir la release de aplicación anterior si procede;
3. no intentar rollback destructivo automático de base de datos;
4. restaurar datos solo desde un dump verificado y mediante una decisión operativa explícita;
5. documentar el incidente antes de reintentar.

## Criterio de salida

Staging se considera aprobado solo cuando:

- recorridos Agricultor y Profesional pasan con servicios reales;
- no hay fallback demo;
- Google Auth funciona en el dominio real;
- móvil 360/390/430 funciona con red real;
- documento público funciona en incógnito;
- storage/OCR funcionan contra bucket real;
- AEMET/radar funcionan con credenciales reales;
- headers/CORS son correctos desde Internet;
- worker no muestra errores repetidos;
- observabilidad básica está activa;
- backup **y restore** han sido probados al menos una vez.

Solo después se decide la integración del candidate. `main` no se toca durante estas pruebas.
