# Mágina Olivo — Offline/Sync Contract RC1 v1

**Status:** Phase 0.5 draft
**Goal:** a saved agricultural record must survive poor/no connectivity, app termination and device restart, then converge safely with the remote backend when connectivity returns.

## 1. Non-negotiable rule

For critical field data:

```text
USER ACTION
  ↓
ROOM TRANSACTION
  ├── domain row
  └── durable outbox operation
  ↓
LOCAL SUCCESS SHOWN TO USER
  ↓
WORKMANAGER WHEN POSSIBLE
  ↓
REMOTE MUTATION
  ↓
LOCAL SYNC METADATA UPDATED
```

The UI does not wait for Supabase before confirming a valid local save.

## 2. Local source-of-truth contract

Normal UI/domain reads are served from Room through repositories/Flows.

Forbidden dependency:

```text
Screen → Supabase → render farm/parcel/activity
```

Required dependency:

```text
Screen → ViewModel → Repository → Room/Flow
                            ↑
                    sync updates Room
```

Remote data is pulled into Room; observers then update naturally.

## 3. Which data must work offline

After initial account/bootstrap and parcel import, offline RC1 must support:

- reading farms;
- reading stored parcels and geometries;
- reading campaigns/history;
- creating/editing farms where domain rules allow;
- recording/editing activities;
- recording expenses;
- recording harvests;
- recording deliveries;
- taking photos and linking local files;
- attaching local document references through Android picker;
- viewing locally available timelines/reports where dependencies are local.

Catastro discovery of a new parcel is an external-online operation unless an imported local GML is used.

## 4. Atomic local writes

Every synchronizable write uses a Room transaction that persists:

1. domain change;
2. updated local sync metadata;
3. one or more durable outbox operations.

If the transaction fails, none of those effects are considered accepted.

A UI must not show "Guardado" before the Room transaction commits.

## 5. Client-generated IDs

All domain UUIDs are generated before local insertion.

Benefits:

- records can be related to each other offline;
- attachments can reference unsynchronized owners;
- retries do not require new IDs;
- remote sync never remaps domain primary keys.

## 6. Local sync status

Synchronizable local records expose operational metadata conceptually equivalent to:

```text
sync_status:
  LOCAL_ONLY
  PENDING
  SYNCING
  SYNCED
  FAILED
  CONFLICT

remote_version: Long?
last_synced_at: Instant?
```

Do not make the domain screen depend on these fields except for concise status/error UI.

## 7. Durable outbox

`sync_outbox` is local and persistent.

Conceptual row:

```text
operation_id UUID PK
entity_type
entity_id
operation CREATE | UPDATE | DELETE | UPLOAD_ATTACHMENT
payload_version
base_remote_version?
attempt_count
next_attempt_at?
last_error_code?
last_error_message?
created_at
updated_at
```

### Coalescing

When safe, multiple pending updates for the same entity may coalesce to the latest intended state.

Never coalesce in a way that loses semantic operations where order matters.

For RC1 CRUD-style domain entities, syncing current intended row state is preferred over replaying every UI keystroke.

## 8. Idempotency

Every remote mutation must be safe to retry.

Requirements:

- stable client UUID for entity;
- stable `operation_id` for the queued mutation attempt;
- create/upsert must not create a second domain record when retried;
- attachment upload names/paths must be deterministic or collision-safe;
- server may track processed operation IDs if required by the final mutation API.

A timeout after the server committed but before the client received the response must converge correctly on retry.

## 9. Remote concurrency metadata

Remote domain rows need server-controlled synchronization metadata separate from client clocks, conceptually:

```text
server_version BIGINT
server_updated_at TIMESTAMPTZ
```

Server time/version is authoritative for remote concurrency.

Client `updated_at` remains useful domain/audit metadata but is not sufficient alone to decide multi-device conflicts.

## 10. Optimistic concurrency

Update/delete mutations carry the client's known `base_remote_version` when one exists.

Conceptual rule:

```text
if current_server_version == base_remote_version:
    apply mutation
    increment server_version
else:
    return CONFLICT
```

The concrete implementation may use Supabase RPC/database functions or another server-side conditional update mechanism. Do not implement "last client timestamp silently wins" for critical agricultural records merely because it is simpler.

## 11. New records

For a record never synchronized:

```text
remote_version = null
```

Push creates the row with the same domain UUID and returns the initial server version/server timestamp.

If the remote row with that UUID already exists because a prior retry actually succeeded, synchronization reconciles with that row instead of creating a duplicate.

## 12. Soft delete synchronization

Critical entities use soft delete.

Local delete:

```text
set deleted_at locally
→ enqueue DELETE/tombstone mutation
→ hide from normal UI
```

Remote sync propagates the tombstone rather than immediately destroying the row.

Pulling a remote tombstone applies the local soft delete unless a conflicting local unsynced edit requires conflict resolution.

Permanent purge is a later maintenance policy, not part of normal RC1 user flow.

## 13. Push/pull cycle

Recommended RC1 cycle:

```text
1. AUTH/SESSION CHECK
2. PUSH LOCAL OUTBOX DOMAIN CHANGES
3. PUSH/RESUME ATTACHMENTS whose owner state is safe
4. PULL REMOTE CHANGES since cursors
5. APPLY REMOTE CHANGES to Room transactionally
6. RECORD cursors/versions
7. RECOMPUTE local projections naturally through queries
```

Exact ordering can be adjusted per dependency, but local pending work must never be overwritten by a blind pull.

## 14. Incremental pull contract

Do not redownload the entire workspace on every sync.

Each remotely synchronized entity type should support a server-controlled change cursor, at minimum using a deterministic ordering such as:

```text
(server_updated_at, id)
```

A future server sequence/change feed is allowed if testing shows timestamps are insufficient.

Client cursors are stored locally per workspace/entity scope.

Bootstrap after login/new installation may perform a complete paginated initial pull.

## 15. Two-device behavior

Scenario:

```text
Device A and B synced at version 4
A offline edits Activity → local pending based on 4
B edits and syncs → server version 5
A reconnects → tries update based on 4
```

Expected result:

- server rejects/flags stale mutation;
- Device A keeps its local version;
- remote version 5 is also retained for comparison;
- local entity becomes `CONFLICT`;
- no version is silently destroyed.

## 16. Conflict policy by data class

### Critical user-created records

Activities, expenses, harvests, deliveries:

- no silent destructive overwrite;
- retain local and remote payloads when versions diverge;
- simple non-overlapping field merge may be added only if provably safe;
- otherwise user-facing resolution flow can be introduced.

### Low-risk metadata

Some preference/presentation metadata may use server/newer value policies where explicitly documented.

Do not apply a global last-write-wins rule to the whole database.

## 17. Relationship/dependency sync

Because IDs are client-generated, child rows can exist locally before parents reach the server.

Outbox draining must respect dependencies where remote foreign keys require parent existence.

Typical ordering:

```text
Workspace membership/bootstrap
→ Farm / Parcel / Campaign
→ joins/snapshots
→ Activities / Expenses / Harvest / Delivery
→ attachment remote metadata/binaries
```

A worker may group compatible independent operations, but correctness outranks maximum parallelism.

## 18. Attachments

Photos/documents are two resources:

1. local attachment metadata/domain link;
2. binary file.

Capture/import flow:

```text
capture/select file
→ persist durable local URI/file copy according to storage strategy
→ hash/metadata
→ create Attachment row
→ enqueue upload
→ UI can immediately show local attachment
```

Upload flow:

```text
network available
→ ensure session/owner requirements
→ upload binary to deterministic workspace-scoped path
→ verify success/metadata
→ update remote attachment record
→ mark local attachment SYNCED
```

Never delete the only local copy merely because upload failed.

## 19. Attachment integrity

Use SHA-256 where practical to:

- detect accidental duplicate uploads;
- verify file identity across retries;
- support future integrity checks.

Do not automatically deduplicate two user attachments solely because hashes match if their semantic ownership should remain distinct; binary reuse and attachment-record identity are separate concerns.

## 20. Draft forms

Unfinished long-form input may use local-only `form_drafts` or equivalent state persistence.

Drafts are not normal agricultural history until the user commits a valid record.

Draft recovery must survive process death for forms identified as important enough to support it.

## 21. WorkManager contract

Use unique persistent synchronization work so multiple triggers do not create uncontrolled competing synchronizers.

Constraints include network connectivity for remote work.

Triggers may include:

- app start/foreground opportunity;
- successful local mutation;
- connectivity return as WorkManager constraints become satisfied;
- manual "Sincronizar ahora";
- sensible periodic safety sync if later needed.

WorkManager is the scheduler/executor; Room outbox remains the durable source describing what still needs to sync.

## 22. Retry classes

### Retryable

Examples:

- no connectivity;
- timeout;
- transient 5xx/service unavailable;
- temporary Storage failure.

Use bounded/exponential backoff via WorkManager/outbox scheduling.

### Blocked until user/auth action

Examples:

- expired/invalid session that cannot refresh;
- workspace access revoked;
- file permission permanently lost.

Do not spin continuously.

### Permanent domain failure

Examples:

- server rejects invalid invariant;
- referenced workspace no longer exists;
- unsupported schema version.

Mark clearly and require corrective handling/migration rather than infinite retry.

## 23. Connectivity UI

Do not display disruptive modal dialogs every time network is absent.

Normal compact states:

```text
✓ Sincronizado
↑ 3 cambios pendientes
⚠ 1 elemento necesita atención
```

When offline but local save succeeded:

`Guardado en el dispositivo. Se sincronizará cuando haya conexión.`

The status language must distinguish "saved locally" from "backed up remotely".

## 24. Authentication/session behavior

After a previously authenticated user has local data, temporary lack of network must not make the local field notebook unusable solely because the server cannot be contacted.

Sensitive session/token persistence follows Android/Supabase security guidance.

Actions that truly require current remote authorization (fresh bootstrap, password reset, protected server operation) may require connectivity.

Exact long-term offline session policy must be tested against the chosen auth SDK/token behavior during implementation.

## 25. New-device restore

A new installation with connectivity:

```text
login
→ authorize workspace
→ bootstrap/pull remote domain data
→ store Room rows/geometries
→ download attachment metadata
→ binaries downloaded lazily/on-demand unless explicitly required offline
```

Previously synchronized agricultural history must be recoverable even if the old phone is lost.

## 26. Schema versions and migrations

Track independently:

- Android app version;
- Room schema version;
- sync payload/protocol version;
- remote DB migrations.

Never require uninstall/reinstall as the normal production migration process.

An outbox operation stores a payload/protocol version so pending work can be migrated or rejected safely across app upgrades.

## 27. Observability

Locally record operational diagnostics without leaking unnecessary agricultural/private content:

- last successful sync;
- pending outbox count;
- oldest pending operation age;
- retry counts;
- sanitized error codes;
- DB schema version;
- app version.

Do not log full notes, protected documents or credentials as routine diagnostics.

## 28. Test scenarios — mandatory

### T1 — Simple offline activity

- synced app;
- airplane mode;
- create activity;
- kill app;
- reopen;
- activity present;
- enable network;
- sync;
- remote row exists once.

### T2 — Timeout after remote success

Simulate server mutation succeeding while response is lost.

Retry must not duplicate record.

### T3 — Offline photo

- capture photo offline;
- kill app/reboot;
- photo still visible locally;
- reconnect;
- upload completes;
- relationship unchanged.

### T4 — Two-device conflict

Both devices modify same critical record from same base version. Second stale push must produce conflict, not silent overwrite.

### T5 — Soft delete offline

Delete offline, restart, reconnect. Tombstone converges without resurrecting the row on next pull.

### T6 — Network flapping

Alternate connected/disconnected during multi-operation sync. Every operation eventually converges exactly once or remains explicitly pending/failed.

### T7 — Token expiry

Pending work survives auth interruption and resumes after valid authentication without duplication.

### T8 — New-device recovery

Create data on device A, sync, then bootstrap device B. Domain history and parcel geometry match remote synchronized state.

### T9 — Migration with pending outbox

Upgrade app while unsynced operations exist. DB/protocol migration preserves or explicitly handles them; no silent drop.

## 29. Phase 0.5 Gate

Phase 0.5 can be approved only when:

- [ ] UI reads Room rather than remote directly;
- [ ] accepted writes commit locally before success is shown;
- [ ] domain write + outbox is atomic;
- [ ] UUIDs are generated client-side;
- [ ] retries are idempotent;
- [ ] server-controlled version metadata exists;
- [ ] stale multi-device updates cannot silently overwrite critical records;
- [ ] soft deletes synchronize as tombstones;
- [ ] attachment upload is local-first and retryable;
- [ ] WorkManager schedules sync but Room persists the durable queue;
- [ ] full bootstrap and incremental pulls are distinguished;
- [ ] new-device recovery is defined;
- [ ] schema/protocol migration with pending work is accounted for;
- [ ] torture tests are part of RC1 acceptance.

## 30. Reference architecture guidance

The contract follows Android's documented offline-first architecture principle: local data is the canonical data source exposed to higher layers, while repositories reconcile local and network sources; important offline writes can be written locally first and queued for later network synchronization, with persistent work commonly delegated to WorkManager.

Official Android references:

- `https://developer.android.com/topic/architecture/data-layer/offline-first`
- `https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started`
