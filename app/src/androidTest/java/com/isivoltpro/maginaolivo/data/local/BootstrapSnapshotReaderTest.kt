package com.isivoltpro.maginaolivo.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.room.withTransaction
import com.isivoltpro.maginaolivo.data.local.entity.*
import com.isivoltpro.maginaolivo.data.local.model.CampaignStatus
import com.isivoltpro.maginaolivo.data.repository.sync.RoomBootstrapSnapshotReader
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BootstrapSnapshotReaderTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dbName = "bootstrap-snapshot-${UUID.randomUUID()}.db"
    private lateinit var db: MaginaOlivoDatabase
    private val workspace = UUID.randomUUID()
    private val farm = UUID.randomUUID()
    private val oldFarm = UUID.randomUUID()
    private val parcel = UUID.randomUUID()
    private val campaign = UUID.randomUUID()
    private val snapshot = UUID.randomUUID()
    private val now = Instant.parse("2026-10-23T10:00:00.123456789Z")
    private val meta = LocalMetadata(now, now)
    private val currentMembership = UUID.randomUUID()
    private val historicalMembership = UUID.randomUUID()

    @Before fun before() = runBlocking {
        db = MaginaOlivoDatabase.create(context, dbName)
        db.workspaceDao().upsert(WorkspaceEntity(workspace, "Local", UUID.randomUUID(), "ES", "Europe/Madrid", "es-ES", "EUR", meta))
        db.farmDao().upsert(FarmEntity(farm, workspace, "Nombre actual finca", metadata = meta))
        db.farmDao().upsert(FarmEntity(oldFarm, workspace, "Finca anterior", metadata = meta))
        db.parcelDao().upsert(ParcelEntity(parcel, workspace, "Nombre actual parcela", source = "MANUAL",
            sourceImportedAt = now, managedAreaM2 = 1234.5678, irrigationSystem = "DRIP",
            irrigationNetwork = "Comunidad", irrigationSector = "4", irrigationDays = "Lunes", metadata = meta))
        db.parcelDao().upsertMembership(FarmParcelMembershipEntity(historicalMembership, workspace, oldFarm,
            parcel, now.minusSeconds(1000), now.minusSeconds(1), meta.copy(deletedAt = now)))
        db.parcelDao().upsertMembership(FarmParcelMembershipEntity(currentMembership, workspace, farm, parcel, now, metadata = meta))
        db.campaignDao().upsert(CampaignEntity(campaign, workspace, farm, "2026/27", LocalDate.parse("2026-10-01"),
            status = CampaignStatus.ACTIVE, metadata = meta))
        db.campaignDao().upsertSnapshots(listOf(CampaignParcelSnapshotEntity(snapshot, workspace, campaign, parcel,
            farm, "Finca al inicio", "Parcela al inicio", 999.25, "catastro histórico", "geometría histórica", meta)))
    }

    @After fun after() { if (::db.isInitialized) db.close(); context.deleteDatabase(dbName) }

    private suspend fun capture(consents: Set<UUID> = setOf(farm, oldFarm), extra: Set<UUID> = emptySet()) =
        RoomBootstrapSnapshotReader(db).capture(workspace, farm, campaign, extra, consents)

    private suspend fun rejected(block: suspend () -> Unit) {
        try { block(); fail("Expected closed preparation") } catch (_: IllegalArgumentException) { }
    }

    @Test fun preservesFullParcelHistoryAndHistoricalCampaignValuesWithoutWrites() = runBlocking {
        val before = db.parcelDao().findById(parcel)
        val value = capture()
        assertEquals(setOf(farm, oldFarm), value.farms.map { it.id }.toSet())
        assertEquals(2, value.parcels.single().memberships.size)
        assertNotNull(value.parcels.single().memberships.single { it.id == historicalMembership }.metadata.deletedAt)
        assertEquals(Instant.ofEpochMilli(now.toEpochMilli()), value.parcels.single().parcel.sourceImportedAt)
        assertEquals("DRIP", value.parcels.single().parcel.irrigationSystem)
        assertEquals("Finca al inicio", value.campaignParcels.single().farmNameAtStart)
        assertEquals("Parcela al inicio", value.campaignParcels.single().parcelNameAtStart)
        assertEquals(999.25, value.campaignParcels.single().managedAreaM2AtStart!!, 0.0)
        assertEquals(before, db.parcelDao().findById(parcel))
        assertEquals(1L, db.farmDao().findById(farm)!!.metadata.version)
    }

    @Test fun endedAndDeletedMembershipStillRequiresConsentForReferencedFarm() = runBlocking {
        rejected { capture(setOf(farm)) }
        assertEquals(2, capture().parcels.single().memberships.size)
    }

    @Test fun foreignWorkspaceAndMissingAdditionalParcelFailWithoutPartialSnapshot() = runBlocking {
        rejected { RoomBootstrapSnapshotReader(db).capture(UUID.randomUUID(), farm, campaign, emptySet(), setOf(farm, oldFarm)) }
        rejected { capture(extra = setOf(UUID.randomUUID())) }
        assertEquals(1, capture().parcels.size)
    }

    @Test fun deletedParentOrParcelIsNotSilentlySkipped() = runBlocking {
        val original = db.farmDao().findById(oldFarm)!!
        db.farmDao().upsert(original.copy(metadata = meta.copy(deletedAt = now)))
        rejected { capture() }
        db.farmDao().upsert(original)
        db.parcelDao().upsert(db.parcelDao().findById(parcel)!!.copy(metadata = meta.copy(deletedAt = now)))
        rejected { capture() }
    }

    @Test fun deletedCampaignSnapshotIsRejectedInsteadOfLosingItsHistory() = runBlocking {
        val original = db.campaignDao().listSnapshots(campaign).single()
        db.campaignDao().upsertSnapshots(listOf(original.copy(metadata = meta.copy(deletedAt = now))))
        rejected { capture() }
    }

    @Test fun capturedValuesStayStableAfterLaterFarmerEdit() = runBlocking {
        val captured = capture()
        val original = db.parcelDao().findById(parcel)!!
        db.parcelDao().upsert(original.copy(displayName = "Edición posterior", metadata = meta.copy(version = 2)))
        assertEquals("Nombre actual parcela", captured.parcels.single().parcel.displayName)
        assertEquals(1L, captured.parcels.single().parcel.metadata.version)
        assertEquals("Edición posterior", capture().parcels.single().parcel.displayName)
    }

    @Test fun preparationCampaignCanHaveNoParcelsButActiveCampaignCannot() = runBlocking {
        db.campaignDao().deleteSnapshots(campaign)
        rejected { capture() }
        val original = db.campaignDao().findById(campaign)!!
        db.campaignDao().upsert(original.copy(status = CampaignStatus.PREPARATION))
        val value = capture(setOf(farm))
        assertTrue(value.parcels.isEmpty())
        assertEquals(listOf(farm), value.farms.map { it.id })
    }

    @Test fun concurrentEditsCannotMixRowsFromTwoTransactions() = runBlocking {
        val originalFarm = db.farmDao().findById(farm)!!
        val originalParcel = db.parcelDao().findById(parcel)!!
        db.withTransaction {
            db.farmDao().upsert(originalFarm.copy(name = "0"))
            db.parcelDao().upsert(originalParcel.copy(displayName = "0"))
        }
        val writer = async(Dispatchers.Default) {
            repeat(30) { index ->
                db.withTransaction {
                    db.farmDao().upsert(originalFarm.copy(name = (index + 1).toString()))
                    yield()
                    db.parcelDao().upsert(originalParcel.copy(displayName = (index + 1).toString()))
                }
                yield()
            }
        }
        repeat(30) {
            val captured = capture()
            assertEquals(captured.farms.single { it.id == farm }.name, captured.parcels.single().parcel.displayName)
            yield()
        }
        writer.await()
    }
}
