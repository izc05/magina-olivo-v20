#!/usr/bin/env bash
set -euo pipefail
script="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/wait-gate3-emulator-ready.sh"
export QA_ADB_COUNT=$(mktemp)
trap 'rm -f -- "$QA_ADB_COUNT"' EXIT
timeout() { shift; "$@"; }
sleep() { :; }
adb() {
  [[ "$1" == -s && "$2" == emulator-5554 ]] || return 88
  shift 2
  if [[ "$1" == get-state ]]; then
    local count=$(cat "$QA_ADB_COUNT"); count=$((count+1)); printf '%s' "$count" > "$QA_ADB_COUNT"
    if ((count <= QA_OFFLINE)); then echo offline; else echo device; fi
  elif [[ "$*" == 'shell getprop ro.kernel.qemu' ]]; then
    echo "$QA_QEMU"
  else return 89; fi
}
export -f timeout sleep adb
export ANDROID_SERIAL=emulator-5554 QA_QEMU=1 QA_OFFLINE=2
printf 0 > "$QA_ADB_COUNT"
bash "$script"
[[ "$(cat "$QA_ADB_COUNT")" == 3 ]]
echo 'PASS: transient offline recovers on the pinned emulator'
export QA_OFFLINE=99
printf 0 > "$QA_ADB_COUNT"
if bash "$script"; then exit 1; fi
[[ "$(cat "$QA_ADB_COUNT")" == 6 ]]
echo 'PASS: permanent loss fails after six attempts'
export QA_OFFLINE=0 QA_QEMU=0
printf 0 > "$QA_ADB_COUNT"
if bash "$script"; then exit 1; fi
[[ "$(cat "$QA_ADB_COUNT")" == 1 ]]
echo 'PASS: non-QEMU rejected'
export ANDROID_SERIAL=physical-device
printf 0 > "$QA_ADB_COUNT"
if bash "$script"; then exit 1; fi
[[ "$(cat "$QA_ADB_COUNT")" == 0 ]]
echo 'PASS: physical/unknown serial rejected before ADB'
