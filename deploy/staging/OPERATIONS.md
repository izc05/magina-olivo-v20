# Mágina Olivo V20 — Staging Operations

Este documento cubre observabilidad, rate limiting, readiness y recuperación operativa del staging Docker Compose.

## Readiness y liveness

- `GET /health` comprueba que el proceso API responde. No valida PostgreSQL.
- `GET /ready` ejecuta una consulta real contra PostgreSQL y devuelve `503` si la base no está configurada o no responde.
- Docker Compose usa `/ready` como healthcheck del servicio `api`, por lo que `web` no se considera listo hasta que la API pueda consultar la base.

Ejemplos desde el host:

```sh
curl -fsS http://127.0.0.1:${API_PORT:-3001}/health
curl -fsS http://127.0.0.1:${API_PORT:-3001}/ready
```

## Correlación de peticiones

Cada petición recibe un UUID generado por la API y se devuelve en `X-Request-Id`. El mismo identificador aparece como `reqId` en los logs estructurados de Fastify/Pino.

Cuando un usuario comunique un error, guardar el `X-Request-Id` permite localizar la petición exacta:

```sh
docker compose --env-file deploy/staging/.env -f deploy/staging/docker-compose.yml logs api | grep '<request-id>'
```

La configuración del logger redacta cookies, autorización y los headers de identidad de desarrollo/workspace. No registrar secretos manualmente en mensajes de log.

## Rotación de logs

`api` y `worker` usan el logging driver `local` de Docker con rotación:

- máximo `10m` por archivo;
- máximo `5` archivos por servicio.

Consultas habituales:

```sh
docker compose --env-file deploy/staging/.env -f deploy/staging/docker-compose.yml logs --since 30m api
docker compose --env-file deploy/staging/.env -f deploy/staging/docker-compose.yml logs --since 30m worker
```

## Rate limiting

En producción el rate limiting está activo por defecto. Staging lo declara de forma explícita.

Variables:

- `RATE_LIMIT_ENABLED=true`
- `RATE_LIMIT_WINDOW_MS=60000`
- `RATE_LIMIT_AUTH_MAX=20`
- `RATE_LIMIT_PUBLIC_MAX=120`
- `RATE_LIMIT_PRIVATE_MAX=300`
- `RATE_LIMIT_MAX_BUCKETS=10000`

Clases:

- `/api/v1/auth/**` → límite de autenticación;
- `/api/v1/public/**` → límite público;
- resto de `/api/v1/**` → límite privado;
- `/health`, `/ready` y `OPTIONS` → exentos.

Las respuestas incluyen `X-RateLimit-Limit`, `X-RateLimit-Remaining` y `X-RateLimit-Reset`. Al superar el límite se devuelve `429`, `Retry-After` y el `request_id`.

### Proxy confiable

`TRUST_PROXY=true` hace que Fastify use la cadena `X-Forwarded-For` para determinar la IP. Solo debe activarse si la API está aislada en loopback/red privada y todo el tráfico externo entra por un proxy/túnel controlado. Nunca habilitarlo si un cliente puede alcanzar directamente el puerto API.

## Backup PostgreSQL

El backup se genera en formato custom de `pg_dump`, sin ownership ni ACL, con permisos restrictivos por `umask 077`.

```sh
COMPOSE_ENV_FILE=deploy/staging/.env \
sh deploy/staging/backup-postgres.sh
```

O indicando destino:

```sh
COMPOSE_ENV_FILE=deploy/staging/.env \
sh deploy/staging/backup-postgres.sh /srv/backups/magina-staging-$(date -u +%Y%m%d).dump
```

Guardar las copias fuera del host de aplicación o sincronizarlas a almacenamiento cifrado. Una copia que solo existe junto al volumen PostgreSQL no protege frente a pérdida del host.

## Restore PostgreSQL

La restauración es destructiva: detiene `api` y `worker`, recrea la base indicada por `POSTGRES_DB`, restaura el dump, ejecuta una consulta de verificación y vuelve a arrancar runtime.

El script se niega a continuar si no se confirma explícitamente el nombre de la base:

```sh
COMPOSE_ENV_FILE=deploy/staging/.env \
CONFIRM_RESTORE=magina_staging \
sh deploy/staging/restore-postgres.sh /srv/backups/magina-staging-20260911.dump
```

Después del restore:

```sh
curl -fsS http://127.0.0.1:${API_PORT:-3001}/ready
docker compose --env-file deploy/staging/.env -f deploy/staging/docker-compose.yml ps
```

## Secuencia mínima ante incidente

1. Capturar `X-Request-Id` o franja horaria del fallo.
2. Revisar `api` y `worker` con `docker compose logs --since`.
3. Comprobar `/health` y `/ready` por separado.
4. Si PostgreSQL falla, revisar `postgres` antes de reiniciar servicios.
5. Antes de cualquier operación destructiva, crear backup y verificar que el archivo es no vacío.
6. Restaurar solo con confirmación explícita y comprobar `/ready` al terminar.
