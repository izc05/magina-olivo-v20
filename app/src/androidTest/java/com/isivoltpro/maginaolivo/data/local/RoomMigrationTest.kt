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

    @Test
    fun migration2To3PreservesCampaignAndAddsHistoricalParcelSnapshots() {
        migrationHelper.createDatabase(TEST_DATABASE, 2).use { database ->
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
            database.execSQL(
                """
                INSERT INTO farms (
                    id, workspace_id, name, cover_document_id, location_label, latitude,
                    longitude, managed_area_m2, notes, status, created_at, updated_at,
                    deleted_at, version, sync_status, remote_version, last_synced_at
                ) VALUES (
                    '33333333-3333-3333-3333-333333333333',
                    '11111111-1111-1111-1111-111111111111', 'La Solana', NULL, NULL,
                    NULL, NULL, NULL, NULL, 'ACTIVE', 1000, 1000, NULL, 1,
                    'LOCAL_ONLY', NULL, NULL
                )
                """.trimIndent(),
            )
            database.execSQL(
                """
                INSERT INTO campaigns (
                    id, workspace_id, farm_id, name, start_date, end_date, status, notes,
                    created_at, updated_at, deleted_at, version, sync_status,
                    remote_version, last_synced_at
                ) VALUES (
                    '44444444-4444-4444-4444-444444444444',
                    '11111111-1111-1111-1111-111111111111',
                    '33333333-3333-3333-3333-333333333333', '2026/27', '2026-10-01',
                    NULL, 'PLANNED', NULL, 1000, 1000, NULL, 1, 'LOCAL_ONLY', NULL, NULL
                )
                """.trimIndent(),
            )
        }

        migrationHelper
            .runMigrationsAndValidate(
                TEST_DATABASE,
                3,
                true,
                DatabaseMigrations.MIGRATION_2_3,
            ).use { database ->
                database.query("SELECT name, status FROM campaigns").use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("2026/27", cursor.getString(0))
                    assertEquals("PREPARATION", cursor.getString(1))
                }

                val snapshotColumns = mutableSetOf<String>()
                database.query("PRAGMA table_info(campaign_parcels)").use { cursor ->
                    val nameIndex = cursor.getColumnIndexOrThrow("name")
                    while (cursor.moveToNext()) snapshotColumns += cursor.getString(nameIndex)
                }
                assertTrue(
                    snapshotColumns.containsAll(
                        setOf(
                            "campaign_id",
                            "parcel_id",
                            "farm_id_at_start",
                            "farm_name_at_start",
                            "parcel_name_at_start",
                            "managed_area_m2_at_start",
                            "cadastral_reference_at_start",
                            "geometry_geo_json_snapshot",
                        ),
                    ),
                )
            }
    }

    private companion object {
        const val TEST_DATABASE = "room-migration-test"
    }
}
