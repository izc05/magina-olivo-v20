#!/usr/bin/env bash
set -euo pipefail
bash .github/scripts/assert-gate3-emulator-safe.sh
export ANDROID_SERIAL="$(adb get-serialno | tr -d '\r')"
PKG=com.isivoltpro.maginaolivo.dev
OUT=evidence/isolated-db-binding
mkdir -p "$OUT"
git rev-parse HEAD > "$OUT/checkout-sha.txt"
adb install -r app/build/outputs/apk/dev/debug/app-dev-debug.apk > "$OUT/install-app.txt"
adb install -r app/build/outputs/apk/androidTest/dev/debug/app-dev-debug-androidTest.apk > "$OUT/install-test.txt"
# The test controls the real singleton monitor; no component-state changes are needed.
adb shell am force-stop "$PKG"
adb logcat -c
set +e
adb shell am instrument -w -e class com.isivoltpro.maginaolivo.IsolatedDatabaseBindingRaceTest \
  "$PKG.test/androidx.test.runner.AndroidJUnitRunner" > "$OUT/instrumentation.txt" 2>&1
rc=$?
set -e
adb logcat -d > "$OUT/logcat.txt" 2>&1 || true
cat "$OUT/instrumentation.txt"
[[ "$rc" == 0 ]]
grep -qE '^OK \(1 test\)' "$OUT/instrumentation.txt"
! grep -qE 'FAILURES!!!|INSTRUMENTATION_FAILED|INSTRUMENTATION_ABORTED|Process crashed' "$OUT/instrumentation.txt"
