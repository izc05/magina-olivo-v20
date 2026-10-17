#!/usr/bin/env bash
set -euo pipefail
TMP="$(mktemp -d)"
TMP_ROOT="$(cd "$TMP" && pwd -P)"
cleanup() {
 # Delete only the same resolved temporary directory created by this self-test.
 local resolved
 resolved="$(cd "$TMP" && pwd -P)" || return 1
 [[ -n "$TMP_ROOT" && "$TMP_ROOT" != / && "$resolved" == "$TMP_ROOT" ]] || return 1
 rm -rf -- "$TMP_ROOT"
}
trap cleanup EXIT
mkdir -p "$TMP/bin" "$TMP/work/.github/scripts"
cp .github/scripts/verify-context-notifications.sh .github/scripts/assert-gate3-emulator-safe.sh "$TMP/work/.github/scripts/"
cat > "$TMP/bin/adb" <<'FAKE'
#!/usr/bin/env bash
printf '%s\n' "$*" >> "$FAKE_TRACE"
case "$*" in
 get-serialno) echo emulator-5554 ;;
 'shell getprop ro.kernel.qemu'|'shell getprop ro.boot.qemu') echo 1 ;;
 'shell dumpsys package '*) echo 'android.permission.POST_NOTIFICATIONS: granted=false, flags=[]' ;;
 'shell am instrument '*)
  if [[ "${FAKE_JUNIT_FAIL:-0}" == 1 ]]; then
   printf 'FAILURES!!!\nTests run: 1, Failures: 1\n'
  else echo 'OK (1 test)'; fi
  exit "${FAKE_ADB_RC:-0}" ;;
 'logcat '*) echo 'synthetic logcat' ;;
 pull\ *)
  mkdir -p "$3"
  for capture in room.txt semantics.txt window.txt ime.txt active-window.txt screen.png; do
   [[ "${FAKE_MISSING_CAPTURE:-}" != "$capture" ]] || continue
   printf 'synthetic capture\n' > "$3/$capture"
  done ;;
esac
FAKE
chmod +x "$TMP/bin/adb"
export PATH="$TMP/bin:$PATH" FAKE_TRACE="$TMP/trace" GATE3_ALLOW_APP_DATA_RESET=1
cd "$TMP/work"
CLASS=com.isivoltpro.maginaolivo.MainReminderPersistenceTest
run_case() {
 local output="$1" expected="$2" rc=0
 bash .github/scripts/verify-context-notifications.sh "$output" "$CLASS" later > "$TMP/$output.log" 2>&1 || rc=$?
 [[ "$rc" == "$expected" ]]
}
# Default wrapper remains the original command and does not collect diagnostics.
run_case default 0
! grep -q qaReminderDiagnostics "$FAKE_TRACE"
! grep -q '^pull\|^logcat' "$FAKE_TRACE"
export GATE3_REMINDER_DIAGNOSTICS=1 FAKE_JUNIT_FAIL=1
run_case junit-fail 1
grep -q FAILURES junit-fail/later.log
for capture in room.txt semantics.txt window.txt ime.txt active-window.txt screen.png; do
 test -s "junit-fail/warm-later/device/$capture"
done
test -s junit-fail/warm-later/logcat.txt
test -s junit-fail/permission-after.txt
grep -q 'adb_exit=0' junit-fail/warm-later/result.txt
grep -q 'qaReminderCloseIme false -e qaReminderIteration warm-later' "$FAKE_TRACE"
export FAKE_JUNIT_FAIL=0 FAKE_MISSING_CAPTURE=room.txt
run_case missing 1
grep -q 'MISSING room.txt' missing/warm-later/capture-manifest.txt
test -s missing/warm-later/device/screen.png
test -s missing/permission-after.txt
export FAKE_MISSING_CAPTURE='' FAKE_ADB_RC=7
run_case adb-fail 7
test -s adb-fail/warm-later/logcat.txt
test -s adb-fail/permission-after.txt
export FAKE_ADB_RC=0
run_case pass 0
# Opt-in guard rejects before any permission mutation or instrumentation.
: > "$FAKE_TRACE"
export GATE3_ALLOW_APP_DATA_RESET=0
run_case guard 1
! grep -q 'shell pm\|shell am instrument\|^pull' "$FAKE_TRACE"
echo 'PASS: warm captures precede JUnit failure; missing capture fails; adb status and permission restoration retained; default unchanged; safety guard enforced'
