#!/usr/bin/env bash
set -euo pipefail
# #808 Android transport: the Pesada v3 writer against a disposable local Supabase that carries the
# #818 proposal, through a pinned TLS bridge, on the attached disposable API 35 emulator.
# Never DEV or production; no scheduler; no receiver; not the historical A/B emulators.
cd "$(dirname "$0")/../../.."
source supabase/tests/lib/diagnostics.sh
command -v supabase >/dev/null
command -v docker >/dev/null
command -v openssl >/dev/null
command -v adb >/dev/null
docker info >/dev/null
work="$(mktemp -d)"
project="mg26-dv3-$(date +%s)-$$"
if (( ${#project} > 40 )); then echo "Project ID longer than 40 characters: ${#project}" >&2; exit 1; fi
server_pid=''
cleanup() {
  local status=$?
  if [[ $status -ne 0 ]]; then g26_diagnostics "$work" "$server_pid" "$(basename "$(dirname "$0")")" "$project"; fi
  if [[ -n "$server_pid" ]]; then
    kill "$server_pid" >/dev/null 2>&1 || true
    wait "$server_pid" 2>/dev/null || true
  fi
  supabase stop --workdir "$work" --project-id "$project" --no-backup >/dev/null 2>&1 || true
  rm -f -- "$work/status.json" "$work/tls-key.pem" "$work/tls-cert.pem" \
    "$work/server.log" "$work/start.log" "$work/instrument.out"
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
  echo 'Disposable Supabase startup failed; sanitized diagnostics follow; raw logs are deleted' >&2
  exit 1
fi
supabase status --workdir "$work" --output json > "$work/status.json"
if (ss -ltn 2>/dev/null || true) | grep -qE ':54443[[:space:]]'; then
  echo 'Fixture port 54443 is already in use' >&2; exit 1
fi
openssl req -x509 -newkey rsa:2048 -nodes -keyout "$work/tls-key.pem" -out "$work/tls-cert.pem" \
  -subj '/CN=10.0.2.2' -addext 'subjectAltName=IP:10.0.2.2' -days 1 >/dev/null 2>&1
G26_WORK="$work" G26_PROJECT="$project" G26_TLS_KEY="$work/tls-key.pem" \
  G26_TLS_CERT="$work/tls-cert.pem" node supabase/tests/g26-delivery-v3-android/server.mjs \
  > "$work/server.log" 2>&1 &
server_pid=$!
for attempt in $(seq 1 90); do
  if grep -q G26_HTTPS_READY "$work/server.log"; then break; fi
  if ! kill -0 "$server_pid" 2>/dev/null; then
    echo 'Disposable HTTPS fixture failed; sanitized diagnostics follow; raw logs are deleted' >&2
    exit 1
  fi
  sleep 1
done
grep -q G26_HTTPS_READY "$work/server.log" || { echo 'Disposable HTTPS fixture timeout' >&2; exit 1; }
grep G808-HOST "$work/server.log" || true
gradle --no-daemon --max-workers=1 --no-parallel assembleDevDebug assembleDevDebugAndroidTest >/dev/null
adb install -r app/build/outputs/apk/dev/debug/app-dev-debug.apk >/dev/null
adb install -r app/build/outputs/apk/androidTest/dev/debug/app-dev-debug-androidTest.apk >/dev/null
adb shell pm clear com.isivoltpro.maginaolivo.dev >/dev/null
certificate="$(base64 -w0 "$work/tls-cert.pem")"
adb logcat -c
adb shell am instrument -w -e class \
  com.isivoltpro.maginaolivo.data.local.DeliveryV3HttpsEphemeralTest \
  -e g26_test_cert "$certificate" \
  com.isivoltpro.maginaolivo.dev.test/androidx.test.runner.AndroidJUnitRunner \
  > "$work/instrument.out"
cat "$work/instrument.out"
adb logcat -d -s G808:I | grep G808- || echo 'G808 no lines'
grep -q 'OK (5 tests)' "$work/instrument.out"
