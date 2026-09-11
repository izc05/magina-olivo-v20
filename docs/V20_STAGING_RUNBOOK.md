# V20 — Staging runbook

Estado: preparado para ejecutar cuando `V20 full candidate check` y `V20 beta browser E2E` estén verdes sobre el mismo HEAD.

## Objetivo

Validar la Beta V20 en un entorno real antes de cualquier merge a `main`:

- web estática sin preview;
- API Fastify real;
- PostgreSQL 17 + PostGIS;
- worker/jobs;
- storage S3-compatible;
- Google Auth;
- AEMET/radar;
- documentos/OCR;
- notificaciones configurables;
- enlaces comerciales públicos.

Staging no usa datos demo ni `ALLOW_DEV_AUTH_HEADERS`.

## Criterios previos obligatorios

1. `V20 full candidate check` verde.
2. `V20 beta browser E2E` verde.
3. Build web dentro del bundle budget.
4. `NEXT_PUBLIC_PREVIEW_MODE=false`.
5. `ALLOW_DEV_AUTH_HEADERS=false`.
6. Backup de la base de datos anterior si se reutiliza un staging existente.
7. Variables de `deploy/staging/.env.example` completas en almacenamiento privado.

## Servicios

### Web

Build:

```bash
pnpm --filter @magina/web build
```

Publicar el contenido de:

```text
apps/web/out
```

El hosting debe respetar `apps/web/public/_headers`:

- assets `_next/static/*` con cache immutable;
- HTML revalidable;
- `/documento-publico*` con `no-store` y `noindex`;
- `nosniff`, anti-framing y Referrer-Policy.

### API

Build:

```bash
pnpm --filter @magina/contracts build
pnpm --filter @magina/weather build
pnpm --filter @magina/jobs build
pnpm --filter @magina/api build
```

La API debe arrancar con `NODE_ENV=production` y una allowlist CORS explícita.

Health check mínimo:

```bash
curl -fsS https://api-staging.example.com/health
```

No debe exponerse `ALLOW_DEV_AUTH_HEADERS=true`.

### Worker

Build:

```bash
pnpm --filter @magina/worker build
```

Debe compartir la misma base de datos y configuración de jobs que la API.

## Base de datos

Staging usa PostgreSQL 17 + PostGIS.

Antes de migrar:

```bash
pg_dump --format=custom --file=magina-staging-before.dump "$DATABASE_URL"
```

Aplicar migraciones en orden:

```bash
for migration in database/migrations/*.sql; do
  echo "Applying $migration"
  psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f "$migration"
done
```

Si una migración falla, parar el despliegue. No continuar manualmente saltando migraciones.

## Smoke post-deploy

### API y seguridad

Comprobar:

- `/health` responde 200;
- origen web permitido por CORS;
- un origen no autorizado es rechazado;
- API privada responde `Cache-Control: no-store`;
- `X-Content-Type-Options: nosniff`;
- `X-Frame-Options: DENY`.

### Auth

1. Entrar con Google.
2. Resolver usuario.
3. Resolver membership/workspace.
4. Recargar página y conservar sesión.
5. Cerrar sesión y comprobar invalidación.

### Agricultor

Recorrido mínimo:

```text
Crear finca
→ registrar trabajo
→ registrar cosecha
→ añadir rendimiento
→ comprobar Campaña
→ adjuntar/revisar documento OCR
→ abrir tiempo/radar
```

No debe aparecer ningún dato demo.

### Profesional

Recorrido mínimo:

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

Usar bucket exclusivo de staging.

Nunca reutilizar el bucket de producción.

Verificar:

- upload prefirmado;
- complete upload;
- SHA-256/integridad;
- read URL temporal;
- PDF comercial archivado;
- enlaces públicos revocables.

## OCR

En staging el OCR puede seguir usando el proveedor provisional definido para Beta, pero:

- ningún OCR crea registros agrícolas automáticamente;
- la revisión humana sigue siendo obligatoria;
- fallos y reintentos quedan observables.

## Logs y observabilidad mínima

Guardar logs separados de:

- API;
- worker;
- migrations/deploy;
- errores de storage/OCR;
- dispatch de notificaciones.

No registrar tokens públicos completos, cookies de sesión, secretos ni contenido sensible de documentos.

## Rollback

Si falla una validación post-deploy:

1. retirar el nuevo web build o volver al build anterior;
2. detener API/worker nuevos;
3. restaurar imagen/artefacto anterior;
4. si hubo cambio de datos incompatible, restaurar el dump previo;
5. documentar el fallo antes de reintentar.

No hacer rollback destructivo de base de datos sin dump válido.

## Criterio de salida de staging

Staging puede considerarse aprobado cuando:

- recorridos Agricultor y Profesional pasan;
- no hay fallback demo;
- móvil 360/390/430 funciona sin overflow crítico;
- documento público funciona en incógnito;
- storage/OCR funcionan con infraestructura real;
- headers/CORS son correctos;
- no aparecen errores repetidos de worker;
- backup/restore ha sido probado al menos una vez.

Solo después se actualiza el PR candidato y se decide si procede preparar merge/squash.
