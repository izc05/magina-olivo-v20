# Mágina Olivo V20 — Centro operativo de Administración

Estado: implementación en `feat/v20-admin-operations-hub`, basada en `integrate/v20-beta-closure`.

## Objetivo

Administración debe ser el centro de gobierno de Mágina Olivo, no solo un CMS. La V20 mantiene las superficies existentes (`/admin`, `/admin/web`, `/admin/media`, `/admin/territorio`, `/admin/fuentes`) y añade `/admin/operaciones` como vista transversal de plataforma.

El principio es deliberado:

- **sí** a consultar y gobernar cualquier dominio relevante;
- **sí** a métricas globales y operativas;
- **sí** a formularios y acciones administrativas tipadas y auditadas;
- **no** a un editor SQL arbitrario desde el navegador;
- **no** a exponer secretos, tokens, binarios o texto OCR bruto.

## Superficies administrativas

| Ruta | Responsabilidad |
| --- | --- |
| `/admin` | usuarios, roles, CMS, ajustes y auditoría |
| `/admin/operaciones` | métricas globales, explorador de datos y App Gateway |
| `/admin/web` | edición visual de portada, contenido, anuncios, SEO y superficies públicas |
| `/admin/media` | biblioteca multimedia |
| `/admin/territorio` | municipios, pueblos y directorio territorial |
| `/admin/fuentes` | AEMET, radar, OCR, Catastro y SIGPAC |

## Métricas de `/admin/operaciones`

La API `GET /api/v1/admin/operations` calcula en tiempo real una fotografía compacta de:

- usuarios totales, activos, suspendidos y altas de 30 días;
- workspaces totales y profesionales;
- fincas activas y superficie registrada;
- campañas activas y tareas abiertas;
- trabajos agrícolas registrados;
- riegos, tratamientos, abonados y podas;
- gastos de campo y total económico;
- kilos de cosecha registrados;
- liquidaciones confirmadas y neto liquidado;
- cobros de cosecha y total cobrado;
- personas/empresas activas vinculadas a trabajos;
- documentos activos y volumen subido;
- OCR pendiente y fallido;
- contenido borrador/publicado;
- facturas emitidas y total facturado;
- presupuestos abiertos y aceptados;
- suscripciones Pro y Profesional;
- histórico de mercado validado y último período;
- cachés meteorológicas caducadas;
- acciones administrativas de las últimas 24 horas.

Estas métricas son de operación y soporte. La arquitectura queda preparada para añadir series temporales e históricos sin cambiar la navegación del Admin.

## Explorador de datos

`GET /api/v1/admin/data/:dataset` permite inspeccionar hasta 250 registros por petición, con 50 por defecto. Solo admite una allowlist explícita de 25 dominios:

- `users` — usuarios;
- `workspaces` — espacios de trabajo;
- `fields` — fincas;
- `campaigns` — campañas;
- `work` — trabajos agrícolas;
- `irrigation` — riegos;
- `treatments` — tratamientos;
- `fertilization` — abonado;
- `pruning` — poda;
- `expenses` — gastos;
- `harvest` — entregas de cosecha;
- `settlements` — liquidaciones;
- `collections` — cobros de cosecha;
- `agenda` — agenda y tareas;
- `parties` — personas y empresas;
- `machinery` — maquinaria;
- `materials` — materiales;
- `documents` — documentos;
- `ocr` — procesos OCR;
- `content` — contenido público;
- `invoices` — facturas profesionales;
- `quotes` — presupuestos profesionales;
- `plans` — planes y suscripciones;
- `territory` — catálogo territorial;
- `market` — histórico de mercado.

La respuesta selecciona columnas útiles para soporte y administración. No existe acceso genérico a nombres de tabla suministrados por el cliente y no se incluyen secretos, sesiones, credenciales, objetos binarios ni texto OCR bruto.

La primera versión del explorador es **solo lectura**. Las modificaciones de negocio se harán con acciones dedicadas para validar permisos, invariantes de dominio y auditoría. Es preferible añadir un editor de finca, campaña, usuario, contenido o plan concreto que permitir `UPDATE` arbitrarios.

## Edición de la web

El centro operativo no sustituye al editor de sitio. `/admin/web` continúa siendo la superficie para cambiar la experiencia pública: portada, contenido editorial, noticias/eventos, territorio visible, publicidad, SEO y recursos gestionados. `/admin/media` gestiona las imágenes y `/admin/territorio` el catálogo geográfico.

La ampliación prevista consiste en ir sustituyendo ajustes JSON por formularios tipados por módulo. Así se podrá gobernar cualquier sección visible sin dar acceso directo a SQL ni mezclar configuración editorial con datos de negocio.

## App Gateway

La idea de disponer de una aplicación ya existente desde Administración se implementa como **App Gateway**, sin copiar dependencias dentro del panel.

Configuración privada almacenada en `site_settings` bajo `platform.external_app`:

```json
{
  "enabled": true,
  "name": "Aplicación externa",
  "description": "Aplicación complementaria",
  "url": "https://app.example.com",
  "mode": "new_tab",
  "health_url": "https://app.example.com/health"
}
```

Modos:

1. `new_tab`: recomendado por defecto. La aplicación mantiene su despliegue y autenticación propios.
2. `embedded`: muestra una vista `iframe` dentro de `/admin/operaciones`.

`embedded` solo funcionará si la aplicación de destino permite ser enmarcada mediante su CSP `frame-ancestors` y no envía un `X-Frame-Options` incompatible. Si no lo permite, el botón de apertura externa sigue disponible.

Para una integración definitiva bajo el mismo dominio se recomienda reverse proxy, por ejemplo:

```text
https://magina.example.com/          -> web V20
https://magina.example.com/api/      -> API V20
https://magina.example.com/app/      -> aplicación complementaria
```

Después el App Gateway puede usar `/app/` sin acoplar los repositorios. Si más adelante decidimos convertir esa aplicación en parte nativa de V20, entonces sí conviene importarla como paquete/app del monorepo y compartir contratos y autenticación.

La configuración del Gateway exige `admin` o `super_admin` y cada cambio genera `external_app.updated` en `admin_audit_log`.

## Prueba rápida en un PC de desarrollo

Requisitos:

- Node.js 22;
- pnpm 10.15.1;
- Docker + Docker Compose;
- cliente PostgreSQL (`psql`);
- un OAuth Web Client de Google si se quiere entrar al Admin desde el navegador.

### 1. Obtener la rama

```bash
git fetch origin
git switch feat/v20-admin-operations-hub
pnpm install --frozen-lockfile
```

### 2. Base PostgreSQL/PostGIS

```bash
docker compose -f infra/docker-compose.dev.yml up -d
export PGPASSWORD=magina
for migration in database/migrations/*.sql; do
  psql -h 127.0.0.1 -U magina -d magina_v20 -v ON_ERROR_STOP=1 -f "$migration"
done
```

Opcionalmente:

```bash
psql -h 127.0.0.1 -U magina -d magina_v20 -v ON_ERROR_STOP=1 -f database/seeds/001_demo.sql
```

### 3. API

En una terminal:

```bash
export DATABASE_URL=postgresql://magina:magina@127.0.0.1:5432/magina_v20
export CORS_ALLOWED_ORIGINS=http://127.0.0.1:3000,http://localhost:3000
export AUTH_COOKIE_SECURE=false
export ADMIN_BOOTSTRAP_EMAILS=TU_CORREO_GOOGLE
export GOOGLE_CLIENT_ID=TU_CLIENT_ID.apps.googleusercontent.com
pnpm --filter @magina/contracts build
pnpm --filter @magina/weather build
pnpm --filter @magina/jobs build
pnpm dev:api
```

### 4. Web

En otra terminal:

```bash
export NEXT_PUBLIC_API_URL=http://127.0.0.1:3001
export NEXT_PUBLIC_GOOGLE_CLIENT_ID=TU_CLIENT_ID.apps.googleusercontent.com
pnpm dev
```

Abrir:

```text
http://127.0.0.1:3000/admin/operaciones
```

En la configuración del OAuth Web Client deben estar autorizados los orígenes locales que realmente se usen (`http://localhost:3000` y/o `http://127.0.0.1:3000`).

`ADMIN_BOOTSTRAP_EMAILS` solo debe utilizarse para establecer el primer superadministrador. Después los roles pueden gestionarse desde la propia plataforma.

## Prueba en mini-PC / servidor Docker

Para una prueba más parecida a staging, V20 ya dispone de `deploy/staging/docker-compose.yml`. Construye PostgreSQL/PostGIS, migrador, API, worker y web, con web y API publicados únicamente en loopback del host.

Preparación:

```bash
cp deploy/staging/.env.example deploy/staging/.env
```

Editar `deploy/staging/.env` con valores **privados**. Nunca commitear ese fichero. Como mínimo deben resolverse:

- contraseña y `DATABASE_URL`;
- `CORS_ALLOWED_ORIGINS` y `NEXT_PUBLIC_API_URL`;
- Google Client ID para API/web;
- `ADMIN_BOOTSTRAP_EMAILS` para el primer acceso;
- almacenamiento S3 compatible si se quieren probar documentos/radar;
- AEMET si se quiere probar clima real;
- VAPID si se quieren probar notificaciones push.

Arranque desde la raíz del repositorio:

```bash
docker compose --env-file deploy/staging/.env -f deploy/staging/docker-compose.yml up -d --build
```

Estado:

```bash
docker compose --env-file deploy/staging/.env -f deploy/staging/docker-compose.yml ps
curl -fsS http://127.0.0.1:3001/ready
curl -fsS http://127.0.0.1:8080/healthz
```

Parada conservando datos:

```bash
docker compose --env-file deploy/staging/.env -f deploy/staging/docker-compose.yml down
```

No usar `down -v` salvo que se quiera borrar deliberadamente la base de datos local.

Para acceder desde otro equipo de la red o desde Internet, no se deben abrir directamente PostgreSQL/API. La opción prevista es colocar HTTPS/reverse proxy o Cloudflare Tunnel delante de web/API.

## Validación de esta rama

El workflow `V20 platform admin check` incluye:

1. typecheck/build de API y web;
2. todas las migraciones sobre PostgreSQL/PostGIS real;
3. smoke existente de acceso corporativo/CMS/media/auditoría;
4. smoke específico que abre métricas, recorre los 25 datasets y valida el App Gateway;
5. smokes de territorio y fuentes.

Además, el PR se somete a los gates transversales del candidate: full candidate, browser E2E, staging readiness, environment contract y runtime hardening.

## Próximas ampliaciones previstas

La arquitectura deja sitio para añadir sin rehacer el panel:

- filtros/paginación/exportación CSV por dataset;
- fichas administrativas editables de finca, campaña y workspace;
- administración de planes y límites;
- métricas históricas y gráficas por día/semana/mes;
- estado de jobs/colas y últimos errores;
- gestor de feature flags;
- configuración por módulo con formularios en vez de JSON;
- health check activo del App Gateway desde backend, con protección SSRF;
- reverse proxy bajo el mismo dominio para la app complementaria;
- acciones de soporte como reasignación controlada, reintento OCR o refresco de fuentes, siempre auditadas.
