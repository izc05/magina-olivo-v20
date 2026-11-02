package com.isivoltpro.maginaolivo.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.isivoltpro.maginaolivo.data.local.entity.WorkspaceAuthorizationCacheEntity
import com.isivoltpro.maginaolivo.data.local.entity.WorkspaceSelectionEntity
import com.isivoltpro.maginaolivo.data.repository.sync.CampaignReceiveCoordinator
import com.isivoltpro.maginaolivo.data.repository.sync.CampaignReceiveOutcome
import com.isivoltpro.maginaolivo.data.repository.sync.DeliveryRpcResponse
import com.isivoltpro.maginaolivo.data.repository.sync.DeliveryRpcTransport
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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * G2.6 2c-H7 (#814 order): historical campaigns over archived parcels and really archived farms.
 * Parents are seeded as the 2c-2 v2 receiver leaves them (SYNCED, version == remoteVersion, an
 * archive = status ARCHIVED + its instant, full membership history). The server row is a fake of
 * `get_own_campaign_receive_v1`. Every refusal is checked for zero writes.
 */
@RunWith(AndroidJUnit4::class)
class CampaignHistoryReceiveRoomTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "g26-campaign-history-h7"
    private val issuer = "https://project.example"
    private val a = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
    private val c = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc")
    private val workspace = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val otherWorkspace = UUID.fromString("19999999-9999-9999-9999-999999999999")
    private val north = UUID.fromString("22222222-2222-2222-2222-222222222221")
    private val south = UUID.fromString("22222222-2222-2222-2222-222222222222")
    private val parcel = UUID.fromString("33333333-3333-3333-3333-333333333331")
    private val period = UUID.fromString("44444444-4444-4444-4444-444444444441")
    private val campaign = UUID.fromString("55555555-5555-5555-5555-555555555551")
    private val snapshot = UUID.fromString("66666666-6666-6666-6666-666666666661")
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
        db.workspaceSelectionDao().upsert(WorkspaceSelectionEntity(issuer, a.toString(), workspace))
        sql("INSERT INTO workspaces(id,name,owner_user_id,country_code,timezone,locale,currency,created_at,updated_at,version,sync_status,remote_version) VALUES ('$workspace','Olivar','$a','ES','Europe/Madrid','es-ES','EUR',1000,1000,1,'SYNCED',1)")
        farm(north, "Finca Norte")
        farm(south, "Finca Sur")
    }

    @After fun close() {
        if (::db.isInitialized) db.close()
        context.deleteDatabase(name)
    }

    @Test fun closedCampaignOverAnArchivedParcelIsImportedWithImmutableSnapshotsAndIsIdempotent() = runBlocking {
        archivedParcelIn(north, from = "2025-10-01T00:00:00Z", until = "2026-03-01T10:00:00Z")
        val parents = parentRows()
        assertEquals(CampaignReceiveOutcome.IMPORTED, receiver().receive(campaign))
        val stored = db.campaignDao().listSnapshots(campaign).single()
        assertEquals(listOf(snapshot, parcel, north, "Finca Norte", "Parcela 2025", 8_000.0, "23044A00400030", "{\"type\":\"Polygon\"}"),
            listOf(stored.id, stored.parcelId, stored.farmIdAtStart, stored.farmNameAtStart, stored.parcelNameAtStart,
                stored.managedAreaM2AtStart, stored.cadastralReferenceAtStart, stored.geometryGeoJsonSnapshot))
        assertEquals(parents, parentRows()) // parcels and periods are read, never touched
        val first = allRows()
        assertEquals(CampaignReceiveOutcome.ALREADY_CURRENT, receiver().receive(campaign))
        assertEquals(first, allRows())
    }

    @Test fun withoutArchivesTheSameCampaignStillImportsAsBefore() = runBlocking {
        sql("INSERT INTO parcels(id,workspace_id,display_name,source,status,created_at,updated_at,version,sync_status,remote_version) VALUES ('$parcel','$workspace','Parcela viva','MANUAL','ACTIVE',1000,1000,1,'SYNCED',1)")
        server.status = "ACTIVE"; server.endDate = null
        assertEquals(CampaignReceiveOutcome.IMPORTED, receiver().receive(campaign))
    }

    @Test fun aReallyArchivedFarmWithAnOpenPeriodAcceptsAClosedCampaignAndInventsNoClosure() = runBlocking {
        sql("UPDATE farms SET status='ARCHIVED', deleted_at=${ms("2026-05-01T12:00:00Z")}, version=2, remote_version=2 WHERE id='$north'")
        activeParcelIn(north, from = "2025-10-01T00:00:00Z")
        val parents = parentRows()
        assertEquals(CampaignReceiveOutcome.IMPORTED, receiver().receive(campaign))
        assertEquals(parents, parentRows())
        assertEquals(null, db.parcelDao().findCurrentMembership(parcel)?.validUntil)
    }

    @Test fun archiveThenRestoreElsewhereKeepsTheHistoricalSnapshotInItsStartFarm() = runBlocking {
        // Closed period in north during the campaign, then restored into south: currently ACTIVE in south.
        sql("INSERT INTO parcels(id,workspace_id,display_name,source,status,created_at,updated_at,version,sync_status,remote_version) VALUES ('$parcel','$workspace','Parcela','MANUAL','ACTIVE',1000,1000,3,'SYNCED',3)")
        membership(period, north, "2025-10-01T00:00:00Z", "2026-03-01T10:00:00Z", 2)
        membership(UUID.randomUUID(), south, "2026-04-01T09:30:00Z", null, 1)
        assertEquals(CampaignReceiveOutcome.IMPORTED, receiver().receive(campaign))
        assertEquals(north, db.campaignDao().listSnapshots(campaign).single().farmIdAtStart)
        assertEquals(south, db.parcelDao().findCurrentMembership(parcel)?.farmId)
    }

    @Test fun runningOrUnprovenStatesOverArchivedParentsAreRefusedAndWriteNothing() = runBlocking {
        archivedParcelIn(north, from = "2025-10-01T00:00:00Z", until = "2026-03-01T10:00:00Z")
        val before = allRows()
        // HARVEST is running (isRunning), even with an end date that "looks" closed.
        for ((status, end) in listOf("ACTIVE" to null, "PREPARATION" to null, "HARVEST" to null, "HARVEST" to "2026-02-28",
            "CLOSED" to null, "CLOSED" to "2025-09-30")) {
            server.status = status; server.endDate = end
            assertEquals("$status/$end", CampaignReceiveOutcome.ARCHIVED_PARENT_INCOMPATIBLE, receiver().receive(campaign))
        }
        assertEquals(before, allRows())
    }

    @Test fun incoherentForeignOrMissingParentsAreRefusedAndWriteNothing() = runBlocking {
        val cases: List<Pair<String, String>> = listOf(
            "parcel not received" to "",
            "ACTIVE parcel with a tombstone" to "INSERT INTO parcels(id,workspace_id,display_name,source,status,created_at,updated_at,deleted_at,version,sync_status,remote_version) VALUES ('$parcel','$workspace','P','MANUAL','ACTIVE',1000,1000,2000,2,'SYNCED',2)",
            "ARCHIVED parcel without instant" to "INSERT INTO parcels(id,workspace_id,display_name,source,status,created_at,updated_at,version,sync_status,remote_version) VALUES ('$parcel','$workspace','P','MANUAL','ARCHIVED',1000,1000,2,'SYNCED',2)",
            "parcel of another workspace" to "INSERT INTO workspaces(id,name,owner_user_id,country_code,timezone,locale,currency,created_at,updated_at,version,sync_status) VALUES ('$otherWorkspace','Otro','$c','ES','Europe/Madrid','es-ES','EUR',1000,1000,1,'SYNCED'); INSERT INTO parcels(id,workspace_id,display_name,source,status,created_at,updated_at,deleted_at,version,sync_status,remote_version) VALUES ('$parcel','$otherWorkspace','P','MANUAL','ARCHIVED',1000,1000,2000,2,'SYNCED',2)",
        )
        for ((label, setupSql) in cases) {
            if (setupSql.isNotEmpty()) setupSql.split("; ").forEach(::sql)
            val before = allRows()
            assertEquals(label, CampaignReceiveOutcome.PARENTS_MISSING, receiver().receive(campaign))
            assertEquals(label, before, allRows())
            sql("DELETE FROM farm_parcel_memberships"); sql("DELETE FROM parcels"); sql("DELETE FROM workspaces WHERE id='$otherWorkspace'")
        }
        // Farm with a bare tombstone (ACTIVE + deleted_at), then ARCHIVED without its instant.
        activeParcelIn(north, from = "2025-10-01T00:00:00Z")
        for (farmSql in listOf("UPDATE farms SET deleted_at=2000 WHERE id='$north'",
            "UPDATE farms SET status='ARCHIVED', deleted_at=NULL WHERE id='$north'")) {
            sql(farmSql)
            val before = allRows()
            assertEquals(farmSql, CampaignReceiveOutcome.PARENTS_MISSING, receiver().receive(campaign))
            assertEquals(before, allRows())
        }
    }

    @Test fun noHistoricalPeriodInTheCampaignFarmMeansNoJustification() = runBlocking {
        // Archived parcel whose only period was in south, or in north but outside the campaign dates.
        archivedParcelIn(south, from = "2025-10-01T00:00:00Z", until = "2026-03-01T10:00:00Z")
        assertEquals(CampaignReceiveOutcome.PARENTS_MISSING, receiver().receive(campaign))
        sql("UPDATE farm_parcel_memberships SET farm_id='$north', valid_from=${ms("2026-06-01T00:00:00Z")}, valid_until=${ms("2026-07-01T00:00:00Z")}")
        val before = allRows()
        assertEquals(CampaignReceiveOutcome.PARENTS_MISSING, receiver().receive(campaign))
        assertEquals(before, allRows())
    }

    @Test fun localPendingUnknownTombstoneAndDivergentSnapshotsAreNeverOverwritten() = runBlocking {
        archivedParcelIn(north, from = "2025-10-01T00:00:00Z", until = "2026-03-01T10:00:00Z")
        // An archived parcel with an UNKNOWN (FAILED) marker is not a confirmed parent.
        sql("INSERT INTO sync_outbox(id,entity_type,entity_id,operation,payload_version,status,attempt_count,created_at,updated_at,account_id,payload_json,last_error_code) VALUES ('${UUID.randomUUID()}','PARCEL','$parcel','ARCHIVE',1,'FAILED',1,2000,2000,'$a','{}','OUTCOME_UNKNOWN')")
        assertEquals(CampaignReceiveOutcome.PARENTS_MISSING, receiver().receive(campaign))
        sql("DELETE FROM sync_outbox")
        // A not-yet-confirmed remote revision of the archived parcel.
        sql("UPDATE parcels SET version=3 WHERE id='$parcel'")
        assertEquals(CampaignReceiveOutcome.PARENTS_MISSING, receiver().receive(campaign))
        sql("UPDATE parcels SET version=2 WHERE id='$parcel'")
        // The campaign itself pending locally, then imported and changed locally, then divergent snapshots.
        val pending = UUID.randomUUID()
        sql("INSERT INTO sync_outbox(id,entity_type,entity_id,operation,payload_version,status,attempt_count,created_at,updated_at) VALUES ('$pending','CAMPAIGN','$campaign','UPDATE',1,'PENDING',0,2000,2000)")
        assertEquals(CampaignReceiveOutcome.CONFLICT, receiver().receive(campaign))
        sql("DELETE FROM sync_outbox")
        assertEquals(CampaignReceiveOutcome.IMPORTED, receiver().receive(campaign))
        sql("UPDATE campaigns SET name='Edición local', version=2, sync_status='PENDING' WHERE id='$campaign'")
        server.version = 2; server.name = "Remota v2"
        assertEquals(CampaignReceiveOutcome.CONFLICT, receiver().receive(campaign))
        assertEquals("Edición local", db.campaignDao().findById(campaign)?.name)
        sql("UPDATE campaigns SET name='2025/26', version=1, sync_status='SYNCED' WHERE id='$campaign'")
        server.snapshotName = "Otro nombre histórico"
        val before = allRows()
        assertEquals(CampaignReceiveOutcome.CONFLICT, receiver().receive(campaign))
        assertEquals(before, allRows())
        assertEquals("Parcela 2025", db.campaignDao().listSnapshots(campaign).single().parcelNameAtStart)
    }

    @Test fun grantIssuerSubjectSelectionAndAccountChangesRefuseWithoutWrites() = runBlocking {
        archivedParcelIn(north, from = "2025-10-01T00:00:00Z", until = "2026-03-01T10:00:00Z")
        sql("INSERT INTO workspaces(id,name,owner_user_id,country_code,timezone,locale,currency,created_at,updated_at,version,sync_status) VALUES ('$otherWorkspace','Otro','$c','ES','Europe/Madrid','es-ES','EUR',1000,1000,1,'SYNCED')")
        val before = allRows()
        server.subject = c
        assertEquals(CampaignReceiveOutcome.NOT_AUTHORIZED, receiver().receive(campaign))
        server.subject = a; server.issuerOverride = "https://other.example/auth/v1"
        assertEquals(CampaignReceiveOutcome.NOT_AUTHORIZED, receiver().receive(campaign))
        server.issuerOverride = null
        db.workspaceSelectionDao().upsert(WorkspaceSelectionEntity(issuer, a.toString(), otherWorkspace))
        assertEquals(CampaignReceiveOutcome.NOT_AUTHORIZED, receiver().receive(campaign))
        db.workspaceSelectionDao().upsert(WorkspaceSelectionEntity(issuer, a.toString(), workspace))
        server.duringRead = { grant(emptyList()) }
        assertEquals(CampaignReceiveOutcome.NOT_AUTHORIZED, receiver().receive(campaign))
        grant(listOf(workspace))
        server.duringRead = { account.logout(false); login(c) }
        assertEquals(CampaignReceiveOutcome.NOT_AUTHORIZED, receiver().receive(campaign))
        server.duringRead = null
        login(a)
        assertEquals(before, allRows())
        assertEquals(CampaignReceiveOutcome.IMPORTED, receiver().receive(campaign))
    }

    // ---- fixtures --------------------------------------------------------------------------------

    private fun receiver() = CampaignReceiveCoordinator(db, account, issuer, server) { now }
    private fun login(id: UUID) {
        store.write(AccountRecord(session = AuthSession(id, "$id@example.test", "access", "refresh", now.plusSeconds(3600))))
        account = AccountRepository(FakeAuth, store) { now }
    }
    private suspend fun grant(ids: List<UUID>) = db.workspaceAuthorizationCacheDao().upsert(WorkspaceAuthorizationCacheEntity(issuer, a.toString(),
        """{"contract_version":1,"issuer":"$issuer/auth/v1","subject":"$a","verified_at":"$now","token_expires_at":"${now.plusSeconds(3600)}","workspaces":[${ids.joinToString(",") { """{"id":"$it","name":"Olivar","role":"VIEWER","permissions":["read"],"grant_version":1,"authorization_expires_at":"${now.plusSeconds(900)}"}""" }}]}"""))
    private fun sql(statement: String) = db.openHelper.writableDatabase.execSQL(statement)
    private fun ms(instant: String) = Instant.parse(instant).toEpochMilli()
    private fun farm(id: UUID, farmName: String) =
        sql("INSERT INTO farms(id,workspace_id,name,status,created_at,updated_at,version,sync_status,remote_version) VALUES ('$id','$workspace','$farmName','ACTIVE',1000,1000,1,'SYNCED',1)")
    private fun membership(id: UUID, farm: UUID, from: String, until: String?, version: Int) =
        sql("INSERT INTO farm_parcel_memberships(id,workspace_id,farm_id,parcel_id,valid_from,valid_until,created_at,updated_at,version,sync_status,remote_version) VALUES ('$id','$workspace','$farm','$parcel',${ms(from)},${until?.let(::ms) ?: "NULL"},1000,1000,$version,'SYNCED',$version)")
    /** As the v2 receiver stores a real archive: status ARCHIVED, deletedAt = archivedAt, closed period. */
    private fun archivedParcelIn(farm: UUID, from: String, until: String) {
        sql("INSERT INTO parcels(id,workspace_id,display_name,source,status,created_at,updated_at,deleted_at,version,sync_status,remote_version) VALUES ('$parcel','$workspace','Parcela','MANUAL','ARCHIVED',1000,1000,${ms(until)},2,'SYNCED',2)")
        membership(period, farm, from, until, 2)
    }
    private fun activeParcelIn(farm: UUID, from: String) {
        sql("INSERT INTO parcels(id,workspace_id,display_name,source,status,created_at,updated_at,version,sync_status,remote_version) VALUES ('$parcel','$workspace','Parcela','MANUAL','ACTIVE',1000,1000,1,'SYNCED',1)")
        membership(period, farm, from, null, 1)
    }
    private fun rows(query: String): List<String> = db.openHelper.readableDatabase.query(query).use { cursor ->
        buildList { while (cursor.moveToNext()) add((0 until cursor.columnCount).joinToString("|") { cursor.getString(it) ?: "∅" }) }
    }
    private fun parentRows() = rows("SELECT * FROM farms ORDER BY id") + rows("SELECT * FROM parcels ORDER BY id") +
        rows("SELECT * FROM farm_parcel_memberships ORDER BY id")
    private fun allRows() = listOf("workspaces", "farms", "parcels", "farm_parcel_memberships", "campaigns", "campaign_parcels", "sync_outbox")
        .flatMap { rows("SELECT * FROM $it ORDER BY id") }

    /** `get_own_campaign_receive_v1`: a 2025/26 campaign of north with one snapshot of [parcel]. */
    private inner class Server : DeliveryRpcTransport {
        var status = "CLOSED"
        var endDate: String? = "2026-02-28"
        var version = 1
        var name = "2025/26"
        var snapshotName = "Parcela 2025"
        var subject = a
        var issuerOverride: String? = null
        var duringRead: (suspend () -> Unit)? = null
        override suspend fun rpc(name: String, bodyJson: String, accessToken: String): DeliveryRpcResponse {
            assertEquals("get_own_campaign_receive_v1", name)
            duringRead?.invoke()
            val end = endDate?.let { "\"$it\"" } ?: "null"
            return DeliveryRpcResponse(200, """{"contract_version":1,"issuer":"${issuerOverride ?: "$issuer/auth/v1"}","subject":"$subject","campaign":{"id":"$campaign","workspaceId":"$workspace","farmId":"$north","name":"${this.name}","startDate":"2025-10-01","endDate":$end,"status":"$status","notes":null,"version":$version,"createdAt":"2025-10-01T08:00:00Z","updatedAt":"2026-02-28T18:00:00Z","snapshotFormat":1},"snapshots":[{"id":"$snapshot","workspaceId":"$workspace","campaignId":"$campaign","parcelId":"$parcel","farmIdAtStart":"$north","farmNameAtStart":"Finca Norte","parcelNameAtStart":"$snapshotName","managedAreaM2AtStart":8000.0,"cadastralReferenceAtStart":"23044A00400030","geometryGeoJsonSnapshot":"{\"type\":\"Polygon\"}"}]}""")
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
