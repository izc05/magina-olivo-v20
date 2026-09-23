# Phase 11 — Attachments implementation plan

**Status:** Gate 11 PASS on branch (Android CI #340 + independent emulator run #57); not yet merged
**Precondition:** Gate 10 PASS, merged into `main` as `4acc3ab9`
**Base commit:** `ee948b1` (`main`)
**Branch:** `claude/dreamy-dijkstra-tdui2c`

## Contract

`ROADMAP-RC1.2` Phase 11:

- camera / document picker;
- local attachment lifecycle;
- thumbnails;
- farm cover;
- activity / document attachments;
- PDF / image handling.

**Gate 11:** an attachment survives an app restart, and a failed future upload cannot
destroy the local reference.

Normative sources: `OFFLINE-SYNC-CONTRACT-RC1` §18–§19 (capture/import flow, upload flow,
"never delete the only local copy merely because upload failed", SHA-256 integrity),
`DATA-MODEL-RC1-FUTURE` §13 (`attachments`), `DATA-MODEL-RC1.1-ADDENDUM` §1 (Farm cover
is an attachment; UI may derive/cache thumbnails), `MASTER-SPEC-RC1` §14 (attachments are
local-first), `SCREEN-MAP-RC1` S26 / S91, and `RC1-NORMATIVE-ADDENDUM` D10 (Attachment is
its own aggregate).

## Normative reconciliation

1. **The table is `documents`, not `attachments`.** Schema v2 already shipped the
   attachment table under the name `documents`, with every column the contract needs
   for Phase 11: owner type/id, type, MIME type, display name, size, SHA-256, local URI,
   remote path, upload status and the standard metadata tail. Renaming it would be a
   migration with no behavioural gain. Phase 11 therefore needs **no Room schema change**:
   the database stays at v5 and its exported identity hash is untouched. `captured_at`
   is not stored; `created_at` is the recorded time.
2. **The pre-Phase 11 Farm cover kept only a content URI.** Phase 6 recorded the picked
   URI with a persisted read grant "without copying attachment binaries before the
   Documents phase". That reference dies with the source file, so it could not satisfy
   Gate 11. Covers are now copied like any other attachment. Existing covers written
   before this phase keep their content URI and keep rendering as before; there is no
   data rewrite, because a migration cannot copy a file it may no longer read.
3. **One upload intent per attachment, none for its owner.** D10 lists Attachment as an
   aggregate of its own. Attaching a photo to a completed Activity therefore neither
   reopens the Activity nor moves its version. The only owner write is the Farm cover
   pointer, which already had its own Farm `UPDATE` intent.

## Decisions this phase had to make

| Question | Decision |
| --- | --- |
| Which file types? | Images (`image/*`) and PDF — the "PDF/image handling" of the roadmap. Anything else is rejected with `unsupported_type`, never stored as a guess. |
| Where does the binary live? | `filesDir/attachments/<attachment-id>.<ext>`, written to a `.partial` file, synced and renamed, so a crash never leaves a half-written attachment behind a row. |
| Size limit | 50 MB, to protect the device from an accidental video-sized pick. Reported to the farmer, not silent. |
| Thumbnails | Derived data in `filesDir/attachments/thumbnails/<id>.jpg`, 320 px on the long edge, EXIF-rotated for photos, first page for PDFs. Never stored in Room; absent thumbnail falls back to a label. |
| What does removal do? | Explicit, confirmed user action: soft delete (tombstone row), any waiting upload replaced by one `DELETE` intent, then the app-owned binary is released. Removing the current Farm cover also clears the Farm's pointer. |
| What does an upload failure do? | `recordUploadFailure` marks the row `FAILED` and the upload intent `FAILED` with its attempt count and error, and changes nothing else: same local URI, same file, same version. This is the entry point the Phase 23 synchronization worker will call. |
| Accepted sources | `content://` only — the document picker and the app's own `FileProvider` for camera captures. A `file://` source is refused. |
| Which owners get UI? | Farm, Parcel (S26) and Activity detail. The repository also accepts `CAMPAIGN`; its screen arrives with campaign reporting. Expense, Harvest and Delivery owners arrive with their own phases. |
| Viewer (S91) | The device's own viewer through a read-only `FileProvider` grant. Rename and share are not in this phase. |

## Architecture

```text
Compose section  →  AttachmentsViewModel  →  AttachmentRepository
                                                │
                     AttachmentFileStore ◄──────┤  1. copy + SHA-256 + thumbnail
                     (filesDir/attachments)     │
                                                └─ 2. Room transaction:
                                                      documents row + UPLOAD_ATTACHMENT intent
```

- The owner is validated before anything is copied, and again inside the transaction.
- If the transaction fails, the copy is released: no orphan file outlives a row that was
  never written.
- The UI reads observable Room data; nothing waits for a network.

## Implementation

| Slice | Files |
| --- | --- |
| A — contract | `domain/attachment/AttachmentRepository.kt` |
| B — binary store | `data/repository/AttachmentFileStore.kt`, `data/repository/AndroidAttachmentFileStore.kt`, `res/xml/attachment_paths.xml`, `AndroidManifest.xml` |
| C — repository | `data/repository/OfflineFirstAttachmentRepository.kt`, `dao/DocumentDao.kt`, `dao/SyncOutboxDao.kt` |
| D — Farm cover | `data/repository/OfflineFirstFarmCoverRepository.kt` (the retained-URI source is removed) |
| E — UI | `feature/attachments/*`, Farm / Parcel / Activity detail screens |
| F — tests | `AttachmentContractTest`, `AttachmentsSectionTest`, `OfflineFirstFarmCoverRepositoryTest`, `AttachmentKindTest`, `AttachmentsViewModelTest` |
| G — CI | the two attachment test classes join the airplane-mode repository run |

Out of scope: remote upload, the Supabase Storage path, OCR, a workspace-wide document
browser (S90), rename/share, Expense/Harvest/Delivery attachments.
