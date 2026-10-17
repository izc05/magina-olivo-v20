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
SOURCE=.github/scripts/gate3-emulator-evidence.sh
mkdir -p "$TMP/bin" "$TMP/work"
# Exercise the actual full-suite command/pull/assert block without running a device.
awk '/^assert_instrumentation_passed\(\)/ {copy=1} copy {print} copy && /^}/ {exit}' "$SOURCE" > "$TMP/assert.sh"
awk '/^FAILURE_DIAGNOSTIC_ARGS=/ {copy=1} /^# Native GPS/ {exit} copy {print}' "$SOURCE" > "$TMP/block.sh"
cat > "$TMP/bin/adb" <<'FAKE'
#!/usr/bin/env bash
printf '%s\n' "$*" >> "$FAKE_TRACE"
case "$*" in
 'shell am instrument '*)
  if [[ "${FAKE_FAIL:-0}" == 1 ]]; then printf 'FAILURES!!!\nTests run: 827, Failures: 3\n';
  else echo 'OK (827 tests)'; fi
  exit "${FAKE_ADB_RC:-0}" ;;
 pull\ *failure-evidence*)
  [[ "${FAKE_PULL_FAIL:-0}" != 1 ]] || exit 9
  for sentinel in synthetic-sentinel synthetic-sentinel-unavailable; do
   folder="$3/com.isivoltpro.maginaolivo.FailureEvidenceRuleTest-$sentinel"
   mkdir -p "$folder"
   for capture in failure.txt semantics.txt screen.png active-window.txt window.txt ime.txt activity.txt logcat.txt; do
    [[ "${FAKE_MISSING:-}" != "$capture" ]] || continue
    echo 'synthetic sentinel evidence' > "$folder/$capture"
   done
  done ;;
esac
FAKE
chmod +x "$TMP/bin/adb"
export PATH="$TMP/bin:$PATH" FAKE_TRACE="$TMP/trace"
export BLOCK="$TMP/block.sh" ASSERT="$TMP/assert.sh"
cat > "$TMP/runner.sh" <<'RUNNER'
#!/usr/bin/env bash
set -euo pipefail
cd "$1"
PKG=com.isivoltpro.maginaolivo.dev
TEST_PKG="$PKG.test"
RUNNER=androidx.test.runner.AndroidJUnitRunner
annotate_instrumentation_failure() { echo 'ASSERT_FAILURE' >> "$FAKE_TRACE"; }
source "$ASSERT"
source "$BLOCK"
RUNNER
run_case() {
 local name="$1" expected="$2" rc=0
 mkdir -p "$TMP/work/$name/evidence"
 bash "$TMP/runner.sh" "$TMP/work/$name" > "$TMP/$name.log" 2>&1 || rc=$?
 [[ "$rc" == "$expected" ]]
}
export GATE3_REMINDER_DIAGNOSTICS=1 FAKE_FAIL=1
run_case warm-fail 1
grep -q 'qaFailureDiagnostics true' "$FAKE_TRACE"
test -s "$TMP/work/warm-fail/evidence/failure-evidence/com.isivoltpro.maginaolivo.FailureEvidenceRuleTest-synthetic-sentinel/failure.txt"
grep -q FAILURES "$TMP/work/warm-fail/evidence/instrumentation-all.txt"
awk '/^pull .*failure-evidence/ {pulled=1} /ASSERT_FAILURE/ {if (!pulled) exit 1; seen=1} END {if (!seen) exit 1}' "$FAKE_TRACE"
: > "$FAKE_TRACE"
export GATE3_REMINDER_DIAGNOSTICS=0 FAKE_FAIL=0
run_case default 0
! grep -q 'qaFailureDiagnostics\|^pull .*failure-evidence' "$FAKE_TRACE"
: > "$FAKE_TRACE"
export GATE3_REMINDER_DIAGNOSTICS=1 FAKE_ADB_RC=7
run_case adb-fail 1
grep -q 'failed (adb rc=7)' "$TMP/adb-fail.log"
test -s "$TMP/work/adb-fail/evidence/failure-evidence/com.isivoltpro.maginaolivo.FailureEvidenceRuleTest-synthetic-sentinel/failure.txt"
export FAKE_ADB_RC=0 FAKE_PULL_FAIL=1
run_case pull-fail 1
grep -q 'Warm full suite passed but required sentinel evidence' "$TMP/pull-fail.log"
export FAKE_FAIL=1
run_case junit-and-pull-fail 1
grep -q 'Full instrumented test suite failed (adb rc=0)' "$TMP/junit-and-pull-fail.log"
! grep -q 'Warm full suite passed but required sentinel evidence' "$TMP/junit-and-pull-fail.log"
export FAKE_FAIL=0
export FAKE_PULL_FAIL=0 FAKE_MISSING=screen.png
run_case missing 1
grep -q 'MISSING .*screen.png' "$TMP/work/missing/evidence/failure-evidence-manifest.txt"
export FAKE_MISSING=''
run_case warm-pass 0
[[ "$(grep -c '^PRESENT ' "$TMP/work/warm-pass/evidence/failure-evidence-manifest.txt")" == 16 ]]
# Rule nesting is essential: the failed callback runs before Compose tears down.
BASE=app/src/androidTest/java/com/isivoltpro/maginaolivo
for file in AppNavigationTest MainActivitySmokeTest; do
 grep -A1 '\.around(composeRule)' "$BASE/$file.kt" | grep -q '\.around(FailureEvidenceRule'
done
grep -A1 'RuleChain.outerRule(compose)' "$BASE/MapSearchScreenTest.kt" | grep -q '\.around(FailureEvidenceRule'
grep -q 'qaFailureDiagnostics.*true' "$BASE/FailureEvidenceRule.kt"
grep -q 'onFailure(error::addSuppressed)' "$BASE/FailureEvidenceRule.kt"
! grep -q '| head\|shell(.*|' "$BASE/FailureEvidenceRule.kt"
grep -q 'WINDOW MANAGER' "$BASE/FailureEvidenceRuleTest.kt"
grep -q 'Input Method Manager' "$BASE/FailureEvidenceRuleTest.kt"
grep -q 'ACTIVITY MANAGER ACTIVITIES' "$BASE/FailureEvidenceRuleTest.kt"
grep -q 'Expected structured threadtime logcat' "$BASE/FailureEvidenceRuleTest.kt"
echo 'PASS: warm-only arguments; pull before original JUnit/adb failure; pull/missing-capture failures reject warm PASS; both sentinels required; default unchanged; watcher inside Compose'
