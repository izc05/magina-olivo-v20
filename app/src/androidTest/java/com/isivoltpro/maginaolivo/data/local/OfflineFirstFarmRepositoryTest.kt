package com.isivoltpro.maginaolivo.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.core.dispatchers.AppDispatchers
import com.isivoltpro.maginaolivo.core.id.IdGenerator
import com.isivoltpro.maginaolivo.core.time.AppClock
import com.isivoltpro.maginaolivo.data.local.entity.LocalMetadata
import com.isivoltpro.maginaolivo.data.local.entity.WorkspaceEntity
import com.isivoltpro.maginaolivo.data.local.model.FarmStatus
import com.isivoltpro.maginaolivo.data.local.model.OutboxOperation
import com.isivoltpro.maginaolivo.data.local.model.SyncEntityType
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstFarmRepository
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstParcelRepository
import com.isivoltpro.maginaolivo.data.repository.LocalWorkspaceRepository
import com.isivoltpro.maginaolivo.domain.farm.FarmChanges
import com.isivoltpro.maginaolivo.domain.farm.NewFarm
import com.isivoltpro.maginaolivo.domain.parcel.NewParcel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.ArrayDeque
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfflineFirstFarmRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun clearDatabase() {
        context.deleteDatabase(TEST_DATABASE)
    }

    @After
    fun cleanUp() {
        context.deleteDatabase(TEST_DATABASE)
    }

    @Test
    fun offlineCreateArchiveAndOutboxSurviveDatabaseRestart() = runBlocking {
        val workspaceId = uuid("10000000-0000-0000-0000-000000000001")
        val farmId = uuid("20000000-0000-0000-0000-000000000001")
        val createOperationId = uuid("30000000-0000-0000-0000-000000000001")
        val archiveOperationId = uuid("30000000-0000-0000-0000-000000000002")
        val createdAt = Instant.parse("2026-09-19T17:00:00Z")

        val firstDatabase = MaginaOlivoDatabase.create(context, TEST_DATABASE)
        try {
            firstDatabase.workspaceDao().upsert(workspace(workspaceId, createdAt))
            val repository = repository(
                database = firstDatabase,
                now = createdAt,
                ids = listOf(farmId, createOperationId),
            )

            val result = repository.create(
                NewFarm(
                    workspaceId = workspaceId,
                    name = "  La Solana  ",
                    municipality = "  Huelma ",
                ),
            )

            assertEquals(AppResult.Success(farmId), result)
            assertEquals("La Solana", firstDatabase.farmDao().findById(farmId)?.name)
            assertEquals(
                listOf(OutboxOperation.CREATE),
                firstDatabase
                    .syncOutboxDao()
                    .listForEntity(SyncEntityType.FARM, farmId)
                    .map { it.operation },
            )
        } finally {
            firstDatabase.close()
        }

        val reopenedDatabase = MaginaOlivoDatabase.create(context, TEST_DATABASE)
        try {
            assertNotNull(reopenedDatabase.farmDao().findById(farmId))
            val repository = repository(
                database = reopenedDatabase,
                now = createdAt.plusSeconds(3600),
                ids = listOf(archiveOperationId),
            )

            assertEquals(AppResult.Success(Unit), repository.archive(farmId))
            assertTrue(repository.observeActive(workspaceId).first().isEmpty())

            val archived = reopenedDatabase.farmDao().findById(farmId)
            assertEquals(FarmStatus.ARCHIVED, archived?.status)
            assertNotNull(archived?.metadata?.deletedAt)
            assertEquals(2L, archived?.metadata?.version)
            assertEquals(
                listOf(OutboxOperation.CREATE, OutboxOperation.DELETE),
                reopenedDatabase
                    .syncOutboxDao()
                    .listForEntity(SyncEntityType.FARM, farmId)
                    .map { it.operation },
            )
        } finally {
            reopenedDatabase.close()
        }
    }

    @Test
    fun failedForeignKeyRollsBackFarmAndOutboxTogether() = runBlocking {
        val missingWorkspaceId = uuid("10000000-0000-0000-0000-000000000099")
        val farmId = uuid("20000000-0000-0000-0000-000000000099")

        val database = MaginaOlivoDatabase.create(context, TEST_DATABASE)
        try {
            val repository = repository(
                database = database,
                now = Instant.parse("2026-09-19T17:00:00Z"),
                ids = listOf(
                    farmId,
                    uuid("30000000-0000-0000-0000-000000000099"),
                ),
            )

            val result = repository.create(
                NewFarm(
                    workspaceId = missingWorkspaceId,
                    name = "Sin workspace",
                ),
            )

            assertTrue(result is AppResult.Failure)
            assertEquals(null, database.farmDao().findById(farmId))
            assertTrue(
                database
                    .syncOutboxDao()
                    .listForEntity(SyncEntityType.FARM, farmId)
                    .isEmpty(),
            )
        } finally {
            database.close()
        }
    }

    @Test
    fun devFixtureSeedIsDeterministicIdempotentAndDoesNotQueueSync() = runBlocking {
        val database = MaginaOlivoDatabase.create(context, TEST_DATABASE)
        try {
            DevDatabaseSeeder.seed(database)
            DevDatabaseSeeder.seed(database)

            assertEquals(
                "Mi olivar de muestra",
                database.workspaceDao().findById(DevDatabaseSeeder.workspaceId)?.name,
            )
            assertEquals(
                "La Solana",
                database.farmDao().findById(DevDatabaseSeeder.farmId)?.name,
            )
            assertTrue(
                database
                    .syncOutboxDao()
                    .listForEntity(SyncEntityType.FARM, DevDatabaseSeeder.farmId)
                    .isEmpty(),
            )
        } finally {
            database.close()
        }
    }

    @Test
    fun editArchiveAndRestoreSurviveRestartWithOrderedOutbox() = runBlocking {
        val workspaceId = uuid("10000000-0000-0000-0000-000000000020")
        val farmId = uuid("20000000-0000-0000-0000-000000000020")
        val ids = listOf(
            farmId,
            uuid("30000000-0000-0000-0000-000000000020"),
            uuid("30000000-0000-0000-0000-000000000021"),
            uuid("30000000-0000-0000-0000-000000000022"),
            uuid("30000000-0000-0000-0000-000000000023"),
        )
        val database = MaginaOlivoDatabase.create(context, TEST_DATABASE)
        try {
            database.workspaceDao().upsert(workspace(workspaceId, TEST_INSTANT))
            val repository = repository(database, TEST_INSTANT, ids)
            assertEquals(
                AppResult.Success(farmId),
                repository.create(NewFarm(workspaceId = workspaceId, name = "La Solana")),
            )
            assertEquals(
                AppResult.Success(Unit),
                repository.update(
                    farmId,
                    FarmChanges(
                        name = "Los Llanos",
                        municipality = "Huelma",
                        province = "Jaén",
                        notes = "Linde norte revisada",
                    ),
                ),
            )
            assertEquals(AppResult.Success(Unit), repository.archive(farmId))
            assertEquals(AppResult.Success(Unit), repository.restore(farmId))
        } finally {
            database.close()
        }

        val reopened = MaginaOlivoDatabase.create(context, TEST_DATABASE)
        try {
            val farm = reopened.farmDao().findById(farmId)
            assertEquals("Los Llanos", farm?.name)
            assertEquals("Huelma", farm?.municipality)
            assertEquals(FarmStatus.ACTIVE, farm?.status)
            assertEquals(null, farm?.metadata?.deletedAt)
            assertEquals(4L, farm?.metadata?.version)
            assertEquals(
                listOf(
                    OutboxOperation.CREATE,
                    OutboxOperation.UPDATE,
                    OutboxOperation.DELETE,
                    OutboxOperation.UPDATE,
                ),
                reopened
                    .syncOutboxDao()
                    .listForEntity(SyncEntityType.FARM, farmId)
                    .map { it.operation },
            )
        } finally {
            reopened.close()
        }
    }

    @Test
    fun activeSummaryDerivesCurrentParcelAreaAndCampaign() = runBlocking {
        val workspaceId = uuid("10000000-0000-0000-0000-000000000030")
        val farmId = uuid("20000000-0000-0000-0000-000000000030")
        val parcelId = uuid("40000000-0000-0000-0000-000000000030")
        val database = MaginaOlivoDatabase.create(context, TEST_DATABASE)
        try {
            database.workspaceDao().upsert(workspace(workspaceId, TEST_INSTANT))
            database.farmDao().upsert(
                com.isivoltpro.maginaolivo.data.local.entity.FarmEntity(
                    id = farmId,
                    workspaceId = workspaceId,
                    name = "La Solana",
                    metadata = LocalMetadata(TEST_INSTANT, TEST_INSTANT),
                ),
            )
            database.openHelper.writableDatabase.execSQL(
                """
                INSERT INTO parcels (
                    id, workspace_id, display_name, cadastral_reference, cadastral_polygon,
                    cadastral_parcel, municipality, province, source, geometry_geo_json,
                    cadastral_area_m2, managed_area_m2, notes, status, created_at, updated_at,
                    deleted_at, version, sync_status, remote_version, last_synced_at
                ) VALUES (
                    '$parcelId', '$workspaceId', 'Parcela Norte', NULL, NULL, NULL, 'Huelma',
                    'Jaén', 'MANUAL', NULL, 16000.0, 15000.0, NULL, 'ACTIVE',
                    1000, 1000, NULL, 1, 'LOCAL_ONLY', NULL, NULL
                )
                """.trimIndent(),
            )
            database.openHelper.writableDatabase.execSQL(
                """
                INSERT INTO farm_parcel_memberships (
                    id, workspace_id, farm_id, parcel_id, valid_from, valid_until,
                    created_at, updated_at, deleted_at, version, sync_status,
                    remote_version, last_synced_at
                ) VALUES (
                    '50000000-0000-0000-0000-000000000030', '$workspaceId', '$farmId',
                    '$parcelId', 1000, NULL, 1000, 1000, NULL, 1, 'LOCAL_ONLY', NULL, NULL
                )
                """.trimIndent(),
            )
            database.openHelper.writableDatabase.execSQL(
                """
                INSERT INTO campaigns (
                    id, workspace_id, farm_id, name, start_date, end_date, status, notes,
                    created_at, updated_at, deleted_at, version, sync_status,
                    remote_version, last_synced_at
                ) VALUES (
                    '60000000-0000-0000-0000-000000000030', '$workspaceId', '$farmId',
                    '2026/27', '2026-09-01', NULL, 'ACTIVE', NULL,
                    1000, 1000, NULL, 1, 'LOCAL_ONLY', NULL, NULL
                )
                """.trimIndent(),
            )

            val summary = repository(database, TEST_INSTANT, emptyList())
                .observeActive(workspaceId)
                .first()
                .single()

            assertEquals(1L, summary.parcelCount)
            assertEquals(15_000.0, summary.totalAreaM2 ?: 0.0, 0.0)
            assertEquals("2026/27", summary.activeCampaignName)
        } finally {
            database.close()
        }
    }

    @Test
    fun localWorkspaceBootstrapIsIdempotentAndQueuesCreateOnce() = runBlocking {
        val workspaceId = uuid("10000000-0000-0000-0000-000000000040")
        val database = MaginaOlivoDatabase.create(context, TEST_DATABASE)
        try {
            val repository = LocalWorkspaceRepository(
                database = database,
                clock = FixedClock(TEST_INSTANT),
                idGenerator = QueuedIdGenerator(
                    listOf(
                        workspaceId,
                        uuid("90000000-0000-0000-0000-000000000040"),
                        uuid("30000000-0000-0000-0000-000000000040"),
                    ),
                ),
                dispatchers = TestDispatchers,
                regionalContext = com.isivoltpro.maginaolivo.core.regional.RegionalContext.spainDefault(),
            )

            assertEquals(AppResult.Success(workspaceId), repository.ensureLocalWorkspace())
            assertEquals(AppResult.Success(workspaceId), repository.ensureLocalWorkspace())
            assertEquals("Mi olivar", database.workspaceDao().findById(workspaceId)?.name)
            assertEquals(
                listOf(OutboxOperation.CREATE),
                database.syncOutboxDao()
                    .listForEntity(SyncEntityType.WORKSPACE, workspaceId)
                    .map { it.operation },
            )
        } finally {
            database.close()
        }
    }

    @Test
    fun parcelLifecyclePreservesMembershipHistoryAndOutbox() = runBlocking {
        val workspaceId = uuid("10000000-0000-0000-0000-000000000080")
        val farmId = uuid("20000000-0000-0000-0000-000000000080")
        val parcelId = uuid("40000000-0000-0000-0000-000000000080")
        val database = MaginaOlivoDatabase.create(context, TEST_DATABASE)
        try {
            database.workspaceDao().upsert(workspace(workspaceId, TEST_INSTANT))
            assertEquals(
                AppResult.Success(farmId),
                repository(
                    database, TEST_INSTANT,
                    listOf(farmId, uuid("30000000-0000-0000-0000-000000000080")),
                ).create(NewFarm(workspaceId, "La Solana")),
            )
            fun parcels(now: Instant, ids: List<UUID>) = OfflineFirstParcelRepository(
                database, FixedClock(now), QueuedIdGenerator(ids), TestDispatchers,
            )
            assertEquals(
                AppResult.Success(parcelId),
                parcels(
                    TEST_INSTANT,
                    listOf(
                        parcelId,
                        uuid("50000000-0000-0000-0000-000000000080"),
                        uuid("60000000-0000-0000-0000-000000000080"),
                    ),
                ).create(NewParcel(farmId, "Parcela Norte", managedAreaM2 = 12_400.0)),
            )
            assertEquals("Parcela Norte", database.parcelDao().observeActive(farmId).first().single().parcel.displayName)
            assertEquals(
                AppResult.Success(Unit),
                parcels(
                    TEST_INSTANT.plusSeconds(1),
                    listOf(uuid("60000000-0000-0000-0000-000000000081")),
                ).archive(parcelId),
            )
            assertTrue(database.parcelDao().observeActive(farmId).first().isEmpty())
            val restored = parcels(
                TEST_INSTANT.plusSeconds(2),
                listOf(
                    uuid("50000000-0000-0000-0000-000000000081"),
                    uuid("60000000-0000-0000-0000-000000000082"),
                ),
            )
            assertEquals(AppResult.Success(Unit), restored.restore(parcelId, farmId))
            assertEquals(2, restored.membershipHistory(parcelId).size)
            assertEquals(
                listOf(OutboxOperation.CREATE, OutboxOperation.DELETE, OutboxOperation.UPDATE),
                database.syncOutboxDao().listForEntity(SyncEntityType.PARCEL, parcelId).map { it.operation },
            )
        } finally {
            database.close()
        }
    }

    private fun repository(
        database: MaginaOlivoDatabase,
        now: Instant,
        ids: List<UUID>,
    ) = OfflineFirstFarmRepository(
        database = database,
        clock = FixedClock(now),
        idGenerator = QueuedIdGenerator(ids),
        dispatchers = TestDispatchers,
    )

    private fun workspace(
        id: UUID,
        now: Instant,
    ) = WorkspaceEntity(
        id = id,
        name = "Mi olivar",
        ownerUserId = uuid("90000000-0000-0000-0000-000000000001"),
        countryCode = "ES",
        timezone = "Europe/Madrid",
        locale = "es-ES",
        currency = "EUR",
        metadata = LocalMetadata(createdAt = now, updatedAt = now),
    )

    private fun uuid(value: String): UUID = UUID.fromString(value)

    private class FixedClock(
        private val now: Instant,
    ) : AppClock {
        override fun nowInstant(): Instant = now

        override fun today(zoneId: ZoneId): LocalDate = LocalDate.ofInstant(now, zoneId)
    }

    private class QueuedIdGenerator(ids: List<UUID>) : IdGenerator {
        private val ids = ArrayDeque(ids)

        override fun newId(): UUID = ids.removeFirst()
    }

    private object TestDispatchers : AppDispatchers {
        override val default: CoroutineDispatcher = Dispatchers.Default
        override val io: CoroutineDispatcher = Dispatchers.IO
        override val main: CoroutineDispatcher = Dispatchers.Main
    }

    private companion object {
        const val TEST_DATABASE = "offline-farm-repository-test.db"
        val TEST_INSTANT: Instant = Instant.parse("2026-09-19T17:00:00Z")
    }
}
