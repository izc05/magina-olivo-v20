package com.isivoltpro.maginaolivo.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.isivoltpro.maginaolivo.core.common.AppError
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.core.dispatchers.DefaultAppDispatchers
import com.isivoltpro.maginaolivo.core.id.UuidGenerator
import com.isivoltpro.maginaolivo.core.time.SystemAppClock
import com.isivoltpro.maginaolivo.data.local.entity.WorkspaceAuthorizationCacheEntity
import com.isivoltpro.maginaolivo.data.local.entity.WorkspaceSelectionEntity
import com.isivoltpro.maginaolivo.data.local.model.CampaignStatus
import com.isivoltpro.maginaolivo.data.local.model.OutboxOperation
import com.isivoltpro.maginaolivo.data.local.model.OutboxStatus
import com.isivoltpro.maginaolivo.data.local.model.SyncEntityType
import com.isivoltpro.maginaolivo.data.local.model.SyncStatus
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstCampaignRepository
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstParcelRepository
import com.isivoltpro.maginaolivo.data.repository.sync.CampaignCreateOutcome
import com.isivoltpro.maginaolivo.data.repository.sync.CampaignCreateSendCoordinator
import com.isivoltpro.maginaolivo.data.repository.sync.DeliveryDispatchLease
import com.isivoltpro.maginaolivo.data.repository.sync.DeliveryRpcError
import com.isivoltpro.maginaolivo.data.repository.sync.DeliveryRpcFailure
import com.isivoltpro.maginaolivo.data.repository.sync.DeliveryRpcResponse
import com.isivoltpro.maginaolivo.data.repository.sync.DeliveryRpcTransport
import com.isivoltpro.maginaolivo.data.repository.sync.campaignCreateHash
import com.isivoltpro.maginaolivo.data.repository.sync.parseCampaignCreateEnvelope
import com.isivoltpro.maginaolivo.domain.campaign.CampaignPreparationChanges
import com.isivoltpro.maginaolivo.domain.campaign.NewCampaign
import com.isivoltpro.maginaolivo.domain.parcel.NewParcel
import com.isivoltpro.maginaolivo.feature.account.AccountAuthApi
import com.isivoltpro.maginaolivo.feature.account.AccountRecord
import com.isivoltpro.maginaolivo.feature.account.AccountRepository
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Phase 2: the Android CAMPAIGN CREATE sender against a fake that models the reviewed `create_own_campaign` +
 * `check_sync_operations` semantics (owner only, owned farm, exact replays by operation id and payload hash), and the
 * snapshot contract: a confirmed campaign keeps its parcel snapshots through edits and activation.
 */
@RunWith(AndroidJUnit4::class)
class CampaignCreateSendRoomTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "phase2-campaign-create-room"
    private val issuer = "https://project.example"
    private val a = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
    private val workspace = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val farm = UUID.fromString("22222222-2222-2222-2222-222222222221")
    private val now = Instant.now()
    private val store = MemoryAccountSessionStore()
    private val server = Server()
    private var ledger: FarmLinkLedger? = null
    private lateinit var db: MaginaOlivoDatabase
    private lateinit var account: AccountRepository
    private lateinit var lease: DeliveryDispatchLease
    private lateinit var parcel: UUID
    private lateinit var parcelCreate: UUID

    @Before fun setup() = runBlocking {
        context.deleteDatabase(name)
        db = MaginaOlivoDatabase.create(context, name)
        lease = DeliveryDispatchLease.tryAcquire(File(context.noBackupFilesDir, "phase2-campaign-create-room"))!!
        store.write(AccountRecord(session = AuthSession(a, "a@example.test", "access", "refresh", now.plusSeconds(3600))))
        account = AccountRepository(Auth(), store) { now }
        // WORKSPACE (#798), FARM (#799) and PARCEL (#800) already confirmed: SYNCED, remote v1, CREATEs CONFIRMED.
        val sql = db.openHelper.writableDatabase
        sql.execSQL("INSERT INTO workspaces(id,name,owner_user_id,country_code,timezone,locale,currency,created_at,updated_at,version,sync_status,remote_version) VALUES ('$workspace','Olivar A','$a','ES','Europe/Madrid','es-ES','EUR',1000,1000,1,'SYNCED',1)")
        sql.execSQL("INSERT INTO sync_outbox(id,entity_type,entity_id,operation,payload_version,status,attempt_count,created_at,updated_at,account_id) VALUES ('${UUID.randomUUID()}','WORKSPACE','$workspace','CREATE',1,'CONFIRMED',1,1000,1000,'$a')")
        sql.execSQL("INSERT INTO farms(id,workspace_id,name,status,created_at,updated_at,version,sync_status,remote_version) VALUES ('$farm','$workspace','Finca Norte','ACTIVE',1000,1000,1,'SYNCED',1)")
        sql.execSQL("INSERT INTO sync_outbox(id,entity_type,entity_id,operation,payload_version,status,attempt_count,created_at,updated_at,account_id,farm_id) VALUES ('${UUID.randomUUID()}','FARM','$farm','CREATE',1,'CONFIRMED',1,1000,1000,'$a','$farm')")
        db.workspaceSelectionDao().upsert(WorkspaceSelectionEntity(issuer, a.toString(), workspace))
        grant(listOf("read", "write", "manage_members"))
        parcel = (OfflineFirstParcelRepository(db, SystemAppClock(), UuidGenerator(), DefaultAppDispatchers(), workspaceRepository = FixtureWorkspace { db })
            .create(NewParcel(farm, "Parcela 1", municipality = "Bedmar", province = "Jaén", managedAreaM2 = 1234.5)) as AppResult.Success).value
        parcelCreate = db.syncOutboxDao().listForEntity(SyncEntityType.PARCEL, parcel).single().id
        sql.execSQL("UPDATE parcels SET sync_status='SYNCED', remote_version=1 WHERE id='$parcel'")
        sql.execSQL("UPDATE sync_outbox SET status='CONFIRMED', account_id='$a' WHERE id='$parcelCreate'")
        server.workspaceOwners[workspace] = a
        server.farmOwners[farm] = a
    }

    @After fun close() {
        if (::lease.isInitialized) lease.close()
        if (::db.isInitialized) db.close()
        context.deleteDatabase(name)
    }

    @Test fun stageRequiresConfirmedParentsWriteGrantAndConsent() = runBlocking {
        val (campaign, op) = createCampaign("2026/27")
        assertEquals(CampaignCreateOutcome.CONSENT_REQUIRED, sender().stage(op))
        consent()
        db.openHelper.writableDatabase.execSQL("UPDATE sync_outbox SET status='PENDING' WHERE id='$parcelCreate'")
        assertEquals(CampaignCreateOutcome.PARENT_NOT_SYNCED, sender().stage(op))
        db.openHelper.writableDatabase.execSQL("UPDATE sync_outbox SET status='CONFIRMED' WHERE id='$parcelCreate'")
        grant(listOf("read"))
        assertEquals(CampaignCreateOutcome.NOT_AUTHORIZED, sender().stage(op))
        grant(listOf("read", "write", "manage_members"))
        assertNull(db.syncOutboxDao().findById(op)?.payloadJson)

        assertEquals(CampaignCreateOutcome.STAGED, sender().stage(op))
        val staged = db.syncOutboxDao().findById(op)!!
        assertEquals(a.toString(), staged.accountId); assertEquals(farm, staged.farmId)
        val snapshot = parseCampaignCreateEnvelope(staged.payloadJson!!)!!
        assertEquals(campaign, snapshot.campaignId)
        assertEquals(db.campaignDao().listSnapshots(campaign).map { it.id }.toSet(), snapshot.snapshotPayloads.keys)
        assertEquals(CampaignCreateOutcome.REJECTED, sender().stage(op))
        assertTrue(server.calls.isEmpty())
    }

    @Test fun sendConfirmsByReceiptAndTheConfirmedSnapshotsSurviveEditsAndActivation() = runBlocking {
        consent()
        val (campaign, op) = createCampaign("2026/27")
        assertEquals(CampaignCreateOutcome.STAGED, sender().stage(op))
        assertEquals(CampaignCreateOutcome.SYNCED, sender().send(op, lease))
        assertEquals(listOf("create_own_campaign", "check_sync_operations"), server.calls.map { it.first })
        val sent = (Json.parseToJsonElement(server.calls.first().second) as JsonObject).getValue("p_campaign") as JsonObject
        assertEquals(campaign.toString(), sent.getValue("id").jsonPrimitive.content)
        assertEquals("PREPARATION", sent.getValue("status").jsonPrimitive.content)
        val c = db.campaignDao().findById(campaign)!!
        assertEquals(SyncStatus.SYNCED, c.metadata.syncStatus); assertEquals(1L, c.metadata.remoteVersion)
        val snapshots = db.campaignDao().listSnapshots(campaign)
        assertTrue(snapshots.all { it.metadata.syncStatus == SyncStatus.SYNCED && it.metadata.remoteVersion == 1L })
        assertEquals(OutboxStatus.CONFIRMED, db.syncOutboxDao().findById(op)!!.status)
        assertEquals(CampaignCreateOutcome.ALREADY_CONFIRMED, sender().send(op, lease))

        // A confirmed campaign keeps its snapshots exactly through an edit and the activation...
        assertTrue(repo().updatePreparation(campaign, CampaignPreparationChanges("2026/27 editada", c.startDate, setOf(parcel), "nota"))
            is AppResult.Success)
        assertTrue(repo().activate(campaign) is AppResult.Success)
        assertEquals(snapshots, db.campaignDao().listSnapshots(campaign))
        assertEquals(CampaignStatus.ACTIVE, db.campaignDao().findById(campaign)!!.status)
        // ...and each change is one CAS UPDATE marker, never another CREATE.
        val markers = db.syncOutboxDao().listForEntity(SyncEntityType.CAMPAIGN, campaign).filter { it.status == OutboxStatus.PENDING }
        assertEquals(listOf(OutboxOperation.UPDATE), markers.map { it.operation })
        // A different parcel set is a real change with no snapshot contract yet: refused, nothing changed.
        val other = (OfflineFirstParcelRepository(db, SystemAppClock(), UuidGenerator(), DefaultAppDispatchers(), workspaceRepository = FixtureWorkspace { db })
            .create(NewParcel(farm, "Parcela 2")) as AppResult.Success).value
        db.openHelper.writableDatabase.execSQL("UPDATE campaigns SET status='PREPARATION' WHERE id='$campaign'")
        val before = db.campaignDao().listSnapshots(campaign)
        val refused = repo().updatePreparation(campaign, CampaignPreparationChanges("x", c.startDate, setOf(parcel, other)))
        assertEquals(AppError.Validation("parcelIds", "confirmed_campaign_parcels_locked"), (refused as AppResult.Failure).error)
        assertEquals(before, db.campaignDao().listSnapshots(campaign))
    }

    @Test fun aLostResponseIsReplayedWithTheSameOperationAndNeverDuplicated() = runBlocking {
        consent()
        val (campaign, op) = createCampaign("2026/27")
        assertEquals(CampaignCreateOutcome.STAGED, sender().stage(op))
        val payload = db.syncOutboxDao().findById(op)!!.payloadJson
        server.mode = Mode.LOST_AFTER_CREATE
        assertEquals(CampaignCreateOutcome.OUTCOME_UNKNOWN, sender().send(op, lease))
        assertEquals(OutboxStatus.FAILED, db.syncOutboxDao().findById(op)!!.status)
        assertEquals(payload, db.syncOutboxDao().findById(op)!!.payloadJson)
        assertNull(db.campaignDao().findById(campaign)!!.metadata.remoteVersion)
        server.mode = Mode.NORMAL
        assertEquals(CampaignCreateOutcome.SYNCED, sender().send(op, lease))
        assertEquals(1, server.campaigns.size)
        assertEquals(listOf(op), server.receipts.keys.map { it.second })
        assertEquals(1L, db.campaignDao().findById(campaign)!!.metadata.remoteVersion)
    }

    @Test fun aReceiptForOtherBytesIsAConflictAndNeverSynced() = runBlocking {
        consent()
        val (campaign, op) = createCampaign("2026/27")
        assertEquals(CampaignCreateOutcome.STAGED, sender().stage(op))
        server.mode = Mode.BAD_RECEIPT_HASH
        assertEquals(CampaignCreateOutcome.CONFLICT, sender().send(op, lease))
        val blocked = db.syncOutboxDao().findById(op)!!
        assertEquals(OutboxStatus.BLOCKED, blocked.status); assertEquals("RECEIPT_CONFLICT", blocked.lastErrorCode)
        assertEquals(SyncStatus.CONFLICT, db.campaignDao().findById(campaign)!!.metadata.syncStatus)
        assertNull(db.campaignDao().findById(campaign)!!.metadata.remoteVersion)
    }

    @Test fun anEditMadeWhileStagedIsKeptAndTravelsLaterAsAnUpdate() = runBlocking {
        consent()
        val (campaign, op) = createCampaign("2026/27")
        assertEquals(CampaignCreateOutcome.STAGED, sender().stage(op))
        val c = db.campaignDao().findById(campaign)!!
        assertTrue(repo().updatePreparation(campaign, CampaignPreparationChanges("2026/27 tras preparar", c.startDate, setOf(parcel)))
            is AppResult.Success)
        assertEquals(CampaignCreateOutcome.CONFIRMED_LOCAL_PENDING, sender().send(op, lease))
        // The staged bytes went; the later edit is kept and promoted to the CAS UPDATE that can carry it.
        val sent = (Json.parseToJsonElement(server.calls.first().second) as JsonObject).getValue("p_campaign") as JsonObject
        assertEquals("2026/27", sent.getValue("name").jsonPrimitive.content)
        assertEquals("2026/27 tras preparar", db.campaignDao().findById(campaign)!!.name)
        val later = db.syncOutboxDao().listForEntity(SyncEntityType.CAMPAIGN, campaign).filter { it.id != op }
        assertEquals(listOf(OutboxOperation.UPDATE to OutboxStatus.PENDING), later.map { it.operation to it.status })
        assertNotNull(db.campaignDao().findById(campaign)!!.metadata.remoteVersion)
    }

    // ------------------------------------------------------------------------------------------------------- helpers

    private fun sender() = CampaignCreateSendCoordinator(db, account, issuer, server, { ledger }, { now })
    private fun repo() = OfflineFirstCampaignRepository(db, SystemAppClock(), UuidGenerator(), DefaultAppDispatchers(), FixtureWorkspace { db })

    private suspend fun createCampaign(campaignName: String): Pair<UUID, UUID> {
        val id = (repo().create(NewCampaign(farm, campaignName, LocalDate.now().minusDays(30), setOf(parcel))) as AppResult.Success).value
        return id to db.syncOutboxDao().listForEntity(SyncEntityType.CAMPAIGN, id).single { it.operation == OutboxOperation.CREATE }.id
    }

    private fun consent() {
        ledger = FarmLinkLedger(listOf(FarmLinkConsent(UUID.randomUUID(), "$issuer|$a",
            listOf(FarmChoice(farm, workspace, "Finca Norte", 1)), UUID.randomUUID(), "b".repeat(64), now)))
    }

    private suspend fun grant(permissions: List<String>) = db.workspaceAuthorizationCacheDao().upsert(
        WorkspaceAuthorizationCacheEntity(issuer, a.toString(),
            """{"contract_version":1,"issuer":"$issuer/auth/v1","subject":"$a","verified_at":"$now","token_expires_at":"${now.plusSeconds(3600)}","workspaces":[{"id":"$workspace","name":"Olivar A","role":"OWNER","permissions":${permissions.joinToString(",", "[", "]") { "\"$it\"" }},"authorization_expires_at":"${now.plusSeconds(3600)}","grant_version":1}]}"""))

    private enum class Mode { NORMAL, LOST_AFTER_CREATE, BAD_RECEIPT_HASH }
    private data class Receipt(val campaign: UUID, val hash: String)

    /** create_own_campaign + check_sync_operations: owner-only, owned farm, create-only, exact replays. */
    private inner class Server : DeliveryRpcTransport {
        var mode = Mode.NORMAL
        val calls = mutableListOf<Pair<String, String>>()
        val workspaceOwners = mutableMapOf<UUID, UUID>()
        val farmOwners = mutableMapOf<UUID, UUID>()
        val campaigns = mutableMapOf<UUID, UUID>()
        val receipts = mutableMapOf<Pair<UUID, UUID>, Receipt>()
        override suspend fun rpc(name: String, bodyJson: String, accessToken: String): DeliveryRpcResponse {
            calls += name to bodyJson
            val actor = (store.read().session ?: return DeliveryRpcResponse(401, "{}")).userId
            val body = Json.parseToJsonElement(bodyJson) as JsonObject
            return when (name) {
                "create_own_campaign" -> create(actor, body)
                "check_sync_operations" -> {
                    val op = UUID.fromString((body.getValue("p_operation_ids") as JsonArray).single().jsonPrimitive.content)
                    val rows = receipts[actor to op]?.let { r ->
                        val hash = if (mode == Mode.BAD_RECEIPT_HASH) "0".repeat(64) else r.hash
                        """[{"operation_id":"$op","entity_id":"${r.campaign}","entity_type":"CAMPAIGN","remote_version":1,"payload_version":1,"payload_hash":"$hash"}]"""
                    } ?: "[]"
                    DeliveryRpcResponse(200, """{"success":true,"confirmed_operations":[],"confirmed_receipts":$rows}""")
                }
                else -> error("unexpected $name")
            }
        }
        private fun create(actor: UUID, body: JsonObject): DeliveryRpcResponse {
            val op = UUID.fromString(body.getValue("p_operation_id").jsonPrimitive.content)
            val campaign = body.getValue("p_campaign") as JsonObject
            val id = UUID.fromString(campaign.getValue("id").jsonPrimitive.content)
            val ws = UUID.fromString(campaign.getValue("workspaceId").jsonPrimitive.content)
            val f = UUID.fromString(campaign.getValue("farmId").jsonPrimitive.content)
            if (workspaceOwners[ws] != actor || farmOwners[f] != actor) return DeliveryRpcResponse(403, """{"code":"42501"}""")
            val hash = campaignCreateHash(campaign)
            val saved = receipts[actor to op]
            if (saved != null) {
                if (saved.campaign != id || saved.hash != hash) return DeliveryRpcResponse(409, """{"code":"23505"}""")
            } else {
                if (id in campaigns) return DeliveryRpcResponse(409, """{"code":"23505"}""")
                campaigns[id] = actor; receipts[actor to op] = Receipt(id, hash)
            }
            if (mode == Mode.LOST_AFTER_CREATE) throw DeliveryRpcFailure(DeliveryRpcError.NETWORK)
            return DeliveryRpcResponse(200,
                """{"success":true,"operation_id":"$op","entity_id":"$id","status":"APPLIED","remote_version":1,"payload_version":1,"idempotent":${saved != null}}""")
        }
    }

    private inner class Auth : AccountAuthApi {
        override val redirectUri = "app://callback"
        override suspend fun signIn(email: String, password: String): AuthSession = error("no login")
        override suspend fun signUp(email: String, password: String, challenge: String): AuthSession? = error("no signup")
        override suspend fun recover(email: String, challenge: String) = error("no recovery")
        override suspend fun exchange(code: String, verifier: String): AuthSession = error("no exchange")
        override suspend fun refresh(session: AuthSession): AuthSession = error("no refresh")
        override suspend fun user(session: AuthSession): Pair<UUID, String> = session.userId to session.email
        override suspend fun changePassword(session: AuthSession, password: String) = error("no password")
        override suspend fun signOut(session: AuthSession) = Unit
    }
}
