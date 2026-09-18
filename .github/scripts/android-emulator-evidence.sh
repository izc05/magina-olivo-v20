#!/bin/sh
set -eu

mkdir -p evidence

PKG="com.isivoltpro.maginaolivo.dev"
ACTIVITY="com.isivoltpro.maginaolivo.MainActivity"
TEST_PKG="com.isivoltpro.maginaolivo.dev.test"

APK="$(find app/build/outputs/apk/dev/debug -name '*.apk' | head -n 1)"
TEST_APK="$(find app/build/outputs/apk/androidTest -name '*.apk' | head -n 1)"

{
  echo "serial=$(adb get-serialno)"
  echo "android_release=$(adb shell getprop ro.build.version.release | tr -d '\r')"
  echo "sdk=$(adb shell getprop ro.build.version.sdk | tr -d '\r')"
  echo "model=$(adb shell getprop ro.product.model | tr -d '\r')"
  echo "abi=$(adb shell getprop ro.product.cpu.abi | tr -d '\r')"
  echo "wm_size=$(adb shell wm size | tr -d '\r')"
  echo "app_apk=$APK"
  echo "test_apk=$TEST_APK"
} > evidence/device.txt

adb install -r -t "$APK" > evidence/install-app.txt 2>&1
cat evidence/install-app.txt
adb install -r -t "$TEST_APK" > evidence/install-test.txt 2>&1
cat evidence/install-test.txt

adb shell cmd package resolve-activity --brief "$PKG" > evidence/resolve-activity.txt 2>&1 || true
adb shell dumpsys package "$PKG" | grep -Ei 'DEBUGGABLE|profileable|isProfileable' > evidence/profileability.txt 2>&1 || true

adb logcat -c
adb shell dumpsys gfxinfo "$PKG" reset > /dev/null 2>&1 || true

: > evidence/startup-runs.txt
for n in 1 2 3; do
  echo "=== cold-start-$n ===" >> evidence/startup-runs.txt
  adb shell am force-stop "$PKG"
  sleep 1
  adb shell am start -W -S -n "$PKG/$ACTIVITY" >> evidence/startup-runs.txt 2>&1
  sleep 2
done

i=0
while [ "$i" -lt 20 ]; do
  if adb shell pidof -s "$PKG" >/dev/null 2>&1; then
    break
  fi
  i=$((i + 1))
  sleep 1
done

adb exec-out screencap -p > evidence/launch.png

adb shell uiautomator dump /sdcard/window.xml > evidence/uiautomator-command.txt 2>&1 || true
adb pull /sdcard/window.xml evidence/ui.xml > evidence/uiautomator-pull.txt 2>&1 || true

adb shell dumpsys activity top > evidence/activity-top.txt 2>&1 || true
adb shell dumpsys window windows > evidence/window.txt 2>&1 || true
adb shell dumpsys gfxinfo "$PKG" > evidence/gfxinfo.txt 2>&1 || true
adb shell dumpsys gfxinfo "$PKG" framestats > evidence/gfxinfo-framestats.txt 2>&1 || true
adb shell dumpsys meminfo "$PKG" > evidence/meminfo.txt 2>&1 || true

PERF_DEVICE="/data/misc/perfetto-traces/magina-launch.pftrace"
adb shell rm -f "$PERF_DEVICE" >/dev/null 2>&1 || true
adb shell perfetto --background-wait -o "$PERF_DEVICE" -t 8s --app "$PKG" sched freq idle am wm gfx view binder_driver hal dalvik > evidence/perfetto-command.txt 2>&1 || true
sleep 1
adb shell am force-stop "$PKG"
adb shell am start -W -S -n "$PKG/$ACTIVITY" > evidence/startup-perfetto.txt 2>&1 || true
sleep 9
adb pull "$PERF_DEVICE" evidence/magina-launch.pftrace > evidence/perfetto-pull.txt 2>&1 || true

adb shell rm -f /data/local/tmp/perf.data >/dev/null 2>&1 || true
(
  adb shell simpleperf record --app "$PKG" -o /data/local/tmp/perf.data -e cpu-clock -f 4000 -g --duration 8
) > evidence/simpleperf-record.txt 2>&1 &
SIMPLEPERF_HOST_PID=$!
sleep 1
adb shell am force-stop "$PKG"
adb shell am start -W -S -n "$PKG/$ACTIVITY" > evidence/startup-simpleperf.txt 2>&1 || true
wait "$SIMPLEPERF_HOST_PID" || true
adb pull /data/local/tmp/perf.data evidence/perf.data > evidence/simpleperf-pull.txt 2>&1 || true
adb shell simpleperf report -i /data/local/tmp/perf.data > evidence/simpleperf-report.txt 2>&1 || true

set +e
adb shell am instrument -w "$TEST_PKG/androidx.test.runner.AndroidJUnitRunner" > evidence/instrumentation.txt 2>&1
INSTRUMENTATION_RC=$?
set -e
cat evidence/instrumentation.txt

adb shell am start -W -n "$PKG/$ACTIVITY" > evidence/relaunch-after-test.txt 2>&1 || true
sleep 2
adb exec-out screencap -p > evidence/after-test.png
adb shell uiautomator dump /sdcard/window-after.xml > evidence/uiautomator-after-command.txt 2>&1 || true
adb pull /sdcard/window-after.xml evidence/ui-after.xml > evidence/uiautomator-after-pull.txt 2>&1 || true

adb logcat -d -v threadtime > evidence/logcat.txt 2>&1 || true
adb logcat -b crash -d -v threadtime > evidence/crash.txt 2>&1 || true

{
  echo "package=$PKG"
  echo "activity=$ACTIVITY"
  echo "instrumentation_rc=$INSTRUMENTATION_RC"
  echo
  echo "--- expected UI text checks ---"
  if grep -q 'text="Olivar"' evidence/ui.xml 2>/dev/null; then echo "Olivar=FOUND"; else echo "Olivar=NOT_FOUND"; fi
  if grep -q 'Gestión de tu olivar' evidence/ui.xml 2>/dev/null; then echo "subtitle=FOUND"; else echo "subtitle=NOT_FOUND"; fi
  if grep -q 'text="DEV"' evidence/ui.xml 2>/dev/null; then echo "DEV=FOUND"; else echo "DEV=NOT_FOUND"; fi
  echo
  echo "--- crash buffer bytes ---"
  wc -c evidence/crash.txt || true
  echo
  echo "--- startup summary ---"
  grep -E '^(Status|LaunchState|Activity|ThisTime|TotalTime|WaitTime|Complete):' evidence/startup-runs.txt || true
  echo
  echo "--- gfx headline ---"
  grep -E 'Total frames rendered|Janky frames|50th percentile|90th percentile|95th percentile|99th percentile|Number Missed Vsync|Number High input latency|Number Slow UI thread|Number Slow bitmap uploads|Number Slow issue draw commands|Number Frame deadline missed' evidence/gfxinfo.txt || true
  echo
  echo "--- memory headline ---"
  grep -E 'TOTAL PSS|TOTAL RSS|Java Heap|Native Heap|Graphics|Activities|Views' evidence/meminfo.txt || true
  echo
  echo "--- perfetto trace ---"
  ls -lh evidence/magina-launch.pftrace 2>/dev/null || true
  echo
  echo "--- simpleperf data ---"
  ls -lh evidence/perf.data 2>/dev/null || true
} > evidence/summary.txt

cat evidence/summary.txt

if [ "$INSTRUMENTATION_RC" -ne 0 ]; then
  echo "Instrumented smoke test failed with rc=$INSTRUMENTATION_RC" >&2
  exit "$INSTRUMENTATION_RC"
fi
