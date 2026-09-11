# V20 — Staging runbook

Estado: runtime de staging reproducible preparado y validado en CI. El último runtime de código verificado es `b1dd423e002f582ff9de620f2e7cbc49b4c01e23`.

En ese SHA quedaron verdes conjuntamente:

- `V20 full candidate check` #2070;
- `V20 beta browser E2E` #373;
- `V20 staging readiness` #25.

`staging readiness` ya construye las imágenes Docker de API y worker, valida Tesseract/Poppler dentro de la imagen final, arranca PostGIS, aplica todas las migraciones, levanta la API containerizada y comprueba `/health`.

Esto no equivale todavía a un staging externo aprobado: faltan los secretos y servicios reales del entorno (dominio HTTPS, bucket, Google Auth, AEMET, VAPID y observabilidad).

## Objetivo

Validar la Beta V20 en un entorno real antes de cualquier merge a `main`:

- web estática sin preview;
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

`deploy/staging/` contiene:

```text
.env.example
Dockerfile.api
Dockerfile.worker
docker-compose.yml
```

El stack backend incluye:

```text
PostGIS 17
   ↓
migrate (one-shot)
   ↓
API Fastify ── 127.0.0.1:3001
   │
   └──────── worker
              ├─ OCR Tesseract + Poppler
              ├─ radar
              └─ notificaciones
```

Decisiones de seguridad del compose:

- PostgreSQL no publica ningún puerto al host;
- la API publica únicamente en `127.0.0.1` para colocar HTTPS/Cloudflare/reverse proxy delante;
- API y worker usan filesystem de solo lectura;
- `/tmp` es `tmpfs` con límite explícito;
- se eliminan capabilities Linux (`cap_drop: ALL`);
- `no-new-privileges` está activo;
- los secretos de aplicación no se inyectan en PostgreSQL ni en el contenedor de migraciones;
- las migraciones deben terminar correctamente antes de arrancar API/worker.

## Preparación del host

Requisitos:

- Docker Engine actual;
- Docker Compose v2;
- Git o un mecanismo equivalente para obtener el commit candidato;
- salida HTTPS mediante Cloudflare Tunnel o reverse proxy;
- almacenamiento privado para el archivo `.env` y copias de seguridad.

Crear el archivo privado de entorno:

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
- `PUBLIC_WEB_ORIGIN` apunta al dominio HTTPS real.

Nunca versionar `deploy/staging/.env`.

## Validación del compose

Antes de arrancar:

```bash
docker compose \
  --env-file deploy/staging/.env \
  -f deploy/staging/docker-compose.yml \
  config
```

La configuración debe resolver sin avisos de variables ausentes.

Construir las imágenes:

```bash
docker compose \
  --env-file deploy/staging/.env \
  -f deploy/staging/docker-compose.yml \
  build api worker
```

El worker incluye en su imagen:

- `tesseract`;
- datos de idioma `spa` y `eng`;
- `pdfinfo`;
- `pdftoppm`.

## Base de datos y backup

Staging usa PostgreSQL 17 + PostGIS con volumen persistente `postgres_data`.

Para una instalación nueva puede arrancarse directamente el stack. Si se reutiliza un staging existente, levantar primero solo PostgreSQL y crear un dump **antes** de ejecutar las nuevas migraciones:

```bash
docker compose \
  --env-file deploy/staging/.env \
  -f deploy/staging/docker-compose.yml \
  up -d postgres

mkdir -p backups

docker compose \
  --env-file deploy/staging/.env \
  -f deploy/staging/docker-compose.yml \
  exec -T postgres sh -lc 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' \
  > "backups/magina-staging-before-$(date +%Y%m%d-%H%M%S).dump"
```

Comprobar que el dump existe y no está vacío antes de continuar.

El servicio `migrate` aplica en orden `database/migrations/*.sql` con `ON_ERROR_STOP=1`. Si una migración falla, API/worker no deben arrancar.

## Arranque backend

Con el `.env` completo y el backup realizado cuando corresponda:

```bash
docker compose \
  --env-file deploy/staging/.env \
  -f deploy/staging/docker-compose.yml \
  up -d api worker
```

Estado:

```bash
docker compose \
  --env-file deploy/staging/.env \
  -f deploy/staging/docker-compose.yml \
  ps
```

Logs:

```bash
docker compose \
  --env-file deploy/staging/.env \
  -f deploy/staging/docker-compose.yml \
  logs -f --tail=200 api worker migrate
```

Health local, antes de exponerlo públicamente:

```bash
curl -fsS http://127.0.0.1:3001/health
```

Después de configurar HTTPS/Cloudflare/reverse proxy:

```bash
curl -fsS https://api-staging.example.com/health
```

No publicar directamente el puerto PostgreSQL. El proxy debe apuntar a `http://127.0.0.1:3001`.

## Web

La web continúa siendo un export estático separado del compose backend.

Build de staging:

```bash
NEXT_PUBLIC_API_URL=https://api-staging.example.com \
NEXT_PUBLIC_PREVIEW_MODE=false \
pnpm --filter @magina/web build
```

Publicar:

```text
apps/web/out
```

El hosting debe respetar `apps/web/public/_headers`:

- assets `_next/static/*` con cache immutable;
- HTML revalidable;
- `/documento-publico*` con `no-store` y `noindex`;
- `nosniff`, anti-framing y Referrer-Policy.

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
- el OCR no crea registros agrícolas automáticamente: la revisión humana sigue siendo obligatoria.

La CI confirma que el procesador se inicializa y que los binarios existen dentro de la imagen. Staging real debe comprobar además un documento real contra el bucket real.

## Smoke post-deploy

### API y seguridad

Comprobar:

- `/health` responde 200;
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
4. Recargar página y conservar sesión.
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

Verificar con infraestructura real:

- upload prefirmado;
- complete upload;
- SHA-256/integridad;
- read URL temporal;
- OCR de un PDF/imagen real;
- PDF comercial archivado;
- enlaces públicos revocables.

## Logs y observabilidad mínima

Conservar logs separados o distinguibles de:

- API;
- worker;
- migrations/deploy;
- errores de storage/OCR;
- radar/AEMET;
- dispatch de notificaciones.

No registrar tokens públicos completos, cookies de sesión, secretos ni contenido sensible de documentos.

Antes de aprobar staging, configurar persistencia/rotación de logs o un colector equivalente; `docker compose logs` sirve para diagnóstico inicial, no como estrategia final de observabilidad.

## Rollback

Si falla una validación post-deploy:

1. retirar el nuevo web build o volver al build anterior;
2. volver al commit/imágenes anteriores de API/worker;
3. detener el stack nuevo;
4. si hubo cambio de datos incompatible, restaurar exclusivamente desde un dump verificado;
5. documentar el fallo antes de reintentar.

No hacer rollback destructivo de base de datos sin dump válido.

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
