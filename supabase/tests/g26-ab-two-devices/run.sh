#!/usr/bin/env bash
set -euo pipefail
# Phase 2: first REAL sync between two disposable emulators A2 -> B2 through a disposable local Supabase and a pinned TLS
# bridge. Never DEV or production; no scheduler; NEVER the historical A/B emulators (refused by AVD name below).
#
#   AB_SERIAL_A=emulator-5582 AB_SERIAL_B=emulator-5584 bash scripts/local-qa/run-isolated-ports.sh g26-ab-two-devices 591
#
# A2 creates and sends (workspace, farm, parcel, campaign, Pesada with its Jornada); B2 receives and verifies; the host
# compares both with the server's own state.
cd "$(dirname "$0")/../../.."
source supabase/tests/lib/diagnostics.sh
command -v supabase >/dev/null
command -v docker >/dev/null
command -v openssl >/dev/null
command -v adb >/dev/null
docker info >/dev/null
A="${AB_SERIAL_A:?AB_SERIAL_A (disposable A2 emulator serial) required}"
B="${AB_SERIAL_B:?AB_SERIAL_B (disposable B2 emulator serial) required}"
[[ "$A" != "$B" ]] || { echo 'A2 and B2 must be two different emulators' >&2; exit 2; }
for pair in "$A:A2" "$B:B2"; do
  serial="${pair%%:*}"; want="${pair##*:}"
  avd="$(adb -s "$serial" emu avd name 2>/dev/null | head -1 | tr -d '\r')"
  [[ "$avd" == "MaginaOlivo_AB_${want}_Disposable_API35" ]] || {
    echo "Refusing $serial: AVD '$avd' is not the disposable $want (historical A/B are never used)" >&2; exit 2; }
done
work="$(mktemp -d)"
project="mg26-ab-$(date +%s)-$$"
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
  rm -f -- "$work/status.json" "$work/tls-key.pem" "$work/tls-cert.pem" "$work/server.log" "$work/start.log" \
    "$work/a2.out" "$work/b2.out"
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
openssl req -x509 -newkey rsa:2048 -nodes -keyout "$work/tls-key.pem" -out "$work/tls-cert.pem" \
  -subj '/CN=10.0.2.2' -addext 'subjectAltName=IP:10.0.2.2,IP:127.0.0.1' -days 1 >/dev/null 2>&1
G26_WORK="$work" G26_PROJECT="$project" G26_TLS_KEY="$work/tls-key.pem" \
  G26_TLS_CERT="$work/tls-cert.pem" node supabase/tests/g26-ab-two-devices/server.mjs \
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
grep G808-AB-HOST "$work/server.log" || true
gradle --no-daemon --max-workers=1 --no-parallel assembleDevDebug assembleDevDebugAndroidTest >/dev/null
certificate="$(base64 -w0 "$work/tls-cert.pem")"
test_class=com.isivoltpro.maginaolivo.data.local.AbTwoDeviceStepTest
runner=com.isivoltpro.maginaolivo.dev.test/androidx.test.runner.AndroidJUnitRunner
for serial in "$A" "$B"; do
  adb -s "$serial" install -r app/build/outputs/apk/dev/debug/app-dev-debug.apk >/dev/null
  adb -s "$serial" install -r app/build/outputs/apk/androidTest/dev/debug/app-dev-debug-androidTest.apk >/dev/null
  adb -s "$serial" shell pm clear com.isivoltpro.maginaolivo.dev >/dev/null
done

# A raw instrumentation run passes only if its single step started and ended with code 0.
step_ok() { tr -d '\r' < "$1" | awk '
  /^INSTRUMENTATION_STATUS_CODE: / { code=$2; if (code == 1) started++; else if (code == 0) passed++; else if (code != 2) bad++ }
  /^INSTRUMENTATION_CODE: / { done=$2 }
  END { exit !(started == 1 && passed == 1 && bad == 0 && done == "-1") }'; }
field() { tr -d '\r' < "$1" | sed -n "s/^INSTRUMENTATION_STATUS: $2=//p" | head -1; }
# The failing step's own evidence (stack, crash message, tail). Never aborts the report itself.
show_failure() { { tr -d '\r' < "$1" | grep -E 'stack=|shortMsg|longMsg|Exception|Error' | head -30; tr -d '\r' < "$1" | tail -15; } || true; }

echo '--- A2: create and send'
adb -s "$A" shell am instrument -r -w -e class "$test_class#a2CreatesFarmCampaignAndPesadaAndSendsThem" \
  -e ab_role A -e g26_test_cert "$certificate" "$runner" > "$work/a2.out"
if ! step_ok "$work/a2.out"; then show_failure "$work/a2.out"; echo 'AB RESULT: FAIL (A2)'; exit 1; fi
result="$(field "$work/a2.out" ab_result)"
[[ -n "$result" ]] || { echo 'A2 produced no result'; exit 1; }
echo "A2 result: $result"

echo '--- B2: receive and verify'
expected="$(printf '%s' "$result" | base64 -w0)"
adb -s "$B" shell am instrument -r -w -e class "$test_class#b2ReceivesEverythingAndMatchesWithoutDuplicates" \
  -e ab_role B -e ab_expected "$expected" -e g26_test_cert "$certificate" "$runner" > "$work/b2.out"
if ! step_ok "$work/b2.out"; then show_failure "$work/b2.out"; echo 'AB RESULT: FAIL (B2)'; exit 1; fi
echo "B2 result: $(field "$work/b2.out" ab_b2)"

echo '--- server state'
workspace="$(printf '%s' "$result" | node -e 'let s="";process.stdin.on("data",d=>s+=d).on("end",()=>process.stdout.write(JSON.parse(s).workspace))')"
server_state="$(curl -s --cacert "$work/tls-cert.pem" "https://127.0.0.1:54443/__ab/state?workspace=$workspace")"
echo "server: $server_state"
RESULT="$result" STATE="$server_state" node -e '
  const r = JSON.parse(process.env.RESULT), s = JSON.parse(process.env.STATE);
  const ones = ["workspaces","farms","parcels","campaigns","campaignParcels","harvests","deliveries","deliveryParcels"];
  const bad = ones.filter(k => Number(s[k]) !== 1);
  if (bad.length) throw new Error("server duplicates or missing rows: " + bad.join(","));
  if (s.campaignStatus !== "HARVEST" || Number(s.deliveryNet) !== r.net || Number(s.harvestWeight) !== r.jornadaWeight ||
      s.farmName !== r.farmName || s.parcelName !== r.parcelName) throw new Error("server facts differ from A2");'
echo 'AB RESULT: PASS (A2 -> server -> B2: same records, same UUIDs, no duplicates, repeat receive unchanged)'
