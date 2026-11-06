package com.isivoltpro.maginaolivo.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.isivoltpro.maginaolivo.account.reservation.ReservationRequest
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.core.dispatchers.DefaultAppDispatchers
import com.isivoltpro.maginaolivo.core.id.UuidGenerator
import com.isivoltpro.maginaolivo.core.regional.RegionalContext
import com.isivoltpro.maginaolivo.core.time.SystemAppClock
import com.isivoltpro.maginaolivo.data.local.entity.WorkspaceAuthorizationCacheEntity
import com.isivoltpro.maginaolivo.data.local.entity.WorkspaceSelectionEntity
import com.isivoltpro.maginaolivo.data.local.model.OutboxOperation
import com.isivoltpro.maginaolivo.data.local.model.OutboxStatus
import com.isivoltpro.maginaolivo.data.local.model.SyncEntityType
import com.isivoltpro.maginaolivo.data.local.model.SyncStatus
import com.isivoltpro.maginaolivo.data.repository.LocalWorkspaceRepository
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstFarmRepository
import com.isivoltpro.maginaolivo.data.repository.sync.DeliveryDispatchLease
import com.isivoltpro.maginaolivo.data.repository.sync.DeliveryRpcError
import com.isivoltpro.maginaolivo.data.repository.sync.DeliveryRpcFailure
import com.isivoltpro.maginaolivo.data.repository.sync.DeliveryRpcResponse
import com.isivoltpro.maginaolivo.data.repository.sync.DeliveryRpcTransport
import com.isivoltpro.maginaolivo.data.repository.sync.FarmCreateSnapshot
import com.isivoltpro.maginaolivo.data.repository.sync.FarmSendCoordinator
import com.isivoltpro.maginaolivo.data.repository.sync.FarmSendOutcome
import com.isivoltpro.maginaolivo.data.repository.sync.ParentConflict
import com.isivoltpro.maginaolivo.data.repository.sync.ParentKind
import com.isivoltpro.maginaolivo.data.repository.sync.ParentReceiveCoordinator
import com.isivoltpro.maginaolivo.data.repository.sync.ParentReceiveOutcome
import com.isivoltpro.maginaolivo.data.repository.sync.farmCreateHash
import com.isivoltpro.maginaolivo.data.repository.sync.parseFarmCreateEnvelope
import com.isivoltpro.maginaolivo.domain.farm.FarmChanges
import com.isivoltpro.maginaolivo.domain.farm.NewFarm
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
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Room-only G2.6 FARM send checks against a fake that models the reviewed G1c/G2 RPC semantics. */
@RunWith(AndroidJUnit4::class)
class FarmSendRoomTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "g26-farm-send-room"
    private val issuer = "https://project.example"
    private val a = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
    private val c = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc")
    private val workspace = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val consentId = UUID.fromString("55555555-5555-5555-5555-555555555555")
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
        lease = DeliveryDispatchLease.tryAcquire(File(context.noBackupFilesDir, "g26-farm-send-room"))!!
        login(a)
        // WORKSPACE already confirmed by the G2.6 WORKSPACE sender (#798): SYNCED, owned, bound CREATE CONFIRMED.
        val sql = db.openHelper.writableDatabase
        sql.execSQL("INSERT INTO workspaces(id,name,owner_user_id,country_code,timezone,locale,currency,created_at,updated_at,version,sync_status,remote_version) VALUES ('$workspace','Olivar A','$a','ES','Europe/Madrid','es-ES','EUR',1000,1000,1,'SYNCED',1)")
        sql.execSQL("INSERT INTO sync_outbox(id,entity_type,entity_id,operation,payload_version,status,attempt_count,created_at,updated_at,account_id) VALUES ('${UUID.randomUUID()}','WORKSPACE','$workspace','CREATE',1,'CONFIRMED',0,1000,1000,'$a')")
        db.workspaceSelectionDao().upsert(WorkspaceSelectionEntity(issuer, a.toString(), workspace))
        grant(a, "OWNER", listOf("read", "write", "manage_members"))
        server.workspaceOwners[workspace] = a
    }

    @After fun close() {
        if (::lease.isInitialized) lease.close()
        if (::db.isInitialized) db.close()
        context.deleteDatabase(name)
    }

    @Test fun stageRequiresConfirmedSelectedOwnedWorkspaceAndActiveConsent() = runBlocking {
        val (farm, op) = createFarm("Finca Norte")
        assertEquals(FarmSendOutcome.CONSENT_REQUIRED, sender().stage(op))
        consent(farm, "Finca Norte")
        val wsOp = db.syncOutboxDao().listForEntity(SyncEntityType.WORKSPACE, workspace).single()
        db.openHelper.writableDatabase.execSQL("UPDATE sync_outbox SET status='PENDING' WHERE id='${wsOp.id}'")
        assertEquals(FarmSendOutcome.PARENT_NOT_SYNCED, sender().stage(op))
        db.openHelper.writableDatabase.execSQL("UPDATE sync_outbox SET status='CONFIRMED' WHERE id='${wsOp.id}'")
        grant(a, "VIEWER", listOf("read"))
        assertEquals(FarmSendOutcome.NOT_AUTHORIZED, sender().stage(op))
        grant(a, "OWNER", listOf("read", "write", "manage_members"))
        db.openHelper.writableDatabase.execSQL("DELETE FROM workspace_selections")
        assertEquals(FarmSendOutcome.NOT_AUTHORIZED, sender().stage(op))
        db.workspaceSelectionDao().upsert(WorkspaceSelectionEntity(issuer, a.toString(), workspace))
        assertNull(db.syncOutboxDao().findById(op)?.payloadJson)

        assertEquals(FarmSendOutcome.STAGED, sender().stage(op))
        val staged = db.syncOutboxDao().findById(op)!!
        assertEquals(a.toString(), staged.accountId)
        assertEquals(farm, staged.farmId)
        val snapshot = parseFarmCreateEnvelope(staged.payloadJson!!)!!
        assertEquals(farm, snapshot.farmId)
        assertEquals(consentId, snapshot.reservation.consentId)
        assertEquals("b".repeat(64), snapshot.reservation.manifestSha256)
        assertEquals(FarmSendOutcome.REJECTED, sender().stage(op))
        assertTrue(server.calls.isEmpty())
    }

    @Test fun sendKeepsTheUuidCorrelatesBothReceiptsAndReceivesBackWithoutConflict() = runBlocking {
        val cover = UUID.randomUUID()
        val (farm, op) = createFarm("Finca Norte", cover)
        consent(farm, "Finca Norte")
        assertEquals(FarmSendOutcome.STAGED, sender().stage(op))
        assertEquals(FarmSendOutcome.SYNCED, sender().send(op, lease))
        assertEquals(listOf("reserve_local_farm_g1c", "create_reserved_farm", "check_sync_operations"),
            server.calls.map { it.first })
        val created = Json.parseToJsonElement(server.calls[1].second) as JsonObject
        assertEquals(op.toString(), created.getValue("p_operation_id").jsonPrimitive.content)
        assertEquals(farm.toString(), (created.getValue("p_farm") as JsonObject).getValue("id").jsonPrimitive.content)
        assertEquals("null", (created.getValue("p_farm") as JsonObject).getValue("coverDocumentId").toString())
        assertEquals(a, server.farmOwners[farm])
        val row = db.farmDao().findById(farm)!!
        assertEquals(SyncStatus.SYNCED, row.metadata.syncStatus)
        assertEquals(1L, row.metadata.remoteVersion)
        assertEquals(OutboxStatus.CONFIRMED, db.syncOutboxDao().findById(op)?.status)

        val calls = server.calls.size
        assertEquals(FarmSendOutcome.ALREADY_CONFIRMED, sender().send(op, lease))
        db.close(); db = MaginaOlivoDatabase.create(context, name)
        assertEquals(FarmSendOutcome.ALREADY_CONFIRMED, sender().send(op, lease))
        assertEquals(calls, server.calls.size)

        val parents = parents(farm, "Finca Norte")
        assertEquals(ParentReceiveOutcome.IMPORTED, ParentReceiveCoordinator(db, account, issuer, parents) { now }.receive(workspace).outcome)
        assertEquals(ParentReceiveOutcome.ALREADY_CURRENT,
            ParentReceiveCoordinator(db, account, issuer, parents) { now }.receive(workspace).outcome)
        assertEquals(cover, db.farmDao().findById(farm)?.coverDocumentId)
        assertEquals(1, db.syncOutboxDao().listForEntity(SyncEntityType.FARM, farm).size)
    }

    @Test fun lostReserveOrCreateResponsesAreReplayedWithTheSameOperations() = runBlocking {
        val (farm, op) = createFarm("Finca Norte")
        consent(farm, "Finca Norte")
        assertEquals(FarmSendOutcome.STAGED, sender().stage(op))
        val payload = db.syncOutboxDao().findById(op)?.payloadJson
        for ((mode, attempts) in listOf(Mode.LOST_AFTER_RESERVE to 1, Mode.LOST_AFTER_CREATE to 2, Mode.ERROR_AFTER_CREATE to 3)) {
            server.mode = mode
            assertEquals(mode.name, FarmSendOutcome.OUTCOME_UNKNOWN, sender().send(op, lease))
            val current = db.syncOutboxDao().findById(op)!!
            assertEquals(OutboxStatus.FAILED, current.status)
            assertEquals(attempts, current.attemptCount)
            assertEquals(payload, current.payloadJson)
            assertEquals(SyncStatus.PENDING, db.farmDao().findById(farm)?.metadata?.syncStatus)
        }
        server.mode = Mode.NORMAL
        assertEquals(FarmSendOutcome.SYNCED, sender().send(op, lease))
        assertEquals(1, server.farmOwners.size)
        assertEquals(1, server.reservations.size)
        assertEquals(1, server.receipts.size)
        assertEquals(1, server.calls.filter { it.first == "reserve_local_farm_g1c" }.map { it.second }.distinct().size)
        assertEquals(1, server.calls.filter { it.first == "create_reserved_farm" }.map { it.second }.distinct().size)
    }

    @Test fun takenUuidBadReceiptOrInvalidPayloadIsBlockedAndNeverRetried() = runBlocking {
        val (taken, takenOp) = createFarm("Ajena")
        consent(taken, "Ajena")
        assertEquals(FarmSendOutcome.STAGED, sender().stage(takenOp))
        server.farmOwners[taken] = c
        assertEquals(FarmSendOutcome.CONFLICT, sender().send(takenOp, lease))
        assertEquals(OutboxStatus.BLOCKED, db.syncOutboxDao().findById(takenOp)?.status)
        assertEquals("FARM_CONFLICT", db.syncOutboxDao().findById(takenOp)?.lastErrorCode)
        assertEquals(SyncStatus.CONFLICT, db.farmDao().findById(taken)?.metadata?.syncStatus)
        assertEquals(c, server.farmOwners[taken])
        val calls = server.calls.size
        assertEquals(FarmSendOutcome.REJECTED, sender().send(takenOp, lease))
        assertEquals(calls, server.calls.size)

        val (forged, forgedOp) = createFarm("Recibo alterado")
        consent(forged, "Recibo alterado")
        assertEquals(FarmSendOutcome.STAGED, sender().stage(forgedOp))
        server.mode = Mode.BAD_RECEIPT_HASH
        assertEquals(FarmSendOutcome.CONFLICT, sender().send(forgedOp, lease))
        assertEquals("RECEIPT_CONFLICT", db.syncOutboxDao().findById(forgedOp)?.lastErrorCode)
        assertNull(db.farmDao().findById(forged)?.metadata?.remoteVersion)

        val (invalid, invalidOp) = createFarm("Rechazada")
        consent(invalid, "Rechazada")
        assertEquals(FarmSendOutcome.STAGED, sender().stage(invalidOp))
        server.mode = Mode.INVALID
        assertEquals(FarmSendOutcome.REJECTED, sender().send(invalidOp, lease))
        assertEquals("FARM_INVALID", db.syncOutboxDao().findById(invalidOp)?.lastErrorCode)
    }

    @Test fun logoutSwitchOfflineForbiddenAndConsentRevocationKeepTheOutbox() = runBlocking {
        val (farm, op) = createFarm("Finca Norte")
        consent(farm, "Finca Norte")
        assertEquals(FarmSendOutcome.STAGED, sender().stage(op))
        val pending = db.syncOutboxDao().findById(op)
        account.logout(false)
        assertEquals(FarmSendOutcome.NOT_AUTHORIZED, sender().send(op, lease))
        login(c)
        assertEquals(FarmSendOutcome.NOT_AUTHORIZED, sender().send(op, lease))
        assertTrue(server.calls.isEmpty())
        login(a)
        auth.offline = true
        assertEquals(FarmSendOutcome.OFFLINE, sender().send(op, lease))
        auth.offline = false
        server.mode = Mode.FORBIDDEN
        assertEquals(FarmSendOutcome.NOT_AUTHORIZED, sender().send(op, lease))
        server.mode = Mode.LOGOUT_DURING_REQUEST
        assertEquals(FarmSendOutcome.NOT_AUTHORIZED, sender().send(op, lease))
        assertEquals(pending, db.syncOutboxDao().findById(op))
        login(a)
        // A grant without write (e.g. downgraded or revoked) stops the send before any request.
        server.mode = Mode.NORMAL
        grant(a, "VIEWER", listOf("read"))
        val before = server.calls.size
        assertEquals(FarmSendOutcome.NOT_AUTHORIZED, sender().send(op, lease))
        assertEquals(before, server.calls.size)
        grant(a, "OWNER", listOf("read", "write", "manage_members"))
        // Local G1b revocation: same effect as LocalFarmLinkRepository.revokeConsentForFarm.
        ledger = ledger!!.copy(revocations = listOf(FarmLinkRevocation(UUID.randomUUID(), "$issuer|$a", farm, now, 1)))
        db.syncOutboxDao().updateStatusForFarm(farm, OutboxStatus.BLOCKED, now.toEpochMilli())
        assertEquals(FarmSendOutcome.CONSENT_REQUIRED, sender().send(op, lease))
        assertEquals(before, server.calls.size)
        assertEquals(pending?.payloadJson, db.syncOutboxDao().findById(op)?.payloadJson)
        assertEquals("Finca Norte", db.farmDao().findById(farm)?.name)
    }

    @Test fun offlineEditAfterStagingIsKeptPendingAndNeverOverwritten() = runBlocking {
        val (farm, op) = createFarm("Finca Norte")
        consent(farm, "Finca Norte")
        assertEquals(FarmSendOutcome.STAGED, sender().stage(op))
        assertTrue(farms().update(farm, FarmChanges("Finca Norte editada offline", municipality = "Bedmar")) is AppResult.Success)
        assertEquals(FarmSendOutcome.CONFIRMED_LOCAL_PENDING, sender().send(op, lease))
        val row = db.farmDao().findById(farm)!!
        assertEquals("Finca Norte editada offline", row.name)
        assertEquals(SyncStatus.PENDING, row.metadata.syncStatus)
        assertEquals(1L, row.metadata.remoteVersion)
        assertEquals(2L, row.metadata.version)
        val ops = db.syncOutboxDao().listForEntity(SyncEntityType.FARM, farm)
        assertEquals(listOf(OutboxOperation.CREATE to OutboxStatus.CONFIRMED, OutboxOperation.UPDATE to OutboxStatus.PENDING),
            ops.map { it.operation to it.status })
        assertEquals("Finca Norte", server.farmNames[farm])
        val report = ParentReceiveCoordinator(db, account, issuer, parents(farm, "Finca Norte")) { now }.receive(workspace)
        assertEquals(listOf(ParentConflict(ParentKind.FARM, farm, "local_pending")), report.conflicts)
        assertEquals("Finca Norte editada offline", db.farmDao().findById(farm)?.name)
    }

    private fun sender() = FarmSendCoordinator(db, account, issuer, server, { ledger }, { now })
    private fun selector() = LocalWorkspaceRepository(db, SystemAppClock(), UuidGenerator(), DefaultAppDispatchers(),
        RegionalContext.spainDefault(), account, issuer)
    private fun farms() = OfflineFirstFarmRepository(db, SystemAppClock(), UuidGenerator(), DefaultAppDispatchers(), selector())
    /** The app's own producer: an unbound FARM CREATE marker in the selected workspace. */
    private suspend fun createFarm(farmName: String, cover: UUID? = null): Pair<UUID, UUID> {
        val farm = (farms().create(NewFarm(workspace, farmName, municipality = "Bedmar", province = "Jaén",
            coverDocumentId = cover)) as AppResult.Success).value
        return farm to db.syncOutboxDao().listForEntity(SyncEntityType.FARM, farm).single().id
    }
    private fun consent(farm: UUID, farmName: String) {
        val entry = FarmLinkConsent(consentId, "$issuer|$a", listOf(FarmChoice(farm, workspace, farmName, 1)),
            UUID.randomUUID(), "b".repeat(64), now)
        ledger = ledger?.let { it.copy(consents = it.consents + entry.copy(operationId = UUID.randomUUID())) }
            ?: FarmLinkLedger(listOf(entry))
    }
    private suspend fun grant(id: UUID, role: String, permissions: List<String>) = db.workspaceAuthorizationCacheDao().upsert(
        WorkspaceAuthorizationCacheEntity(issuer, id.toString(),
            """{"contract_version":1,"issuer":"$issuer/auth/v1","subject":"$id","verified_at":"$now","token_expires_at":"${now.plusSeconds(3600)}","workspaces":[{"id":"$workspace","name":"Olivar A","role":"$role","permissions":[${permissions.joinToString(",") { "\"$it\"" }}],"grant_version":1,"authorization_expires_at":"${now.plusSeconds(900)}"}]}"""))
    private fun parents(farm: UUID, farmName: String) = DeliveryRpcTransport { _, _, _ ->
        DeliveryRpcResponse(200, """{"contract_version":1,"issuer":"$issuer/auth/v1","subject":"$a","workspace":{"id":"$workspace","name":"Olivar A","ownerUserId":"$a","countryCode":"ES","timezone":"Europe/Madrid","locale":"es-ES","currency":"EUR","createdAt":"1970-01-01T00:00:01Z","updatedAt":"1970-01-01T00:00:01Z","version":1},"farms":[{"id":"$farm","workspaceId":"$workspace","name":"$farmName","description":null,"municipality":"Bedmar","province":"Jaén","notes":null,"status":"ACTIVE","createdAt":"2026-11-01T10:00:01.234567+00:00","updatedAt":"2026-11-01T10:00:01.234567+00:00","version":1}],"parcels":[],"memberships":[]}""")
    }
    private fun login(id: UUID) {
        store.write(AccountRecord(session = AuthSession(id, "$id@example.test", "access", "refresh", now.plusSeconds(3600))))
        account = AccountRepository(auth, store) { now }
    }

    private enum class Mode { NORMAL, LOST_AFTER_RESERVE, LOST_AFTER_CREATE, ERROR_AFTER_CREATE, FORBIDDEN, INVALID,
        BAD_RECEIPT_HASH, LOGOUT_DURING_REQUEST }
    private data class Reservation(val request: ReservationRequest, val workspace: UUID, val receipt: UUID)
    private data class Receipt(val farm: UUID, val hash: String)

    /** reserve_local_farm_g1c + create_reserved_farm + check_sync_operations, owner-only, exact replays. */
    private inner class Server : DeliveryRpcTransport {
        var mode = Mode.NORMAL
        val calls = mutableListOf<Pair<String, String>>()
        val workspaceOwners = mutableMapOf<UUID, UUID>()
        val farmOwners = mutableMapOf<UUID, UUID>()
        val farmNames = mutableMapOf<UUID, String>()
        val reservations = mutableMapOf<Pair<UUID, UUID>, Reservation>()
        val receipts = mutableMapOf<Pair<UUID, UUID>, Receipt>()
        override suspend fun rpc(name: String, bodyJson: String, accessToken: String): DeliveryRpcResponse {
            calls += name to bodyJson
            val actor = (store.read().session ?: return DeliveryRpcResponse(401, "{}")).userId
            if (mode == Mode.FORBIDDEN) return DeliveryRpcResponse(403, """{"code":"42501"}""")
            val body = Json.parseToJsonElement(bodyJson) as JsonObject
            return when (name) {
                "reserve_local_farm_g1c" -> reserve(actor, body)
                "create_reserved_farm" -> create(actor, body)
                "check_sync_operations" -> {
                    val op = UUID.fromString((body.getValue("p_operation_ids") as JsonArray).single().jsonPrimitive.content)
                    val rows = receipts[actor to op]?.let { r ->
                        val hash = if (mode == Mode.BAD_RECEIPT_HASH) "0".repeat(64) else r.hash
                        """[{"operation_id":"$op","entity_id":"${r.farm}","entity_type":"FARM","remote_version":1,"payload_version":1,"payload_hash":"$hash"}]"""
                    } ?: "[]"
                    DeliveryRpcResponse(200, """{"success":true,"confirmed_operations":[],"confirmed_receipts":$rows}""")
                }
                else -> error("unexpected $name")
            }
        }
        private fun reserve(actor: UUID, body: JsonObject): DeliveryRpcResponse {
            val target = UUID.fromString(body.getValue("p_remote_workspace").jsonPrimitive.content)
            if (workspaceOwners[target] != actor) return DeliveryRpcResponse(403, """{"code":"42501"}""")
            val r = body.getValue("p_request") as JsonObject
            fun text(key: String) = r.getValue(key).jsonPrimitive.content
            val request = ReservationRequest(UUID.fromString(text("operationId")), UUID.fromString(text("farmId")),
                UUID.fromString(text("localWorkspaceId")), UUID.fromString(text("consentId")),
                r.getValue("consentTextVersion").jsonPrimitive.int, text("manifestSha256"), r.getValue("aggregateVersion").jsonPrimitive.long)
            val saved = reservations[actor to request.operationId]
            val reservation = if (saved != null) {
                if (saved.request != request || saved.workspace != target) return DeliveryRpcResponse(409, """{"code":"23505"}""")
                saved
            } else {
                if (request.farmId in farmOwners || reservations.values.any { it.request.farmId == request.farmId })
                    return DeliveryRpcResponse(409, """{"code":"23505"}""")
                Reservation(request, target, UUID.randomUUID()).also { reservations[actor to request.operationId] = it }
            }
            if (mode == Mode.LOST_AFTER_RESERVE) throw DeliveryRpcFailure(DeliveryRpcError.NETWORK)
            return DeliveryRpcResponse(200, """{"issuer":"$issuer","actorId":"$actor","operationId":"${request.operationId}","farmId":"${request.farmId}","payloadHash":"${request.payloadHash()}","remoteWorkspaceId":"$target","receiptId":"${reservation.receipt}"}""")
        }
        private suspend fun create(actor: UUID, body: JsonObject): DeliveryRpcResponse {
            if (mode == Mode.INVALID) return DeliveryRpcResponse(400, """{"code":"22023"}""")
            val op = UUID.fromString(body.getValue("p_operation_id").jsonPrimitive.content)
            val reservationOp = UUID.fromString(body.getValue("p_reservation_operation_id").jsonPrimitive.content)
            val receiptId = UUID.fromString(body.getValue("p_reservation_receipt_id").jsonPrimitive.content)
            val farm = body.getValue("p_farm") as JsonObject
            val id = UUID.fromString(farm.getValue("id").jsonPrimitive.content)
            val reservation = reservations[actor to reservationOp]
            if (reservation == null || reservation.receipt != receiptId || reservation.request.farmId != id ||
                workspaceOwners[reservation.workspace] != actor) return DeliveryRpcResponse(403, """{"code":"42501"}""")
            val hash = farmCreateHash(FarmCreateSnapshot(issuer, 1, reservation.workspace, id, reservation.request, farm), receiptId)
            val saved = receipts[actor to op]
            val replay = saved != null
            if (saved != null) {
                if (saved.farm != id || saved.hash != hash) return DeliveryRpcResponse(409, """{"code":"23505"}""")
                if (farmOwners[id] != actor) return DeliveryRpcResponse(403, """{"code":"42501"}""")
            } else {
                if (id in farmOwners) return DeliveryRpcResponse(409, """{"code":"23505"}""")
                farmOwners[id] = actor; farmNames[id] = farm.getValue("name").jsonPrimitive.content
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
