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
import com.isivoltpro.maginaolivo.domain.farm.NewFarm
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
            assertEquals(2, archived?.metadata?.version)
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
    }
}
