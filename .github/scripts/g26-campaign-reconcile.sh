#!/usr/bin/env bash
set -euo pipefail
test "$(adb shell getprop ro.build.version.sdk | tr -d '\r')" = 35
adb install -r app/build/outputs/apk/dev/debug/app-dev-debug.apk >/dev/null
adb install -r app/build/outputs/apk/androidTest/dev/debug/app-dev-debug-androidTest.apk >/dev/null
log="$(mktemp)"
trap 'rm -f "$log"' EXIT
adb shell am instrument -w -e class \
  'com.isivoltpro.maginaolivo.data.local.CampaignCasRoomTest,com.isivoltpro.maginaolivo.data.local.RoomMigrationTest#migration32To33PreservesCampaignOutboxAndAuthorization' \
  com.isivoltpro.maginaolivo.dev.test/androidx.test.runner.AndroidJUnitRunner > "$log"
cat "$log"
grep -q 'OK (20 tests)' "$log"
! grep -q 'FAILURES!!!' "$log"
