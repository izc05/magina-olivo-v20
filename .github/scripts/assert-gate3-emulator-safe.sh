#!/usr/bin/env bash
set -euo pipefail

# Gate3 captures deliberately reset the DEV app with "adb shell pm clear" several
# times. Refuse to run unless this is an explicitly opted-in *disposable* emulator.
# This preflight MUST happen before adb install, shell mutations, or screenshots.
if [[ "${GATE3_ALLOW_APP_DATA_RESET:-}" != "1" ]]; then
  echo "Gate 3 device safety: refusing destructive QA without GATE3_ALLOW_APP_DATA_RESET=1" >&2
  exit 1
fi

serial="$(adb get-serialno | tr -d '\r')"
if [[ ! "$serial" =~ ^emulator-[0-9]+$ ]]; then
  echo "Gate 3 device safety: refusing physical/unknown device serial: $serial" >&2
  exit 1
fi

kernel_qemu="$(adb shell getprop ro.kernel.qemu | tr -d '\r')"
boot_qemu="$(adb shell getprop ro.boot.qemu | tr -d '\r')"
if [[ "$kernel_qemu" != "1" && "$boot_qemu" != "1" ]]; then
  echo "Gate 3 device safety: refusing non-QEMU device $serial" >&2
  exit 1
fi

echo "Gate 3: opted-in disposable emulator $serial (DEV app data will be cleared)" >&2
