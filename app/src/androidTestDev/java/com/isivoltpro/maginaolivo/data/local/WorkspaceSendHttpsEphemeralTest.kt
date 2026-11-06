package com.isivoltpro.maginaolivo.data.local

import android.content.Context
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
import com.isivoltpro.maginaolivo.data.local.model.SyncEntityType
import com.isivoltpro.maginaolivo.data.local.model.SyncStatus
import com.isivoltpro.maginaolivo.data.repository.HttpsWorkspaceAuthorizationRemote
import com.isivoltpro.maginaolivo.data.repository.LocalWorkspaceRepository
import com.isivoltpro.maginaolivo.data.repository.WorkspaceAuthorizationContract
import com.isivoltpro.maginaolivo.data.repository.sync.DeliveryDispatchLease
import com.isivoltpro.maginaolivo.data.repository.sync.HttpsDeliveryRpcTransport
import com.isivoltpro.maginaolivo.data.repository.sync.ParentReceiveCoordinator
import com.isivoltpro.maginaolivo.data.repository.sync.ParentReceiveOutcome
import com.isivoltpro.maginaolivo.data.repository.sync.WorkspaceDraft
import com.isivoltpro.maginaolivo.data.repository.sync.WorkspaceSendCoordinator
import com.isivoltpro.maginaolivo.data.repository.sync.WorkspaceSendOutcome
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
 * Device A creates and sends only a WORKSPACE through `create_own_workspace`; device B starts from an
 * empty Room file and receives it with the #797 parent receiver. No FARM, PARCEL or CAMPAIGN is sent.
 */
@RunWith(AndroidJUnit4::class)
class WorkspaceSendHttpsEphemeralTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val issuer = "https://zubpicdibqbmewxeurhm.supabase.co"
    private val endpoint = "https://10.0.2.2:54443"
    private val names = List(3) { "g26-ws-send-${it}-${UUID.randomUUID()}" }
    private val draft = WorkspaceDraft("Olivar QA A", "ES", "Europe/Madrid", "es-ES", "EUR")
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
        lease = DeliveryDispatchLease.tryAcquire(File(context.noBackupFilesDir, "g26-ws-send-https"))!!
    }

    @After fun cleanup() {
        if (::lease.isInitialized) lease.close()
        names.forEach { context.deleteDatabase(it) }
        if (::originalTls.isInitialized) HttpsURLConnection.setDefaultSSLSocketFactory(originalTls)
    }

    @Test fun deviceASendsOnlyWorkspaceAndDeviceBReceivesItWithIsolation() = runBlocking {
        val actor = UUID.fromString(fixture.getString("actor"))
        val session = session(actor, "token")
        val (account, sessions) = account(session)
        val send = HttpsDeliveryRpcTransport(config, scope = HttpsDeliveryRpcTransport.RpcScope.WORKSPACE_SEND)
        val slow = HttpsDeliveryRpcTransport(config, readTimeoutMillis = 3_000,
            scope = HttpsDeliveryRpcTransport.RpcScope.WORKSPACE_SEND)
        val parents = HttpsDeliveryRpcTransport(config, scope = HttpsDeliveryRpcTransport.RpcScope.PARENT_RECEIVE)
        val authorizations = HttpsWorkspaceAuthorizationRemote(endpoint, fixture.getString("anonKey"))

        // Device A: a historical signed-out workspace is never attributed or sent.
        var a = MaginaOlivoDatabase.create(context, names[0])
        val workspace: UUID
        val operation: UUID
        try {
            val legacy = UUID.randomUUID(); val legacyOp = UUID.randomUUID()
            a.openHelper.writableDatabase.execSQL("INSERT INTO workspaces(id,name,owner_user_id,country_code,timezone,locale,currency,created_at,updated_at,version,sync_status) VALUES ('$legacy','Mi olivar','${UUID.randomUUID()}','ES','Europe/Madrid','es-ES','EUR',1000,1000,1,'PENDING')")
            a.openHelper.writableDatabase.execSQL("INSERT INTO sync_outbox(id,entity_type,entity_id,operation,payload_version,status,attempt_count,created_at,updated_at) VALUES ('$legacyOp','WORKSPACE','$legacy','CREATE',1,'PENDING',0,1000,1000)")
            assertEquals(WorkspaceSendOutcome.CONSENT_REQUIRED,
                WorkspaceSendCoordinator(a, account, issuer, send).send(legacyOp, lease))
            assertEquals(0, remote(legacy).getInt("rows"))

            val staged = WorkspaceSendCoordinator(a, account, issuer, send).createOwned(draft)
            assertEquals(WorkspaceSendOutcome.STAGED, staged.outcome)
            workspace = staged.workspaceId!!; operation = staged.operationId!!
            val payload = a.syncOutboxDao().findById(operation)!!.payloadJson

            // Lost response after the server committed, then a read timeout on the replay.
            fetch("/__g26_admin/create_mode?mode=drop", "POST")
            assertEquals(WorkspaceSendOutcome.OUTCOME_UNKNOWN,
                WorkspaceSendCoordinator(a, account, issuer, send).send(operation, lease))
            assertEquals(1, remote(workspace).getInt("rows"))
            assertEquals(1, remote(workspace).getInt("receipts"))
            assertEquals(OutboxStatus.FAILED, a.syncOutboxDao().findById(operation)?.status)
            fetch("/__g26_admin/create_mode?mode=delay", "POST")
            assertEquals(WorkspaceSendOutcome.OUTCOME_UNKNOWN,
                WorkspaceSendCoordinator(a, account, issuer, slow).send(operation, lease))
            fetch("/__g26_admin/create_mode?mode=normal", "POST")
            assertEquals(payload, a.syncOutboxDao().findById(operation)?.payloadJson)
            assertEquals(SyncStatus.PENDING, a.workspaceDao().findById(workspace)?.metadata?.syncStatus)

            // Restart A's Room before recovering: the durable outbox replays the same operation.
            a.close(); a = MaginaOlivoDatabase.create(context, names[0])
            assertEquals(WorkspaceSendOutcome.SYNCED, WorkspaceSendCoordinator(a, account, issuer, send).send(operation, lease))
            assertEquals(WorkspaceSendOutcome.ALREADY_CONFIRMED,
                WorkspaceSendCoordinator(a, account, issuer, send).send(operation, lease))
            val state = remote(workspace)
            assertEquals(listOf(1, 1, 0, 1), listOf("rows", "receipts", "deleted", "version").map(state::getInt))
            assertEquals(actor.toString(), state.getString("owner"))
            assertEquals("Olivar QA A", state.getString("name"))
            assertEquals(OutboxStatus.CONFIRMED, a.syncOutboxDao().findById(operation)?.status)
            assertEquals(1L, a.workspaceDao().findById(workspace)?.metadata?.remoteVersion)

            // A: now an authorized owner grant; the selector accepts it and A reads it back once.
            val grant = authorizations.list(session)
            a.workspaceAuthorizationCacheDao().upsert(WorkspaceAuthorizationCacheEntity(issuer, actor.toString(), grant))
            assertTrue(selector(a, account).selectWorkspace(workspace) is AppResult.Success)
            assertEquals(ParentReceiveOutcome.IMPORTED, ParentReceiveCoordinator(a, account, issuer, parents).receive(workspace).outcome)
            assertEquals(ParentReceiveOutcome.ALREADY_CURRENT,
                ParentReceiveCoordinator(a, account, issuer, parents).receive(workspace).outcome)
            assertEquals(actor, a.workspaceDao().findById(workspace)?.ownerUserId)
            assertEquals(SyncStatus.SYNCED, a.workspaceDao().findById(workspace)?.metadata?.syncStatus)
        } finally { a.close() }

        // Device B: empty Room, same account. Selection needs the received row; then the UUID matches A.
        val b = MaginaOlivoDatabase.create(context, names[1])
        try {
            assertEquals(listOf(0, 0), counts(b))
            b.workspaceAuthorizationCacheDao().upsert(WorkspaceAuthorizationCacheEntity(issuer, actor.toString(),
                authorizations.list(session)))
            assertTrue(selector(b, account).selectWorkspace(workspace) is AppResult.Failure)
            val received = ParentReceiveCoordinator(b, account, issuer, parents).receive(workspace)
            assertEquals(ParentReceiveOutcome.IMPORTED, received.outcome)
            assertEquals(1, received.inserted)
            val row = b.workspaceDao().findById(workspace)!!
            assertEquals("Olivar QA A", row.name)
            assertEquals(actor, row.ownerUserId)
            assertEquals(1L, row.metadata.remoteVersion)
            assertEquals(listOf(1, 0), counts(b))
            assertTrue(selector(b, account).selectWorkspace(workspace) is AppResult.Success)
            assertEquals(ParentReceiveOutcome.ALREADY_CURRENT,
                ParentReceiveCoordinator(b, account, issuer, parents).receive(workspace).outcome)
        } finally { b.close() }

        // Device A again: account switch, logout and offline never send a second pending workspace.
        a = MaginaOlivoDatabase.create(context, names[0])
        val second: UUID
        val secondOp: UUID
        try {
            val staged = WorkspaceSendCoordinator(a, account, issuer, send).createOwned(draft.copy(name = "Olivar QA A segundo"))
            second = staged.workspaceId!!; secondOp = staged.operationId!!
            val pending = a.syncOutboxDao().findById(secondOp)
            val foreignActor = UUID.fromString(fixture.getString("foreignActor"))
            val (switched, _) = account(session(foreignActor, "foreignToken"))
            assertEquals(WorkspaceSendOutcome.NOT_AUTHORIZED,
                WorkspaceSendCoordinator(a, switched, issuer, send).send(secondOp, lease))
            account.logout(false)
            assertEquals(WorkspaceSendOutcome.NOT_AUTHORIZED,
                WorkspaceSendCoordinator(a, account, issuer, send).send(secondOp, lease))
            assertTrue(a.workspaceDao().listUnboundLocal().none { it.id == second || it.id == workspace })
            sessions.write(AccountRecord(session = session))
            val offlineConfig = AuthConfiguration("https://10.0.2.2:9", fixture.getString("anonKey"), "qa://auth/callback")
            val offline = AccountRepository(SupabaseAuthApi(offlineConfig, HttpsAuthTransport(offlineConfig)), sessions)
            assertEquals(WorkspaceSendOutcome.OFFLINE, WorkspaceSendCoordinator(a, offline, issuer,
                HttpsDeliveryRpcTransport(offlineConfig, scope = HttpsDeliveryRpcTransport.RpcScope.WORKSPACE_SEND))
                .send(secondOp, lease))
            assertEquals(pending, a.syncOutboxDao().findById(secondOp))
            assertEquals(0, remote(second).getInt("rows"))
        } finally { a.close() }

        // C claims A's UUID from its own device: the server refuses, nothing of A changes or leaks.
        val foreignActor = UUID.fromString(fixture.getString("foreignActor"))
        val (foreign, _) = account(session(foreignActor, "foreignToken"))
        val c = MaginaOlivoDatabase.create(context, names[2])
        try {
            val ids = ArrayDeque(listOf(workspace, UUID.randomUUID()))
            val claim = WorkspaceSendCoordinator(c, foreign, issuer, send, newId = { ids.removeFirst() })
                .createOwned(draft.copy(name = "Toma de C"))
            assertEquals(workspace, claim.workspaceId)
            assertEquals(WorkspaceSendOutcome.CONFLICT,
                WorkspaceSendCoordinator(c, foreign, issuer, send).send(claim.operationId!!, lease))
            assertEquals(OutboxStatus.BLOCKED, c.syncOutboxDao().findById(claim.operationId)?.status)
            assertEquals("WORKSPACE_CONFLICT", c.syncOutboxDao().findById(claim.operationId)?.lastErrorCode)
            assertEquals(actor.toString(), remote(workspace).getString("owner"))
            assertEquals("Olivar QA A", remote(workspace).getString("name"))
            c.workspaceAuthorizationCacheDao().upsert(WorkspaceAuthorizationCacheEntity(issuer, foreignActor.toString(),
                authorizations.list(session(foreignActor, "foreignToken"))))
            assertEquals(ParentReceiveOutcome.NOT_AUTHORIZED,
                ParentReceiveCoordinator(c, foreign, issuer, parents).receive(workspace).outcome)
            assertEquals("Toma de C", c.workspaceDao().findById(workspace)?.name)
        } finally { c.close() }

        // Revocation: the second workspace commits but the response is lost, then A deletes it remotely.
        // The replay is refused, the outbox and its payload survive, and B cannot receive it.
        val (again, _) = account(session)
        a = MaginaOlivoDatabase.create(context, names[0])
        try {
            fetch("/__g26_admin/create_mode?mode=drop", "POST")
            assertEquals(WorkspaceSendOutcome.OUTCOME_UNKNOWN,
                WorkspaceSendCoordinator(a, again, issuer, send).send(secondOp, lease))
            fetch("/__g26_admin/create_mode?mode=normal", "POST")
            fetch("/__g26_admin/delete_workspace?id=$second", "POST")
            val payload = a.syncOutboxDao().findById(secondOp)?.payloadJson
            assertEquals(WorkspaceSendOutcome.NOT_AUTHORIZED,
                WorkspaceSendCoordinator(a, again, issuer, send).send(secondOp, lease))
            assertEquals(OutboxStatus.FAILED, a.syncOutboxDao().findById(secondOp)?.status)
            assertEquals(payload, a.syncOutboxDao().findById(secondOp)?.payloadJson)
            assertEquals(1, a.syncOutboxDao().listForEntity(SyncEntityType.WORKSPACE, second).size)
            assertTrue(WorkspaceAuthorizationContract.parse(authorizations.list(session)).workspaces.none { it.id == second })
        } finally { a.close() }
        val reopened = MaginaOlivoDatabase.create(context, names[1])
        try {
            reopened.workspaceAuthorizationCacheDao().upsert(WorkspaceAuthorizationCacheEntity(issuer, actor.toString(),
                authorizations.list(session)))
            assertEquals(ParentReceiveOutcome.NOT_AUTHORIZED,
                ParentReceiveCoordinator(reopened, again, issuer, parents).receive(second).outcome)
            assertEquals(listOf(1, 0), counts(reopened))
        } finally { reopened.close() }
    }

    private fun selector(db: MaginaOlivoDatabase, account: AccountRepository) = LocalWorkspaceRepository(
        db, SystemAppClock(), UuidGenerator(), DefaultAppDispatchers(), RegionalContext.spainDefault(), account, issuer)
    private fun remote(id: UUID) = JSONObject(fetch("/__g26_admin/workspace?id=$id", "GET"))
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
    private fun counts(db: MaginaOlivoDatabase) = listOf("workspaces", "sync_outbox").map { table ->
        db.openHelper.readableDatabase.query("SELECT count(*) FROM $table").use { it.moveToFirst(); it.getInt(0) }
    }
}
