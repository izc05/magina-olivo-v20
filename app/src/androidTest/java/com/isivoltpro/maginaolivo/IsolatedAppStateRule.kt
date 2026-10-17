package com.isivoltpro.maginaolivo

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.isivoltpro.maginaolivo.data.local.MaginaOlivoDatabase
import com.isivoltpro.maginaolivo.feature.account.AccountRuntime
import java.io.File
import java.util.UUID
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * Real MainActivity/repositories with a disposable DB; the original DB, preferences and private
 * no-backup state are restored. The account session and the farm-link ledger live in
 * noBackupFilesDir, outside both the database and the preferences, and the process keeps the
 * resumed identity in memory, so a test that moved neither would inherit the previous account and
 * its consents: no contamination between accounts or between runs.
 */
class IsolatedAppStateRule : TestRule {
    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val directory = File(context.applicationInfo.dataDir, "shared_prefs")
            fun names() = directory.listFiles().orEmpty().filter { it.name.endsWith(".xml") }.map { it.name.removeSuffix(".xml") }.toSet()
            val originals = names().associateWith { name -> context.getSharedPreferences(name, Context.MODE_PRIVATE).all.mapValues {
                if (it.value is Set<*>) (it.value as Set<*>).toSet() else it.value
            } }
            // Test-only binding of the existing singleton; no test API ships in production.
            val field = MaginaOlivoDatabase::class.java.getDeclaredField("instance").apply { isAccessible = true }
            val originalDatabase = field.get(null)
            val databaseName = "isolated-main-${UUID.randomUUID()}.db"
            val fixture = MaginaOlivoDatabase.create(context, databaseName)
            val noBackup = context.noBackupFilesDir
            val vault = File(context.cacheDir, "isolated-no-backup-${UUID.randomUUID()}")
            try {
                field.set(null, fixture)
                originals.keys.forEach { context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit() }
                moveAll(noBackup, vault)
                resetAccountRuntime()
                base.evaluate()
            } finally {
                field.set(null, originalDatabase)
                // Whatever the test wrote there is this run's, never the device's: drop it, then
                // put the original files back exactly where they were.
                val spent = File(context.cacheDir, "isolated-no-backup-spent-${UUID.randomUUID()}")
                moveAll(noBackup, spent)
                spent.deleteRecursively()
                moveAll(vault, noBackup)
                vault.deleteRecursively()
                resetAccountRuntime()
                fixture.close()
                (names() - originals.keys).forEach { context.deleteSharedPreferences(it) }
                originals.forEach { (name, values) ->
                    val editor = context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear()
                    values.forEach { (key, value) -> when (value) {
                        is String -> editor.putString(key, value)
                        is Boolean -> editor.putBoolean(key, value)
                        is Int -> editor.putInt(key, value)
                        is Long -> editor.putLong(key, value)
                        is Float -> editor.putFloat(key, value)
                        is Set<*> -> editor.putStringSet(key, value.map { it as String }.toSet())
                        null -> editor.remove(key)
                        else -> error("Unsupported preference type")
                    } }
                    check(editor.commit()) { "Original preferences must be restored" }
                }
                context.deleteDatabase(databaseName)
            }
        }
    }
}

/** Moves every entry of [from] into [to], creating [to]; neither directory itself is removed. */
private fun moveAll(from: File, to: File) {
    to.mkdirs()
    from.listFiles().orEmpty().forEach { entry ->
        val target = File(to, entry.name)
        target.deleteRecursively()
        if (!entry.renameTo(target)) {
            entry.copyRecursively(target, overwrite = true)
            entry.deleteRecursively()
        }
    }
}

/**
 * The process Auth owner caches the account record in memory, so clearing its file is not enough.
 * Dropping the singleton makes the next lookup read whatever file the test is isolated with.
 */
private fun resetAccountRuntime() {
    runCatching {
        AccountRuntime::class.java.getDeclaredField("repository").apply { isAccessible = true }
            .set(AccountRuntime, null)
    }
}
