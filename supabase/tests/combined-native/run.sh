#!/usr/bin/env bash
set -euo pipefail
# One disposable native stack: G0, #772 hardening, G1c, G2.1. Never linked to DEV.
cd "$(dirname "$0")/../../.."
command -v supabase >/dev/null
command -v node >/dev/null
command -v docker >/dev/null
docker info >/dev/null
work="$(mktemp -d)"
project="magina-combined-native-$(date +%s)-$$"
echo "Disposable project: $project"
if docker inspect "supabase_db_$project" >/dev/null 2>&1; then
  echo 'Refusing existing container' >&2; exit 1
fi
cleanup() {
  supabase stop --workdir "$work" --project-id "$project" --no-backup >/dev/null 2>&1 || true
  rm -f "$work/status.json" "$work/start.log" "$work/g0-hardening-772.sql"
}
trap cleanup EXIT

# Pin the auxiliary PR to its reviewed commit and exact SQL blob.
g0_commit="c6ee7b749c4f339fc6b460ec7299b17042f7529e"
g0_path="supabase/migrations/20261024125046_g0_function_security_hardening.sql"
git cat-file -e "$g0_commit^{commit}" 2>/dev/null || git fetch --no-tags --depth=1 origin "$g0_commit" >/dev/null
test "$(git rev-parse "$g0_commit:$g0_path")" = "8356c47176fc13f2882de40a499c53104bf350d8"
git show "$g0_commit:$g0_path" > "$work/g0-hardening-772.sql"

supabase init --workdir "$work" >/dev/null
node --input-type=module - "$work/supabase/config.toml" "$project" <<'JS'
import { readFileSync, writeFileSync } from 'node:fs';
const [file, project] = process.argv.slice(2);
let config = readFileSync(file, 'utf8').replace(/^project_id = .*$/m, `project_id = "${project}"`);
config = config.replace(/^major_version = \d+$/m, 'major_version = 17');
if (!/^enable_anonymous_sign_ins = false$/m.test(config)) throw Error('Review anonymous Auth config before running');
config = config.replace(/^enable_anonymous_sign_ins = false$/m, 'enable_anonymous_sign_ins = true');
writeFileSync(file, config);
JS
if ! supabase start --workdir "$work" --exclude studio,postgres-meta,imgproxy,realtime,edge-runtime,logflare,vector,supavisor > "$work/start.log" 2>&1; then
  echo 'Disposable native stack failed to start; credentials/logs removed during cleanup' >&2; exit 1
fi
supabase status --workdir "$work" --output json > "$work/status.json"

echo 'Migration order: five G0 base -> #772 hardening -> G1c -> G2.1'
MAGINA_COMBINED_NATIVE=1 G1C_NATIVE_WORK="$work" G1C_NATIVE_PROJECT="$project" \
  node --test --test-concurrency=1 supabase/tests/g1c-native/reservations.test.mjs
MAGINA_COMBINED_NATIVE=1 G2_NATIVE_WORK="$work" G2_NATIVE_PROJECT="$project" \
  node --test --test-concurrency=1 supabase/tests/g2-native/sync_own_data.test.mjs supabase/tests/g2-native/jsonb_hash_contract.test.mjs
