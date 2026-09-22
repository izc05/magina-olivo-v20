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
                    id, workspace_id, name, description, municipality, province,
                    cover_document_id, notes, status, created_at, updated_at,
                    deleted_at, version, sync_status, remote_version, last_synced_at
                ) VALUES (
                    '33333333-3333-3333-3333-333333333333',
                    '11111111-1111-1111-1111-111111111111', 'La Solana', NULL, NULL,
                    NULL, NULL, NULL, 'ACTIVE', 1000, 1000, NULL, 1,
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

    @Test
    fun migration3To4PreservesActivitiesAndAddsParcelTargets() {
        migrationHelper.createDatabase(TEST_DATABASE, 3).use { database ->
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
                    id, workspace_id, name, description, municipality, province,
                    cover_document_id, notes, status, created_at, updated_at,
                    deleted_at, version, sync_status, remote_version, last_synced_at
                ) VALUES (
                    '33333333-3333-3333-3333-333333333333',
                    '11111111-1111-1111-1111-111111111111', 'La Solana', NULL, NULL,
                    NULL, NULL, NULL, 'ACTIVE', 1000, 1000, NULL, 1,
                    'LOCAL_ONLY', NULL, NULL
                )
                """.trimIndent(),
            )
            database.execSQL(
                """
                INSERT INTO activities (
                    id, workspace_id, campaign_id, farm_id, activity_date, type, status,
                    description, product, quantity, unit, cost_minor, currency, notes,
                    created_at, updated_at, deleted_at, version, sync_status,
                    remote_version, last_synced_at
                ) VALUES (
                    '55555555-5555-5555-5555-555555555555',
                    '11111111-1111-1111-1111-111111111111', NULL,
                    '33333333-3333-3333-3333-333333333333', '2026-01-15', 'PRUNING',
                    'PLANNED', 'Poda de formación', NULL, NULL, NULL, NULL, NULL, NULL,
                    1000, 1000, NULL, 1, 'LOCAL_ONLY', NULL, NULL
                )
                """.trimIndent(),
            )
        }

        migrationHelper
            .runMigrationsAndValidate(
                TEST_DATABASE,
                4,
                true,
                DatabaseMigrations.MIGRATION_3_4,
            ).use { database ->
                // The migration is purely additive: no Activity is rewritten or dropped.
                database
                    .query("SELECT description, status, farm_id FROM activities")
                    .use { cursor ->
                        assertTrue(cursor.moveToFirst())
                        assertEquals("Poda de formación", cursor.getString(0))
                        assertEquals("PLANNED", cursor.getString(1))
                        assertEquals("33333333-3333-3333-3333-333333333333", cursor.getString(2))
                        assertTrue(cursor.count == 1)
                    }

                val targetColumns = mutableSetOf<String>()
                database.query("PRAGMA table_info(activity_parcels)").use { cursor ->
                    val nameIndex = cursor.getColumnIndexOrThrow("name")
                    while (cursor.moveToNext()) targetColumns += cursor.getString(nameIndex)
                }
                assertTrue(
                    targetColumns.containsAll(
                        setOf(
                            "id",
                            "workspace_id",
                            "activity_id",
                            "parcel_id",
                            "parcel_name_at_target",
                            "area_affected_m2",
                            "notes",
                        ),
                    ),
                )

                // The unique index is what makes "one Activity, many Parcels" safe to
                // re-apply: targeting the same Parcel twice can never duplicate a row.
                val indices = mutableSetOf<String>()
                database
                    .query(
                        "SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = 'activity_parcels'",
                    ).use { cursor ->
                        while (cursor.moveToNext()) indices += cursor.getString(0)
                    }
                assertTrue(indices.contains("index_activity_parcels_activity_id_parcel_id"))
            }
    }

    @Test
    fun migration4To5KeepsTheActivityAggregateAndAddsTypedDetails() {
        migrationHelper.createDatabase(TEST_DATABASE, 4).use { database ->
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
                    id, workspace_id, name, description, municipality, province,
                    cover_document_id, notes, status, created_at, updated_at,
                    deleted_at, version, sync_status, remote_version, last_synced_at
                ) VALUES (
                    '33333333-3333-3333-3333-333333333333',
                    '11111111-1111-1111-1111-111111111111', 'La Solana', NULL, NULL,
                    NULL, NULL, NULL, 'ACTIVE', 1000, 1000, NULL, 1,
                    'LOCAL_ONLY', NULL, NULL
                )
                """.trimIndent(),
            )
            database.execSQL(
                """
                INSERT INTO parcels (
                    id, workspace_id, display_name, cadastral_reference, cadastral_polygon,
                    cadastral_parcel, municipality, province, source, geometry_geo_json,
                    cadastral_area_m2, managed_area_m2, notes, status, created_at,
                    updated_at, deleted_at, version, sync_status, remote_version,
                    last_synced_at
                ) VALUES (
                    '66666666-6666-6666-6666-666666666666',
                    '11111111-1111-1111-1111-111111111111', 'Parcela Alta', NULL, NULL,
                    NULL, NULL, NULL, 'MANUAL', NULL, NULL, 1200.0, NULL, 'ACTIVE',
                    1000, 1000, NULL, 1, 'LOCAL_ONLY', NULL, NULL
                )
                """.trimIndent(),
            )
            database.execSQL(
                """
                INSERT INTO activities (
                    id, workspace_id, campaign_id, farm_id, activity_date, type, status,
                    description, product, quantity, unit, cost_minor, currency, notes,
                    created_at, updated_at, deleted_at, version, sync_status,
                    remote_version, last_synced_at
                ) VALUES (
                    '55555555-5555-5555-5555-555555555555',
                    '11111111-1111-1111-1111-111111111111', NULL,
                    '33333333-3333-3333-3333-333333333333', '2026-01-15', 'PRUNING',
                    'PLANNED', 'Poda de formación', NULL, NULL, NULL, NULL, NULL, NULL,
                    1000, 1000, NULL, 1, 'LOCAL_ONLY', NULL, NULL
                )
                """.trimIndent(),
            )
            database.execSQL(
                """
                INSERT INTO activity_parcels (
                    id, workspace_id, activity_id, parcel_id, parcel_name_at_target,
                    area_affected_m2, notes, created_at, updated_at, deleted_at, version,
                    sync_status, remote_version, last_synced_at
                ) VALUES (
                    '77777777-7777-7777-7777-777777777777',
                    '11111111-1111-1111-1111-111111111111',
                    '55555555-5555-5555-5555-555555555555',
                    '66666666-6666-6666-6666-666666666666', 'Parcela Alta', 1200.0, NULL,
                    1000, 1000, NULL, 1, 'LOCAL_ONLY', NULL, NULL
                )
                """.trimIndent(),
            )
        }

        migrationHelper
            .runMigrationsAndValidate(
                TEST_DATABASE,
                5,
                true,
                DatabaseMigrations.MIGRATION_4_5,
            ).use { database ->
                // The Phase 9 aggregate survives untouched: header and Parcel relation.
                database
                    .query("SELECT description, status, type FROM activities")
                    .use { cursor ->
                        assertTrue(cursor.moveToFirst())
                        assertEquals("Poda de formación", cursor.getString(0))
                        assertEquals("PLANNED", cursor.getString(1))
                        assertEquals("PRUNING", cursor.getString(2))
                        assertTrue(cursor.count == 1)
                    }
                database
                    .query("SELECT parcel_name_at_target FROM activity_parcels")
                    .use { cursor ->
                        assertTrue(cursor.moveToFirst())
                        assertEquals("Parcela Alta", cursor.getString(0))
                        assertTrue(cursor.count == 1)
                    }

                // The eight new child tables exist and are empty: the migration adds
                // capacity, it never invents agronomic data for records made before it.
                val tables = mutableSetOf<String>()
                database
                    .query("SELECT name FROM sqlite_master WHERE type = 'table'")
                    .use { cursor ->
                        while (cursor.moveToNext()) tables += cursor.getString(0)
                    }
                assertTrue(
                    tables.containsAll(
                        setOf(
                            "pruning_details",
                            "fertilization_details",
                            "phytosanitary_details",
                            "soil_work_details",
                            "irrigation_details",
                            "irrigation_price_snapshots",
                            "maintenance_details",
                            "incident_details",
                        ),
                    ),
                )
                database.query("SELECT COUNT(*) FROM pruning_details").use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals(0, cursor.getInt(0))
                }

                val irrigationColumns = mutableSetOf<String>()
                database.query("PRAGMA table_info(irrigation_details)").use { cursor ->
                    val nameIndex = cursor.getColumnIndexOrThrow("name")
                    while (cursor.moveToNext()) irrigationColumns += cursor.getString(nameIndex)
                }
                assertTrue(
                    irrigationColumns.containsAll(
                        setOf("activity_id", "duration_minutes", "volume_m3", "sector_text", "system_text"),
                    ),
                )
            }
    }

    private companion object {
        const val TEST_DATABASE = "room-migration-test"
    }
}
