#!/usr/bin/env bash
set -euo pipefail
# Fresh local stack only. No linked or persistent Supabase project is accepted.
cd "$(dirname "$0")/../../.."
command -v supabase >/dev/null
command -v docker >/dev/null
docker info >/dev/null
work="$(mktemp -d)"
project="magina-g25-native-$(date +%s)-$$"
cleanup() {
  supabase stop --workdir "$work" --project-id "$project" --no-backup >/dev/null 2>&1 || true
}
trap cleanup EXIT
supabase init --workdir "$work" >/dev/null
node --input-type=module - "$work/supabase/config.toml" "$project" <<'JS'
import {readFileSync,writeFileSync} from 'node:fs';
const [file,project]=process.argv.slice(2);
let text=readFileSync(file,'utf8').replace(/^project_id = .*$/m,`project_id = "${project}"`);
text=text.replace(/^major_version = \d+$/m,'major_version = 17');
writeFileSync(file,text);
JS
if ! supabase start --workdir "$work" --exclude studio,postgres-meta,imgproxy,realtime,edge-runtime,logflare,vector,supavisor > "$work/start.log" 2>&1; then
  echo 'Disposable Supabase stack did not start' >&2
  exit 1
fi
supabase status --workdir "$work" --output json > "$work/status.json"
G25_NATIVE_WORK="$work" G25_NATIVE_PROJECT="$project" node --test supabase/tests/g25-native/gateway.test.mjs
