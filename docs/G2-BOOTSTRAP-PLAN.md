# Android bootstrap preparation

`BootstrapPlan` is a pure local prerequisite scheduler, not a sender or durable journal.
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

## Remaining integration

Persist the complete immutable plan and receipts atomically before sending; add crash/restart
tests; extract full Room snapshots within a consistent transaction; verify actual farm
reservation proofs; implement the entity codecs; check fresh identity and durable consent
immediately around transport. No Context factory, Room migration, HTTP, worker, UI activation,
server deployment or remote sync status is introduced here. Existing unbound outbox entries
remain unbound. Profile, irrigation and publication remain subsequent work.
