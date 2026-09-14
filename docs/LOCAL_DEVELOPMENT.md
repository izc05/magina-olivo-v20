# Mágina Olivo V20 — desarrollo local

Estado: candidato V20. No producción.

## Requisitos

- Node.js 22
- pnpm 10.15.1
- Docker + Docker Compose
- cliente `psql` para aplicar migraciones manualmente

## Comprobación antes de arrancar (Windows)

Después de activar la virtualización y reiniciar el PC, abre PowerShell en la raíz
del proyecto y ejecuta:

```powershell
.\scripts\check-local-v20.ps1 -RequireDocker
```

La comprobación no instala ni modifica nada. Confirma Node 22, pnpm 10.15,
virtualización, WSL 2, Docker y los puertos `3001`, `3002` y `5432`. Cuando todos
los requisitos estén correctos, prepara la base local con:

```powershell
.\scripts\start-local-v20.ps1 -SeedDemo
```

Para preparar la base y arrancar API + web automáticamente, usa en su lugar:

```powershell
.\scripts\run-local-v20.ps1 -SeedDemo
```

La web quedará en `http://127.0.0.1:3001` y la API en
`http://127.0.0.1:3002/health`. Los registros se guardan en `artifacts/`.

No hace falta instalar `psql` en Windows para este flujo: el script ejecuta las
migraciones dentro del contenedor local de PostgreSQL.

## 1. Instalar dependencias

```bash
pnpm install
```

## 2. Levantar PostgreSQL + PostGIS

```bash
docker compose -f infra/docker-compose.dev.yml up -d
```

Esperar a que `magina-v20-postgres` esté healthy.

Conexión local:

```text
postgresql://magina:magina@127.0.0.1:5432/magina_v20
```

## 3. Aplicar migraciones

```bash
export PGPASSWORD=magina
for migration in database/migrations/*.sql; do
  psql -h 127.0.0.1 -U magina -d magina_v20 -v ON_ERROR_STOP=1 -f "$migration"
done
```

Las migraciones se aplican en orden por nombre.

No editar manualmente tablas de un entorno real. Los cambios de esquema deben entrar como migración nueva una vez exista un entorno compartido.

## 4. Cargar datos demo opcionales

```bash
psql -h 127.0.0.1 -U magina -d magina_v20 -v ON_ERROR_STOP=1 -f database/seeds/001_demo.sql
```

Datos demo principales:

- workspace: `10000000-0000-4000-8000-000000000001`
- campaña: `10000000-0000-4000-8000-000000000002`
- Las Cenillas: `10000000-0000-4000-8000-000000000003`

## 5. Compilar contratos compartidos

```bash
pnpm --filter @magina/contracts build
```

## 6. Arrancar API

```bash
export DATABASE_URL=postgresql://magina:magina@127.0.0.1:5432/magina_v20
export PORT=3002
export HOST=127.0.0.1
export CORS_ALLOWED_ORIGINS=http://127.0.0.1:3001,http://localhost:3001
pnpm dev:api
```

Health:

```text
GET http://127.0.0.1:3002/health
```

### Contexto de autenticación provisional

Mientras Auth/Workspace Membership no esté implementado, las rutas privadas exigen dos cabeceras de desarrollo:

```text
x-workspace-id
x-user-id
```

Esto **no es autenticación de producción** y debe desaparecer antes del piloto real.

## 7. Arrancar web

En otra terminal:

```bash
export NEXT_PUBLIC_API_URL=http://127.0.0.1:3002
pnpm dev -- --hostname 127.0.0.1 --port 3001
```

En Windows PowerShell se puede preparar la base local, aplicar las migraciones y
mostrar estos comandos con una sola orden:

```powershell
.\scripts\start-local-v20.ps1
```

Para cargar además los datos demo, usar `-SeedDemo`. El script opera solo sobre
el contenedor local `magina-v20-postgres`; no se conecta al mini PC ni a staging.
Guarda un historial local de migraciones y de la semilla demo, por lo que puede
ejecutarse de nuevo: solo aplicará las que todavía no existan en esa base. Si
encuentra tablas locales sin historial, se detiene sin modificarlas para evitar
perder datos.

## 8. Validación completa

```bash
pnpm typecheck
pnpm build
```

GitHub Actions añade además:

1. PostGIS real;
2. migraciones;
3. seed mínimo de CI;
4. API real;
5. creación de finca;
6. registro de riego;
7. comprobación de Timeline, Costes y Calendario.

## 9. Parar desarrollo

```bash
docker compose -f infra/docker-compose.dev.yml down
```

Para borrar además la base local:

```bash
docker compose -f infra/docker-compose.dev.yml down -v
```

Usar `-v` solo cuando se quiera eliminar deliberadamente la información local.

## Principio

El prototipo web puede seguir usando almacenamiento local mientras el backend se construye. La migración hacia API debe respetar los contratos de `packages/contracts` para no rehacer la UX.
