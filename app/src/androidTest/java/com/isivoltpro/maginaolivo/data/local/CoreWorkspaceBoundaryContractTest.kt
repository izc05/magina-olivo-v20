package com.isivoltpro.maginaolivo.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.isivoltpro.maginaolivo.core.common.AppError
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.core.dispatchers.AppDispatchers
import com.isivoltpro.maginaolivo.core.id.IdGenerator
import com.isivoltpro.maginaolivo.core.time.AppClock
import com.isivoltpro.maginaolivo.data.local.entity.FarmEntity
import com.isivoltpro.maginaolivo.data.local.entity.FarmParcelMembershipEntity
import com.isivoltpro.maginaolivo.data.local.entity.LocalMetadata
import com.isivoltpro.maginaolivo.data.local.entity.ParcelEntity
import com.isivoltpro.maginaolivo.data.local.entity.WorkspaceEntity
import com.isivoltpro.maginaolivo.data.local.model.SyncEntityType
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstActivityRepository
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstCampaignRepository
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstFarmRepository
import com.isivoltpro.maginaolivo.domain.activity.ActivityChanges
import com.isivoltpro.maginaolivo.domain.activity.ActivityType
import com.isivoltpro.maginaolivo.domain.activity.NewActivity
import com.isivoltpro.maginaolivo.domain.campaign.NewCampaign
import com.isivoltpro.maginaolivo.domain.farm.FarmChanges
import com.isivoltpro.maginaolivo.domain.farm.NewFarm
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** #615: a valid UUID from Workspace B is still foreign while Workspace A is active. */
@RunWith(AndroidJUnit4::class)
class CoreWorkspaceBoundaryContractTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val workspaceA = uuid("10000000-0000-0000-0000-0000000000a1")
    private val workspaceB = uuid("10000000-0000-0000-0000-0000000000b1")
    private val farmA = uuid("20000000-0000-0000-0000-0000000000a1")
    private val farmB = uuid("20000000-0000-0000-0000-0000000000b1")
    private val parcelA = uuid("30000000-0000-0000-0000-0000000000a1")
    private val parcelB = uuid("30000000-0000-0000-0000-0000000000b1")
    private val now = Instant.parse("2026-10-06T12:00:00Z")
    private lateinit var db: MaginaOlivoDatabase
    private lateinit var active: MutableWorkspace

    @Before fun before() = runBlocking {
        context.deleteDatabase(DB)
        db = MaginaOlivoDatabase.create(context, DB)
        seed()
        active = MutableWorkspace(workspaceB)
    }

    @After fun after() {
        db.close()
        context.deleteDatabase(DB)
    }

    @Test fun foreignFarmCampaignAndActivityStayInvisibleAndImmutable() = runBlocking {
        val farms = OfflineFirstFarmRepository(db, Clock, Ids, TestDispatchers, active)
        val campaigns = OfflineFirstCampaignRepository(db, Clock, Ids, TestDispatchers, active)
        val activities = OfflineFirstActivityRepository(
            db, Clock, Ids, TestDispatchers, workspaceRepository = active,
        )

        // Build legitimate B history while B is the active experience.
        val campaignB = successId(
            campaigns.create(
                NewCampaign(
                    farmId = farmB,
                    name = "B 2026/27",
                    startDate = LocalDate.of(2026, 10, 1),
                    parcelIds = setOf(parcelB),
                ),
            ),
        )
        assertEquals(AppResult.Success(Unit), campaigns.activate(campaignB))
        val activityB = successId(
            activities.create(
                NewActivity(
                    farmId = farmB,
                    campaignId = campaignB,
                    type = ActivityType.OTHER,
                    activityDate = LocalDate.of(2026, 10, 5),
                    description = "Trabajo B",
                    parcelIds = setOf(parcelB),
                    asDraft = true,
                ),
            ),
        )
        // #427: archiving is no longer an implicit cascade. Resolve the operational Campaign
        // first, then archive B so this test can keep exercising an archived foreign Farm.
        assertEquals(AppResult.Success(Unit), campaigns.close(campaignB, LocalDate.of(2026, 10, 6)))
        assertEquals(AppResult.Success(Unit), farms.archive(farmB))

        val farmBefore = db.farmDao().findById(farmB)!!
        val campaignBefore = db.campaignDao().findById(campaignB)!!
        val activityBefore = db.activityDao().findById(activityB)!!
        val farmOutboxBefore = db.syncOutboxDao().listForEntity(SyncEntityType.FARM, farmB)
        val campaignOutboxBefore = db.syncOutboxDao().listForEntity(SyncEntityType.CAMPAIGN, campaignB)
        val activityOutboxBefore = db.syncOutboxDao().listForEntity(SyncEntityType.ACTIVITY, activityB)

        active.id = workspaceA

        // Detail/list reads from A do not expose B, including B's archived historical Farm.
        assertNull(farms.observeById(farmB).first())
        assertNull(campaigns.observe(campaignB).first())
        assertTrue(campaigns.observeForFarm(farmB).first().isEmpty())
        assertNull(activities.observe(activityB).first())
        assertTrue(activities.observeForFarm(farmB).first().isEmpty())
        assertTrue(activities.observeForParcel(parcelB).first().isEmpty())

        // A cannot resurrect/edit B's Farm.
        assertContextMismatch(farms.restore(farmB))
        assertContextMismatch(farms.update(farmB, FarmChanges("No tocar B")))
        assertContextMismatch(farms.archive(farmB))

        // A cannot mutate B's Campaign even with a perfectly valid UUID.
        assertContextMismatch(campaigns.close(campaignB, LocalDate.of(2027, 1, 10)))
        assertContextMismatch(campaigns.archivePreparation(campaignB))
        assertContextMismatch(
            campaigns.create(NewCampaign(farmB, "Intrusa", LocalDate.of(2026, 10, 7))),
        )

        // A cannot mutate B's Activity or attach B's Campaign to A.
        assertContextMismatch(
            activities.update(
                activityB,
                ActivityChanges(
                    ActivityType.OTHER,
                    LocalDate.of(2026, 10, 5),
                    "No tocar B",
                    setOf(parcelB),
                ),
            ),
        )
        assertContextMismatch(activities.plan(activityB))
        val crossCampaign = activities.create(
            NewActivity(
                farmId = farmA,
                campaignId = campaignB,
                type = ActivityType.OTHER,
                activityDate = LocalDate.of(2026, 10, 6),
                description = "Cruce",
                parcelIds = setOf(parcelA),
                asDraft = true,
            ),
        )
        assertValidation("campaignId", "context_mismatch", crossCampaign)

        // No rejected operation changed B or created a second intent.
        assertEquals(farmBefore, db.farmDao().findById(farmB))
        assertEquals(campaignBefore, db.campaignDao().findById(campaignB))
        assertEquals(activityBefore, db.activityDao().findById(activityB))
        assertEquals(farmOutboxBefore, db.syncOutboxDao().listForEntity(SyncEntityType.FARM, farmB))
        assertEquals(campaignOutboxBefore, db.syncOutboxDao().listForEntity(SyncEntityType.CAMPAIGN, campaignB))
        assertEquals(activityOutboxBefore, db.syncOutboxDao().listForEntity(SyncEntityType.ACTIVITY, activityB))

        // Normal A work still works offline.
        val campaignA = campaigns.create(
            NewCampaign(farmA, "A 2026/27", LocalDate.of(2026, 10, 1), setOf(parcelA)),
        )
        assertTrue(campaignA is AppResult.Success)
        val activityA = activities.create(
            NewActivity(
                farmId = farmA,
                type = ActivityType.OTHER,
                activityDate = LocalDate.of(2026, 10, 6),
                description = "Trabajo A",
                parcelIds = setOf(parcelA),
                asDraft = true,
            ),
        )
        assertTrue(activityA is AppResult.Success)

        // Even create Farm cannot smuggle an arbitrary Workspace id from the UI.
        assertContextMismatch(farms.create(NewFarm(workspaceId = workspaceB, name = "Intrusa B")))
    }

    private suspend fun seed() {
        val meta = LocalMetadata(now, now)
        db.workspaceDao().upsert(workspace(workspaceA, "A", meta))
        db.workspaceDao().upsert(workspace(workspaceB, "B", meta))
        db.farmDao().upsert(FarmEntity(farmA, workspaceA, "Finca A", metadata = meta))
        db.farmDao().upsert(FarmEntity(farmB, workspaceB, "Finca B", metadata = meta))
        db.parcelDao().upsert(ParcelEntity(parcelA, workspaceA, "Parcela A", source = "MANUAL", managedAreaM2 = 1_000.0, metadata = meta))
        db.parcelDao().upsert(ParcelEntity(parcelB, workspaceB, "Parcela B", source = "MANUAL", managedAreaM2 = 1_000.0, metadata = meta))
        db.parcelDao().upsertMembership(FarmParcelMembershipEntity(UUID.randomUUID(), workspaceA, farmA, parcelA, now, metadata = meta))
        db.parcelDao().upsertMembership(FarmParcelMembershipEntity(UUID.randomUUID(), workspaceB, farmB, parcelB, now, metadata = meta))
    }

    private fun workspace(id: UUID, name: String, meta: LocalMetadata) =
        WorkspaceEntity(id, name, UUID.randomUUID(), "ES", "Europe/Madrid", "es-ES", "EUR", meta)

    private fun successId(result: AppResult<UUID>): UUID =
        (result as AppResult.Success).value

    private fun assertContextMismatch(result: AppResult<*>) {
        val error = (result as AppResult.Failure).error as AppError.Validation
        assertEquals("context_mismatch", error.code)
    }

    private fun assertValidation(field: String, code: String, result: AppResult<*>) {
        val error = (result as AppResult.Failure).error as AppError.Validation
        assertEquals(field, error.field)
        assertEquals(code, error.code)
    }

    private class MutableWorkspace(var id: UUID) : WorkspaceRepository {
        override suspend fun ensureLocalWorkspace(): AppResult<UUID> = AppResult.Success(id)
    }

    private object Clock : AppClock {
        override fun nowInstant(): Instant = Instant.parse("2026-10-06T12:00:00Z")
        override fun today(zoneId: ZoneId): LocalDate = LocalDate.of(2026, 10, 6)
    }

    private object Ids : IdGenerator {
        override fun newId(): UUID = UUID.randomUUID()
    }

    private object TestDispatchers : AppDispatchers {
        override val default: CoroutineDispatcher = Dispatchers.Unconfined
        override val io: CoroutineDispatcher = Dispatchers.Unconfined
        override val main: CoroutineDispatcher = Dispatchers.Unconfined
    }

    private fun uuid(value: String): UUID = UUID.fromString(value)

    private companion object {
        const val DB = "core-workspace-boundary-test.db"
    }
}
