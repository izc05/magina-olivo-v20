#!/usr/bin/env bash
set -euo pipefail

adb install -r app/build/outputs/apk/dev/debug/app-dev-debug.apk
adb install -r app/build/outputs/apk/androidTest/dev/debug/app-dev-debug-androidTest.apk
adb shell am instrument -w -r \
  -e class com.isivoltpro.maginaolivo.data.local.RoomMigrationTest \
  com.isivoltpro.maginaolivo.dev.test/androidx.test.runner.AndroidJUnitRunner \
  | tee /tmp/g26-room-migrations.txt
grep -q 'INSTRUMENTATION_CODE: -1' /tmp/g26-room-migrations.txt
grep -Eq 'OK \([0-9]+ tests\)' /tmp/g26-room-migrations.txt
