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


    @Test
    fun migration5To6KeepsExistingExpensesAsPostedMoneyAndAddsTheLedgerTables() {
        migrationHelper.createDatabase(TEST_DATABASE, 5).use { database ->
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
                INSERT INTO expenses (
                    id, workspace_id, campaign_id, farm_id, parcel_id, expense_date, concept,
                    category, amount_minor, currency, provider, notes, created_at, updated_at,
                    deleted_at, version, sync_status, remote_version, last_synced_at
                ) VALUES (
                    '88888888-8888-8888-8888-888888888888',
                    '11111111-1111-1111-1111-111111111111', NULL, NULL, NULL, '2026-03-02',
                    'Gasóleo', 'FUEL', 9500, 'EUR', 'Estación Sur', NULL, 1000, 1000, NULL, 3,
                    'PENDING', NULL, NULL
                )
                """.trimIndent(),
            )
        }

        migrationHelper
            .runMigrationsAndValidate(
                TEST_DATABASE,
                6,
                true,
                DatabaseMigrations.MIGRATION_5_6,
            ).use { database ->
                // The only expenses that could exist before Phase 12 were typed by a person:
                // they stay counted money, unchanged, with their version and sync state.
                database
                    .query(
                        "SELECT concept, amount_minor, provider, status, origin, version, sync_status, activity_id FROM expenses",
                    ).use { cursor ->
                        assertTrue(cursor.moveToFirst())
                        assertEquals("Gasóleo", cursor.getString(0))
                        assertEquals(9500L, cursor.getLong(1))
                        assertEquals("Estación Sur", cursor.getString(2))
                        assertEquals("POSTED", cursor.getString(3))
                        assertEquals("MANUAL", cursor.getString(4))
                        assertEquals(3L, cursor.getLong(5))
                        assertEquals("PENDING", cursor.getString(6))
                        assertTrue(cursor.isNull(7))
                        assertEquals(1, cursor.count)
                    }

                val tables = mutableSetOf<String>()
                database
                    .query("SELECT name FROM sqlite_master WHERE type = 'table'")
                    .use { cursor ->
                        while (cursor.moveToNext()) tables += cursor.getString(0)
                    }
                assertTrue(
                    tables.containsAll(
                        setOf(
                            "agricultural_organizations",
                            "organization_roles",
                            "purchases",
                            "purchase_items",
                            "document_ocr_extractions",
                        ),
                    ),
                )
                assertTrue("expenses_new" !in tables)
                database.query("SELECT COUNT(*) FROM purchases").use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals(0, cursor.getInt(0))
                }
            }
    }

    @Test
    fun migration6To7KeepsExistingHarvestsAndAddsTheirOriginParcels() {
        migrationHelper.createDatabase(TEST_DATABASE, 6).use { database ->
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
                INSERT INTO harvests (
                    id, workspace_id, campaign_id, farm_id, harvest_date, weight_grams,
                    destination, notes, created_at, updated_at, deleted_at, version,
                    sync_status, remote_version, last_synced_at
                ) VALUES (
                    '99999999-9999-9999-9999-999999999999',
                    '11111111-1111-1111-1111-111111111111', NULL, NULL, '2026-11-18',
                    2850000, NULL, 'Primer día', 1000, 1000, NULL, 2, 'PENDING', NULL, NULL
                )
                """.trimIndent(),
            )
        }

        migrationHelper
            .runMigrationsAndValidate(
                TEST_DATABASE,
                7,
                true,
                DatabaseMigrations.MIGRATION_6_7,
            ).use { database ->
                // A harvest recorded before Phase 13 keeps its weight, version and sync
                // state; its new collection fields are unknown, not invented.
                database
                    .query(
                        "SELECT weight_grams, notes, version, sync_status, collection_method, worker_count, machinery_text FROM harvests",
                    ).use { cursor ->
                        assertTrue(cursor.moveToFirst())
                        assertEquals(2_850_000L, cursor.getLong(0))
                        assertEquals("Primer día", cursor.getString(1))
                        assertEquals(2L, cursor.getLong(2))
                        assertEquals("PENDING", cursor.getString(3))
                        assertTrue(cursor.isNull(4))
                        assertTrue(cursor.isNull(5))
                        assertTrue(cursor.isNull(6))
                        assertEquals(1, cursor.count)
                    }
                // No origin Parcel is guessed for it.
                database.query("SELECT COUNT(*) FROM harvest_parcels").use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals(0, cursor.getInt(0))
                }
            }
    }

    @Test
    fun migration7To8AddsEmptyDeliveryTablesAndKeepsHarvests() {
        migrationHelper.createDatabase(TEST_DATABASE, 7).use { database ->
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
                INSERT INTO harvests (
                    id, workspace_id, campaign_id, farm_id, harvest_date, weight_grams,
                    destination, notes, created_at, updated_at, deleted_at, version,
                    sync_status, remote_version, last_synced_at, collection_method,
                    worker_count, machinery_text
                ) VALUES (
                    '99999999-9999-9999-9999-999999999999',
                    '11111111-1111-1111-1111-111111111111', NULL, NULL, '2026-11-18',
                    2850000, NULL, NULL, 1000, 1000, NULL, 1, 'PENDING', NULL, NULL,
                    'TRUNK_SHAKER', 4, NULL
                )
                """.trimIndent(),
            )
        }

        migrationHelper
            .runMigrationsAndValidate(
                TEST_DATABASE,
                8,
                true,
                DatabaseMigrations.MIGRATION_7_8,
            ).use { database ->
                database.query("SELECT weight_grams, collection_method, worker_count FROM harvests").use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals(2_850_000L, cursor.getLong(0))
                    assertEquals("TRUNK_SHAKER", cursor.getString(1))
                    assertEquals(4, cursor.getInt(2))
                }
                listOf("deliveries", "delivery_parcels", "delivery_yield_analyses").forEach { table ->
                    database.query("SELECT COUNT(*) FROM $table").use { cursor ->
                        assertTrue(cursor.moveToFirst())
                        assertEquals(0, cursor.getInt(0))
                    }
                }
            }
    }

    @Test
    fun migration8To9AddsEmptyMachineryTables() {
        migrationHelper.createDatabase(TEST_DATABASE, 8).use { database ->
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
                9,
                true,
                DatabaseMigrations.MIGRATION_8_9,
            ).use { database ->
                listOf("machines", "activity_machines").forEach { table ->
                    database.query("SELECT COUNT(*) FROM $table").use { cursor ->
                        assertTrue(cursor.moveToFirst())
                        assertEquals(0, cursor.getInt(0))
                    }
                }
                database.query("SELECT COUNT(*) FROM workspaces").use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals(1, cursor.getInt(0))
                }
            }
    }

    @Test
    fun migration9To10AddsEmptyPlanningAndReminderTables() {
        migrationHelper.createDatabase(TEST_DATABASE, 9).use { database ->
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
                10,
                true,
                DatabaseMigrations.MIGRATION_9_10,
            ).use { database ->
                listOf("activity_planning_details", "reminders").forEach { table ->
                    database.query("SELECT COUNT(*) FROM $table").use { cursor ->
                        assertTrue(cursor.moveToFirst())
                        assertEquals(0, cursor.getInt(0))
                    }
                }
                database.query("SELECT COUNT(*) FROM workspaces").use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals(1, cursor.getInt(0))
                }
            }
    }

    private companion object {
        const val TEST_DATABASE = "room-migration-test"
    }
}
