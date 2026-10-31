#!/usr/bin/env bash
set -euo pipefail
# Disposable native Supabase only. Never link, deploy, reset or accept a database URL.
cd "$(dirname "$0")/../../.."
command -v supabase >/dev/null
command -v node >/dev/null
docker info >/dev/null
supabase start --help >/dev/null
supabase stop --help >/dev/null
work="$(mktemp -d)"
project="magina-g2-native-$(date +%s)-$$"
echo "Disposable project: $project"
if docker inspect "supabase_db_$project" >/dev/null 2>&1; then
  echo 'Refusing existing container' >&2; exit 1
fi
cleanup() {
  supabase stop --workdir "$work" --project-id "$project" --no-backup >/dev/null 2>&1 || true
  rm -f "$work/status.json" "$work/start.log"
}
trap cleanup EXIT
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
# Raw local credentials stay in temporary storage, never evidence or stdout.
if ! supabase start --workdir "$work" --exclude studio,postgres-meta,imgproxy,realtime,edge-runtime,logflare,vector,supavisor > "$work/start.log" 2>&1; then
  echo 'Native ephemeral stack failed to start; credentials/logs will be removed during cleanup' >&2; exit 1
fi
supabase status --workdir "$work" --output json > "$work/status.json"
G2_NATIVE_WORK="$work" G2_NATIVE_PROJECT="$project" \
  node --test --test-concurrency=1 supabase/tests/g2-native/sync_own_data.test.mjs supabase/tests/g2-native/jsonb_hash_contract.test.mjs
