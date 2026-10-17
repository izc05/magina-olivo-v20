#!/usr/bin/env bash
set -euo pipefail
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$TMP/bin" "$TMP/work/.github/scripts"
cp .github/scripts/reminder-save-diagnostics.sh .github/scripts/assert-gate3-emulator-safe.sh "$TMP/work/.github/scripts/"
cat > "$TMP/bin/adb" <<'FAKE'
#!/usr/bin/env bash
printf '%s\n' "$*" >> "$FAKE_TRACE"
case "$*" in
 get-serialno) echo emulator-5554 ;;
 'shell getprop ro.kernel.qemu'|'shell getprop ro.boot.qemu') echo 1 ;;
 pull\ *)
   if [[ "${FAKE_MISSING_CAPTURE:-0}" != 1 ]]; then
     mkdir -p "$3"
     for capture in room.txt semantics.txt active-window.txt ime.txt window.txt screen.png; do
       printf 'synthetic capture\n' > "$3/$capture"
     done
   fi ;;
 'shell am instrument '*)
   if [[ "${FAKE_FAIL_ORIGINAL:-0}" == 1 && "$*" == *'qaReminderIteration original-1 '* ]]; then
     printf 'FAILURES!!!\nTests run: 1, Failures: 1\n'
   else
     echo 'OK (1 test)'
   fi ;;
esac
FAKE
chmod +x "$TMP/bin/adb"
export PATH="$TMP/bin:$PATH" FAKE_TRACE="$TMP/trace" GATE3_ALLOW_APP_DATA_RESET=1
# Run in a temporary directory so the self-test never overwrites real evidence.
cd "$TMP/work"
git init -q
git -c user.name=QA -c user.email=qa@example.test commit -q --allow-empty -m fixture
export FAKE_FAIL_ORIGINAL=1
set +e
bash .github/scripts/reminder-save-diagnostics.sh > "$TMP/failed-output" 2>&1
rc=$?
set -e
[[ "$rc" != 0 ]]
[[ "$(grep -c '^original' evidence/reminder-save-diagnostics/results.tsv)" == 10 ]]
[[ "$(grep -c '^close-ime' evidence/reminder-save-diagnostics/results.tsv)" == 5 ]]
grep -q $'original\t1\t0\tFAIL' evidence/reminder-save-diagnostics/results.tsv
grep -q 'failed_iterations=1' evidence/reminder-save-diagnostics/conclusion.txt
[[ "$(grep -c 'shell pm revoke .*POST_NOTIFICATIONS' "$FAKE_TRACE")" == 15 ]]
! grep -q 'shell pm grant' "$FAKE_TRACE"
[[ "$(grep -c 'qaReminderCloseIme true' "$FAKE_TRACE")" == 5 ]]
[[ "$(grep -c 'qaReminderCloseIme false' "$FAKE_TRACE")" == 10 ]]
[[ -f evidence/reminder-save-diagnostics/original-1/instrumentation.txt ]]
[[ -f evidence/reminder-save-diagnostics/close-ime-5/instrumentation.txt ]]
export FAKE_FAIL_ORIGINAL=0
bash .github/scripts/reminder-save-diagnostics.sh > "$TMP/passed-output" 2>&1
grep -q 'failed_iterations=0' evidence/reminder-save-diagnostics/conclusion.txt
rm -rf evidence/reminder-save-diagnostics
export FAKE_MISSING_CAPTURE=1
set +e
bash .github/scripts/reminder-save-diagnostics.sh > "$TMP/missing-output" 2>&1
rc=$?
set -e
[[ "$rc" != 0 ]]
grep -q 'FAIL_DIAGNOSTICS' evidence/reminder-save-diagnostics/results.tsv
grep -q 'MISSING room.txt' evidence/reminder-save-diagnostics/original-1/capture-manifest.txt
echo 'PASS: original failures retained; all-pass succeeds; missing captures fail closed'
