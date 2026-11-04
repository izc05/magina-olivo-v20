package com.isivoltpro.maginaolivo.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.core.dispatchers.DefaultAppDispatchers
import com.isivoltpro.maginaolivo.core.id.UuidGenerator
import com.isivoltpro.maginaolivo.core.time.SystemAppClock
import com.isivoltpro.maginaolivo.data.local.entity.ActivityEntity
import com.isivoltpro.maginaolivo.data.local.entity.ActivityParcelTargetEntity
import com.isivoltpro.maginaolivo.data.local.entity.DeliveryEntity
import com.isivoltpro.maginaolivo.data.local.entity.DeliveryParcelEntity
import com.isivoltpro.maginaolivo.data.local.entity.ExpenseEntity
import com.isivoltpro.maginaolivo.data.local.entity.HarvestEntity
import com.isivoltpro.maginaolivo.data.local.entity.HarvestParcelEntity
import com.isivoltpro.maginaolivo.data.local.entity.LocalMetadata
import com.isivoltpro.maginaolivo.data.local.entity.SyncOutboxEntity
import com.isivoltpro.maginaolivo.data.local.entity.WorkspaceAuthorizationCacheEntity
import com.isivoltpro.maginaolivo.data.local.entity.WorkspaceSelectionEntity
import com.isivoltpro.maginaolivo.data.local.model.ActivityStatus
import com.isivoltpro.maginaolivo.data.local.model.CampaignStatus
import com.isivoltpro.maginaolivo.data.local.model.OutboxOperation
import com.isivoltpro.maginaolivo.data.local.model.OutboxStatus
import com.isivoltpro.maginaolivo.data.local.model.RecordStatus
import com.isivoltpro.maginaolivo.data.local.model.SyncEntityType
import com.isivoltpro.maginaolivo.data.local.model.SyncStatus
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstCampaignRepository
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstParcelRepository
import com.isivoltpro.maginaolivo.data.repository.parseCampaignCasEnvelope
import com.isivoltpro.maginaolivo.data.repository.sync.CampaignCasCoordinator
import com.isivoltpro.maginaolivo.data.repository.sync.CampaignCasOutcome
import com.isivoltpro.maginaolivo.data.repository.sync.CampaignCasRpcAdapter
import com.isivoltpro.maginaolivo.data.repository.sync.DeliveryDispatchLease
import com.isivoltpro.maginaolivo.data.repository.sync.DeliveryRpcError
import com.isivoltpro.maginaolivo.data.repository.sync.DeliveryRpcFailure
import com.isivoltpro.maginaolivo.data.repository.sync.DeliveryRpcResponse
import com.isivoltpro.maginaolivo.data.repository.sync.DeliveryRpcTransport
import com.isivoltpro.maginaolivo.data.repository.sync.ParcelMutationKind
import com.isivoltpro.maginaolivo.data.repository.sync.ParcelMutationOutcome
import com.isivoltpro.maginaolivo.data.repository.sync.ParcelMutationSendCoordinator
import com.isivoltpro.maginaolivo.data.repository.sync.parcelMutationHash
import com.isivoltpro.maginaolivo.domain.parcel.NewParcel
import com.isivoltpro.maginaolivo.domain.workspace.WorkspaceRepository
import com.isivoltpro.maginaolivo.feature.account.AccountAuthApi
import com.isivoltpro.maginaolivo.feature.account.AccountRecord
import com.isivoltpro.maginaolivo.feature.account.AccountRepository
import com.isivoltpro.maginaolivo.feature.account.AuthFailure
import com.isivoltpro.maginaolivo.feature.account.AuthSession
import com.isivoltpro.maginaolivo.feature.account.MemoryAccountSessionStore
import com.isivoltpro.maginaolivo.feature.accountlink.FarmChoice
import com.isivoltpro.maginaolivo.feature.accountlink.FarmLinkConsent
import com.isivoltpro.maginaolivo.feature.accountlink.FarmLinkLedger
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * G2.6 2b causal order between a pending CAMPAIGN UPDATE (campaign CAS) and a PARCEL ARCHIVE, with the
 * app's own producers and a fake modelling #805 plus `update_own_campaign_v1`: the server refuses
 * ARCHIVE_BLOCKED_CAMPAIGN while a campaign that holds the parcel is running remotely.
 */
@RunWith(AndroidJUnit4::class)
class ParcelArchiveCausalOrderRoomTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "g26-parcel-archive-causal-room"
    private val issuer = "https://project.example"
    private val a = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
    private val workspace = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val north = UUID.fromString("22222222-2222-2222-2222-222222222221")
    private var now = Instant.now()
    private val store = MemoryAccountSessionStore()
    private val auth = FakeAuth()
    private val server = Server()
    private var ledger: FarmLinkLedger? = null
    private lateinit var db: MaginaOlivoDatabase
    private lateinit var account: AccountRepository
    private lateinit var lease: DeliveryDispatchLease

    @Before fun setup() = runBlocking {
        context.deleteDatabase(name)
        db = MaginaOlivoDatabase.create(context, name)
        lease = DeliveryDispatchLease.tryAcquire(File(context.noBackupFilesDir, "g26-parcel-archive-causal-room"))!!
        login()
        // WORKSPACE (#798) and FARM (#799) already confirmed: SYNCED, remote v1, bound CREATEs CONFIRMED.
        val sql = db.openHelper.writableDatabase
        sql.execSQL("INSERT INTO workspaces(id,name,owner_user_id,country_code,timezone,locale,currency,created_at,updated_at,version,sync_status,remote_version) VALUES ('$workspace','Olivar A','$a','ES','Europe/Madrid','es-ES','EUR',1000,1000,1,'SYNCED',1)")
        sql.execSQL("INSERT INTO sync_outbox(id,entity_type,entity_id,operation,payload_version,status,attempt_count,created_at,updated_at,account_id) VALUES ('${UUID.randomUUID()}','WORKSPACE','$workspace','CREATE',1,'CONFIRMED',0,1000,1000,'$a')")
        sql.execSQL("INSERT INTO farms(id,workspace_id,name,status,created_at,updated_at,version,sync_status,remote_version) VALUES ('$north','$workspace','Finca Norte','ACTIVE',1000,1000,1,'SYNCED',1)")
        sql.execSQL("INSERT INTO sync_outbox(id,entity_type,entity_id,operation,payload_version,status,attempt_count,created_at,updated_at,account_id,farm_id) VALUES ('${UUID.randomUUID()}','FARM','$north','CREATE',1,'CONFIRMED',0,1000,1000,'$a','$north')")
        server.farmOwners[north] = a
        server.workspaceOwners[workspace] = a
        db.workspaceSelectionDao().upsert(WorkspaceSelectionEntity(issuer, a.toString(), workspace))
        db.workspaceAuthorizationCacheDao().upsert(WorkspaceAuthorizationCacheEntity(issuer, a.toString(), contract()))
        ledger = FarmLinkLedger(listOf(FarmLinkConsent(UUID.randomUUID(), "$issuer|$a",
            listOf(FarmChoice(north, workspace, "Finca Norte", 1)), UUID.randomUUID(), "b".repeat(64), now)))
    }

    @After fun close() {
        if (::lease.isInitialized) lease.close()
        if (::db.isInitialized) db.close()
        context.deleteDatabase(name)
    }

    /** The reviewed counterexample: remote ACTIVE, local close pending, then local archive. */
    @Test fun pendingCloseOfARemotelyActiveCampaignIsConfirmedBeforeTheArchive() = runBlocking {
        val parcel = confirmedParcel("Parcela en campaña")
        val campaign = confirmedCampaign(CampaignStatus.ACTIVE, parcel)
        assertEquals(AppResult.Success(Unit), campaigns().closeToday(campaign))
        // After a confirmed CREATE the producer writes a CAS UPDATE, never a second CREATE.
        val close = openCampaign(campaign).single()
        assertEquals(OutboxOperation.UPDATE, close.operation)
        assertEquals(AppResult.Success(Unit), parcels().archive(parcel))
        val archive = openParcel(parcel).single()
        assertEquals(OutboxOperation.ARCHIVE, archive.operation)

        // R1 strict order: the ARCHIVE waits for the CAMPAIGN UPDATE...
        assertEquals(ParcelMutationOutcome.WAITING_DEPENDENCIES, sender().stage(archive.id))
        assertEquals("CAMPAIGN:PENDING=1", db.syncOutboxDao().findById(archive.id)?.lastErrorMessage)
        // ...and the CAS no longer waits for that unsent ARCHIVE: no cycle.
        assertEquals(CampaignCasOutcome.STAGED, cas().stage(close.id))
        assertEquals(CampaignCasOutcome.SYNCED, cas().dispatch(close.id, lease))
        assertEquals(CampaignStatus.CLOSED.name, server.campaigns.getValue(campaign).status)
        assertEquals(OutboxStatus.CONFIRMED, db.syncOutboxDao().findById(close.id)?.status)

        assertEquals(ParcelMutationOutcome.STAGED, sender().stage(archive.id))
        assertEquals(ParcelMutationOutcome.SYNCED, sender().send(archive.id, lease))
        assertEquals(RecordStatus.ARCHIVED, server.parcels.getValue(parcel).status)
        assertEquals(listOf("check_sync_operations", "update_own_campaign_v1", "check_sync_operations",
            "archive_own_parcel_v1", "check_sync_operations"), server.calls)
        assertTrue(server.refusals.isEmpty())
        // Archiving is logical: the snapshot (history) keeps the parcel.
        assertEquals(listOf(parcel), db.campaignDao().listSnapshots(campaign).map { it.parcelId })
    }

    @Test fun ordinaryUpdateOfARemotelyClosedCampaignThenArchiveHasNoCycle() = runBlocking {
        val parcel = confirmedParcel("Parcela 1")
        val campaign = confirmedCampaign(CampaignStatus.CLOSED, parcel)
        // A correction of the historical close boundary: reopen, then close with another end date.
        assertEquals(AppResult.Success(Unit), campaigns().reopen(campaign))
        assertEquals(AppResult.Success(Unit), campaigns().close(campaign, LocalDate.parse("2026-07-15")))
        val update = openCampaign(campaign).single()
        assertEquals(OutboxOperation.UPDATE, update.operation)
        assertEquals(AppResult.Success(Unit), parcels().archive(parcel))
        val archive = openParcel(parcel).single()

        assertEquals(ParcelMutationOutcome.WAITING_DEPENDENCIES, sender().stage(archive.id))
        assertEquals(CampaignCasOutcome.STAGED, cas().stage(update.id))
        assertEquals(CampaignCasOutcome.SYNCED, cas().dispatch(update.id, lease))
        assertEquals("2026-07-15", server.campaigns.getValue(campaign).endDate)
        assertEquals(ParcelMutationOutcome.STAGED, sender().stage(archive.id))
        assertEquals(ParcelMutationOutcome.SYNCED, sender().send(archive.id, lease))
        assertEquals(SyncStatus.SYNCED, db.parcelDao().findById(parcel)?.metadata?.syncStatus)
        assertTrue(server.refusals.isEmpty())
    }

    /** Only a never-sent ARCHIVE is accepted under the CAS; a staged one keeps the unchanged rule. */
    @Test fun aStagedArchiveStillHoldsTheCasUntilItIsConfirmed() = runBlocking {
        val parcel = confirmedParcel("Parcela 1")
        val campaign = confirmedCampaign(CampaignStatus.CLOSED, parcel)
        assertEquals(AppResult.Success(Unit), parcels().archive(parcel))
        val archive = openParcel(parcel).single()
        assertEquals(ParcelMutationOutcome.STAGED, sender().stage(archive.id))
        assertEquals(AppResult.Success(Unit), campaigns().reopen(campaign))
        assertEquals(AppResult.Success(Unit), campaigns().close(campaign, LocalDate.parse("2026-07-15")))
        val update = openCampaign(campaign).single()

        assertEquals(CampaignCasOutcome.REJECTED, cas().stage(update.id))
        val held = db.syncOutboxDao().findById(update.id)!!
        assertEquals(OutboxStatus.PENDING, held.status)
        assertNull(held.accountId)
        assertNull(held.payloadJson)
        // The staged ARCHIVE goes first (the server campaign is CLOSED), then the CAS.
        assertEquals(ParcelMutationOutcome.SYNCED, sender().send(archive.id, lease))
        assertEquals(CampaignCasOutcome.STAGED, cas().stage(update.id))
        assertEquals(CampaignCasOutcome.SYNCED, cas().dispatch(update.id, lease))
        assertTrue(server.refusals.isEmpty())
    }

    /**
     * A staged ARCHIVE meets a campaign that is ACTIVE on the server (e.g. reopened on another device
     * after staging): the refusal is kept, nothing is lost and nothing is retried. It needs an explicit
     * resolution; until then the campaign CAS over this parcel stays refused too.
     */
    @Test fun stagedArchiveRefusedByARemotelyActiveCampaignStaysBlockedWithoutRetries() = runBlocking {
        val parcel = confirmedParcel("Parcela 1")
        val campaign = confirmedCampaign(CampaignStatus.CLOSED, parcel)
        assertEquals(AppResult.Success(Unit), parcels().archive(parcel))
        val archive = openParcel(parcel).single()
        assertEquals(ParcelMutationOutcome.STAGED, sender().stage(archive.id))
        val staged = db.syncOutboxDao().findById(archive.id)!!
        server.campaigns.getValue(campaign).status = CampaignStatus.ACTIVE.name

        assertEquals(ParcelMutationOutcome.CONFLICT, sender().send(archive.id, lease))
        val blocked = db.syncOutboxDao().findById(archive.id)!!
        assertEquals(OutboxStatus.BLOCKED, blocked.status)
        assertEquals("ARCHIVE_BLOCKED_CAMPAIGN", blocked.lastErrorCode)
        assertEquals(staged.payloadJson, blocked.payloadJson)
        assertEquals(staged.accountId, blocked.accountId)
        assertEquals(staged.baseRemoteVersion, blocked.baseRemoteVersion)
        assertEquals(0, blocked.attemptCount)
        assertEquals(listOf("ARCHIVE_BLOCKED_CAMPAIGN"), server.refusals)
        assertEquals(RecordStatus.ACTIVE, server.parcels.getValue(parcel).status)
        // The local archive is kept and marked in conflict, never silently undone.
        assertEquals(RecordStatus.ARCHIVED, db.parcelDao().findById(parcel)?.status)
        assertEquals(SyncStatus.CONFLICT, db.parcelDao().findById(parcel)?.metadata?.syncStatus)

        // No automatic retry: neither send nor stage reaches the server again.
        val calls = server.calls.size
        repeat(3) {
            assertEquals(ParcelMutationOutcome.REJECTED, sender().send(archive.id, lease))
            assertEquals(ParcelMutationOutcome.REJECTED, sender().stage(archive.id))
        }
        assertEquals(calls, server.calls.size)
        assertEquals(blocked, db.syncOutboxDao().findById(archive.id))
        assertEquals(listOf(archive.id), openParcel(parcel).map { it.id })

        // A later campaign edit is not staged over the refused ARCHIVE: explicit resolution required.
        assertEquals(AppResult.Success(Unit), campaigns().reopen(campaign))
        assertEquals(AppResult.Success(Unit), campaigns().close(campaign, LocalDate.parse("2026-07-15")))
        val update = openCampaign(campaign).single()
        assertEquals(CampaignCasOutcome.REJECTED, cas().stage(update.id))
        assertNull(db.syncOutboxDao().findById(update.id)?.payloadJson)
        assertEquals(calls, server.calls.size)
    }

    @Test fun pendingDeliveryHoldsTheArchiveUntilItIsConfirmed() = runBlocking {
        val parcel = confirmedParcel("Parcela con Pesada")
        val campaign = confirmedCampaign(CampaignStatus.CLOSED, parcel)
        val delivery = UUID.randomUUID()
        val meta = LocalMetadata(now, now, syncStatus = SyncStatus.PENDING)
        db.deliveryDao().upsert(DeliveryEntity(id = delivery, workspaceId = workspace, farmId = north,
            campaignId = campaign, deliveryDate = LocalDate.parse("2026-02-01"), destinationName = "Cooperativa",
            netGrams = 1_000_000, source = "MANUAL", metadata = meta))
        db.deliveryDao().upsertParcels(listOf(DeliveryParcelEntity(id = UUID.randomUUID(), workspaceId = workspace,
            deliveryId = delivery, parcelId = parcel, parcelNameAtDelivery = "Parcela con Pesada",
            allocationMode = "FULL", metadata = meta)))
        val marker = SyncOutboxEntity(id = UUID.randomUUID(), entityType = SyncEntityType.DELIVERY, entityId = delivery,
            operation = OutboxOperation.CREATE, payloadVersion = 1, createdAt = now, updatedAt = now)
        db.syncOutboxDao().insert(marker)
        val deliveryBefore = db.deliveryDao().findById(delivery)
        assertEquals(AppResult.Success(Unit), parcels().archive(parcel))
        val archive = openParcel(parcel).single()

        for ((status, cause) in listOf(OutboxStatus.PENDING to "DELIVERY:PENDING=1", OutboxStatus.FAILED to "DELIVERY:FAILED=1")) {
            db.openHelper.writableDatabase.execSQL("UPDATE sync_outbox SET status='${status.name}' WHERE id='${marker.id}'")
            assertEquals(ParcelMutationOutcome.WAITING_DEPENDENCIES, sender().stage(archive.id))
            assertEquals(cause, db.syncOutboxDao().findById(archive.id)?.lastErrorMessage)
            assertNull(db.syncOutboxDao().findById(archive.id)?.payloadJson)
        }
        assertTrue(server.calls.isEmpty())
        // DeliveryDispatchWorker confirms the Pesada with its receipt: the ARCHIVE continues.
        db.openHelper.writableDatabase.execSQL("UPDATE sync_outbox SET status='CONFIRMED' WHERE id='${marker.id}'")
        assertEquals(ParcelMutationOutcome.STAGED, sender().stage(archive.id))
        assertEquals(ParcelMutationOutcome.SYNCED, sender().send(archive.id, lease))
        assertEquals(deliveryBefore, db.deliveryDao().findById(delivery))
    }

    @Test fun pendingExpenseKeepsTheArchiveBlockedWithoutTouchingData() = runBlocking {
        val parcel = confirmedParcel("Parcela con gasto")
        val expense = UUID.randomUUID()
        db.expenseDao().upsert(ExpenseEntity(expense, workspace, farmId = north, parcelId = parcel,
            expenseDate = LocalDate.parse("2026-02-01"), concept = "Poda", category = "LABOUR", amountMinor = 12_000,
            currency = "EUR", metadata = LocalMetadata(now, now)))
        val marker = SyncOutboxEntity(id = UUID.randomUUID(), entityType = SyncEntityType.EXPENSE, entityId = expense,
            operation = OutboxOperation.CREATE, payloadVersion = 1, createdAt = now, updatedAt = now)
        db.syncOutboxDao().insert(marker)
        val expenseBefore = db.expenseDao().findById(expense)
        val markerBefore = db.syncOutboxDao().findById(marker.id)
        assertEquals(AppResult.Success(Unit), parcels().archive(parcel))
        val archive = openParcel(parcel).single()

        repeat(3) {
            assertEquals(ParcelMutationOutcome.WAITING_DEPENDENCIES, sender().stage(archive.id))
            val waiting = db.syncOutboxDao().findById(archive.id)!!
            assertEquals(OutboxStatus.PENDING, waiting.status)
            assertEquals(0, waiting.attemptCount)
            assertNull(waiting.accountId)
            assertNull(waiting.payloadJson)
            assertEquals("EXPENSE:PENDING=1", waiting.lastErrorMessage)
        }
        assertEquals(expenseBefore, db.expenseDao().findById(expense))
        assertEquals(markerBefore, db.syncOutboxDao().findById(marker.id))
        assertEquals(RecordStatus.ACTIVE, server.parcels.getValue(parcel).status)
        assertTrue(server.calls.isEmpty())
    }

    @Test fun pendingHarvestAndActivityAreKeptButDoNotHoldTheArchive() = runBlocking {
        val parcel = confirmedParcel("Parcela con cosecha")
        val meta = LocalMetadata(now, now, syncStatus = SyncStatus.PENDING)
        val harvest = UUID.randomUUID()
        db.harvestDao().upsert(HarvestEntity(id = harvest, workspaceId = workspace, farmId = north,
            harvestDate = LocalDate.parse("2026-01-10"), weightGrams = 500_000, metadata = meta))
        db.harvestDao().upsertParcels(listOf(HarvestParcelEntity(id = UUID.randomUUID(), workspaceId = workspace,
            harvestId = harvest, parcelId = parcel, parcelNameAtHarvest = "Parcela con cosecha",
            allocationMode = "FULL", metadata = meta)))
        val activity = UUID.randomUUID()
        db.activityDao().upsert(ActivityEntity(id = activity, workspaceId = workspace, farmId = north,
            activityDate = LocalDate.parse("2026-01-12"), type = "PRUNING", status = ActivityStatus.COMPLETED,
            description = "Poda hecha", metadata = meta))
        db.activityDao().upsertTargets(listOf(ActivityParcelTargetEntity(id = UUID.randomUUID(), workspaceId = workspace,
            activityId = activity, parcelId = parcel, parcelNameAtTarget = "Parcela con cosecha", metadata = meta)))
        val markers = listOf(SyncEntityType.HARVEST to harvest, SyncEntityType.ACTIVITY to activity).map { (type, id) ->
            SyncOutboxEntity(id = UUID.randomUUID(), entityType = type, entityId = id, operation = OutboxOperation.CREATE,
                payloadVersion = 1, createdAt = now, updatedAt = now).also { db.syncOutboxDao().insert(it) }
        }
        // Read back: Room stores instants with millisecond precision.
        val stored = markers.map { db.syncOutboxDao().findById(it.id) }
        assertEquals(AppResult.Success(Unit), parcels().archive(parcel))
        val archive = openParcel(parcel).single()

        assertTrue(sender().archiveDependencies(parcel).isEmpty())
        assertEquals(ParcelMutationOutcome.STAGED, sender().stage(archive.id))
        assertEquals(ParcelMutationOutcome.SYNCED, sender().send(archive.id, lease))
        // Not synchronized in G2.6: neither discarded nor confirmed, byte for byte as they were.
        assertEquals(stored, markers.map { db.syncOutboxDao().findById(it.id) })
        assertTrue(stored.all { it?.status == OutboxStatus.PENDING && it.accountId == null && it.payloadJson == null })
        assertEquals(parcel, db.harvestDao().listParcels(harvest).single().parcelId)
    }

    @Test fun logoutWorkspaceSwitchNetworkFailureAndUncertainReplyChangeNoMarkerOrAuthorization() = runBlocking {
        val parcel = confirmedParcel("Parcela en campaña")
        val campaign = confirmedCampaign(CampaignStatus.ACTIVE, parcel)
        assertEquals(AppResult.Success(Unit), campaigns().closeToday(campaign))
        val close = openCampaign(campaign).single()
        assertEquals(AppResult.Success(Unit), parcels().archive(parcel))
        val archive = openParcel(parcel).single()
        val grantBefore = db.workspaceAuthorizationCacheDao().find(issuer, a.toString())
        suspend fun outbox() = listOf(db.syncOutboxDao().findById(close.id), db.syncOutboxDao().findById(archive.id))
        val untouched = outbox()

        account.logout(false)
        assertEquals(CampaignCasOutcome.NOT_AUTHORIZED, cas().stage(close.id))
        assertEquals(ParcelMutationOutcome.NOT_AUTHORIZED, sender().stage(archive.id))
        assertEquals(untouched, outbox())
        login()
        db.workspaceSelectionDao().upsert(WorkspaceSelectionEntity(issuer, a.toString(), UUID.randomUUID()))
        assertEquals(CampaignCasOutcome.NOT_AUTHORIZED, cas().stage(close.id))
        assertEquals(ParcelMutationOutcome.NOT_AUTHORIZED, sender().stage(archive.id))
        assertEquals(untouched, outbox())
        db.workspaceSelectionDao().upsert(WorkspaceSelectionEntity(issuer, a.toString(), workspace))
        auth.offline = true
        assertEquals(CampaignCasOutcome.NOT_AUTHORIZED, cas().stage(close.id))
        assertEquals(ParcelMutationOutcome.OFFLINE, sender().stage(archive.id))
        assertEquals(untouched, outbox())
        auth.offline = false
        assertTrue(server.calls.isEmpty())

        assertEquals(CampaignCasOutcome.STAGED, cas().stage(close.id))
        val staged = db.syncOutboxDao().findById(close.id)!!.payloadJson
        // Network failure before the server commits, then a reply lost after it commits.
        for (mode in listOf(Mode.OFFLINE_NO_COMMIT, Mode.LOST_AFTER_APPLY)) {
            server.mode = mode
            assertEquals(mode.name, CampaignCasOutcome.OUTCOME_UNKNOWN, cas().dispatch(close.id, lease))
            assertEquals(OutboxStatus.FAILED, db.syncOutboxDao().findById(close.id)?.status)
            assertEquals(staged, db.syncOutboxDao().findById(close.id)?.payloadJson)
            // An unknown outcome is never taken as confirmed: the ARCHIVE keeps waiting, unbound.
            assertEquals(ParcelMutationOutcome.WAITING_DEPENDENCIES, sender().stage(archive.id))
            assertEquals("CAMPAIGN:FAILED=1", db.syncOutboxDao().findById(archive.id)?.lastErrorMessage)
            assertNull(db.syncOutboxDao().findById(archive.id)?.payloadJson)
            assertEquals(0, db.syncOutboxDao().findById(archive.id)?.attemptCount)
        }
        assertEquals(CampaignStatus.CLOSED.name, server.campaigns.getValue(campaign).status)
        assertEquals(RecordStatus.ACTIVE, server.parcels.getValue(parcel).status)
        server.mode = Mode.NORMAL
        // The receipt is recovered before any new send: the close is applied once.
        assertEquals(CampaignCasOutcome.SYNCED, cas().dispatch(close.id, lease))
        assertEquals(2, server.calls.count { it == "update_own_campaign_v1" })
        assertEquals(1, server.campaignApplied)
        assertEquals(ParcelMutationOutcome.STAGED, sender().stage(archive.id))
        assertEquals(ParcelMutationOutcome.SYNCED, sender().send(archive.id, lease))
        assertEquals(grantBefore, db.workspaceAuthorizationCacheDao().find(issuer, a.toString()))
        assertTrue(server.refusals.isEmpty())
    }

    private fun sender() = ParcelMutationSendCoordinator(db, account, issuer, server, { ledger }, { now })
    private fun cas() = CampaignCasCoordinator(db, account, issuer, CampaignCasRpcAdapter(server)) { now }
    private fun parcels() = OfflineFirstParcelRepository(db, SystemAppClock(), UuidGenerator(), DefaultAppDispatchers(), workspaceRepository = FixtureWorkspace { db })
    private fun campaigns() = OfflineFirstCampaignRepository(db, SystemAppClock(), UuidGenerator(), DefaultAppDispatchers(),
        object : WorkspaceRepository { override suspend fun ensureLocalWorkspace(): AppResult<UUID> = AppResult.Success(workspace) })

    /** The app's producer, then the #800 CREATE confirmed: parcel and membership at remote v1 on both sides. */
    private suspend fun confirmedParcel(parcelName: String): UUID {
        val parcel = (parcels().create(NewParcel(north, parcelName, municipality = "Bedmar", province = "Jaén",
            managedAreaM2 = 1234.5)) as AppResult.Success).value
        val sql = db.openHelper.writableDatabase
        sql.execSQL("UPDATE sync_outbox SET status='CONFIRMED', account_id='$a', farm_id='$north' WHERE entity_type='PARCEL' AND entity_id='$parcel'")
        sql.execSQL("UPDATE parcels SET remote_version=1, sync_status='SYNCED' WHERE id='$parcel'")
        sql.execSQL("UPDATE farm_parcel_memberships SET remote_version=1, sync_status='SYNCED' WHERE parcel_id='$parcel'")
        val membership = db.parcelDao().listMemberships(parcel).single()
        server.parcels[parcel] = RemoteParcel(workspace, 1, RecordStatus.ACTIVE,
            mutableMapOf(membership.id to RemoteMembership(north, null, 1)))
        return parcel
    }

    /** A campaign already created remotely (CREATE confirmed, v1) with a synced snapshot of [parcel]. */
    private fun confirmedCampaign(status: CampaignStatus, parcel: UUID): UUID {
        val campaign = UUID.randomUUID()
        val end = if (status == CampaignStatus.CLOSED) "'2026-06-30'" else "NULL"
        val sql = db.openHelper.writableDatabase
        sql.execSQL("INSERT INTO campaigns(id,workspace_id,farm_id,name,start_date,end_date,status,created_at,updated_at,version,sync_status,remote_version) VALUES ('$campaign','$workspace','$north','2025/26','2025-10-01',$end,'${status.name}',1000,1000,1,'SYNCED',1)")
        sql.execSQL("INSERT INTO sync_outbox(id,entity_type,entity_id,operation,payload_version,status,attempt_count,created_at,updated_at,account_id) VALUES ('${UUID.randomUUID()}','CAMPAIGN','$campaign','CREATE',1,'CONFIRMED',1,1000,1000,'$a')")
        sql.execSQL("INSERT INTO campaign_parcels(id,workspace_id,campaign_id,parcel_id,farm_id_at_start,farm_name_at_start,parcel_name_at_start,created_at,updated_at,version,sync_status) VALUES ('${UUID.randomUUID()}','$workspace','$campaign','$parcel','$north','Finca Norte','Parcela',1000,1000,1,'SYNCED')")
        server.campaigns[campaign] = RemoteCampaign(workspace, 1, status.name, null, setOf(parcel))
        return campaign
    }

    private suspend fun openParcel(parcel: UUID) =
        db.syncOutboxDao().listForEntity(SyncEntityType.PARCEL, parcel).filter { it.status != OutboxStatus.CONFIRMED }
    private suspend fun openCampaign(campaign: UUID) =
        db.syncOutboxDao().listForEntity(SyncEntityType.CAMPAIGN, campaign).filter { it.status != OutboxStatus.CONFIRMED }

    private fun contract() = """{"contract_version":1,"issuer":"$issuer/auth/v1","subject":"$a","verified_at":"$now","token_expires_at":"${now.plusSeconds(3600)}","workspaces":[{"id":"$workspace","name":"Olivar A","role":"OWNER","permissions":["read","write","manage_members"],"grant_version":1,"authorization_expires_at":"${now.plusSeconds(900)}"}]}"""
    private fun login() {
        store.write(AccountRecord(session = AuthSession(a, "$a@example.test", "access", "refresh", now.plusSeconds(3600))))
        account = AccountRepository(auth, store) { now }
    }

    private enum class Mode { NORMAL, OFFLINE_NO_COMMIT, LOST_AFTER_APPLY }
    private data class RemoteMembership(val farm: UUID, var until: String?, var version: Long)
    private data class RemoteParcel(val workspace: UUID, var version: Long, var status: RecordStatus,
        val memberships: MutableMap<UUID, RemoteMembership>)
    private data class RemoteCampaign(val workspace: UUID, var version: Long, var status: String, var endDate: String?,
        val parcels: Set<UUID>)

    /**
     * #805 PARCEL RPCs, `update_own_campaign_v1` and `check_sync_operations`: owner-only, CAS on both
     * aggregates, exact idempotent receipts, and ARCHIVE_BLOCKED_CAMPAIGN while a campaign holding the
     * parcel is running remotely.
     */
    private inner class Server : DeliveryRpcTransport {
        var mode = Mode.NORMAL
        var campaignApplied = 0
        val calls = mutableListOf<String>()
        val refusals = mutableListOf<String>()
        val workspaceOwners = mutableMapOf<UUID, UUID>()
        val farmOwners = mutableMapOf<UUID, UUID>()
        val parcels = mutableMapOf<UUID, RemoteParcel>()
        val campaigns = mutableMapOf<UUID, RemoteCampaign>()
        private val receipts = mutableMapOf<Pair<UUID, UUID>, String>()
        private val parcelReplays = mutableMapOf<Pair<UUID, UUID>, Triple<String, Long, String>>()

        override suspend fun rpc(name: String, bodyJson: String, accessToken: String): DeliveryRpcResponse {
            calls += name
            val actor = (store.read().session ?: return DeliveryRpcResponse(401, "{}")).userId
            val body = Json.parseToJsonElement(bodyJson) as JsonObject
            if (name == "check_sync_operations") {
                val op = UUID.fromString((body.getValue("p_operation_ids") as JsonArray).single().jsonPrimitive.content)
                val rows = receipts[actor to op]?.let { "[$it]" } ?: "[]"
                return DeliveryRpcResponse(200, """{"success":true,"confirmed_operations":[],"confirmed_receipts":$rows}""")
            }
            if (name == "update_own_campaign_v1") return updateCampaign(actor, body)
            val kind = ParcelMutationKind.entries.single { it.rpc == name }
            val op = UUID.fromString(body.getValue("p_operation_id").jsonPrimitive.content)
            val id = UUID.fromString(body.getValue("p_parcel_id").jsonPrimitive.content)
            val expected = body.getValue("p_expected_version").jsonPrimitive.long
            val mutation = body.getValue(kind.argument) as JsonObject
            val parcel = parcels[id] ?: return DeliveryRpcResponse(403, """{"code":"42501"}""")
            if (workspaceOwners[parcel.workspace] != actor) return DeliveryRpcResponse(403, """{"code":"42501"}""")
            val hash = parcelMutationHash(kind, id, expected, mutation)
            parcelReplays[actor to op]?.let { (savedHash, version, touched) ->
                if (savedHash != hash) return conflict("RECEIPT_CONFLICT")
                return ack(op, id, kind, version, touched, true)
            }
            if (parcel.version != expected) return conflict("PARCEL_VERSION_CONFLICT")
            if (kind != ParcelMutationKind.ARCHIVE) return conflict("NOT_MODELLED")
            if (campaigns.values.any { id in it.parcels && it.status in setOf("ACTIVE", "HARVEST") }) {
                refusals += "ARCHIVE_BLOCKED_CAMPAIGN"
                return conflict("ARCHIVE_BLOCKED_CAMPAIGN")
            }
            val closing = UUID.fromString(mutation.getValue("closeMembershipId").jsonPrimitive.content)
            val row = parcel.memberships[closing]?.takeIf { it.until == null && parcel.status == RecordStatus.ACTIVE }
                ?: return conflict("MEMBERSHIP_NOT_CURRENT")
            if (farmOwners[row.farm] != actor) return DeliveryRpcResponse(403, """{"code":"42501"}""")
            row.until = mutation.getValue("archivedAt").jsonPrimitive.content; row.version += 1
            parcel.status = RecordStatus.ARCHIVED
            parcel.version += 1
            val touched = """[{"id":"$closing","version":${row.version}}]"""
            parcelReplays[actor to op] = Triple(hash, parcel.version, touched)
            receipts[actor to op] = """{"operation_id":"$op","entity_id":"$id","entity_type":"PARCEL_${kind.name}","remote_version":${parcel.version},"payload_version":1,"payload_hash":"$hash"}"""
            return ack(op, id, kind, parcel.version, touched, false)
        }

        /** `update_own_campaign_v1` does not look at parcel liveness (20261029104720_g26_campaign_cas.sql). */
        private suspend fun updateCampaign(actor: UUID, body: JsonObject): DeliveryRpcResponse {
            if (mode == Mode.OFFLINE_NO_COMMIT) throw DeliveryRpcFailure(DeliveryRpcError.NETWORK)
            val op = UUID.fromString(body.getValue("p_operation_id").jsonPrimitive.content)
            val expected = body.getValue("p_expected_version").jsonPrimitive.long
            val row = body.getValue("p_campaign") as JsonObject
            val id = UUID.fromString(row.getValue("id").jsonPrimitive.content)
            val campaign = campaigns[id] ?: return DeliveryRpcResponse(403, """{"code":"42501"}""")
            if (workspaceOwners[campaign.workspace] != actor) return DeliveryRpcResponse(403, """{"code":"42501"}""")
            if (receipts[actor to op] == null) {
                if (campaign.version != expected) return DeliveryRpcResponse(409, """{"code":"23505"}""")
                campaign.status = row.getValue("status").jsonPrimitive.content
                campaign.endDate = row["endDate"]?.jsonPrimitive?.content?.takeIf { it != "null" }
                campaign.version += 1
                campaignApplied += 1
                // The fake reuses the client's canonical hash; the real hash is covered by the SQL contract.
                val hash = parseCampaignCasEnvelope(db.syncOutboxDao().findById(op)!!.payloadJson!!)!!.hash
                receipts[actor to op] = """{"operation_id":"$op","entity_id":"$id","entity_type":"CAMPAIGN","payload_hash":"$hash","payload_version":1,"remote_version":${campaign.version}}"""
            }
            if (mode == Mode.LOST_AFTER_APPLY) throw DeliveryRpcFailure(DeliveryRpcError.NETWORK)
            return DeliveryRpcResponse(200, """{"operation_id":"$op","entity_id":"$id","remote_version":${campaign.version},"payload_version":1,"status":"APPLIED"}""")
        }

        private fun conflict(reason: String) =
            DeliveryRpcResponse(409, """{"code":"23505","message":"parcel conflict: $reason"}""")
        private fun ack(op: UUID, id: UUID, kind: ParcelMutationKind, version: Long, touched: String, replay: Boolean) =
            DeliveryRpcResponse(200, """{"success":true,"operation_id":"$op","entity_id":"$id","kind":"${kind.name}","status":"APPLIED","remote_version":$version,"payload_version":1,"memberships":$touched,"idempotent":$replay}""")
    }

    private inner class FakeAuth : AccountAuthApi {
        var offline = false
        override val redirectUri = "app://callback"
        override suspend fun signIn(email: String, password: String): AuthSession = error("no login")
        override suspend fun signUp(email: String, password: String, challenge: String): AuthSession? = error("no signup")
        override suspend fun recover(email: String, challenge: String) = error("no recovery")
        override suspend fun exchange(code: String, verifier: String): AuthSession = error("no exchange")
        override suspend fun refresh(session: AuthSession): AuthSession = error("no refresh")
        override suspend fun user(session: AuthSession): Pair<UUID, String> {
            if (offline) throw AuthFailure(AuthFailure.Kind.NETWORK)
            return session.userId to session.email
        }
        override suspend fun changePassword(session: AuthSession, password: String) = error("no password")
        override suspend fun signOut(session: AuthSession) = Unit
    }
}
