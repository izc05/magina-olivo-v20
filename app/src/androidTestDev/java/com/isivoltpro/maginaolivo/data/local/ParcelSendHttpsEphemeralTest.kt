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
import com.isivoltpro.maginaolivo.data.local.entity.WorkspaceSelectionEntity
import com.isivoltpro.maginaolivo.data.local.model.OutboxOperation
import com.isivoltpro.maginaolivo.data.local.model.OutboxStatus
import com.isivoltpro.maginaolivo.data.local.model.SyncEntityType
import com.isivoltpro.maginaolivo.data.local.model.SyncStatus
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
import com.isivoltpro.maginaolivo.data.repository.sync.ParcelSendCoordinator
import com.isivoltpro.maginaolivo.data.repository.sync.ParcelSendOutcome
import com.isivoltpro.maginaolivo.data.repository.sync.ParentConflict
import com.isivoltpro.maginaolivo.data.repository.sync.ParentKind
import com.isivoltpro.maginaolivo.data.repository.sync.ParentReceiveCoordinator
import com.isivoltpro.maginaolivo.data.repository.sync.ParentReceiveOutcome
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
 * Device A sends its WORKSPACE (#798), FARM rows (#799) and then PARCEL aggregates, each with the
 * creation-time membership the app wrote. Device B starts empty and receives them with the #797
 * parent receiver. No membership change, parcel UPDATE/DELETE or CAMPAIGN is sent.
 */
@RunWith(AndroidJUnit4::class)
class ParcelSendHttpsEphemeralTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val issuer = "https://zubpicdibqbmewxeurhm.supabase.co"
    private val endpoint = "https://10.0.2.2:54443"
    private val names = List(4) { "g26-parcel-send-${it}-${UUID.randomUUID()}" }
    private val ledgers = List(2) { File(context.noBackupFilesDir, "g26-parcel-send-ledger-$it-${UUID.randomUUID()}.json") }
    private lateinit var originalTls: SSLSocketFactory
    private lateinit var fixture: JSONObject
    private lateinit var config: AuthConfiguration
    private lateinit var lease: DeliveryDispatchLease
    private lateinit var send: HttpsDeliveryRpcTransport
    private lateinit var farmSend: HttpsDeliveryRpcTransport
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
        lease = DeliveryDispatchLease.tryAcquire(File(context.noBackupFilesDir, "g26-parcel-send-https"))!!
        send = HttpsDeliveryRpcTransport(config, scope = HttpsDeliveryRpcTransport.RpcScope.PARCEL_SEND)
        farmSend = HttpsDeliveryRpcTransport(config, scope = HttpsDeliveryRpcTransport.RpcScope.FARM_SEND)
        authorizations = HttpsWorkspaceAuthorizationRemote(endpoint, fixture.getString("anonKey"))
    }

    @After fun cleanup() {
        if (::lease.isInitialized) lease.close()
        names.forEach { context.deleteDatabase(it) }
        ledgers.forEach { File(it.path).delete(); File(it.path + ".bak").delete() }
        if (::originalTls.isInitialized) HttpsURLConnection.setDefaultSSLSocketFactory(originalTls)
    }

    @Test fun deviceASendsOnlyParcelsAndDeviceBReceivesThemWithIsolation() = runBlocking {
        val actor = UUID.fromString(fixture.getString("actor"))
        val session = session(actor, "token")
        val (account, sessions) = account(session)
        val slow = HttpsDeliveryRpcTransport(config, readTimeoutMillis = 3_000, scope = HttpsDeliveryRpcTransport.RpcScope.PARCEL_SEND)
        val parents = HttpsDeliveryRpcTransport(config, scope = HttpsDeliveryRpcTransport.RpcScope.PARENT_RECEIVE)

        var a = MaginaOlivoDatabase.create(context, names[0])
        val workspace: UUID
        val farm: UUID
        val farm2: UUID
        val parcel: UUID
        val edited: UUID
        try {
            workspace = sendWorkspace(a, account, "Olivar QA A")
            refreshGrant(a, actor, session)
            assertTrue(selector(a, account).selectWorkspace(workspace) is AppResult.Success)
            var links = LocalFarmLinkRepository(context, a, account, issuer, AndroidFarmLinkLedger(ledgers[0]))
            fun sender(transport: HttpsDeliveryRpcTransport = send) =
                ParcelSendCoordinator(a, account, issuer, transport, { links.ledgerFor(it) })
            farm = sendFarm(a, account, actor, links, workspace, "Finca Norte")
            farm2 = sendFarm(a, account, actor, links, workspace, "Finca Sur")

            // A parcel in a farm that is not yet confirmed remotely is not sent.
            val localFarm = (farms(a, account).create(NewFarm(workspace, "Finca Local")) as AppResult.Success).value
            links.consent(actor, links.choices().filter { it.id == localFarm }, accepted = true)
            val (_, waitingOp) = createParcel(a, localFarm, "Parcela en espera")
            assertEquals(ParcelSendOutcome.PARENT_NOT_SYNCED, sender().stage(waitingOp))

            // Lost response after the server committed, then a read timeout on the replay, then a Room restart.
            val (created, op) = createParcel(a, farm, "Parcela 1")
            parcel = created
            val membership = a.parcelDao().listMemberships(parcel).single()
            assertEquals(ParcelSendOutcome.STAGED, sender().stage(op))
            val payload = a.syncOutboxDao().findById(op)!!.payloadJson
            fetch("/__g26_admin/create_mode?mode=drop", "POST")
            assertEquals(ParcelSendOutcome.OUTCOME_UNKNOWN, sender().send(op, lease))
            assertEquals(listOf(1, 1, 1), listOf("rows", "receipts", "memberships").map(remoteParcel(parcel)::getInt))
            fetch("/__g26_admin/create_mode?mode=delay", "POST")
            assertEquals(ParcelSendOutcome.OUTCOME_UNKNOWN, sender(slow).send(op, lease))
            fetch("/__g26_admin/create_mode?mode=normal", "POST")
            assertEquals(OutboxStatus.FAILED, a.syncOutboxDao().findById(op)?.status)
            assertEquals(payload, a.syncOutboxDao().findById(op)?.payloadJson)
            a.close(); a = MaginaOlivoDatabase.create(context, names[0])
            links = LocalFarmLinkRepository(context, a, account, issuer, AndroidFarmLinkLedger(ledgers[0]))
            assertEquals(ParcelSendOutcome.SYNCED, sender().send(op, lease))
            assertEquals(ParcelSendOutcome.ALREADY_CONFIRMED, sender().send(op, lease))
            val state = remoteParcel(parcel)
            assertEquals(listOf(1, 1, 1, 1), listOf("rows", "receipts", "memberships", "version").map(state::getInt))
            assertEquals(workspace.toString(), state.getString("workspace"))
            assertEquals(SyncStatus.SYNCED, a.parcelDao().findById(parcel)?.metadata?.syncStatus)
            assertEquals(SyncStatus.SYNCED, a.parcelDao().listMemberships(parcel).single().metadata.syncStatus)
            assertEquals(membership.id, a.parcelDao().listMemberships(parcel).single().id)

            // Offline edit after staging: confirmed remotely as staged, the local edit stays pending.
            val (second, secondOp) = createParcel(a, farm, "Parcela 2")
            edited = second
            assertEquals(ParcelSendOutcome.STAGED, sender().stage(secondOp))
            assertTrue(parcels(a).update(second, ParcelChanges("Parcela 2 editada offline", managedAreaM2 = 900.0)) is AppResult.Success)
            assertEquals(ParcelSendOutcome.CONFIRMED_LOCAL_PENDING, sender().send(secondOp, lease))
            assertEquals("Parcela 2", remoteParcel(second).getString("name"))
            assertEquals(listOf(OutboxOperation.CREATE to OutboxStatus.CONFIRMED, OutboxOperation.UPDATE to OutboxStatus.PENDING),
                a.syncOutboxDao().listForEntity(SyncEntityType.PARCEL, second).map { it.operation to it.status })

            // A reads back: the synced parcel adopts server instants, the edited one is protected.
            refreshGrant(a, actor, session)
            val back = ParentReceiveCoordinator(a, account, issuer, parents).receive(workspace)
            assertTrue(back.conflicts.toString(), back.conflicts.contains(ParentConflict(ParentKind.PARCEL, second, "local_pending")))
            assertTrue(back.conflicts.none { it.id == parcel })
            assertEquals("Parcela 2 editada offline", a.parcelDao().findById(second)?.displayName)
            val again = ParentReceiveCoordinator(a, account, issuer, parents).receive(workspace)
            assertEquals(0, again.inserted + again.updated)

            // G1b revocation of the second farm: its parcel is never sent and keeps its outbox.
            val (revoked, revokedOp) = createParcel(a, farm2, "Parcela Revocada")
            assertEquals(ParcelSendOutcome.STAGED, sender().stage(revokedOp))
            links.revokeConsentForFarm(farm2)
            assertEquals(ParcelSendOutcome.CONSENT_REQUIRED, sender().send(revokedOp, lease))
            assertEquals(OutboxStatus.BLOCKED, a.syncOutboxDao().findById(revokedOp)?.status)
            assertEquals(0, remoteParcel(revoked).getInt("rows"))

            // Account switch, logout and offline never send a staged parcel.
            val (pendingParcel, pendingOp) = createParcel(a, farm, "Parcela Pendiente")
            assertEquals(ParcelSendOutcome.STAGED, sender().stage(pendingOp))
            val pending = a.syncOutboxDao().findById(pendingOp)
            val foreignActor = UUID.fromString(fixture.getString("foreignActor"))
            val (switched, _) = account(session(foreignActor, "foreignToken"))
            assertEquals(ParcelSendOutcome.NOT_AUTHORIZED,
                ParcelSendCoordinator(a, switched, issuer, send, { links.ledgerFor(it) }).send(pendingOp, lease))
            account.logout(false)
            assertEquals(ParcelSendOutcome.NOT_AUTHORIZED, sender().send(pendingOp, lease))
            sessions.write(AccountRecord(session = session))
            val offlineConfig = AuthConfiguration("https://10.0.2.2:9", fixture.getString("anonKey"), "qa://auth/callback")
            val offline = AccountRepository(SupabaseAuthApi(offlineConfig, HttpsAuthTransport(offlineConfig)), sessions)
            assertEquals(ParcelSendOutcome.OFFLINE, ParcelSendCoordinator(a, offline, issuer,
                HttpsDeliveryRpcTransport(offlineConfig, scope = HttpsDeliveryRpcTransport.RpcScope.PARCEL_SEND),
                { links.ledgerFor(it) }).send(pendingOp, lease))
            assertEquals(pending, a.syncOutboxDao().findById(pendingOp))
            assertEquals(0, remoteParcel(pendingParcel).getInt("rows"))
        } finally { a.close() }

        // Device B: empty Room, its own session for the same account; receives the original UUIDs and staged content.
        val (deviceB, _) = account(session)
        val b = MaginaOlivoDatabase.create(context, names[1])
        try {
            refreshGrant(b, actor, session)
            val received = ParentReceiveCoordinator(b, deviceB, issuer, parents).receive(workspace)
            assertEquals(ParentReceiveOutcome.IMPORTED, received.outcome)
            assertEquals(7, received.inserted)
            assertEquals(listOf(1, 2, 2, 2, 0), counts(b))
            assertEquals("Parcela 1", b.parcelDao().findById(parcel)?.displayName)
            assertEquals("Parcela 2", b.parcelDao().findById(edited)?.displayName)
            assertEquals(1234.5, b.parcelDao().findById(parcel)?.managedAreaM2 ?: 0.0, 0.0)
            assertEquals(farm, b.parcelDao().findCurrentMembership(parcel)?.farmId)
            assertTrue(selector(b, deviceB).selectWorkspace(workspace) is AppResult.Success)
            assertEquals(ParentReceiveOutcome.ALREADY_CURRENT,
                ParentReceiveCoordinator(b, deviceB, issuer, parents).receive(workspace).outcome)
        } finally { b.close() }

        // C: cannot receive; cannot add a parcel to A's workspace or farm; cannot take A's parcel UUID.
        val foreignActor = UUID.fromString(fixture.getString("foreignActor"))
        val foreignSession = session(foreignActor, "foreignToken")
        val (foreign, _) = account(foreignSession)
        val forged = MaginaOlivoDatabase.create(context, names[3])
        try {
            val now = Instant.now()
            val grant = """{"contract_version":1,"issuer":"$issuer/auth/v1","subject":"$foreignActor","verified_at":"$now","token_expires_at":"${now.plusSeconds(600)}","workspaces":[{"id":"$workspace","name":"x","role":"OWNER","permissions":["read","write"],"grant_version":1,"authorization_expires_at":"${now.plusSeconds(600)}"}]}"""
            forged.workspaceAuthorizationCacheDao().upsert(WorkspaceAuthorizationCacheEntity(issuer, foreignActor.toString(), grant))
            assertEquals(ParentReceiveOutcome.NOT_AUTHORIZED,
                ParentReceiveCoordinator(forged, foreign, issuer, parents).receive(workspace).outcome)
            // A forged local copy of A's confirmed workspace and farm: the server still refuses the parcel.
            val sql = forged.openHelper.writableDatabase
            sql.execSQL("INSERT INTO workspaces(id,name,owner_user_id,country_code,timezone,locale,currency,created_at,updated_at,version,sync_status,remote_version) VALUES ('$workspace','Falso','$foreignActor','ES','Europe/Madrid','es-ES','EUR',1000,1000,1,'SYNCED',1)")
            sql.execSQL("INSERT INTO farms(id,workspace_id,name,status,created_at,updated_at,version,sync_status,remote_version) VALUES ('$farm','$workspace','Falsa','ACTIVE',1000,1000,1,'SYNCED',1)")
            forged.workspaceSelectionDao().upsert(WorkspaceSelectionEntity(issuer, foreignActor.toString(), workspace))
            val links = LocalFarmLinkRepository(context, forged, foreign, issuer, AndroidFarmLinkLedger(ledgers[1]))
            links.consent(foreignActor, links.choices().filter { it.id == farm }, accepted = true)
            val (intruder, intruderOp) = createParcel(forged, farm, "Intrusa")
            val intruderSender = ParcelSendCoordinator(forged, foreign, issuer, send, { links.ledgerFor(it) })
            assertEquals(ParcelSendOutcome.STAGED, intruderSender.stage(intruderOp))
            assertEquals(ParcelSendOutcome.NOT_AUTHORIZED, intruderSender.send(intruderOp, lease))
            assertEquals(OutboxStatus.PENDING, forged.syncOutboxDao().findById(intruderOp)?.status)
            assertEquals(0, remoteParcel(intruder).getInt("rows"))
        } finally { forged.close() }
        val c = MaginaOlivoDatabase.create(context, names[2])
        try {
            val own = sendWorkspace(c, foreign, "Olivar QA C")
            refreshGrant(c, foreignActor, foreignSession)
            assertTrue(selector(c, foreign).selectWorkspace(own) is AppResult.Success)
            val links = LocalFarmLinkRepository(context, c, foreign, issuer, AndroidFarmLinkLedger(ledgers[1]))
            val ownFarm = sendFarm(c, foreign, foreignActor, links, own, "Finca C")
            val ids = ArrayDeque(listOf(parcel, UUID.randomUUID(), UUID.randomUUID()))
            val claim = (parcels(c, object : IdGenerator { override fun newId() = ids.removeFirst() })
                .create(NewParcel(ownFarm, "Toma de C")) as AppResult.Success).value
            assertEquals(parcel, claim)
            val claimOp = c.syncOutboxDao().listForEntity(SyncEntityType.PARCEL, parcel).single().id
            val claimer = ParcelSendCoordinator(c, foreign, issuer, send, { links.ledgerFor(it) })
            assertEquals(ParcelSendOutcome.STAGED, claimer.stage(claimOp))
            assertEquals(ParcelSendOutcome.CONFLICT, claimer.send(claimOp, lease))
            assertEquals("PARCEL_CONFLICT", c.syncOutboxDao().findById(claimOp)?.lastErrorCode)
            assertEquals(workspace.toString(), remoteParcel(parcel).getString("workspace"))
            assertEquals("Parcela 1", remoteParcel(parcel).getString("name"))
        } finally { c.close() }

        // Server-side revocation: the parcel commits, the response is lost, A deletes the workspace.
        // The replay is refused, the outbox and payload survive, and B can no longer receive.
        val (again, _) = account(session)
        a = MaginaOlivoDatabase.create(context, names[0])
        try {
            refreshGrant(a, actor, session)
            val links = LocalFarmLinkRepository(context, a, again, issuer, AndroidFarmLinkLedger(ledgers[0]))
            val (last, lastOp) = createParcel(a, farm, "Parcela Final")
            val lastSender = ParcelSendCoordinator(a, again, issuer, send, { links.ledgerFor(it) })
            assertEquals(ParcelSendOutcome.STAGED, lastSender.stage(lastOp))
            fetch("/__g26_admin/create_mode?mode=drop", "POST")
            assertEquals(ParcelSendOutcome.OUTCOME_UNKNOWN, lastSender.send(lastOp, lease))
            fetch("/__g26_admin/create_mode?mode=normal", "POST")
            assertEquals(1, remoteParcel(last).getInt("rows"))
            fetch("/__g26_admin/delete_workspace?id=$workspace", "POST")
            val payload = a.syncOutboxDao().findById(lastOp)?.payloadJson
            assertEquals(ParcelSendOutcome.NOT_AUTHORIZED, lastSender.send(lastOp, lease))
            assertEquals(OutboxStatus.FAILED, a.syncOutboxDao().findById(lastOp)?.status)
            assertEquals(payload, a.syncOutboxDao().findById(lastOp)?.payloadJson)
        } finally { a.close() }
        val reopened = MaginaOlivoDatabase.create(context, names[1])
        try {
            assertEquals(ParentReceiveOutcome.NOT_AUTHORIZED,
                ParentReceiveCoordinator(reopened, again, issuer, parents).receive(workspace).outcome)
            assertEquals(listOf(1, 2, 2, 2, 0), counts(reopened))
        } finally { reopened.close() }
    }

    /** WORKSPACE created and confirmed with the #798 sender. */
    private suspend fun sendWorkspace(db: MaginaOlivoDatabase, account: AccountRepository, name: String): UUID {
        val transport = HttpsDeliveryRpcTransport(config, scope = HttpsDeliveryRpcTransport.RpcScope.WORKSPACE_SEND)
        val staged = WorkspaceSendCoordinator(db, account, issuer, transport)
            .createOwned(WorkspaceDraft(name, "ES", "Europe/Madrid", "es-ES", "EUR"))
        assertEquals(WorkspaceSendOutcome.SYNCED, WorkspaceSendCoordinator(db, account, issuer, transport).send(staged.operationId!!, lease))
        return staged.workspaceId!!
    }
    /** FARM created by the app, consented through G1b and confirmed with the #799 sender. */
    private suspend fun sendFarm(db: MaginaOlivoDatabase, account: AccountRepository, userId: UUID,
        links: LocalFarmLinkRepository, workspace: UUID, name: String): UUID {
        val farm = (farms(db, account).create(NewFarm(workspace, name, municipality = "Bedmar", province = "Jaén"))
            as AppResult.Success).value
        val op = db.syncOutboxDao().listForEntity(SyncEntityType.FARM, farm).single().id
        links.consent(userId, links.choices().filter { it.id == farm }, accepted = true)
        val sender = FarmSendCoordinator(db, account, issuer, farmSend, { links.ledgerFor(it) })
        assertEquals(FarmSendOutcome.STAGED, sender.stage(op))
        assertEquals(FarmSendOutcome.SYNCED, sender.send(op, lease))
        return farm
    }
    /** The app's own producer: parcel, initial membership and one unbound PARCEL CREATE marker. */
    private suspend fun createParcel(db: MaginaOlivoDatabase, farm: UUID, name: String): Pair<UUID, UUID> {
        val parcel = (parcels(db).create(NewParcel(farm, name, municipality = "Bedmar", province = "Jaén",
            managedAreaM2 = 1234.5)) as AppResult.Success).value
        return parcel to db.syncOutboxDao().listForEntity(SyncEntityType.PARCEL, parcel).single().id
    }
    private suspend fun refreshGrant(db: MaginaOlivoDatabase, actor: UUID, session: AuthSession) =
        db.workspaceAuthorizationCacheDao().upsert(WorkspaceAuthorizationCacheEntity(issuer, actor.toString(), authorizations.list(session)))
    private fun selector(db: MaginaOlivoDatabase, account: AccountRepository) = LocalWorkspaceRepository(
        db, SystemAppClock(), UuidGenerator(), DefaultAppDispatchers(), RegionalContext.spainDefault(), account, issuer)
    private fun farms(db: MaginaOlivoDatabase, account: AccountRepository) =
        OfflineFirstFarmRepository(db, SystemAppClock(), UuidGenerator(), DefaultAppDispatchers(), selector(db, account))
    private fun parcels(db: MaginaOlivoDatabase, ids: IdGenerator = UuidGenerator()) =
        OfflineFirstParcelRepository(db, SystemAppClock(), ids, DefaultAppDispatchers())
    private fun remoteParcel(id: UUID) = JSONObject(fetch("/__g26_admin/parcel?id=$id", "GET"))
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
    private fun counts(db: MaginaOlivoDatabase) =
        listOf("workspaces", "farms", "parcels", "farm_parcel_memberships", "sync_outbox").map { table ->
            db.openHelper.readableDatabase.query("SELECT count(*) FROM $table").use { it.moveToFirst(); it.getInt(0) }
        }
}
