#!/usr/bin/env bash
set -euo pipefail
# APK-only acceptance A2 <-> B2: the normal DEV debug APK, its login screen and its «Sincronizar ahora» button, against a
# DISPOSABLE local Supabase. Never DEV or production; NEVER the historical A/B emulators (refused by AVD name).
#
# Start the two disposable emulators with the fail-closed DNS of this bridge first, for example:
#   emulator -avd MaginaOlivo_AB_A2_Disposable_API35 -port 5582 -dns-server 127.0.0.1 -no-snapshot-save &
#   emulator -avd MaginaOlivo_AB_B2_Disposable_API35 -port 5584 -dns-server 127.0.0.1 -no-snapshot-save &
# then:
#   AB_SERIAL_A=emulator-5582 AB_SERIAL_B=emulator-5584 bash supabase/tests/g26-ab-apk/start.sh
#
# It stays up until interrupted, printing the test account to sign in with on both phones. Everything is removed on exit.
cd "$(dirname "$0")/../../.."
export MSYS_NO_PATHCONV=1   # Git Bash on Windows: keep /CN=… and /data/… device paths literal
for tool in supabase docker openssl adb node gradle; do command -v "$tool" >/dev/null || { echo "missing $tool" >&2; exit 2; }; done
docker info >/dev/null
A="${AB_SERIAL_A:?AB_SERIAL_A required}"; B="${AB_SERIAL_B:?AB_SERIAL_B required}"
[[ "$A" != "$B" ]] || { echo 'A2 and B2 must differ' >&2; exit 2; }
for pair in "$A:A2" "$B:B2"; do
  serial="${pair%%:*}"; want="${pair##*:}"
  avd="$(adb -s "$serial" emu avd name 2>/dev/null | head -1 | tr -d '\r')"
  [[ "$avd" == "MaginaOlivo_AB_${want}_Disposable_API35" ]] || { echo "Refusing $serial: '$avd' is not the disposable $want" >&2; exit 2; }
done
host=zubpicdibqbmewxeurhm.supabase.co
work="$(mktemp -d)"; command -v cygpath >/dev/null && work="$(cygpath -m "$work")"
project="mg26-apk-$(date +%s)-$$"
server_pid=''
cleanup() {
  [[ -n "$server_pid" ]] && { kill "$server_pid" >/dev/null 2>&1 || true; wait "$server_pid" 2>/dev/null || true; }
  supabase stop --workdir "$work" --project-id "$project" --no-backup >/dev/null 2>&1 || true
  rm -rf -- "$work"
}
trap cleanup EXIT
supabase init --workdir "$work" >/dev/null
prefix="${G26_PORT_PREFIX:-581}"   # local ports <prefix>20-29, never the default 543xx of other local stacks
[[ "$prefix" =~ ^[1-6][0-9]{2}$ && "$prefix" != 543 ]] || { echo "bad port prefix" >&2; exit 2; }
node --input-type=module - "$work/supabase/config.toml" "$project" "$prefix" <<'JS'
import {readFileSync,writeFileSync} from 'node:fs';
const [file,project,prefix]=process.argv.slice(2);
writeFileSync(file,readFileSync(file,'utf8').replace(/^project_id = .*$/m,`project_id = "${project}"`).replace(/^major_version = \d+$/m,'major_version = 17').replace(/= 543(\d\d)$/mg,`= ${prefix}$1`));
JS
supabase start --workdir "$work" --exclude studio,postgres-meta,imgproxy,realtime,edge-runtime,logflare,vector,supavisor > "$work/start.log" 2>&1 \
  || { echo 'Disposable Supabase startup failed' >&2; tail -20 "$work/start.log" >&2; exit 1; }
supabase status --workdir "$work" --output json > "$work/status.json"

# Throw-away CA (one day) and the bridge certificate for the DEV host name.
openssl req -x509 -newkey rsa:2048 -nodes -keyout "$work/ca-key.pem" -out "$work/ca.pem" -days 1 \
  -subj '/CN=Magina disposable QA CA' -addext 'basicConstraints=critical,CA:TRUE' -addext 'keyUsage=critical,keyCertSign,cRLSign' >/dev/null 2>&1
openssl req -newkey rsa:2048 -nodes -keyout "$work/tls-key.pem" -out "$work/tls.csr" -subj "/CN=$host" >/dev/null 2>&1
printf 'subjectAltName=DNS:%s\nextendedKeyUsage=serverAuth\nbasicConstraints=CA:FALSE\n' "$host" > "$work/ext.cnf"
openssl x509 -req -in "$work/tls.csr" -CA "$work/ca.pem" -CAkey "$work/ca-key.pem" -CAcreateserial -days 1 \
  -extfile "$work/ext.cnf" -out "$work/tls-cert.pem" >/dev/null 2>&1

G26_WORK="$work" G26_PROJECT="$project" G26_TLS_KEY="$work/tls-key.pem" G26_TLS_CERT="$work/tls-cert.pem" \
  node supabase/tests/g26-ab-apk/server.mjs > "$work/server.log" 2>&1 &
server_pid=$!
for _ in $(seq 1 90); do grep -q G26_APK_READY "$work/server.log" && break; kill -0 "$server_pid" 2>/dev/null || { cat "$work/server.log" >&2; exit 1; }; sleep 1; done
grep -q G26_APK_READY "$work/server.log" || { echo 'bridge timeout' >&2; exit 1; }

# The disposable emulators only: the throw-away CA as a user CA, and no captive-portal probe (its hosts are refused).
hash="$(openssl x509 -subject_hash_old -noout -in "$work/ca.pem")"
for serial in "$A" "$B"; do
  adb -s "$serial" root >/dev/null; adb -s "$serial" wait-for-device
  adb -s "$serial" push "$work/ca.pem" "/data/misc/user/0/cacerts-added/$hash.0" >/dev/null
  adb -s "$serial" shell "chown system:system /data/misc/user/0/cacerts-added/$hash.0 && chmod 644 /data/misc/user/0/cacerts-added/$hash.0"
  adb -s "$serial" shell settings put global captive_portal_mode 0
  adb -s "$serial" shell settings put global captive_portal_detection_enabled 0
  adb -s "$serial" shell 'svc wifi disable; svc data disable; sleep 2; svc wifi enable; svc data enable' >/dev/null 2>&1 || true
done

# The normal DEV debug APK, configured for the DEV host name (resolved to this bridge only on these emulators).
anon="$(node -e "process.stdout.write(JSON.parse(require('fs').readFileSync(process.argv[1],'utf8')).ANON_KEY)" "$work/status.json")"
gradle --no-daemon -q assembleDevDebug -PMAGINA_AUTH_DEV_URL="https://$host" -PMAGINA_AUTH_DEV_PUBLIC_KEY="$anon" >/dev/null
apk=app/build/outputs/apk/dev/debug/app-dev-debug.apk
for serial in "$A" "$B"; do
  adb -s "$serial" uninstall com.isivoltpro.maginaolivo.dev >/dev/null 2>&1 || true
  adb -s "$serial" install -r "$apk" >/dev/null
done
mkdir -p "${G26_APK_OUT:-$work}"
cp "$apk" "${G26_APK_OUT:-$work}/magina-olivo-dev-local-qa.apk"
node -e "const a=JSON.parse(require('fs').readFileSync(process.argv[1],'utf8'));console.log('G26_APK_ACCOUNT '+a.email+' '+a.password)" "$work/account.json"
printf 'G26_APK_STATE curl -s --resolve %s:443:127.0.0.1 --cacert %s https://%s/__apk/state\n' "$host" "$work/ca.pem" "$host"
echo G26_APK_SESSION_READY
wait "$server_pid"
