#!/usr/bin/env bash
set -euo pipefail

# Only a deliberately opted-in disposable emulator; no data reset is performed here.
bash .github/scripts/assert-gate3-emulator-safe.sh
export ANDROID_SERIAL="$(adb get-serialno | tr -d '\r')"

record_device_state() {
  local phase="$1"
  mkdir -p integration-device-evidence
  for service in power window input; do
    timeout 15s adb shell dumpsys "$service" > "integration-device-evidence/$phase-$service.txt" 2>&1 || true
  done
}
collect_evidence() {
  record_device_state final
  adb pull /sdcard/Android/data/com.isivoltpro.maginaolivo.dev/files/navigation-evidence navigation-evidence >/dev/null 2>&1 || true
  adb pull /sdcard/Android/data/com.isivoltpro.maginaolivo.dev/files/account-auth-evidence account-auth-evidence >/dev/null 2>&1 || true
}
trap collect_evidence EXIT
record_device_state before
# Physical input tests require an interactive, unlocked display. Unlike the full
# capture gate, this batch has no preceding onboarding taps to activate it.
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard
adb shell svc power stayon true
record_device_state prepared
adb install -r app/build/outputs/apk/dev/debug/app-dev-debug.apk
adb install -r app/build/outputs/apk/androidTest/dev/debug/app-dev-debug-androidTest.apk
adb shell am instrument -w -r -e class com.isivoltpro.maginaolivo.AccountAuthGateTest,com.isivoltpro.maginaolivo.AccountAuthScreenTest,com.isivoltpro.maginaolivo.FarmLinkScreenTest,com.isivoltpro.maginaolivo.AppNavigationTest,com.isivoltpro.maginaolivo.AgriculturalCampaignScreenTest,com.isivoltpro.maginaolivo.CampaignOverviewFlowTest,com.isivoltpro.maginaolivo.NotebookHomeScreenTest,com.isivoltpro.maginaolivo.FarmScreensTest,com.isivoltpro.maginaolivo.FarmOverviewScreenTest,com.isivoltpro.maginaolivo.GlobalQuickActionsTest,com.isivoltpro.maginaolivo.HomeFeedsScreenTest,com.isivoltpro.maginaolivo.WeatherWeekScreenTest,com.isivoltpro.maginaolivo.data.local.RoomMigrationTest,com.isivoltpro.maginaolivo.data.local.WorkspaceSelectionRoomTest,com.isivoltpro.maginaolivo.data.local.WorkspaceSendRoomTest,com.isivoltpro.maginaolivo.data.local.CampaignCreateSendRoomTest com.isivoltpro.maginaolivo.dev.test/androidx.test.runner.AndroidJUnitRunner | tee integration-tests.txt
grep -q 'INSTRUMENTATION_CODE: -1' integration-tests.txt
grep -Eq 'OK \([0-9]+ tests\)' integration-tests.txt
