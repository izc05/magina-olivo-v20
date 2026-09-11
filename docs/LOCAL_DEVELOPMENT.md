# Mágina Olivo V20 — desarrollo local

Estado: candidato V20. No producción.

## Requisitos

- Node.js 22 (`.nvmrc`)
- pnpm 10.15.1 (`packageManager` en `package.json`)
- Docker + Docker Compose
- cliente `psql`

Con `nvm`:

```bash
nvm use
```

## 1. Instalar dependencias

El repositorio mantiene `pnpm-lock.yaml`, por lo que la instalación normal debe respetarlo:

```bash
pnpm install --frozen-lockfile
```

Si un cambio legítimo modifica `package.json` de algún workspace, se actualiza el lockfile en la misma rama. El workflow `V20 lockfile guard` comprueba esta sincronización y puede regenerarlo en PRs del propio repositorio.

## 2. Levantar PostgreSQL + PostGIS

```bash
docker compose -f infra/docker-compose.dev.yml up -d
```

Esperar a que `magina-v20-postgres` esté healthy.

Conexión local por defecto:

```text
postgresql://magina:magina@127.0.0.1:5432/magina_v20
```

## 3. Aplicar migraciones

```bash
export PGPASSWORD=magina
for migration in database/migrations/*.sql; do
  echo "Applying ${migration}"
  psql -h 127.0.0.1 -U magina -d magina_v20 -v ON_ERROR_STOP=1 -f "$migration"
done
```

Las migraciones se aplican en orden por nombre. Una migración que ya haya sido compartida no se reescribe: cualquier cambio de esquema entra en un archivo nuevo.

## 4. Cargar datos locales opcionales

Datos funcionales de ejemplo:

```bash
psql -h 127.0.0.1 -U magina -d magina_v20 -v ON_ERROR_STOP=1 -f database/seeds/001_demo.sql
```

Identidad local reproducible para desarrollo:

```bash
psql -h 127.0.0.1 -U magina -d magina_v20 -v ON_ERROR_STOP=1 -f database/seeds/002_dev_identity.sql
```

Identificadores del entorno local:

```text
workspace: 10000000-0000-4000-8000-000000000001
campaign:  10000000-0000-4000-8000-000000000002
field:     10000000-0000-4000-8000-000000000003
user:      10000000-0000-4000-8000-000000000005
```

Los seeds son solo para desarrollo y pruebas. No se cargan en staging ni producción.

## 5. Variables mínimas

Partir de `.env.example`. Ese archivo es el contrato común de configuración de V20: cualquier `process.env.*` usado en `apps/` o `packages/` debe estar documentado allí y CI lo comprueba automáticamente.

Para API local:

```bash
export DATABASE_URL=postgresql://magina:magina@127.0.0.1:5432/magina_v20
export NODE_ENV=development
export CORS_ALLOWED_ORIGINS=http://127.0.0.1:3000,http://localhost:3000
```

La autenticación normal ya usa sesiones y memberships. Los headers de desarrollo están desactivados por defecto. Solo para pruebas locales/automatizadas se pueden habilitar explícitamente:

```bash
export ALLOW_DEV_AUTH_HEADERS=true
```

Con esa variable activa, las peticiones pueden usar:

```text
x-workspace-id: 10000000-0000-4000-8000-000000000001
x-user-id:      10000000-0000-4000-8000-000000000005
```

No activar `ALLOW_DEV_AUTH_HEADERS` en staging ni producción.

## 6. Compilar paquetes compartidos

```bash
pnpm --filter @magina/contracts build
pnpm --filter @magina/weather build
pnpm --filter @magina/jobs build
```

## 7. Arrancar API

```bash
pnpm dev:api
```

Health:

```text
GET http://127.0.0.1:3001/health
```

## 8. Arrancar web

En otra terminal:

```bash
export NEXT_PUBLIC_API_URL=http://127.0.0.1:3001
export NEXT_PUBLIC_PREVIEW_MODE=false
pnpm dev
```

`NEXT_PUBLIC_PREVIEW_MODE=false` es la configuración correcta para validar el producto real. Si la API falla, la web debe mostrar el error/estado vacío correspondiente; no debe inventar datos demo.

El modo preview se reserva para la demo estática:

```bash
export NEXT_PUBLIC_PREVIEW_MODE=true
```

## 9. Validación

Controles rápidos antes de compilar todo:

```bash
pnpm check:lockfile
pnpm check:env
```

`check:lockfile` falla si los manifests y `pnpm-lock.yaml` no coinciden. `check:env` falla si código runtime usa una variable no declarada en `.env.example`.

Chequeo estructural del monorepo:

```bash
pnpm check
```

`pnpm check` ejecuta lockfile + contrato de entorno + typecheck + build. Para una pasada algo más rápida sin build final:

```bash
pnpm check:fast
```

Para el navegador:

```bash
pnpm e2e:beta
```

Playwright usa `E2E_WORKSPACE_ID` y `E2E_USER_ID` si se definen; en caso contrario usa la identidad E2E documentada en `playwright.config.ts`. La API que acompaña al E2E debe arrancar con `ALLOW_DEV_AUTH_HEADERS=true`.

GitHub Actions añade PostGIS real, migraciones, smokes de API/GIS/clima/radar/documentos/profesional, browser E2E y auditoría móvil. Los workstreams paralelos y su propiedad están documentados en `docs/WORKSTREAMS.md`.

## 10. Parar desarrollo

```bash
docker compose -f infra/docker-compose.dev.yml down
```

Para borrar también la base local:

```bash
docker compose -f infra/docker-compose.dev.yml down -v
```

Usar `-v` solo cuando se quiera eliminar deliberadamente la información local.

## Principios de desarrollo

- API real por defecto; preview explícita para demo.
- Finca como unidad principal visible para el usuario.
- Contratos compartidos antes de duplicar tipos entre web/API/worker.
- Cambios de base de datos mediante migraciones nuevas.
- Un workstream por responsabilidad principal; evitar duplicar ramas que ya tengan propietario.
- Lockfile y contrato de entorno forman parte del cambio cuando se modifican dependencias o configuración.
- Ningún secreto ni documento real debe entrar en Git.
