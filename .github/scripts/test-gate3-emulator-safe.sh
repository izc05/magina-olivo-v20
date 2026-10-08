#!/usr/bin/env bash
set -euo pipefail

# Pure unit tests: no real adb calls; a fake adb answers only read-only probes.
root="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
cat > "$tmp/adb" <<'FAKE_ADB'
#!/usr/bin/env bash
set -euo pipefail
case "$*" in
  get-serialno) printf '%s\n' "${FAKE_ADB_SERIAL:-}" ;;
  "shell getprop ro.kernel.qemu") printf '%s\n' "${FAKE_ADB_KERNEL:-}" ;;
  "shell getprop ro.boot.qemu") printf '%s\n' "${FAKE_ADB_BOOT:-}" ;;
  *) echo "Unexpected adb call: $*" >&2; exit 99 ;;
esac
FAKE_ADB
chmod +x "$tmp/adb"

run_case() {
  GATE3_ALLOW_APP_DATA_RESET="$1" FAKE_ADB_SERIAL="$2" \
    FAKE_ADB_KERNEL="$3" FAKE_ADB_BOOT="$4" PATH="$tmp:$PATH" \
    bash "$root/assert-gate3-emulator-safe.sh"
}
expect_rejected() {
  local label="$1"
  shift
  if run_case "$@" > "$tmp/output.txt" 2>&1; then
    echo "FAIL: $label was allowed" >&2
    exit 1
  fi
  echo "PASS: rejected $label"
}

expect_rejected "no explicit opt-in" "" "emulator-5554" "1" ""
expect_rejected "physical device despite opt-in" "1" "R58N1234ABC" "0" ""
expect_rejected "serial spoof without QEMU" "1" "emulator-5554" "0" "0"
expect_rejected "unknown serial" "1" "unknown" "1" ""

run_case "1" "emulator-5554" "1" "" > "$tmp/output.txt" 2>&1 || {
  echo "FAIL: opted-in QEMU emulator was rejected" >&2
  cat "$tmp/output.txt" >&2
  exit 1
}
echo "PASS: allowed opted-in QEMU emulator (kernel property)"
run_case "1" "emulator-5556" "" "1" > "$tmp/output.txt" 2>&1 || {
  echo "FAIL: opted-in boot-QEMU emulator was rejected" >&2
  cat "$tmp/output.txt" >&2
  exit 1
}
echo "PASS: allowed opted-in QEMU emulator (boot property)"
echo "Gate 3 safety preflight: all 6 cases passed"
