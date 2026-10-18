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
}
