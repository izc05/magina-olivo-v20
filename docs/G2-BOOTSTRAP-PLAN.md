# Android bootstrap preparation

`BootstrapPlan` is a pure local prerequisite scheduler, not a sender.
`DurableBootstrapJournal` now persists its complete prepared plan and receipts in one private file.
It binds an immutable prepared list to issuer/user, remote workspace and consent UUID.
Every operation carries a stable UUID, entity identity, payload bytes, JSONB contract hash,
payload version, prerequisite operation UUIDs and all referenced farm UUIDs.

The workspace is the unique first operation and a prerequisite of every descendant.
Parcel/campaign steps must depend on all referenced farm operations, and campaigns on all
explicitly referenced parcel operations. Empty parcel sets support preparation campaigns;
the snapshot builder must supply every campaign parcel reference.
Dependencies must precede the operation, rejecting cycles, missing prerequisites and duplicate
operations/entities. Parcel history must include every referenced farm in its required scope;
the eventual Room snapshot builder is responsible for extracting that complete set.

`ready` requires the same visible actor and consent, with every prepared farm still consented.
Only exact, positive-version receipts unblock successors. Unknown, foreign, altered, duplicate
or prerequisite-incomplete receipt collections stop the whole plan. A lost acknowledgement
leaves the exact same prepared operation ready; it never generates a replacement UUID.

The supplied hash is **not** calculated or authenticated by this class. Entity codecs must
verify it against PostgreSQL JSONB vectors before a future sender can use this plan. This is
not evidence of remote ownership, a valid reservation, fresh Auth or persisted consent.

## Verification

Nine standalone JVM JUnit tests cover prerequisite progression, unchanged uncertain retries,
account switch/logout/revocation/consent replacement, corrupt receipt recovery, unconsented
cross-farm history, invalid dependency graphs and defensive copies of caller collections.

## Durable journal

`account/bootstrap/persistence/DurableBootstrapJournal.kt` stores a single immutable plan per
file. `prepare` commits before returning, and repeating an identical prepare retains receipts.
Different identity, consent, workspace, payload or dependency scope is rejected. `record`
accepts only exact operation-bound receipts whose prerequisite receipts already exist; repeating
one is idempotent, but changing its historical remote version is rejected. This adapter does
not authenticate the source of receipts; a future transport must do that.

Writes use a temporary file, file fsync and atomic replace with no non-atomic fallback. A
canonical-path JVM lock plus filesystem lock serializes instances/processes. The complete
snapshot has a versioned binary envelope and SHA-256 corruption checksum. Reads reject
corruption/truncation/trailing bytes and never silently reset an unreadable plan. UTF-8 encoding
and decoding are strict; fields are capped at 4 MiB, UUID collections at 10,000 and complete
files at 16 MiB. Larger plans need an explicit partition/storage design, never silent clipping.
Orphaned UUID-named temporary files are cleaned under the filesystem lock.

Caller must use app-private no-backup storage. No Context factory is wired. These records
contain agricultural payloads, but no credentials. A checksum is not protection against a
malicious file writer. File fsync and atomic rename demonstrate process interruption recovery;
no directory-fsync or device power-loss guarantee is claimed.

Verification adds 11 journal tests plus a real child-process test forcibly killing writers at
three boundaries: prepared, receipt fsynced before rename, and receipt committed. Recovery
retains exact operation UUIDs, payloads, actor, workspace and consent, and exposes only pending
steps after confirming current actor/consent. Additional cases exercise concurrent instances,
wrong/out-of-order receipts, immutable replay, corruption, strict Unicode, large UTF-8 snapshots
and size limits preserving an existing valid file. These are JVM tests, not Android device QA.

## Remaining integration

Extract full Room snapshots within a consistent transaction; verify actual farm
reservation proofs; implement the entity codecs; check fresh identity and durable consent
immediately around transport. No Context factory, Room migration, HTTP, worker, UI activation,
server deployment or remote sync status is introduced here. Existing unbound outbox entries
remain unbound. Profile, irrigation and publication remain subsequent work.
