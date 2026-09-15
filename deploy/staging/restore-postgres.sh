#!/bin/sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
COMPOSE_FILE=${COMPOSE_FILE:-$SCRIPT_DIR/docker-compose.yml}
COMPOSE_ENV_FILE=${COMPOSE_ENV_FILE:-$SCRIPT_DIR/.env}
BACKUP=${1:-}

if [ -z "$BACKUP" ] || [ ! -f "$BACKUP" ]; then
  echo "Usage: CONFIRM_RESTORE=<database-name> sh deploy/staging/restore-postgres.sh <backup.dump>" >&2
  exit 1
fi

if [ ! -f "$COMPOSE_ENV_FILE" ]; then
  echo "Staging env file not found: $COMPOSE_ENV_FILE" >&2
  exit 1
fi

compose() {
  docker compose --env-file "$COMPOSE_ENV_FILE" -f "$COMPOSE_FILE" "$@"
}

wait_for_postgres_health() {
  POSTGRES_CONTAINER_ID=$(compose ps -q postgres)
  if [ -z "$POSTGRES_CONTAINER_ID" ]; then
    echo "PostgreSQL container is not running." >&2
    return 1
  fi

  for attempt in $(seq 1 45); do
    HEALTH_STATUS=$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "$POSTGRES_CONTAINER_ID")
    if [ "$HEALTH_STATUS" = 'healthy' ]; then
      return 0
    fi
    if [ "$HEALTH_STATUS" = 'unhealthy' ]; then
      echo "PostgreSQL container became unhealthy." >&2
      return 1
    fi
    if [ "$attempt" -eq 45 ]; then
      echo "PostgreSQL container did not become healthy within the expected window." >&2
      return 1
    fi
    sleep 1
  done
}

compose up -d postgres >/dev/null
wait_for_postgres_health

DATABASE_NAME=$(compose exec -T postgres sh -ec 'printf %s "$POSTGRES_DB"' | tr -d '\r')
if [ "${CONFIRM_RESTORE:-}" != "$DATABASE_NAME" ]; then
  echo "Refusing destructive restore. Set CONFIRM_RESTORE=$DATABASE_NAME to continue." >&2
  exit 1
fi

RUNNING_RUNTIME_SERVICES=$(compose ps --status running --services | grep -E '^(api|worker)$' || true)

restart_runtime() {
  if [ -n "$RUNNING_RUNTIME_SERVICES" ]; then
    # shellcheck disable=SC2086
    compose start $RUNNING_RUNTIME_SERVICES >/dev/null 2>&1 || true
  fi
}
trap restart_runtime EXIT INT TERM

if [ -n "$RUNNING_RUNTIME_SERVICES" ]; then
  # shellcheck disable=SC2086
  compose stop $RUNNING_RUNTIME_SERVICES >/dev/null
fi

echo "Recreating database $DATABASE_NAME from maintenance database postgres..."
compose exec -T postgres sh -ec '
  dropdb \
    --if-exists \
    --force \
    --maintenance-db=postgres \
    -U "$POSTGRES_USER" \
    "$POSTGRES_DB"
  createdb \
    --maintenance-db=postgres \
    -U "$POSTGRES_USER" \
    "$POSTGRES_DB"
'

echo "Restoring dump into $DATABASE_NAME..."
compose exec -T postgres sh -ec '
  pg_restore \
    -U "$POSTGRES_USER" \
    -d "$POSTGRES_DB" \
    --exit-on-error \
    --no-owner \
    --no-acl
' < "$BACKUP"

compose exec -T postgres sh -ec 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 -c "SELECT 1" >/dev/null'

restart_runtime
trap - EXIT INT TERM

echo "Restore completed for database: $DATABASE_NAME"
