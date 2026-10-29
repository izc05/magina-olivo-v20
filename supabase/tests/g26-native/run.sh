#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../../.."
command -v docker >/dev/null
docker info >/dev/null
work="$(mktemp -d)"
project="magina-g26-native-$(date +%s)-$$"
cleanup() { npx --yes supabase@2.79.0 stop --workdir "$work" --project-id "$project" --no-backup >/dev/null 2>&1 || true; }
trap cleanup EXIT
npx --yes supabase@2.79.0 init --workdir "$work" >/dev/null
node --input-type=module - "$work/supabase/config.toml" "$project" <<'JS'
import {readFileSync,writeFileSync} from 'node:fs';
const [file,project]=process.argv.slice(2);
let source=readFileSync(file,'utf8').replace(/^project_id = .*$/m,`project_id = "${project}"`);
source=source.replace(/^major_version = \d+$/m,'major_version = 17');
writeFileSync(file,source);
JS
npx --yes supabase@2.79.0 start --workdir "$work" --exclude studio,postgres-meta,imgproxy,realtime,edge-runtime,logflare,vector,supavisor > "$work/start.log" 2>&1
npx --yes supabase@2.79.0 status --workdir "$work" --output json > "$work/status.json"
G26_NATIVE_WORK="$work" G26_NATIVE_PROJECT="$project" node --test supabase/tests/g26-native/authorization.test.mjs
