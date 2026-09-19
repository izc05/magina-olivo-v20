#!/usr/bin/env bash
set -euo pipefail

mkdir -p evidence

PKG="com.isivoltpro.maginaolivo.dev"
ACTIVITY="com.isivoltpro.maginaolivo.MainActivity"
TEST_PKG="com.isivoltpro.maginaolivo.dev.test"
RUNNER="androidx.test.runner.AndroidJUnitRunner"
SCREENSHOT_TEST="com.isivoltpro.maginaolivo.Gate3EvidenceScreenshotTest"
OFFLINE_ROOM_TEST="com.isivoltpro.maginaolivo.data.local.OfflineFirstFarmRepositoryTest"

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
PHYSICAL_HEIGHT="${PHYSICAL_SIZE#*x}"

if [[ -z "$PHYSICAL_WIDTH" || "$PHYSICAL_WIDTH" == "$PHYSICAL_SIZE" ]]; then
  echo "Could not determine physical display width from: $PHYSICAL_SIZE" >&2
  exit 1
fi

cleanup() {
  adb shell cmd connectivity airplane-mode disable >/dev/null 2>&1 || true
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

dismiss_quickstep_anr() {
  local xml_file="$1"

  if grep -q "Quickstep isn't responding" "$xml_file" && grep -q 'text="Close app"' "$xml_file"; then
    echo "Dismissing emulator launcher ANR overlay" >&2
    tap_text_from_xml "Close app" "$xml_file"
    sleep 0.5
    return 0
  fi

  return 1
}

wait_for_home() {
  local destination="$1"

  for _ in {1..20}; do
    dump_ui "$destination"
    if dismiss_quickstep_anr "$destination"; then
      continue
    fi
    if grep -q 'Buenos días' "$destination" || grep -q 'text="Inicio"' "$destination"; then
      return 0
    fi
    sleep 0.25
  done

  echo "Home did not become visible before evidence capture" >&2
  return 1
}

wait_for_onboarding() {
  local destination="$1"

  for _ in {1..20}; do
    dump_ui "$destination"
    if dismiss_quickstep_anr "$destination"; then
      continue
    fi
    if grep -q 'text="Saltar"' "$destination"; then
      return 0
    fi
    sleep 0.25
  done

  echo "Onboarding did not become visible before evidence capture" >&2
  return 1
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

assert_instrumentation_passed() {
  local output_file="$1"
  local label="$2"
  local command_rc="$3"

  if [[ "$command_rc" -ne 0 ]] || \
    ! grep -Eq '^OK \([0-9]+ tests?\)$' "$output_file" || \
    grep -Eq 'FAILURES!!!|INSTRUMENTATION_FAILED|shortMsg=Process crashed' "$output_file"; then
    echo "$label failed (adb rc=$command_rc); see $output_file" >&2
    return 1
  fi
}

set_airplane_mode() {
  local command="$1"
  local expected="$2"
  local state

  adb shell cmd connectivity airplane-mode "$command"
  state="$(adb shell settings get global airplane_mode_on | tr -d '\r')"
  if [[ "$state" != "$expected" ]]; then
    echo "Airplane mode $command did not reach state $expected (actual=$state)" >&2
    return 1
  fi
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

  adb shell pm clear "$PKG" > "$out/clear-app-data.txt"
  adb shell am force-stop "$PKG"
  adb shell am start -W -S -n "$PKG/$ACTIVITY" > "$out/start-onboarding.txt"
  wait_for_app
  wait_for_onboarding "$out/onboarding-ui.xml"
  adb exec-out screencap -p > "$out/onboarding.png"

  tap_text_from_xml "Saltar" "$out/onboarding-ui.xml"
  wait_for_home "$out/home-ui.xml"
  adb exec-out screencap -p > "$out/home.png"

  adb shell input swipe \
    "$((PHYSICAL_WIDTH / 2))" \
    "$((PHYSICAL_HEIGHT * 4 / 5))" \
    "$((PHYSICAL_WIDTH / 2))" \
    "$((PHYSICAL_HEIGHT / 4))" \
    450
  sleep 1
  adb exec-out screencap -p > "$out/home-scrolled.png"
  dump_ui "$out/home-scrolled-ui.xml"

  if grep -q 'Mercado del aceite' "$out/home-scrolled-ui.xml"; then
    echo "market_after_scroll=FOUND" >> "$out/display.txt"
  else
    echo "market_after_scroll=NOT_VISIBLE" >> "$out/display.txt"
  fi

  adb shell run-as "$PKG" rm -rf files/gate3 >/dev/null 2>&1 || true

  set +e
  adb shell am instrument -w \
    -e class "$SCREENSHOT_TEST" \
    "$TEST_PKG/$RUNNER" > "$out/reference-instrumentation.txt" 2>&1
  local screenshot_rc=$?
  set -e

  cat "$out/reference-instrumentation.txt"

  assert_instrumentation_passed \
    "$out/reference-instrumentation.txt" \
    "Reference screenshot instrumentation for $label" \
    "$screenshot_rc"

  pull_private_gate3_screens "$out/reference"
}

capture_variant 360 "phone-360dp" "1.0"
capture_variant 393 "phone-393dp" "1.0"
capture_variant 480 "phone-480dp" "1.0"
capture_variant 393 "phone-393dp-font130" "1.3"

COMMON_DENSITY=$(( (PHYSICAL_WIDTH * 160 + 393 / 2) / 393 ))
adb shell wm density "$COMMON_DENSITY"
adb shell settings put system font_scale 1.0

set_airplane_mode enable 1 | tee evidence/airplane-mode.txt
{
  echo "airplane_mode=$(adb shell settings get global airplane_mode_on | tr -d '\r')"
  echo "wifi=$(adb shell dumpsys wifi | grep -m 1 'Wi-Fi is' | tr -d '\r' || true)"
} >> evidence/airplane-mode.txt

set +e
adb shell am instrument -w \
  -e class "$OFFLINE_ROOM_TEST" \
  "$TEST_PKG/$RUNNER" > evidence/offline-room-instrumentation.txt 2>&1
OFFLINE_ROOM_RC=$?
set -e
cat evidence/offline-room-instrumentation.txt

assert_instrumentation_passed \
  evidence/offline-room-instrumentation.txt \
  "Airplane-mode Room CRUD instrumentation" \
  "$OFFLINE_ROOM_RC"

set_airplane_mode disable 0 | tee -a evidence/airplane-mode.txt

set +e
adb shell am instrument -w "$TEST_PKG/$RUNNER" > evidence/instrumentation-all.txt 2>&1
INSTRUMENTATION_RC=$?
set -e
cat evidence/instrumentation-all.txt

assert_instrumentation_passed \
  evidence/instrumentation-all.txt \
  "Full instrumented test suite" \
  "$INSTRUMENTATION_RC"

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
  echo "offline_room_instrumentation_rc=$OFFLINE_ROOM_RC"
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
