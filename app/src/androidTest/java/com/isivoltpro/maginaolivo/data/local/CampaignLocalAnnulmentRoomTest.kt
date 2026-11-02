package com.isivoltpro.maginaolivo.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.isivoltpro.maginaolivo.core.common.AppError
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.core.dispatchers.DefaultAppDispatchers
import com.isivoltpro.maginaolivo.core.id.UuidGenerator
import com.isivoltpro.maginaolivo.core.time.SystemAppClock
import com.isivoltpro.maginaolivo.data.local.entity.DeliveryEntity
import com.isivoltpro.maginaolivo.data.local.entity.DeliveryParcelEntity
import com.isivoltpro.maginaolivo.data.local.entity.ExpenseEntity
import com.isivoltpro.maginaolivo.data.local.entity.LocalMetadata
import com.isivoltpro.maginaolivo.data.local.entity.SyncOutboxEntity
import com.isivoltpro.maginaolivo.data.local.model.OutboxOperation
import com.isivoltpro.maginaolivo.data.local.model.OutboxStatus
import com.isivoltpro.maginaolivo.data.local.model.SyncEntityType
import com.isivoltpro.maginaolivo.data.local.model.SyncStatus
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstCampaignRepository
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstParcelRepository
import com.isivoltpro.maginaolivo.domain.campaign.CampaignPreparationChanges
import com.isivoltpro.maginaolivo.domain.campaign.NewCampaign
import com.isivoltpro.maginaolivo.domain.workspace.WorkspaceRepository
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * G2.6 2c-4 (C14, #815 order): local annulment of a PREPARATION campaign whose CAMPAIGN CREATE provably
 * never left the device, and the enumerated R1 exclusion. Every operation that might have reached the
 * server (prepared, bound, attempted, in flight, unknown, confirmed, or with other markers) keeps the
 * previous path, byte for byte.
 */
@RunWith(AndroidJUnit4::class)
class CampaignLocalAnnulmentRoomTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "g26-campaign-annulment-2c4"
    private val workspace = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val otherWorkspace = UUID.fromString("19999999-9999-9999-9999-999999999999")
    private val farm = UUID.fromString("22222222-2222-2222-2222-222222222221")
    private val parcel = UUID.fromString("33333333-3333-3333-3333-333333333331")
    private val start = LocalDate.parse("2026-10-01")
    private var active: AppResult<UUID> = AppResult.Success(workspace)
    private lateinit var db: MaginaOlivoDatabase

    @Before fun setup() {
        context.deleteDatabase(name)
        db = MaginaOlivoDatabase.create(context, name)
        sql("INSERT INTO workspaces(id,name,owner_user_id,country_code,timezone,locale,currency,created_at,updated_at,version,sync_status,remote_version) VALUES ('$workspace','Olivar','${UUID.randomUUID()}','ES','Europe/Madrid','es-ES','EUR',1000,1000,1,'SYNCED',1)")
        sql("INSERT INTO farms(id,workspace_id,name,status,created_at,updated_at,version,sync_status,remote_version) VALUES ('$farm','$workspace','Finca','ACTIVE',1000,1000,1,'SYNCED',1)")
        sql("INSERT INTO parcels(id,workspace_id,display_name,source,status,created_at,updated_at,version,sync_status,remote_version) VALUES ('$parcel','$workspace','Parcela','MANUAL','ACTIVE',1000,1000,1,'SYNCED',1)")
        sql("INSERT INTO farm_parcel_memberships(id,workspace_id,farm_id,parcel_id,valid_from,created_at,updated_at,version,sync_status,remote_version) VALUES ('${UUID.randomUUID()}','$workspace','$farm','$parcel',1000,1000,1000,1,'SYNCED',1)")
    }

    @After fun close() {
        if (::db.isInitialized) db.close()
        context.deleteDatabase(name)
    }

    @Test fun c14ANeverSentPreparationIsAnnulledLocallyWithoutDeleteAndFreesTheParcelArchive() = runBlocking {
        val campaign = created("2026/27")
        assertTrue(campaigns().updatePreparation(campaign, CampaignPreparationChanges("2026/27 corregida", start, setOf(parcel))) is AppResult.Success)
        val create = markers(campaign).single()
        assertEquals(OutboxOperation.CREATE, create.operation)
        val snapshots = rows("SELECT * FROM campaign_parcels ORDER BY id")
        // Before: the live campaign and its pending CREATE hold the parcel archive (R1 and the local guard).
        assertEquals(listOf(create.id), db.syncOutboxDao().listParcelDependents(parcel).map { it.id })

        assertEquals(AppResult.Success(Unit), campaigns().archivePreparation(campaign))

        assertNotNull(db.campaignDao().findById(campaign)?.metadata?.deletedAt ?: rawDeletedAt(campaign))
        val annulled = markers(campaign).single()
        assertEquals(listOf(create.id, create.createdAt, OutboxOperation.CREATE, OutboxStatus.BLOCKED, "RESOLVED_LOCAL_ANNULLED", 0),
            listOf(annulled.id, annulled.createdAt, annulled.operation, annulled.status, annulled.lastErrorCode, annulled.attemptCount))
        assertNull(annulled.accountId); assertNull(annulled.payloadJson); assertNull(annulled.baseRemoteVersion)
        assertEquals(snapshots, rows("SELECT * FROM campaign_parcels ORDER BY id"))
        // After: nothing holds the parcel any more; the archive is allowed and queued normally.
        assertTrue(db.syncOutboxDao().listParcelDependents(parcel).isEmpty())
        assertEquals(AppResult.Success(Unit), parcels().archive(parcel))
        assertEquals(listOf(OutboxOperation.ARCHIVE), db.syncOutboxDao().listForEntity(SyncEntityType.PARCEL, parcel).map { it.operation })
    }

    @Test fun repeatingTheArchiveIsIdempotentAndWritesNothing() = runBlocking {
        val campaign = created("2026/27")
        assertEquals(AppResult.Success(Unit), campaigns().archivePreparation(campaign))
        val after = allRows()
        repeat(3) { assertEquals(AppResult.Success(Unit), campaigns().archivePreparation(campaign)) }
        assertEquals(after, allRows())
    }

    @Test fun anythingThatMightHaveReachedTheServerIsNeverAnnulled() = runBlocking {
        val cases = listOf(
            "prepared (payload)" to "payload_json='{}'",
            "bound to an account" to "account_id='${UUID.randomUUID()}'",
            "attempted" to "attempt_count=1",
            "outcome unknown" to "status='FAILED', attempt_count=1, account_id='x', payload_json='{}', last_error_code='OUTCOME_UNKNOWN'",
            "in flight" to "status='PROCESSING', account_id='x', payload_json='{}'",
            "with a base version" to "base_remote_version=1",
            "confirmed" to "status='CONFIRMED', account_id='x'",
        )
        for ((label, change) in cases) {
            val campaign = created(label)
            val create = markers(campaign).single()
            sql("UPDATE sync_outbox SET $change WHERE id='${create.id}'")
            val createRow = rows("SELECT * FROM sync_outbox WHERE id='${create.id}'")
            val snapshots = rows("SELECT * FROM campaign_parcels WHERE campaign_id='$campaign'")
            assertEquals(label, AppResult.Success(Unit), campaigns().archivePreparation(campaign))
            // The CREATE stays byte for byte; the previous path queues its DELETE; snapshots stay.
            assertEquals(label, createRow, rows("SELECT * FROM sync_outbox WHERE id='${create.id}'"))
            assertEquals(label, listOf(OutboxOperation.CREATE, OutboxOperation.DELETE), markers(campaign).map { it.operation })
            assertEquals(label, snapshots, rows("SELECT * FROM campaign_parcels WHERE campaign_id='$campaign'"))
            val held = db.syncOutboxDao().listParcelDependents(parcel).filter { it.entityId == campaign }.map { it.operation }
            assertEquals(label, if (label == "confirmed") listOf(OutboxOperation.DELETE) else listOf(OutboxOperation.CREATE, OutboxOperation.DELETE), held)
        }
    }

    @Test fun anotherMarkerOfTheCampaignMeansNoProofAndNoAnnulment() = runBlocking {
        val campaign = created("Con otro marcador")
        val extra = UUID.randomUUID()
        sql("INSERT INTO sync_outbox(id,entity_type,entity_id,operation,payload_version,status,attempt_count,created_at,updated_at) VALUES ('$extra','CAMPAIGN','$campaign','UPDATE',1,'PENDING',0,5000,5000)")
        val create = markers(campaign).first { it.operation == OutboxOperation.CREATE }
        val createRow = rows("SELECT * FROM sync_outbox WHERE id='${create.id}'")
        assertEquals(AppResult.Success(Unit), campaigns().archivePreparation(campaign))
        assertEquals(createRow, rows("SELECT * FROM sync_outbox WHERE id='${create.id}'"))
        assertTrue(markers(campaign).any { it.operation == OutboxOperation.DELETE })
    }

    @Test fun aFailingWriteRollsTheWholeArchiveBackAndARetryCompletesIt() = runBlocking {
        val campaign = created("2026/27")
        val before = allRows()
        sql("CREATE TRIGGER g26_fail_annulment BEFORE UPDATE ON sync_outbox WHEN NEW.last_error_code = 'RESOLVED_LOCAL_ANNULLED' BEGIN SELECT RAISE(ABORT, 'simulated'); END")
        val failed = campaigns().archivePreparation(campaign)
        assertTrue(failed is AppResult.Failure && failed.error is AppError.Storage)
        assertEquals(before, allRows()) // campaign not archived, CREATE untouched, no DELETE
        sql("DROP TRIGGER g26_fail_annulment")
        assertEquals(AppResult.Success(Unit), campaigns().archivePreparation(campaign))
        assertEquals("RESOLVED_LOCAL_ANNULLED", markers(campaign).single().lastErrorCode)
    }

    @Test fun anotherActiveWorkspaceOrNoSelectionChangesNothing() = runBlocking {
        val campaign = created("2026/27")
        val before = allRows()
        sql("INSERT INTO workspaces(id,name,owner_user_id,country_code,timezone,locale,currency,created_at,updated_at,version,sync_status) VALUES ('$otherWorkspace','Otro','${UUID.randomUUID()}','ES','Europe/Madrid','es-ES','EUR',1000,1000,1,'SYNCED')")
        val withOther = allRows()
        active = AppResult.Success(otherWorkspace)
        assertTrue(campaigns().archivePreparation(campaign) is AppResult.Failure)
        active = AppResult.Failure(AppError.Validation("workspaceId", "selection_required"))
        assertTrue(campaigns().archivePreparation(campaign) is AppResult.Failure)
        assertEquals(withOther, allRows())
        assertTrue(before.size < withOther.size)
    }

    /** R1 excludes exactly three resolved CAMPAIGN codes, only when BLOCKED; nothing else is relaxed. */
    @Test fun r1ExcludesOnlyTheEnumeratedResolvedCampaignBlockers() = runBlocking {
        val campaign = created("R1")
        sql("DELETE FROM sync_outbox")
        val excluded = listOf("BLOCKED" to "RESOLVED_LOCAL_ANNULLED", "BLOCKED" to "RESOLVED_CAS_CONFLICT", "BLOCKED" to "RESOLVED_NEEDS_REBASE")
        val held = listOf("BLOCKED" to "RESOLVED_REVIEW", "BLOCKED" to null, "BLOCKED" to "CAS_CONFLICT",
            "BLOCKED" to "RESOLVED_LOCAL_ANNULLED_X", "FAILED" to "RESOLVED_LOCAL_ANNULLED", "FAILED" to "OUTCOME_UNKNOWN",
            "PENDING" to null, "PROCESSING" to null, "PENDING" to "RESOLVED_CAS_CONFLICT")
        for ((status, code) in excluded + held) {
            val id = UUID.randomUUID()
            db.syncOutboxDao().insert(SyncOutboxEntity(id = id, entityType = SyncEntityType.CAMPAIGN, entityId = campaign,
                operation = OutboxOperation.UPDATE, payloadVersion = 1, status = OutboxStatus.valueOf(status),
                lastErrorCode = code, createdAt = Instant.now(), updatedAt = Instant.now()))
            val listed = db.syncOutboxDao().listParcelDependents(parcel).any { it.id == id }
            assertEquals("$status/$code", (status to code) in held, listed)
            sql("DELETE FROM sync_outbox WHERE id='$id'")
        }
        // DELIVERY and EXPENSE are never relaxed, whatever their code.
        val now = Instant.now()
        val meta = LocalMetadata(now, now, syncStatus = SyncStatus.PENDING)
        val delivery = UUID.randomUUID(); val expense = UUID.randomUUID()
        db.deliveryDao().upsert(DeliveryEntity(id = delivery, workspaceId = workspace, farmId = farm, campaignId = campaign,
            deliveryDate = LocalDate.parse("2026-10-10"), destinationName = "Cooperativa", netGrams = 1_000, source = "MANUAL", metadata = meta))
        db.deliveryDao().upsertParcels(listOf(DeliveryParcelEntity(id = UUID.randomUUID(), workspaceId = workspace, deliveryId = delivery,
            parcelId = parcel, parcelNameAtDelivery = "Parcela", allocationMode = "FULL", metadata = meta)))
        db.expenseDao().upsert(ExpenseEntity(expense, workspace, farmId = farm, parcelId = parcel, expenseDate = LocalDate.parse("2026-10-10"),
            concept = "Poda", category = "LABOUR", amountMinor = 1_000, currency = "EUR", metadata = LocalMetadata(now, now)))
        for ((type, id) in listOf(SyncEntityType.DELIVERY to delivery, SyncEntityType.EXPENSE to expense)) {
            db.syncOutboxDao().insert(SyncOutboxEntity(id = UUID.randomUUID(), entityType = type, entityId = id,
                operation = OutboxOperation.CREATE, payloadVersion = 1, status = OutboxStatus.BLOCKED,
                lastErrorCode = "RESOLVED_LOCAL_ANNULLED", createdAt = now, updatedAt = now))
        }
        assertEquals(setOf(SyncEntityType.DELIVERY, SyncEntityType.EXPENSE),
            db.syncOutboxDao().listParcelDependents(parcel).map { it.entityType }.toSet())
    }

    // ---- fixtures --------------------------------------------------------------------------------

    private fun campaigns() = OfflineFirstCampaignRepository(db, SystemAppClock(), UuidGenerator(), DefaultAppDispatchers(),
        object : WorkspaceRepository { override suspend fun ensureLocalWorkspace(): AppResult<UUID> = active })
    private fun parcels() = OfflineFirstParcelRepository(db, SystemAppClock(), UuidGenerator(), DefaultAppDispatchers())
    /** The app's producer: a PREPARATION campaign with a snapshot of [parcel] and one unbound CREATE. */
    private suspend fun created(campaignName: String): UUID =
        (campaigns().create(NewCampaign(farm, campaignName, start, setOf(parcel))) as AppResult.Success).value
    private suspend fun markers(campaign: UUID) = db.syncOutboxDao().listForEntity(SyncEntityType.CAMPAIGN, campaign)
    private fun rawDeletedAt(campaign: UUID) = rows("SELECT deleted_at FROM campaigns WHERE id='$campaign' AND deleted_at IS NOT NULL").singleOrNull()
    private fun sql(statement: String) = db.openHelper.writableDatabase.execSQL(statement)
    private fun rows(query: String): List<String> = db.openHelper.readableDatabase.query(query).use { cursor ->
        buildList { while (cursor.moveToNext()) add((0 until cursor.columnCount).joinToString("|") { cursor.getString(it) ?: "∅" }) }
    }
    private fun allRows() = listOf("workspaces", "farms", "parcels", "farm_parcel_memberships", "campaigns", "campaign_parcels", "sync_outbox")
        .flatMap { rows("SELECT * FROM $it ORDER BY id") }
}
