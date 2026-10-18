package com.isivoltpro.maginaolivo

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.isivoltpro.maginaolivo.data.local.MaginaOlivoDatabase
import com.isivoltpro.maginaolivo.data.local.entity.*
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.Description
import org.junit.runners.model.Statement

class IsolatedAppStateRuleTest {
    @Test fun failureRestoresOriginalDatabaseAndAllPreferenceTypes() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = MaginaOlivoDatabase.getInstance(context)
        val count = database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM farms").use { it.moveToFirst(); it.getLong(0) }
        val preferenceName = "isolation-check-${UUID.randomUUID()}"
        val preferences = context.getSharedPreferences(preferenceName, Context.MODE_PRIVATE)
        check(preferences.edit().putString("value", "original").putInt("int", 7).putLong("long", 9)
            .putBoolean("boolean", true).putFloat("float", 1.25f).putStringSet("set", setOf("a", "b")).commit())
        val before = preferences.all
        class ExpectedFailure : RuntimeException()
        try {
            val result = runCatching {
                IsolatedAppStateRule().apply(object : Statement() {
                    override fun evaluate() {
                        val fixture = MaginaOlivoDatabase.getInstance(context)
                        assertNotSame(database, fixture)
                        assertTrue(preferences.all.isEmpty())
                        runBlocking {
                            val now = Instant.parse("2026-10-13T12:00:00Z")
                            val workspace = UUID.randomUUID()
                            val meta = LocalMetadata(now, now)
                            fixture.workspaceDao().upsert(WorkspaceEntity(workspace, "Fixture", UUID.randomUUID(), "ES", "Europe/Madrid", "es-ES", "EUR", meta))
                            fixture.farmDao().upsert(FarmEntity(UUID.randomUUID(), workspace, "Fixture", metadata = meta))
                        }
                        preferences.edit().putString("value", "fixture").commit()
                        throw ExpectedFailure()
                    }
                }, Description.createTestDescription(javaClass, "isolated fixture")).evaluate()
            }
            assertTrue(result.exceptionOrNull() is ExpectedFailure)
            assertSame(database, MaginaOlivoDatabase.getInstance(context))
            assertEquals(before, preferences.all)
            val after = database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM farms").use { it.moveToFirst(); it.getLong(0) }
            assertEquals(count, after)
        } finally { context.deleteSharedPreferences(preferenceName) }
    }

    /** #757: the account session and the farm-link ledger must not cross tests or accounts. */
    @Test fun theAccountSessionAndTheLinkLedgerAreIsolatedAndRestored() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val ledger = java.io.File(context.noBackupFilesDir, "farm-link-consents-v1.json")
        val session = java.io.File(context.noBackupFilesDir, "account-auth-isolationcheck.enc")
        val ledgerExisted = ledger.exists()
        val ledgerBefore = if (ledgerExisted) ledger.readBytes() else null
        check(!session.exists()) { "The synthetic session file must not already exist" }
        ledger.writeBytes("{\"device\":\"original\"}".toByteArray())
        session.writeBytes("original-identity".toByteArray())
        var leakedLedger: String? = null
        var leakedSession = true
        try {
            IsolatedAppStateRule().apply(object : Statement() {
                override fun evaluate() {
                    leakedLedger = ledger.takeIf { it.exists() }?.readText()
                    leakedSession = session.exists()
                    ledger.writeBytes("{\"device\":\"fixture\"}".toByteArray())
                    java.io.File(context.noBackupFilesDir, "account-auth-fixture.enc").writeBytes("fixture".toByteArray())
                }
            }, Description.createTestDescription(javaClass, "isolated no-backup state")).evaluate()
            assertNull("The previous ledger must not reach the test", leakedLedger)
            assertFalse("The previous session must not reach the test", leakedSession)
            assertFalse("A session written inside the test must not survive it",
                java.io.File(context.noBackupFilesDir, "account-auth-fixture.enc").exists())
            assertEquals("{\"device\":\"original\"}", ledger.readText())
            assertEquals("original-identity", session.readText())
        } finally {
            session.delete()
            if (ledgerBefore != null) ledger.writeBytes(ledgerBefore) else ledger.delete()
        }
    }
}
