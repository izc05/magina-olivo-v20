# Phase 11 — Attachments

**Phase decision:** PENDING — implementation complete, CI evidence not yet collected
**Reviewed:** 2026-09-23
**Base commit:** `ee948b1` (`main`, Phase 10 merged and recorded)
**Branch:** `claude/dreamy-dijkstra-tdui2c`
**Plan:** `docs/07-plans/PHASE11-ATTACHMENTS.md`

> No CI, emulator or artifact figure appears below until a real run produces it. The
> implementation was written in an environment without the Android SDK, so it has not
> been compiled locally either: the first Android CI run on this branch is the first
> build.

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

| Check | Command / workflow | Result | Run |
| --- | --- | --- | --- |
| Lint | `:app:lintDevDebug` | pending | — |
| Unit tests | `:app:testDevDebugUnitTest` | pending | — |
| Instrumented compilation | `:app:assembleDevDebugAndroidTest` | pending | — |
| Debug builds | `assembleDevDebug assembleStagingDebug assembleProductionDebug` | pending | — |
| Full API 35 instrumentation | `gate3-emulator` | pending | — |
| Repository tests in airplane mode | `offline-room-instrumentation.txt` | pending | — |
| Emulator crash buffer | `gate3-emulator-evidence` | pending | — |
| Independent emulator run | `Gate 3 Android Emulator Evidence` | pending | — |
| Room schema | unchanged at v5, identityHash `a1fcd78acb39c2497f0f20efb5602598` expected | pending | — |

## Known gaps

- Camera capture and the system document picker are exercised on a device only by hand;
  the automated tests feed the same `FileProvider` path the camera uses.
- Real-device verification of camera capture is still to be recorded.
