#!/usr/bin/env bash
set -euo pipefail

# Fresh CI emulator only. The shared preflight precedes every install/reset mutation.
bash .github/scripts/assert-gate3-emulator-safe.sh
export ANDROID_SERIAL="$(adb get-serialno | tr -d '\r')"
OUT=evidence/reminder-save-diagnostics
PKG=com.isivoltpro.maginaolivo.dev
CLASS='com.isivoltpro.maginaolivo.MainReminderPersistenceTest#failedPlanRetryAndRecreationPreserveReminder'
mkdir -p "$OUT"
git rev-parse HEAD > "$OUT/checkout-sha.txt"
adb install -r app/build/outputs/apk/dev/debug/app-dev-debug.apk > "$OUT/install-app.txt"
adb install -r app/build/outputs/apk/androidTest/dev/debug/app-dev-debug-androidTest.apk > "$OUT/install-test.txt"
printf 'mode\titeration\tadb_exit\tresult\n' > "$OUT/results.tsv"
failed=0
for mode in original close-ime; do
  close_ime=false
  [[ "$mode" != close-ime ]] || close_ime=true
  repeats=10
  [[ "$mode" != close-ime ]] || repeats=5
  for ((iteration=1; iteration<=repeats; iteration++)); do
    label="$mode-$iteration"
    directory="$OUT/$label"
    mkdir -p "$directory"
    adb shell pm clear "$PKG" > "$directory/reset.txt"
    # Match verify-context-notifications.sh: the test sees denied permission even
    # when that wrapper captured granted=true as the state to restore afterward.
    adb shell pm revoke "$PKG" android.permission.POST_NOTIFICATIONS
    adb shell pm clear-permission-flags "$PKG" android.permission.POST_NOTIFICATIONS user-set user-fixed
    adb shell dumpsys package "$PKG" > "$directory/package-before.txt"
    adb logcat -c
    set +e
    adb shell am instrument -w -r -e class "$CLASS" \
      -e notificationQaMode later -e qaReminderDiagnostics true \
      -e qaReminderCloseIme "$close_ime" -e qaReminderIteration "$label" \
      "$PKG.test/androidx.test.runner.AndroidJUnitRunner" > "$directory/instrumentation.txt" 2>&1
    rc=$?
    set -e
    # Preserve each failed experiment; subsequent iterations never overwrite its result.
    result=FAIL
    if [[ "$rc" == 0 ]] && grep -qE '^OK \(1 test\)' "$directory/instrumentation.txt" &&
       ! grep -qE 'FAILURES!!!|INSTRUMENTATION_FAILED|INSTRUMENTATION_ABORTED|Process crashed' "$directory/instrumentation.txt"; then
      result=PASS
    fi
    capture_failed=0
    adb logcat -d > "$directory/logcat.txt" 2>&1 || capture_failed=1
    adb pull "/sdcard/Android/data/$PKG/files/reminder-save-diagnostics/$label" "$directory/device" > "$directory/pull.txt" 2>&1 || capture_failed=1
    : > "$directory/capture-manifest.txt"
    for capture in room.txt semantics.txt active-window.txt ime.txt window.txt screen.png; do
      if [[ -s "$directory/device/$capture" ]]; then
        printf 'present %s\n' "$capture" >> "$directory/capture-manifest.txt"
      else
        printf 'MISSING %s\n' "$capture" >> "$directory/capture-manifest.txt"
        capture_failed=1
      fi
    done
    if [[ "$capture_failed" != 0 ]]; then
      [[ "$result" != PASS ]] || result=FAIL_DIAGNOSTICS
    fi
    [[ "$result" == PASS ]] || failed=$((failed+1))
    printf '%s\t%s\t%s\t%s\n' "$mode" "$iteration" "$rc" "$result" | tee -a "$OUT/results.tsv"
    cat "$directory/instrumentation.txt"
  done
done
printf 'failed_iterations=%s\n' "$failed" | tee "$OUT/conclusion.txt"
# Intentionally fail the job when any original or experimental iteration fails.
[[ "$failed" == 0 ]]
