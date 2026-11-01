package com.isivoltpro.maginaolivo.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.core.dispatchers.DefaultAppDispatchers
import com.isivoltpro.maginaolivo.core.id.UuidGenerator
import com.isivoltpro.maginaolivo.core.time.SystemAppClock
import com.isivoltpro.maginaolivo.data.local.entity.WorkspaceAuthorizationCacheEntity
import com.isivoltpro.maginaolivo.data.local.entity.WorkspaceSelectionEntity
import com.isivoltpro.maginaolivo.data.local.model.OutboxOperation
import com.isivoltpro.maginaolivo.data.local.model.OutboxStatus
import com.isivoltpro.maginaolivo.data.local.model.SyncEntityType
import com.isivoltpro.maginaolivo.data.local.model.SyncStatus
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstParcelRepository
import com.isivoltpro.maginaolivo.data.repository.sync.DeliveryDispatchLease
import com.isivoltpro.maginaolivo.data.repository.sync.DeliveryRpcError
import com.isivoltpro.maginaolivo.data.repository.sync.DeliveryRpcFailure
import com.isivoltpro.maginaolivo.data.repository.sync.DeliveryRpcResponse
import com.isivoltpro.maginaolivo.data.repository.sync.DeliveryRpcTransport
import com.isivoltpro.maginaolivo.data.repository.sync.ParcelSendCoordinator
import com.isivoltpro.maginaolivo.data.repository.sync.ParcelSendOutcome
import com.isivoltpro.maginaolivo.data.repository.sync.ParentConflict
import com.isivoltpro.maginaolivo.data.repository.sync.ParentKind
import com.isivoltpro.maginaolivo.data.repository.sync.ParentReceiveCoordinator
import com.isivoltpro.maginaolivo.data.repository.sync.ParentReceiveOutcome
import com.isivoltpro.maginaolivo.data.repository.sync.parcelCreateHash
import com.isivoltpro.maginaolivo.data.repository.sync.parseParcelCreateEnvelope
import com.isivoltpro.maginaolivo.domain.parcel.NewParcel
import com.isivoltpro.maginaolivo.domain.parcel.ParcelChanges
import com.isivoltpro.maginaolivo.feature.account.AccountAuthApi
import com.isivoltpro.maginaolivo.feature.account.AccountRecord
import com.isivoltpro.maginaolivo.feature.account.AccountRepository
import com.isivoltpro.maginaolivo.feature.account.AuthFailure
import com.isivoltpro.maginaolivo.feature.account.AuthSession
import com.isivoltpro.maginaolivo.feature.account.MemoryAccountSessionStore
import com.isivoltpro.maginaolivo.feature.accountlink.FarmChoice
import com.isivoltpro.maginaolivo.feature.accountlink.FarmLinkConsent
import com.isivoltpro.maginaolivo.feature.accountlink.FarmLinkLedger
import com.isivoltpro.maginaolivo.feature.accountlink.FarmLinkRevocation
import java.io.File
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Room-only G2.6 PARCEL send checks against a fake that models the reviewed `create_own_parcel` semantics. */
@RunWith(AndroidJUnit4::class)
class ParcelSendRoomTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "g26-parcel-send-room"
    private val issuer = "https://project.example"
    private val a = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
    private val c = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc")
    private val workspace = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val farm = UUID.fromString("22222222-2222-2222-2222-222222222221")
    private var now = Instant.now()
    private val store = MemoryAccountSessionStore()
    private val auth = FakeAuth()
    private val server = Server()
    private var ledger: FarmLinkLedger? = null
    private lateinit var db: MaginaOlivoDatabase
    private lateinit var account: AccountRepository
    private lateinit var lease: DeliveryDispatchLease
    private lateinit var farmCreate: UUID

    @Before fun setup() = runBlocking {
        context.deleteDatabase(name)
        db = MaginaOlivoDatabase.create(context, name)
        lease = DeliveryDispatchLease.tryAcquire(File(context.noBackupFilesDir, "g26-parcel-send-room"))!!
        login(a)
        // WORKSPACE (#798) and FARM (#799) already confirmed: SYNCED, remote v1, bound CREATEs CONFIRMED.
        farmCreate = UUID.randomUUID()
        val sql = db.openHelper.writableDatabase
        sql.execSQL("INSERT INTO workspaces(id,name,owner_user_id,country_code,timezone,locale,currency,created_at,updated_at,version,sync_status,remote_version) VALUES ('$workspace','Olivar A','$a','ES','Europe/Madrid','es-ES','EUR',1000,1000,1,'SYNCED',1)")
        sql.execSQL("INSERT INTO sync_outbox(id,entity_type,entity_id,operation,payload_version,status,attempt_count,created_at,updated_at,account_id) VALUES ('${UUID.randomUUID()}','WORKSPACE','$workspace','CREATE',1,'CONFIRMED',0,1000,1000,'$a')")
        sql.execSQL("INSERT INTO farms(id,workspace_id,name,status,created_at,updated_at,version,sync_status,remote_version) VALUES ('$farm','$workspace','Finca Norte','ACTIVE',1000,1000,1,'SYNCED',1)")
        sql.execSQL("INSERT INTO sync_outbox(id,entity_type,entity_id,operation,payload_version,status,attempt_count,created_at,updated_at,account_id,farm_id) VALUES ('$farmCreate','FARM','$farm','CREATE',1,'CONFIRMED',0,1000,1000,'$a','$farm')")
        db.workspaceSelectionDao().upsert(WorkspaceSelectionEntity(issuer, a.toString(), workspace))
        grant(listOf("read", "write", "manage_members"))
        server.workspaceOwners[workspace] = a
        server.farmOwners[farm] = a
    }

    @After fun close() {
        if (::lease.isInitialized) lease.close()
        if (::db.isInitialized) db.close()
        context.deleteDatabase(name)
    }

    @Test fun stageRequiresConfirmedParentsWriteGrantAndConsent() = runBlocking {
        val (parcel, op) = createParcel("Parcela 1")
        assertEquals(ParcelSendOutcome.CONSENT_REQUIRED, sender().stage(op))
        consent()
        db.openHelper.writableDatabase.execSQL("UPDATE sync_outbox SET status='PENDING' WHERE id='$farmCreate'")
        assertEquals(ParcelSendOutcome.PARENT_NOT_SYNCED, sender().stage(op))
        db.openHelper.writableDatabase.execSQL("UPDATE sync_outbox SET status='CONFIRMED' WHERE id='$farmCreate'")
        grant(listOf("read"))
        assertEquals(ParcelSendOutcome.NOT_AUTHORIZED, sender().stage(op))
        grant(listOf("read", "write", "manage_members"))
        assertNull(db.syncOutboxDao().findById(op)?.payloadJson)

        assertEquals(ParcelSendOutcome.STAGED, sender().stage(op))
        val staged = db.syncOutboxDao().findById(op)!!
        assertEquals(a.toString(), staged.accountId)
        assertEquals(farm, staged.farmId)
        val snapshot = parseParcelCreateEnvelope(staged.payloadJson!!)!!
        assertEquals(parcel, snapshot.parcelId)
        assertEquals(setOf(farm), snapshot.farmIds)
        assertEquals(db.parcelDao().listMemberships(parcel).map { it.id }.toSet(), snapshot.membershipPayloads.keys)
        assertEquals(ParcelSendOutcome.REJECTED, sender().stage(op))
        assertTrue(server.calls.isEmpty())
    }

    @Test fun sendKeepsUuidsCorrelatesTheReceiptAndReceivesBackWithoutConflict() = runBlocking {
        consent()
        val (parcel, op) = createParcel("Parcela 1")
        val membership = db.parcelDao().listMemberships(parcel).single()
        assertEquals(ParcelSendOutcome.STAGED, sender().stage(op))
        assertEquals(ParcelSendOutcome.SYNCED, sender().send(op, lease))
        assertEquals(listOf("create_own_parcel", "check_sync_operations"), server.calls.map { it.first })
        val sent = (Json.parseToJsonElement(server.calls.first().second) as JsonObject).getValue("p_parcel") as JsonObject
        assertEquals(parcel.toString(), sent.getValue("id").jsonPrimitive.content)
        assertEquals(membership.id.toString(),
            ((sent.getValue("memberships") as JsonArray).single() as JsonObject).getValue("id").jsonPrimitive.content)
        assertEquals(a, server.parcelOwners[parcel])
        assertEquals(SyncStatus.SYNCED, db.parcelDao().findById(parcel)?.metadata?.syncStatus)
        assertEquals(1L, db.parcelDao().findById(parcel)?.metadata?.remoteVersion)
        assertEquals(SyncStatus.SYNCED, db.parcelDao().listMemberships(parcel).single().metadata.syncStatus)
        assertEquals(OutboxStatus.CONFIRMED, db.syncOutboxDao().findById(op)?.status)

        val calls = server.calls.size
        assertEquals(ParcelSendOutcome.ALREADY_CONFIRMED, sender().send(op, lease))
        db.close(); db = MaginaOlivoDatabase.create(context, name)
        assertEquals(ParcelSendOutcome.ALREADY_CONFIRMED, sender().send(op, lease))
        assertEquals(calls, server.calls.size)

        val parents = parents(parcel, membership.id, "Parcela 1", membership.validFrom)
        assertEquals(ParentReceiveOutcome.IMPORTED, ParentReceiveCoordinator(db, account, issuer, parents) { now }.receive(workspace).outcome)
        assertEquals(ParentReceiveOutcome.ALREADY_CURRENT,
            ParentReceiveCoordinator(db, account, issuer, parents) { now }.receive(workspace).outcome)
        assertEquals(1, db.parcelDao().listMemberships(parcel).size)
        assertEquals(1, db.syncOutboxDao().listForEntity(SyncEntityType.PARCEL, parcel).size)
    }

    @Test fun lostResponseOrServerErrorIsReplayedWithTheSameOperationAndPayload() = runBlocking {
        consent()
        val (parcel, op) = createParcel("Parcela 1")
        assertEquals(ParcelSendOutcome.STAGED, sender().stage(op))
        val payload = db.syncOutboxDao().findById(op)?.payloadJson
        for ((mode, attempts) in listOf(Mode.LOST_AFTER_CREATE to 1, Mode.ERROR_AFTER_CREATE to 2)) {
            server.mode = mode
            assertEquals(mode.name, ParcelSendOutcome.OUTCOME_UNKNOWN, sender().send(op, lease))
            val current = db.syncOutboxDao().findById(op)!!
            assertEquals(OutboxStatus.FAILED, current.status)
            assertEquals(attempts, current.attemptCount)
            assertEquals(payload, current.payloadJson)
            assertEquals(SyncStatus.PENDING, db.parcelDao().findById(parcel)?.metadata?.syncStatus)
        }
        server.mode = Mode.NORMAL
        assertEquals(ParcelSendOutcome.SYNCED, sender().send(op, lease))
        assertEquals(1, server.parcelOwners.size)
        assertEquals(1, server.receipts.size)
        assertEquals(1, server.calls.filter { it.first == "create_own_parcel" }.map { it.second }.distinct().size)
    }

    @Test fun takenUuidBadReceiptOrInvalidPayloadIsBlockedAndNeverRetried() = runBlocking {
        consent()
        val (taken, takenOp) = createParcel("Ajena")
        assertEquals(ParcelSendOutcome.STAGED, sender().stage(takenOp))
        server.parcelOwners[taken] = c
        assertEquals(ParcelSendOutcome.CONFLICT, sender().send(takenOp, lease))
        assertEquals(OutboxStatus.BLOCKED, db.syncOutboxDao().findById(takenOp)?.status)
        assertEquals("PARCEL_CONFLICT", db.syncOutboxDao().findById(takenOp)?.lastErrorCode)
        assertEquals(SyncStatus.CONFLICT, db.parcelDao().findById(taken)?.metadata?.syncStatus)
        val calls = server.calls.size
        assertEquals(ParcelSendOutcome.REJECTED, sender().send(takenOp, lease))
        assertEquals(calls, server.calls.size)

        val (forged, forgedOp) = createParcel("Recibo alterado")
        assertEquals(ParcelSendOutcome.STAGED, sender().stage(forgedOp))
        server.mode = Mode.BAD_RECEIPT_HASH
        assertEquals(ParcelSendOutcome.CONFLICT, sender().send(forgedOp, lease))
        assertEquals("RECEIPT_CONFLICT", db.syncOutboxDao().findById(forgedOp)?.lastErrorCode)
        assertNull(db.parcelDao().findById(forged)?.metadata?.remoteVersion)

        val (_, invalidOp) = createParcel("Rechazada")
        assertEquals(ParcelSendOutcome.STAGED, sender().stage(invalidOp))
        server.mode = Mode.INVALID
        assertEquals(ParcelSendOutcome.REJECTED, sender().send(invalidOp, lease))
        assertEquals("PARCEL_INVALID", db.syncOutboxDao().findById(invalidOp)?.lastErrorCode)
    }

    @Test fun logoutSwitchOfflineForbiddenAndConsentRevocationKeepTheOutbox() = runBlocking {
        consent()
        val (parcel, op) = createParcel("Parcela 1")
        assertEquals(ParcelSendOutcome.STAGED, sender().stage(op))
        val pending = db.syncOutboxDao().findById(op)
        account.logout(false)
        assertEquals(ParcelSendOutcome.NOT_AUTHORIZED, sender().send(op, lease))
        login(c)
        assertEquals(ParcelSendOutcome.NOT_AUTHORIZED, sender().send(op, lease))
        assertTrue(server.calls.isEmpty())
        login(a)
        auth.offline = true
        assertEquals(ParcelSendOutcome.OFFLINE, sender().send(op, lease))
        auth.offline = false
        server.mode = Mode.FORBIDDEN
        assertEquals(ParcelSendOutcome.NOT_AUTHORIZED, sender().send(op, lease))
        server.mode = Mode.LOGOUT_DURING_REQUEST
        assertEquals(ParcelSendOutcome.NOT_AUTHORIZED, sender().send(op, lease))
        assertEquals(pending, db.syncOutboxDao().findById(op))
        login(a)
        server.mode = Mode.NORMAL
        grant(listOf("read"))
        val before = server.calls.size
        assertEquals(ParcelSendOutcome.NOT_AUTHORIZED, sender().send(op, lease))
        assertEquals(before, server.calls.size)
        grant(listOf("read", "write", "manage_members"))
        // Local G1b revocation of the parcel's farm: same effect as LocalFarmLinkRepository.revokeConsentForFarm.
        ledger = ledger!!.copy(revocations = listOf(FarmLinkRevocation(UUID.randomUUID(), "$issuer|$a", farm, now, 1)))
        db.syncOutboxDao().updateStatusForFarm(farm, OutboxStatus.BLOCKED, now.toEpochMilli())
        assertEquals(ParcelSendOutcome.CONSENT_REQUIRED, sender().send(op, lease))
        assertEquals(before, server.calls.size)
        assertEquals(OutboxStatus.BLOCKED, db.syncOutboxDao().findById(op)?.status)
        assertEquals(pending?.payloadJson, db.syncOutboxDao().findById(op)?.payloadJson)
        assertEquals("Parcela 1", db.parcelDao().findById(parcel)?.displayName)
    }

    @Test fun offlineEditAfterStagingIsKeptPendingAndNeverOverwritten() = runBlocking {
        consent()
        val (parcel, op) = createParcel("Parcela 1")
        val membership = db.parcelDao().listMemberships(parcel).single()
        assertEquals(ParcelSendOutcome.STAGED, sender().stage(op))
        assertTrue(parcels().update(parcel, ParcelChanges("Parcela 1 editada offline", managedAreaM2 = 900.0)) is AppResult.Success)
        assertEquals(ParcelSendOutcome.CONFIRMED_LOCAL_PENDING, sender().send(op, lease))
        val row = db.parcelDao().findById(parcel)!!
        assertEquals("Parcela 1 editada offline", row.displayName)
        assertEquals(SyncStatus.PENDING, row.metadata.syncStatus)
        assertEquals(1L, row.metadata.remoteVersion)
        assertEquals(listOf(OutboxOperation.CREATE to OutboxStatus.CONFIRMED, OutboxOperation.UPDATE to OutboxStatus.PENDING),
            db.syncOutboxDao().listForEntity(SyncEntityType.PARCEL, parcel).map { it.operation to it.status })
        assertEquals("Parcela 1", server.parcelNames[parcel])
        val report = ParentReceiveCoordinator(db, account, issuer, parents(parcel, membership.id, "Parcela 1", membership.validFrom)) { now }
            .receive(workspace)
        assertTrue(report.conflicts.contains(ParentConflict(ParentKind.PARCEL, parcel, "local_pending")))
        assertEquals("Parcela 1 editada offline", db.parcelDao().findById(parcel)?.displayName)
    }

    private fun sender() = ParcelSendCoordinator(db, account, issuer, server, { ledger }, { now })
    private fun parcels() = OfflineFirstParcelRepository(db, SystemAppClock(), UuidGenerator(), DefaultAppDispatchers())
    /** The app's own producer: the parcel, its initial membership and one unbound PARCEL CREATE marker. */
    private suspend fun createParcel(parcelName: String): Pair<UUID, UUID> {
        val parcel = (parcels().create(NewParcel(farm, parcelName, municipality = "Bedmar", province = "Jaén",
            managedAreaM2 = 1234.5)) as AppResult.Success).value
        return parcel to db.syncOutboxDao().listForEntity(SyncEntityType.PARCEL, parcel).single().id
    }
    private fun consent() {
        ledger = FarmLinkLedger(listOf(FarmLinkConsent(UUID.randomUUID(), "$issuer|$a",
            listOf(FarmChoice(farm, workspace, "Finca Norte", 1)), UUID.randomUUID(), "b".repeat(64), now)))
    }
    private suspend fun grant(permissions: List<String>) = db.workspaceAuthorizationCacheDao().upsert(
        WorkspaceAuthorizationCacheEntity(issuer, a.toString(),
            """{"contract_version":1,"issuer":"$issuer/auth/v1","subject":"$a","verified_at":"$now","token_expires_at":"${now.plusSeconds(3600)}","workspaces":[{"id":"$workspace","name":"Olivar A","role":"OWNER","permissions":[${permissions.joinToString(",") { "\"$it\"" }}],"grant_version":1,"authorization_expires_at":"${now.plusSeconds(900)}"}]}"""))
    private fun parents(parcel: UUID, membership: UUID, parcelName: String, from: Instant) = DeliveryRpcTransport { _, _, _ ->
        DeliveryRpcResponse(200, """{"contract_version":1,"issuer":"$issuer/auth/v1","subject":"$a","workspace":{"id":"$workspace","name":"Olivar A","ownerUserId":"$a","countryCode":"ES","timezone":"Europe/Madrid","locale":"es-ES","currency":"EUR","createdAt":"1970-01-01T00:00:01Z","updatedAt":"1970-01-01T00:00:01Z","version":1},"farms":[{"id":"$farm","workspaceId":"$workspace","name":"Finca Norte","description":null,"municipality":null,"province":null,"notes":null,"status":"ACTIVE","createdAt":"1970-01-01T00:00:01Z","updatedAt":"1970-01-01T00:00:01Z","version":1}],"parcels":[{"id":"$parcel","workspaceId":"$workspace","displayName":"$parcelName","cadastralReference":null,"cadastralPolygon":null,"cadastralParcel":null,"municipality":"Bedmar","province":"Jaén","source":"MANUAL","geometryGeoJson":null,"sourceProvider":null,"sourceImportedAt":null,"cadastralAreaM2":null,"managedAreaM2":1234.5,"notes":null,"status":"ACTIVE","oliveTreeCount":null,"variety":null,"irrigationSystem":null,"irrigationNetwork":null,"irrigationSector":null,"irrigationDays":null,"createdAt":"2026-11-01T10:00:01.234567+00:00","updatedAt":"2026-11-01T10:00:01.234567+00:00","version":1}],"memberships":[{"id":"$membership","workspaceId":"$workspace","farmId":"$farm","parcelId":"$parcel","validFrom":"$from","validUntil":null,"createdAt":"2026-11-01T10:00:01.234567+00:00","updatedAt":"2026-11-01T10:00:01.234567+00:00","version":1}]}""")
    }
    private fun login(id: UUID) {
        store.write(AccountRecord(session = AuthSession(id, "$id@example.test", "access", "refresh", now.plusSeconds(3600))))
        account = AccountRepository(auth, store) { now }
    }

    private enum class Mode { NORMAL, LOST_AFTER_CREATE, ERROR_AFTER_CREATE, FORBIDDEN, INVALID, BAD_RECEIPT_HASH, LOGOUT_DURING_REQUEST }
    private data class Receipt(val parcel: UUID, val hash: String)

    /** create_own_parcel + check_sync_operations: owner-only, owned farms, create-only, exact replays. */
    private inner class Server : DeliveryRpcTransport {
        var mode = Mode.NORMAL
        val calls = mutableListOf<Pair<String, String>>()
        val workspaceOwners = mutableMapOf<UUID, UUID>()
        val farmOwners = mutableMapOf<UUID, UUID>()
        val parcelOwners = mutableMapOf<UUID, UUID>()
        val parcelNames = mutableMapOf<UUID, String>()
        val receipts = mutableMapOf<Pair<UUID, UUID>, Receipt>()
        override suspend fun rpc(name: String, bodyJson: String, accessToken: String): DeliveryRpcResponse {
            calls += name to bodyJson
            val actor = (store.read().session ?: return DeliveryRpcResponse(401, "{}")).userId
            if (mode == Mode.FORBIDDEN) return DeliveryRpcResponse(403, """{"code":"42501"}""")
            val body = Json.parseToJsonElement(bodyJson) as JsonObject
            return when (name) {
                "create_own_parcel" -> create(actor, body)
                "check_sync_operations" -> {
                    val op = UUID.fromString((body.getValue("p_operation_ids") as JsonArray).single().jsonPrimitive.content)
                    val rows = receipts[actor to op]?.let { r ->
                        val hash = if (mode == Mode.BAD_RECEIPT_HASH) "0".repeat(64) else r.hash
                        """[{"operation_id":"$op","entity_id":"${r.parcel}","entity_type":"PARCEL","remote_version":1,"payload_version":1,"payload_hash":"$hash"}]"""
                    } ?: "[]"
                    DeliveryRpcResponse(200, """{"success":true,"confirmed_operations":[],"confirmed_receipts":$rows}""")
                }
                else -> error("unexpected $name")
            }
        }
        private suspend fun create(actor: UUID, body: JsonObject): DeliveryRpcResponse {
            if (mode == Mode.INVALID) return DeliveryRpcResponse(400, """{"code":"22023"}""")
            val op = UUID.fromString(body.getValue("p_operation_id").jsonPrimitive.content)
            val parcel = body.getValue("p_parcel") as JsonObject
            val id = UUID.fromString(parcel.getValue("id").jsonPrimitive.content)
            val ws = UUID.fromString(parcel.getValue("workspaceId").jsonPrimitive.content)
            val farms = (parcel.getValue("memberships") as JsonArray)
                .map { UUID.fromString((it as JsonObject).getValue("farmId").jsonPrimitive.content) }
            if (workspaceOwners[ws] != actor || farms.any { farmOwners[it] != actor })
                return DeliveryRpcResponse(403, """{"code":"42501"}""")
            val hash = parcelCreateHash(parcel)
            val saved = receipts[actor to op]
            val replay = saved != null
            if (saved != null) {
                if (saved.parcel != id || saved.hash != hash) return DeliveryRpcResponse(409, """{"code":"23505"}""")
                if (parcelOwners[id] != actor) return DeliveryRpcResponse(403, """{"code":"42501"}""")
            } else {
                if (id in parcelOwners) return DeliveryRpcResponse(409, """{"code":"23505"}""")
                parcelOwners[id] = actor; parcelNames[id] = parcel.getValue("displayName").jsonPrimitive.content
                receipts[actor to op] = Receipt(id, hash)
            }
            return when (mode) {
                Mode.LOST_AFTER_CREATE -> throw DeliveryRpcFailure(DeliveryRpcError.NETWORK)
                Mode.ERROR_AFTER_CREATE -> DeliveryRpcResponse(503, "{}")
                Mode.LOGOUT_DURING_REQUEST -> { account.logout(false); ack(op, id, replay) }
                else -> ack(op, id, replay)
            }
        }
        private fun ack(op: UUID, id: UUID, replay: Boolean) = DeliveryRpcResponse(200,
            """{"success":true,"operation_id":"$op","entity_id":"$id","status":"APPLIED","remote_version":1,"payload_version":1,"idempotent":$replay}""")
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
