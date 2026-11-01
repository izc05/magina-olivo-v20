#!/usr/bin/env bash
# Canary check: the sanitized view must never contain secrets the raw logs can hold.
set -euo pipefail
source "$(dirname "$0")/diagnostics.sh"
jwt='eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJjYW5hcnkifQ.c2lnbmF0dXJlLWNhbmFyeQ'
raw="$(cat <<EOF
Authorization: Bearer $jwt
ANON_KEY=sb_publishable_CanaryPublishable123
"SERVICE_ROLE_KEY": "sb_secret_CanarySecret456"
DB_URL=postgresql://postgres:postgres@127.0.0.1:54322/postgres
password=Fixture!0b6c3d2e-canary
g26-parents-a-0000@example.test
JWT_SECRET: super-secret-jwt-token-with-at-least-32-characters-long
AssertionError [ERR_ASSERTION]: /auth/v1/admin/users: 502
EOF
)"
clean="$(printf '%s\n' "$raw" | g26_sanitize)"
for leak in "$jwt" CanaryPublishable123 CanarySecret456 'postgres:postgres@' 'Fixture!0b6c' \
  '@example.test' 'super-secret-jwt-token'; do
  if grep -qF -- "$leak" <<<"$clean"; then echo "LEAK: $leak" >&2; exit 1; fi
done
# Useful evidence survives.
grep -qF '/auth/v1/admin/users: 502' <<<"$clean"
grep -qF 'AssertionError' <<<"$clean"
echo 'diagnostics sanitizer: ok'
