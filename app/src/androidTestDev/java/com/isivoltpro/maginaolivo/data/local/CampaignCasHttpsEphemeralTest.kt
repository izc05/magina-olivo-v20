package com.isivoltpro.maginaolivo.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.isivoltpro.maginaolivo.data.local.entity.WorkspaceAuthorizationCacheEntity
import com.isivoltpro.maginaolivo.data.local.entity.WorkspaceSelectionEntity
import com.isivoltpro.maginaolivo.data.local.model.OutboxStatus
import com.isivoltpro.maginaolivo.data.local.model.SyncStatus
import com.isivoltpro.maginaolivo.data.repository.HttpsWorkspaceAuthorizationRemote
import com.isivoltpro.maginaolivo.data.repository.WorkspaceAuthorizationContract
import com.isivoltpro.maginaolivo.data.repository.parseCampaignCasEnvelope
import com.isivoltpro.maginaolivo.data.repository.sync.CampaignCasCoordinator
import com.isivoltpro.maginaolivo.data.repository.sync.CampaignCasOutcome
import com.isivoltpro.maginaolivo.data.repository.sync.CampaignCasRpcAdapter
import com.isivoltpro.maginaolivo.data.repository.sync.CampaignReceiveCoordinator
import com.isivoltpro.maginaolivo.data.repository.sync.CampaignReceiveOutcome
import com.isivoltpro.maginaolivo.data.repository.sync.DeliveryDispatchLease
import com.isivoltpro.maginaolivo.data.repository.sync.HttpsDeliveryRpcTransport
import com.isivoltpro.maginaolivo.data.repository.sync.DeliveryRpcTransport
import com.isivoltpro.maginaolivo.feature.account.AccountRecord
import com.isivoltpro.maginaolivo.feature.account.AccountRepository
import com.isivoltpro.maginaolivo.feature.account.AuthConfiguration
import com.isivoltpro.maginaolivo.feature.account.AuthSession
import com.isivoltpro.maginaolivo.feature.account.HttpsAuthTransport
import com.isivoltpro.maginaolivo.feature.account.MemoryAccountSessionStore
import com.isivoltpro.maginaolivo.feature.account.SupabaseAuthApi
import java.net.URL
import java.io.IOException
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

/** Explicit DEV-only probe. Requires a disposable local Supabase and a pinned test certificate. */
@RunWith(AndroidJUnit4::class)
class CampaignCasHttpsEphemeralTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val issuer = "https://zubpicdibqbmewxeurhm.supabase.co"
    private val endpoint = "https://10.0.2.2:54443"
    private val databaseName = "g26-https-${UUID.randomUUID()}"
    private lateinit var originalTls: SSLSocketFactory
    private lateinit var db: MaginaOlivoDatabase
    private lateinit var fixture: JSONObject

    @Before fun setup() {
        val encoded = InstrumentationRegistry.getArguments().getString("g26_test_cert")
            ?: error("Disposable TLS certificate required")
        val certificate = CertificateFactory.getInstance("X.509")
            .generateCertificate(Base64.getDecoder().decode(encoded).inputStream())
        val store = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null); setCertificateEntry("g26-ephemeral", certificate)
        }
        val manager = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(store) }
        val context = javax.net.ssl.SSLContext.getInstance("TLS").apply { init(null, manager.trustManagers, null) }
        originalTls = HttpsURLConnection.getDefaultSSLSocketFactory()
        HttpsURLConnection.setDefaultSSLSocketFactory(context.socketFactory)
        val connection = URL("$endpoint/__g26_fixture").openConnection() as HttpsURLConnection
        fixture = try { JSONObject(connection.inputStream.bufferedReader().use { it.readText() }) }
            finally { connection.disconnect() }
        db = MaginaOlivoDatabase.create(this.context, databaseName)
    }

    @After fun cleanup() {
        if (::db.isInitialized) db.close()
        context.deleteDatabase(databaseName)
        if (::originalTls.isInitialized) HttpsURLConnection.setDefaultSSLSocketFactory(originalTls)
    }

    @Test fun realHttpsAckDuplicateAndLostResponsePreserveExactRevisions() = runBlocking {
        val actor = UUID.fromString(fixture.getString("actor"))
        val workspace = UUID.fromString(fixture.getString("workspace"))
        val farm = UUID.fromString(fixture.getString("farm"))
        val parcel = UUID.fromString(fixture.getString("parcel"))
        val campaign = UUID.fromString(fixture.getString("campaign"))
        val snapshot = UUID.fromString(fixture.getString("snapshot"))
        val createOp = UUID.fromString(fixture.getString("createOp"))
        val updateOp = UUID.randomUUID()
        val token = fixture.getString("token")
        val config = AuthConfiguration(endpoint, fixture.getString("anonKey"), "qa://auth/callback")
        val expiration = JSONObject(String(Base64.getUrlDecoder().decode(token.split('.')[1]))).getLong("exp")
        val session = AuthSession(actor, "qa@example.test", token, "unused", Instant.ofEpochSecond(expiration))
        val sessionStore = MemoryAccountSessionStore().apply { write(AccountRecord(session = session)) }
        val account = AccountRepository(SupabaseAuthApi(config, HttpsAuthTransport(config)), sessionStore)
        val transport = HttpsDeliveryRpcTransport(config, scope = HttpsDeliveryRpcTransport.RpcScope.CAMPAIGN_CAS)
        assertEquals(campaign, CampaignCasRpcAdapter(transport).receipt(session, createOp)?.campaignId)
        val grant = HttpsWorkspaceAuthorizationRemote(endpoint, fixture.getString("anonKey")).list(session)
        assertTrue(WorkspaceAuthorizationContract.parse(grant).available(Instant.now()).any {
            it.id == workspace && "write" in it.permissions
        })
        db.workspaceSelectionDao().upsert(WorkspaceSelectionEntity(issuer, actor.toString(), workspace))
        db.workspaceAuthorizationCacheDao().upsert(WorkspaceAuthorizationCacheEntity(issuer, actor.toString(), grant))
        // These are disposable QA rows. The server CREATE completed before its UUID is recorded locally.
        db.openHelper.writableDatabase.execSQL("INSERT INTO workspaces(id,name,owner_user_id,country_code,timezone,locale,currency,created_at,updated_at,version,sync_status) VALUES ('$workspace','QA','$actor','ES','Europe/Madrid','es-ES','EUR',1000,1000,1,'SYNCED')")
        db.openHelper.writableDatabase.execSQL("INSERT INTO farms(id,workspace_id,name,status,created_at,updated_at,version,sync_status) VALUES ('$farm','$workspace','HTTPS farm','ACTIVE',1000,1000,1,'SYNCED')")
        db.openHelper.writableDatabase.execSQL("INSERT INTO parcels(id,workspace_id,display_name,source,status,created_at,updated_at,version,sync_status) VALUES ('$parcel','$workspace','HTTPS parcel','MANUAL','ACTIVE',1000,1000,1,'SYNCED')")
        db.openHelper.writableDatabase.execSQL("INSERT INTO campaigns(id,workspace_id,farm_id,name,start_date,status,created_at,updated_at,version,sync_status,remote_version) VALUES ('$campaign','$workspace','$farm','HTTPS edited','2026-01-01','PREPARATION',1000,1000,2,'PENDING',1)")
        db.openHelper.writableDatabase.execSQL("INSERT INTO campaign_parcels(id,workspace_id,campaign_id,parcel_id,farm_id_at_start,farm_name_at_start,parcel_name_at_start,created_at,updated_at,version,sync_status) VALUES ('$snapshot','$workspace','$campaign','$parcel','$farm','HTTPS farm','HTTPS parcel',1000,1000,1,'SYNCED')")
        db.openHelper.writableDatabase.execSQL("INSERT INTO sync_outbox(id,entity_type,entity_id,operation,payload_version,status,attempt_count,created_at,updated_at) VALUES ('$createOp','CAMPAIGN','$campaign','CREATE',1,'CONFIRMED',1,1000,1000)")
        db.openHelper.writableDatabase.execSQL("INSERT INTO sync_outbox(id,entity_type,entity_id,operation,payload_version,status,attempt_count,created_at,updated_at) VALUES ('$updateOp','CAMPAIGN','$campaign','UPDATE',1,'PENDING',0,1001,1001)")

        val coordinator = CampaignCasCoordinator(db, account, issuer, CampaignCasRpcAdapter(transport))
        assertEquals(CampaignCasOutcome.STAGED, coordinator.stage(updateOp))
        val staged = db.syncOutboxDao().findById(updateOp)!!
        assertEquals(OutboxStatus.PENDING, staged.status)
        assertEquals(1L, staged.baseRemoteVersion)
        assertTrue(!staged.payloadJson.isNullOrBlank())
        val lease = DeliveryDispatchLease.tryAcquire(java.io.File(context.noBackupFilesDir, "g26-https-lease"))!!
        val outcome = try { coordinator.dispatch(updateOp, lease) } finally { lease.close() }
        assertEquals(CampaignCasOutcome.SYNCED, outcome)
        assertEquals(OutboxStatus.CONFIRMED, db.syncOutboxDao().findById(updateOp)?.status)
        assertEquals(SyncStatus.SYNCED, db.campaignDao().findById(campaign)?.metadata?.syncStatus)
        assertEquals(2L, db.campaignDao().findById(campaign)?.metadata?.remoteVersion)

        // B is a separate durable Room file. Parents are fixture rows because general FARM/PARCEL
        // reception is not implemented; the receiver is forbidden to invent them.
        val bName = "g26-receiver-${UUID.randomUUID()}"
        var b = MaginaOlivoDatabase.create(context, bName)
        try {
            b.workspaceSelectionDao().upsert(WorkspaceSelectionEntity(issuer, actor.toString(), workspace))
            b.workspaceAuthorizationCacheDao().upsert(WorkspaceAuthorizationCacheEntity(issuer, actor.toString(), grant))
            val receiveTransport = HttpsDeliveryRpcTransport(config,
                scope = HttpsDeliveryRpcTransport.RpcScope.CAMPAIGN_RECEIVE)
            var receiver = CampaignReceiveCoordinator(b, account, issuer, receiveTransport)
            assertEquals(CampaignReceiveOutcome.PARENTS_MISSING, receiver.receive(campaign))
            b.openHelper.writableDatabase.execSQL("INSERT INTO workspaces(id,name,owner_user_id,country_code,timezone,locale,currency,created_at,updated_at,version,sync_status) VALUES ('$workspace','QA','$actor','ES','Europe/Madrid','es-ES','EUR',1000,1000,1,'SYNCED')")
            b.openHelper.writableDatabase.execSQL("INSERT INTO farms(id,workspace_id,name,status,created_at,updated_at,version,sync_status) VALUES ('$farm','$workspace','HTTPS farm','ACTIVE',1000,1000,1,'SYNCED')")
            b.openHelper.writableDatabase.execSQL("INSERT INTO parcels(id,workspace_id,display_name,source,status,created_at,updated_at,version,sync_status) VALUES ('$parcel','$workspace','HTTPS parcel','MANUAL','ACTIVE',1000,1000,1,'SYNCED')")
            assertEquals(CampaignReceiveOutcome.IMPORTED, receiver.receive(campaign))
            assertEquals("HTTPS edited", b.campaignDao().findById(campaign)?.name)
            assertEquals(1, b.campaignDao().listSnapshots(campaign).size)
            assertEquals(CampaignReceiveOutcome.ALREADY_CURRENT, receiver.receive(campaign))
            assertEquals(1, b.campaignDao().listSnapshots(campaign).size)
            b.close()
            b = MaginaOlivoDatabase.create(context, bName)
            receiver = CampaignReceiveCoordinator(b, account, issuer, receiveTransport)
            assertEquals("HTTPS edited", b.campaignDao().findById(campaign)?.name)
            assertEquals(CampaignReceiveOutcome.ALREADY_CURRENT, receiver.receive(campaign))

            val foreignToken = fixture.getString("foreignToken")
            val foreignActor = UUID.fromString(fixture.getString("foreignActor"))
            val foreignExp = JSONObject(String(Base64.getUrlDecoder().decode(foreignToken.split('.')[1]))).getLong("exp")
            val foreignSession = AuthSession(foreignActor, "foreign@example.test", foreignToken,
                "unused", Instant.ofEpochSecond(foreignExp))
            val foreignStore = MemoryAccountSessionStore().apply { write(AccountRecord(session = foreignSession)) }
            val foreignAccount = AccountRepository(SupabaseAuthApi(config, HttpsAuthTransport(config)), foreignStore)
            assertEquals(CampaignReceiveOutcome.NOT_AUTHORIZED,
                CampaignReceiveCoordinator(b, foreignAccount, issuer, receiveTransport).receive(campaign))
            assertEquals("HTTPS edited", b.campaignDao().findById(campaign)?.name)
        val receipt = CampaignCasRpcAdapter(transport).receipt(session, updateOp)!!
        assertEquals(updateOp, receipt.operationId)
        assertEquals(campaign, receipt.campaignId)
        assertEquals(2L, receipt.remoteVersion)
        val stagedSnapshot = parseCampaignCasEnvelope(staged.payloadJson!!)!!
        assertEquals(2L, CampaignCasRpcAdapter(transport).update(session, updateOp, 1, stagedSnapshot.campaign).remoteVersion)
        assertEquals(2L, CampaignCasRpcAdapter(transport).receipt(session, updateOp)?.remoteVersion)
        assertEquals(2L, db.campaignDao().findById(campaign)?.metadata?.remoteVersion)

        val next = UUID.randomUUID()
        val current = db.campaignDao().findById(campaign)!!
        db.campaignDao().upsert(current.copy(name = "Second HTTPS edit", metadata = current.metadata.copy(
            version = 3, syncStatus = SyncStatus.PENDING)))
        db.openHelper.writableDatabase.execSQL("INSERT INTO sync_outbox(id,entity_type,entity_id,operation,payload_version,status,attempt_count,created_at,updated_at) VALUES ('$next','CAMPAIGN','$campaign','UPDATE',1,'PENDING',0,1002,1002)")
        var sends = 0
        var loseOnce = true
        val responseLoss = DeliveryRpcTransport { name, body, accessToken ->
            val response = transport.rpc(name, body, accessToken)
            if (name == "update_own_campaign_v1") {
                sends++
                if (loseOnce) { loseOnce = false; throw IOException("response lost after server commit") }
            }
            response
        }
        val recovery = CampaignCasCoordinator(db, account, issuer, CampaignCasRpcAdapter(responseLoss))
        assertEquals(CampaignCasOutcome.STAGED, recovery.stage(next))
        suspend fun dispatchNext(): CampaignCasOutcome {
            val held = DeliveryDispatchLease.tryAcquire(java.io.File(context.noBackupFilesDir, "g26-https-lease"))!!
            return try { recovery.dispatch(next, held) } finally { held.close() }
        }
        assertEquals(CampaignCasOutcome.OUTCOME_UNKNOWN, dispatchNext())
        assertEquals(OutboxStatus.FAILED, db.syncOutboxDao().findById(next)?.status)
        assertEquals(CampaignCasOutcome.SYNCED, dispatchNext())
        assertEquals(1, sends)
        assertEquals(OutboxStatus.CONFIRMED, db.syncOutboxDao().findById(next)?.status)
        assertEquals(3L, db.campaignDao().findById(campaign)?.metadata?.remoteVersion)
        assertEquals("Second HTTPS edit", db.campaignDao().findById(campaign)?.name)
        assertEquals(3, db.syncOutboxDao().listForEntity(com.isivoltpro.maginaolivo.data.local.model.SyncEntityType.CAMPAIGN, campaign).size)
        assertEquals(CampaignReceiveOutcome.UPDATED, receiver.receive(campaign))
        assertEquals("Second HTTPS edit", b.campaignDao().findById(campaign)?.name)
        assertEquals(3L, b.campaignDao().findById(campaign)?.metadata?.remoteVersion)
        assertEquals(CampaignReceiveOutcome.ALREADY_CURRENT, receiver.receive(campaign))
        val bCurrent = b.campaignDao().findById(campaign)!!
        b.campaignDao().upsert(bCurrent.copy(name = "B offline edit", metadata = bCurrent.metadata.copy(
            version = 4, syncStatus = SyncStatus.PENDING)))
        val bPending = UUID.randomUUID()
        b.openHelper.writableDatabase.execSQL("INSERT INTO sync_outbox(id,entity_type,entity_id,operation,payload_version,status,attempt_count,created_at,updated_at) VALUES ('$bPending','CAMPAIGN','$campaign','UPDATE',1,'PENDING',0,1003,1003)")
        assertEquals(CampaignReceiveOutcome.CONFLICT, receiver.receive(campaign))
        assertEquals("B offline edit", b.campaignDao().findById(campaign)?.name)
        assertEquals(1, b.syncOutboxDao().listForEntity(
            com.isivoltpro.maginaolivo.data.local.model.SyncEntityType.CAMPAIGN, campaign).size)
        assertEquals(OutboxStatus.PENDING, b.syncOutboxDao().findById(bPending)?.status)
        } finally { b.close(); context.deleteDatabase(bName) }
    }
}
