#!/usr/bin/env bash
set -euo pipefail

mkdir -p evidence

PKG="com.isivoltpro.maginaolivo.dev"
ACTIVITY="com.isivoltpro.maginaolivo.MainActivity"
TEST_PKG="com.isivoltpro.maginaolivo.dev.test"
RUNNER="androidx.test.runner.AndroidJUnitRunner"
SCREENSHOT_TEST="com.isivoltpro.maginaolivo.Gate3EvidenceScreenshotTest"

APP_APK="$(find app/build/outputs/apk/dev/debug -name '*.apk' | head -n 1)"
TEST_APK="$(find app/build/outputs/apk/androidTest -name '*.apk' | head -n 1)"

if [[ -z "$APP_APK" || -z "$TEST_APK" ]]; then
  echo "Required APKs were not found" >&2
  exit 1
fi

adb install -r -t "$APP_APK" | tee evidence/install-app.txt
adb install -r -t "$TEST_APK" | tee evidence/install-test.txt

PHYSICAL_SIZE="$(adb shell wm size | tr -d '\r' | sed -n 's/^Physical size: //p' | head -n 1)"
PHYSICAL_WIDTH="${PHYSICAL_SIZE%x*}"

if [[ -z "$PHYSICAL_WIDTH" || "$PHYSICAL_WIDTH" == "$PHYSICAL_SIZE" ]]; then
  echo "Could not determine physical display width from: $PHYSICAL_SIZE" >&2
  exit 1
fi

cleanup() {
  adb shell wm density reset >/dev/null 2>&1 || true
  adb shell settings put system font_scale 1.0 >/dev/null 2>&1 || true
}
trap cleanup EXIT

wait_for_app() {
  local i=0
  while [[ "$i" -lt 30 ]]; do
    if adb shell pidof -s "$PKG" >/dev/null 2>&1; then
      return 0
    fi
    i=$((i + 1))
    sleep 1
  done
  echo "App process did not become available" >&2
  return 1
}

dump_ui() {
  local destination="$1"
  adb shell uiautomator dump /sdcard/gate3-window.xml >/dev/null
  adb pull /sdcard/gate3-window.xml "$destination" >/dev/null
}

tap_text_from_xml() {
  local text_to_find="$1"
  local xml_file="$2"
  local coords

  coords="$(python3 - "$text_to_find" "$xml_file" <<'PY'
import re
import sys
import xml.etree.ElementTree as ET

text = sys.argv[1]
xml_file = sys.argv[2]
root = ET.parse(xml_file).getroot()

for node in root.iter("node"):
    if node.attrib.get("text") == text or node.attrib.get("content-desc") == text:
        bounds = node.attrib.get("bounds", "")
        match = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds)
        if match:
            x1, y1, x2, y2 = map(int, match.groups())
            print(f"{(x1 + x2) // 2} {(y1 + y2) // 2}")
            sys.exit(0)

sys.exit(1)
PY
)"

  read -r x y <<<"$coords"
  adb shell input tap "$x" "$y"
}

pull_private_gate3_screens() {
  local output_dir="$1"
  mkdir -p "$output_dir"

  local names
  names="$(adb shell run-as "$PKG" ls files/gate3 2>/dev/null | tr -d '\r' || true)"

  if [[ -z "$names" ]]; then
    echo "No Gate 3 screenshot files found in app-private storage" >&2
    return 1
  fi

  while IFS= read -r name; do
    [[ -z "$name" ]] && continue
    adb exec-out run-as "$PKG" cat "files/gate3/$name" > "$output_dir/$name"
    test -s "$output_dir/$name"
  done <<<"$names"
}

capture_variant() {
  local target_dp="$1"
  local label="$2"
  local font_scale="$3"

  local density=$(( (PHYSICAL_WIDTH * 160 + target_dp / 2) / target_dp ))
  local out="evidence/$label"
  mkdir -p "$out"

  adb shell wm density "$density"
  adb shell settings put system font_scale "$font_scale"
  adb shell settings put global window_animation_scale 0
  adb shell settings put global transition_animation_scale 0
  adb shell settings put global animator_duration_scale 0

  {
    echo "target_dp=$target_dp"
    echo "requested_density=$density"
    echo "font_scale=$font_scale"
    adb shell wm size | tr -d '\r'
    adb shell wm density | tr -d '\r'
  } > "$out/display.txt"

  adb shell am force-stop "$PKG"
  adb shell am start -W -S -n "$PKG/$ACTIVITY" > "$out/start-onboarding.txt"
  wait_for_app
  sleep 1

  adb exec-out screencap -p > "$out/onboarding.png"
  dump_ui "$out/onboarding-ui.xml"

  if ! grep -q 'text="Saltar"' "$out/onboarding-ui.xml"; then
    echo "Onboarding accessibility tree does not expose 'Saltar' for $label" >&2
    exit 1
  fi

  tap_text_from_xml "Saltar" "$out/onboarding-ui.xml"
  sleep 1

  adb exec-out screencap -p > "$out/home.png"
  dump_ui "$out/home-ui.xml"

  if ! grep -q 'Mercado del aceite' "$out/home-ui.xml"; then
    echo "Home accessibility tree does not expose 'Mercado del aceite' for $label" >&2
    exit 1
  fi

  adb shell run-as "$PKG" rm -rf files/gate3 >/dev/null 2>&1 || true

  set +e
  adb shell am instrument -w \
    -e class "$SCREENSHOT_TEST" \
    "$TEST_PKG/$RUNNER" > "$out/reference-instrumentation.txt" 2>&1
  local screenshot_rc=$?
  set -e

  cat "$out/reference-instrumentation.txt"

  if [[ "$screenshot_rc" -ne 0 ]]; then
    echo "Reference screenshot instrumentation failed for $label" >&2
    exit "$screenshot_rc"
  fi

  pull_private_gate3_screens "$out/reference"
}

capture_variant 360 "phone-360dp" "1.0"
capture_variant 393 "phone-393dp" "1.0"
capture_variant 480 "phone-480dp" "1.0"
capture_variant 393 "phone-393dp-font130" "1.3"

COMMON_DENSITY=$(( (PHYSICAL_WIDTH * 160 + 393 / 2) / 393 ))
adb shell wm density "$COMMON_DENSITY"
adb shell settings put system font_scale 1.0

set +e
adb shell am instrument -w "$TEST_PKG/$RUNNER" > evidence/instrumentation-all.txt 2>&1
INSTRUMENTATION_RC=$?
set -e
cat evidence/instrumentation-all.txt

adb logcat -c
: > evidence/startup-runs.txt
for n in 1 2 3; do
  echo "=== cold-start-$n ===" >> evidence/startup-runs.txt
  adb shell am force-stop "$PKG"
  adb shell am start -W -S -n "$PKG/$ACTIVITY" >> evidence/startup-runs.txt 2>&1
  sleep 1
done

adb shell dumpsys gfxinfo "$PKG" > evidence/gfxinfo.txt 2>&1 || true
adb shell dumpsys meminfo "$PKG" > evidence/meminfo.txt 2>&1 || true
adb shell dumpsys activity top > evidence/activity-top.txt 2>&1 || true
adb logcat -d -v threadtime > evidence/logcat.txt 2>&1 || true
adb logcat -b crash -d -v threadtime > evidence/crash.txt 2>&1 || true

{
  echo "serial=$(adb get-serialno)"
  echo "android_release=$(adb shell getprop ro.build.version.release | tr -d '\r')"
  echo "sdk=$(adb shell getprop ro.build.version.sdk | tr -d '\r')"
  echo "model=$(adb shell getprop ro.product.model | tr -d '\r')"
  echo "abi=$(adb shell getprop ro.product.cpu.abi | tr -d '\r')"
  echo "physical_size=$PHYSICAL_SIZE"
  echo "instrumentation_rc=$INSTRUMENTATION_RC"
  echo
  echo "--- cold-start summary ---"
  grep -E '^(Status|LaunchState|Activity|ThisTime|TotalTime|WaitTime|Complete):' evidence/startup-runs.txt || true
  echo
  echo "--- gfx summary ---"
  grep -E 'Total frames rendered|Janky frames|50th percentile|90th percentile|95th percentile|99th percentile|Number Missed Vsync|Number Slow UI thread|Number Frame deadline missed' evidence/gfxinfo.txt || true
  echo
  echo "--- memory summary ---"
  grep -E 'TOTAL PSS|TOTAL RSS|Java Heap|Native Heap|Graphics|Activities|Views' evidence/meminfo.txt || true
  echo
  echo "--- crash buffer bytes ---"
  wc -c evidence/crash.txt || true
} > evidence/summary.txt

cat evidence/summary.txt

if [[ "$INSTRUMENTATION_RC" -ne 0 ]]; then
  echo "Full instrumented test suite failed with rc=$INSTRUMENTATION_RC" >&2
  exit "$INSTRUMENTATION_RC"
fi
