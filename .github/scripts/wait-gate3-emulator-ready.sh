#!/usr/bin/env bash
set -euo pipefail
# Recover only a transient ADB transport gap on the already selected emulator.
# Never reset data, restart ADB, choose another device, or hide a lost emulator.
serial=${ANDROID_SERIAL:-}
[[ "$serial" =~ ^emulator-[0-9]+$ ]] || { echo 'Explicit emulator serial required' >&2; exit 1; }
for attempt in 1 2 3 4 5 6; do
  if [[ "$(timeout 3s adb -s "$serial" get-state 2>/dev/null | tr -d '\r' || true)" == device ]]; then
    qemu=$(timeout 3s adb -s "$serial" shell getprop ro.kernel.qemu 2>/dev/null | tr -d '\r' || true)
    if [[ "$qemu" == 1 ]]; then
      echo "ADB ready: $serial (attempt $attempt)"
      exit 0
    fi
    [[ -z "$qemu" ]] || { echo 'Selected device is not QEMU' >&2; exit 1; }
  fi
  [[ "$attempt" == 6 ]] || sleep 2
done
echo "Selected emulator did not recover: $serial" >&2
exit 1
