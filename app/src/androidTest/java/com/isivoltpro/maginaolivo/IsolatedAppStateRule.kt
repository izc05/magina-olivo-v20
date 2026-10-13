package com.isivoltpro.maginaolivo

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.isivoltpro.maginaolivo.data.local.MaginaOlivoDatabase
import java.io.File
import java.util.UUID
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/** Real MainActivity/repositories with a disposable DB; original DB and preferences are restored. */
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
            try {
                field.set(null, fixture)
                originals.keys.forEach { context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit() }
                base.evaluate()
            } finally {
                field.set(null, originalDatabase)
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
