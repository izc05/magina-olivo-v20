package com.isivoltpro.maginaolivo.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.core.dispatchers.AppDispatchers
import com.isivoltpro.maginaolivo.core.id.IdGenerator
import com.isivoltpro.maginaolivo.core.time.AppClock
import com.isivoltpro.maginaolivo.data.local.entity.FarmEntity
import com.isivoltpro.maginaolivo.data.local.entity.FarmParcelMembershipEntity
import com.isivoltpro.maginaolivo.data.local.entity.LocalMetadata
import com.isivoltpro.maginaolivo.data.local.entity.ParcelEntity
import com.isivoltpro.maginaolivo.data.local.entity.WorkspaceEntity
import com.isivoltpro.maginaolivo.data.local.model.CampaignStatus
import com.isivoltpro.maginaolivo.data.local.model.OutboxOperation
import com.isivoltpro.maginaolivo.data.local.model.SyncEntityType
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstCampaignRepository
import com.isivoltpro.maginaolivo.domain.campaign.CampaignPreparationChanges
import com.isivoltpro.maginaolivo.domain.campaign.NewCampaign
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfflineFirstCampaignRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val workspaceId = UUID.fromString("10000000-0000-0000-0000-000000000001")
    private val farmId = UUID.fromString("20000000-0000-0000-0000-000000000001")
    private val parcelId = UUID.fromString("30000000-0000-0000-0000-000000000001")
    private val now = Instant.parse("2026-09-20T10:00:00Z")

    @Before fun before() { context.deleteDatabase(DB) }
    @After fun after() { context.deleteDatabase(DB) }

    @Test fun activationIsAtomicAndHistoricalSnapshotsStayImmutableAcrossRestart() = runBlocking {
        var db = MaginaOlivoDatabase.create(context, DB)
        seed(db)
        val repository = repository(db)
        val emptyId = (repository.create(NewCampaign(farmId, "Vacía", LocalDate.parse("2026-10-01"))) as AppResult.Success).value
        assertTrue(repository.activate(emptyId) is AppResult.Failure)
        assertEquals(CampaignStatus.PREPARATION, db.campaignDao().findById(emptyId)?.status)

        val campaignId = (repository.create(NewCampaign(farmId, "2026/27", LocalDate.parse("2026-10-01"), setOf(parcelId))) as AppResult.Success).value
        db.farmDao().upsert(db.farmDao().findById(farmId)!!.copy(name = "Nombre al activar"))
        db.parcelDao().upsert(db.parcelDao().findById(parcelId)!!.copy(displayName = "Parcela al activar"))
        assertEquals(AppResult.Success(Unit), repository.activate(campaignId))
        assertEquals(1, db.syncOutboxDao().listForEntity(SyncEntityType.CAMPAIGN, campaignId).size)
        assertEquals(OutboxOperation.CREATE, db.syncOutboxDao().listForEntity(SyncEntityType.CAMPAIGN, campaignId).single().operation)

        val secondId = (repository.create(NewCampaign(farmId, "Otra", LocalDate.parse("2027-10-01"), setOf(parcelId))) as AppResult.Success).value
        assertTrue(repository.activate(secondId) is AppResult.Failure)
        assertEquals(CampaignStatus.PREPARATION, db.campaignDao().findById(secondId)?.status)
        assertEquals(AppResult.Success(Unit), repository.markHarvest(campaignId))
        assertTrue(repository.close(campaignId, LocalDate.parse("2026-09-30")) is AppResult.Failure)
        assertEquals(AppResult.Success(Unit), repository.close(campaignId, LocalDate.parse("2027-02-01")))
        assertTrue(repository.archivePreparation(campaignId) is AppResult.Failure)

        db.farmDao().upsert(db.farmDao().findById(farmId)!!.copy(name = "Nombre posterior"))
        db.parcelDao().upsert(db.parcelDao().findById(parcelId)!!.copy(displayName = "Parcela posterior"))
        db.close()
        db = MaginaOlivoDatabase.create(context, DB)
        try {
            val restored = repository(db).observe(campaignId).first()!!
            assertEquals(CampaignStatus.CLOSED, restored.status)
            assertEquals("Nombre al activar", restored.snapshots.single().farmName)
            assertEquals("Parcela al activar", restored.snapshots.single().parcelName)
            assertEquals(parcelId, restored.snapshots.single().parcelId)
        } finally { db.close() }
    }

    @Test fun preparationSelectionCanChangeButCannotAfterActivation() = runBlocking {
        val db = MaginaOlivoDatabase.create(context, DB)
        try {
            seed(db)
            val repository = repository(db)
            val id = (repository.create(NewCampaign(farmId, "Borrador", LocalDate.parse("2026-10-01"))) as AppResult.Success).value
            assertEquals(AppResult.Success(Unit), repository.updatePreparation(id, CampaignPreparationChanges("Campaña", LocalDate.parse("2026-10-02"), setOf(parcelId))))
            assertEquals(AppResult.Success(Unit), repository.activate(id))
            assertTrue(repository.updatePreparation(id, CampaignPreparationChanges("Mutada", LocalDate.parse("2026-10-02"), emptySet())) is AppResult.Failure)
            assertEquals("Campaña", db.campaignDao().findById(id)?.name)
        } finally { db.close() }
    }

    private suspend fun seed(db: MaginaOlivoDatabase) {
        val meta = LocalMetadata(now, now)
        db.workspaceDao().upsert(WorkspaceEntity(workspaceId, "Olivar", UUID.randomUUID(), "ES", "Europe/Madrid", "es-ES", "EUR", meta))
        db.farmDao().upsert(FarmEntity(farmId, workspaceId, "Finca inicial", metadata = meta))
        db.parcelDao().upsert(ParcelEntity(parcelId, workspaceId, "Parcela inicial", source = "MANUAL", managedAreaM2 = 1200.0, metadata = meta))
        db.parcelDao().upsertMembership(FarmParcelMembershipEntity(UUID.randomUUID(), workspaceId, farmId, parcelId, now, metadata = meta))
    }

    private fun repository(db: MaginaOlivoDatabase) = OfflineFirstCampaignRepository(db, FixedClock(now), RandomIds, TestDispatchers)
    private data class FixedClock(val value: Instant) : AppClock {
        override fun nowInstant() = value
        override fun today(zoneId: ZoneId) = LocalDate.ofInstant(value, zoneId)
    }
    private object RandomIds : IdGenerator { override fun newId() = UUID.randomUUID() }
    private object TestDispatchers : AppDispatchers {
        override val default: CoroutineDispatcher = Dispatchers.Unconfined
        override val io: CoroutineDispatcher = Dispatchers.Unconfined
        override val main: CoroutineDispatcher = Dispatchers.Unconfined
    }
    companion object { private const val DB = "campaign-repository-test.db" }
}
