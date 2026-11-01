#!/usr/bin/env bash
# Sanitized failure evidence for disposable Supabase runners. Sourced, never executed directly.
# Raw logs may contain JWTs, API keys, DB URLs and fixture passwords: only the redacted view is printed.

g26_sanitize() {
  sed -E \
    -e 's/eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]*/[REDACTED_JWT]/g' \
    -e 's/sb_(secret|publishable)_[A-Za-z0-9_-]+/[REDACTED_KEY]/g' \
    -e 's/Fixture![A-Za-z0-9-]+/[REDACTED_PASSWORD]/g' \
    -e 's#postgres(ql)?://[^[:space:]"]+#[REDACTED_DB_URL]#g' \
    -e 's/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/[REDACTED_EMAIL]/g' \
    -e 's/((key|secret|password|token|authorization|apikey)["]?[[:space:]]*[:=][[:space:]]*["]?)[^[:space:]",]+/\1[REDACTED]/Ig' \
    -e 's/[A-Za-z0-9+\/_=-]{40,}/[REDACTED_LONG]/g'
}

# g26_diagnostics <workdir> <server_pid|empty> <label>
g26_diagnostics() {
  local work="$1" pid="$2" label="$3"
  echo "::group::$label sanitized diagnostics"
  if [[ -n "$pid" ]]; then
    if kill -0 "$pid" 2>/dev/null; then echo "fixture process: running"; else
      wait "$pid" 2>/dev/null; echo "fixture process: exited (status $?)"; fi
  fi
  for log in server.log start.log instrument.out; do
    if [[ -s "$work/$log" ]]; then
      echo "--- $log (last 80 lines, redacted) ---"
      tail -n 80 "$work/$log" | g26_sanitize
    fi
  done
  echo "--- listeners on fixture and Supabase ports ---"
  (ss -ltnp 2>/dev/null || true) | grep -E ':(54443|5432[0-9]|5433[0-9])[[:space:]]' | g26_sanitize || echo "(none)"
  echo "--- supabase containers ---"
  (docker ps -a --format '{{.Names}} {{.Status}}' 2>/dev/null || true) | grep '^supabase_' | g26_sanitize || echo "(none)"
  echo "::endgroup::"
}
