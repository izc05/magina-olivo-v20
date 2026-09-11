# V20 — Staging runbook

Estado: runtime de staging reproducible preparado y validado en CI. El último HEAD técnico completamente verificado es `a88dde53069b5508eaba4ffbbd0ab9dcc6dd34d9`.

En ese SHA quedaron verdes conjuntamente:

- `V20 full candidate check` #2122;
- `V20 beta browser E2E` #426;
- `V20 staging readiness` #63.

`staging readiness` construye las imágenes Docker de **web + API + worker**, valida Tesseract/Poppler dentro de la imagen final, arranca PostGIS, aplica las migraciones registradas, levanta el stack y comprueba health. También ejecuta una segunda pasada de migraciones sobre la misma base y exige que sea un no-op.

Esto **no equivale todavía a un staging externo aprobado**: faltan el host, los secretos y servicios reales del entorno (dominio HTTPS, bucket, Google Auth, AEMET/radar, VAPID y observabilidad).

## Objetivo

Validar la Beta V20 en un entorno real antes de cualquier merge a `main`:

- web estática sin preview servida por nginx no-root;
- API Fastify real;
- PostgreSQL 17 + PostGIS;
- worker/jobs;
- OCR Tesseract de producción;
- storage S3-compatible;
- Google Auth;
- AEMET/radar;
- documentos/OCR;
- notificaciones configurables;
- enlaces comerciales públicos.

Staging no usa datos demo ni `ALLOW_DEV_AUTH_HEADERS`.

## Runtime preparado

`deploy/staging/` contiene, entre otros:

```text
.env.example
Dockerfile.api
Dockerfile.worker
Dockerfile.web
docker-compose.yml
deploy-host.sh
```

El stack incluye:

```text
                    ┌──────── web nginx no-root ─────── 127.0.0.1:WEB_PORT
                    │
PostGIS 17 ── migrate registry/checksum ── API Fastify ─ 127.0.0.1:API_PORT
                                      │
                                      └── worker
                                          ├─ OCR Tesseract + Poppler
                                          ├─ radar
                                          └─ notificaciones
```

Decisiones de seguridad del compose:

- PostgreSQL no publica ningún puerto al host;
- web y API publican únicamente en loopback para colocar HTTPS/Cloudflare/reverse proxy delante;
- API y worker usan filesystem de solo lectura;
- `/tmp` es `tmpfs` con límite explícito;
- se eliminan capabilities Linux (`cap_drop: ALL`);
- `no-new-privileges` está activo;
- los secretos de aplicación no se inyectan en PostgreSQL ni en el contenedor de migraciones;
- las migraciones deben terminar correctamente antes de arrancar API/worker;
- la web se compila con `NEXT_PUBLIC_PREVIEW_MODE=false` y la URL real de API queda embebida en el build.

## Migraciones persistentes

Las migraciones históricas no son idempotentes por sí mismas, así que staging no las reejecuta ciegamente.

El runner mantiene un registro de migraciones aplicado una sola vez y guarda checksum SHA-256. Comportamiento esperado:

1. instalación nueva → aplica cada SQL en orden y lo registra;
2. segundo despliegue → omite todas las ya aplicadas;
3. migración histórica modificada → falla por checksum distinto;
4. migración interrumpida/inconsistente → falla antes de continuar.

`V20 staging readiness` #63 validó explícitamente primera pasada + segunda pasada no-op sobre la misma base.

## Preparación del host

Requisitos:

- Docker Engine actual;
- Docker Compose v2;
- `curl` y `tar`;
- salida HTTPS mediante Cloudflare Tunnel o reverse proxy;
- almacenamiento privado para `.env` y backups;
- usuario SSH con permisos limitados suficientes para Docker y el directorio de staging.

El despliegue automatizado no necesita clonar Git en el host: GitHub Actions sube una release inmutable por SHA.

## Environment de GitHub `staging`

El workflow `.github/workflows/staging-deploy.yml` espera un Environment llamado `staging`.

### Secrets

- `STAGING_ENV_FILE`: contenido completo del `.env` privado de staging.
- `STAGING_SSH_PRIVATE_KEY`: clave SSH privada dedicada al despliegue.
- `STAGING_SSH_KNOWN_HOSTS`: entrada `known_hosts` verificada del host.

### Variables

- `STAGING_HOST`: hostname/IP del host accesible por SSH.
- `STAGING_USER`: usuario SSH de despliegue.
- `STAGING_PORT`: puerto SSH.
- `STAGING_PATH`: ruta absoluta de releases, por ejemplo `/srv/stacks/magina-olivo-v20-staging`.

Recomendado: proteger el Environment con aprobación manual mientras V20 siga en Beta.

## `.env` privado

Partir de:

```bash
cp deploy/staging/.env.example deploy/staging/.env
chmod 600 deploy/staging/.env
```

Editar **todos** los `CHANGE_ME` y revisar que:

- `POSTGRES_PASSWORD` coincide con la contraseña embebida en `DATABASE_URL`;
- `CORS_ALLOWED_ORIGINS` contiene únicamente el origen web real de staging;
- `NEXT_PUBLIC_PREVIEW_MODE=false`;
- `ALLOW_DEV_AUTH_HEADERS=false`;
- `S3_BUCKET` es exclusivo de staging;
- las credenciales S3 no son las de producción;
- Google Auth, AEMET y VAPID pertenecen al entorno de staging;
- `PUBLIC_WEB_ORIGIN` apunta al dominio HTTPS real;
- `NEXT_PUBLIC_API_URL` apunta al dominio HTTPS real de API;
- `WEB_PORT` y `API_PORT` quedan en loopback detrás del proxy/túnel.

Nunca versionar `deploy/staging/.env`.

## Validación local del compose

Antes de arrancar manualmente:

```bash
docker compose \
  --env-file deploy/staging/.env \
  -f deploy/staging/docker-compose.yml \
  config
```

Construir las tres imágenes:

```bash
docker compose \
  --env-file deploy/staging/.env \
  -f deploy/staging/docker-compose.yml \
  build api worker web
```

El worker incluye:

- `tesseract`;
- idiomas `spa` y `eng`;
- `pdfinfo`;
- `pdftoppm`.

## Backup previo

Staging usa PostgreSQL 17 + PostGIS con volumen persistente `postgres_data`.

`deploy-host.sh` crea automáticamente un dump `pg_dump -Fc` antes de migrar **si ya existe un PostgreSQL de staging en ejecución**. El backup se guarda fuera de la release inmutable, en el directorio indicado por `STAGING_BACKUP_DIR`.

El workflow remoto usa:

```text
$STAGING_PATH/backups
```

La existencia de backup no autoriza un restore automático. No se hace rollback destructivo de base de datos.

## Despliegue remoto manual

Workflow:

```text
V20 staging deploy
```

Es exclusivamente `workflow_dispatch` y exige:

- `ref`: rama/tag/SHA a desplegar;
- `expected_sha`: SHA completo de 40 caracteres;
- `confirm`: exactamente `DEPLOY-STAGING`.

Antes de abrir SSH, el workflow exige que existan ejecuciones `success` para ese **mismo SHA exacto** de:

- `V20 full candidate check`;
- `V20 beta browser E2E`;
- `V20 staging readiness`.

Después:

1. valida `STAGING_ENV_FILE` con el preflight de producción;
2. valida variables SSH y `known_hosts`;
3. comprueba Docker/Compose/curl/tar en el host;
4. sube una release inmutable a `$STAGING_PATH/releases/<SHA>`;
5. instala el `.env` con permisos restrictivos;
6. ejecuta `deploy/staging/deploy-host.sh`;
7. crea backup previo cuando corresponde;
8. construye web/API/worker;
9. arranca el stack;
10. espera health local de API y web;
11. vuelve a ejecutar migraciones y exige no-op;
12. ejecuta smoke externo HTTPS;
13. solo después actualiza `$STAGING_PATH/current` y `CURRENT_SHA`.

## Ejecución manual en el host

Si se necesita operar sin Actions:

```bash
STAGING_BACKUP_DIR=/srv/stacks/magina-olivo-v20-staging/backups \
  ./deploy/staging/deploy-host.sh deploy/staging/.env
```

El script muestra `compose ps` y logs recientes si falla.

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

`PUBLIC_WEB_ORIGIN`, `NEXT_PUBLIC_API_URL` y `CORS_ALLOWED_ORIGINS` deben coincidir exactamente con estos orígenes HTTPS.

## OCR de producción

El worker usa `OCR_PROVIDER=tesseract`.

Controles actuales:

- descarga el objeto desde el bucket configurado;
- limita tamaño máximo (`OCR_MAX_BYTES`);
- verifica SHA-256 contra la versión documental encolada;
- limita páginas PDF (`OCR_MAX_PDF_PAGES`);
- convierte PDF con Poppler sin usar shell para procesar el archivo;
- ejecuta Tesseract mediante `execFile`;
- impone timeout;
- limpia el directorio temporal al terminar;
- rechaza un job que pida explícitamente otro proveedor;
- mantiene revisión humana obligatoria.

CI confirma runtime y binarios. Staging real debe probar además un documento contra el bucket real.

## Smoke post-deploy

### API y seguridad

Comprobar:

- `/health` responde 200;
- `/healthz` web responde `ok`;
- origen web permitido por CORS;
- un origen no autorizado es rechazado;
- API privada responde `Cache-Control: no-store`;
- `X-Content-Type-Options: nosniff`;
- `X-Frame-Options: DENY`;
- `ALLOW_DEV_AUTH_HEADERS` permanece desactivado;
- PostgreSQL no es accesible desde la red exterior.

### Auth

1. Entrar con Google.
2. Resolver usuario.
3. Resolver membership/workspace.
4. Recargar y conservar sesión.
5. Cerrar sesión y comprobar invalidación.

### Agricultor

```text
Crear finca
→ añadir/gestionar límites
→ registrar trabajo
→ registrar cosecha
→ añadir rendimiento
→ comprobar Campaña
→ adjuntar/revisar documento OCR
→ abrir tiempo/radar
```

No debe aparecer ningún dato demo.

### Profesional

```text
Cliente
→ presupuesto
→ PDF
→ compartir
→ confirmar envío
→ abrir enlace público en incógnito
→ aceptar/rechazar
→ convertir a trabajo
→ factura
→ cobro
```

La decisión pública debe quedar ligada al envío y a la versión PDF correspondiente.

## Storage

Usar un bucket exclusivo de staging. Nunca reutilizar el bucket de producción.

Verificar:

- upload prefirmado;
- complete upload;
- SHA-256/integridad;
- read URL temporal;
- OCR de PDF/imagen real;
- PDF comercial archivado;
- enlaces públicos revocables.

## Logs y observabilidad mínima

Conservar logs separados o distinguibles de:

- API;
- worker;
- migrations/deploy;
- storage/OCR;
- radar/AEMET;
- dispatch de notificaciones.

No registrar tokens públicos completos, cookies, secretos ni contenido sensible de documentos.

`docker compose logs` sirve para diagnóstico inicial, no como estrategia final de observabilidad.

## Rollback

Si falla una validación post-deploy:

1. no se actualiza `current` si el smoke externo no pasa;
2. volver a una release SHA anterior para web/API/worker;
3. detener el stack defectuoso si procede;
4. restaurar base exclusivamente desde un dump verificado si hubo un cambio de datos incompatible;
5. documentar el fallo antes de reintentar.

No hay restore destructivo automático.

## Criterio de salida de staging

Staging puede considerarse aprobado cuando:

- recorridos Agricultor y Profesional pasan con servicios reales;
- no hay fallback demo;
- Google Auth funciona en el dominio real;
- móvil 360/390/430 funciona con latencia/red real;
- documento público funciona en incógnito;
- storage/OCR funcionan contra el bucket real;
- AEMET/radar funcionan con credenciales reales;
- headers/CORS son correctos desde Internet;
- no aparecen errores repetidos de worker;
- observabilidad básica está activa;
- backup **y restore** han sido probados al menos una vez.

Solo después se actualiza el PR candidato para decidir merge/squash. `main` no se toca durante estas pruebas.
