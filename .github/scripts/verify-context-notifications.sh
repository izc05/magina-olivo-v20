#!/usr/bin/env bash
set -euo pipefail
OUT="${1:-evidence/context-notifications}"
CLASS="${2:-com.isivoltpro.maginaolivo.ContextNotificationPermissionTest}"
case "$CLASS" in
 com.isivoltpro.maginaolivo.ContextNotificationPermissionTest|com.isivoltpro.maginaolivo.FirstReminderPermissionTest\#firstSavedReminderRequestsPermissionAndPreservesWork|com.isivoltpro.maginaolivo.FirstReminderPermissionTest\#saveAcrossRecreationStillRequestsPermission|com.isivoltpro.maginaolivo.MainReminderPersistenceTest) ;;
 *) echo 'Unsupported test class'; exit 1 ;;
esac
PKG=com.isivoltpro.maginaolivo.dev
PERMISSION=android.permission.POST_NOTIFICATIONS
[[ "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]] || { echo 'Emulator required'; exit 1; }
if [[ "${GATE3_REMINDER_DIAGNOSTICS:-}" == 1 && "$CLASS" == com.isivoltpro.maginaolivo.MainReminderPersistenceTest ]]; then
 bash .github/scripts/assert-gate3-emulator-safe.sh
fi
mkdir -p "$OUT"
permission_state() { adb shell dumpsys package "$PKG" | tr -d '\r' | grep 'android.permission.POST_NOTIFICATIONS: granted=' | sed 's/^[[:space:]]*//'; }
BEFORE="$(permission_state)"
[[ -n "$BEFORE" ]] || { echo 'Cannot preserve notification permission'; exit 1; }
printf '%s\n' "$BEFORE" > "$OUT/permission-before.txt"
restore() {
 adb shell input keyevent KEYCODE_BACK >/dev/null
 if [[ "$BEFORE" == *'granted=true'* ]]; then adb shell pm grant "$PKG" "$PERMISSION"; else adb shell pm revoke "$PKG" "$PERMISSION"; fi
 adb shell pm clear-permission-flags "$PKG" "$PERMISSION" user-set user-fixed
 [[ "$BEFORE" != *USER_SET* ]] || adb shell pm set-permission-flags "$PKG" "$PERMISSION" user-set
 [[ "$BEFORE" != *USER_FIXED* ]] || adb shell pm set-permission-flags "$PKG" "$PERMISSION" user-fixed
 AFTER="$(permission_state)"
 printf '%s\n' "$AFTER" > "$OUT/permission-after.txt"
 [[ "$BEFORE" == "$AFTER" ]] || { echo 'Permission/flags restoration failed'; return 1; }
}
trap restore EXIT
case "${3:-all}" in
 all) MODES=(baseline grant deny later) ;;
 later) MODES=(later) ;;
 *) echo 'Unsupported permission mode set'; exit 1 ;;
esac
for MODE in "${MODES[@]}"; do
 adb shell pm revoke "$PKG" "$PERMISSION"
 adb shell pm clear-permission-flags "$PKG" "$PERMISSION" user-set user-fixed
 if [[ "${GATE3_REMINDER_DIAGNOSTICS:-}" == 1 && "$CLASS" == com.isivoltpro.maginaolivo.MainReminderPersistenceTest ]]; then
  # Preserve the warm pipeline state: no reset, retry, IME change or logcat clear.
  capture_dir="$OUT/warm-$MODE"
  mkdir -p "$capture_dir"
  instrument_rc=0
  adb shell am instrument -w -r -e notificationQaMode "$MODE" -e class "$CLASS" \
   -e qaReminderDiagnostics true -e qaReminderCloseIme false -e qaReminderIteration "warm-$MODE" \
   "$PKG.test/androidx.test.runner.AndroidJUnitRunner" > "$OUT/$MODE.log" 2>&1 || instrument_rc=$?
  capture_rc=0
  # Collect before interpreting JUnit output (adb can return zero on JUnit failure).
  timeout 20s adb logcat -d -v threadtime > "$capture_dir/logcat.txt" 2>&1 || capture_rc=1
  timeout 20s adb pull "/sdcard/Android/data/$PKG/files/reminder-save-diagnostics/warm-$MODE" "$capture_dir/device" > "$capture_dir/pull.txt" 2>&1 || capture_rc=1
  : > "$capture_dir/capture-manifest.txt"
  for capture in room.txt semantics.txt window.txt ime.txt active-window.txt screen.png; do
   if ! find "$capture_dir/device" -type f -name "$capture" -size +0c 2>/dev/null | grep -q .; then
    printf 'MISSING %s\n' "$capture" >> "$capture_dir/capture-manifest.txt"
    capture_rc=1
   else
    printf 'PRESENT %s\n' "$capture" >> "$capture_dir/capture-manifest.txt"
   fi
  done
  printf 'adb_exit=%s\ncapture_exit=%s\n' "$instrument_rc" "$capture_rc" > "$capture_dir/result.txt"
  if [[ "$instrument_rc" != 0 || "$capture_rc" != 0 ]]; then
   cat "$OUT/$MODE.log"
   cat "$capture_dir/capture-manifest.txt"
   [[ "$instrument_rc" == 0 ]] || exit "$instrument_rc"
   exit 1
  fi
 else
  adb shell am instrument -w -r -e notificationQaMode "$MODE" -e class "$CLASS" "$PKG.test/androidx.test.runner.AndroidJUnitRunner" > "$OUT/$MODE.log" 2>&1
 fi
 grep -q 'OK (1 test)' "$OUT/$MODE.log"
 if grep -Eq 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed' "$OUT/$MODE.log"; then cat "$OUT/$MODE.log"; exit 1; fi
 echo "$MODE: 1/1"
done
