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
adb logcat -c
base=com.isivoltpro.maginaolivo.data.local
adb shell am instrument -w -e class \
  $base.ParcelMutationSendRoomTest,$base.ParcelArchiveCausalOrderRoomTest,$base.CampaignCasRoomTest,$base.ParcelSendRoomTest,$base.FarmSendRoomTest,$base.WorkspaceSendRoomTest,$base.ParentReceiveRoomTest,$base.OfflineFirstFarmRepositoryTest,$base.CoreWorkspaceBoundaryContractTest,$base.CampaignLifecycleContractTest,$base.OfflineFirstCampaignRepositoryTest,$base.ParentReceiveV2RoomTest,$base.ParentReceiveV2MemoryTest,$base.ParcelConvergenceRoomTest,$base.CampaignHistoryReceiveRoomTest,$base.CampaignLocalAnnulmentRoomTest,$base.DeliveryHistoricalSendRoomTest,$base.HarvestSendRoomTest,$base.DeliveryV3RecoveryRoomTest,$base.DeliveryDeleteSendRoomTest,$base.RoomMigrationTest,$base.SyncReceiveStoreRoomTest,$base.HarvestReceiveVerificationDeviceTest,$base.JornadaCoherenceRoomTest \
  com.isivoltpro.maginaolivo.dev.test/androidx.test.runner.AndroidJUnitRunner \
  > "$work/instrument.out"
cat "$work/instrument.out"
# 2c-2 memory evidence (#812 condition 4): heap, GC and latency of the v2 receiver.
adb logcat -d -s G26MEM:I | grep G26-MEM || echo 'G26-MEM no lines'
# 2c-3 convergence evidence: per scene, A/B/server hashes and outbox states.
adb logcat -d -s G26CONV:I | grep G26-CONV || echo 'G26-CONV no lines'
grep -q 'OK (237 tests)' "$work/instrument.out"
