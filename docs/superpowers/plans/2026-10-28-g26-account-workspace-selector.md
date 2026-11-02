# G2.6 Account Workspace Selector Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A signed-in account reads and writes only an explicitly selected workspace supported by verified account/issuer evidence; prior local data remains intact.
**Architecture:** Keep authorization distinct from UI selection. Derive remote workspace access from verified G2.5 import receipts, persist only the selected workspace key in an additive Room table, and require a nonexpired cached Auth session to resolve it offline. Unbound local workspaces are never attributed to an account through `owner_user_id`.
**Tech Stack:** Kotlin, Room, Compose, Android instrumentation, Gradle.
**Spec:** `docs/07-plans/G2.6-PARENT-SYNC-DESIGN-2026-10-28.md`

## Global Constraints

- No remote contract or persistent SQL change; no B/D mutation, no heavy delivery replay, no candidate APK.
- No generated UUID or outbox entry from selection or passive reads.
- Unknown identity, expired session, absent or conflicting receipt and missing selected workspace fail closed.
- Preserve Room data, outbox, PREPARE, receipts and files through migration and account switches.

## Review Focus

The original `owner_user_id` of `Mi olivar` is random; it proves no account ownership. A cached account session may be expired. Two accounts may have evidence for the same local workspace, which must block both. A Flow started under A can outlive a switch to C unless it is rechecked. Passive weather and Home reads must not create a workspace.

---

### Task 1: Identity and authorization policy

**Files:** `AccountRepository.kt`, new workspace policy and focused unit tests.

- [ ] Write failing tests for A/C, logout, expiry, offline and conflicting evidence.
- [ ] Implement a short-lived identity snapshot from the Auth owner and deterministic authorization from `(issuer, account_id, workspace_id)` receipt evidence.
- [ ] Run focused JVM tests and commit.

### Task 2: Durable selection and migration

**Files:** Room entity/DAO, `DatabaseMigrations.kt`, `MaginaOlivoDatabase.kt`, exported schema, `RoomMigrationTest.kt`.

- [ ] Write a failing 30→31 migration test preserving existing rows and an empty selector.
- [ ] Add an additive selection table keyed by issuer and account; no migration infers bindings from legacy owner IDs.
- [ ] Verify migration and persistence after restart on isolated API 35; commit.

### Task 3: Repository and UI wiring

**Files:** `WorkspaceRepository.kt`, `LocalWorkspaceRepository.kt`, `ActiveWorkspaceScope.kt`, passive read callers, account UI, repository tests.

- [ ] Write failing tests for zero/one/multiple authorized workspaces, explicit selection, cross-account reads/writes, missing workspace, logout, offline and no outbox on selection.
- [ ] Implement list/select/resolve APIs, passive resolution without creation, and Account UI affordance to select an authorized workspace.
- [ ] Recheck identity on repository reads/writes; run focused unit and instrumentation tests and commit.

### Task 4: CI and DRAFT PR

- [ ] Run scoped compile, JVM and API 35 instrumented tests. Review all read/write routes for cross-account leaks.
- [ ] Publish branch and a non-artifact CI gate for changed SHA, then create DRAFT PR.
- [ ] Record GO/NO-GO and any limitations without widening transport scope.
