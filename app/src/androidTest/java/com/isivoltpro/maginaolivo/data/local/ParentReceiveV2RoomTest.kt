package com.isivoltpro.maginaolivo.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.isivoltpro.maginaolivo.data.local.entity.WorkspaceAuthorizationCacheEntity
import com.isivoltpro.maginaolivo.data.local.model.FarmStatus
import com.isivoltpro.maginaolivo.data.local.model.OutboxStatus
import com.isivoltpro.maginaolivo.data.local.model.RecordStatus
import com.isivoltpro.maginaolivo.data.local.model.SyncEntityType
import com.isivoltpro.maginaolivo.data.local.model.SyncStatus
import com.isivoltpro.maginaolivo.data.repository.sync.DeliveryRpcError
import com.isivoltpro.maginaolivo.data.repository.sync.DeliveryRpcFailure
import com.isivoltpro.maginaolivo.data.repository.sync.DeliveryRpcResponse
import com.isivoltpro.maginaolivo.data.repository.sync.DeliveryRpcTransport
import com.isivoltpro.maginaolivo.data.repository.sync.ParentConflict
import com.isivoltpro.maginaolivo.data.repository.sync.ParentKind
import com.isivoltpro.maginaolivo.data.repository.sync.ParentReceiveOutcome
import com.isivoltpro.maginaolivo.data.repository.sync.ParentReceiveV2Coordinator
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Room-only G2.6 2c-2 receiver over a fake `get_own_workspace_parents_v2`; never a network call. */
@RunWith(AndroidJUnit4::class)
class ParentReceiveV2RoomTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "g26-parent-receive-v2-room"
    private val issuer = "https://project.example"
    private val a = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
    private val c = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc")
    private val workspace = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val north = UUID.fromString("22222222-2222-2222-2222-222222222221")
    private val south = UUID.fromString("22222222-2222-2222-2222-222222222222")
    private val old = UUID.fromString("22222222-2222-2222-2222-222222222223")
    private val p1 = UUID.fromString("33333333-3333-3333-3333-333333333331")
    private val p2 = UUID.fromString("33333333-3333-3333-3333-333333333332")
    private val p3 = UUID.fromString("33333333-3333-3333-3333-333333333333")
    private val m1 = UUID.fromString("44444444-4444-4444-4444-444444444441")
    private val m2 = UUID.fromString("44444444-4444-4444-4444-444444444442")
    private val m3 = UUID.fromString("44444444-4444-4444-4444-444444444443")
    private val start = "2026-01-01T00:00:00.007Z"
    private var now = Instant.parse("2026-11-02T10:00:00Z")
    private val store = MemoryAccountSessionStore()
    private lateinit var db: MaginaOlivoDatabase
    private lateinit var account: AccountRepository
    private val server = Server()

    @Before fun setup() = runBlocking {
        context.deleteDatabase(name)
        db = MaginaOlivoDatabase.create(context, name)
        login(a)
        grant(listOf(workspace))
        server.farms += listOf(F(north, "Finca Norte"), F(south, "Finca Sur"))
        server.parcels += listOf(P(p1, "Parcela 1"), P(p2, "Parcela 2"))
        server.memberships += listOf(M(m1, north, p1, start), M(m2, south, p2, start))
    }

    @After fun close() {
        if (::db.isInitialized) db.close()
        context.deleteDatabase(name)
    }

    @Test fun freshDeviceImportsArchivedHistoryAndArchivedFarmWithItsOpenPeriod() = runBlocking {
        server.farms += F(old, "Finca vieja", status = "ARCHIVED", archivedAt = "2026-05-01T12:00:00.123456+00:00", version = 2)
        server.parcels.single { it.id == p2 }.apply { status = "ARCHIVED"; archivedAt = "2026-03-01T10:00:00.123456789Z"; version = 2 }
        server.memberships.single { it.id == m2 }.apply { until = "2026-03-01T10:00:00.123456789Z"; version = 2 }
        server.parcels += P(p3, "En finca archivada")
        server.memberships += M(m3, old, p3, start) // archive is not a cascade: still open
        val report = receiver().receive(workspace)
        assertEquals(ParentReceiveOutcome.IMPORTED, report.outcome)
        val farm = db.farmDao().findById(old)!!
        assertEquals(FarmStatus.ARCHIVED, farm.status)
        assertEquals(Instant.parse("2026-05-01T12:00:00.123Z"), farm.metadata.deletedAt)
        val archived = db.parcelDao().findById(p2)!!
        assertEquals(RecordStatus.ARCHIVED, archived.status)
        assertEquals(Instant.parse("2026-03-01T10:00:00.123Z"), archived.metadata.deletedAt)
        assertEquals(2L, archived.metadata.remoteVersion)
        assertNull(db.parcelDao().findCurrentMembership(p2))
        assertEquals(old, db.parcelDao().findCurrentMembership(p3)?.farmId)
        assertEquals(listOf(1, 3, 3, 3, 0), counts())
        // Repeated receive: ALREADY_CURRENT and not one byte written.
        val first = snapshot()
        assertEquals(ParentReceiveOutcome.ALREADY_CURRENT, receiver().receive(workspace).outcome)
        assertEquals(first, snapshot())
    }

    @Test fun archiveThenRestoreInAnotherFarmConvergesOnACleanLocalTombstone() = runBlocking {
        assertEquals(ParentReceiveOutcome.IMPORTED, receiver().receive(workspace).outcome)
        // A archives p1 remotely.
        server.parcels.single { it.id == p1 }.apply { status = "ARCHIVED"; archivedAt = "2026-03-02T08:00:00.000000001Z"; version = 2 }
        server.memberships.single { it.id == m1 }.apply { until = "2026-03-02T08:00:00.000000001Z"; version = 2 }
        assertEquals(ParentReceiveOutcome.IMPORTED, receiver().receive(workspace).outcome)
        assertEquals(RecordStatus.ARCHIVED, db.parcelDao().findById(p1)?.status)
        assertEquals(Instant.parse("2026-03-02T08:00:00Z"), db.parcelDao().findById(p1)?.metadata?.deletedAt)
        assertNull(db.parcelDao().findCurrentMembership(p1))
        // Then restores it in another farm: v1 marked this a local_tombstone forever (H4).
        server.parcels.single { it.id == p1 }.apply { status = "ACTIVE"; archivedAt = null; version = 3 }
        server.memberships += M(m3, south, p1, "2026-04-01T09:30:00.5Z")
        assertEquals(ParentReceiveOutcome.IMPORTED, receiver().receive(workspace).outcome)
        val restored = db.parcelDao().findById(p1)!!
        assertEquals(RecordStatus.ACTIVE, restored.status)
        assertNull(restored.metadata.deletedAt)
        assertEquals(3L, restored.metadata.remoteVersion)
        assertEquals(SyncStatus.SYNCED, restored.metadata.syncStatus)
        val history = db.parcelDao().listMemberships(p1)
        assertEquals(setOf(m1, m3), history.map { it.id }.toSet())
        assertEquals(south, db.parcelDao().findCurrentMembership(p1)?.farmId)
        assertEquals(0, count("sync_outbox"))
    }

    @Test fun aCleanArchivedFarmAcceptsARemoteRestore() = runBlocking {
        server.farms.single { it.id == south }.apply { status = "ARCHIVED"; archivedAt = "2026-05-01T12:00:00Z"; version = 2 }
        assertEquals(ParentReceiveOutcome.IMPORTED, receiver().receive(workspace).outcome)
        assertEquals(Instant.parse("2026-05-01T12:00:00Z"), db.farmDao().findById(south)?.metadata?.deletedAt)
        server.farms.single { it.id == south }.apply { status = "ACTIVE"; archivedAt = null; version = 3 }
        assertEquals(ParentReceiveOutcome.IMPORTED, receiver().receive(workspace).outcome)
        val farm = db.farmDao().findById(south)!!
        assertEquals(FarmStatus.ACTIVE, farm.status)
        assertNull(farm.metadata.deletedAt)
        assertEquals(3L, farm.metadata.remoteVersion)
    }

    @Test fun staleAndDivergentRemoteNeverOverwrite() = runBlocking {
        server.parcels.single { it.id == p1 }.version = 3
        assertEquals(ParentReceiveOutcome.IMPORTED, receiver().receive(workspace).outcome)
        val before = snapshot()
        server.parcels.single { it.id == p1 }.apply { version = 2; name = "Rollback" }
        assertEquals(listOf(ParentConflict(ParentKind.PARCEL, p1, "stale_remote")), receiver().receive(workspace).conflicts)
        server.parcels.single { it.id == p1 }.apply { version = 3; name = "Mismo número, otro contenido" }
        assertEquals(listOf(ParentConflict(ParentKind.PARCEL, p1, "divergent_same_version")), receiver().receive(workspace).conflicts)
        // Same version and content, archived on one side only: also divergent, never adopted.
        server.parcels.single { it.id == p1 }.apply { name = "Parcela 1"; status = "ARCHIVED"; archivedAt = "2026-03-01T00:00:00Z" }
        server.memberships.single { it.id == m1 }.until = "2026-03-01T00:00:00Z"
        assertEquals(listOf(ParentConflict(ParentKind.PARCEL, p1, "divergent_same_version")), receiver().receive(workspace).conflicts)
        assertEquals(before, snapshot())
    }

    @Test fun localPendingFailedBlockedAndConflictRowsAndTheOutboxAreNeverOverwritten() = runBlocking {
        assertEquals(ParentReceiveOutcome.IMPORTED, receiver().receive(workspace).outcome)
        val sql = db.openHelper.writableDatabase
        // p1: offline edit with its PENDING marker; the server moves on: remote_ahead.
        sql.execSQL("UPDATE parcels SET display_name='Edición de B', version=2, sync_status='PENDING' WHERE id='$p1'")
        val pending = UUID.randomUUID()
        sql.execSQL("INSERT INTO sync_outbox(id,entity_type,entity_id,operation,payload_version,status,attempt_count,created_at,updated_at) VALUES ('$pending','PARCEL','$p1','UPDATE',1,'PENDING',0,2000,2000)")
        // p2: a sent ARCHIVE whose outcome is unknown (FAILED); same remote version: local_pending.
        val unknown = UUID.randomUUID()
        sql.execSQL("INSERT INTO sync_outbox(id,entity_type,entity_id,operation,payload_version,status,attempt_count,created_at,updated_at,account_id,payload_json,last_error_code) VALUES ('$unknown','PARCEL','$p2','ARCHIVE',1,'FAILED',1,2000,2000,'$a','{}','OUTCOME_UNKNOWN')")
        // south: a farm in CONFLICT after a refused send.
        sql.execSQL("UPDATE farms SET sync_status='CONFLICT' WHERE id='$south'")
        val outbox = outboxRows()
        server.parcels.single { it.id == p1 }.apply { version = 2; name = "Edición de A" }
        server.farms.single { it.id == south }.apply { version = 2; name = "Finca Sur remota" }
        val report = receiver().receive(workspace)
        assertEquals(ParentReceiveOutcome.CONFLICT, report.outcome)
        assertEquals(setOf(ParentConflict(ParentKind.PARCEL, p1, "remote_ahead"),
            ParentConflict(ParentKind.PARCEL, p2, "local_pending"),
            ParentConflict(ParentKind.FARM, south, "local_conflict")), report.conflicts.toSet())
        assertEquals("Edición de B", db.parcelDao().findById(p1)?.displayName)
        assertEquals(SyncStatus.PENDING, db.parcelDao().findById(p1)?.metadata?.syncStatus)
        assertEquals(1L, db.parcelDao().findById(p1)?.metadata?.remoteVersion)
        assertEquals("Finca Sur", db.farmDao().findById(south)?.name)
        assertEquals(outbox, outboxRows())
        assertEquals(OutboxStatus.FAILED, db.syncOutboxDao().findById(unknown)?.status)
    }

    /** Guard 1: one conflicting membership leaves its parcel and all its periods untouched. */
    @Test fun aParcelAndAllItsMembershipsApplyAsOneUnitWhileOtherParcelsAdvance() = runBlocking {
        assertEquals(ParentReceiveOutcome.IMPORTED, receiver().receive(workspace).outcome)
        val p1Before = rowsOf(p1)
        // p1 archived remotely, but its period now names another farm: immutable_field_changed.
        server.parcels.single { it.id == p1 }.apply { status = "ARCHIVED"; archivedAt = "2026-03-01T00:00:00Z"; version = 2 }
        server.memberships.single { it.id == m1 }.apply { farm = south; until = "2026-03-01T00:00:00Z"; version = 2 }
        // p2 advances normally in the same graph.
        server.parcels.single { it.id == p2 }.apply { name = "Parcela 2 ampliada"; version = 2 }
        val report = receiver().receive(workspace)
        assertEquals(listOf(ParentConflict(ParentKind.MEMBERSHIP, m1, "immutable_field_changed")), report.conflicts)
        assertEquals(p1Before, rowsOf(p1))
        assertEquals(RecordStatus.ACTIVE, db.parcelDao().findById(p1)?.status)
        assertEquals("Parcela 2 ampliada", db.parcelDao().findById(p2)?.displayName)
        // A synced local period the server does not return: remote_missing, nothing of p2 written.
        val extra = UUID.randomUUID()
        db.openHelper.writableDatabase.execSQL("INSERT INTO farm_parcel_memberships(id,workspace_id,farm_id,parcel_id,valid_from,valid_until,created_at,updated_at,version,sync_status,remote_version) VALUES ('$extra','$workspace','$north','$p2',1000,2000,1000,1000,1,'SYNCED',1)")
        server.memberships.single { it.id == m1 }.apply { farm = north }
        server.parcels.single { it.id == p2 }.apply { name = "No debe llegar"; version = 3 }
        val p2Before = rowsOf(p2)
        val second = receiver().receive(workspace)
        assertTrue(ParentConflict(ParentKind.MEMBERSHIP, extra, "remote_missing") in second.conflicts)
        assertEquals(p2Before, rowsOf(p2))
        // p1 is consistent again and its archive applies as a unit.
        assertEquals(RecordStatus.ARCHIVED, db.parcelDao().findById(p1)?.status)
        assertNull(db.parcelDao().findCurrentMembership(p1))
    }

    @Test fun aSyncedLocalParcelOrFarmMissingRemotelyIsReportedNeverDeleted() = runBlocking {
        assertEquals(ParentReceiveOutcome.IMPORTED, receiver().receive(workspace).outcome)
        server.parcels.removeAll { it.id == p2 }
        server.memberships.removeAll { it.id == m2 }
        val before = snapshot()
        val report = receiver().receive(workspace)
        assertEquals(listOf(ParentConflict(ParentKind.PARCEL, p2, "remote_missing")), report.conflicts)
        assertEquals(before, snapshot())
    }

    @Test fun identityGrantAndAccountChangesBetweenReadAndCommitWriteNothing() = runBlocking {
        server.subject = c
        assertEquals(ParentReceiveOutcome.NOT_AUTHORIZED, receiver().receive(workspace).outcome)
        server.subject = a
        server.issuerOverride = "https://other.example/auth/v1"
        assertEquals(ParentReceiveOutcome.NOT_AUTHORIZED, receiver().receive(workspace).outcome)
        server.issuerOverride = null
        server.mode = Mode.REVOKE_DURING_READ
        assertEquals(ParentReceiveOutcome.NOT_AUTHORIZED, receiver().receive(workspace).outcome)
        grant(listOf(workspace))
        server.mode = Mode.SWITCH_ACCOUNT_DURING_READ
        assertEquals(ParentReceiveOutcome.NOT_AUTHORIZED, receiver().receive(workspace).outcome)
        login(a)
        server.mode = Mode.FORBIDDEN
        assertEquals(ParentReceiveOutcome.NOT_AUTHORIZED, receiver().receive(workspace).outcome)
        server.mode = Mode.OFFLINE
        assertEquals(ParentReceiveOutcome.OFFLINE, receiver().receive(workspace).outcome)
        server.mode = Mode.OTHER_WORKSPACE
        assertEquals(ParentReceiveOutcome.REJECTED, receiver().receive(workspace).outcome)
        server.mode = Mode.NORMAL
        now = now.plusSeconds(14 * 60)
        assertEquals("expired grant cache", ParentReceiveOutcome.NOT_AUTHORIZED, receiver().receive(workspace).outcome)
        assertEquals(listOf(0, 0, 0, 0, 0), counts())
    }

    @Test fun tooLargeFromTheServerTheTransportOrTheParserIsVisibleAndWritesNothing() = runBlocking {
        for (mode in listOf(Mode.SERVER_TOO_LARGE, Mode.TRANSPORT_TOO_LARGE, Mode.OVERSIZED_BODY)) {
            server.mode = mode
            assertEquals(mode.name, ParentReceiveOutcome.TOO_LARGE, receiver().receive(workspace).outcome)
        }
        assertEquals(listOf(0, 0, 0, 0, 0), counts())
    }

    private fun receiver() = ParentReceiveV2Coordinator(db, account, issuer, server) { now }
    private fun login(id: UUID) {
        store.write(AccountRecord(session = AuthSession(id, "$id@example.test", "access", "refresh", now.plusSeconds(3600))))
        account = AccountRepository(FakeAuth, store) { now }
    }
    private suspend fun grant(ids: List<UUID>) = db.workspaceAuthorizationCacheDao()
        .upsert(WorkspaceAuthorizationCacheEntity(issuer, a.toString(), contract(a, ids)))
    private fun contract(id: UUID, ids: List<UUID>) = """{"contract_version":1,"issuer":"$issuer/auth/v1","subject":"$id","verified_at":"$now","token_expires_at":"${now.plusSeconds(3600)}","workspaces":[${ids.joinToString(",") { """{"id":"$it","name":"QA","role":"VIEWER","permissions":["read"],"grant_version":1,"authorization_expires_at":"${now.plusSeconds(900)}"}""" }}]}"""
    private fun count(table: String) = db.openHelper.readableDatabase.query("SELECT count(*) FROM $table").use { it.moveToFirst(); it.getInt(0) }
    private fun counts() = listOf("workspaces", "farms", "parcels", "farm_parcel_memberships", "sync_outbox").map(::count)
    private fun rows(query: String): List<String> = db.openHelper.readableDatabase.query(query).use { cursor ->
        buildList { while (cursor.moveToNext()) add((0 until cursor.columnCount).joinToString("|") { cursor.getString(it) ?: "∅" }) }
    }
    private fun snapshot() = listOf("workspaces", "farms", "parcels", "farm_parcel_memberships", "sync_outbox")
        .flatMap { rows("SELECT * FROM $it ORDER BY id") }
    /** Logical hash of one parcel unit: the parcel row and every one of its periods. */
    private fun rowsOf(parcel: UUID) = rows("SELECT * FROM parcels WHERE id='$parcel'") +
        rows("SELECT * FROM farm_parcel_memberships WHERE parcel_id='$parcel' ORDER BY id")
    private fun outboxRows() = rows("SELECT * FROM sync_outbox ORDER BY id")

    private data class F(val id: UUID, var name: String, var status: String = "ACTIVE", var archivedAt: String? = null, var version: Int = 1)
    private data class P(val id: UUID, var name: String, var status: String = "ACTIVE", var archivedAt: String? = null, var version: Int = 1)
    private data class M(val id: UUID, var farm: UUID, val parcel: UUID, val from: String, var until: String? = null, var version: Int = 1)
    private enum class Mode { NORMAL, FORBIDDEN, OFFLINE, OTHER_WORKSPACE, REVOKE_DURING_READ, SWITCH_ACCOUNT_DURING_READ,
        SERVER_TOO_LARGE, TRANSPORT_TOO_LARGE, OVERSIZED_BODY }

    private inner class Server : DeliveryRpcTransport {
        var mode = Mode.NORMAL
        var subject = a
        var issuerOverride: String? = null
        val farms = mutableListOf<F>()
        val parcels = mutableListOf<P>()
        val memberships = mutableListOf<M>()
        override suspend fun rpc(name: String, bodyJson: String, accessToken: String): DeliveryRpcResponse {
            assertEquals("get_own_workspace_parents_v2", name)
            assertEquals("""{"p_workspace_id":"$workspace"}""", bodyJson)
            return when (mode) {
                Mode.FORBIDDEN -> DeliveryRpcResponse(403, """{"code":"42501"}""")
                Mode.OFFLINE -> throw DeliveryRpcFailure(DeliveryRpcError.NETWORK)
                Mode.OTHER_WORKSPACE -> DeliveryRpcResponse(200, body().replace("\"id\":\"$workspace\",\"name\"", "\"id\":\"${UUID.randomUUID()}\",\"name\""))
                Mode.REVOKE_DURING_READ -> { grant(emptyList()); DeliveryRpcResponse(200, body()) }
                // The in-flight repository logs out and another account signs in on this device.
                Mode.SWITCH_ACCOUNT_DURING_READ -> { account.logout(false); login(c); DeliveryRpcResponse(200, body()) }
                Mode.SERVER_TOO_LARGE -> DeliveryRpcResponse(500, """{"code":"54000","message":"parents too large for contract v2: bytes"}""")
                Mode.TRANSPORT_TOO_LARGE -> throw DeliveryRpcFailure(DeliveryRpcError.PROTOCOL, 200,
                    reason = ParentReceiveV2Coordinator.RESPONSE_TOO_LARGE)
                Mode.OVERSIZED_BODY -> DeliveryRpcResponse(200, body().replaceFirst("\"notes\":null", "\"notes\":\"${"x".repeat(8 * 1024 * 1024)}\""))
                Mode.NORMAL -> DeliveryRpcResponse(200, body())
            }
        }
        private fun at(v: Int) = "2026-10-${10 + v}T08:00:00.123456+00:00"
        private fun text(value: String?) = value?.let { "\"$it\"" } ?: "null"
        private fun farm(f: F) = """{"id":"${f.id}","workspaceId":"$workspace","name":"${f.name}","description":null,"municipality":"Bedmar","province":"Jaén","notes":null,"status":"${f.status}","archivedAt":${text(f.archivedAt)},"createdAt":"${at(1)}","updatedAt":"${at(f.version)}","version":${f.version}}"""
        private fun parcel(p: P) = """{"id":"${p.id}","workspaceId":"$workspace","displayName":"${p.name}","cadastralReference":null,"cadastralPolygon":null,"cadastralParcel":null,"municipality":null,"province":null,"source":"MANUAL","geometryGeoJson":null,"sourceProvider":null,"sourceImportedAt":null,"cadastralAreaM2":null,"managedAreaM2":1234.5,"notes":null,"status":"${p.status}","oliveTreeCount":120,"variety":"Picual","irrigationSystem":null,"irrigationNetwork":null,"irrigationSector":null,"irrigationDays":null,"archivedAt":${text(p.archivedAt)},"createdAt":"${at(1)}","updatedAt":"${at(p.version)}","version":${p.version}}"""
        private fun membership(m: M) = """{"id":"${m.id}","workspaceId":"$workspace","farmId":"${m.farm}","parcelId":"${m.parcel}","validFrom":"${m.from}","validUntil":${text(m.until)},"createdAt":"${at(1)}","updatedAt":"${at(m.version)}","version":${m.version}}"""
        fun body() = """{"contract_version":2,"issuer":"${issuerOverride ?: "$issuer/auth/v1"}","subject":"$subject","workspace":{"id":"$workspace","name":"Olivar A","ownerUserId":"$a","countryCode":"ES","timezone":"Europe/Madrid","locale":"es-ES","currency":"EUR","createdAt":"${at(1)}","updatedAt":"${at(1)}","version":1},"farms":[${farms.joinToString(",") { farm(it) }}],"parcels":[${parcels.joinToString(",") { parcel(it) }}],"memberships":[${memberships.joinToString(",") { membership(it) }}]}"""
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
