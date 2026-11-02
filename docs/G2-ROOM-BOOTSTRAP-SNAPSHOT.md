# G2 Android: read-only Room preparation

`RoomBootstrapSnapshotReader` reads one explicitly selected workspace/farm/campaign and
explicit additional delivery parcels in a single Room transaction. It never chooses the
first workspace, infers the logged-in account or writes sync metadata/outbox rows.

It returns the full stored workspace, required farms, campaign header, historical campaign
parcel snapshots, current parcel rows and every membership row of those parcels. Ended and
logically deleted memberships are retained and their referenced farms still require consent.
Additional parcels must also have a historical association with the selected farm. The set
of consented IDs is only a guard supplied by the caller; the account-bound durable ledger
must still be verified by a future coordinator. No server ownership is inferred from local
owner UUIDs.

All parent/child workspace references must match. Missing/deleted parents or parcels,
unconsented historical farms, malformed campaign references and deleted campaign snapshots
stop the entire preparation. The server create contract cannot represent campaign snapshot
tombstones yet, so this reader rejects them instead of dropping that history. Archived live
rows remain supported. Only a PREPARATION campaign may have no snapshots.

Lists are immutable copies sorted by UUID for deterministic downstream preparation. Original
historical names, areas, geometry and cadastral references are retained independently of
later parcel/farm edits. Raw local metadata is included so a future coordinator can compare
local versions before committing the immutable request. These rows are not remote payloads;
remote workspace mapping, JSON codecs/hashes and stable operation construction remain to do.

The existing Room Instant converters store epoch milliseconds. This reader preserves the
precision already stored in Room; it cannot recover nanoseconds discarded before reading.
The backend's nanosecond support does not imply Android currently stores that precision.
No schema change is introduced here.

## Verification

Eight Android instrumented tests are included in the existing disposable Gate 3 emulator
suite, covering historical/current values, full logical-deletion history, cross-farm consent,
missing/wrong-scope rows, deleted entities, stable captured data after a later edit, empty
preparation campaigns and concurrent transactional edits. They require Android/Room execution;
adding or compiling them is not a claim that they passed. CI results must be checked for the
final commit. Script syntax is checked locally with `bash -n`.

No Context factory, app UI, HTTP, WorkManager sender, persistent SQL deployment or candidate APK
is activated. Profile/irrigation work remains outside this synchronization slice.
