package com.isivoltpro.maginaolivo.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.isivoltpro.maginaolivo.data.repository.LocalFarmLinkRepository
import com.isivoltpro.maginaolivo.feature.account.AccountAuthApi
import com.isivoltpro.maginaolivo.feature.account.AccountRepository
import com.isivoltpro.maginaolivo.feature.account.MemoryAccountSessionStore
import com.isivoltpro.maginaolivo.feature.account.SimulatedAccountAuthApi
import com.isivoltpro.maginaolivo.data.repository.AndroidFarmLinkLedger
import java.io.File
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Integration #796 + #808, owner decision D1 (option A): which farms an account may consent to link.
 * Own workspaces and G1b legacy (unbound local) workspaces are offered; another account's workspace never is; a shared
 * workspace only with this account's VALID remote authorization carrying `write`; linking never changes any owner.
 */
class FarmLinkChoicesRoomTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "qa-d1-${UUID.randomUUID()}.db"
    private val database = MaginaOlivoDatabase.create(context, name)
    private val ledgerFile = File(context.noBackupFilesDir, "qa-d1-${UUID.randomUUID()}.json")
    private val store = AndroidFarmLinkLedger(ledgerFile)
    private val api = object : AccountAuthApi by SimulatedAccountAuthApi() { override val simulated = false }
    private val account = AccountRepository(api, MemoryAccountSessionStore())
    private val issuer = "qa-issuer"
    private val sql get() = database.openHelper.writableDatabase

    @After fun cleanup() {
        runCatching { store.read()?.consents?.forEach { File(context.noBackupFilesDir, "account-link-backups/${it.backupId}").deleteRecursively() } }
        database.close(); context.deleteDatabase(name)
        listOf(ledgerFile, File(ledgerFile.path + ".bak"), File(ledgerFile.path + ".new")).forEach { it.delete() }
    }

    private fun repo() = LocalFarmLinkRepository(context, database, account, issuer, store)

    private fun workspace(owner: UUID, status: String): UUID = UUID.randomUUID().also {
        sql.execSQL("INSERT INTO workspaces (id,name,owner_user_id,country_code,timezone,locale,currency,created_at,updated_at,version,sync_status) VALUES (?,'QA',?,'ES','Europe/Madrid','es-ES','EUR',1,1,1,?)",
            arrayOf<Any>(it.toString(), owner.toString(), status))
    }

    private fun farm(workspace: UUID, label: String): UUID = UUID.randomUUID().also {
        sql.execSQL("INSERT INTO farms (id,workspace_id,name,status,created_at,updated_at,version,sync_status) VALUES (?,?,?,'ACTIVE',1,1,1,'LOCAL_ONLY')",
            arrayOf<Any>(it.toString(), workspace.toString(), label))
    }

    private fun select(accountId: String, workspace: UUID) =
        sql.execSQL("INSERT OR REPLACE INTO workspace_selections(issuer,account_id,workspace_id) VALUES (?,?,?)", arrayOf<Any>(issuer, accountId, workspace.toString()))

    private fun authorize(accountId: UUID, workspace: UUID, permissions: String, verifiedAt: Instant = Instant.now(), contractIssuer: String = "$issuer/auth/v1") {
        val grant = """{"id":"$workspace","name":"Compartido","permissions":[$permissions],"authorization_expires_at":"${Instant.now().plusSeconds(3600)}","grant_version":1,"role":"EDITOR"}"""
        val body = """{"contract_version":1,"issuer":"$contractIssuer","subject":"$accountId","verified_at":"$verifiedAt","token_expires_at":"${Instant.now().plusSeconds(3600)}","workspaces":[$grant]}"""
        sql.execSQL("INSERT OR REPLACE INTO workspace_authorization_cache(issuer,account_id,contract_json) VALUES (?,?,?)", arrayOf<Any>(issuer, accountId.toString(), body))
    }

    private fun owners() = sql.query("SELECT id, owner_user_id FROM workspaces ORDER BY id").use { c ->
        buildList { while (c.moveToNext()) add(c.getString(0) to c.getString(1)) }
    }

    @Test fun anotherAccountsWorkspaceIsNeverOfferedWhileOwnAndLegacyAre() = runBlocking<Unit> {
        // Account A created its own workspace on this device (bound to A by its selection) and never consented.
        account.login("a@example.test", "password42", true)
        val a = account.state.value.userId!!
        val wa = workspace(a, "PENDING"); select(a.toString(), wa); val fa = farm(wa, "Finca de A")
        // A legacy workspace from before any account (random local owner, only the signed-out selection).
        val wl = workspace(UUID.randomUUID(), "LOCAL_ONLY"); select("", wl); val fl = farm(wl, "Finca legacy")
        account.logout(false)
        account.login("b@example.test", "password42", true)
        val b = account.state.value.userId!!
        val wb = workspace(b, "PENDING"); select(b.toString(), wb); val fb = farm(wb, "Finca de B")
        val offered = repo().choices().map { it.id }.toSet()
        assertEquals(setOf(fb, fl), offered)
        assertFalse("B never sees A's farm", fa in offered)
    }

    @Test fun aSharedWorkspaceNeedsThisAccountsValidWriteAuthorization() = runBlocking<Unit> {
        account.login("member@example.test", "password42", true)
        val member = account.state.value.userId!!
        val shared = workspace(UUID.randomUUID(), "SYNCED"); val fs = farm(shared, "Finca compartida")
        assertTrue("no authorization: not offered", repo().choices().none { it.id == fs })
        authorize(member, shared, "\"read\"")
        assertTrue("read only: not offered", repo().choices().none { it.id == fs })
        authorize(member, shared, "\"read\",\"write\"", verifiedAt = Instant.now().minusSeconds(3600))
        assertTrue("stale evidence: not offered", repo().choices().none { it.id == fs })
        authorize(member, shared, "\"read\",\"write\"", contractIssuer = "https://otro.example/auth/v1")
        assertTrue("foreign issuer: not offered", repo().choices().none { it.id == fs })
        authorize(UUID.randomUUID(), shared, "\"read\",\"write\"")
        assertTrue("another account's evidence: not offered", repo().choices().none { it.id == fs })
        authorize(member, shared, "\"read\",\"write\"")
        assertEquals(listOf(fs), repo().choices().map { it.id })
    }

    @Test fun linkingNeverChangesAnyOwnerSoACollaboratorGainsNoOwnership() = runBlocking<Unit> {
        account.login("member@example.test", "password42", true)
        val member = account.state.value.userId!!
        val shared = workspace(UUID.randomUUID(), "SYNCED"); val fs = farm(shared, "Finca compartida")
        val legacy = workspace(UUID.randomUUID(), "LOCAL_ONLY"); val fl = farm(legacy, "Finca legacy")
        authorize(member, shared, "\"read\",\"write\"")
        val before = owners()
        val repository = repo()
        val choices = repository.choices()
        assertEquals(setOf(fs, fl), choices.map { it.id }.toSet())
        val ledger = repository.consent(member, choices, true)
        assertEquals(setOf(fs, fl), ledger.farmIds)
        assertEquals(before, owners())
    }
}
