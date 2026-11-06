package com.isivoltpro.maginaolivo.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.isivoltpro.maginaolivo.data.local.entity.WorkspaceAuthorizationCacheEntity
import com.isivoltpro.maginaolivo.data.local.entity.WorkspaceSelectionEntity
import com.isivoltpro.maginaolivo.data.local.model.OutboxStatus
import com.isivoltpro.maginaolivo.data.local.model.SyncStatus
import com.isivoltpro.maginaolivo.data.repository.parseCampaignCasEnvelope
import com.isivoltpro.maginaolivo.data.repository.sync.CampaignCasCoordinator
import com.isivoltpro.maginaolivo.data.repository.sync.CampaignCasOutcome
import com.isivoltpro.maginaolivo.data.repository.sync.CampaignCasRpcAdapter
import com.isivoltpro.maginaolivo.data.repository.sync.CampaignCasReconciler
import com.isivoltpro.maginaolivo.data.repository.sync.CampaignReconcileOutcome
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
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CampaignCasRoomTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "g26-campaign-cas-room"
    private val issuer = "https://project.example"
    private val a = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
    private val c = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc")
    private val workspace = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val farm = UUID.fromString("22222222-2222-2222-2222-222222222222")
    private val campaign = UUID.fromString("33333333-3333-3333-3333-333333333333")
    private val createOp = UUID.fromString("44444444-4444-4444-4444-444444444444")
    private val updateOp = UUID.fromString("55555555-5555-5555-5555-555555555555")
    private var now = Instant.parse("2026-10-29T11:00:00Z")
    private lateinit var db: MaginaOlivoDatabase
    private lateinit var account: AccountRepository
    private lateinit var transport: FakeTransport
    private lateinit var coordinator: CampaignCasCoordinator
    private val store = MemoryAccountSessionStore()

    @Before fun setup() = runBlocking {
        context.deleteDatabase(name)
        db = MaginaOlivoDatabase.create(context, name)
        store.write(AccountRecord(session=AuthSession(a,"a@example.test","access","refresh",now.plusSeconds(3600))))
        account = AccountRepository(FakeAuth,store) { now }
        db.openHelper.writableDatabase.execSQL("INSERT INTO workspaces(id,name,owner_user_id,country_code,timezone,locale,currency,created_at,updated_at,version,sync_status) VALUES ('$workspace','QA','$a','ES','Europe/Madrid','es-ES','EUR',1000,1000,1,'SYNCED')")
        db.openHelper.writableDatabase.execSQL("INSERT INTO farms(id,workspace_id,name,status,created_at,updated_at,version,sync_status) VALUES ('$farm','$workspace','Farm','ACTIVE',1000,1000,1,'SYNCED')")
        db.openHelper.writableDatabase.execSQL("INSERT INTO campaigns(id,workspace_id,farm_id,name,start_date,status,created_at,updated_at,version,sync_status,remote_version) VALUES ('$campaign','$workspace','$farm','Edited','2026-01-01','PREPARATION',1000,1000,2,'PENDING',1)")
        db.openHelper.writableDatabase.execSQL("INSERT INTO sync_outbox(id,entity_type,entity_id,operation,payload_version,status,attempt_count,created_at,updated_at) VALUES ('$createOp','CAMPAIGN','$campaign','CREATE',1,'CONFIRMED',1,1000,1000)")
        db.openHelper.writableDatabase.execSQL("INSERT INTO sync_outbox(id,entity_type,entity_id,operation,payload_version,status,attempt_count,created_at,updated_at) VALUES ('$updateOp','CAMPAIGN','$campaign','UPDATE',1,'PENDING',0,1001,1001)")
        db.workspaceSelectionDao().upsert(WorkspaceSelectionEntity(issuer,a.toString(),workspace))
        db.workspaceAuthorizationCacheDao().upsert(WorkspaceAuthorizationCacheEntity(issuer,a.toString(),contract(a)))
        transport = FakeTransport()
        coordinator = coordinator()
    }

    @After fun close() {
        if (::db.isInitialized) db.close()
        context.deleteDatabase(name)
    }

    @Test fun ackOnlyConfirmsExactRevisionAndKeepsOutbox() = runBlocking {
        assertEquals(CampaignCasOutcome.STAGED,coordinator.stage(updateOp))
        assertEquals(CampaignCasOutcome.SYNCED,dispatch())
        assertEquals(OutboxStatus.CONFIRMED,db.syncOutboxDao().findById(updateOp)?.status)
        assertEquals(SyncStatus.SYNCED,db.campaignDao().findById(campaign)?.metadata?.syncStatus)
        assertEquals(2L,db.campaignDao().findById(campaign)?.metadata?.remoteVersion)
        assertEquals(1,transport.sends)
        assertEquals(2,db.syncOutboxDao().listForEntity(com.isivoltpro.maginaolivo.data.local.model.SyncEntityType.CAMPAIGN,campaign).size)
    }

    @Test fun timeoutThenRestartRecoversReceiptBeforeAnotherSend() = runBlocking {
        assertEquals(CampaignCasOutcome.STAGED,coordinator.stage(updateOp))
        transport.mode = Mode.TIMEOUT_AFTER_COMMIT
        assertEquals(CampaignCasOutcome.OUTCOME_UNKNOWN,dispatch())
        assertEquals(OutboxStatus.FAILED,db.syncOutboxDao().findById(updateOp)?.status)
        db.close()
        db = MaginaOlivoDatabase.create(context,name)
        coordinator = coordinator()
        transport.mode = Mode.NORMAL
        assertEquals(CampaignCasOutcome.SYNCED,dispatch())
        assertEquals(1,transport.sends)
        assertEquals(OutboxStatus.CONFIRMED,db.syncOutboxDao().findById(updateOp)?.status)
    }

    @Test fun concurrentLocalEditAndRemoteConflictRemainRecoverable() = runBlocking {
        assertEquals(CampaignCasOutcome.STAGED,coordinator.stage(updateOp))
        transport.mode = Mode.LOCAL_EDIT_DURING_ACK
        assertEquals(CampaignCasOutcome.NEEDS_REBASE,dispatch())
        assertEquals("Later local edit",db.campaignDao().findById(campaign)?.name)
        assertEquals(SyncStatus.CONFLICT,db.campaignDao().findById(campaign)?.metadata?.syncStatus)
        assertEquals(OutboxStatus.CONFIRMED,db.syncOutboxDao().findById(updateOp)?.status)
        assertEquals(OutboxStatus.BLOCKED,db.syncOutboxDao().findById(transport.sibling)?.status)
        val second = UUID.randomUUID()
        db.openHelper.writableDatabase.execSQL("INSERT INTO sync_outbox(id,entity_type,entity_id,operation,payload_version,status,attempt_count,created_at,updated_at) VALUES ('$second','CAMPAIGN','$campaign','UPDATE',1,'PENDING',0,1002,1002)")
        assertEquals(CampaignCasOutcome.REJECTED,coordinator.stage(second))
    }

    @Test fun stale409AndIdentityChangesNeverAcknowledge() = runBlocking {
        assertEquals(CampaignCasOutcome.STAGED,coordinator.stage(updateOp))
        transport.mode = Mode.CONFLICT
        assertEquals(CampaignCasOutcome.CONFLICT,dispatch())
        assertEquals(OutboxStatus.BLOCKED,db.syncOutboxDao().findById(updateOp)?.status)
        assertEquals(SyncStatus.CONFLICT,db.campaignDao().findById(campaign)?.metadata?.syncStatus)
        assertEquals(1L,db.campaignDao().findById(campaign)?.metadata?.remoteVersion)
    }

    @Test fun logoutExpiredCacheAndDifferentAccountFailClosed() = runBlocking {
        assertEquals(CampaignCasOutcome.STAGED,coordinator.stage(updateOp))
        account.logout(false)
        assertEquals(CampaignCasOutcome.NOT_AUTHORIZED,dispatch())
        assertEquals(OutboxStatus.PENDING,db.syncOutboxDao().findById(updateOp)?.status)
        store.write(AccountRecord(session=AuthSession(c,"c@example.test","other","refresh",now.plusSeconds(3600))))
        account = AccountRepository(FakeAuth,store) { now }
        coordinator=coordinator()
        assertEquals(CampaignCasOutcome.NOT_AUTHORIZED,dispatch())
        store.write(AccountRecord(session=AuthSession(a,"a@example.test","access","refresh",now.minusSeconds(1))))
        account = AccountRepository(FakeAuth,store) { now }
        coordinator=coordinator()
        assertEquals(CampaignCasOutcome.NOT_AUTHORIZED,dispatch())
        store.write(AccountRecord(session=AuthSession(a,"a@example.test","access","refresh",now.plusSeconds(3600))))
        account=AccountRepository(FakeAuth,store) { now }
        coordinator=coordinator()
        now=now.plusSeconds(901)
        assertEquals(CampaignCasOutcome.NOT_AUTHORIZED,dispatch())
        assertEquals(OutboxStatus.PENDING,db.syncOutboxDao().findById(updateOp)?.status)
    }

    @Test fun foreignIssuerAndWorkspaceSelectionNeverSend() = runBlocking {
        assertEquals(CampaignCasOutcome.STAGED,coordinator.stage(updateOp))
        db.workspaceSelectionDao().upsert(WorkspaceSelectionEntity(issuer,a.toString(),UUID.randomUUID()))
        assertEquals(CampaignCasOutcome.NOT_AUTHORIZED,dispatch())
        assertEquals(0,transport.sends)
        db.workspaceSelectionDao().upsert(WorkspaceSelectionEntity(issuer,a.toString(),workspace))
        db.workspaceAuthorizationCacheDao().upsert(WorkspaceAuthorizationCacheEntity(issuer,a.toString(),
            contract(a).replace("$issuer/auth/v1","https://other.example/auth/v1")))
        assertEquals(CampaignCasOutcome.NOT_AUTHORIZED,dispatch())
        assertEquals(OutboxStatus.PENDING,db.syncOutboxDao().findById(updateOp)?.status)
    }

    @Test fun offlineBeforeCommitRetriesSameIntentAfterReconnect() = runBlocking {
        assertEquals(CampaignCasOutcome.STAGED,coordinator.stage(updateOp))
        transport.mode=Mode.OFFLINE_NO_COMMIT
        assertEquals(CampaignCasOutcome.OUTCOME_UNKNOWN,dispatch())
        assertEquals(OutboxStatus.FAILED,db.syncOutboxDao().findById(updateOp)?.status)
        transport.mode=Mode.NORMAL
        assertEquals(CampaignCasOutcome.SYNCED,dispatch())
        assertEquals(2,transport.sends)
        assertEquals(OutboxStatus.CONFIRMED,db.syncOutboxDao().findById(updateOp)?.status)
    }

    @Test fun changedParcelSnapshotCannotBeAcknowledgedByHeaderOnlyRpc() = runBlocking {
        val parcel=UUID.randomUUID()
        val snapshot=UUID.randomUUID()
        db.openHelper.writableDatabase.execSQL("INSERT INTO parcels(id,workspace_id,display_name,source,status,created_at,updated_at,version,sync_status) VALUES ('$parcel','$workspace','Parcel','MANUAL','ACTIVE',1000,1000,1,'SYNCED')")
        db.openHelper.writableDatabase.execSQL("INSERT INTO campaign_parcels(id,workspace_id,campaign_id,parcel_id,farm_id_at_start,farm_name_at_start,parcel_name_at_start,created_at,updated_at,version,sync_status) VALUES ('$snapshot','$workspace','$campaign','$parcel','$farm','Farm','Parcel',1000,1000,1,'PENDING')")
        assertEquals(CampaignCasOutcome.REJECTED,coordinator.stage(updateOp))
        assertEquals(0,transport.sends)
        assertEquals(OutboxStatus.PENDING,db.syncOutboxDao().findById(updateOp)?.status)
    }

    @Test fun replacingUnstagedMarkerNeverDeletesHistoricalCreate() = runBlocking {
        val historical=UUID.randomUUID()
        db.openHelper.writableDatabase.execSQL("INSERT INTO sync_outbox(id,entity_type,entity_id,operation,payload_version,status,attempt_count,created_at,updated_at) VALUES ('$historical','CAMPAIGN','$campaign','CREATE',1,'PENDING',0,1002,1002)")
        db.syncOutboxDao().deleteUnstagedCampaignMarkers(campaign)
        assertEquals(OutboxStatus.PENDING,db.syncOutboxDao().findById(historical)?.status)
        assertEquals(null,db.syncOutboxDao().findById(updateOp))
    }

    @Test fun fixtureReceiptWithWrongHashCannotConfirmHistoricalIntent() = runBlocking {
        assertEquals(CampaignCasOutcome.STAGED,coordinator.stage(updateOp))
        transport.mode=Mode.WRONG_RECEIPT_HASH
        assertEquals(CampaignCasOutcome.CONFLICT,dispatch())
        assertEquals(OutboxStatus.BLOCKED,db.syncOutboxDao().findById(updateOp)?.status)
        assertEquals(1L,db.campaignDao().findById(campaign)?.metadata?.remoteVersion)
    }

    @Test fun logoutDuringResponseLeavesIntentForLaterReceiptRecovery() = runBlocking {
        assertEquals(CampaignCasOutcome.STAGED,coordinator.stage(updateOp))
        transport.mode=Mode.LOGOUT_DURING_RESPONSE
        assertEquals(CampaignCasOutcome.NOT_AUTHORIZED,dispatch())
        assertEquals(OutboxStatus.PENDING,db.syncOutboxDao().findById(updateOp)?.status)
        store.write(AccountRecord(session=AuthSession(a,"a@example.test","access","refresh",now.plusSeconds(3600))))
        account=AccountRepository(FakeAuth,store) { now }
        coordinator=coordinator()
        transport.mode=Mode.NORMAL
        assertEquals(CampaignCasOutcome.SYNCED,dispatch())
        assertEquals(1,transport.sends)
    }

    @Test fun explicitConflictResolutionKeepsBothRevisionsAndOldIntent() = runBlocking {
        assertEquals(CampaignCasOutcome.STAGED, coordinator.stage(updateOp))
        transport.mode=Mode.CONFLICT
        assertEquals(CampaignCasOutcome.CONFLICT, dispatch())
        transport.headVersion=2
        val resolver=resolver()
        assertEquals(CampaignReconcileOutcome.CAPTURED,resolver.capture(updateOp))
        val comparison=resolver.comparison(updateOp)!!
        assertTrue("name" in comparison.changedFields)
        assertEquals("CAS_CONFLICT",comparison.record.reason)
        assertEquals("Edited",db.campaignDao().findById(campaign)?.name)
        val replacement=UUID.randomUUID()
        assertEquals(CampaignReconcileOutcome.RESOLVED,resolver.keepLocal(updateOp,replacement))
        assertEquals("RESOLVED_CAS_CONFLICT",db.syncOutboxDao().findById(updateOp)?.lastErrorCode)
        assertEquals(OutboxStatus.BLOCKED,db.syncOutboxDao().findById(updateOp)?.status)
        assertEquals(2L,db.syncOutboxDao().findById(replacement)?.baseRemoteVersion)
        assertEquals(replacement,resolver.comparison(updateOp)?.record?.resolvedOperationId)
        transport.mode=Mode.NORMAL
        assertEquals(CampaignCasOutcome.SYNCED,dispatch(replacement))
        assertEquals(3L,db.campaignDao().findById(campaign)?.metadata?.remoteVersion)
        assertEquals(3,db.syncOutboxDao().listForEntity(com.isivoltpro.maginaolivo.data.local.model.SyncEntityType.CAMPAIGN,campaign).size)
    }

    @Test fun laterLocalRevisionCanBeExplicitlyRebasedWithoutReplacingIt() = runBlocking {
        assertEquals(CampaignCasOutcome.STAGED, coordinator.stage(updateOp))
        transport.mode=Mode.LOCAL_EDIT_DURING_ACK
        assertEquals(CampaignCasOutcome.NEEDS_REBASE,dispatch())
        transport.mode=Mode.NORMAL
        transport.headVersion=2
        val resolver=resolver()
        assertEquals(CampaignReconcileOutcome.CAPTURED,resolver.capture(updateOp))
        val replacement=UUID.randomUUID()
        assertEquals(CampaignReconcileOutcome.RESOLVED,resolver.keepLocal(updateOp,replacement))
        assertEquals("Later local edit",db.campaignDao().findById(campaign)?.name)
        assertEquals(OutboxStatus.BLOCKED,db.syncOutboxDao().findById(transport.sibling)?.status)
        assertEquals("RESOLVED_NEEDS_REBASE",db.syncOutboxDao().findById(transport.sibling)?.lastErrorCode)
        assertEquals(OutboxStatus.CONFIRMED,db.syncOutboxDao().findById(updateOp)?.status)
    }

    @Test fun unknownOutcomeMustRecoverReceiptBeforeHeadOrDecision() = runBlocking {
        assertEquals(CampaignCasOutcome.STAGED, coordinator.stage(updateOp))
        transport.mode=Mode.TIMEOUT_AFTER_COMMIT
        assertEquals(CampaignCasOutcome.OUTCOME_UNKNOWN,dispatch())
        assertEquals(CampaignReconcileOutcome.OUTCOME_UNKNOWN,resolver().capture(updateOp))
        assertEquals(null,db.campaignReconciliationDao().find(updateOp))
        transport.mode=Mode.NORMAL
        assertEquals(CampaignCasOutcome.SYNCED,dispatch())
        assertEquals(1,transport.sends)
    }

    @Test fun wrongReceiptCannotBecomeEditableConflict() = runBlocking {
        assertEquals(CampaignCasOutcome.STAGED, coordinator.stage(updateOp))
        transport.mode=Mode.WRONG_RECEIPT_HASH
        assertEquals(CampaignCasOutcome.CONFLICT,dispatch())
        assertEquals(CampaignReconcileOutcome.RECEIPT_CONFLICT,resolver().capture(updateOp))
        assertEquals(null,db.campaignReconciliationDao().find(updateOp))
    }

    @Test fun receiptForAnotherEntityCannotBecomeEditableConflict() = runBlocking {
        assertEquals(CampaignCasOutcome.STAGED, coordinator.stage(updateOp))
        transport.mode=Mode.WRONG_RECEIPT_ENTITY
        assertEquals(CampaignCasOutcome.CONFLICT,dispatch())
        assertEquals(CampaignReconcileOutcome.RECEIPT_CONFLICT,resolver().capture(updateOp))
        assertEquals(null,db.campaignReconciliationDao().find(updateOp))
    }

    @Test fun restartAndChangedAccountCannotResolveCapturedRevision() = runBlocking {
        assertEquals(CampaignCasOutcome.STAGED, coordinator.stage(updateOp))
        transport.mode=Mode.CONFLICT
        assertEquals(CampaignCasOutcome.CONFLICT,dispatch())
        assertEquals(CampaignReconcileOutcome.CAPTURED,resolver().capture(updateOp))
        db.close()
        db=MaginaOlivoDatabase.create(context,name)
        assertEquals("CAS_CONFLICT",resolver().comparison(updateOp)?.record?.reason)
        store.write(AccountRecord(session=AuthSession(c,"c@example.test","other","refresh",now.plusSeconds(3600))))
        account=AccountRepository(FakeAuth,store) { now }
        assertEquals(null,resolver().comparison(updateOp))
        assertEquals(CampaignReconcileOutcome.NOT_AUTHORIZED,resolver().keepLocal(updateOp,UUID.randomUUID()))
        assertEquals(null,db.campaignReconciliationDao().find(updateOp)?.resolvedOperationId)
        store.write(AccountRecord(session=AuthSession(a,"a@example.test","access","refresh",now.plusSeconds(3600))))
        account=AccountRepository(FakeAuth,store) { now }
        assertEquals("CAS_CONFLICT",resolver().comparison(updateOp)?.record?.reason)
    }

    @Test fun revocationExpiryAndLogoutDuringHeadKeepConflictBlocked() = runBlocking {
        assertEquals(CampaignCasOutcome.STAGED, coordinator.stage(updateOp))
        transport.mode=Mode.CONFLICT
        assertEquals(CampaignCasOutcome.CONFLICT,dispatch())
        transport.mode=Mode.LOGOUT_DURING_HEAD
        assertEquals(CampaignReconcileOutcome.NOT_AUTHORIZED,resolver().capture(updateOp))
        assertEquals(null,db.campaignReconciliationDao().find(updateOp))
        store.write(AccountRecord(session=AuthSession(a,"a@example.test","access","refresh",now.plusSeconds(3600))))
        account=AccountRepository(FakeAuth,store) { now }
        transport.mode=Mode.NORMAL
        assertEquals(CampaignReconcileOutcome.CAPTURED,resolver().capture(updateOp))
        db.workspaceAuthorizationCacheDao().upsert(WorkspaceAuthorizationCacheEntity(issuer,a.toString(),
            contract(a).replace("\"read\",\"write\",\"manage_members\"","\"read\"")))
        assertEquals(CampaignReconcileOutcome.NOT_AUTHORIZED,resolver().keepLocal(updateOp,UUID.randomUUID()))
        db.workspaceAuthorizationCacheDao().upsert(WorkspaceAuthorizationCacheEntity(issuer,a.toString(),contract(a)))
        now=now.plusSeconds(901)
        assertEquals(CampaignReconcileOutcome.NOT_AUTHORIZED,resolver().keepLocal(updateOp,UUID.randomUUID()))
        assertEquals(OutboxStatus.BLOCKED,db.syncOutboxDao().findById(updateOp)?.status)
    }

    @Test fun foreignIssuerHeadIsNeverPersisted() = runBlocking {
        assertEquals(CampaignCasOutcome.STAGED,coordinator.stage(updateOp))
        transport.mode=Mode.CONFLICT
        assertEquals(CampaignCasOutcome.CONFLICT,dispatch())
        transport.headIssuer="https://other.example/auth/v1"
        assertEquals(CampaignReconcileOutcome.NOT_AUTHORIZED,resolver().capture(updateOp))
        assertEquals(null,db.campaignReconciliationDao().find(updateOp))
    }

    private fun coordinator()=CampaignCasCoordinator(db,account,issuer,CampaignCasRpcAdapter(transport)) { now }
    private fun resolver()=CampaignCasReconciler(db,account,issuer,CampaignCasRpcAdapter(transport)) { now }
    private suspend fun dispatch(operationId: UUID = updateOp): CampaignCasOutcome {
        val dir=File(context.cacheDir,"g26-cas-lease")
        val lease=DeliveryDispatchLease.tryAcquire(dir)!!
        return try { coordinator.dispatch(operationId,lease) } finally { lease.close() }
    }
    private fun contract(id: UUID)="""{"contract_version":1,"issuer":"$issuer/auth/v1","subject":"$id","verified_at":"$now","token_expires_at":"${now.plusSeconds(3600)}","workspaces":[{"id":"$workspace","name":"QA","role":"OWNER","permissions":["read","write","manage_members"],"grant_version":1,"authorization_expires_at":"${now.plusSeconds(900)}"}]}"""

    private enum class Mode { NORMAL, TIMEOUT_AFTER_COMMIT, OFFLINE_NO_COMMIT, LOCAL_EDIT_DURING_ACK,
        WRONG_RECEIPT_HASH, WRONG_RECEIPT_ENTITY, LOGOUT_DURING_RESPONSE, LOGOUT_DURING_HEAD, CONFLICT }
    private inner class FakeTransport : DeliveryRpcTransport {
        var mode=Mode.NORMAL
        var sends=0
        var receipt: String?=null
        var sibling: UUID=UUID.randomUUID()
        var headVersion=1L
        var headIssuer="$issuer/auth/v1"
        override suspend fun rpc(name: String, bodyJson: String, accessToken: String): DeliveryRpcResponse {
            if (name=="check_sync_operations") return DeliveryRpcResponse(200,
                """{"confirmed_receipts":[${receipt ?: ""}]}""")
            if (name=="get_own_campaign_head_v1") {
                if (mode==Mode.LOGOUT_DURING_HEAD) account.logout(false)
                return DeliveryRpcResponse(200,"""{"contract_version":1,"issuer":"$headIssuer","subject":"$a","version":$headVersion,"campaign":{"id":"$campaign","workspaceId":"$workspace","farmId":"$farm","name":"Remote edit","startDate":"2026-01-01","endDate":null,"status":"PREPARATION","notes":null}}""")
            }
            assertEquals("update_own_campaign_v1",name)
            assertEquals("access",accessToken)
            sends++
            if (mode==Mode.CONFLICT) return DeliveryRpcResponse(409,"""{"code":"23505"}""")
            if (mode==Mode.OFFLINE_NO_COMMIT) throw IOException("simulated offline")
            val request=JSONObject(bodyJson)
            val operationId=UUID.fromString(request.getString("p_operation_id"))
            val remoteVersion=request.getLong("p_expected_version")+1
            val op=db.syncOutboxDao().findById(operationId)!!
            val hash=parseCampaignCasEnvelope(op.payloadJson!!)!!.hash
            receipt="""{"operation_id":"$operationId","entity_id":"${if(mode==Mode.WRONG_RECEIPT_ENTITY) UUID.randomUUID() else campaign}","entity_type":"CAMPAIGN","payload_hash":"${if(mode==Mode.WRONG_RECEIPT_HASH) "0".repeat(64) else hash}","payload_version":1,"remote_version":$remoteVersion}"""
            if (mode==Mode.LOCAL_EDIT_DURING_ACK) {
                val current=db.campaignDao().findById(campaign)!!
                db.campaignDao().upsert(current.copy(name="Later local edit",metadata=current.metadata.copy(version=3,syncStatus=SyncStatus.PENDING)))
                db.openHelper.writableDatabase.execSQL("INSERT INTO sync_outbox(id,entity_type,entity_id,operation,payload_version,status,attempt_count,created_at,updated_at) VALUES ('$sibling','CAMPAIGN','$campaign','UPDATE',1,'PENDING',0,1002,1002)")
            }
            if (mode==Mode.TIMEOUT_AFTER_COMMIT) throw IOException("simulated lost response")
            if (mode==Mode.LOGOUT_DURING_RESPONSE) account.logout(false)
            return DeliveryRpcResponse(200,"""{"operation_id":"$operationId","entity_id":"$campaign","remote_version":$remoteVersion,"payload_version":1,"status":"APPLIED"}""")
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
