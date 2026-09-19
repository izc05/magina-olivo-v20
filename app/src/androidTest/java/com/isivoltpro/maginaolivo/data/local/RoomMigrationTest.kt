package com.isivoltpro.maginaolivo.data.local

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomMigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @get:Rule
    val migrationHelper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            MaginaOlivoDatabase::class.java,
            emptyList(),
            FrameworkSQLiteOpenHelperFactory(),
        )

    @After
    fun deleteDatabase() {
        context.deleteDatabase(TEST_DATABASE)
    }

    @Test
    fun migration1To2PreservesDataAndCreatesCoreTables() {
        migrationHelper.createDatabase(TEST_DATABASE, 1).use { database ->
            database.execSQL(
                """
                INSERT INTO workspaces (
                    id, name, owner_user_id, country_code, timezone, locale, currency,
                    created_at, updated_at, deleted_at, version, sync_status,
                    remote_version, last_synced_at
                ) VALUES (
                    '11111111-1111-1111-1111-111111111111', 'Mi olivar',
                    '22222222-2222-2222-2222-222222222222', 'ES', 'Europe/Madrid',
                    'es-ES', 'EUR', 1000, 1000, NULL, 1, 'LOCAL_ONLY', NULL, NULL
                )
                """.trimIndent(),
            )
        }

        migrationHelper
            .runMigrationsAndValidate(
                TEST_DATABASE,
                2,
                true,
                DatabaseMigrations.MIGRATION_1_2,
            ).use { database ->
                database.query("SELECT name FROM workspaces").use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("Mi olivar", cursor.getString(0))
                }

                val tables = mutableSetOf<String>()
                database
                    .query("SELECT name FROM sqlite_master WHERE type = 'table'")
                    .use { cursor ->
                        while (cursor.moveToNext()) {
                            tables += cursor.getString(0)
                        }
                    }

                assertTrue(
                    tables.containsAll(
                        setOf(
                            "workspaces",
                            "farms",
                            "sync_outbox",
                            "user_profiles",
                            "parcels",
                            "farm_parcel_memberships",
                            "campaigns",
                            "activities",
                            "harvests",
                            "expenses",
                            "documents",
                            "weather_cache",
                            "alerts",
                        ),
                    ),
                )
            }
    }

    private companion object {
        const val TEST_DATABASE = "room-migration-test"
    }
}
