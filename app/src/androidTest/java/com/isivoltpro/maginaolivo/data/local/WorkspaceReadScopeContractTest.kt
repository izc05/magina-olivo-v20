package com.isivoltpro.maginaolivo.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.core.dispatchers.AppDispatchers
import com.isivoltpro.maginaolivo.core.id.IdGenerator
import com.isivoltpro.maginaolivo.core.time.AppClock
import com.isivoltpro.maginaolivo.data.local.entity.ActivityEntity
import com.isivoltpro.maginaolivo.data.local.entity.CampaignEntity
import com.isivoltpro.maginaolivo.data.local.entity.DeliveryEntity
import com.isivoltpro.maginaolivo.data.local.entity.FarmEntity
import com.isivoltpro.maginaolivo.data.local.entity.HarvestEntity
import com.isivoltpro.maginaolivo.data.local.entity.LocalMetadata
import com.isivoltpro.maginaolivo.data.local.entity.WorkspaceEntity
import com.isivoltpro.maginaolivo.data.local.model.ActivityStatus
import com.isivoltpro.maginaolivo.data.local.model.CampaignStatus
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstActivityRepository
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstDeliveryRepository
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstHarvestRepository
import com.isivoltpro.maginaolivo.domain.activity.ActivityType
import com.isivoltpro.maginaolivo.domain.delivery.DeliverySource
import com.isivoltpro.maginaolivo.domain.workspace.WorkspaceRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkspaceReadScopeContractTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val now = Instant.parse("2026-10-06T10:00:00Z")
    private val date = LocalDate.parse("2026-10-06")

    private val workspaceA = UUID.fromString("10000000-0000-0000-0000-000000000463")
    private val workspaceB = UUID.fromString("10000000-0000-0000-0000-000000000464")
    private val farmA = UUID.fromString("20000000-0000-0000-0000-000000000463")
    private val farmB = UUID.fromString("20000000-0000-0000-0000-000000000464")
    private val campaignA = UUID.fromString("30000000-0000-0000-0000-000000000463")
    private val campaignB = UUID.fromString("30000000-0000-0000-0000-000000000464")
    private val activityA = UUID.fromString("40000000-0000-0000-0000-000000000463")
    private val activityB = UUID.fromString("40000000-0000-0000-0000-000000000464")
    private val harvestA = UUID.fromString("50000000-0000-0000-0000-000000000463")
    private val harvestB = UUID.fromString("50000000-0000-0000-0000-000000000464")
    private val deliveryA = UUID.fromString("60000000-0000-0000-0000-000000000463")
    private val deliveryB = UUID.fromString("60000000-0000-0000-0000-000000000464")

    private lateinit var db: MaginaOlivoDatabase

    @Before
    fun before() = runBlocking {
        context.deleteDatabase(DB)
        db = MaginaOlivoDatabase.create(context, DB)
        seed()
    }

    @After
    fun after() {
        db.close()
        context.deleteDatabase(DB)
    }

    @Test
    fun homeReadModelsNeverMixWorkspaces() = runBlocking {
        val active = FixedWorkspace(workspaceA)
        val clock = FixedClock(now)
        val activities = OfflineFirstActivityRepository(
            db,
            clock,
            RandomIds,
            TestDispatchers,
            workspaceRepository = active,
        )
        val harvests = OfflineFirstHarvestRepository(
            db,
            clock,
            RandomIds,
            TestDispatchers,
            workspaceRepository = active,
        )
        val deliveries = OfflineFirstDeliveryRepository(
            db,
            clock,
            RandomIds,
            TestDispatchers,
            workspaceRepository = active,
        )

        assertEquals(listOf(activityA), activities.observeAgenda().first().map { it.activityId })
        assertEquals(listOf(harvestA), harvests.observeAll().first().map { it.id })
        assertEquals(listOf(campaignA), harvests.observeContexts().first().map { it.campaignId })
        assertEquals(listOf(deliveryA), deliveries.observeAll().first().map { it.id })
        assertEquals(listOf(campaignA), deliveries.observeContexts().first().map { it.campaignId })
    }

    private suspend fun seed() {
        val meta = LocalMetadata(now, now)
        db.workspaceDao().upsert(workspace(workspaceA, "Olivar A", meta))
        db.workspaceDao().upsert(workspace(workspaceB, "Olivar B", meta))

        db.farmDao().upsert(FarmEntity(farmA, workspaceA, "Finca A", metadata = meta))
        db.farmDao().upsert(FarmEntity(farmB, workspaceB, "Finca B", metadata = meta))

        db.campaignDao().upsert(
            CampaignEntity(campaignA, workspaceA, farmA, "Campaña A", date.minusMonths(1), status = CampaignStatus.HARVEST, metadata = meta),
        )
        db.campaignDao().upsert(
            CampaignEntity(campaignB, workspaceB, farmB, "Campaña B", date.minusMonths(1), status = CampaignStatus.HARVEST, metadata = meta),
        )

        db.activityDao().upsert(
            ActivityEntity(activityA, workspaceA, campaignA, farmA, date, ActivityType.PRUNING.name, ActivityStatus.PLANNED, "Poda A", metadata = meta),
        )
        db.activityDao().upsert(
            ActivityEntity(activityB, workspaceB, campaignB, farmB, date, ActivityType.PRUNING.name, ActivityStatus.PLANNED, "Poda B", metadata = meta),
        )

        db.harvestDao().upsert(
            HarvestEntity(harvestA, workspaceA, campaignA, farmA, date, 1_000_000, metadata = meta),
        )
        db.harvestDao().upsert(
            HarvestEntity(harvestB, workspaceB, campaignB, farmB, date, 2_000_000, metadata = meta),
        )

        db.deliveryDao().upsert(
            DeliveryEntity(deliveryA, workspaceA, farmA, campaignA, date, destinationName = "Cooperativa A", netGrams = 1_000_000, source = DeliverySource.MANUAL.name, metadata = meta),
        )
        db.deliveryDao().upsert(
            DeliveryEntity(deliveryB, workspaceB, farmB, campaignB, date, destinationName = "Cooperativa B", netGrams = 2_000_000, source = DeliverySource.MANUAL.name, metadata = meta),
        )
    }

    private fun workspace(id: UUID, name: String, meta: LocalMetadata) = WorkspaceEntity(
        id = id,
        name = name,
        ownerUserId = UUID.nameUUIDFromBytes(name.toByteArray()),
        countryCode = "ES",
        timezone = "Europe/Madrid",
        locale = "es-ES",
        currency = "EUR",
        metadata = meta,
    )

    private class FixedWorkspace(private val id: UUID) : WorkspaceRepository {
        override suspend fun ensureLocalWorkspace(): AppResult<UUID> = AppResult.Success(id)
    }

    private data class FixedClock(val value: Instant) : AppClock {
        override fun nowInstant() = value
        override fun today(zoneId: ZoneId) = LocalDate.ofInstant(value, zoneId)
    }

    private object RandomIds : IdGenerator {
        override fun newId(): UUID = UUID.randomUUID()
    }

    private object TestDispatchers : AppDispatchers {
        override val default: CoroutineDispatcher = Dispatchers.Unconfined
        override val io: CoroutineDispatcher = Dispatchers.Unconfined
        override val main: CoroutineDispatcher = Dispatchers.Unconfined
    }

    private companion object {
        const val DB = "workspace-read-scope-contract-test.db"
    }
}
