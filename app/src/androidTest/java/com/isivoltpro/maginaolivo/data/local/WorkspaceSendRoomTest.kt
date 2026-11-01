package com.isivoltpro.maginaolivo.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.isivoltpro.maginaolivo.data.local.entity.WorkspaceAuthorizationCacheEntity
import com.isivoltpro.maginaolivo.data.local.model.OutboxOperation
import com.isivoltpro.maginaolivo.data.local.model.OutboxStatus
import com.isivoltpro.maginaolivo.data.local.model.SyncEntityType
import com.isivoltpro.maginaolivo.data.local.model.SyncStatus
import com.isivoltpro.maginaolivo.data.repository.sync.DeliveryDispatchLease
import com.isivoltpro.maginaolivo.data.repository.sync.DeliveryRpcError
import com.isivoltpro.maginaolivo.data.repository.sync.DeliveryRpcFailure
import com.isivoltpro.maginaolivo.data.repository.sync.DeliveryRpcResponse
import com.isivoltpro.maginaolivo.data.repository.sync.DeliveryRpcTransport
import com.isivoltpro.maginaolivo.data.repository.sync.ParentReceiveCoordinator
import com.isivoltpro.maginaolivo.data.repository.sync.ParentReceiveOutcome
import com.isivoltpro.maginaolivo.data.repository.sync.WorkspaceDraft
import com.isivoltpro.maginaolivo.data.repository.sync.WorkspaceSendCoordinator
import com.isivoltpro.maginaolivo.data.repository.sync.WorkspaceSendOutcome
import com.isivoltpro.maginaolivo.data.repository.sync.parseWorkspaceCreateEnvelope
import com.isivoltpro.maginaolivo.feature.account.AccountAuthApi
import com.isivoltpro.maginaolivo.feature.account.AccountRecord
import com.isivoltpro.maginaolivo.feature.account.AccountRepository
import com.isivoltpro.maginaolivo.feature.account.AuthFailure
import com.isivoltpro.maginaolivo.feature.account.AuthSession
import com.isivoltpro.maginaolivo.feature.account.MemoryAccountSessionStore
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Room-only G2.6 WORKSPACE send checks against a fake that models the reviewed RPC semantics. */
@RunWith(AndroidJUnit4::class)
class WorkspaceSendRoomTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "g26-workspace-send-room"
    private val issuer = "https://project.example"
    private val a = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
    private val c = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc")
    private val draft = WorkspaceDraft("Olivar A", "ES", "Europe/Madrid", "es-ES", "EUR")
    private var now = Instant.parse("2026-11-01T10:00:00Z")
    private val store = MemoryAccountSessionStore()
    private val auth = FakeAuth()
    private val server = Server()
    private lateinit var db: MaginaOlivoDatabase
    private lateinit var account: AccountRepository
    private lateinit var lease: DeliveryDispatchLease

    @Before fun setup() {
        context.deleteDatabase(name)
        db = MaginaOlivoDatabase.create(context, name)
        lease = DeliveryDispatchLease.tryAcquire(File(context.noBackupFilesDir, "g26-workspace-send-room"))!!
        login(a)
    }

    @After fun close() {
        if (::lease.isInitialized) lease.close()
        if (::db.isInitialized) db.close()
        context.deleteDatabase(name)
    }

    @Test fun createOwnedBindsOnlyToTheSignedInAccountAndStaysHiddenWhenSignedOut() = runBlocking {
        assertEquals(WorkspaceSendOutcome.REJECTED, sender().createOwned(draft.copy(currency = "euro")).outcome)
        val staged = sender().createOwned(draft)
        assertEquals(WorkspaceSendOutcome.STAGED, staged.outcome)
        val row = db.workspaceDao().findById(staged.workspaceId!!)!!
        assertEquals(a, row.ownerUserId)
        assertEquals(SyncStatus.PENDING, row.metadata.syncStatus)
        assertNull(row.metadata.remoteVersion)
        val op = db.syncOutboxDao().findById(staged.operationId!!)!!
        assertEquals(OutboxOperation.CREATE, op.operation)
        assertEquals(OutboxStatus.PENDING, op.status)
        assertEquals(a.toString(), op.accountId)
        assertEquals(row.id, parseWorkspaceCreateEnvelope(op.payloadJson!!)?.workspaceId)
        // An account-bound workspace is never offered as unbound local data after logout.
        assertTrue(db.workspaceDao().listUnboundLocal().none { it.id == row.id })
        account.logout(false)
        assertTrue(db.workspaceDao().listUnboundLocal().none { it.id == row.id })
        assertEquals(WorkspaceSendOutcome.NOT_AUTHORIZED, sender().createOwned(draft).outcome)
        assertEquals(listOf(1, 1), counts())
        assertTrue(server.calls.isEmpty())
    }

    @Test fun sendKeepsTheUuidConfirmsTheCorrelatedReceiptAndReceivesBackWithoutConflict() = runBlocking {
        val staged = sender().createOwned(draft)
        assertEquals(WorkspaceSendOutcome.SYNCED, sender().send(staged.operationId!!, lease))
        assertEquals(listOf("create_own_workspace", "check_sync_operations"), server.calls.map { it.first })
        val sent = (Json.parseToJsonElement(server.calls.first().second) as JsonObject)
        assertEquals(staged.operationId.toString(), sent.getValue("p_operation_id").jsonPrimitive.content)
        assertEquals(staged.workspaceId.toString(),
            (sent.getValue("p_workspace") as JsonObject).getValue("id").jsonPrimitive.content)
        assertEquals(a, server.owners[staged.workspaceId])
        val row = db.workspaceDao().findById(staged.workspaceId!!)!!
        assertEquals(SyncStatus.SYNCED, row.metadata.syncStatus)
        assertEquals(1L, row.metadata.remoteVersion)
        assertEquals(1L, row.metadata.version)
        assertEquals(OutboxStatus.CONFIRMED, db.syncOutboxDao().findById(staged.operationId)?.status)

        val calls = server.calls.size
        assertEquals(WorkspaceSendOutcome.ALREADY_CONFIRMED, sender().send(staged.operationId, lease))
        db.close(); db = MaginaOlivoDatabase.create(context, name)
        assertEquals(WorkspaceSendOutcome.ALREADY_CONFIRMED, sender().send(staged.operationId, lease))
        assertEquals(calls, server.calls.size)

        // The server assigned its own instants; A adopts them once and is then current.
        db.workspaceAuthorizationCacheDao().upsert(WorkspaceAuthorizationCacheEntity(issuer, a.toString(),
            """{"contract_version":1,"issuer":"$issuer/auth/v1","subject":"$a","verified_at":"$now","token_expires_at":"${now.plusSeconds(3600)}","workspaces":[{"id":"${row.id}","name":"Olivar A","role":"OWNER","permissions":["read","write","manage_members"],"grant_version":1,"authorization_expires_at":"${now.plusSeconds(900)}"}]}"""))
        val parents = DeliveryRpcTransport { _, _, _ -> DeliveryRpcResponse(200,
            """{"contract_version":1,"issuer":"$issuer/auth/v1","subject":"$a","workspace":{"id":"${row.id}","name":"Olivar A","ownerUserId":"$a","countryCode":"ES","timezone":"Europe/Madrid","locale":"es-ES","currency":"EUR","createdAt":"2026-11-01T10:00:01.234567+00:00","updatedAt":"2026-11-01T10:00:01.234567+00:00","version":1},"farms":[],"parcels":[],"memberships":[]}""") }
        val first = ParentReceiveCoordinator(db, account, issuer, parents) { now }.receive(row.id)
        assertEquals(ParentReceiveOutcome.IMPORTED, first.outcome)
        assertEquals(1, first.updated)
        assertEquals(Instant.parse("2026-11-01T10:00:01.234Z"), db.workspaceDao().findById(row.id)?.metadata?.updatedAt)
        assertEquals(ParentReceiveOutcome.ALREADY_CURRENT,
            ParentReceiveCoordinator(db, account, issuer, parents) { now }.receive(row.id).outcome)
        assertEquals(a, db.workspaceDao().findById(row.id)?.ownerUserId)
        assertEquals(listOf(1, 1), counts())
    }

    @Test fun lostResponseOrServerErrorIsReplayedWithTheSameOperationAndPayload() = runBlocking {
        val staged = sender().createOwned(draft)
        val before = db.syncOutboxDao().findById(staged.operationId!!)!!
        server.mode = Mode.LOST_AFTER_COMMIT
        assertEquals(WorkspaceSendOutcome.OUTCOME_UNKNOWN, sender().send(staged.operationId, lease))
        var op = db.syncOutboxDao().findById(staged.operationId)!!
        assertEquals(OutboxStatus.FAILED, op.status)
        assertEquals("OUTCOME_UNKNOWN", op.lastErrorCode)
        assertEquals(1, op.attemptCount)
        assertEquals(before.payloadJson, op.payloadJson)
        assertEquals(SyncStatus.PENDING, db.workspaceDao().findById(staged.workspaceId!!)?.metadata?.syncStatus)
        server.mode = Mode.ERROR_AFTER_COMMIT
        assertEquals(WorkspaceSendOutcome.OUTCOME_UNKNOWN, sender().send(staged.operationId, lease))
        assertEquals(2, db.syncOutboxDao().findById(staged.operationId)?.attemptCount)
        server.mode = Mode.NORMAL
        assertEquals(WorkspaceSendOutcome.SYNCED, sender().send(staged.operationId, lease))
        op = db.syncOutboxDao().findById(staged.operationId)!!
        assertEquals(OutboxStatus.CONFIRMED, op.status)
        assertEquals(before.payloadJson, op.payloadJson)
        val creates = server.calls.filter { it.first == "create_own_workspace" }
        assertEquals(3, creates.size)
        assertEquals(1, creates.map { it.second }.distinct().size)
        assertEquals(1, server.owners.size)
        assertEquals(1, server.receipts.size)
        assertEquals(listOf(1, 1), counts())
    }

    @Test fun takenUuidBadReceiptOrInvalidPayloadIsBlockedAndNeverRetried() = runBlocking {
        val taken = sender().createOwned(draft)
        server.owners[taken.workspaceId!!] = c
        assertEquals(WorkspaceSendOutcome.CONFLICT, sender().send(taken.operationId!!, lease))
        val op = db.syncOutboxDao().findById(taken.operationId)!!
        assertEquals(OutboxStatus.BLOCKED, op.status)
        assertEquals("WORKSPACE_CONFLICT", op.lastErrorCode)
        assertNotNull(parseWorkspaceCreateEnvelope(op.payloadJson!!))
        assertEquals(SyncStatus.CONFLICT, db.workspaceDao().findById(taken.workspaceId)?.metadata?.syncStatus)
        assertEquals(c, server.owners[taken.workspaceId])
        val calls = server.calls.size
        assertEquals(WorkspaceSendOutcome.REJECTED, sender().send(taken.operationId, lease))
        assertEquals(calls, server.calls.size)

        val forged = sender().createOwned(draft.copy(name = "Recibo alterado"))
        server.mode = Mode.BAD_RECEIPT_HASH
        assertEquals(WorkspaceSendOutcome.CONFLICT, sender().send(forged.operationId!!, lease))
        assertEquals("RECEIPT_CONFLICT", db.syncOutboxDao().findById(forged.operationId)?.lastErrorCode)
        assertEquals(null, db.workspaceDao().findById(forged.workspaceId!!)?.metadata?.remoteVersion)

        val invalid = sender().createOwned(draft.copy(name = "Rechazado"))
        server.mode = Mode.INVALID
        assertEquals(WorkspaceSendOutcome.REJECTED, sender().send(invalid.operationId!!, lease))
        assertEquals("WORKSPACE_INVALID", db.syncOutboxDao().findById(invalid.operationId)?.lastErrorCode)
        assertEquals(OutboxStatus.BLOCKED, db.syncOutboxDao().findById(invalid.operationId)?.status)
        assertEquals(listOf(3, 3), counts())
    }

    @Test fun historicalWorkspaceNeedsConsentAndIsNeverSent() = runBlocking {
        val legacy = UUID.randomUUID(); val legacyOp = UUID.randomUUID()
        val forged = UUID.randomUUID(); val forgedOp = UUID.randomUUID()
        val sql = db.openHelper.writableDatabase
        // Shape written by LocalWorkspaceRepository while signed out: random owner, unbound outbox.
        sql.execSQL("INSERT INTO workspaces(id,name,owner_user_id,country_code,timezone,locale,currency,created_at,updated_at,version,sync_status) VALUES ('$legacy','Mi olivar','${UUID.randomUUID()}','ES','Europe/Madrid','es-ES','EUR',1000,1000,1,'PENDING')")
        sql.execSQL("INSERT INTO sync_outbox(id,entity_type,entity_id,operation,payload_version,status,attempt_count,created_at,updated_at) VALUES ('$legacyOp','WORKSPACE','$legacy','CREATE',1,'PENDING',0,1000,1000)")
        // Even a bound outbox cannot attribute a workspace whose local owner is not this account.
        sql.execSQL("INSERT INTO workspaces(id,name,owner_user_id,country_code,timezone,locale,currency,created_at,updated_at,version,sync_status) VALUES ('$forged','Ajeno','${UUID.randomUUID()}','ES','Europe/Madrid','es-ES','EUR',1000,1000,1,'PENDING')")
        val payload = """{"issuer":"$issuer","localVersion":1,"workspace":{"id":"$forged","name":"Ajeno","countryCode":"ES","timezone":"Europe/Madrid","locale":"es-ES","currency":"EUR"}}"""
        sql.execSQL("INSERT INTO sync_outbox(id,entity_type,entity_id,operation,payload_version,status,attempt_count,created_at,updated_at,account_id,payload_json) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
            arrayOf<Any?>(forgedOp.toString(), "WORKSPACE", forged.toString(), "CREATE", 1, "PENDING", 0, 1000, 1000, a.toString(), payload))
        val before = snapshot()
        assertEquals(WorkspaceSendOutcome.CONSENT_REQUIRED, sender().send(legacyOp, lease))
        assertEquals(WorkspaceSendOutcome.CONSENT_REQUIRED, sender().send(forgedOp, lease))
        assertEquals(before, snapshot())
        assertTrue(server.calls.isEmpty())
        assertTrue(db.workspaceDao().listUnboundLocal().any { it.id == legacy })
    }

    @Test fun logoutAccountSwitchRevocationAndOfflineKeepTheOutbox() = runBlocking {
        val staged = sender().createOwned(draft)
        val op = staged.operationId!!
        val pending = db.syncOutboxDao().findById(op)
        account.logout(false)
        assertEquals(WorkspaceSendOutcome.NOT_AUTHORIZED, sender().send(op, lease))
        login(c)
        assertEquals(WorkspaceSendOutcome.NOT_AUTHORIZED, sender().send(op, lease))
        assertTrue(server.calls.isEmpty())
        login(a)
        auth.offline = true
        assertEquals(WorkspaceSendOutcome.OFFLINE, sender().send(op, lease))
        auth.offline = false
        server.mode = Mode.FORBIDDEN
        assertEquals(WorkspaceSendOutcome.NOT_AUTHORIZED, sender().send(op, lease))
        assertEquals(pending, db.syncOutboxDao().findById(op))
        // Logout while the request is in flight: the server committed, Room is not touched.
        server.mode = Mode.LOGOUT_DURING_REQUEST
        assertEquals(WorkspaceSendOutcome.NOT_AUTHORIZED, sender().send(op, lease))
        assertEquals(pending, db.syncOutboxDao().findById(op))
        assertEquals(SyncStatus.PENDING, db.workspaceDao().findById(staged.workspaceId!!)?.metadata?.syncStatus)
        login(a)
        server.mode = Mode.NORMAL
        assertEquals(WorkspaceSendOutcome.SYNCED, sender().send(op, lease))
        assertEquals(1, server.owners.size)
        assertEquals(listOf(1, 1), counts())
    }

    @Test fun duplicateOpenCreateIsRejectedWithoutNetwork() = runBlocking {
        val staged = sender().createOwned(draft)
        val extra = UUID.randomUUID()
        db.openHelper.writableDatabase.execSQL("INSERT INTO sync_outbox(id,entity_type,entity_id,operation,payload_version,status,attempt_count,created_at,updated_at) VALUES ('$extra','WORKSPACE','${staged.workspaceId}','CREATE',1,'PENDING',0,1000,1000)")
        assertEquals(WorkspaceSendOutcome.REJECTED, sender().send(staged.operationId!!, lease))
        assertTrue(server.calls.isEmpty())
        assertEquals(2, db.syncOutboxDao().listForEntity(SyncEntityType.WORKSPACE, staged.workspaceId!!).size)
    }

    private fun sender() = WorkspaceSendCoordinator(db, account, issuer, server, { now })
    private fun login(id: UUID) {
        store.write(AccountRecord(session = AuthSession(id, "$id@example.test", "access", "refresh", now.plusSeconds(3600))))
        account = AccountRepository(auth, store) { now }
    }
    private fun count(table: String) = db.openHelper.readableDatabase.query("SELECT count(*) FROM $table").use { it.moveToFirst(); it.getInt(0) }
    private fun counts() = listOf("workspaces", "sync_outbox").map(::count)
    private fun snapshot(): List<String> = listOf("workspaces", "sync_outbox").flatMap { table ->
        db.openHelper.readableDatabase.query("SELECT * FROM $table ORDER BY id").use { cursor ->
            buildList { while (cursor.moveToNext()) add((0 until cursor.columnCount).joinToString("|") { cursor.getString(it) ?: "∅" }) }
        }
    }

    private enum class Mode { NORMAL, LOST_AFTER_COMMIT, ERROR_AFTER_COMMIT, FORBIDDEN, INVALID, BAD_RECEIPT_HASH, LOGOUT_DURING_REQUEST }
    private data class Receipt(val entity: UUID, val hash: String)

    /** create_own_workspace + check_sync_operations: create-only, per-account receipts, replay re-checks ownership. */
    private inner class Server : DeliveryRpcTransport {
        var mode = Mode.NORMAL
        val calls = mutableListOf<Pair<String, String>>()
        val owners = mutableMapOf<UUID, UUID>()
        val receipts = mutableMapOf<Pair<UUID, UUID>, Receipt>()
        override suspend fun rpc(name: String, bodyJson: String, accessToken: String): DeliveryRpcResponse {
            calls += name to bodyJson
            val actor = (store.read().session ?: return DeliveryRpcResponse(401, "{}")).userId
            val body = Json.parseToJsonElement(bodyJson) as JsonObject
            return when (name) {
                "create_own_workspace" -> create(actor, body)
                "check_sync_operations" -> {
                    val op = UUID.fromString(((body.getValue("p_operation_ids") as JsonArray).single()).jsonPrimitive.content)
                    val rows = receipts[actor to op]?.let { r ->
                        val hash = if (mode == Mode.BAD_RECEIPT_HASH) "0".repeat(64) else r.hash
                        """[{"operation_id":"$op","entity_id":"${r.entity}","entity_type":"WORKSPACE","remote_version":1,"payload_version":1,"payload_hash":"$hash"}]"""
                    } ?: "[]"
                    DeliveryRpcResponse(200, """{"success":true,"confirmed_operations":[],"confirmed_receipts":$rows}""")
                }
                else -> error("unexpected $name")
            }
        }
        private suspend fun create(actor: UUID, body: JsonObject): DeliveryRpcResponse {
            if (mode == Mode.FORBIDDEN) return DeliveryRpcResponse(403, """{"code":"42501"}""")
            if (mode == Mode.INVALID) return DeliveryRpcResponse(400, """{"code":"22023"}""")
            val op = UUID.fromString(body.getValue("p_operation_id").jsonPrimitive.content)
            val workspace = body.getValue("p_workspace") as JsonObject
            val id = UUID.fromString(workspace.getValue("id").jsonPrimitive.content)
            val hash = parseWorkspaceCreateEnvelope("""{"issuer":"x","localVersion":1,"workspace":$workspace}""")!!.hash
            val saved = receipts[actor to op]
            val replay = saved != null
            if (saved != null) {
                if (saved.entity != id || saved.hash != hash) return DeliveryRpcResponse(409, """{"code":"23505"}""")
                if (owners[id] != actor) return DeliveryRpcResponse(403, """{"code":"42501"}""")
            } else {
                if (id in owners) return DeliveryRpcResponse(409, """{"code":"23505"}""")
                owners[id] = actor; receipts[actor to op] = Receipt(id, hash)
            }
            return when (mode) {
                Mode.LOST_AFTER_COMMIT -> throw DeliveryRpcFailure(DeliveryRpcError.NETWORK)
                Mode.ERROR_AFTER_COMMIT -> DeliveryRpcResponse(503, "{}")
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
