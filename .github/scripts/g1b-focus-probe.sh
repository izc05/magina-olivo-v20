#!/usr/bin/env bash
# G1b focus probe. Diagnostic only: reports, never gates.
#
# Reproduces, in isolation, the intermittent RootViewWithoutFocusException of
# AppNavigationTest#centralAddIsAButtonAndCancelsOnEveryRootWithoutNavigation
# (AppNavigationTest.kt:75, the pressBack() right after scenario.recreate()),
# and the post-test cold-start block that failed the evidence run 39130595749.
#
# It touches no shared script and no existing workflow: new file, new workflow,
# own evidence folder. It performs no `pm clear` and no data reset, so it does
# not need GATE3_ALLOW_APP_DATA_RESET; it still refuses anything that is not a
# disposable QEMU emulator.
set -uo pipefail

PKG="com.isivoltpro.maginaolivo.dev"
TEST_PKG="com.isivoltpro.maginaolivo.dev.test"
RUNNER="androidx.test.runner.AndroidJUnitRunner"
ACTIVITY="com.isivoltpro.maginaolivo.MainActivity"
CLASS="com.isivoltpro.maginaolivo.AppNavigationTest#centralAddIsAButtonAndCancelsOnEveryRootWithoutNavigation"
ITERATIONS="${FOCUS_PROBE_ITERATIONS:-6}"
OUT="evidence/g1b-focus-probe"

serial="$(adb get-serialno | tr -d '\r')"
if [[ ! "$serial" =~ ^emulator-[0-9]+$ ]]; then
  echo "Focus probe: refusing physical/unknown device serial: $serial" >&2
  exit 1
fi
kernel_qemu="$(adb shell getprop ro.kernel.qemu | tr -d '\r')"
boot_qemu="$(adb shell getprop ro.boot.qemu | tr -d '\r')"
if [[ "$kernel_qemu" != "1" && "$boot_qemu" != "1" ]]; then
  echo "Focus probe: refusing non-QEMU device $serial" >&2
  exit 1
fi

mkdir -p "$OUT"
: > "$OUT/summary.txt"
echo "serial=$serial iterations=$ITERATIONS" >> "$OUT/summary.txt"

APP_APK="$(find app/build/outputs/apk/dev/debug -name '*.apk' | head -n 1)"
TEST_APK="$(find app/build/outputs/apk/androidTest -name '*.apk' | head -n 1)"
[[ -n "$APP_APK" && -n "$TEST_APK" ]] || { echo "Focus probe: APKs not built" >&2; exit 1; }
adb install -r -t "$APP_APK" > "$OUT/install-app.txt" 2>&1
adb install -r -t "$TEST_APK" > "$OUT/install-test.txt" 2>&1

failures=0
for i in $(seq 1 "$ITERATIONS"); do
  log="$OUT/iteration-$i.log"
  window_samples="$OUT/iteration-$i-window-samples.txt"
  adb logcat -c >/dev/null 2>&1 || true

  # Sample the window state WHILE the test runs: after the fact the activity is
  # already gone, so a single post-mortem dumpsys would not show which window
  # held the focus during the ten seconds Espresso waited.
  (
    while :; do
      echo "--- $(date -u +%H:%M:%S) ---"
      adb shell dumpsys window 2>&1 | grep -E 'mCurrentFocus|mFocusedApp|mInputMethodTarget|imeInputTarget|mHasSurface|isReadyForDisplay' || true
      sleep 2
    done
  ) > "$window_samples" 2>&1 &
  sampler=$!

  adb shell am instrument -w -r -e class "$CLASS" "$TEST_PKG/$RUNNER" > "$log" 2>&1
  rc=$?

  kill "$sampler" >/dev/null 2>&1 || true
  wait "$sampler" 2>/dev/null || true

  if grep -q 'OK (1 test)' "$log"; then
    echo "iteration $i: PASS rc=$rc" | tee -a "$OUT/summary.txt"
    rm -f "$window_samples"
  else
    failures=$((failures + 1))
    echo "iteration $i: FAIL rc=$rc" | tee -a "$OUT/summary.txt"
    grep -E 'RootViewWithoutFocusException|has-window-focus|ComposeTimeoutException|INSTRUMENTATION_STATUS: stack' "$log" \
      | head -20 >> "$OUT/summary.txt" || true
    adb logcat -d -v threadtime > "$OUT/iteration-$i-logcat.txt" 2>&1 || true
    adb shell dumpsys window > "$OUT/iteration-$i-window-after.txt" 2>&1 || true
    adb shell dumpsys activity activities > "$OUT/iteration-$i-activities.txt" 2>&1 || true
    adb shell dumpsys input_method > "$OUT/iteration-$i-input-method.txt" 2>&1 || true
  fi
done

# The block that failed run 39130595749 after every test had passed. Here each
# command's exit code is recorded instead of aborting the script, which is the
# whole point: to learn which one returns non-zero.
cold="$OUT/cold-start.txt"
: > "$cold"
adb logcat -c >/dev/null 2>&1
echo "logcat_clear_rc=$?" >> "$cold"
for n in 1 2 3; do
  adb shell am force-stop "$PKG" >> "$cold" 2>&1
  echo "force_stop_${n}_rc=$?" >> "$cold"
  adb shell am start -W -S -n "$PKG/$ACTIVITY" >> "$cold" 2>&1
  echo "am_start_${n}_rc=$?" >> "$cold"
  sleep 1
done

echo "failures=$failures of $ITERATIONS" | tee -a "$OUT/summary.txt"
cat "$OUT/summary.txt"
cat "$cold"
probe_annotation="$(sed -e 's/%/%25/g' -e 's/\r//g' "$OUT/summary.txt" | awk 'BEGIN { ORS = "%0A" } { print }')"
echo "::notice title=G1b focus probe::${probe_annotation}"

# Diagnostic: a reproduced failure is the result, not a broken job.
exit 0
