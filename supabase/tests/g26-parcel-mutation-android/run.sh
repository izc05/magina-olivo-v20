#!/usr/bin/env bash
# Room-only PARCEL UPDATE/ARCHIVE/RESTORE sender and producer on the attached disposable API 35
# emulator, with the Room regressions of #797-#800, the campaign CAS and the 2b causal order
# (CAMPAIGN UPDATE before PARCEL ARCHIVE). No network fixture: the RPCs are a local fake.
set -euo pipefail
cd "$(dirname "$0")/../../.."
command -v adb >/dev/null
work="$(mktemp -d)"
cleanup() { rm -rf "$work"; }
trap cleanup EXIT
gradle --no-daemon --max-workers=1 --no-parallel assembleDevDebug assembleDevDebugAndroidTest >/dev/null
adb install -r app/build/outputs/apk/dev/debug/app-dev-debug.apk >/dev/null
adb install -r app/build/outputs/apk/androidTest/dev/debug/app-dev-debug-androidTest.apk >/dev/null
adb shell pm clear com.isivoltpro.maginaolivo.dev >/dev/null
base=com.isivoltpro.maginaolivo.data.local
adb shell am instrument -w -e class \
  $base.ParcelMutationSendRoomTest,$base.ParcelArchiveCausalOrderRoomTest,$base.CampaignCasRoomTest,$base.ParcelSendRoomTest,$base.FarmSendRoomTest,$base.WorkspaceSendRoomTest,$base.ParentReceiveRoomTest,$base.OfflineFirstFarmRepositoryTest,$base.CoreWorkspaceBoundaryContractTest,$base.CampaignLifecycleContractTest,$base.OfflineFirstCampaignRepositoryTest \
  com.isivoltpro.maginaolivo.dev.test/androidx.test.runner.AndroidJUnitRunner \
  > "$work/instrument.out"
cat "$work/instrument.out"
grep -q 'OK (96 tests)' "$work/instrument.out"
