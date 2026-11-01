package com.isivoltpro.maginaolivo.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.core.dispatchers.DefaultAppDispatchers
import com.isivoltpro.maginaolivo.core.id.IdGenerator
import com.isivoltpro.maginaolivo.core.id.UuidGenerator
import com.isivoltpro.maginaolivo.core.regional.RegionalContext
import com.isivoltpro.maginaolivo.core.time.SystemAppClock
import com.isivoltpro.maginaolivo.data.local.entity.WorkspaceAuthorizationCacheEntity
import com.isivoltpro.maginaolivo.data.local.model.OutboxOperation
import com.isivoltpro.maginaolivo.data.local.model.OutboxStatus
import com.isivoltpro.maginaolivo.data.local.model.SyncEntityType
import com.isivoltpro.maginaolivo.data.local.model.SyncStatus
import com.isivoltpro.maginaolivo.data.repository.AndroidFarmLinkLedger
import com.isivoltpro.maginaolivo.data.repository.HttpsWorkspaceAuthorizationRemote
import com.isivoltpro.maginaolivo.data.repository.LocalFarmLinkRepository
import com.isivoltpro.maginaolivo.data.repository.LocalWorkspaceRepository
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstFarmRepository
import com.isivoltpro.maginaolivo.data.repository.sync.DeliveryDispatchLease
import com.isivoltpro.maginaolivo.data.repository.sync.FarmSendCoordinator
import com.isivoltpro.maginaolivo.data.repository.sync.FarmSendOutcome
import com.isivoltpro.maginaolivo.data.repository.sync.HttpsDeliveryRpcTransport
import com.isivoltpro.maginaolivo.data.repository.sync.ParentConflict
import com.isivoltpro.maginaolivo.data.repository.sync.ParentKind
import com.isivoltpro.maginaolivo.data.repository.sync.ParentReceiveCoordinator
import com.isivoltpro.maginaolivo.data.repository.sync.ParentReceiveOutcome
import com.isivoltpro.maginaolivo.data.repository.sync.WorkspaceDraft
import com.isivoltpro.maginaolivo.data.repository.sync.WorkspaceSendCoordinator
import com.isivoltpro.maginaolivo.data.repository.sync.WorkspaceSendOutcome
import com.isivoltpro.maginaolivo.domain.farm.FarmChanges
import com.isivoltpro.maginaolivo.domain.farm.NewFarm
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
 * Explicit DEV-only probe against a disposable local Supabase with a pinned test certificate.
 * Device A sends its WORKSPACE (#798) and then FARM rows through the G1c reservation and
 * `create_reserved_farm`, with real G1b consent; device B starts empty and receives them with the
 * #797 parent receiver. No PARCEL, membership or CAMPAIGN is sent.
 */
@RunWith(AndroidJUnit4::class)
class FarmSendHttpsEphemeralTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val issuer = "https://zubpicdibqbmewxeurhm.supabase.co"
    private val endpoint = "https://10.0.2.2:54443"
    private val names = List(4) { "g26-farm-send-${it}-${UUID.randomUUID()}" }
    private val ledgers = List(2) { File(context.noBackupFilesDir, "g26-farm-send-ledger-$it-${UUID.randomUUID()}.json") }
    private lateinit var originalTls: SSLSocketFactory
    private lateinit var fixture: JSONObject
    private lateinit var config: AuthConfiguration
    private lateinit var lease: DeliveryDispatchLease

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
        lease = DeliveryDispatchLease.tryAcquire(File(context.noBackupFilesDir, "g26-farm-send-https"))!!
    }

    @After fun cleanup() {
        if (::lease.isInitialized) lease.close()
        names.forEach { context.deleteDatabase(it) }
        ledgers.forEach { File(it.path).delete(); File(it.path + ".bak").delete() }
        if (::originalTls.isInitialized) HttpsURLConnection.setDefaultSSLSocketFactory(originalTls)
    }

    @Test fun deviceASendsOnlyFarmsAndDeviceBReceivesThemWithIsolation() = runBlocking {
        val actor = UUID.fromString(fixture.getString("actor"))
        val session = session(actor, "token")
        val (account, sessions) = account(session)
        val send = HttpsDeliveryRpcTransport(config, scope = HttpsDeliveryRpcTransport.RpcScope.FARM_SEND)
        val slow = HttpsDeliveryRpcTransport(config, readTimeoutMillis = 3_000, scope = HttpsDeliveryRpcTransport.RpcScope.FARM_SEND)
        val wsSend = HttpsDeliveryRpcTransport(config, scope = HttpsDeliveryRpcTransport.RpcScope.WORKSPACE_SEND)
        val parents = HttpsDeliveryRpcTransport(config, scope = HttpsDeliveryRpcTransport.RpcScope.PARENT_RECEIVE)
        val authorizations = HttpsWorkspaceAuthorizationRemote(endpoint, fixture.getString("anonKey"))

        var a = MaginaOlivoDatabase.create(context, names[0])
        val workspace: UUID
        val farm: UUID
        val edited: UUID
        try {
            workspace = sendWorkspace(a, account, wsSend, "Olivar QA A")
            a.workspaceAuthorizationCacheDao().upsert(WorkspaceAuthorizationCacheEntity(issuer, actor.toString(), authorizations.list(session)))
            assertTrue(selector(a, account).selectWorkspace(workspace) is AppResult.Success)
            var links = LocalFarmLinkRepository(context, a, account, issuer, AndroidFarmLinkLedger(ledgers[0]))
            fun sender(db: MaginaOlivoDatabase = a, transport: HttpsDeliveryRpcTransport = send) =
                FarmSendCoordinator(db, account, issuer, transport, { links.ledgerFor(it) })

            // No G1b consent yet: nothing is attributed or sent.
            val (created, op) = createFarm(a, account, workspace, "Finca Norte")
            farm = created
            assertEquals(FarmSendOutcome.CONSENT_REQUIRED, sender().stage(op))
            assertEquals(0, remoteFarm(farm).getInt("reservations"))
            links.consent(actor, links.choices().filter { it.id == farm }, accepted = true)
            assertEquals(FarmSendOutcome.STAGED, sender().stage(op))
            val payload = a.syncOutboxDao().findById(op)!!.payloadJson

            // Lost response after the server committed, then a read timeout on the replay.
            fetch("/__g26_admin/create_mode?mode=drop", "POST")
            assertEquals(FarmSendOutcome.OUTCOME_UNKNOWN, sender().send(op, lease))
            assertEquals(listOf(1, 1, 1), listOf("rows", "receipts", "reservations").map(remoteFarm(farm)::getInt))
            fetch("/__g26_admin/create_mode?mode=delay", "POST")
            assertEquals(FarmSendOutcome.OUTCOME_UNKNOWN, sender(transport = slow).send(op, lease))
            fetch("/__g26_admin/create_mode?mode=normal", "POST")
            assertEquals(OutboxStatus.FAILED, a.syncOutboxDao().findById(op)?.status)
            assertEquals(payload, a.syncOutboxDao().findById(op)?.payloadJson)

            a.close(); a = MaginaOlivoDatabase.create(context, names[0])
            links = LocalFarmLinkRepository(context, a, account, issuer, AndroidFarmLinkLedger(ledgers[0]))
            assertEquals(FarmSendOutcome.SYNCED, sender().send(op, lease))
            assertEquals(FarmSendOutcome.ALREADY_CONFIRMED, sender().send(op, lease))
            val state = remoteFarm(farm)
            assertEquals(listOf(1, 1, 1, 1), listOf("rows", "receipts", "reservations", "version").map(state::getInt))
            assertEquals("$actor/$actor", state.getString("owner"))
            assertEquals("Finca Norte", state.getString("name"))
            assertEquals(SyncStatus.SYNCED, a.farmDao().findById(farm)?.metadata?.syncStatus)

            // Offline edit after staging: confirmed remotely as staged, the local edit stays pending.
            val (second, secondOp) = createFarm(a, account, workspace, "Finca Sur")
            edited = second
            links.consent(actor, links.choices().filter { it.id == second }, accepted = true)
            assertEquals(FarmSendOutcome.STAGED, sender().stage(secondOp))
            assertTrue(farms(a, account).update(second, FarmChanges("Finca Sur editada offline", municipality = "Jódar")) is AppResult.Success)
            assertEquals(FarmSendOutcome.CONFIRMED_LOCAL_PENDING, sender().send(secondOp, lease))
            assertEquals("Finca Sur", remoteFarm(second).getString("name"))
            assertEquals("Finca Sur editada offline", a.farmDao().findById(second)?.name)
            assertEquals(listOf(OutboxOperation.CREATE to OutboxStatus.CONFIRMED, OutboxOperation.UPDATE to OutboxStatus.PENDING),
                a.syncOutboxDao().listForEntity(SyncEntityType.FARM, second).map { it.operation to it.status })

            // A reads its own farms back: the synced one adopts server instants, the edited one is protected.
            a.workspaceAuthorizationCacheDao().upsert(WorkspaceAuthorizationCacheEntity(issuer, actor.toString(), authorizations.list(session)))
            val back = ParentReceiveCoordinator(a, account, issuer, parents).receive(workspace)
            assertEquals(listOf(ParentConflict(ParentKind.FARM, second, "local_pending")), back.conflicts)
            assertEquals("Finca Sur editada offline", a.farmDao().findById(second)?.name)
            val again = ParentReceiveCoordinator(a, account, issuer, parents).receive(workspace)
            assertEquals(0, again.inserted + again.updated)

            // A third farm whose G1b consent is revoked locally is never sent; its outbox is kept.
            val (third, thirdOp) = createFarm(a, account, workspace, "Finca Revocada")
            links.consent(actor, links.choices().filter { it.id == third }, accepted = true)
            assertEquals(FarmSendOutcome.STAGED, sender().stage(thirdOp))
            links.revokeConsentForFarm(third)
            assertEquals(FarmSendOutcome.CONSENT_REQUIRED, sender().send(thirdOp, lease))
            assertEquals(OutboxStatus.BLOCKED, a.syncOutboxDao().findById(thirdOp)?.status)
            assertEquals(0, remoteFarm(third).getInt("rows"))

            // Account switch, logout and offline never send a fourth staged farm.
            val (fourth, fourthOp) = createFarm(a, account, workspace, "Finca Pendiente")
            links.consent(actor, links.choices().filter { it.id == fourth }, accepted = true)
            assertEquals(FarmSendOutcome.STAGED, sender().stage(fourthOp))
            val pending = a.syncOutboxDao().findById(fourthOp)
            val foreignActor = UUID.fromString(fixture.getString("foreignActor"))
            val (switched, _) = account(session(foreignActor, "foreignToken"))
            assertEquals(FarmSendOutcome.NOT_AUTHORIZED,
                FarmSendCoordinator(a, switched, issuer, send, { links.ledgerFor(it) }).send(fourthOp, lease))
            account.logout(false)
            assertEquals(FarmSendOutcome.NOT_AUTHORIZED, sender().send(fourthOp, lease))
            sessions.write(AccountRecord(session = session))
            val offlineConfig = AuthConfiguration("https://10.0.2.2:9", fixture.getString("anonKey"), "qa://auth/callback")
            val offline = AccountRepository(SupabaseAuthApi(offlineConfig, HttpsAuthTransport(offlineConfig)), sessions)
            assertEquals(FarmSendOutcome.OFFLINE, FarmSendCoordinator(a, offline, issuer,
                HttpsDeliveryRpcTransport(offlineConfig, scope = HttpsDeliveryRpcTransport.RpcScope.FARM_SEND),
                { links.ledgerFor(it) }).send(fourthOp, lease))
            assertEquals(pending, a.syncOutboxDao().findById(fourthOp))
            assertEquals(0, remoteFarm(fourth).getInt("rows"))
        } finally { a.close() }

        // Device B: empty Room, same account; receives the same farm UUIDs and the staged content.
        // Device B has its own session for the same account; A's repository was logged out above.
        val (deviceB, _) = account(session)
        val b = MaginaOlivoDatabase.create(context, names[1])
        try {
            b.workspaceAuthorizationCacheDao().upsert(WorkspaceAuthorizationCacheEntity(issuer, actor.toString(), authorizations.list(session)))
            val received = ParentReceiveCoordinator(b, deviceB, issuer, parents).receive(workspace)
            assertEquals(ParentReceiveOutcome.IMPORTED, received.outcome)
            assertEquals(3, received.inserted)
            assertEquals("Finca Norte", b.farmDao().findById(farm)?.name)
            assertEquals("Finca Sur", b.farmDao().findById(edited)?.name)
            assertEquals(SyncStatus.SYNCED, b.farmDao().findById(farm)?.metadata?.syncStatus)
            assertEquals(listOf(1, 2, 0), counts(b))
            assertTrue(selector(b, deviceB).selectWorkspace(workspace) is AppResult.Success)
            assertEquals(ParentReceiveOutcome.ALREADY_CURRENT,
                ParentReceiveCoordinator(b, deviceB, issuer, parents).receive(workspace).outcome)
        } finally { b.close() }

        // C: cannot receive A's farms, cannot create one in A's workspace, cannot take A's farm UUID.
        val foreignActor = UUID.fromString(fixture.getString("foreignActor"))
        val foreignSession = session(foreignActor, "foreignToken")
        val (foreign, _) = account(foreignSession)
        val c = MaginaOlivoDatabase.create(context, names[2])
        try {
            val now = Instant.now()
            c.workspaceAuthorizationCacheDao().upsert(WorkspaceAuthorizationCacheEntity(issuer, foreignActor.toString(),
                """{"contract_version":1,"issuer":"$issuer/auth/v1","subject":"$foreignActor","verified_at":"$now","token_expires_at":"${now.plusSeconds(600)}","workspaces":[{"id":"$workspace","name":"x","role":"OWNER","permissions":["read","write"],"grant_version":1,"authorization_expires_at":"${now.plusSeconds(600)}"}]}"""))
            assertEquals(ParentReceiveOutcome.NOT_AUTHORIZED,
                ParentReceiveCoordinator(c, foreign, issuer, parents).receive(workspace).outcome)
            assertEquals(listOf(0, 0, 0), counts(c))

            // C's own confirmed workspace, then a farm forced onto A's farm UUID.
            val own = sendWorkspace(c, foreign, wsSend, "Olivar QA C")
            c.workspaceAuthorizationCacheDao().upsert(WorkspaceAuthorizationCacheEntity(issuer, foreignActor.toString(),
                authorizations.list(foreignSession)))
            assertTrue(selector(c, foreign).selectWorkspace(own) is AppResult.Success)
            val links = LocalFarmLinkRepository(context, c, foreign, issuer, AndroidFarmLinkLedger(ledgers[1]))
            val ids = ArrayDeque(listOf(farm, UUID.randomUUID()))
            val claim = (farms(c, foreign, object : IdGenerator { override fun newId() = ids.removeFirst() })
                .create(NewFarm(own, "Toma de C")) as AppResult.Success).value
            assertEquals(farm, claim)
            links.consent(foreignActor, links.choices().filter { it.id == farm }, accepted = true)
            val claimOp = c.syncOutboxDao().listForEntity(SyncEntityType.FARM, farm).single().id
            val claimer = FarmSendCoordinator(c, foreign, issuer, send, { links.ledgerFor(it) })
            assertEquals(FarmSendOutcome.STAGED, claimer.stage(claimOp))
            assertEquals(FarmSendOutcome.CONFLICT, claimer.send(claimOp, lease))
            assertEquals("FARM_CONFLICT", c.syncOutboxDao().findById(claimOp)?.lastErrorCode)
            assertEquals("$actor/$actor", remoteFarm(farm).getString("owner"))
            assertEquals("Finca Norte", remoteFarm(farm).getString("name"))
        } finally { c.close() }

        // C forging a local copy of A's workspace still cannot create a farm in it: the server refuses.
        val forged = MaginaOlivoDatabase.create(context, names[3])
        try {
            val now = Instant.now()
            val sql = forged.openHelper.writableDatabase
            sql.execSQL("INSERT INTO workspaces(id,name,owner_user_id,country_code,timezone,locale,currency,created_at,updated_at,version,sync_status,remote_version) VALUES ('$workspace','Falso','$foreignActor','ES','Europe/Madrid','es-ES','EUR',1000,1000,1,'SYNCED',1)")
            forged.workspaceSelectionDao().upsert(com.isivoltpro.maginaolivo.data.local.entity.WorkspaceSelectionEntity(issuer, foreignActor.toString(), workspace))
            forged.workspaceAuthorizationCacheDao().upsert(WorkspaceAuthorizationCacheEntity(issuer, foreignActor.toString(),
                """{"contract_version":1,"issuer":"$issuer/auth/v1","subject":"$foreignActor","verified_at":"$now","token_expires_at":"${now.plusSeconds(600)}","workspaces":[{"id":"$workspace","name":"x","role":"OWNER","permissions":["read","write"],"grant_version":1,"authorization_expires_at":"${now.plusSeconds(600)}"}]}"""))
            val (intruder, intruderOp) = createFarm(forged, foreign, workspace, "Intrusa")
            val links = LocalFarmLinkRepository(context, forged, foreign, issuer, AndroidFarmLinkLedger(ledgers[1]))
            links.consent(foreignActor, links.choices().filter { it.id == intruder }, accepted = true)
            val intruderSender = FarmSendCoordinator(forged, foreign, issuer, send, { links.ledgerFor(it) })
            assertEquals(FarmSendOutcome.STAGED, intruderSender.stage(intruderOp))
            assertEquals(FarmSendOutcome.NOT_AUTHORIZED, intruderSender.send(intruderOp, lease))
            assertEquals(OutboxStatus.PENDING, forged.syncOutboxDao().findById(intruderOp)?.status)
            assertEquals(0, remoteFarm(intruder).getInt("rows"))
        } finally { forged.close() }

        // Server-side revocation: the farm commits, the response is lost, A deletes the workspace.
        // The replay is refused, the outbox and payload survive, and B can no longer receive.
        val (again, _) = account(session)
        a = MaginaOlivoDatabase.create(context, names[0])
        try {
            a.workspaceAuthorizationCacheDao().upsert(WorkspaceAuthorizationCacheEntity(issuer, actor.toString(), authorizations.list(session)))
            val links = LocalFarmLinkRepository(context, a, again, issuer, AndroidFarmLinkLedger(ledgers[0]))
            val (last, lastOp) = createFarm(a, again, workspace, "Finca Final")
            links.consent(actor, links.choices().filter { it.id == last }, accepted = true)
            val lastSender = FarmSendCoordinator(a, again, issuer, send, { links.ledgerFor(it) })
            assertEquals(FarmSendOutcome.STAGED, lastSender.stage(lastOp))
            fetch("/__g26_admin/create_mode?mode=drop", "POST")
            assertEquals(FarmSendOutcome.OUTCOME_UNKNOWN, lastSender.send(lastOp, lease))
            fetch("/__g26_admin/create_mode?mode=normal", "POST")
            fetch("/__g26_admin/delete_workspace?id=$workspace", "POST")
            val payload = a.syncOutboxDao().findById(lastOp)?.payloadJson
            assertEquals(FarmSendOutcome.NOT_AUTHORIZED, lastSender.send(lastOp, lease))
            assertEquals(OutboxStatus.FAILED, a.syncOutboxDao().findById(lastOp)?.status)
            assertEquals(payload, a.syncOutboxDao().findById(lastOp)?.payloadJson)
        } finally { a.close() }
        val reopened = MaginaOlivoDatabase.create(context, names[1])
        try {
            assertEquals(ParentReceiveOutcome.NOT_AUTHORIZED,
                ParentReceiveCoordinator(reopened, again, issuer, parents).receive(workspace).outcome)
            assertEquals(listOf(1, 2, 0), counts(reopened))
        } finally { reopened.close() }
    }

    /** A creates and confirms its WORKSPACE with the #798 sender. */
    private suspend fun sendWorkspace(db: MaginaOlivoDatabase, account: AccountRepository,
        transport: HttpsDeliveryRpcTransport, name: String): UUID {
        val staged = WorkspaceSendCoordinator(db, account, issuer, transport)
            .createOwned(WorkspaceDraft(name, "ES", "Europe/Madrid", "es-ES", "EUR"))
        assertEquals(WorkspaceSendOutcome.SYNCED, WorkspaceSendCoordinator(db, account, issuer, transport).send(staged.operationId!!, lease))
        return staged.workspaceId!!
    }
    /** The app's own producer: an unbound FARM CREATE marker in the selected workspace. */
    private suspend fun createFarm(db: MaginaOlivoDatabase, account: AccountRepository, workspace: UUID, name: String): Pair<UUID, UUID> {
        val farm = (farms(db, account).create(NewFarm(workspace, name, municipality = "Bedmar", province = "Jaén"))
            as AppResult.Success).value
        return farm to db.syncOutboxDao().listForEntity(SyncEntityType.FARM, farm).single().id
    }
    private fun selector(db: MaginaOlivoDatabase, account: AccountRepository) = LocalWorkspaceRepository(
        db, SystemAppClock(), UuidGenerator(), DefaultAppDispatchers(), RegionalContext.spainDefault(), account, issuer)
    private fun farms(db: MaginaOlivoDatabase, account: AccountRepository, ids: IdGenerator = UuidGenerator()) =
        OfflineFirstFarmRepository(db, SystemAppClock(), ids, DefaultAppDispatchers(), selector(db, account))
    private fun remoteFarm(id: UUID) = JSONObject(fetch("/__g26_admin/farm?id=$id", "GET"))
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
    private fun counts(db: MaginaOlivoDatabase) = listOf("workspaces", "farms", "sync_outbox").map { table ->
        db.openHelper.readableDatabase.query("SELECT count(*) FROM $table").use { it.moveToFirst(); it.getInt(0) }
    }
}
