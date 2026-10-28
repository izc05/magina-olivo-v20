# G2.6 Parent Sync Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Resolve the six pending parent operations from Android B with explicit provenance, account isolation, and no loss of offline data.
**Architecture:** Account-bound workspace selection precedes transport. A separate resolution journal records fixture equivalence and conflicts; only a real client ACK changes Room sync state. Server RPCs use immutable operation IDs, canonical hashes, and version compare-and-swap.
**Tech Stack:** Kotlin, Room, Supabase Auth/Postgres RPC/RLS, Android emulator, Gradle.
**Spec:** `docs/07-plans/G2.6-PARENT-SYNC-DESIGN-2026-10-28.md`

## Global Constraints

- [ ] Integrate prerequisite PRs in reviewed order; revalidate checks on each new SHA. Never infer B transmission from a DEV fixture receipt.
- [ ] Keep B, its receipts, the confirmed delivery, production, and candidate APKs untouched throughout design and implementation. Use isolated QA databases/devices for positive sends.
- [ ] Do not enable a general scheduler or cross-workspace transport in G2.6.

## Review Focus

Provenance of existing DEV receipts; campaign version conflict; preservation of outbox and child rows; account binding during logout and switch; automatic workspace creation.

---

## Task 1: Immutable evidence and operation classification

**Files:** Add a dedicated G2.6 evidence manifest under `docs/07-plans/`; add a Room resolution-journal entity, DAO, migration, and repository in the Android data module. Keep encrypted raw exports outside Git.

- [ ] Read six B operations and their full graph without writes; record IDs, revisions, canonical hashes, issuer/account, dependencies, and the confirmed delivery baseline.
- [ ] Read DEV rows and `sync_operations` with script provenance. Classify WORKSPACE/FARM/PARCEL as fixture receipts, CAMPAIGN as divergent fixture, PROFILE_SETTINGS/HARVEST as unsupported or local-only.
- [ ] Implement journal states `RECONCILED_FIXTURE`, `LOCAL_ONLY`, `CONFLICT`, `BLOCKED_UNSUPPORTED`, and `CONFIRMED_BY_CLIENT_ACK`; permit only the last to mark Room `SYNCED`/outbox `CONFIRMED`.
- [ ] Test migration without row loss and transition rules, including rejection of fixture-to-ACK promotion.

## Task 2: Bind workspace selection to identity

**Files:** `LocalWorkspaceRepository`, `WorkspaceDao`, `ActiveWorkspaceScope`, account/session integration, weather and market callers, plus focused Room and UI tests.

- [ ] Replace global `findFirstActive()` selection with explicit `(issuer, account_id, workspace_id)` binding and a local-unbound mode. Persist selection per identity.
- [ ] Remove passive `ensureLocalWorkspace()` calls; allow local workspace creation only on the first explicit offline agricultural write.
- [ ] Verify fresh install, import with an existing local workspace, A→C→A, logout, revoked consent, and expired token. No account sees another account's workspace.
- [ ] Preserve every existing workspace, outbox row, and local file during migration.

## Task 3: Canonical parent contracts and server guardrails

**Files:** Versioned DTOs and serializers in the sync data module; new Supabase migrations and ephemeral Postgres tests for RPC/RLS; no production migration.

- [ ] Define canonical hashes for complete parent aggregates, memberships, historical snapshots, harvest partitions, and tombstones. Pin timestamp normalization and UUID semantics.
- [ ] Add campaign update CAS and harvest create RPCs; derive actor from JWT, enforce owner/issuer/consent, commit aggregate plus receipt atomically, reject UUID reuse with changed hash.
- [ ] Test same-request retry, timeout after server commit, concurrent campaign version updates, incomplete child aggregate, and C cross-account denial.
- [ ] Keep PROFILE_SETTINGS local reminders device-only; document a separate remote preference contract before any upload of municipality/organization fields.

## Task 4: Client resolution and ACK recovery

**Files:** G2.6 coordinator, outbox repository, Room transaction code, and focused unit/integration tests.

- [ ] Resolve dependencies in order workspace → farm reservation/consent → farm → parcel/memberships → campaign/snapshots → harvest/partitions; handle profile settings separately.
- [ ] Before sending, compare the immutable local manifest to remote HEAD and receipts. Existing fixture UUIDs enter reconciliation; divergent campaign enters conflict/CAS review. Never overwrite or silently adopt.
- [ ] Retry only identical operation UUID/hash. Recover timeout by querying receipt and HEAD; apply ACK in one Room transaction only if the local revision still matches.
- [ ] Test local edits after ACK, force stop, offline restart, conflict retention, and byte-for-byte preservation of the already confirmed delivery.

## Task 5: Isolated end-to-end gate and release decision

**Files:** G2.6 evidence report under `docs/07-plans/`; CI test definitions only if needed for reproducible guardrails.

- [ ] Run ephemeral Postgres/RLS and isolated Android QA scenarios. Capture counts and hashes before/after for six parents and all children; verify zero unintended writes to B and production.
- [ ] Exercise idempotency, conflict, account isolation, offline preservation, workspace creation and selection, token/consent changes, and ACK recovery.
- [ ] Check exact changed SHA and record GO/NO-GO against the design spec. A GO for DEV requires all negative tests green and explicit scope approval; general sync remains a later gate.
- [ ] Do not repeat G2.4/G2.5 physical tests unless changed code affects those flows. Do not build an APK candidate or merge PRs as part of this plan.
