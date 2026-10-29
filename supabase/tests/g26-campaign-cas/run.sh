#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../../.."
command -v supabase >/dev/null
command -v node >/dev/null
docker info >/dev/null
work="$(mktemp -d)"
project="magina-g26-cas-$(date +%s)-$$"
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
import {readFileSync,writeFileSync} from 'node:fs';
const [file,project]=process.argv.slice(2);
let config=readFileSync(file,'utf8').replace(/^project_id = .*$/m,`project_id = "${project}"`);
config=config.replace(/^major_version = \d+$/m,'major_version = 17');
writeFileSync(file,config);
JS
if ! supabase start --workdir "$work" --exclude studio,postgres-meta,imgproxy,realtime,edge-runtime,logflare,vector,supavisor > "$work/start.log" 2>&1; then
  echo 'Disposable Supabase startup failed; local logs may contain credentials' >&2; exit 1
fi
supabase status --workdir "$work" --output json > "$work/status.json"
G26_CAS_WORK="$work" G26_CAS_PROJECT="$project" node --test supabase/tests/g26-campaign-cas/contract.test.mjs
