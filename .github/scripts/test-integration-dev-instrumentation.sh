#!/usr/bin/env bash
set -euo pipefail
repo="$(cd "$(dirname "$0")/../.." && pwd -P)"
mkdir -p "$repo/scratch"
scratch="$(cd "$repo/scratch" && pwd -P)"
[[ "$scratch" == "$repo/scratch" ]] || { echo 'Scratch must stay inside this worktree' >&2; exit 1; }
root="$(mktemp -d "$scratch/cr015-ci-XXXXXXXX")"
cleanup() {
  [[ -d "$root" && "$(cd "$root" && pwd -P)" == "$root" && "$root" == "$scratch"/cr015-ci-* ]] || return 1
  rm -rf -- "$root"
}
trap cleanup EXIT
mkdir -p "$root/bin"
cat > "$root/bin/adb" <<'MOCK'
#!/usr/bin/env bash
printf '%s\n' "$*" >> "$MOCK_LOG"
case "$*" in
  get-serialno) echo "${MOCK_SERIAL:-emulator-5554}" ;;
  'shell getprop ro.kernel.qemu'|'shell getprop ro.boot.qemu') echo "${MOCK_QEMU:-1}" ;;
  'shell dumpsys power'|'shell dumpsys window'|'shell dumpsys input') echo 'synthetic device diagnostics' ;;
  'shell input keyevent KEYCODE_WAKEUP'|'shell wm dismiss-keyguard'|'shell svc power stayon true') exit 0 ;;
  install*) echo Success ;;
  'shell am instrument'*)
    if [[ "$MOCK_RESULT" == junit-fail ]]; then
      echo 'FAILURES!!!'; echo 'Tests run: 176, Failures: 1'; echo 'INSTRUMENTATION_CODE: -1'
    else
      echo 'OK (176 tests)'; echo 'INSTRUMENTATION_CODE: -1'
      [[ "$MOCK_RESULT" != transport-fail ]] || exit 7
    fi ;;
  pull*) exit 0 ;;
  *) echo "Unexpected adb: $*" >&2; exit 2 ;;
esac
MOCK
chmod +x "$root/bin/adb"
for scenario in pass junit-fail transport-fail physical non-qemu no-opt-in; do
  dir="$root/$scenario"
  mkdir -p "$dir/.github/scripts"
  cp "$repo/.github/scripts/assert-gate3-emulator-safe.sh" "$repo/.github/scripts/integration-dev-instrumentation.sh" "$dir/.github/scripts/"
  serial=emulator-5554; opt=1; qemu=1
  [[ "$scenario" != physical ]] || serial=physical-test-device
  [[ "$scenario" != no-opt-in ]] || opt=0
  [[ "$scenario" != non-qemu ]] || qemu=0
  set +e
  (cd "$dir" && PATH="$root/bin:$PATH" MOCK_LOG="$dir/adb.log" MOCK_RESULT="$scenario" MOCK_SERIAL="$serial" MOCK_QEMU="$qemu" GATE3_ALLOW_APP_DATA_RESET="$opt" bash .github/scripts/integration-dev-instrumentation.sh > output.log 2>&1)
  status=$?
  set -e
  if [[ "$scenario" == pass ]]; then
    [[ "$status" == 0 ]] || { cat "$dir/output.log"; exit 1; }
  else
    [[ "$status" != 0 ]] || { echo "Unexpected success: $scenario"; exit 1; }
  fi
  if [[ "$scenario" == physical || "$scenario" == non-qemu || "$scenario" == no-opt-in ]]; then
    ! grep -Eq '^install|^shell am instrument|^pull|^shell input|^shell wm|^shell svc' "$dir/adb.log" 2>/dev/null || { echo 'Unsafe side effect'; exit 1; }
  else
    [[ $(grep -c '^pull' "$dir/adb.log") == 2 ]] || { echo 'Missing diagnostics'; exit 1; }
    tail -n 2 "$dir/adb.log" | grep -q '^pull' || exit 1
    [[ $(grep -c '^shell dumpsys' "$dir/adb.log") == 9 ]] || { echo 'Missing device diagnostics'; exit 1; }
    grep -q '^shell input keyevent KEYCODE_WAKEUP$' "$dir/adb.log"
    grep -q '^shell wm dismiss-keyguard$' "$dir/adb.log"
    grep -q '^shell svc power stayon true$' "$dir/adb.log"
  fi
  echo "PASS: $scenario (rc=$status)"
done
