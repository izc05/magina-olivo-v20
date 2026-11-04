package com.isivoltpro.maginaolivo.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.core.dispatchers.DefaultAppDispatchers
import com.isivoltpro.maginaolivo.core.id.UuidGenerator
import com.isivoltpro.maginaolivo.core.regional.RegionalContext
import com.isivoltpro.maginaolivo.core.time.SystemAppClock
import com.isivoltpro.maginaolivo.data.repository.LocalWorkspaceRepository
import com.isivoltpro.maginaolivo.data.repository.WorkspaceAuthorizationRemote
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstFarmRepository
import com.isivoltpro.maginaolivo.domain.farm.NewFarm
import com.isivoltpro.maginaolivo.feature.account.AccountAuthApi
import com.isivoltpro.maginaolivo.feature.account.AccountRecord
import com.isivoltpro.maginaolivo.feature.account.AccountRepository
import com.isivoltpro.maginaolivo.feature.account.AuthSession
import com.isivoltpro.maginaolivo.feature.account.MemoryAccountSessionStore
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkspaceSelectionRoomTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "g26-selector-test"
    private val issuer = "https://project.example"
    private val a = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
    private val c = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc")
    private val w1 = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val w2 = UUID.fromString("22222222-2222-2222-2222-222222222222")
    private val local = UUID.fromString("33333333-3333-3333-3333-333333333333")
    private var database: MaginaOlivoDatabase? = null
    private var now = Instant.now()

    @After fun close() { database?.close(); context.deleteDatabase(name) }

    @Test fun accountSwitchOfflineRestartAndWritesRemainIsolated() = runBlocking {
        val db = open()
        insertWorkspace(db, w1, "A one")
        insertWorkspace(db, w2, "A two")
        insertWorkspace(db, local, "Unbound local")
        db.openHelper.writableDatabase.execSQL("INSERT INTO sync_outbox (id,entity_type,entity_id,operation,payload_version,status,attempt_count,created_at,updated_at) VALUES ('local-op','WORKSPACE','$local','CREATE',1,'PENDING',0,1000,1000)")
        insertVerifiedDelivery(db, w1, a, "1")
        insertVerifiedDelivery(db, w2, a, "2")
        val storeA = sessionStore(a)
        val accountA = account(storeA)
        val workA = workspace(db, accountA)
        val choices = workA.availableWorkspaces()
        assertTrue(choices.toString(), choices is AppResult.Success)
        assertEquals(setOf(w1, w2), (choices as AppResult.Success).value.map { it.id }.toSet())
        assertTrue(workA.activeWorkspace() is AppResult.Failure)
        assertTrue(workA.selectWorkspace(w2) is AppResult.Success)
        assertEquals(w2, (workA.activeWorkspace() as AppResult.Success).value)
        assertTrue(workA.selectWorkspace(local) is AppResult.Failure)
        assertEquals(1, count(db, "SELECT count(*) FROM sync_outbox"))
        val farmsA = farms(db, workA)
        assertTrue(farmsA.create(NewFarm(local, "Forbidden")) is AppResult.Failure)
        assertTrue(farmsA.create(NewFarm(w2, "Allowed")) is AppResult.Success)
        assertEquals(1, count(db, "SELECT count(*) FROM farms WHERE workspace_id='$w2' AND name='Allowed'"))
        assertEquals(1, farmsA.observeActive(w2).first().count { it.name == "Allowed" })
        assertTrue(farmsA.observeActive(local).first().isEmpty())

        accountA.resume(false)
        assertEquals(w2, (workA.activeWorkspace() as AppResult.Success).value)
        db.close(); database = null
        val reopened = open()
        val workAfterRestart = workspace(reopened, account(storeA))
        assertEquals(w2, (workAfterRestart.activeWorkspace() as AppResult.Success).value)
        assertEquals(1, count(reopened, "SELECT count(*) FROM sync_outbox WHERE id='local-op' AND status='PENDING'"))

        val storeC = sessionStore(c)
        val workC = workspace(reopened, account(storeC))
        assertTrue(workC.activeWorkspace() is AppResult.Failure)
        assertTrue(workC.selectWorkspace(w1) is AppResult.Failure)
        assertTrue(farms(reopened, workC).create(NewFarm(w2, "Cross-account")) is AppResult.Failure)
        assertTrue(farms(reopened, workC).observeActive(w2).first().isEmpty())
        assertEquals(0, count(reopened, "SELECT count(*) FROM farms WHERE name='Cross-account'"))
        assertEquals(w2, (workAfterRestart.activeWorkspace() as AppResult.Success).value)

        now = now.plusSeconds(7200)
        assertTrue(workAfterRestart.activeWorkspace() is AppResult.Failure)
        now = now.minusSeconds(7200)
        val accountLoggedOut = account(storeA)
        accountLoggedOut.logout(false)
        val signedOut = workspace(reopened, accountLoggedOut)
        assertTrue("Logged out session on configured account must fail closed", signedOut.activeWorkspace() is AppResult.Failure)
        assertTrue("Logged out session must not ensure workspaces", signedOut.ensureLocalWorkspace() is AppResult.Failure)
        org.junit.Assert.assertNull(signedOut.observeLocalWorkspace().first())
        assertEquals(1, count(reopened, "SELECT count(*) FROM workspaces WHERE id='$local'"))
        assertEquals(1, count(reopened, "SELECT count(*) FROM workspaces WHERE id='$w2'"))
        assertEquals(1, count(reopened, "SELECT count(*) FROM sync_outbox WHERE id='local-op' AND status='PENDING'"))
    }

    @Test fun revokedAuthorizationCannotBeRestoredByHistoricReceipt() = runBlocking {
        val db = open()
        insertWorkspace(db, w1, "Shared")
        insertVerifiedDelivery(db, w1, a, "1")
        var allowed = setOf(w1)
        val identity = account(sessionStore(a))
        val work = workspace(db, identity) { allowed }
        val selection = work.selectWorkspace(w1)
        assertTrue(selection.toString(), selection is AppResult.Success)
        allowed = emptySet()
        assertTrue(work.refreshAuthorizations() is AppResult.Success)
        assertTrue(work.activeWorkspace() is AppResult.Failure)
        identity.resume(false)
        insertVerifiedDelivery(db, w1, c, "2")
        assertTrue(work.activeWorkspace() is AppResult.Failure)
        assertTrue(work.selectWorkspace(w2) is AppResult.Failure)
        assertEquals(0, count(db, "SELECT count(*) FROM sync_outbox"))
        allowed = setOf(w1)
        identity.resume(true)
        assertTrue(work.refreshAuthorizations() is AppResult.Success)
        assertEquals(w1, (work.activeWorkspace() as AppResult.Success).value)
    }

    @Test fun freshInstallProvisionsUnboundWorkspaceWithoutDuplicateOutbox() = runBlocking {
        val db = open()
        val unconfiguredAccount = AccountRepository(null)
        val work = workspace(db, unconfiguredAccount)

        assertEquals(0, count(db, "SELECT count(*) FROM workspaces"))
        assertEquals(0, count(db, "SELECT count(*) FROM sync_outbox"))
        assertTrue("activeWorkspace before ensure must be NotFound", work.activeWorkspace() is AppResult.Failure)

        val observed = work.observeActiveWorkspace().first()
        assertTrue("Observation must not provision a workspace: $observed", observed is AppResult.Failure)
        assertEquals(0, count(db, "SELECT count(*) FROM workspaces"))
        assertEquals(0, count(db, "SELECT count(*) FROM sync_outbox"))

        val ensured = work.ensureLocalWorkspace()
        assertTrue(ensured is AppResult.Success)
        val workspaceId = (ensured as AppResult.Success).value

        assertEquals(1, count(db, "SELECT count(*) FROM workspaces"))
        assertEquals(1, count(db, "SELECT count(*) FROM sync_outbox WHERE entity_type='WORKSPACE' AND operation='CREATE'"))

        // Ensuring again or observing again must reuse the same workspace without creating duplicate outbox entries
        val ensuredAgain = work.ensureLocalWorkspace()
        assertTrue(ensuredAgain is AppResult.Success)
        assertEquals(workspaceId, (ensuredAgain as AppResult.Success).value)

        val activeNow = work.activeWorkspace()
        assertTrue(activeNow is AppResult.Success)
        assertEquals(workspaceId, (activeNow as AppResult.Success).value)

        assertEquals(1, count(db, "SELECT count(*) FROM workspaces"))
        assertEquals(1, count(db, "SELECT count(*) FROM sync_outbox WHERE entity_type='WORKSPACE' AND operation='CREATE'"))
    }

    @Test fun observationAcrossAccountSwitchAndLogoutNeverCreatesWorkspace() = runBlocking {
        val db = open()
        val signedOut = workspace(db, account(MemoryAccountSessionStore()))
        assertTrue(signedOut.observeActiveWorkspace().first() is AppResult.Failure)
        val accountA = account(sessionStore(a))
        assertTrue(workspace(db, accountA).observeActiveWorkspace().first() is AppResult.Failure)
        val accountC = account(sessionStore(c))
        assertTrue(workspace(db, accountC).observeActiveWorkspace().first() is AppResult.Failure)
        accountC.logout(false)
        assertTrue(workspace(db, accountC).observeActiveWorkspace().first() is AppResult.Failure)
        assertEquals(0, count(db, "SELECT count(*) FROM workspaces"))
        assertEquals(0, count(db, "SELECT count(*) FROM sync_outbox"))
        assertEquals(0, count(db, "SELECT count(*) FROM workspace_selections"))
    }

    @Test fun securityBoundariesNeverAutoCreateWorkspaceOrGenerateOutbox() = runBlocking {
        val db = open()
        insertWorkspace(db, w1, "Workspace One")
        insertWorkspace(db, w2, "Workspace Two")

        // 1. Multiple workspaces without selection -> must NOT auto-create a third workspace
        val storeA = sessionStore(a)
        val accountA = account(storeA)
        val workA = workspace(db, accountA) { setOf(w1, w2) }

        val activeMulti = workA.activeWorkspace()
        assertTrue("Multiple workspaces without selection must require selection: $activeMulti", activeMulti is AppResult.Failure)
        val ensuredMulti = workA.ensureLocalWorkspace()
        assertTrue("ensureLocalWorkspace must NOT create workspace when multiple choices exist: $ensuredMulti", ensuredMulti is AppResult.Failure)
        assertEquals(2, count(db, "SELECT count(*) FROM workspaces"))
        assertEquals(0, count(db, "SELECT count(*) FROM sync_outbox"))

        // 2. Select w1 -> active is w1
        assertTrue(workA.selectWorkspace(w1) is AppResult.Success)
        assertEquals(w1, (workA.activeWorkspace() as AppResult.Success).value)

        // 3. Revoked authorization -> must fail closed and NOT auto-create a replacement workspace
        var allowed = emptySet<UUID>()
        val workRevoked = workspace(db, accountA) { allowed }
        assertTrue(workRevoked.refreshAuthorizations() is AppResult.Success)
        assertTrue("Revoked workspace must fail closed on activeWorkspace", workRevoked.activeWorkspace() is AppResult.Failure)
        assertTrue("Revoked workspace must fail closed on ensureLocalWorkspace without creating new workspace", workRevoked.ensureLocalWorkspace() is AppResult.Failure)
        assertEquals(2, count(db, "SELECT count(*) FROM workspaces"))
        assertEquals(0, count(db, "SELECT count(*) FROM sync_outbox"))

        // 4. Expired session -> must fail closed and NOT auto-create
        now = now.plusSeconds(7200)
        assertTrue("Expired session must fail closed on activeWorkspace", workRevoked.activeWorkspace() is AppResult.Failure)
        assertTrue("Expired session must fail closed on ensureLocalWorkspace", workRevoked.ensureLocalWorkspace() is AppResult.Failure)
        assertEquals(2, count(db, "SELECT count(*) FROM workspaces"))
        assertEquals(0, count(db, "SELECT count(*) FROM sync_outbox"))
        now = now.minusSeconds(7200)

        // 5. Logout -> must fail closed and NOT auto-create or expose previous data
        accountA.logout(false)
        val workLoggedOut = workspace(db, accountA)
        assertTrue("Logged out session must fail closed on activeWorkspace", workLoggedOut.activeWorkspace() is AppResult.Failure)
        assertTrue("Logged out session must fail closed on ensureLocalWorkspace", workLoggedOut.ensureLocalWorkspace() is AppResult.Failure)
        assertNull("Logged out session must emit null on observeLocalWorkspace", workLoggedOut.observeLocalWorkspace().first())
        assertEquals(2, count(db, "SELECT count(*) FROM workspaces"))
        assertEquals(0, count(db, "SELECT count(*) FROM sync_outbox"))
    }

    private fun open() = MaginaOlivoDatabase.create(context, name).also { database = it }
    private fun workspace(db: MaginaOlivoDatabase, account: AccountRepository, allowed: () -> Set<UUID> = { setOf(w1, w2) }) = LocalWorkspaceRepository(
        db, SystemAppClock(), UuidGenerator(), DefaultAppDispatchers(), RegionalContext.spainDefault(), account, issuer,
        WorkspaceAuthorizationRemote { session ->
            val grants = (if (session.userId == a) allowed() else emptySet()).joinToString(",") { id ->
                """{"id":"$id","name":"Workspace $id","role":"OWNER","permissions":["read","write","manage_members"],"grant_version":1,"authorization_expires_at":"${now.plusSeconds(900)}"}"""
            }
            """{"contract_version":1,"issuer":"$issuer/auth/v1","subject":"${session.userId}","verified_at":"$now","token_expires_at":"${now.plusSeconds(3600)}","workspaces":[$grants]}"""
        },
    )
    private fun farms(db: MaginaOlivoDatabase, work: LocalWorkspaceRepository) = OfflineFirstFarmRepository(
        db, SystemAppClock(), UuidGenerator(), DefaultAppDispatchers(), work,
    )
    private fun sessionStore(id: UUID) = MemoryAccountSessionStore().also {
        it.write(AccountRecord(session = AuthSession(id, "user@example.test", "access", "refresh", now.plusSeconds(3600))))
    }
    private fun account(store: MemoryAccountSessionStore) = AccountRepository(NoNetworkApi, store) { now }
    private fun count(db: MaginaOlivoDatabase, sql: String): Int = db.openHelper.writableDatabase.query(sql).use {
        it.moveToFirst(); it.getInt(0)
    }
    private fun insertWorkspace(db: MaginaOlivoDatabase, id: UUID, label: String) {
        db.openHelper.writableDatabase.execSQL("INSERT INTO workspaces (id,name,owner_user_id,country_code,timezone,locale,currency,created_at,updated_at,version,sync_status) VALUES ('$id','$label','99999999-9999-9999-9999-999999999999','ES','Europe/Madrid','es-ES','EUR',1000,1000,1,'PENDING')")
    }
    private fun insertVerifiedDelivery(db: MaginaOlivoDatabase, workspace: UUID, account: UUID, suffix: String) {
        val farm = "f0000000-0000-0000-0000-00000000000$suffix"
        val campaign = "c0000000-0000-0000-0000-00000000000$suffix"
        val delivery = "d0000000-0000-0000-0000-00000000000$suffix"
        db.openHelper.writableDatabase.execSQL("INSERT INTO farms (id,workspace_id,name,status,created_at,updated_at,version,sync_status) VALUES ('$farm','$workspace','Farm','ACTIVE',1000,1000,1,'SYNCED')")
        db.openHelper.writableDatabase.execSQL("INSERT INTO campaigns (id,workspace_id,farm_id,name,start_date,status,created_at,updated_at,version,sync_status) VALUES ('$campaign','$workspace','$farm','2025/2026','2025-10-01','PREPARATION',1000,1000,1,'SYNCED')")
        db.openHelper.writableDatabase.execSQL("INSERT INTO deliveries (id,workspace_id,farm_id,campaign_id,delivery_date,destination_name,net_grams,source,created_at,updated_at,version,sync_status) VALUES ('$delivery','$workspace','$farm','$campaign','2025-11-20','Cooperative',1000,'MANUAL',1000,1000,1,'SYNCED')")
        db.openHelper.writableDatabase.execSQL("INSERT INTO delivery_import_receipts (delivery_id,account_id,issuer,head_payload_hash,head_version,receive_contract_version,received_at) VALUES ('$delivery','$account','$issuer','hash',1,1,1000)")
    }

    private object NoNetworkApi : AccountAuthApi {
        override val redirectUri = "app://callback"
        override suspend fun signIn(email: String, password: String): AuthSession = error("unexpected network")
        override suspend fun signUp(email: String, password: String, challenge: String): AuthSession? = error("unexpected network")
        override suspend fun recover(email: String, challenge: String) = error("unexpected network")
        override suspend fun exchange(code: String, verifier: String): AuthSession = error("unexpected network")
        override suspend fun refresh(session: AuthSession): AuthSession = error("unexpected network")
        override suspend fun user(session: AuthSession): Pair<UUID, String> = session.userId to session.email
        override suspend fun changePassword(session: AuthSession, password: String) = error("unexpected network")
        override suspend fun signOut(session: AuthSession) = error("unexpected network")
    }
}
