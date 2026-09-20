# Gate 6 Campaigns Implementation Plan

> **For agentic workers:** implement task-by-task with TDD; each task must be green and committed before the next begins.

**Goal:** Deliver a production, offline-first Campaign lifecycle with immutable historical Parcel snapshots and real Farm → Campaign navigation.

**Architecture:** Campaign is a local-first aggregate rooted in `campaigns`; `campaign_parcels` are aggregate children materialized atomically on activation. Compose reads Room-backed `Flow` only through ViewModels and a repository. Closed history renders snapshot values, never current Farm/Parcel names or geometry.

**Tech Stack:** Kotlin, Room, coroutines/Flow, Jetpack Compose, Navigation Compose, JUnit, AndroidX instrumented tests.

**Spec:** `docs/00-master/RC1-BASELINE.md`, `docs/00-master/RC1-NORMATIVE-ADDENDUM.md` D3/D4/D7/D9, and `docs/00-master/MASTER-SPEC-RC1.md` §8.

## Global constraints

- Room remains the immediate UI source of truth; every successful mutation commits locally before sync.
- Lifecycle is `PREPARATION → ACTIVE → HARVEST → CLOSED`; persistence may migrate legacy `PLANNED` to `PREPARATION`.
- Parcel selection is editable only in `PREPARATION`.
- Activation atomically freezes Farm/Parcel names, managed area, cadastral reference and geometry.
- ACTIVE, HARVEST and CLOSED campaigns are protected from normal deletion; CLOSED edits require explicit reopen.
- At most one ACTIVE or HARVEST campaign may exist per Farm.
- Snapshot children synchronize with the Campaign aggregate through one deterministic outbox intent.
- No fabricated kg, yield, delivery or expense totals; absent data renders an explicit empty/unknown state.
- No new third-party dependency.

## Review focus

- activation with zero selected Parcels must fail without changing status or queuing outbox work;
- activation after a Farm/Parcel rename must freeze values visible at activation time;
- later Farm/Parcel edits must not change an activated or closed Campaign detail;
- a second active Campaign for the same Farm must fail atomically;
- end dates before start dates and illegal lifecycle transitions must leave persisted state untouched.

---

### Task 1: Room v3 Campaign snapshot foundation

**Files:**
- Modify: `app/src/main/java/com/isivoltpro/maginaolivo/data/local/model/SyncModels.kt`
- Modify: `app/src/main/java/com/isivoltpro/maginaolivo/data/local/entity/CoreEntities.kt`
- Create: `app/src/main/java/com/isivoltpro/maginaolivo/data/local/model/CampaignRows.kt`
- Create: `app/src/main/java/com/isivoltpro/maginaolivo/data/local/dao/CampaignDao.kt`
- Modify: `app/src/main/java/com/isivoltpro/maginaolivo/data/local/DatabaseMigrations.kt`
- Modify: `app/src/main/java/com/isivoltpro/maginaolivo/data/local/MaginaOlivoDatabase.kt`
- Modify: `app/src/androidTest/java/com/isivoltpro/maginaolivo/data/local/RoomMigrationTest.kt`
- Add generated schema: `app/schemas/com.isivoltpro.maginaolivo.data.local.MaginaOlivoDatabase/3.json`

**Produces:** `CampaignDao`; `CampaignParcelSnapshotEntity`; Room schema v3 and `MIGRATION_2_3`.

- [ ] Add a failing migration test that opens the v2 fixture, migrates to v3, preserves existing Campaign rows, and verifies `campaign_parcels` columns, FKs and indexes.
- [ ] Run `:app:connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.isivoltpro.maginaolivo.data.local.RoomMigrationTest`; expect failure because v3 does not exist.
- [ ] Replace `PLANNED` with `PREPARATION`, add `HARVEST`, define snapshot entity fields from D3 plus local metadata, and add DAO observation/mutation queries.
- [ ] Implement `MIGRATION_2_3`, register the entity/DAO, set `VERSION = 3`, and export schema 3.
- [ ] Compile instrumented tests and run the migration test; expect PASS.
- [ ] Commit `feat(android): add Campaign snapshot Room schema`.

### Task 2: Offline Campaign aggregate repository

**Files:**
- Create: `app/src/main/java/com/isivoltpro/maginaolivo/domain/campaign/CampaignRepository.kt`
- Create: `app/src/main/java/com/isivoltpro/maginaolivo/data/repository/OfflineFirstCampaignRepository.kt`
- Modify: `app/src/main/java/com/isivoltpro/maginaolivo/app/LocalPersistence.kt`
- Modify: `app/src/main/java/com/isivoltpro/maginaolivo/app/AppCompositionRoot.kt`
- Create: `app/src/androidTest/java/com/isivoltpro/maginaolivo/data/local/OfflineFirstCampaignRepositoryTest.kt`

**Produces:** `CampaignRepository` with `observeForFarm`, `observe(id)`, `create`, `updatePreparation`, `activate`, `markHarvest`, `close`, `reopen`, and `archivePreparation`.

- [ ] Write failing repository tests for create/outbox, preparation edits, zero-Parcel activation rollback, atomic snapshots, duplicate-active rejection, legal transitions, close/reopen, protected delete, restart and historical stability after Farm/Parcel edits.
- [ ] Run the Campaign repository class on API 35; expect failures because the repository is absent.
- [ ] Implement domain models/validation and transactional repository methods using `CampaignDao`, `ParcelDao`, `FarmDao`, `WorkspaceDao` and `SyncOutboxDao`.
- [ ] Ensure each mutation increments Campaign version and replaces one pending Campaign outbox intent without independently synchronizing snapshots.
- [ ] Run repository tests normally and with airplane mode plus Wi-Fi disabled; expect PASS.
- [ ] Commit `feat(android): add offline Campaign aggregate`.

### Task 3: Campaign ViewModels and state contracts

**Files:**
- Create: `app/src/main/java/com/isivoltpro/maginaolivo/feature/campaigns/CampaignViewModels.kt`
- Create: `app/src/test/java/com/isivoltpro/maginaolivo/feature/campaigns/CampaignViewModelTest.kt`

**Produces:** `FarmCampaignsViewModel`, `CampaignDetailViewModel`, drafts and explicit loading/empty/success/error state.

- [ ] Write failing coroutine tests for initial loading, active/history partitioning, validation errors, every legal transition, rejected transitions and one-shot success messages.
- [ ] Run `:app:testDevDebugUnitTest`; expect Campaign ViewModel tests to fail to compile.
- [ ] Implement ViewModels that depend only on `CampaignRepository`, expose immutable `StateFlow`, and never access Room directly.
- [ ] Run all unit tests; expect PASS.
- [ ] Commit `feat(android): add Campaign presentation state`.

### Task 4: Production Campaign list, editor and history detail

**Files:**
- Create: `app/src/main/java/com/isivoltpro/maginaolivo/feature/campaigns/CampaignScreens.kt`
- Modify: `app/src/main/java/com/isivoltpro/maginaolivo/feature/farms/FarmScreens.kt`
- Create: `app/src/androidTest/java/com/isivoltpro/maginaolivo/CampaignScreensTest.kt`

**Produces:** Farm Campaign section, create/edit sheet, active/history list, snapshot-backed detail and lifecycle actions.

- [ ] Write failing Compose tests for empty state, create form validation, active/history labels, snapshot rendering, protected CLOSED state and accessible lifecycle confirmation.
- [ ] Run instrumented-test compilation; expect missing production screens.
- [ ] Implement canonical cream/olive screens using existing tokens/components and minimum 48dp actions; preserve reference screens only for DEV/reference tests.
- [ ] Display kg, deliveries, yield and expense summaries only from persisted rows; render `Sin datos` when absent.
- [ ] Compile and run the Campaign screen tests; expect PASS.
- [ ] Commit `feat(android): add production Campaign screens`.

### Task 5: Real Campaign navigation and combined Gate 6 flow

**Files:**
- Modify: `app/src/main/java/com/isivoltpro/maginaolivo/navigation/AppNavigation.kt`
- Modify: `app/src/androidTest/java/com/isivoltpro/maginaolivo/AppNavigationTest.kt`

**Produces:** real Farm → Campaign detail route with stable UUID argument and deterministic Back behavior.

- [ ] Add a failing E2E test: onboarding → create Farm → create Parcel → create Campaign selecting that Parcel → activate → close → Back → reopen app → verify historical snapshot and persistence.
- [ ] Run the AppNavigation test on API 35; expect failure because the route still renders `CampaignReferenceScreen`.
- [ ] Replace the production Campaign reference destination with `CampaignDetailRoute`; connect Farm active/history callbacks using real UUIDs.
- [ ] Re-run the E2E test and the complete 42+ instrumented suite; expect PASS and an empty crash buffer.
- [ ] Commit `feat(android): connect Campaign lifecycle navigation`.

### Task 6: Gate 6 validation and evidence

**Files:**
- Create: `docs/06-testing/PHASE6-CAMPAIGNS-SLICE.md`
- Modify: `docs/00-master/CURRENT-STATE.md`
- Modify: `docs/07-plans/PHASE6-CAMPAIGNS.md`

- [ ] Run `lintDevDebug testDevDebugUnitTest assembleDevDebugAndroidTest assembleDevDebug assembleStagingDebug assembleProductionDebug` locally; require PASS.
- [ ] Run the full API 35 emulator workflow, offline repository proof, UI hierarchy capture and crash-buffer check; require PASS.
- [ ] Verify and hash the installable DEV APK artifact.
- [ ] Record exact counts, run/artifact IDs, open bugs and `CAMPAIGNS SLICE = PASS` only when all checks are green.
- [ ] Mark `GATE 6 = PASS` only if Farms, Parcels, Campaigns and the combined persistence flow all pass; otherwise preserve FAIL with the blocker.
- [ ] Commit `docs(android): record Campaign slice and Gate 6 result`.

