#!/usr/bin/env bash
set -euo pipefail
TMP="$(mktemp -d)"
TMP_ROOT="$(cd "$TMP" && pwd -P)"
cleanup() {
  local resolved
  resolved="$(cd "$TMP" && pwd -P)" || return 1
  [[ -n "$TMP_ROOT" && "$TMP_ROOT" != / && "$resolved" == "$TMP_ROOT" ]] || return 1
  rm -rf -- "$TMP_ROOT"
}
trap cleanup EXIT
mkdir -p "$TMP/bin" "$TMP/source/.github/scripts"
cp .github/scripts/test-g1c-journal-process.sh .github/scripts/assert-gate3-emulator-safe.sh "$TMP/source/.github/scripts/"
cat > "$TMP/bin/adb" <<'FAKE'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$*" >> "$FAKE_TRACE"
case "$*" in
 get-serialno) echo "${FAKE_SERIAL:-emulator-5554}" ;;
 'shell getprop ro.kernel.qemu'|'shell getprop ro.boot.qemu') echo 1 ;;
 'shell am instrument '*)
   checkpoint=''
   previous=''
   for argument in "$@"; do
     [[ "$previous" != qaG1cJournalCheckpoint ]] || checkpoint="$argument"
     previous="$argument"
   done
   [[ -n "$checkpoint" ]]
   printf '%s\n' "$checkpoint" > "$FAKE_STATE/current"
   if [[ "$*" == *'#holdAtCheckpoint '* ]]; then
     [[ "${FAKE_EARLY:-0}" != 1 ]] || { echo 'FAILURES!!! early writer'; exit 0; }
     payload=pending
     [[ "$checkpoint" != receipt-committed ]] || payload=receipt
     hash="$(printf '%s' "$payload" | sha256sum | cut -d ' ' -f1)"
     printf 'checkpoint=%s\npid=4321\njournal_sha256=%s\n' "$checkpoint" "$hash" > "$FAKE_STATE/marker-$checkpoint"
     echo 4321 > "$FAKE_STATE/pid"
     # A real background client remains alive until the harness force-stops DEV.
     while [[ ! -f "$FAKE_STATE/stopped-$checkpoint" ]]; do sleep 0.05; done
     exit 0
   else
     if [[ "${FAKE_RECOVERY_FAIL:-0}" == 1 ]]; then echo 'FAILURES!!!'; echo 'Tests run: 1, Failures: 1'
     else echo 'OK (1 test)'; fi
   fi ;;
 'exec-out run-as '*)
   checkpoint="$(cat "$FAKE_STATE/current")"
   cat "$FAKE_STATE/marker-$checkpoint" ;;
 'shell pidof -s '*)
   if [[ -f "$FAKE_STATE/pid" ]]; then cat "$FAKE_STATE/pid"
   elif [[ "${FAKE_PID_TIMEOUT:-0}" == 1 ]]; then exit 124
   else exit 1; fi ;;
 'shell am force-stop '*)
   if [[ -f "$FAKE_STATE/current" ]]; then touch "$FAKE_STATE/stopped-$(cat "$FAKE_STATE/current")"; fi
   rm -f "$FAKE_STATE/pid" ;;
 pull\ *)
   [[ "${FAKE_PULL_FAIL:-0}" != 1 ]] || exit 9
   checkpoint="$(cat "$FAKE_STATE/current")"
   destination="$3"
   mkdir -p "$destination"
   cp "$FAKE_STATE/marker-$checkpoint" "$destination/marker.txt"
   payload=pending
   [[ "$checkpoint" != receipt-committed ]] || payload=receipt
   printf '%s' "$payload" > "$destination/journal-before.bin"
   printf '%s' "$payload" > "$destination/journal-after-b.bin"
   printf 'receipt' > "$destination/journal-after-a.bin"
   printf 'PASS checkpoint=%s\n' "$checkpoint" > "$destination/result.txt"
   [[ "${FAKE_MISSING:-0}" != 1 ]] || rm "$destination/journal-after-a.bin" ;;
esac
FAKE
chmod +x "$TMP/bin/adb"
export PATH="$TMP/bin:$PATH" G1C_QA_TOKEN=11111111-2222-3333-4444-555555555555
run_case() {
  local name="$1" expected="$2" rc=0
  local work="$TMP/$name"
  mkdir -p "$work" "$work/state"
  cp -R "$TMP/source/.github" "$work/"
  export FAKE_TRACE="$work/trace.txt" FAKE_STATE="$work/state"
  : > "$FAKE_TRACE"
  (cd "$work"; git init -q; git -c user.name=QA -c user.email=qa@example.test commit -q --allow-empty -m fixture)
  (cd "$work"; bash .github/scripts/test-g1c-journal-process.sh) > "$work/output.txt" 2>&1 || rc=$?
  [[ "$rc" == "$expected" ]] || { cat "$work/output.txt"; echo "Unexpected exit $name:$rc"; exit 1; }
  ! grep -q 'shell pm clear' "$FAKE_TRACE"
}
export GATE3_ALLOW_APP_DATA_RESET=1
run_case success 0
[[ "$(grep -c $'\tPASS$' "$TMP/success/evidence/g1c-journal-process/results.tsv")" == 3 ]]
[[ "$(grep -c 'shell am force-stop' "$TMP/success/trace.txt")" == 3 ]]
export FAKE_RECOVERY_FAIL=1
run_case junit-fail 1
test -s "$TMP/junit-fail/evidence/g1c-journal-process/prepared/device/journal-before.bin"
grep -q FAILURES "$TMP/junit-fail/evidence/g1c-journal-process/prepared/recovery.txt"
export FAKE_RECOVERY_FAIL=0 FAKE_EARLY=1
run_case early-writer 1
grep -q FAIL_MARKER "$TMP/early-writer/evidence/g1c-journal-process/results.tsv"
! grep -q recoverAfterForceStop "$TMP/early-writer/trace.txt"
export FAKE_EARLY=0 FAKE_MISSING=1
run_case missing 1
grep -q 'MISSING journal-after-a.bin' "$TMP/missing/evidence/g1c-journal-process/prepared/capture-manifest.txt"
export FAKE_MISSING=0 FAKE_PULL_FAIL=1
run_case pull-fail 1
export FAKE_PULL_FAIL=0 GATE3_ALLOW_APP_DATA_RESET=0
run_case no-opt-in 1
! grep -qE '^(install |shell am force-stop|shell am instrument)' "$TMP/no-opt-in/trace.txt"
export GATE3_ALLOW_APP_DATA_RESET=1 FAKE_SERIAL=physical-test
run_case physical 1
! grep -qE '^(install |shell am force-stop|shell am instrument)' "$TMP/physical/trace.txt"
export FAKE_SERIAL=emulator-5554 G1C_QA_TOKEN=invalid
run_case invalid-token 1
! grep -qE '^(install |shell am force-stop|shell am instrument)' "$TMP/invalid-token/trace.txt"
export G1C_QA_TOKEN=11111111-2222-3333-4444-555555555555 FAKE_PID_TIMEOUT=1
run_case pid-timeout 1
grep -q FAIL_PID_STOP "$TMP/pid-timeout/evidence/g1c-journal-process/results.tsv"
grep -qx 124 "$TMP/pid-timeout/evidence/g1c-journal-process/prepared/pid-after-stop-exit.txt"
! grep -q recoverAfterForceStop "$TMP/pid-timeout/trace.txt"
echo 'PASS: three live-until-stop fake writers; zero-exit JUnit failure retained; early writer, missing/pull evidence, safety, UUID and post-stop PID transport guards reject'
