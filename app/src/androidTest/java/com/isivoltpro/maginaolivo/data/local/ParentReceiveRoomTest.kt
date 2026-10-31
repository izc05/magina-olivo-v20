package com.isivoltpro.maginaolivo.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.isivoltpro.maginaolivo.data.local.entity.WorkspaceAuthorizationCacheEntity
import com.isivoltpro.maginaolivo.data.local.model.OutboxStatus
import com.isivoltpro.maginaolivo.data.local.model.SyncEntityType
import com.isivoltpro.maginaolivo.data.local.model.SyncStatus
import com.isivoltpro.maginaolivo.data.repository.sync.DeliveryRpcError
import com.isivoltpro.maginaolivo.data.repository.sync.DeliveryRpcFailure
import com.isivoltpro.maginaolivo.data.repository.sync.DeliveryRpcResponse
import com.isivoltpro.maginaolivo.data.repository.sync.DeliveryRpcTransport
import com.isivoltpro.maginaolivo.data.repository.sync.ParentConflict
import com.isivoltpro.maginaolivo.data.repository.sync.ParentKind
import com.isivoltpro.maginaolivo.data.repository.sync.ParentReceiveCoordinator
import com.isivoltpro.maginaolivo.data.repository.sync.ParentReceiveOutcome
import com.isivoltpro.maginaolivo.feature.account.AccountAuthApi
import com.isivoltpro.maginaolivo.feature.account.AccountRecord
import com.isivoltpro.maginaolivo.feature.account.AccountRepository
import com.isivoltpro.maginaolivo.feature.account.AuthSession
import com.isivoltpro.maginaolivo.feature.account.MemoryAccountSessionStore
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Room-only G2.6 parent receiver checks; the server graph is a fake transport, never a network call. */
@RunWith(AndroidJUnit4::class)
class ParentReceiveRoomTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "g26-parent-receive-room"
    private val issuer = "https://project.example"
    private val a = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
    private val c = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc")
    private val workspace = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val otherWorkspace = UUID.fromString("19999999-9999-9999-9999-999999999999")
    private val farm1 = UUID.fromString("22222222-2222-2222-2222-222222222221")
    private val farm2 = UUID.fromString("22222222-2222-2222-2222-222222222222")
    private val parcel1 = UUID.fromString("33333333-3333-3333-3333-333333333331")
    private val parcel2 = UUID.fromString("33333333-3333-3333-3333-333333333332")
    private val m1 = UUID.fromString("44444444-4444-4444-4444-444444444441")
    private val m2 = UUID.fromString("44444444-4444-4444-4444-444444444442")
    private var now = Instant.parse("2026-10-31T10:00:00Z")
    private val store = MemoryAccountSessionStore()
    private lateinit var db: MaginaOlivoDatabase
    private lateinit var account: AccountRepository
    private val server = Server()

    @Before fun setup() = runBlocking {
        context.deleteDatabase(name)
        db = MaginaOlivoDatabase.create(context, name)
        login()
        grant(listOf(workspace))
    }

    @After fun close() {
        if (::db.isInitialized) db.close()
        context.deleteDatabase(name)
    }

    @Test fun freshDeviceImportsWholeGraphWithOriginalIdsAndNoOutbox() = runBlocking {
        val report = receiver().receive(workspace)
        assertEquals(ParentReceiveOutcome.IMPORTED, report.outcome)
        assertEquals(7, report.inserted)
        assertEquals(listOf(1, 2, 2, 2, 0), counts())
        val ws = db.workspaceDao().findById(workspace)!!
        assertEquals(a, ws.ownerUserId)
        assertEquals(SyncStatus.SYNCED, ws.metadata.syncStatus)
        assertEquals(1L, ws.metadata.remoteVersion)
        assertEquals("Finca Norte", db.farmDao().findById(farm1)?.name)
        assertEquals(farm1, db.parcelDao().findCurrentMembership(parcel1)?.farmId)
        assertEquals(Instant.parse("2026-01-01T00:00:00.007Z"), db.parcelDao().findCurrentMembership(parcel1)?.validFrom)
        // Synced remote rows are not unbound local data: after logout they are hidden, not offered.
        assertTrue(db.workspaceDao().listUnboundLocal().none { it.id == workspace })
    }

    @Test fun repeatedReceiveAndRestartWriteNothing() = runBlocking {
        assertEquals(ParentReceiveOutcome.IMPORTED, receiver().receive(workspace).outcome)
        val first = snapshot()
        val again = receiver().receive(workspace)
        assertEquals(ParentReceiveOutcome.ALREADY_CURRENT, again.outcome)
        assertEquals(7, again.unchanged)
        assertEquals(first, snapshot())
        db.close()
        db = MaginaOlivoDatabase.create(context, name)
        assertEquals(first, snapshot())
        assertEquals(ParentReceiveOutcome.ALREADY_CURRENT, receiver().receive(workspace).outcome)
        assertEquals(listOf(1, 2, 2, 2, 0), counts())
    }

    @Test fun newerRemoteUpdatesButLocalPendingEditAndOutboxSurvive() = runBlocking {
        assertEquals(ParentReceiveOutcome.IMPORTED, receiver().receive(workspace).outcome)
        val local = db.farmDao().findById(farm2)!!
        db.farmDao().upsert(local.copy(name = "Edición offline de B", metadata = local.metadata.copy(
            version = 2, syncStatus = SyncStatus.PENDING)))
        val pending = UUID.randomUUID()
        db.openHelper.writableDatabase.execSQL("INSERT INTO sync_outbox(id,entity_type,entity_id,operation,payload_version,status,attempt_count,created_at,updated_at) VALUES ('$pending','FARM','$farm2','UPDATE',1,'PENDING',0,2000,2000)")
        server.farm1Name = "Finca Norte II"; server.farm1Version = 2
        server.farm2Name = "Finca Sur remota"; server.farm2Version = 2
        server.parcel2Version = 2; server.parcel2Name = "Parcela 2 ampliada"
        val report = receiver().receive(workspace)
        assertEquals(ParentReceiveOutcome.CONFLICT, report.outcome)
        assertEquals(listOf(ParentConflict(ParentKind.FARM, farm2, "local_pending")), report.conflicts)
        assertEquals(2, report.updated)
        assertEquals("Finca Norte II", db.farmDao().findById(farm1)?.name)
        assertEquals(2L, db.farmDao().findById(farm1)?.metadata?.remoteVersion)
        assertEquals("Parcela 2 ampliada", db.parcelDao().findById(parcel2)?.displayName)
        assertEquals("Edición offline de B", db.farmDao().findById(farm2)?.name)
        assertEquals(SyncStatus.PENDING, db.farmDao().findById(farm2)?.metadata?.syncStatus)
        assertEquals(OutboxStatus.PENDING, db.syncOutboxDao().findById(pending)?.status)
        assertEquals(1, db.syncOutboxDao().listForEntity(SyncEntityType.FARM, farm2).size)
        assertEquals(listOf(1, 2, 2, 2, 1), counts())
    }

    @Test fun staleOrDivergentRemoteNeverOverwrites() = runBlocking {
        server.farm1Version = 3
        assertEquals(ParentReceiveOutcome.IMPORTED, receiver().receive(workspace).outcome)
        server.farm1Version = 2; server.farm1Name = "Rollback"
        assertEquals(listOf(ParentConflict(ParentKind.FARM, farm1, "stale_remote")), receiver().receive(workspace).conflicts)
        server.farm1Version = 3; server.farm1Name = "Mismo número, otro contenido"
        assertEquals(listOf(ParentConflict(ParentKind.FARM, farm1, "divergent_same_version")),
            receiver().receive(workspace).conflicts)
        assertEquals("Finca Norte", db.farmDao().findById(farm1)?.name)
        assertEquals(3L, db.farmDao().findById(farm1)?.metadata?.remoteVersion)
    }

    @Test fun localOwnerIsNeverRewrittenAndForeignIdCollisionsStayPut() = runBlocking {
        val localOwner = UUID.randomUUID()
        val sql = db.openHelper.writableDatabase
        sql.execSQL("INSERT INTO workspaces(id,name,owner_user_id,country_code,timezone,locale,currency,created_at,updated_at,version,sync_status,remote_version) VALUES ('$workspace','Olivar A','$localOwner','ES','Europe/Madrid','es-ES','EUR',1000,1000,1,'SYNCED',1)")
        sql.execSQL("INSERT INTO workspaces(id,name,owner_user_id,country_code,timezone,locale,currency,created_at,updated_at,version,sync_status) VALUES ('$otherWorkspace','Mi olivar','${UUID.randomUUID()}','ES','Europe/Madrid','es-ES','EUR',1000,1000,1,'LOCAL_ONLY')")
        sql.execSQL("INSERT INTO farms(id,workspace_id,name,status,created_at,updated_at,version,sync_status) VALUES ('$farm1','$otherWorkspace','Finca local','ACTIVE',1000,1000,1,'LOCAL_ONLY')")
        server.workspaceVersion = 2; server.workspaceName = "Olivar A renombrado"
        val report = receiver().receive(workspace)
        assertEquals(ParentReceiveOutcome.CONFLICT, report.outcome)
        assertEquals(setOf(ParentConflict(ParentKind.FARM, farm1, "foreign_workspace"),
            ParentConflict(ParentKind.MEMBERSHIP, m1, "parent_unavailable")), report.conflicts.toSet())
        val ws = db.workspaceDao().findById(workspace)!!
        assertEquals("Olivar A renombrado", ws.name)
        assertEquals(localOwner, ws.ownerUserId)
        assertEquals(otherWorkspace, db.farmDao().findById(farm1)?.workspaceId)
        assertEquals("Finca local", db.farmDao().findById(farm1)?.name)
        assertEquals(null, db.parcelDao().listMemberships(parcel1).singleOrNull { it.id == m1 })
        assertEquals(farm2, db.parcelDao().findCurrentMembership(parcel2)?.farmId)
    }

    @Test fun duplicateLocalPeriodIsAConflictNotAConstraintCrash() = runBlocking {
        server.withMembership1 = false
        assertEquals(ParentReceiveOutcome.IMPORTED, receiver().receive(workspace).outcome)
        val local = UUID.randomUUID()
        db.openHelper.writableDatabase.execSQL("INSERT INTO farm_parcel_memberships(id,workspace_id,farm_id,parcel_id,valid_from,created_at,updated_at,version,sync_status) VALUES ('$local','$workspace','$farm1','$parcel1',${Instant.parse("2026-01-01T00:00:00.007Z").toEpochMilli()},1000,1000,1,'PENDING')")
        server.withMembership1 = true
        val report = receiver().receive(workspace)
        assertEquals(listOf(ParentConflict(ParentKind.MEMBERSHIP, m1, "duplicate_period")), report.conflicts)
        assertEquals(listOf(local), db.parcelDao().listMemberships(parcel1).map { it.id })
    }

    @Test fun deniedOfflineOrMalformedReceiveLeavesRoomUntouched() = runBlocking {
        db.workspaceAuthorizationCacheDao().upsert(WorkspaceAuthorizationCacheEntity(issuer, a.toString(),
            contract(a, listOf(otherWorkspace))))
        assertEquals(ParentReceiveOutcome.NOT_AUTHORIZED, receiver().receive(workspace).outcome)
        grant(listOf(workspace))
        server.subject = c
        assertEquals(ParentReceiveOutcome.NOT_AUTHORIZED, receiver().receive(workspace).outcome)
        server.subject = a
        server.mode = Mode.FORBIDDEN
        assertEquals(ParentReceiveOutcome.NOT_AUTHORIZED, receiver().receive(workspace).outcome)
        server.mode = Mode.OFFLINE
        assertEquals(ParentReceiveOutcome.OFFLINE, receiver().receive(workspace).outcome)
        server.mode = Mode.MALFORMED
        assertEquals(ParentReceiveOutcome.REJECTED, receiver().receive(workspace).outcome)
        server.mode = Mode.LOGOUT_DURING_READ
        assertEquals(ParentReceiveOutcome.NOT_AUTHORIZED, receiver().receive(workspace).outcome)
        assertEquals(ParentReceiveOutcome.NOT_AUTHORIZED, receiver().receive(workspace).outcome)
        assertEquals(listOf(0, 0, 0, 0, 0), counts())
        login(); grant(listOf(workspace))
        server.mode = Mode.NORMAL
        now = now.plusSeconds(14 * 60)
        assertEquals("expired grant cache", ParentReceiveOutcome.NOT_AUTHORIZED, receiver().receive(workspace).outcome)
        assertEquals(listOf(0, 0, 0, 0, 0), counts())
    }

    @Test fun revocationKeepsReceivedDataButStopsFurtherReceive() = runBlocking {
        assertEquals(ParentReceiveOutcome.IMPORTED, receiver().receive(workspace).outcome)
        val before = snapshot()
        grant(emptyList())
        server.farm1Version = 2; server.farm1Name = "Después de revocar"
        assertEquals(ParentReceiveOutcome.NOT_AUTHORIZED, receiver().receive(workspace).outcome)
        server.mode = Mode.FORBIDDEN; grant(listOf(workspace))
        assertEquals(ParentReceiveOutcome.NOT_AUTHORIZED, receiver().receive(workspace).outcome)
        assertEquals(before, snapshot())
    }

    private fun receiver() = ParentReceiveCoordinator(db, account, issuer, server) { now }
    private fun login() {
        store.write(AccountRecord(session = AuthSession(a, "a@example.test", "access", "refresh", now.plusSeconds(3600))))
        account = AccountRepository(FakeAuth, store) { now }
    }
    private suspend fun grant(ids: List<UUID>) = db.workspaceAuthorizationCacheDao()
        .upsert(WorkspaceAuthorizationCacheEntity(issuer, a.toString(), contract(a, ids)))
    private fun contract(id: UUID, ids: List<UUID>) = """{"contract_version":1,"issuer":"$issuer/auth/v1","subject":"$id","verified_at":"$now","token_expires_at":"${now.plusSeconds(3600)}","workspaces":[${ids.joinToString(",") { """{"id":"$it","name":"QA","role":"VIEWER","permissions":["read"],"grant_version":1,"authorization_expires_at":"${now.plusSeconds(900)}"}""" }}]}"""
    private fun count(table: String) = db.openHelper.readableDatabase.query("SELECT count(*) FROM $table").use { it.moveToFirst(); it.getInt(0) }
    private fun counts() = listOf("workspaces", "farms", "parcels", "farm_parcel_memberships", "sync_outbox").map(::count)
    private fun snapshot(): List<String> = listOf("workspaces", "farms", "parcels", "farm_parcel_memberships", "sync_outbox").flatMap { table ->
        db.openHelper.readableDatabase.query("SELECT * FROM $table ORDER BY id").use { cursor ->
            buildList { while (cursor.moveToNext()) add((0 until cursor.columnCount).joinToString("|") { cursor.getString(it) ?: "∅" }) }
        }
    }

    private enum class Mode { NORMAL, FORBIDDEN, OFFLINE, MALFORMED, LOGOUT_DURING_READ }
    private inner class Server : DeliveryRpcTransport {
        var mode = Mode.NORMAL
        var subject = a
        var workspaceVersion = 1; var workspaceName = "Olivar A"
        var farm1Version = 1; var farm1Name = "Finca Norte"
        var farm2Version = 1; var farm2Name = "Finca Sur"
        var parcel2Version = 1; var parcel2Name = "Parcela 2"
        var withMembership1 = true
        override suspend fun rpc(name: String, bodyJson: String, accessToken: String): DeliveryRpcResponse {
            assertEquals("get_own_workspace_parents_v1", name)
            assertEquals("""{"p_workspace_id":"$workspace"}""", bodyJson)
            return when (mode) {
                Mode.FORBIDDEN -> DeliveryRpcResponse(403, """{"code":"42501"}""")
                Mode.OFFLINE -> throw DeliveryRpcFailure(DeliveryRpcError.NETWORK)
                Mode.MALFORMED -> DeliveryRpcResponse(200, body().replace("\"memberships\"", "\"membership\""))
                Mode.LOGOUT_DURING_READ -> { account.logout(false); DeliveryRpcResponse(200, body()) }
                Mode.NORMAL -> DeliveryRpcResponse(200, body())
            }
        }
        private fun at(v: Int) = "2026-10-${10 + v}T08:00:00.123456+00:00"
        private fun farm(id: UUID, n: String, v: Int) = """{"id":"$id","workspaceId":"$workspace","name":"$n","description":null,"municipality":"Bedmar","province":"Jaén","notes":null,"status":"ACTIVE","createdAt":"${at(1)}","updatedAt":"${at(v)}","version":$v}"""
        private fun parcel(id: UUID, n: String, v: Int) = """{"id":"$id","workspaceId":"$workspace","displayName":"$n","cadastralReference":null,"cadastralPolygon":null,"cadastralParcel":null,"municipality":null,"province":null,"source":"MANUAL","geometryGeoJson":null,"sourceProvider":null,"sourceImportedAt":null,"cadastralAreaM2":null,"managedAreaM2":1234.5,"notes":null,"status":"ACTIVE","oliveTreeCount":120,"variety":"Picual","irrigationSystem":null,"irrigationNetwork":null,"irrigationSector":null,"irrigationDays":null,"createdAt":"${at(1)}","updatedAt":"${at(v)}","version":$v}"""
        private fun membership(id: UUID, f: UUID, p: UUID, from: String) = """{"id":"$id","workspaceId":"$workspace","farmId":"$f","parcelId":"$p","validFrom":"$from","validUntil":null,"createdAt":"${at(1)}","updatedAt":"${at(1)}","version":1}"""
        fun body(): String {
            val memberships = listOfNotNull(
                membership(m1, farm1, parcel1, "2026-01-01T00:00:00.007Z").takeIf { withMembership1 },
                membership(m2, farm2, parcel2, "2026-01-01T00:00:00Z"))
            return """{"contract_version":1,"issuer":"$issuer/auth/v1","subject":"$subject","workspace":{"id":"$workspace","name":"$workspaceName","ownerUserId":"$a","countryCode":"ES","timezone":"Europe/Madrid","locale":"es-ES","currency":"EUR","createdAt":"${at(1)}","updatedAt":"${at(workspaceVersion)}","version":$workspaceVersion},"farms":[${farm(farm1, farm1Name, farm1Version)},${farm(farm2, farm2Name, farm2Version)}],"parcels":[${parcel(parcel1, "Parcela 1", 1)},${parcel(parcel2, parcel2Name, parcel2Version)}],"memberships":[${memberships.joinToString(",")}]}"""
        }
    }

    private object FakeAuth : AccountAuthApi {
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
