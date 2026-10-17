#!/usr/bin/env bash
set -euo pipefail

# Synthetic private fixtures only. Reuse the fail-closed disposable-emulator preflight.
bash .github/scripts/assert-gate3-emulator-safe.sh
export ANDROID_SERIAL="$(adb get-serialno | tr -d '\r')"
fixture="$(cat /proc/sys/kernel/random/uuid)"
[[ "$fixture" =~ ^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$ ]]
pkg="com.isivoltpro.maginaolivo.dev"
class="com.isivoltpro.maginaolivo.FarmLinkProcessTest"
mkdir -p evidence
trace="$(mktemp)"
trap 'rm -f "$trace"' EXIT
: > evidence/g1b-process-recovery.txt
for phase in persist recover; do
  set +e
  adb shell am instrument -w -e class "$class" -e qaG1bPhase "$phase" -e qaG1bFixture "$fixture" \
    "$pkg.test/androidx.test.runner.AndroidJUnitRunner" > "$trace" 2>&1
  rc=$?
  set -e
  cat "$trace" | tee -a evidence/g1b-process-recovery.txt
  if [[ "$rc" != 0 ]] || ! grep -qE '^OK \(1 test\)' "$trace" || \
      grep -qE 'FAILURES!!!|INSTRUMENTATION_FAILED|INSTRUMENTATION_ABORTED' "$trace"; then
    echo "G1b synthetic process phase $phase failed" >&2
    exit 1
  fi
  if [[ "$phase" == persist ]]; then
    # No pm clear: recovery must reopen the same durable consent, DB, files and Keystore identity.
    adb shell am force-stop "$pkg"
    echo 'PASS: durable consent committed; process force-stopped without clearing data' | tee -a evidence/g1b-process-recovery.txt
  fi
done
echo 'PASS: encrypted cached identity, exact selection, original data and bytes recovered offline; account B denied' | tee -a evidence/g1b-process-recovery.txt
