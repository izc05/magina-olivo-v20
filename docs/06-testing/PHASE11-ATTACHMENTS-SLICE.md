# Phase 11 — Attachments

**Phase decision:** PASS — implementation complete and validated by CI
**Reviewed:** 2026-09-23
**Base commit:** `ee948b1` (`main`, Phase 10 merged and recorded)
**Branch:** `claude/dreamy-dijkstra-tdui2c`
**Validated commit:** `803d69b6`
**Merged:** PR #207, merge commit `e42754ac`
**Plan:** `docs/07-plans/PHASE11-ATTACHMENTS.md`

> Every CI, emulator and artifact figure below is copied from a real run. Nothing is
> estimated, and no evidence from an earlier phase is reused. The code was written in an
> environment without the Android SDK; the first build of it was Android CI #340, which
> passed without any fix.

## Gate 11

| Clause | Proof |
| --- | --- |
| An attachment survives an app restart | `AttachmentContractTest.anAttachmentSurvivesAnAppRestart`, `OfflineFirstFarmCoverRepositoryTest.copiedCoverAndOutboxSurviveRestart` |
| A failed future upload cannot destroy the local reference | `AttachmentContractTest.aFailedUploadNeverDestroysTheLocalReference` (two failures, then a restart) |

## Tests added

`AttachmentContractTest` — instrumented, and also run under airplane mode:

| Test | Contract clause |
| --- | --- |
| `aPhotoIsCopiedIntoAppStorageWithItsHashAndSize` | copy into app storage, SHA-256 and size (`OFFLINE-SYNC-CONTRACT-RC1` §18–§19) |
| `theAttachmentOutlivesTheFileTheUserPicked` | the attachment does not depend on the picked file |
| `photosAndPdfsGetADerivedThumbnail` | thumbnails for images and PDFs |
| `anAttachmentSurvivesAnAppRestart` | **Gate 11** — restart |
| `aFailedUploadNeverDestroysTheLocalReference` | **Gate 11** — failed upload keeps URI, file and bytes; the intent keeps its attempts and error |
| `eachAttachmentQueuesOneUploadIntentAndNoneForItsOwner` | D10: Attachment is its own aggregate; a completed Activity keeps its version and intents |
| `attachmentsAreListedOnlyUnderTheirOwner` | owner isolation |
| `anUnsupportedFileIsRejectedWithoutARowAFileOrAnIntent` | only images and PDF, never guessed |
| `anEmptyFileIsRejected` | no empty attachment |
| `aMissingOwnerIsRejectedBeforeAnythingIsCopied` | no orphan file |
| `aFileSchemeSourceIsRefused` | only `content://` sources |
| `removalQueuesATombstoneAndReleasesTheCopy` | confirmed removal: tombstone, one `DELETE` intent, binary released, idempotent |
| `theFarmCoverIsACopiedFarmPhotoAndRemovingItClearsTheCover` | Farm cover is an attachment and survives deletion of its source |
| `aCoverMustBeAnImage` | a PDF is never a cover |

`AttachmentsSectionTest` — Compose: truthful empty state, both ways to add, a failed
upload shown as kept on the device, and removal only after confirmation.

`OfflineFirstFarmCoverRepositoryTest` — now uses the real file store instead of a fake
retained URI.

JVM: `AttachmentKindTest` (accepted MIME types), `AttachmentsViewModelTest` (list, attach,
remove, farmer-readable errors, status labels, sizes).

## Evidence

Both workflows were started with `workflow_dispatch` on the branch, because pushes to
`claude/**` do not trigger them.

| Check | Command / workflow | Result | Run |
| --- | --- | --- | --- |
| Lint | `:app:lintDevDebug` | PASS | [Android CI #340](https://github.com/izc05/magina-olivo-v20/actions/runs/35840622582), job `foundation` |
| Unit tests | `:app:testDevDebugUnitTest` | PASS (the log does not print a count) | Android CI #340 |
| Instrumented compilation | `:app:assembleDevDebugAndroidTest` | PASS | Android CI #340 |
| Debug builds | `assembleDevDebug assembleStagingDebug assembleProductionDebug` | PASS | Android CI #340 |
| Full API 35 instrumentation | `gate3-emulator` | PASS — 111 instrumented tests, `instrumentation_rc=0` | Android CI #340, job `107114543985` |
| Attachment contract | `AttachmentContractTest` | PASS — 14/14 | Android CI #340 |
| Attachment section UI | `AttachmentsSectionTest` | PASS — 3/3 | Android CI #340 |
| Room migrations | `RoomMigrationTest` | PASS — 4/4; no entity changed, schema stays v5 | Android CI #340 |
| Repository tests in airplane mode | `offline-room-instrumentation.txt` | PASS — 52 tests, `offline_room_instrumentation_rc=0`, including `AttachmentContractTest` 14/14 and `OfflineFirstFarmCoverRepositoryTest` 1/1 | Android CI #340 |
| Emulator crash buffer | `gate3-emulator-evidence` | EMPTY — `0 evidence/crash.txt` | Android CI #340 |
| Independent emulator run | `Gate 3 Android Emulator Evidence #57` | PASS | [run 35842241545](https://github.com/izc05/magina-olivo-v20/actions/runs/35842241545) |
| Installable DEV APK | `magina-olivo-dev-debug`, artifact `10740723939` | 13 402 191 bytes (zip) | `sha256:2e35ec1f7c62a3ced3461a3c70f524b6b5f3950ff32773b680eacd9bb3b13afe` |
| Evidence bundle | `gate3-emulator-evidence`, artifact `10740754213` | 3 282 986 bytes | `sha256:bd20fc91473f53a32cd3c0c3d614488b2e00c3ac02089dc29295c88097662a11` |

### Emulator and device

```text
serial=emulator-5554
android_release=15
sdk=35
model=Android SDK built for x86_64
abi=x86_64
physical_size=1080x2400
```

```text
GATE 11 = PASS (on branch, commit 803d69b6)
```

## Known gaps

- Camera capture and the system document picker are exercised on a device only by hand;
  the automated tests feed the same `FileProvider` path the camera uses.
- Real-device verification of camera capture is still to be recorded.
