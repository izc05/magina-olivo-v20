package com.isivoltpro.maginaolivo.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.isivoltpro.maginaolivo.data.local.entity.WorkspaceAuthorizationCacheEntity
import com.isivoltpro.maginaolivo.data.local.entity.WorkspaceSelectionEntity
import com.isivoltpro.maginaolivo.data.repository.sync.AgriculturalCycleRpcAdapter
import com.isivoltpro.maginaolivo.data.repository.sync.AgriculturalCycleSyncCoordinator
import com.isivoltpro.maginaolivo.data.repository.sync.CycleSyncOutcome
import com.isivoltpro.maginaolivo.data.repository.sync.DeliveryDispatchLease
import com.isivoltpro.maginaolivo.data.repository.sync.DeliveryRpcResponse
import com.isivoltpro.maginaolivo.data.repository.sync.DeliveryRpcTransport
import com.isivoltpro.maginaolivo.feature.account.AccountAuthApi
import com.isivoltpro.maginaolivo.feature.account.AccountRecord
import com.isivoltpro.maginaolivo.feature.account.AccountRepository
import com.isivoltpro.maginaolivo.feature.account.AuthSession
import com.isivoltpro.maginaolivo.feature.account.MemoryAccountSessionStore
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** CR-015 P4: two phones of one owner against an in-memory server with the SQL contract's CAS rules. */
@RunWith(AndroidJUnit4::class)
class AgriculturalCycleSyncRoomTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val issuer = "https://project.example"
    private val a = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
    private val c = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc")
    private val workspace = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val farm = UUID.fromString("22222222-2222-2222-2222-222222222222")
    private val recollection = UUID.fromString("33333333-3333-3333-3333-333333333333")
    private val cycle = UUID.fromString("44444444-4444-4444-4444-444444444444")
    private val now = Instant.parse("2026-11-07T11:00:00Z")
    private val server = FakeServer()
    private val store = MemoryAccountSessionStore()
    private lateinit var account: AccountRepository
    private lateinit var phoneA: MaginaOlivoDatabase
    private lateinit var phoneB: MaginaOlivoDatabase

    @Before fun setup() = runBlocking {
        listOf("p4-a","p4-b").forEach(context::deleteDatabase)
        store.write(AccountRecord(session=AuthSession(a,"a@example.test","access","refresh",now.plusSeconds(3600))))
        account = AccountRepository(FakeAuth,store) { now }
        phoneA = phone("p4-a")
        phoneB = phone("p4-b")
    }

    @After fun close() {
        if (::phoneA.isInitialized) phoneA.close()
        if (::phoneB.isInitialized) phoneB.close()
        listOf("p4-a","p4-b").forEach(context::deleteDatabase)
    }

    @Test fun sendThenReceiveOnTheSecondPhoneAndNothingIsResent() = runBlocking {
        openCycle(phoneA)
        assertEquals(CycleSyncOutcome.SYNCED, sync(phoneA))
        assertEquals(1L, server.revision)
        assertEquals(CycleSyncOutcome.NOTHING_TO_SEND, coordinator(phoneA).stage(farm))
        assertEquals(1, server.writes)

        assertEquals(CycleSyncOutcome.IMPORTED, coordinator(phoneB).receive(farm))
        assertEquals("2026/27", phoneB.agriculturalCycleDao().find(cycle)?.label)
        assertEquals(cycle, phoneB.agriculturalCycleDao().recollectionCycle(recollection))
        assertEquals(1, count(phoneB, "agricultural_cycle_operations"))
        assertEquals(CycleSyncOutcome.NOTHING_TO_SEND, coordinator(phoneB).stage(farm))
        assertEquals(CycleSyncOutcome.ALREADY_CURRENT, coordinator(phoneB).receive(farm))
    }

    @Test fun lostAnswerIsRetriedWithTheSameOperationAfterRestartWithoutDuplicating() = runBlocking {
        openCycle(phoneA)
        assertEquals(CycleSyncOutcome.STAGED, coordinator(phoneA).stage(farm))
        val frozen = phoneA.agriculturalCycleDao().syncState(farm)!!
        server.loseNextAnswer = true
        assertEquals(CycleSyncOutcome.OUTCOME_UNKNOWN, dispatch(phoneA))
        phoneA.close()
        phoneA = MaginaOlivoDatabase.create(context, "p4-a")
        assertEquals(CycleSyncOutcome.STAGED, coordinator(phoneA).stage(farm))
        assertEquals(frozen.pendingBody, phoneA.agriculturalCycleDao().syncState(farm)?.pendingBody)
        assertEquals(CycleSyncOutcome.SYNCED, dispatch(phoneA))
        assertEquals(1L, server.revision)
        assertEquals(1, server.writes)
        assertEquals(2, server.calls)
    }

    @Test fun concurrentClosuresOnTwoPhonesBecomeAVisibleConflictAndNothingIsOverwritten() = runBlocking {
        openCycle(phoneA)
        assertEquals(CycleSyncOutcome.SYNCED, sync(phoneA))
        assertEquals(CycleSyncOutcome.IMPORTED, coordinator(phoneB).receive(farm))
        close(phoneA, "2027-01-31")
        close(phoneB, "2027-02-10")
        assertEquals(CycleSyncOutcome.SYNCED, sync(phoneA))
        assertEquals(CycleSyncOutcome.CONFLICT, sync(phoneB))
        assertEquals("STALE_REVISION", phoneB.agriculturalCycleDao().syncState(farm)?.reason)
        assertEquals(CycleSyncOutcome.CONFLICT, coordinator(phoneB).receive(farm))
        assertEquals(LocalDate.parse("2027-02-10"), phoneB.agriculturalCycleDao().find(cycle)?.effectiveEndDate)
        assertEquals("2027-01-31", server.cycles().getJSONObject(0).getString("effectiveEndDate"))
        assertEquals(CycleSyncOutcome.CONFLICT, coordinator(phoneB).stage(farm))
        assertEquals(2L, server.revision)
    }

    @Test fun recollectionNotYetOnTheServerWaitsAndSendsNothing() = runBlocking {
        openCycle(phoneA)
        phoneA.openHelper.writableDatabase.execSQL("UPDATE campaigns SET sync_status='PENDING' WHERE id='$recollection'")
        assertEquals(CycleSyncOutcome.WAITING_PARENTS, coordinator(phoneA).stage(farm))
        assertEquals(0, server.calls)
        assertEquals("WAITING_PARENTS", phoneA.agriculturalCycleDao().syncState(farm)?.status)
    }

    @Test fun anotherAccountOrANonOwnerGrantNeverSendsOrReceives() = runBlocking {
        openCycle(phoneA)
        phoneA.workspaceAuthorizationCacheDao().upsert(WorkspaceAuthorizationCacheEntity(issuer,a.toString(),contract(a,"EDITOR")))
        assertEquals(CycleSyncOutcome.NOT_AUTHORIZED, coordinator(phoneA).stage(farm))
        assertEquals(CycleSyncOutcome.NOT_AUTHORIZED, coordinator(phoneA).receive(farm))
        phoneA.workspaceAuthorizationCacheDao().upsert(WorkspaceAuthorizationCacheEntity(issuer,a.toString(),contract(a)))
        assertEquals(CycleSyncOutcome.STAGED, coordinator(phoneA).stage(farm))
        store.write(AccountRecord(session=AuthSession(c,"c@example.test","other","refresh",now.plusSeconds(3600))))
        account = AccountRepository(FakeAuth,store) { now }
        assertEquals(CycleSyncOutcome.NOT_AUTHORIZED, dispatch(phoneA))
        assertEquals(0, server.calls)
        assertEquals(a, phoneA.agriculturalCycleDao().syncState(farm)?.accountId)
    }

    @Test fun serverHistoryThatRewritesALocalClosedCycleIsHeldAsConflict() = runBlocking {
        openCycle(phoneA)
        close(phoneA, "2027-01-31")
        assertEquals(CycleSyncOutcome.SYNCED, sync(phoneA))
        assertEquals(CycleSyncOutcome.IMPORTED, coordinator(phoneB).receive(farm))
        server.tamper { it.getJSONObject(0).put("effectiveEndDate","2027-01-15") }
        assertEquals(CycleSyncOutcome.CONFLICT, coordinator(phoneB).receive(farm))
        assertEquals("CLOSED_CYCLE_CHANGED", phoneB.agriculturalCycleDao().syncState(farm)?.reason)
        assertEquals(LocalDate.parse("2027-01-31"), phoneB.agriculturalCycleDao().find(cycle)?.effectiveEndDate)
    }

    // ---------------------------------------------------------------------------------------------------- helpers

    private fun phone(name: String): MaginaOlivoDatabase = MaginaOlivoDatabase.create(context, name).also { db ->
        val sql = db.openHelper.writableDatabase
        sql.execSQL("INSERT INTO workspaces(id,name,owner_user_id,country_code,timezone,locale,currency,created_at,updated_at,version,sync_status,remote_version) VALUES ('$workspace','QA','$a','ES','Europe/Madrid','es-ES','EUR',1000,1000,1,'SYNCED',1)")
        sql.execSQL("INSERT INTO farms(id,workspace_id,name,status,created_at,updated_at,version,sync_status,remote_version) VALUES ('$farm','$workspace','Farm','ACTIVE',1000,1000,1,'SYNCED',1)")
        sql.execSQL("INSERT INTO campaigns(id,workspace_id,farm_id,name,start_date,status,created_at,updated_at,version,sync_status,remote_version) VALUES ('$recollection','$workspace','$farm','Recogida','2026-11-07','CLOSED',1000,1000,1,'SYNCED',1)")
        runBlocking {
            db.workspaceSelectionDao().upsert(WorkspaceSelectionEntity(issuer,a.toString(),workspace))
            db.workspaceAuthorizationCacheDao().upsert(WorkspaceAuthorizationCacheEntity(issuer,a.toString(),contract(a)))
        }
    }

    /** What P3 leaves after «Crear» + «Vincular»: one open cycle, its link and the per-farm revision. */
    private fun openCycle(db: MaginaOlivoDatabase) {
        val sql = db.openHelper.writableDatabase
        sql.execSQL("INSERT INTO agricultural_cycles(id,workspace_id,farm_id,label,start_date,no_recollection,version) VALUES ('$cycle','$workspace','$farm','2026/27','2026-11-07',0,1)")
        sql.execSQL("INSERT INTO agricultural_cycle_recollections(campaign_id,cycle_id) VALUES ('$recollection','$cycle')")
        sql.execSQL("INSERT OR REPLACE INTO agricultural_cycle_farm_state(farm_id,revision) VALUES ('$farm',2)")
    }

    private fun close(db: MaginaOlivoDatabase, end: String) {
        val sql = db.openHelper.writableDatabase
        // Nanoseconds on purpose: the wire keeps microseconds, the local row keeps its own value.
        sql.execSQL("UPDATE agricultural_cycles SET effective_end_date='$end',closed_at=${Instant.parse("2027-02-11T10:00:00.123456789Z").toEpochMilli()},version=version+1 WHERE id='$cycle'")
        sql.execSQL("UPDATE agricultural_cycle_farm_state SET revision=revision+1 WHERE farm_id='$farm'")
    }

    private fun count(db: MaginaOlivoDatabase, table: String) =
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); it.getInt(0) }

    private fun coordinator(db: MaginaOlivoDatabase) =
        AgriculturalCycleSyncCoordinator(db, account, issuer, AgriculturalCycleRpcAdapter(server), { now })

    private suspend fun dispatch(db: MaginaOlivoDatabase): CycleSyncOutcome {
        val lease = DeliveryDispatchLease.tryAcquire(File(context.cacheDir, "p4-cycle-lease"))!!
        return try { coordinator(db).dispatch(farm, lease) } finally { lease.close() }
    }

    private suspend fun sync(db: MaginaOlivoDatabase): CycleSyncOutcome {
        val staged = coordinator(db).stage(farm)
        return if (staged == CycleSyncOutcome.STAGED) dispatch(db) else staged
    }

    private fun contract(id: UUID, role: String = "OWNER") = """{"contract_version":1,"issuer":"$issuer/auth/v1","subject":"$id","verified_at":"$now","token_expires_at":"${now.plusSeconds(3600)}","workspaces":[{"id":"$workspace","name":"QA","role":"$role","permissions":["read","write"],"grant_version":1,"authorization_expires_at":"${now.plusSeconds(900)}"}]}"""

    /** CAS per farm, idempotent receipts keyed by operation id and exact body; renders instants as PostgreSQL does. */
    private inner class FakeServer : DeliveryRpcTransport {
        var revision = 0L
        var writes = 0
        var calls = 0
        var loseNextAnswer = false
        private var state: JSONObject? = null
        private val receipts = mutableMapOf<String, Pair<String, Long>>()
        private val micros = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'").withZone(ZoneOffset.UTC)

        fun cycles(): JSONArray = state!!.getJSONArray("cycles")
        fun tamper(change: (JSONArray) -> Unit) { change(cycles()); revision++ }

        override suspend fun rpc(name: String, bodyJson: String, accessToken: String): DeliveryRpcResponse {
            calls++
            if (accessToken != "access") return DeliveryRpcResponse(403, """{"code":"42501"}""")
            val request = JSONObject(bodyJson)
            if (name == "get_own_farm_cycles_v1") return DeliveryRpcResponse(200, head().toString())
            assertEquals("put_own_farm_cycles_v1", name)
            val operation = request.getString("p_operation_id")
            receipts[operation]?.let { (body, version) ->
                return if (body == bodyJson) answer(operation, version)
                else DeliveryRpcResponse(409, """{"code":"23505","message":"cycles conflict: OPERATION_REUSED"}""")
            }
            if (request.getLong("p_expected_revision") != revision)
                return DeliveryRpcResponse(409, """{"code":"23505","message":"cycles conflict: STALE_REVISION"}""")
            state = request.getJSONObject("p_state")
            revision++; writes++
            receipts[operation] = bodyJson to revision
            if (loseNextAnswer) { loseNextAnswer = false; throw IOException("simulated lost answer") }
            return answer(operation, revision)
        }

        private fun answer(operation: String, version: Long) = DeliveryRpcResponse(200,
            """{"success":true,"operation_id":"$operation","entity_id":"$farm","status":"APPLIED","remote_version":$version,"payload_version":1,"idempotent":false}""")

        private fun head(): JSONObject {
            val rendered = JSONArray()
            val source = state?.getJSONArray("cycles") ?: JSONArray()
            for (i in 0 until source.length()) {
                val row = JSONObject(source.getJSONObject(i).toString())
                if (!row.isNull("closedAt")) row.put("closedAt", micros.format(Instant.parse(row.getString("closedAt"))))
                rendered.put(row)
            }
            return JSONObject().put("contract_version",1).put("subject",a.toString()).put("farm_id",farm.toString())
                .put("workspace_id",workspace.toString()).put("revision",revision).put("cycles",rendered)
                .put("recollections",state?.getJSONArray("recollections") ?: JSONArray())
        }
    }

    private object FakeAuth : AccountAuthApi {
        override val redirectUri="app://callback"
        override suspend fun signIn(email:String,password:String):AuthSession=error("no login")
        override suspend fun signUp(email:String,password:String,challenge:String):AuthSession?=error("no signup")
        override suspend fun recover(email:String,challenge:String)=error("no recovery")
        override suspend fun exchange(code:String,verifier:String):AuthSession=error("no exchange")
        override suspend fun refresh(session:AuthSession):AuthSession=error("no refresh")
        override suspend fun user(session:AuthSession):Pair<UUID,String> = session.userId to session.email
        override suspend fun changePassword(session:AuthSession,password:String)=error("no password")
        override suspend fun signOut(session:AuthSession)=Unit
    }
}
