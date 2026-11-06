#!/usr/bin/env bash
set -euo pipefail
# #808 gate J: server-side Jornada coherence (kilos, split, delete order) on a disposable Supabase.
# Never DEV or production. No Android, no emulator.
cd "$(dirname "$0")/../../.."
source supabase/tests/lib/diagnostics.sh
command -v supabase >/dev/null
command -v node >/dev/null
docker info >/dev/null
work="$(mktemp -d)"
project="mg26-jc-$(date +%s)-$$"
if (( ${#project} > 40 )); then echo "Project ID longer than 40 characters: ${#project}" >&2; exit 1; fi
if docker inspect "supabase_db_$project" >/dev/null 2>&1; then
  echo 'Refusing existing container' >&2; exit 1
fi
cleanup() {
  local status=$?
  if [[ $status -ne 0 ]]; then g26_diagnostics "$work" '' "$(basename "$(dirname "$0")")" "$project"; fi
  supabase stop --workdir "$work" --project-id "$project" --no-backup >/dev/null 2>&1 || true
  rm -f -- "$work/status.json" "$work/start.log"
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
  echo 'Disposable Supabase startup failed; sanitized diagnostics follow; raw logs are deleted' >&2; exit 1
fi
supabase status --workdir "$work" --output json > "$work/status.json"
docker exec "supabase_db_$project" psql -X -U postgres -d postgres -At -c "SELECT 'G808-JORNADA-HOST '||version()"
G26_JC_WORK="$work" G26_JC_PROJECT="$project" \
  node --test --test-concurrency=1 supabase/tests/g26-jornada-coherence/contract.test.mjs
