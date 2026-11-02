package com.isivoltpro.maginaolivo.data.local

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.core.dispatchers.DefaultAppDispatchers
import com.isivoltpro.maginaolivo.core.id.UuidGenerator
import com.isivoltpro.maginaolivo.core.regional.RegionalContext
import com.isivoltpro.maginaolivo.core.time.SystemAppClock
import com.isivoltpro.maginaolivo.data.local.entity.WorkspaceAuthorizationCacheEntity
import com.isivoltpro.maginaolivo.data.local.model.OutboxStatus
import com.isivoltpro.maginaolivo.data.local.model.RecordStatus
import com.isivoltpro.maginaolivo.data.local.model.SyncEntityType
import com.isivoltpro.maginaolivo.data.repository.AndroidFarmLinkLedger
import com.isivoltpro.maginaolivo.data.repository.HttpsWorkspaceAuthorizationRemote
import com.isivoltpro.maginaolivo.data.repository.LocalFarmLinkRepository
import com.isivoltpro.maginaolivo.data.repository.LocalWorkspaceRepository
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstFarmRepository
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstParcelRepository
import com.isivoltpro.maginaolivo.data.repository.sync.DeliveryDispatchLease
import com.isivoltpro.maginaolivo.data.repository.sync.FarmSendCoordinator
import com.isivoltpro.maginaolivo.data.repository.sync.FarmSendOutcome
import com.isivoltpro.maginaolivo.data.repository.sync.HttpsDeliveryRpcTransport
import com.isivoltpro.maginaolivo.data.repository.sync.ParcelMutationOutcome
import com.isivoltpro.maginaolivo.data.repository.sync.ParcelMutationSendCoordinator
import com.isivoltpro.maginaolivo.data.repository.sync.ParcelSendCoordinator
import com.isivoltpro.maginaolivo.data.repository.sync.ParcelSendOutcome
import com.isivoltpro.maginaolivo.data.repository.sync.ParentReceiveOutcome
import com.isivoltpro.maginaolivo.data.repository.sync.ParentReceiveV2Coordinator
import com.isivoltpro.maginaolivo.data.repository.sync.WorkspaceDraft
import com.isivoltpro.maginaolivo.data.repository.sync.WorkspaceSendCoordinator
import com.isivoltpro.maginaolivo.data.repository.sync.WorkspaceSendOutcome
import com.isivoltpro.maginaolivo.domain.farm.NewFarm
import com.isivoltpro.maginaolivo.domain.parcel.NewParcel
import com.isivoltpro.maginaolivo.domain.parcel.ParcelChanges
import com.isivoltpro.maginaolivo.feature.account.AccountRecord
import com.isivoltpro.maginaolivo.feature.account.AccountRepository
import com.isivoltpro.maginaolivo.feature.account.AuthConfiguration
import com.isivoltpro.maginaolivo.feature.account.AuthSession
import com.isivoltpro.maginaolivo.feature.account.HttpsAuthTransport
import com.isivoltpro.maginaolivo.feature.account.MemoryAccountSessionStore
import com.isivoltpro.maginaolivo.feature.account.SupabaseAuthApi
import java.io.File
import java.net.URL
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.time.Instant
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.UUID
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * G2.6 2c-3 explicit DEV-only probe against a disposable local Supabase with a pinned test certificate.
 * Device A creates and sends its WORKSPACE, FARMs and PARCELs, then UPDATE/ARCHIVE/RESTORE through the
 * real #805 RPCs; device B (its own Room, same account) receives them with the 2c-2 receiver over
 * `get_own_workspace_parents_v2`. Scenes C1-C4, C7 and C9 of #810 §5; A, B and the server are compared
 * at Room's millisecond precision. No campaign, delivery, scheduler or historical emulator is involved.
 */
@RunWith(AndroidJUnit4::class)
class ParcelConvergenceHttpsEphemeralTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val issuer = "https://zubpicdibqbmewxeurhm.supabase.co"
    private val endpoint = "https://10.0.2.2:54443"
    private val names = List(3) { "g26-convergence-https-$it-${UUID.randomUUID()}" }
    private val ledgerFile = File(context.noBackupFilesDir, "g26-convergence-ledger-${UUID.randomUUID()}.json")
    private lateinit var originalTls: SSLSocketFactory
    private lateinit var fixture: JSONObject
    private lateinit var config: AuthConfiguration
    private lateinit var lease: DeliveryDispatchLease
    private lateinit var authorizations: HttpsWorkspaceAuthorizationRemote

    @Before fun setup() {
        val encoded = InstrumentationRegistry.getArguments().getString("g26_test_cert")
            ?: error("Disposable TLS certificate required")
        val certificate = CertificateFactory.getInstance("X.509")
            .generateCertificate(Base64.getDecoder().decode(encoded).inputStream())
        val store = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null); setCertificateEntry("g26-ephemeral", certificate)
        }
        val manager = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(store) }
        val tls = javax.net.ssl.SSLContext.getInstance("TLS").apply { init(null, manager.trustManagers, null) }
        originalTls = HttpsURLConnection.getDefaultSSLSocketFactory()
        HttpsURLConnection.setDefaultSSLSocketFactory(tls.socketFactory)
        fixture = JSONObject(fetch("/__g26_fixture", "GET"))
        config = AuthConfiguration(endpoint, fixture.getString("anonKey"), "qa://auth/callback")
        lease = DeliveryDispatchLease.tryAcquire(File(context.noBackupFilesDir, "g26-convergence-https"))!!
        authorizations = HttpsWorkspaceAuthorizationRemote(endpoint, fixture.getString("anonKey"))
    }

    @After fun cleanup() {
        if (::lease.isInitialized) lease.close()
        names.forEach { context.deleteDatabase(it) }
        File(ledgerFile.path).delete(); File(ledgerFile.path + ".bak").delete()
        if (::originalTls.isInitialized) HttpsURLConnection.setDefaultSSLSocketFactory(originalTls)
    }

    @Test fun twoDevicesConvergeOverRealRpcsWithLostResponsesAndIdentityChecks() = runBlocking {
        val actor = UUID.fromString(fixture.getString("actor"))
        val session = session(actor, "token")
        val (accountA, _) = account(session)
        val (accountB, storeB) = account(session)
        val mutations = HttpsDeliveryRpcTransport(config, scope = HttpsDeliveryRpcTransport.RpcScope.PARCEL_MUTATION_SEND)
        val parents = HttpsDeliveryRpcTransport(config, scope = HttpsDeliveryRpcTransport.RpcScope.PARENT_RECEIVE_V2)
        val a = MaginaOlivoDatabase.create(context, names[0])
        val b = MaginaOlivoDatabase.create(context, names[1])
        try {
            val workspace = sendWorkspace(a, accountA)
            refreshGrant(a, actor, session)
            assertTrue(selector(a, accountA).selectWorkspace(workspace) is AppResult.Success)
            val links = LocalFarmLinkRepository(context, a, accountA, issuer, AndroidFarmLinkLedger(ledgerFile))
            val north = sendFarm(a, accountA, actor, links, workspace, "Finca Norte")
            val south = sendFarm(a, accountA, actor, links, workspace, "Finca Sur")
            val p = sendParcel(a, accountA, links, north, "Parcela P")
            val q = sendParcel(a, accountA, links, north, "Parcela Q")
            val r = sendParcel(a, accountA, links, north, "Parcela R")
            fun sender() = ParcelMutationSendCoordinator(a, accountA, issuer, mutations, { links.ledgerFor(it) })
            suspend fun push(parcel: UUID) = a.syncOutboxDao().listForEntity(SyncEntityType.PARCEL, parcel)
                .filter { it.status != OutboxStatus.CONFIRMED }.map { op ->
                    assertEquals(ParcelMutationOutcome.STAGED, sender().stage(op.id))
                    sender().send(op.id, lease)
                }
            suspend fun receiveB() = ParentReceiveV2Coordinator(b, accountB, issuer, parents).receive(workspace)

            refreshGrant(b, actor, session)
            assertEquals(ParentReceiveOutcome.IMPORTED, receiveB().outcome)
            for (parcel in listOf(p, q, r)) converged("BASE", a, b, parcel)

            // C1: offline edit on A, sent; B receives; a repeated receive writes nothing.
            assertTrue(parcels(a).update(p, ParcelChanges("Parcela P editada", managedAreaM2 = 2500.0)) is AppResult.Success)
            assertEquals(listOf(ParcelMutationOutcome.SYNCED), push(p))
            assertEquals(ParentReceiveOutcome.IMPORTED, receiveB().outcome)
            converged("C1", a, b, p)
            val stable = snapshot(b)
            assertEquals(ParentReceiveOutcome.ALREADY_CURRENT, receiveB().outcome)
            assertEquals(stable, snapshot(b))

            // C2: archive on A reaches B with its exact instant (millisecond floor) and closed period.
            assertEquals(AppResult.Success(Unit), parcels(a).archive(p))
            assertEquals(listOf(ParcelMutationOutcome.SYNCED), push(p))
            assertEquals(ParentReceiveOutcome.IMPORTED, receiveB().outcome)
            converged("C2", a, b, p)
            assertEquals(RecordStatus.ARCHIVED, b.parcelDao().findById(p)?.status)

            // C4: restore in another farm applied on B's clean archived copy.
            assertEquals(AppResult.Success(Unit), parcels(a).restore(p, south))
            assertEquals(listOf(ParcelMutationOutcome.SYNCED), push(p))
            assertEquals(ParentReceiveOutcome.IMPORTED, receiveB().outcome)
            converged("C4", a, b, p)
            assertEquals(south, b.parcelDao().findCurrentMembership(p)?.farmId)

            // C3: archive and restore of Q, received by B once at the end.
            assertEquals(AppResult.Success(Unit), parcels(a).archive(q))
            Thread.sleep(5)
            assertEquals(AppResult.Success(Unit), parcels(a).restore(q, south))
            assertEquals(listOf(ParcelMutationOutcome.CONFIRMED_LOCAL_PENDING, ParcelMutationOutcome.SYNCED), push(q))
            assertEquals(ParentReceiveOutcome.IMPORTED, receiveB().outcome)
            converged("C3", a, b, q)
            assertEquals(2, b.parcelDao().listMemberships(q).size)

            // C7: the ARCHIVE response is lost after Supabase committed; B receives before A recovers.
            assertEquals(AppResult.Success(Unit), parcels(a).archive(r))
            val op = a.syncOutboxDao().listForEntity(SyncEntityType.PARCEL, r).single { it.status != OutboxStatus.CONFIRMED }.id
            assertEquals(ParcelMutationOutcome.STAGED, sender().stage(op))
            fetch("/__g26_admin/mutation_mode?mode=drop", "POST")
            assertEquals(ParcelMutationOutcome.OUTCOME_UNKNOWN, sender().send(op, lease))
            fetch("/__g26_admin/mutation_mode?mode=normal", "POST")
            assertEquals("ARCHIVED", remote(r).getString("status"))
            assertEquals(ParentReceiveOutcome.IMPORTED, receiveB().outcome)
            assertEquals(canonical(remote(r)), canonical(b, r))
            assertEquals(ParcelMutationOutcome.SYNCED, sender().send(op, lease))
            assertEquals(2, remote(r).getInt("receipts"))
            converged("C7", a, b, r)

            // C9: another account, a logout and a lost grant receive nothing; B stays byte-identical.
            val before = snapshot(b)
            val foreignActor = UUID.fromString(fixture.getString("foreignActor"))
            val (foreign, _) = account(session(foreignActor, "foreignToken"))
            val forged = MaginaOlivoDatabase.create(context, names[2])
            try {
                val now = Instant.now()
                forged.workspaceAuthorizationCacheDao().upsert(WorkspaceAuthorizationCacheEntity(issuer, foreignActor.toString(),
                    """{"contract_version":1,"issuer":"$issuer/auth/v1","subject":"$foreignActor","verified_at":"$now","token_expires_at":"${now.plusSeconds(600)}","workspaces":[{"id":"$workspace","name":"x","role":"OWNER","permissions":["read","write"],"grant_version":1,"authorization_expires_at":"${now.plusSeconds(600)}"}]}"""))
                assertEquals(ParentReceiveOutcome.NOT_AUTHORIZED,
                    ParentReceiveV2Coordinator(forged, foreign, issuer, parents).receive(workspace).outcome)
                assertEquals(0, count(forged, "parcels"))
            } finally { forged.close() }
            val now = Instant.now()
            b.workspaceAuthorizationCacheDao().upsert(WorkspaceAuthorizationCacheEntity(issuer, actor.toString(),
                """{"contract_version":1,"issuer":"$issuer/auth/v1","subject":"$actor","verified_at":"$now","token_expires_at":"${now.plusSeconds(600)}","workspaces":[]}"""))
            assertEquals(ParentReceiveOutcome.NOT_AUTHORIZED, receiveB().outcome)
            refreshGrant(b, actor, session)
            accountB.logout(false)
            assertEquals(ParentReceiveOutcome.NOT_AUTHORIZED, receiveB().outcome)
            assertEquals(before, snapshot(b))
            storeB.write(AccountRecord(session = session))
        } finally { a.close(); b.close() }
    }

    // ---- comparison at Room precision ------------------------------------------------------------

    private fun floor(text: String?): Long? = text?.takeIf { it != "null" }
        ?.let { OffsetDateTime.parse(it).toInstant().truncatedTo(ChronoUnit.MILLIS).toEpochMilli() }
    private fun canonical(server: JSONObject): String {
        val periods = server.getJSONArray("memberships").let { array -> (0 until array.length()).map { array.getJSONObject(it) } }
        return listOf(server.getString("name"), server.getString("status"),
            floor(if (server.isNull("archivedAt")) null else server.getString("archivedAt")), server.getLong("version"),
            periods.sortedBy { it.getString("id") }.joinToString(";") {
                "${it.getString("id")}|${it.getString("farmId")}|${floor(it.getString("validFrom"))}|" +
                    "${floor(if (it.isNull("validUntil")) null else it.getString("validUntil"))}|${it.getLong("version")}"
            }).joinToString("#")
    }
    private suspend fun canonical(db: MaginaOlivoDatabase, parcel: UUID): String {
        val row = db.parcelDao().findById(parcel)!!
        val periods = db.parcelDao().listMemberships(parcel).filter { it.metadata.deletedAt == null }.sortedBy { it.id.toString() }
        return listOf(row.displayName, row.status.name, row.metadata.deletedAt?.toEpochMilli(), row.metadata.remoteVersion,
            periods.joinToString(";") { "${it.id}|${it.farmId}|${it.validFrom.toEpochMilli()}|${it.validUntil?.toEpochMilli()}|${it.metadata.remoteVersion}" },
        ).joinToString("#")
    }
    private suspend fun converged(scene: String, a: MaginaOlivoDatabase, b: MaginaOlivoDatabase, parcel: UUID) {
        val server = canonical(remote(parcel))
        assertEquals("$scene A", server, canonical(a, parcel))
        assertEquals("$scene B", server, canonical(b, parcel))
        for (db in listOf(a, b)) {
            assertTrue("$scene outbox", db.syncOutboxDao().listForEntity(SyncEntityType.PARCEL, parcel).all { it.status == OutboxStatus.CONFIRMED })
            db.parcelDao().findById(parcel)!!.metadata.let { assertEquals("$scene version", it.remoteVersion, it.version) }
        }
        Log.i("G26CONV", "G26-CONV-HTTPS $scene CONVERGED parcel=${parcel.toString().take(8)} version=${remote(parcel).getLong("version")}")
    }

    // ---- real producers and senders of #798-#800 -------------------------------------------------

    private suspend fun sendWorkspace(db: MaginaOlivoDatabase, account: AccountRepository): UUID {
        val transport = HttpsDeliveryRpcTransport(config, scope = HttpsDeliveryRpcTransport.RpcScope.WORKSPACE_SEND)
        val staged = WorkspaceSendCoordinator(db, account, issuer, transport)
            .createOwned(WorkspaceDraft("Olivar convergencia", "ES", "Europe/Madrid", "es-ES", "EUR"))
        assertEquals(WorkspaceSendOutcome.SYNCED, WorkspaceSendCoordinator(db, account, issuer, transport).send(staged.operationId!!, lease))
        return staged.workspaceId!!
    }
    private suspend fun sendFarm(db: MaginaOlivoDatabase, account: AccountRepository, userId: UUID,
        links: LocalFarmLinkRepository, workspace: UUID, name: String): UUID {
        val farm = (farms(db, account).create(NewFarm(workspace, name, municipality = "Bedmar", province = "Jaén"))
            as AppResult.Success).value
        val op = db.syncOutboxDao().listForEntity(SyncEntityType.FARM, farm).single().id
        links.consent(userId, links.choices().filter { it.id == farm }, accepted = true)
        val sender = FarmSendCoordinator(db, account, issuer,
            HttpsDeliveryRpcTransport(config, scope = HttpsDeliveryRpcTransport.RpcScope.FARM_SEND), { links.ledgerFor(it) })
        assertEquals(FarmSendOutcome.STAGED, sender.stage(op))
        assertEquals(FarmSendOutcome.SYNCED, sender.send(op, lease))
        return farm
    }
    private suspend fun sendParcel(db: MaginaOlivoDatabase, account: AccountRepository, links: LocalFarmLinkRepository,
        farm: UUID, name: String): UUID {
        val parcel = (parcels(db).create(NewParcel(farm, name, municipality = "Bedmar", province = "Jaén",
            managedAreaM2 = 1234.5)) as AppResult.Success).value
        val op = db.syncOutboxDao().listForEntity(SyncEntityType.PARCEL, parcel).single().id
        val sender = ParcelSendCoordinator(db, account, issuer,
            HttpsDeliveryRpcTransport(config, scope = HttpsDeliveryRpcTransport.RpcScope.PARCEL_SEND), { links.ledgerFor(it) })
        assertEquals(ParcelSendOutcome.STAGED, sender.stage(op))
        assertEquals(ParcelSendOutcome.SYNCED, sender.send(op, lease))
        return parcel
    }
    private suspend fun refreshGrant(db: MaginaOlivoDatabase, actor: UUID, session: AuthSession) =
        db.workspaceAuthorizationCacheDao().upsert(WorkspaceAuthorizationCacheEntity(issuer, actor.toString(), authorizations.list(session)))
    private fun selector(db: MaginaOlivoDatabase, account: AccountRepository) = LocalWorkspaceRepository(
        db, SystemAppClock(), UuidGenerator(), DefaultAppDispatchers(), RegionalContext.spainDefault(), account, issuer)
    private fun farms(db: MaginaOlivoDatabase, account: AccountRepository) =
        OfflineFirstFarmRepository(db, SystemAppClock(), UuidGenerator(), DefaultAppDispatchers(), selector(db, account))
    private fun parcels(db: MaginaOlivoDatabase) = OfflineFirstParcelRepository(db, SystemAppClock(), UuidGenerator(), DefaultAppDispatchers())
    private fun remote(id: UUID) = JSONObject(fetch("/__g26_admin/parcel?id=$id", "GET"))
    private fun session(actor: UUID, key: String): AuthSession {
        val token = fixture.getString(key)
        val expiration = JSONObject(String(Base64.getUrlDecoder().decode(token.split('.')[1]))).getLong("exp")
        return AuthSession(actor, "qa@example.test", token, "unused", Instant.ofEpochSecond(expiration))
    }
    private fun account(session: AuthSession): Pair<AccountRepository, MemoryAccountSessionStore> {
        val store = MemoryAccountSessionStore().apply { write(AccountRecord(session = session)) }
        return AccountRepository(SupabaseAuthApi(config, HttpsAuthTransport(config)), store) to store
    }
    private fun fetch(path: String, method: String): String {
        val connection = URL("$endpoint$path").openConnection() as HttpsURLConnection
        return try {
            connection.requestMethod = method
            if (method == "POST") { connection.doOutput = true; connection.outputStream.use { } }
            check(connection.responseCode in 200..299)
            if (connection.responseCode == 204) "" else connection.inputStream.bufferedReader().use { it.readText() }
        } finally { connection.disconnect() }
    }
    private fun count(db: MaginaOlivoDatabase, table: String) =
        db.openHelper.readableDatabase.query("SELECT count(*) FROM $table").use { it.moveToFirst(); it.getInt(0) }
    private fun snapshot(db: MaginaOlivoDatabase) = listOf("workspaces", "farms", "parcels", "farm_parcel_memberships", "sync_outbox")
        .flatMap { table ->
            db.openHelper.readableDatabase.query("SELECT * FROM $table ORDER BY id").use { cursor ->
                buildList { while (cursor.moveToNext()) add((0 until cursor.columnCount).joinToString("|") { cursor.getString(it) ?: "∅" }) }
            }
        }
}
