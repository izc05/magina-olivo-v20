#!/bin/sh
set -eu

umask 077
SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
COMPOSE_FILE=${COMPOSE_FILE:-$SCRIPT_DIR/docker-compose.yml}
COMPOSE_ENV_FILE=${COMPOSE_ENV_FILE:-$SCRIPT_DIR/.env}
TIMESTAMP=$(date -u +%Y%m%dT%H%M%SZ)
OUTPUT=${1:-$SCRIPT_DIR/backups/magina-staging-$TIMESTAMP.dump}

if [ ! -f "$COMPOSE_ENV_FILE" ]; then
  echo "Staging env file not found: $COMPOSE_ENV_FILE" >&2
  exit 1
fi

mkdir -p "$(dirname -- "$OUTPUT")"

compose() {
  docker compose --env-file "$COMPOSE_ENV_FILE" -f "$COMPOSE_FILE" "$@"
}

compose exec -T postgres sh -ec '
  pg_dump \
    -U "$POSTGRES_USER" \
    -d "$POSTGRES_DB" \
    --format=custom \
    --no-owner \
    --no-acl
' > "$OUTPUT"

if [ ! -s "$OUTPUT" ]; then
  rm -f "$OUTPUT"
  echo "Backup failed: output is empty." >&2
  exit 1
fi

BYTES=$(wc -c < "$OUTPUT" | tr -d ' ')
echo "Backup created: $OUTPUT ($BYTES bytes)"
