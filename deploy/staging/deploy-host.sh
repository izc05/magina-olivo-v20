#!/bin/sh
set -eu

ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
ENV_FILE="${1:-$ROOT/deploy/staging/.env}"
COMPOSE_FILE="$ROOT/deploy/staging/docker-compose.yml"
BACKUP_DIR="${STAGING_BACKUP_DIR:-$ROOT/backups/staging}"

case "$ENV_FILE" in
  /*) ;;
  *) ENV_FILE="$ROOT/$ENV_FILE" ;;
esac

if [ ! -f "$ENV_FILE" ]; then
  echo "Staging env file not found: $ENV_FILE" >&2
  exit 1
fi

compose() {
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" "$@"
}

on_exit() {
  status=$?
  trap - EXIT INT TERM
  if [ "$status" -ne 0 ]; then
    echo "Staging deploy failed; showing container state and recent logs." >&2
    compose ps >&2 || true
    compose logs --no-color --tail=200 postgres migrate api worker web >&2 || true
  fi
  exit "$status"
}
trap on_exit EXIT INT TERM

mkdir -p "$BACKUP_DIR"
chmod 700 "$BACKUP_DIR" 2>/dev/null || true

echo "Validating private staging environment..."
docker run --rm \
  -v "$ROOT:/app:ro" \
  -w /app \
  node:22-bookworm-slim \
  node scripts/staging-env-preflight.mjs "${ENV_FILE#$ROOT/}"

if [ -n "$(compose ps --status running -q postgres 2>/dev/null || true)" ]; then
  stamp="$(date -u +%Y%m%dT%H%M%SZ)"
  backup="$BACKUP_DIR/magina-staging-before-$stamp.dump"
  echo "Creating PostgreSQL backup at $backup"
  compose exec -T postgres sh -ec 'pg_dump -U "$POSTGRES_USER" -Fc "$POSTGRES_DB"' > "$backup"
  test -s "$backup"
  chmod 600 "$backup" 2>/dev/null || true
fi

echo "Building staging images..."
compose build api worker web

echo "Starting PostgreSQL, migrations, API, worker and web..."
compose up -d web worker

api_address="$(compose port api 3001 | tail -n 1)"
web_address="$(compose port web 8080 | tail -n 1)"
if [ -z "$api_address" ] || [ -z "$web_address" ]; then
  echo "Unable to resolve published API/web loopback ports." >&2
  exit 1
fi

api_ready=false
web_ready=false
for attempt in $(seq 1 120); do
  if curl -fsS "http://$api_address/health" >/tmp/magina-staging-api-health.json 2>/dev/null; then
    api_ready=true
  fi
  if curl -fsS "http://$web_address/healthz" >/tmp/magina-staging-web-health.txt 2>/dev/null; then
    web_ready=true
  fi
  if [ "$api_ready" = true ] && [ "$web_ready" = true ]; then
    break
  fi
  sleep 1
done

if [ "$api_ready" != true ] || [ "$web_ready" != true ]; then
  echo "Staging containers did not become healthy in time." >&2
  exit 1
fi

grep -Fx 'ok' /tmp/magina-staging-web-health.txt >/dev/null

echo "Verifying migration runner is a no-op after successful startup..."
second_run="$(compose run --rm migrate 2>&1)"
printf '%s\n' "$second_run"
if printf '%s\n' "$second_run" | grep -F 'Applying /migrations/'; then
  echo "Migration runner attempted to reapply an existing migration." >&2
  exit 1
fi

compose ps
printf 'Local staging health passed. API: http://%s  Web: http://%s\n' "$api_address" "$web_address"
