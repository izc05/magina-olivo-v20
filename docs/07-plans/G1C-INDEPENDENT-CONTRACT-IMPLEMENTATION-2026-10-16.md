# G1c Independent Reservation Contract Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans to implement this plan inline. No subagents. A local commit is permitted only after GREEN and final root review.

**Status:** Draft preparation; blocked by #755/#757. No G1c activation or app wiring.

**Goal:** Prepare a pure Kotlin reservation/recovery contract without app wiring or remote activity.

**Architecture:** An immutable single-farm request, issuer-scoped actor, opaque receipt and abstract transport define the boundary. A pure recovery snapshot retains uncertain operations and validates receipts before recording a reserved state. Production persistence and server enforcement remain separate deferred work.

**Tech Stack:** Existing Kotlin/JVM, Java SHA-256/UUID and JUnit 4; existing Gradle 9.4.1 and Android Studio JBR only.

**Spec:** `docs/07-plans/G1C-REMOTE-RESERVATION-CONTRACT-2026-10-16.md`.

## Global constraints

No remote operations, SQL migrations, dependencies, agricultural DB edits, G1b ledger changes, app wiring, sync, APK, merge or deployment. Initial base is `feat/g1b-farm-consent` at `94c876f2`; final verification must use its reviewed update `68132f8c`. This preparation does not validate or activate G1/G2.

## Review focus

- Same UUID under another issuer must neither send nor apply a receipt.
- Account changes during response must retain a private receipt for the original actor.
- Unknown commit outcome must retry the original operation and exact payload.
- Mismatched or changed immutable receipts must fail closed, including restored snapshots.
- Foreign/legacy UUID conflicts expose no metadata and cannot retry into a reservation.

## Task: immutable contract and recovery

Create `app/src/main/java/com/isivoltpro/maginaolivo/account/reservation/RemoteReservationContract.kt`.
Test `app/src/test/java/com/isivoltpro/maginaolivo/account/reservation/RemoteReservationContractTest.kt`.

Interfaces: `ReservationTransport.reserve(scope: ReservationScope, request: ReservationRequest): ReservationResponse`; `ReservationRecovery.attempt(currentActor: () -> ReservationActor?, transport: ReservationTransport): ReservationRecovery`; `accept(incoming: ReservationReceipt, visibleActor: ReservationActor?): ReservationRecovery`.


- [x] Review baseline, existing G1b preparation contract, isolated branch at 94c876f2.
- [x] Write JVM scenarios for lost acknowledgement, changed replay, stale actor/receipt and conflict.
- [x] Observe RED via Gradle JVM task, then implement minimal immutable model and transport boundary.
- [x] Run directed and full JVM suite; review diff for absence of ledger, SQL, network and app wiring.

The client actor is local correlation only; future server authentication must derive actor from verified credentials. The transport must atomically enforce actor/operation replay and unique farm reservations. In-memory fixtures are not server security evidence. No durable journal, backup restore, SQL, SDK, JWT handling, APK, synchronization or gate activation is included.

## Review contract

- `ReservationRequest` binds only the selected farm and its consent, copy manifest and aggregate version. Consent text versions are positive integers (including G1b 1/2), matching its existing model. This is local correlation; a future server must validate the exact version against consent proof. No text upgrade or re-consent is inferred.
- `payloadVersion=1` canonical bytes are ordered UTF-8 fields, each prefixed by a four-byte big-endian byte length: domain `magina-reservation`, payload version, operation UUID, farm UUID, local workspace UUID, consent UUID, consent text version, lowercase manifest SHA-256, decimal aggregate version. Hash is lowercase SHA-256. This is a draft wire contract, not a deployed RPC.
- A scope is issuer + user UUID + operation. Issuer matching is exact; no project or account is inferred from a UUID alone. Issuers must be nonblank and at most 2048 characters. Persist scope/request before first send. The same operation and bytes survive retry or process recovery. A new operation cannot be substituted after timeout.
- Acknowledgements bind scope, farm and payload hash. Once retained, the whole immutable receipt must match. A different current actor retains a private receipt awaiting the original actor; no access is granted by any state here.
- Conflict and invalid receipt are terminal and generic. Repeated transport calls cannot overwrite them. Legacy/foreign UUID occupancy is represented solely as generic conflict; adapter/server implementation remains deferred.
- The only in-memory server lives under tests. No production persistence, credentials, API calls, SQL, new dependencies, app consumers, sync, APK or deployment are provided.

## Verification record

Initial RED: manual Gradle 9.4.1, Android Studio JBR, `:app:testDevDebugUnitTest --tests '*RemoteReservationContractTest' --no-daemon --max-workers=1 -Dorg.gradle.jvmargs=-Xmx1536m`: 9 tests, 8 expected assertion failures against provisional implementation. Existing Kotlin warnings outside this scope were present.

Second RED: 14 tests, 5 expected assertion failures (actor change during request, invalid restored receipt, nonpositive aggregate/consent versions, blank/oversized issuer), 1m19s. Fixes then validate constructor state and re-read the current actor after transport. Directed GREEN: all 14 tests passed, BUILD SUCCESSFUL in 1m18s. Root/independent review found no remaining blockers; an independent literal wire vector is added before the final directed run and rebase. Final directed GREEN including the independent wire vector: 15/15 passed, zero failures/errors, BUILD SUCCESSFUL in 52s. Rebased full JVM suite on G1b `68132f8c`: 696 tests in 134 suites, zero failures/errors/skips, BUILD SUCCESSFUL in 1m28s. Command: the same manual Gradle invocation without the `--tests` filter. No APK output directory exists; source references to the transport/recovery exist solely in their own new file. The diff contains exactly this plan, the pure contract and its test.

The transport boundary is synchronous and currently has no IO implementation; a future adapter must execute outside the UI thread or evolve the boundary to suspend. This change provides no adapter or scheduling.


Canonical interoperability vector (independently calculated with .NET MemoryStream/SHA256): operation UUID `00000000-0000-0000-0000-000000000003`, farm UUID ending `0002`, local workspace UUID ending `0004`, consent UUID ending `0005` (same all-zero UUID prefix), consent version `1`, manifest 64 lowercase `a` characters, aggregate version `7`. Together with domain `magina-reservation` and payload version `1`, the length-prefixed UTF-8 sequence is 265 bytes and SHA-256 is `cf676e143f408ff520e646aed3ac57fd3ae1838eca2d769775c46b1668a7de33`. The JVM test asserts this literal without deriving its expectation from production code.
