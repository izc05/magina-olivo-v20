package com.isivoltpro.maginaolivo.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.isivoltpro.maginaolivo.data.local.entity.WorkspaceAuthorizationCacheEntity
import com.isivoltpro.maginaolivo.data.local.entity.WorkspaceSelectionEntity
import com.isivoltpro.maginaolivo.data.local.model.OutboxStatus
import com.isivoltpro.maginaolivo.data.local.model.SyncEntityType
import com.isivoltpro.maginaolivo.data.local.model.SyncStatus
import com.isivoltpro.maginaolivo.data.repository.HttpsWorkspaceAuthorizationRemote
import com.isivoltpro.maginaolivo.data.repository.WorkspaceAuthorizationContract
import com.isivoltpro.maginaolivo.data.repository.sync.CampaignReceiveCoordinator
import com.isivoltpro.maginaolivo.data.repository.sync.CampaignReceiveOutcome
import com.isivoltpro.maginaolivo.data.repository.sync.HttpsDeliveryRpcTransport
import com.isivoltpro.maginaolivo.data.repository.sync.ParentConflict
import com.isivoltpro.maginaolivo.data.repository.sync.ParentKind
import com.isivoltpro.maginaolivo.data.repository.sync.ParentReceiveCoordinator
import com.isivoltpro.maginaolivo.data.repository.sync.ParentReceiveOutcome
import com.isivoltpro.maginaolivo.feature.account.AccountRecord
import com.isivoltpro.maginaolivo.feature.account.AccountRepository
import com.isivoltpro.maginaolivo.feature.account.AuthConfiguration
import com.isivoltpro.maginaolivo.feature.account.AuthSession
import com.isivoltpro.maginaolivo.feature.account.HttpsAuthTransport
import com.isivoltpro.maginaolivo.feature.account.MemoryAccountSessionStore
import com.isivoltpro.maginaolivo.feature.account.SupabaseAuthApi
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
 * Explicit DEV-only, read-only probe. Requires a disposable local Supabase and a pinned test
 * certificate. Device B starts from an empty Room file: every WORKSPACE/FARM/PARCEL row comes
 * from the authorized server contract, none is inserted as a local fixture.
 */
@RunWith(AndroidJUnit4::class)
class ParentReceiveHttpsEphemeralTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val issuer = "https://zubpicdibqbmewxeurhm.supabase.co"
    private val endpoint = "https://10.0.2.2:54443"
    private val names = List(3) { "g26-parents-${it}-${UUID.randomUUID()}" }
    private lateinit var originalTls: SSLSocketFactory
    private lateinit var fixture: JSONObject
    private lateinit var config: AuthConfiguration

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
    }

    @After fun cleanup() {
        names.forEach { context.deleteDatabase(it) }
        if (::originalTls.isInitialized) HttpsURLConnection.setDefaultSSLSocketFactory(originalTls)
    }

    @Test fun deviceBReceivesAuthorizedParentsThenCampaignAndKeepsIsolation() = runBlocking {
        val actor = UUID.fromString(fixture.getString("actor"))
        val workspace = uuid("workspace"); val farm1 = uuid("farm1"); val farm2 = uuid("farm2")
        val parcel1 = uuid("parcel1"); val parcel2 = uuid("parcel2"); val campaign = uuid("campaign")
        val session = session(actor, "token")
        val (account, sessions) = account(session)
        val transport = HttpsDeliveryRpcTransport(config, scope = HttpsDeliveryRpcTransport.RpcScope.PARENT_RECEIVE)
        val campaignTransport = HttpsDeliveryRpcTransport(config,
            scope = HttpsDeliveryRpcTransport.RpcScope.CAMPAIGN_RECEIVE)
        val grant = HttpsWorkspaceAuthorizationRemote(endpoint, fixture.getString("anonKey")).list(session)
        assertTrue(WorkspaceAuthorizationContract.parse(grant).available(Instant.now()).any { it.id == workspace })

        var b = MaginaOlivoDatabase.create(context, names[0])
        try {
            b.workspaceAuthorizationCacheDao().upsert(WorkspaceAuthorizationCacheEntity(issuer, actor.toString(), grant))
            b.workspaceSelectionDao().upsert(WorkspaceSelectionEntity(issuer, actor.toString(), workspace))
            assertEquals(listOf(0, 0, 0, 0, 0), counts(b))
            assertEquals(CampaignReceiveOutcome.PARENTS_MISSING,
                CampaignReceiveCoordinator(b, account, issuer, campaignTransport).receive(campaign))

            val imported = ParentReceiveCoordinator(b, account, issuer, transport).receive(workspace)
            assertEquals(ParentReceiveOutcome.IMPORTED, imported.outcome)
            assertEquals(7, imported.inserted)
            assertEquals(listOf(1, 2, 2, 2, 0), counts(b))
            assertEquals(actor, b.workspaceDao().findById(workspace)?.ownerUserId)
            assertEquals(SyncStatus.SYNCED, b.farmDao().findById(farm1)?.metadata?.syncStatus)
            assertEquals(farm1, b.parcelDao().findCurrentMembership(parcel1)?.farmId)
            assertEquals(Instant.parse("2026-01-01T00:00:00.007Z"), b.parcelDao().findCurrentMembership(parcel1)?.validFrom)
            assertEquals(1234.5, b.parcelDao().findById(parcel1)?.managedAreaM2 ?: 0.0, 0.0)

            // The campaign receiver now finds real received parents, not fixtures.
            assertEquals(CampaignReceiveOutcome.IMPORTED,
                CampaignReceiveCoordinator(b, account, issuer, campaignTransport).receive(campaign))
            assertEquals(parcel1, b.campaignDao().listSnapshots(campaign).single().parcelId)
            assertEquals(ParentReceiveOutcome.ALREADY_CURRENT,
                ParentReceiveCoordinator(b, account, issuer, transport).receive(workspace).outcome)
            val stable = snapshot(b)
            b.close()
            b = MaginaOlivoDatabase.create(context, names[0])
            assertEquals(stable, snapshot(b))
            assertEquals(ParentReceiveOutcome.ALREADY_CURRENT,
                ParentReceiveCoordinator(b, account, issuer, transport).receive(workspace).outcome)
            assertEquals(CampaignReceiveOutcome.ALREADY_CURRENT,
                CampaignReceiveCoordinator(b, account, issuer, campaignTransport).receive(campaign))

            // B edits farm2 offline while A edits both farms remotely.
            val local = b.farmDao().findById(farm2)!!
            b.farmDao().upsert(local.copy(name = "Edición offline B", metadata = local.metadata.copy(
                version = local.metadata.version + 1, syncStatus = SyncStatus.PENDING)))
            val pending = UUID.randomUUID()
            b.openHelper.writableDatabase.execSQL("INSERT INTO sync_outbox(id,entity_type,entity_id,operation,payload_version,status,attempt_count,created_at,updated_at) VALUES ('$pending','FARM','$farm2','UPDATE',1,'PENDING',0,3000,3000)")
            fetch("/__g26_admin/edit_farms", "POST")
            val merged = ParentReceiveCoordinator(b, account, issuer, transport).receive(workspace)
            assertEquals(ParentReceiveOutcome.CONFLICT, merged.outcome)
            assertEquals(listOf(ParentConflict(ParentKind.FARM, farm2, "local_pending")), merged.conflicts)
            assertEquals("Finca Norte (A)", b.farmDao().findById(farm1)?.name)
            assertEquals(2L, b.farmDao().findById(farm1)?.metadata?.remoteVersion)
            assertEquals("Edición offline B", b.farmDao().findById(farm2)?.name)
            assertEquals(OutboxStatus.PENDING, b.syncOutboxDao().findById(pending)?.status)
            assertEquals(1, b.syncOutboxDao().listForEntity(SyncEntityType.FARM, farm2).size)
            assertEquals(listOf(1, 2, 2, 2, 1), counts(b))

            // Logout: no receive, nothing deleted, and the synced remote workspace is not offered as local.
            val beforeLogout = snapshot(b)
            account.logout(false)
            assertEquals(ParentReceiveOutcome.NOT_AUTHORIZED,
                ParentReceiveCoordinator(b, account, issuer, transport).receive(workspace).outcome)
            assertTrue(b.workspaceDao().listUnboundLocal().none { it.id == workspace })
            assertEquals(beforeLogout, snapshot(b))

            // Offline: an unreachable server leaves Room readable and unchanged.
            sessions.write(AccountRecord(session = session))
            val offlineConfig = AuthConfiguration("https://10.0.2.2:9", fixture.getString("anonKey"), "qa://auth/callback")
            val offline = AccountRepository(SupabaseAuthApi(offlineConfig, HttpsAuthTransport(offlineConfig)), sessions)
            assertEquals(ParentReceiveOutcome.OFFLINE, ParentReceiveCoordinator(b, offline, issuer,
                HttpsDeliveryRpcTransport(offlineConfig, scope = HttpsDeliveryRpcTransport.RpcScope.PARENT_RECEIVE))
                .receive(workspace).outcome)
            assertEquals(beforeLogout, snapshot(b))
        } finally { b.close() }

        // C: a forged local grant for A's workspace is still refused by the server; C's Room stays empty.
        val foreignActor = UUID.fromString(fixture.getString("foreignActor"))
        val (foreign, _) = account(session(foreignActor, "foreignToken"))
        val c = MaginaOlivoDatabase.create(context, names[1])
        try {
            val now = Instant.now()
            c.workspaceAuthorizationCacheDao().upsert(WorkspaceAuthorizationCacheEntity(issuer, foreignActor.toString(),
                """{"contract_version":1,"issuer":"$issuer/auth/v1","subject":"$foreignActor","verified_at":"$now","token_expires_at":"${now.plusSeconds(600)}","workspaces":[{"id":"$workspace","name":"x","role":"VIEWER","permissions":["read"],"grant_version":1,"authorization_expires_at":"${now.plusSeconds(600)}"}]}"""))
            assertEquals(ParentReceiveOutcome.NOT_AUTHORIZED,
                ParentReceiveCoordinator(c, foreign, issuer, transport).receive(workspace).outcome)
            assertEquals(listOf(0, 0, 0, 0, 0), counts(c))
        } finally { c.close() }

        // M: an active read membership receives; after revocation the grant disappears and the server
        // refuses even a stale cache. What M already received is preserved, never deleted.
        val memberActor = UUID.fromString(fixture.getString("memberActor"))
        val memberSession = session(memberActor, "memberToken")
        val (member, _) = account(memberSession)
        val authorizations = HttpsWorkspaceAuthorizationRemote(endpoint, fixture.getString("anonKey"))
        val m = MaginaOlivoDatabase.create(context, names[2])
        try {
            val memberGrant = authorizations.list(memberSession)
            m.workspaceAuthorizationCacheDao().upsert(WorkspaceAuthorizationCacheEntity(issuer, memberActor.toString(), memberGrant))
            assertEquals(ParentReceiveOutcome.IMPORTED,
                ParentReceiveCoordinator(m, member, issuer, transport).receive(workspace).outcome)
            assertEquals(actor, m.workspaceDao().findById(workspace)?.ownerUserId)
            val received = snapshot(m)
            fetch("/__g26_admin/revoke_member", "POST")
            assertEquals(ParentReceiveOutcome.NOT_AUTHORIZED,
                ParentReceiveCoordinator(m, member, issuer, transport).receive(workspace).outcome)
            val revoked = authorizations.list(memberSession)
            assertTrue(WorkspaceAuthorizationContract.parse(revoked).workspaces.none { it.id == workspace })
            m.workspaceAuthorizationCacheDao().upsert(WorkspaceAuthorizationCacheEntity(issuer, memberActor.toString(), revoked))
            assertEquals(ParentReceiveOutcome.NOT_AUTHORIZED,
                ParentReceiveCoordinator(m, member, issuer, transport).receive(workspace).outcome)
            assertEquals(received, snapshot(m))
        } finally { m.close() }
    }

    private fun uuid(key: String) = UUID.fromString(fixture.getString(key))
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
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally { connection.disconnect() }
    }
    private fun counts(db: MaginaOlivoDatabase) =
        listOf("workspaces", "farms", "parcels", "farm_parcel_memberships", "sync_outbox").map { table ->
            db.openHelper.readableDatabase.query("SELECT count(*) FROM $table").use { it.moveToFirst(); it.getInt(0) }
        }
    private fun snapshot(db: MaginaOlivoDatabase): List<String> =
        listOf("workspaces", "farms", "parcels", "farm_parcel_memberships", "campaigns", "campaign_parcels", "sync_outbox")
            .flatMap { table ->
                db.openHelper.readableDatabase.query("SELECT * FROM $table ORDER BY id").use { cursor ->
                    buildList { while (cursor.moveToNext()) add((0 until cursor.columnCount).joinToString("|") { cursor.getString(it) ?: "∅" }) }
                }
            }
}
