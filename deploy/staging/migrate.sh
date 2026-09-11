#!/bin/sh
set -eu

: "${DATABASE_URL:?DATABASE_URL is required}"
MIGRATIONS_DIR="${MIGRATIONS_DIR:-/migrations}"

psql "$DATABASE_URL" -v ON_ERROR_STOP=1 <<'SQL'
CREATE TABLE IF NOT EXISTS public.schema_migrations (
  version TEXT PRIMARY KEY,
  checksum TEXT NOT NULL,
  status TEXT NOT NULL CHECK (status IN ('applying', 'applied')),
  started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  applied_at TIMESTAMPTZ
);
SQL

found=false
for migration in "$MIGRATIONS_DIR"/*.sql; do
  [ -f "$migration" ] || continue
  found=true
  version="$(basename "$migration")"
  checksum="$(sha256sum "$migration" | awk '{print $1}')"

  if ! printf '%s' "$version" | grep -Eq '^[0-9]{4}_[A-Za-z0-9._-]+\.sql$'; then
    echo "Unsafe migration filename: $version" >&2
    exit 1
  fi
  if ! printf '%s' "$checksum" | grep -Eq '^[0-9a-f]{64}$'; then
    echo "Invalid SHA-256 checksum for $version" >&2
    exit 1
  fi

  existing="$(psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -Atc \
    "SELECT checksum || '|' || status FROM public.schema_migrations WHERE version = '$version';")"

  if [ -n "$existing" ]; then
    existing_checksum="${existing%%|*}"
    existing_status="${existing#*|}"
    if [ "$existing_checksum" != "$checksum" ]; then
      echo "Migration checksum mismatch for $version. Applied migrations must never be edited." >&2
      exit 1
    fi
    if [ "$existing_status" = "applying" ]; then
      echo "Migration $version is marked as applying from a previous interrupted/failed run. Inspect the database before retrying." >&2
      exit 1
    fi
    if [ "$existing_status" != "applied" ]; then
      echo "Migration $version has unexpected status: $existing_status" >&2
      exit 1
    fi
    echo "Skipping already applied migration $version"
    continue
  fi

  psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -c \
    "INSERT INTO public.schema_migrations(version, checksum, status) VALUES ('$version', '$checksum', 'applying');"

  echo "Applying $migration"
  if ! psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f "$migration"; then
    echo "Migration $version failed. It remains marked as applying; inspect and reconcile before another deploy." >&2
    exit 1
  fi

  updated="$(psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -Atc \
    "UPDATE public.schema_migrations SET status='applied', applied_at=now() WHERE version='$version' AND status='applying' RETURNING version;")"
  if [ "$updated" != "$version" ]; then
    echo "Migration registry update failed for $version" >&2
    exit 1
  fi
done

if [ "$found" != true ]; then
  echo "No migration files found in $MIGRATIONS_DIR" >&2
  exit 1
fi

pending="$(psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -Atc "SELECT count(*) FROM public.schema_migrations WHERE status <> 'applied';")"
if [ "$pending" != "0" ]; then
  echo "Migration registry contains $pending non-applied row(s)." >&2
  exit 1
fi

echo "Database migrations are up to date."
