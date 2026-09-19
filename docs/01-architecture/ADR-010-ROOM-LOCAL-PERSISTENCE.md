# ADR-010 — Room local persistence foundation

**Status:** Accepted for RC1.2 Phase 5  
**Date:** 2026-09-19  
**Baseline:** `RC1.2-BASELINE-2026-09-18`

## Context

Mágina Olivo must accept critical agricultural work without coverage and retain it across process or device restarts. Phase 5 needs a durable local source of truth and synchronization seams without introducing Supabase, WorkManager or production feature UI ahead of their gates.

## Decision

Use Room 2.8.5 with KSP, committed schema snapshots and explicit migrations. The application keeps its current single Gradle module, while package boundaries preserve the dependency direction:

```text
Compose → ViewModel/application layer → repository contract
                                      ↓
                            local repository → Room DAO
                                      ↓
                              domain row + outbox
```

Compose and navigation code must not import Room, a DAO or the database. Remote SDKs will update Room through later infrastructure implementations; they will not become a second UI source of truth.

## Schema foundation

Schema v2 contains:

- `workspaces` and `user_profiles` for ownership;
- `farms`, `parcels` and time-bounded `farm_parcel_memberships`;
- `campaigns`, `activities`, `harvests` and `expenses`;
- `documents` metadata, `weather_cache` and `alerts`;
- durable `sync_outbox` operations.

Synchronizable records use client-generated UUIDs and common metadata for creation/update time, soft deletion, local version, sync status, remote version and last synchronization time.

Machine timestamps are UTC `Instant`; agricultural dates are `LocalDate`; money is stored in minor currency units; harvest weight is stored in grams. Parcel geometry is reserved as GeoJSON rather than reduced to a centre point.

## Atomic writes

An accepted synchronizable mutation commits the domain row and outbox row in one `RoomDatabase.withTransaction` block. A failure rolls both back. Normal queries hide tombstones; explicit recovery/reconciliation reads may retain them.

The initial `OfflineFirstFarmRepository` is a vertical proof of this contract. It does not expose Farm feature UI early.

## Migrations

- exported schema snapshots are committed under `app/schemas`;
- `MIGRATION_1_2` is explicit and covered by Android instrumentation;
- destructive fallback is forbidden;
- migration tests retain an existing v1 workspace and validate the complete v2 schema.

## Fixtures

`DevDatabaseSeeder` lives only in the `dev` flavor. It has stable identifiers and timestamps, is idempotent, never queues synchronization, and is never invoked during normal startup. Production variants cannot reference or package it.

## Deferred

Phase 5 intentionally does not add:

- Supabase or remote payloads;
- WorkManager execution;
- production Farm forms or lists;
- attachment file copying;
- multi-device conflict UI.

Those concerns must build on these boundaries in their approved phases.

## Consequences

Positive:

- valid local data survives restart without network access;
- synchronization can retry using stable IDs and durable operations;
- schema changes are reviewable and testable;
- UI remains independent from persistence and remote SDK details.

Trade-offs:

- explicit migration SQL and repository mapping add maintenance work;
- the single Gradle module still relies on architecture tests and review for boundary enforcement.

## Validation

Gate 5 requires green lint/unit/build jobs plus API 35 instrumentation proving migration, close/reopen persistence, soft deletion, transaction rollback and CRUD while airplane mode is enabled.
