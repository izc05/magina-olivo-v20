#!/usr/bin/env bash
set -euo pipefail
# No real adb is called. Exercise the exact production harness with synthetic process responses.
calls="$(mktemp)"
output="$(mktemp)"
trap 'rm -f "$calls" "$output"' EXIT
export calls
cat() {
  if [[ "${1:-}" == /proc/sys/kernel/random/uuid ]]; then
    printf '%s\n' '00000000-0000-4000-8000-000000000001'
  else command cat "$@"; fi
}
adb() {
  echo "$*" >> "$calls"
  case "$*" in
    get-serialno) echo "${FAKE_SERIAL:-emulator-9999}" ;;
    'shell getprop ro.kernel.qemu'|'shell getprop ro.boot.qemu') echo 1 ;;
    *'shell am instrument'*qaG1bPhase\ persist*)
      if [[ "${FAKE_FAILURE:-}" == persist ]]; then echo 'FAILURES!!!'; else echo 'OK (1 test)'; fi ;;
    *'shell am instrument'*qaG1bPhase\ recover*)
      if [[ "${FAKE_FAILURE:-}" == recover ]]; then echo 'INSTRUMENTATION_FAILED'; else echo 'OK (1 test)'; fi ;;
    'shell am force-stop com.isivoltpro.maginaolivo.dev') return 0 ;;
    *) echo "Unexpected fake adb command: $*" >&2; return 9 ;;
  esac
}
export -f adb cat
export GATE3_ALLOW_APP_DATA_RESET=1
bash .github/scripts/test-g1b-consent-restart.sh > "$output" 2>&1
[[ "$(grep -c 'shell am instrument' "$calls")" == 2 ]]
[[ "$(grep -c 'shell am force-stop' "$calls")" == 1 ]]
! grep -q 'pm clear' "$calls"
grep -q 'PASS: encrypted cached identity' "$output"
for phase in persist recover; do
  : > "$calls"
  if FAKE_FAILURE="$phase" bash .github/scripts/test-g1b-consent-restart.sh > "$output" 2>&1; then
    echo "Harness incorrectly accepted $phase failure" >&2; exit 1
  fi
  if [[ "$phase" == persist ]]; then ! grep -q 'force-stop\|qaG1bPhase recover' "$calls"; fi
done
: > "$calls"
if FAKE_SERIAL='physical-device' bash .github/scripts/test-g1b-consent-restart.sh > "$output" 2>&1; then
  echo 'Harness incorrectly accepted a physical device' >&2; exit 1
fi
! grep -q 'instrument\|force-stop' "$calls"
echo 'PASS: fake adb recovery sequence, no data clear, both phase failures and physical-device refusal'
