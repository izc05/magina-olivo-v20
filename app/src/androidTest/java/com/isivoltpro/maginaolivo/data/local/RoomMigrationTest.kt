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
    fun databaseVersionMatchesLatestExportedSchema() {
        assertEquals(36, MaginaOlivoDatabase.VERSION)
    }

    @Test
    fun migration34To35AddsEmptyCycleSidecarsWithoutRewritingAnyHistoricalFactOrOutbox() {
        val before = migrationHelper.createDatabase(TEST_DATABASE,34).use { database ->
            listOf(
                "INSERT INTO workspaces(id,name,owner_user_id,country_code,timezone,locale,currency,created_at,updated_at,version,sync_status) VALUES ('w','QA','owner','ES','Europe/Madrid','es-ES','EUR',1,2,3,'PENDING')",
                "INSERT INTO farms(id,workspace_id,name,status,created_at,updated_at,version,sync_status) VALUES ('f','w','Histórica','ACTIVE',1,2,4,'SYNCED')",
                "INSERT INTO campaigns(id,workspace_id,farm_id,name,start_date,end_date,status,created_at,updated_at,version,sync_status) VALUES ('c','w','f','2020/21','2020-02-01','2021-01-20','CLOSED',1,2,5,'PENDING')",
                "INSERT INTO activities(id,workspace_id,farm_id,activity_date,type,status,description,created_at,updated_at,version,sync_status) VALUES ('a','w','f','2020-02-15','PRUNING','COMPLETED','Poda histórica',1,2,6,'PENDING')",
                "INSERT INTO expenses(id,workspace_id,farm_id,expense_date,concept,category,amount_minor,currency,status,origin,created_at,updated_at,version,sync_status) VALUES ('e','w','f','2021-05-01','Factura tardía','OTHER',12345,'EUR','POSTED','MANUAL',1,2,7,'PENDING')",
                "INSERT INTO sync_outbox(id,entity_type,entity_id,operation,payload_version,payload_json,status,attempt_count,created_at,updated_at) VALUES ('o','ACTIVITY','a','CREATE',1,'{\"unchanged\":true}','PENDING',0,1,2)",
                "INSERT INTO sync_receive_receipts(issuer,account_id,entity_type,entity_id,remote_version,deleted,contract_version,received_at) VALUES ('iss','actor','CAMPAIGN','c',5,0,1,2)",
            ).forEach(database::execSQL)
            dumpTables(database)
        }
        val sidecars=setOf("agricultural_cycles","agricultural_cycle_farm_state","agricultural_cycle_activities",
            "agricultural_cycle_expenses","agricultural_cycle_recollections","agricultural_cycle_operations")
        migrationHelper.runMigrationsAndValidate(TEST_DATABASE,35,true,AGRICULTURAL_CYCLE_MIGRATION_34_35).use { database ->
            assertEquals(before,dumpTables(database,exclude=sidecars))
            sidecars.forEach { table -> database.query("SELECT COUNT(*) FROM $table").use { c ->
                assertTrue(c.moveToFirst()); assertEquals(table,0,c.getInt(0))
            } }
            database.query("PRAGMA foreign_key_check").use { c -> assertTrue(!c.moveToFirst()) }
        }
    }

    /** CR-015 P4: 35 -> 36 adds one empty transport-state table; P3 cycles, links and operations stay identical. */
    @Test
    fun migration35To36AddsEmptyCycleSyncStateWithoutRewritingP3Cycles() {
        val before = migrationHelper.createDatabase(TEST_DATABASE,35).use { database ->
            listOf(
                "INSERT INTO workspaces(id,name,owner_user_id,country_code,timezone,locale,currency,created_at,updated_at,version,sync_status) VALUES ('w','QA','owner','ES','Europe/Madrid','es-ES','EUR',1,2,3,'PENDING')",
                "INSERT INTO farms(id,workspace_id,name,status,created_at,updated_at,version,sync_status) VALUES ('f','w','Histórica','ACTIVE',1,2,4,'SYNCED')",
                "INSERT INTO campaigns(id,workspace_id,farm_id,name,start_date,end_date,status,created_at,updated_at,version,sync_status) VALUES ('c','w','f','2025/26','2025-11-01','2026-01-20','CLOSED',1,2,5,'PENDING')",
                "INSERT INTO agricultural_cycles(id,workspace_id,farm_id,label,start_date,effective_end_date,closed_at,no_recollection,version) VALUES ('y','w','f','2025/26','2025-02-01','2026-01-25',1800000000000,0,2)",
                "INSERT INTO agricultural_cycle_recollections(campaign_id,cycle_id) VALUES ('c','y')",
                "INSERT INTO agricultural_cycle_farm_state(farm_id,revision) VALUES ('f',3)",
                "INSERT INTO agricultural_cycle_operations(id,cycle_id,owner_id,fingerprint,recorded_at,reason) VALUES ('op','y',NULL,'hash',1800000000000,'Cerrar: 2026-01-25')",
            ).forEach(database::execSQL)
            dumpTables(database)
        }
        migrationHelper.runMigrationsAndValidate(TEST_DATABASE,36,true,AGRICULTURAL_CYCLE_MIGRATION_35_36).use { database ->
            assertEquals(before,dumpTables(database,exclude=setOf("agricultural_cycle_sync_state")))
            database.query("SELECT COUNT(*) FROM agricultural_cycle_sync_state").use { c ->
                assertTrue(c.moveToFirst()); assertEquals(0,c.getInt(0))
            }
            database.query("PRAGMA foreign_key_check").use { c -> assertTrue(!c.moveToFirst()) }
        }
    }

    /**
     * #808 R3-a: 33 -> 34 only adds two empty tables. Every pre-existing table is dumped row by row before and after
     * (workspace, farm, campaign, AUTO_DAY and MANUAL Jornadas with versions, a deleted Pesada and its parcels, outbox
     * operations in every state, an unsettled PREPARE, a held receipt, a G2.5 import receipt and a campaign
     * reconciliation): the dumps are identical, and the schema validates against 34.json.
     */
    @Test
    fun migration33To34AddsEmptyReceiveTablesAndPreservesEveryRowVersionReceiptOutboxAndPrepare() {
        val a64 = "a".repeat(64)
        val b64 = "b".repeat(64)
        val c64 = "c".repeat(64)
        val before = migrationHelper.createDatabase(TEST_DATABASE, 33).use { database ->
            listOf(
                "INSERT INTO workspaces(id,name,owner_user_id,country_code,timezone,locale,currency,created_at,updated_at,version,sync_status,remote_version) VALUES ('w','QA','owner','ES','Europe/Madrid','es-ES','EUR',1000,1000,2,'SYNCED',2)",
                "INSERT INTO farms(id,workspace_id,name,status,created_at,updated_at,version,sync_status,remote_version) VALUES ('f','w','Farm','ACTIVE',1000,1000,1,'SYNCED',1)",
                "INSERT INTO campaigns(id,workspace_id,farm_id,name,start_date,status,created_at,updated_at,version,sync_status,remote_version) VALUES ('c','w','f','2026/27','2026-10-01','HARVEST',1000,1000,1,'SYNCED',1)",
                "INSERT INTO harvests(id,workspace_id,campaign_id,farm_id,harvest_date,weight_grams,day_origin,created_at,updated_at,version,sync_status,remote_version) VALUES ('h-auto','w','c','f','2026-11-18',1400000,'AUTO_DAY',1000,2000,3,'PENDING',1)",
                "INSERT INTO harvests(id,workspace_id,campaign_id,farm_id,harvest_date,weight_grams,day_origin,notes,created_at,updated_at,version,sync_status,remote_version) VALUES ('h-manual','w','c','f','2026-11-19',4242000,'MANUAL','typed',1000,1000,1,'SYNCED',1)",
                "INSERT INTO deliveries(id,workspace_id,farm_id,campaign_id,delivery_date,destination_name,net_grams,source,harvest_id,created_at,updated_at,version,sync_status,remote_version) VALUES ('d-live','w','f','c','2026-11-18','Coop',1400000,'MANUAL','h-auto',1000,1000,1,'SYNCED',1)",
                "INSERT INTO deliveries(id,workspace_id,farm_id,campaign_id,delivery_date,destination_name,net_grams,source,harvest_id,created_at,updated_at,deleted_at,version,sync_status,remote_version) VALUES ('d-gone','w','f','c','2026-11-18','Coop',600000,'MANUAL','h-auto',1000,2000,2000,2,'PENDING',1)",
                "INSERT INTO parcels(id,workspace_id,display_name,source,status,created_at,updated_at,version,sync_status,remote_version) VALUES ('p','w','Norte','MANUAL','ACTIVE',1000,1000,1,'SYNCED',1)",
                "INSERT INTO delivery_parcels(id,workspace_id,delivery_id,parcel_id,parcel_name_at_delivery,weight_grams,allocation_mode,created_at,updated_at,version,sync_status) VALUES ('dp','w','d-gone','p','Norte',600000,'EXACT',1000,1000,1,'SYNCED')",
                "INSERT INTO sync_outbox(id,entity_type,entity_id,operation,payload_version,status,attempt_count,account_id,payload_json,farm_id,created_at,updated_at) VALUES ('op-pending','DELIVERY','d-gone','DELETE',3,'PENDING',0,'a','{\"kind\":\"DELETE\"}','f',1000,1000)",
                "INSERT INTO sync_outbox(id,entity_type,entity_id,operation,payload_version,status,attempt_count,last_error_code,account_id,payload_json,farm_id,created_at,updated_at) VALUES ('op-processing','DELIVERY','d-live','UPDATE',2,'PROCESSING',1,'lease:1','a','{}','f',1000,1000)",
                "INSERT INTO sync_outbox(id,entity_type,entity_id,operation,payload_version,status,attempt_count,last_error_code,created_at,updated_at) VALUES ('op-unknown','HARVEST','h-auto','UPDATE',1,'FAILED',2,'OUTCOME_UNKNOWN',1000,1000)",
                "INSERT INTO sync_outbox(id,entity_type,entity_id,operation,payload_version,status,attempt_count,last_error_code,last_error_message,created_at,updated_at) VALUES ('op-held','DELIVERY','d-live','CREATE',3,'BLOCKED',1,'V3_ACK_AWAITING_HEAD','HEAD_SUPERSEDED',1000,1000)",
                "INSERT INTO sync_outbox(id,entity_type,entity_id,operation,payload_version,status,attempt_count,created_at,updated_at) VALUES ('op-done','HARVEST','h-manual','CREATE',1,'CONFIRMED',1,1000,1000)",
                "INSERT INTO delivery_dispatch_prepare(operation_id,delivery_id,farm_id,account_id,issuer,subject,local_workspace_id,consent_operation_id,consent_text_version,payload_version,payload_hash,lease_marker,prepared_at) VALUES ('op-processing','d-live','f','a','iss','iss|a','w','consent',2,2,'$a64','lease:1',1000)",
                "INSERT INTO delivery_held_receipts(operation_id,delivery_id,account_id,farm_id,remote_version,payload_hash,payload_version,revocation_operation_id,state,received_at) VALUES ('op-old','d-live','a','f',1,'$b64',2,'rev','HELD',1000)",
                "INSERT INTO delivery_import_receipts(delivery_id,account_id,issuer,head_payload_hash,head_version,receive_contract_version,received_at) VALUES ('d-imported','a','iss','$c64',1,1,1000)",
                "INSERT INTO campaign_reconciliations(original_operation_id,issuer,account_id,workspace_id,farm_id,campaign_id,reason,local_payload_json,remote_payload_json,remote_version,captured_at) VALUES ('rec','iss','a','w','f','c','CAS_CONFLICT','{}','{}',2,1000)",
            ).forEach(database::execSQL)
            dumpTables(database)
        }
        migrationHelper.runMigrationsAndValidate(TEST_DATABASE, 34, true, DatabaseMigrations.MIGRATION_33_34).use { database ->
            assertEquals(before, dumpTables(database, exclude = setOf("sync_receive_receipts", "sync_receive_holds")))
            for (table in listOf("sync_receive_receipts", "sync_receive_holds")) {
                database.query("SELECT COUNT(*) FROM $table").use { c -> assertTrue(c.moveToFirst()); assertEquals(0, c.getInt(0)) }
            }
        }
    }

    /** Every user table, every row (by rowid), every column, NULL kept distinct from text. */
    private fun dumpTables(database: androidx.sqlite.db.SupportSQLiteDatabase, exclude: Set<String> = emptySet()): String {
        val tables = database.query(
            "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' " +
                "AND name NOT IN ('room_master_table','android_metadata') ORDER BY name",
        ).use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }.filterNot { it in exclude }
        return tables.joinToString("\n") { table ->
            database.query("SELECT * FROM $table ORDER BY rowid").use { c ->
                val rows = buildList {
                    while (c.moveToNext()) add((0 until c.columnCount).joinToString("|") { i -> if (c.isNull(i)) "<NULL>" else c.getString(i) })
                }
                "$table(${c.columnNames.joinToString(",")}):${rows.joinToString(";")}"
            }
        }
    }

    /** Every table with its columns and rows (each row as column -> value), to compare a source version with the target. */
    private fun snapshot(database: androidx.sqlite.db.SupportSQLiteDatabase): Map<String, Pair<List<String>, List<Map<String, String?>>>> {
        val tables = database.query(
            "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' " +
                "AND name NOT IN ('room_master_table','android_metadata') ORDER BY name",
        ).use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }
        return tables.associateWith { table ->
            database.query("SELECT * FROM $table").use { c ->
                val columns = c.columnNames.toList()
                columns to buildList { while (c.moveToNext()) add(columns.indices.associate { i -> columns[i] to (if (c.isNull(i)) null else c.getString(i)) }) }
            }
        }
    }

    /**
     * Integration #796 + #808: every table of [before] still exists after migrating, with exactly the same rows over its
     * ORIGINAL columns (order-independent); new columns and new tables may only be added.
     */
    private fun assertPreserved(before: Map<String, Pair<List<String>, List<Map<String, String?>>>>,
        after: Map<String, Pair<List<String>, List<Map<String, String?>>>>) {
        for ((table, source) in before) {
            val (columns, rows) = source
            val target = after[table] ?: error("table $table disappeared")
            assertTrue("$table lost columns: ${columns - target.first.toSet()}", target.first.containsAll(columns))
            fun key(row: Map<String, String?>) = columns.joinToString("|") { row[it] ?: "<NULL>" }
            assertEquals("rows of $table", rows.map(::key).sorted(), target.second.map(::key).sorted())
        }
    }

    private fun count(database: androidx.sqlite.db.SupportSQLiteDatabase, table: String) =
        database.query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); it.getInt(0) }

    /**
     * Integration #796 + #808: a device still on schema 26 (the one of `main`) with a representative field dataset migrates
     * through EVERY step to 34 (the chain's), with the app's own migration list: nothing is lost or altered and the
     * receive tables arrive empty.
     */
    @Test
    fun chain26To34PreservesARepresentativeFieldDataset() {
        val before = migrationHelper.createDatabase(TEST_DATABASE, 26).use { database ->
            database.execSQL("INSERT INTO workspaces (id, name, owner_user_id, country_code, timezone, locale, currency, created_at, updated_at, version, sync_status) VALUES ('w','Olivar Bedmar','owner','ES','Europe/Madrid','es-ES','EUR',1000,1000,1,'LOCAL_ONLY')")
            database.execSQL("INSERT INTO farms (id, workspace_id, name, description, municipality, province, status, created_at, updated_at, version, sync_status) VALUES ('f','w','Finca Los Olivos','Olivos centenarios','Bedmar y Garcíez','Jaén','ACTIVE',1000,1000,1,'LOCAL_ONLY')")
            database.execSQL("INSERT INTO parcels (id, workspace_id, display_name, cadastral_reference, managed_area_m2, source, status, created_at, updated_at, version, sync_status) VALUES ('p','w','Parcela El Cerrón','23902A001000010000AB',15000.0,'MANUAL','ACTIVE',1000,1000,1,'LOCAL_ONLY')")
            database.execSQL("INSERT INTO campaigns (id, workspace_id, farm_id, name, start_date, status, created_at, updated_at, version, sync_status) VALUES ('c','w','f','2025/2026','2025-10-01','HARVEST',1000,1000,1,'LOCAL_ONLY')")
            database.execSQL("INSERT INTO harvests (id, workspace_id, campaign_id, farm_id, harvest_date, weight_grams, created_at, updated_at, version, sync_status) VALUES ('h','w','c','f','2025-11-20',4242000,1000,1000,1,'LOCAL_ONLY')")
            database.execSQL("INSERT INTO deliveries (id, workspace_id, farm_id, campaign_id, delivery_date, destination_name, net_grams, ticket_number, source, harvest_id, created_at, updated_at, version, sync_status) VALUES ('d','w','f','c','2025-11-20','S.C.A. Bedmarense',3500000,'T-101','MANUAL','h',1000,1000,2,'SYNCED')")
            database.execSQL("INSERT INTO harvest_labour (id, workspace_id, harvest_id, quantity, unit, created_at, updated_at, version, sync_status) VALUES ('l','w','h',4,'DAY',1000,1000,1,'LOCAL_ONLY')")
            database.execSQL("INSERT INTO expenses (id, workspace_id, campaign_id, farm_id, expense_date, concept, category, amount_minor, currency, status, origin, created_at, updated_at, version, sync_status) VALUES ('e','w','c','f','2025-11-20','Gasóleo tractor','FUEL',8500,'EUR','POSTED','MANUAL',1000,1000,1,'LOCAL_ONLY')")
            database.execSQL("INSERT INTO documents (id, workspace_id, owner_type, owner_id, type, mime_type, display_name, local_uri, upload_status, created_at, updated_at, version, sync_status) VALUES ('doc','w','FARM','f','PHOTO','image/jpeg','Foto Olivar','file:///photos/olivar.jpg','UPLOADED',1000,1000,1,'LOCAL_ONLY')")
            database.execSQL("INSERT INTO sync_outbox (id, entity_type, entity_id, operation, payload_version, base_remote_version, status, attempt_count, next_attempt_at, last_error_code, last_error_message, created_at, updated_at) VALUES ('op-1','DELIVERY','d','UPDATE',1,1,'PENDING',0,NULL,NULL,NULL,1000,1000)")
            snapshot(database)
        }
        migrationHelper.runMigrationsAndValidate(TEST_DATABASE, 34, true, *DatabaseMigrations.all).use { database ->
            assertPreserved(before, snapshot(database))
            assertEquals(0, count(database, "sync_receive_receipts")); assertEquals(0, count(database, "sync_receive_holds"))
            database.query("SELECT weight_grams FROM harvests WHERE id='h'").use { assertTrue(it.moveToFirst()); assertEquals(4242000L, it.getLong(0)) }
        }
    }

    /**
     * Integration #796 + #808: a device on schema 32 (the one of #796) with a representative dataset, including a blocked
     * campaign CAS operation, a workspace selection and its authorization cache, migrates 32 -> 33 -> 34 with nothing lost.
     */
    @Test
    fun chain32To34PreservesARepresentativeDatasetWithSelectionAndAuthorization() {
        val before = migrationHelper.createDatabase(TEST_DATABASE, 32).use { database ->
            database.execSQL("INSERT INTO workspaces(id,name,owner_user_id,country_code,timezone,locale,currency,created_at,updated_at,version,sync_status) VALUES ('w','QA','owner','ES','Europe/Madrid','es-ES','EUR',1000,1000,1,'SYNCED')")
            database.execSQL("INSERT INTO farms(id,workspace_id,name,status,created_at,updated_at,version,sync_status) VALUES ('f','w','Farm','ACTIVE',1000,1000,1,'SYNCED')")
            database.execSQL("INSERT INTO parcels (id, workspace_id, display_name, source, status, created_at, updated_at, version, sync_status) VALUES ('p','w','Parcela','MANUAL','ACTIVE',1000,1000,1,'SYNCED')")
            database.execSQL("INSERT INTO campaigns(id,workspace_id,farm_id,name,start_date,status,created_at,updated_at,version,sync_status) VALUES ('c','w','f','Edited','2026-01-01','HARVEST',1000,1000,2,'CONFLICT')")
            database.execSQL("INSERT INTO harvests (id, workspace_id, campaign_id, farm_id, harvest_date, weight_grams, created_at, updated_at, version, sync_status) VALUES ('h','w','c','f','2026-01-20',4242000,1000,1000,1,'PENDING')")
            database.execSQL("INSERT INTO deliveries (id, workspace_id, farm_id, campaign_id, delivery_date, destination_name, net_grams, source, harvest_id, created_at, updated_at, version, sync_status) VALUES ('d','w','f','c','2026-01-20','Coop',1500000,'MANUAL','h',1000,1000,1,'PENDING')")
            database.execSQL("INSERT INTO sync_outbox(id,entity_type,entity_id,operation,payload_version,status,attempt_count,last_error_code,created_at,updated_at) VALUES ('op','CAMPAIGN','c','UPDATE',1,'BLOCKED',1,'CAS_CONFLICT',1000,1000)")
            database.execSQL("INSERT INTO sync_outbox(id,entity_type,entity_id,operation,payload_version,status,attempt_count,created_at,updated_at) VALUES ('op-d','DELIVERY','d','CREATE',2,'PENDING',0,1000,1000)")
            database.execSQL("INSERT INTO workspace_selections(issuer,account_id,workspace_id) VALUES ('issuer','a','w')")
            database.execSQL("INSERT INTO workspace_authorization_cache(issuer,account_id,contract_json) VALUES ('issuer','a','{}')")
            snapshot(database)
        }
        migrationHelper.runMigrationsAndValidate(TEST_DATABASE, 34, true, DatabaseMigrations.MIGRATION_32_33, DatabaseMigrations.MIGRATION_33_34).use { database ->
            assertPreserved(before, snapshot(database))
            assertEquals(0, count(database, "sync_receive_receipts")); assertEquals(0, count(database, "sync_receive_holds"))
            assertEquals(0, count(database, "campaign_reconciliations"))
        }
    }

    @Test
    fun migration32To33PreservesCampaignOutboxAndAuthorization() {
        migrationHelper.createDatabase(TEST_DATABASE, 32).use { database ->
            database.execSQL("INSERT INTO workspaces(id,name,owner_user_id,country_code,timezone,locale,currency,created_at,updated_at,version,sync_status) VALUES ('w','QA','owner','ES','Europe/Madrid','es-ES','EUR',1000,1000,1,'SYNCED')")
            database.execSQL("INSERT INTO farms(id,workspace_id,name,status,created_at,updated_at,version,sync_status) VALUES ('f','w','Farm','ACTIVE',1000,1000,1,'SYNCED')")
            database.execSQL("INSERT INTO campaigns(id,workspace_id,farm_id,name,start_date,status,created_at,updated_at,version,sync_status) VALUES ('c','w','f','Edited','2026-01-01','PREPARATION',1000,1000,2,'CONFLICT')")
            database.execSQL("INSERT INTO sync_outbox(id,entity_type,entity_id,operation,payload_version,status,attempt_count,last_error_code,created_at,updated_at) VALUES ('op','CAMPAIGN','c','UPDATE',1,'BLOCKED',1,'CAS_CONFLICT',1000,1000)")
            database.execSQL("INSERT INTO workspace_selections(issuer,account_id,workspace_id) VALUES ('issuer','a','w')")
            database.execSQL("INSERT INTO workspace_authorization_cache(issuer,account_id,contract_json) VALUES ('issuer','a','{}')")
        }
        migrationHelper.runMigrationsAndValidate(TEST_DATABASE,33,true,DatabaseMigrations.MIGRATION_32_33).use { database ->
            database.query("SELECT name,sync_status FROM campaigns WHERE id='c'").use { c ->
                assertTrue(c.moveToFirst()); assertEquals("Edited",c.getString(0)); assertEquals("CONFLICT",c.getString(1))
            }
            database.query("SELECT status,last_error_code FROM sync_outbox WHERE id='op'").use { c ->
                assertTrue(c.moveToFirst()); assertEquals("BLOCKED",c.getString(0)); assertEquals("CAS_CONFLICT",c.getString(1))
            }
            database.query("SELECT COUNT(*) FROM campaign_reconciliations").use { c ->
                assertTrue(c.moveToFirst()); assertEquals(0,c.getInt(0))
            }
            database.query("SELECT COUNT(*) FROM workspace_authorization_cache").use { c ->
                assertTrue(c.moveToFirst()); assertEquals(1,c.getInt(0))
            }
        }
    }

    @Test
    fun migration31To32AddsEmptyAuthorizationCacheAndPreservesSelection() {
        migrationHelper.createDatabase(TEST_DATABASE, 31).use { database ->
            database.execSQL("INSERT INTO workspace_selections (issuer,account_id,workspace_id) VALUES ('https://project.example','aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa','11111111-1111-1111-1111-111111111111')")
        }
        migrationHelper.runMigrationsAndValidate(TEST_DATABASE, 32, true, DatabaseMigrations.MIGRATION_31_32).use { database ->
            database.query("SELECT workspace_id FROM workspace_selections").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("11111111-1111-1111-1111-111111111111", cursor.getString(0))
            }
            database.query("SELECT COUNT(*) FROM workspace_authorization_cache").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
        }
    }

    @Test
    fun migration30To31AddsEmptySelectionWithoutChangingExistingWorkspaceOrOutbox() {
        migrationHelper.createDatabase(TEST_DATABASE, 30).use { database ->
            database.execSQL("INSERT INTO workspaces (id,name,owner_user_id,country_code,timezone,locale,currency,created_at,updated_at,version,sync_status) VALUES ('w','Unbound','generated-owner','ES','Europe/Madrid','es-ES','EUR',1000,1000,1,'PENDING')")
            database.execSQL("INSERT INTO sync_outbox (id,entity_type,entity_id,operation,payload_version,status,attempt_count,created_at,updated_at) VALUES ('op','WORKSPACE','w','CREATE',1,'PENDING',0,1000,1000)")
        }
        migrationHelper.runMigrationsAndValidate(TEST_DATABASE, 31, true, DatabaseMigrations.MIGRATION_30_31).use { database ->
            database.query("SELECT name,owner_user_id,sync_status FROM workspaces WHERE id='w'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Unbound", cursor.getString(0))
                assertEquals("generated-owner", cursor.getString(1))
                assertEquals("PENDING", cursor.getString(2))
            }
            database.query("SELECT status FROM sync_outbox WHERE id='op'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("PENDING", cursor.getString(0))
            }
            database.query("SELECT COUNT(*) FROM workspace_selections").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
        }
    }

    @Test
    fun migration29To30AddsDeliveryImportReceiptsTableAndKeepsExistingDataIntact() {
        migrationHelper.createDatabase(TEST_DATABASE, 29).use { database ->
            database.execSQL("INSERT INTO workspaces (id, name, owner_user_id, country_code, timezone, locale, currency, created_at, updated_at, version, sync_status) VALUES ('w','Olivar Bedmar','owner','ES','Europe/Madrid','es-ES','EUR',1000,1000,1,'LOCAL_ONLY')")
            database.execSQL("INSERT INTO farms (id, workspace_id, name, description, municipality, province, status, created_at, updated_at, version, sync_status) VALUES ('f','w','Finca Los Olivos',NULL,NULL,NULL,'ACTIVE',1000,1000,1,'LOCAL_ONLY')")
            database.execSQL("INSERT INTO campaigns (id, workspace_id, farm_id, name, start_date, status, created_at, updated_at, version, sync_status) VALUES ('c','w','f','2025/2026','2025-10-01','PREPARATION',1000,1000,1,'LOCAL_ONLY')")
            database.execSQL("INSERT INTO deliveries (id, workspace_id, farm_id, campaign_id, delivery_date, destination_name, net_grams, ticket_number, source, created_at, updated_at, version, sync_status, remote_version) VALUES ('d','w','f','c','2025-11-20','S.C.A. Bedmarense',3500000,'T-101','MANUAL',1000,1000,2,'PENDING',4)")
            database.execSQL("INSERT INTO delivery_dispatch_prepare (operation_id, delivery_id, farm_id, account_id, issuer, subject, local_workspace_id, remote_workspace_id, consent_operation_id, consent_text_version, payload_version, payload_hash, base_remote_version, lease_marker, prepared_at, settled_at, settled_outcome) VALUES ('op-prep','d','f','account-a','https://example.com','sub','w',NULL,'consent-op',2,2,'hash123',NULL,'lease',1000,1100,'CONFIRMED')")
        }
        migrationHelper.runMigrationsAndValidate(TEST_DATABASE, 30, true, DatabaseMigrations.MIGRATION_29_30).use { database ->
            database.query("SELECT net_grams, version, sync_status, remote_version FROM deliveries WHERE id='d'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(3500000L, cursor.getLong(0))
                assertEquals(2L, cursor.getLong(1))
                assertEquals("PENDING", cursor.getString(2))
                assertEquals(4L, cursor.getLong(3))
            }
            database.query("SELECT operation_id, settled_outcome FROM delivery_dispatch_prepare WHERE delivery_id='d'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("op-prep", cursor.getString(0))
                assertEquals("CONFIRMED", cursor.getString(1))
            }
            database.query("SELECT COUNT(*) FROM delivery_import_receipts").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
            database.execSQL("INSERT INTO delivery_import_receipts (delivery_id, account_id, issuer, head_payload_hash, head_version, receive_contract_version, received_at) VALUES ('d','account-a','https://issuer.com','abc123hash',1,1,2000)")
            database.query("SELECT account_id, issuer, head_payload_hash, head_version, receive_contract_version, received_at FROM delivery_import_receipts WHERE delivery_id='d'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("account-a", cursor.getString(0))
                assertEquals("https://issuer.com", cursor.getString(1))
                assertEquals("abc123hash", cursor.getString(2))
                assertEquals(1L, cursor.getLong(3))
                assertEquals(1, cursor.getInt(4))
                assertEquals(2000L, cursor.getLong(5))
            }
        }
    }

    @Test
    fun migration28Through30PreservesAgriculturalRowsOutboxAndReceipts() {
        migrationHelper.createDatabase(TEST_DATABASE, 28).use { database ->
            database.execSQL("INSERT INTO workspaces (id,name,owner_user_id,country_code,timezone,locale,currency,created_at,updated_at,version,sync_status) VALUES ('w','Olivar','owner','ES','Europe/Madrid','es-ES','EUR',1000,1000,3,'PENDING')")
            database.execSQL("INSERT INTO farms (id,workspace_id,name,status,created_at,updated_at,version,sync_status) VALUES ('f','w','Finca','ACTIVE',1000,1000,2,'PENDING')")
            database.execSQL("INSERT INTO campaigns (id,workspace_id,farm_id,name,start_date,status,created_at,updated_at,version,sync_status) VALUES ('c','w','f','2025/2026','2025-10-01','HARVEST',1000,1000,3,'PENDING')")
            database.execSQL("INSERT INTO deliveries (id,workspace_id,farm_id,campaign_id,delivery_date,destination_name,net_grams,source,created_at,updated_at,version,sync_status,remote_version) VALUES ('d','w','f','c','2025-11-20','Cooperativa',1540000,'MANUAL',1000,1000,2,'SYNCED',1)")
            database.execSQL("INSERT INTO sync_outbox (id,entity_type,entity_id,operation,payload_version,status,attempt_count,created_at,updated_at,account_id,payload_json,farm_id) VALUES ('op','CAMPAIGN','c','CREATE',1,'PENDING',0,1000,1000,'account-a','{\"name\":\"2025/2026\"}','f')")
            database.execSQL("INSERT INTO delivery_held_receipts (operation_id,delivery_id,account_id,farm_id,remote_version,payload_hash,payload_version,revocation_operation_id,state,received_at) VALUES ('held','d','account-a','f',2,'${"a".repeat(64)}',2,'rev','HELD',2000)")
        }
        migrationHelper.runMigrationsAndValidate(TEST_DATABASE, 29, true, DatabaseMigrations.MIGRATION_28_29).use { database ->
            database.execSQL("INSERT INTO delivery_dispatch_prepare (operation_id,delivery_id,farm_id,account_id,issuer,subject,local_workspace_id,consent_operation_id,consent_text_version,payload_version,payload_hash,lease_marker,prepared_at) VALUES ('prep','d','f','account-a','https://example.com','sub','w','consent',1,2,'hash','lease',3000)")
        }
        migrationHelper.runMigrationsAndValidate(TEST_DATABASE, 30, true, DatabaseMigrations.MIGRATION_29_30).use { database ->
            val expected = mapOf(
                "workspaces" to "SELECT name || ':' || version || ':' || sync_status FROM workspaces WHERE id='w'" to "Olivar:3:PENDING",
                "farms" to "SELECT name || ':' || version || ':' || sync_status FROM farms WHERE id='f'" to "Finca:2:PENDING",
                "campaigns" to "SELECT status || ':' || version || ':' || sync_status FROM campaigns WHERE id='c'" to "HARVEST:3:PENDING",
                "deliveries" to "SELECT net_grams || ':' || remote_version || ':' || sync_status FROM deliveries WHERE id='d'" to "1540000:1:SYNCED",
                "sync_outbox" to "SELECT status || ':' || account_id || ':' || payload_json FROM sync_outbox WHERE id='op'" to "PENDING:account-a:{\"name\":\"2025/2026\"}",
                "delivery_held_receipts" to "SELECT state || ':' || remote_version FROM delivery_held_receipts WHERE operation_id='held'" to "HELD:2",
                "delivery_dispatch_prepare" to "SELECT lease_marker || ':' || payload_hash FROM delivery_dispatch_prepare WHERE operation_id='prep'" to "lease:hash",
            )
            expected.forEach { (tableAndQuery, value) ->
                val (table, query) = tableAndQuery
                database.query(query).use { cursor ->
                    assertTrue("Missing row in $table", cursor.moveToFirst())
                    assertEquals("Changed row in $table", value, cursor.getString(0))
                    assertTrue("Duplicate row in $table", !cursor.moveToNext())
                }
            }
            database.query("SELECT COUNT(*) FROM delivery_import_receipts").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
        }
    }

    @Test
    fun migration28To29AddsTheDurablePrepareTableAndKeepsHeldReceiptsAndIntentsIntact() {
        migrationHelper.createDatabase(TEST_DATABASE, 28).use { database ->
            database.execSQL("INSERT INTO workspaces (id, name, owner_user_id, country_code, timezone, locale, currency, created_at, updated_at, version, sync_status) VALUES ('w','Olivar Bedmar','owner','ES','Europe/Madrid','es-ES','EUR',1000,1000,1,'LOCAL_ONLY')")
            database.execSQL("INSERT INTO farms (id, workspace_id, name, description, municipality, province, status, created_at, updated_at, version, sync_status) VALUES ('f','w','Finca Los Olivos',NULL,NULL,NULL,'ACTIVE',1000,1000,1,'LOCAL_ONLY')")
            database.execSQL("INSERT INTO campaigns (id, workspace_id, farm_id, name, start_date, status, created_at, updated_at, version, sync_status) VALUES ('c','w','f','2025/2026','2025-10-01','PREPARATION',1000,1000,1,'LOCAL_ONLY')")
            database.execSQL("INSERT INTO deliveries (id, workspace_id, farm_id, campaign_id, delivery_date, destination_name, net_grams, ticket_number, source, created_at, updated_at, version, sync_status, remote_version) VALUES ('d','w','f','c','2025-11-20','S.C.A. Bedmarense',3500000,'T-101','MANUAL',1000,1000,2,'PENDING',4)")
            database.execSQL("INSERT INTO sync_outbox (id, entity_type, entity_id, operation, payload_version, base_remote_version, status, attempt_count, next_attempt_at, last_error_code, last_error_message, created_at, updated_at, account_id, payload_json, farm_id) VALUES ('op-1','DELIVERY','d','UPDATE',2,4,'BLOCKED',1,NULL,'REVOKED_ACK_HELD',NULL,1000,1000,'account-a','{\"netGrams\":3500000}','f')")
            database.execSQL("INSERT INTO delivery_held_receipts (operation_id, delivery_id, account_id, farm_id, remote_version, payload_hash, payload_version, revocation_operation_id, state, conflict_remote_version, conflict_payload_hash, received_at, resolved_at) VALUES ('op-1','d','account-a','f',7,'${"a".repeat(64)}',2,'rev-1','HELD',NULL,NULL,2000,NULL)")
        }
        migrationHelper.runMigrationsAndValidate(TEST_DATABASE, 29, true, DatabaseMigrations.MIGRATION_28_29).use { database ->
            database.query("SELECT net_grams, version, sync_status, remote_version FROM deliveries WHERE id='d'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(3500000L, cursor.getLong(0))
                assertEquals(2L, cursor.getLong(1))
                assertEquals("PENDING", cursor.getString(2))
                assertEquals(4L, cursor.getLong(3))
            }
            database.query("SELECT status, last_error_code, account_id, payload_json, farm_id, base_remote_version FROM sync_outbox WHERE id='op-1'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("BLOCKED", cursor.getString(0))
                assertEquals("REVOKED_ACK_HELD", cursor.getString(1))
                assertEquals("account-a", cursor.getString(2))
                assertEquals("{\"netGrams\":3500000}", cursor.getString(3))
                assertEquals("f", cursor.getString(4))
                assertEquals(4L, cursor.getLong(5))
            }
            database.query("SELECT state, remote_version, payload_hash FROM delivery_held_receipts WHERE operation_id='op-1'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("The held receipt survives untouched", "HELD", cursor.getString(0))
                assertEquals(7L, cursor.getLong(1))
                assertEquals("a".repeat(64), cursor.getString(2))
            }
            database.query("SELECT COUNT(*) FROM delivery_dispatch_prepare").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("No PREPARE is invented for intents enqueued before G2.4", 0, cursor.getInt(0))
            }
        }
    }

    @Test
    fun migration27To28IsAdditiveAndKeepsBlockedDeliveryIntentsIntact() {
        migrationHelper.createDatabase(TEST_DATABASE, 27).use { database ->
            database.execSQL("INSERT INTO workspaces (id, name, owner_user_id, country_code, timezone, locale, currency, created_at, updated_at, version, sync_status) VALUES ('w','Olivar Bedmar','owner','ES','Europe/Madrid','es-ES','EUR',1000,1000,1,'LOCAL_ONLY')")
            database.execSQL("INSERT INTO farms (id, workspace_id, name, description, municipality, province, status, created_at, updated_at, version, sync_status) VALUES ('f','w','Finca Los Olivos','Olivos centenarios','Bedmar y Garcíez','Jaén','ACTIVE',1000,1000,1,'LOCAL_ONLY')")
            database.execSQL("INSERT INTO campaigns (id, workspace_id, farm_id, name, start_date, status, created_at, updated_at, version, sync_status) VALUES ('c','w','f','2025/2026','2025-10-01','PREPARATION',1000,1000,1,'LOCAL_ONLY')")
            database.execSQL("INSERT INTO deliveries (id, workspace_id, farm_id, campaign_id, delivery_date, destination_name, net_grams, ticket_number, source, created_at, updated_at, version, sync_status, remote_version) VALUES ('d','w','f','c','2025-11-20','S.C.A. Bedmarense',3500000,'T-101','MANUAL',1000,1000,2,'PENDING',4)")
            database.execSQL("INSERT INTO sync_outbox (id, entity_type, entity_id, operation, payload_version, base_remote_version, status, attempt_count, next_attempt_at, last_error_code, last_error_message, created_at, updated_at, account_id, payload_json, farm_id) VALUES ('op-1','DELIVERY','d','UPDATE',2,4,'BLOCKED',1,NULL,'DELIVERY_LEASE_V2:x:y',NULL,1000,1000,'account-a','{\"netGrams\":3500000}','f')")
        }
        migrationHelper.runMigrationsAndValidate(TEST_DATABASE, 28, true, DatabaseMigrations.MIGRATION_27_28).use { database ->
            database.query("SELECT net_grams, version, sync_status, remote_version FROM deliveries WHERE id='d'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(3500000L, cursor.getLong(0))
                assertEquals(2L, cursor.getLong(1))
                assertEquals("PENDING", cursor.getString(2))
                assertEquals(4L, cursor.getLong(3))
            }
            database.query("SELECT status, attempt_count, base_remote_version, last_error_code, account_id, payload_json, farm_id FROM sync_outbox WHERE id='op-1'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("BLOCKED", cursor.getString(0))
                assertEquals(1, cursor.getInt(1))
                assertEquals(4L, cursor.getLong(2))
                assertEquals("DELIVERY_LEASE_V2:x:y", cursor.getString(3))
                assertEquals("account-a", cursor.getString(4))
                assertEquals("{\"netGrams\":3500000}", cursor.getString(5))
                assertEquals("f", cursor.getString(6))
            }
            database.query("SELECT COUNT(*) FROM delivery_held_receipts").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("No receipt is invented for existing blocked intents", 0, cursor.getInt(0))
            }
        }
    }

    @Test
    fun migration26To27PreservesFarmsDeliveriesAndExtendsSyncOutbox() {
        migrationHelper.createDatabase(TEST_DATABASE, 26).use { database ->
            database.execSQL("INSERT INTO workspaces (id, name, owner_user_id, country_code, timezone, locale, currency, created_at, updated_at, version, sync_status) VALUES ('w','Olivar Bedmar','owner','ES','Europe/Madrid','es-ES','EUR',1000,1000,1,'LOCAL_ONLY')")
            database.execSQL("INSERT INTO farms (id, workspace_id, name, description, municipality, province, status, created_at, updated_at, version, sync_status) VALUES ('f','w','Finca Los Olivos','Olivos centenarios','Bedmar y Garcíez','Jaén','ACTIVE',1000,1000,1,'LOCAL_ONLY')")
            database.execSQL("INSERT INTO campaigns (id, workspace_id, farm_id, name, start_date, status, created_at, updated_at, version, sync_status) VALUES ('c','w','f','2025/2026','2025-10-01','PREPARATION',1000,1000,1,'LOCAL_ONLY')")
            database.execSQL("INSERT INTO deliveries (id, workspace_id, farm_id, campaign_id, delivery_date, destination_name, net_grams, ticket_number, source, created_at, updated_at, version, sync_status) VALUES ('d','w','f','c','2025-11-20','S.C.A. Bedmarense',3500000,'T-101','MANUAL',1000,1000,1,'LOCAL_ONLY')")
            database.execSQL("INSERT INTO sync_outbox (id, entity_type, entity_id, operation, payload_version, base_remote_version, status, attempt_count, next_attempt_at, last_error_code, last_error_message, created_at, updated_at) VALUES ('op-1','DELIVERY','d','CREATE',1,NULL,'PENDING',0,NULL,NULL,NULL,1000,1000)")
        }
        migrationHelper.runMigrationsAndValidate(TEST_DATABASE, 27, true, DatabaseMigrations.MIGRATION_26_27).use { database ->
            database.query("SELECT name, municipality FROM farms WHERE id='f'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Finca Los Olivos", cursor.getString(0))
                assertEquals("Bedmar y Garcíez", cursor.getString(1))
            }
            database.query("SELECT destination_name, net_grams FROM deliveries WHERE id='d'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("S.C.A. Bedmarense", cursor.getString(0))
                assertEquals(3500000L, cursor.getLong(1))
            }
            database.query("SELECT id, entity_type, operation, status, account_id, payload_json, farm_id FROM sync_outbox WHERE id='op-1'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("op-1", cursor.getString(0))
                assertEquals("DELIVERY", cursor.getString(1))
                assertEquals("CREATE", cursor.getString(2))
                assertEquals("PENDING", cursor.getString(3))
                assertTrue(cursor.isNull(4)) // account_id defaults to null on existing rows
                assertTrue(cursor.isNull(5)) // payload_json defaults to null on existing rows
                assertTrue(cursor.isNull(6)) // farm_id defaults to null on existing rows
            }
        }
    }

    @Test
    fun migration25To26PreservesFarmsParcelsCampaignsDeliveriesLabourExpensesAndDocuments() {
        migrationHelper.createDatabase(TEST_DATABASE, 25).use { database ->
            database.execSQL("INSERT INTO workspaces (id, name, owner_user_id, country_code, timezone, locale, currency, created_at, updated_at, version, sync_status) VALUES ('w','Olivar Bedmar','owner','ES','Europe/Madrid','es-ES','EUR',1000,1000,1,'LOCAL_ONLY')")
            database.execSQL("INSERT INTO farms (id, workspace_id, name, description, municipality, province, status, created_at, updated_at, version, sync_status) VALUES ('f','w','Finca Los Olivos','Olivos centenarios','Bedmar y Garcíez','Jaén','ACTIVE',1000,1000,1,'LOCAL_ONLY')")
            database.execSQL("INSERT INTO parcels (id, workspace_id, display_name, cadastral_reference, managed_area_m2, source, status, created_at, updated_at, version, sync_status) VALUES ('p','w','Parcela El Cerrón','23902A001000010000AB',15000.0,'MANUAL','ACTIVE',1000,1000,1,'LOCAL_ONLY')")
            database.execSQL("INSERT INTO campaigns (id, workspace_id, farm_id, name, start_date, status, created_at, updated_at, version, sync_status) VALUES ('c','w','f','2025/2026','2025-10-01','PREPARATION',1000,1000,1,'LOCAL_ONLY')")
            database.execSQL("INSERT INTO deliveries (id, workspace_id, farm_id, campaign_id, delivery_date, destination_name, net_grams, ticket_number, source, created_at, updated_at, version, sync_status) VALUES ('d','w','f','c','2025-11-20','S.C.A. Bedmarense',3500000,'T-101','MANUAL',1000,1000,1,'LOCAL_ONLY')")
            database.execSQL("INSERT INTO harvests (id, workspace_id, campaign_id, farm_id, harvest_date, weight_grams, created_at, updated_at, version, sync_status) VALUES ('h','w','c','f','2025-11-20',3500000,1000,1000,1,'LOCAL_ONLY')")
            database.execSQL("INSERT INTO harvest_labour (id, workspace_id, harvest_id, quantity, unit, created_at, updated_at, version, sync_status) VALUES ('l','w','h',4,'DAY',1000,1000,1,'LOCAL_ONLY')")
            database.execSQL("INSERT INTO expenses (id, workspace_id, campaign_id, farm_id, expense_date, concept, category, amount_minor, currency, status, origin, created_at, updated_at, version, sync_status) VALUES ('e','w','c','f','2025-11-20','Gasóleo tractor','FUEL',8500,'EUR','POSTED','MANUAL',1000,1000,1,'LOCAL_ONLY')")
            database.execSQL("INSERT INTO documents (id, workspace_id, owner_type, owner_id, type, mime_type, display_name, local_uri, upload_status, created_at, updated_at, version, sync_status) VALUES ('doc','w','FARM','f','PHOTO','image/jpeg','Foto Olivar','file:///photos/olivar.jpg','UPLOADED',1000,1000,1,'LOCAL_ONLY')")
        }
        migrationHelper.runMigrationsAndValidate(TEST_DATABASE, 26, true, DatabaseMigrations.MIGRATION_25_26).use { database ->
            database.query("SELECT name, municipality FROM farms WHERE id='f'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Finca Los Olivos", cursor.getString(0))
                assertEquals("Bedmar y Garcíez", cursor.getString(1))
            }
            database.query("SELECT display_name, cadastral_reference, managed_area_m2 FROM parcels WHERE id='p'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Parcela El Cerrón", cursor.getString(0))
                assertEquals("23902A001000010000AB", cursor.getString(1))
                assertEquals(15000.0, cursor.getDouble(2), 0.0)
            }
            database.query("SELECT name, status FROM campaigns WHERE id='c'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("2025/2026", cursor.getString(0))
                assertEquals("PREPARATION", cursor.getString(1))
            }
            database.query("SELECT destination_name, net_grams, ticket_number FROM deliveries WHERE id='d'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("S.C.A. Bedmarense", cursor.getString(0))
                assertEquals(3500000L, cursor.getLong(1))
                assertEquals("T-101", cursor.getString(2))
            }
            database.query("SELECT quantity, unit FROM harvest_labour WHERE id='l'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(4, cursor.getInt(0))
                assertEquals("DAY", cursor.getString(1))
            }
            database.query("SELECT concept, amount_minor FROM expenses WHERE id='e'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Gasóleo tractor", cursor.getString(0))
                assertEquals(8500L, cursor.getLong(1))
            }
            database.query("SELECT display_name, mime_type, local_uri FROM documents WHERE id='doc'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Foto Olivar", cursor.getString(0))
                assertEquals("image/jpeg", cursor.getString(1))
                assertEquals("file:///photos/olivar.jpg", cursor.getString(2))
            }
            listOf(
                "territorial_municipalities",
                "irrigation_communities",
                "community_water_notices",
                "personal_irrigation_plans",
            ).forEach { table ->
                database.query("SELECT COUNT(*) FROM $table").use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals(0, cursor.getInt(0))
                }
            }
        }
    }

    @Test
    fun migration24To25KeepsLegacyTreatmentAndAddsEmptyCueFields() {
        migrationHelper.createDatabase(TEST_DATABASE, 24).use { database ->
            database.execSQL("INSERT INTO workspaces (id, name, owner_user_id, country_code, timezone, locale, currency, created_at, updated_at, version, sync_status) VALUES ('w','Farm','owner','ES','Europe/Madrid','es-ES','EUR',1000,1000,1,'LOCAL_ONLY')")
            database.execSQL("INSERT INTO activities (id, workspace_id, farm_id, activity_date, type, status, description, created_at, updated_at, version, sync_status) VALUES ('a','w',NULL,'2026-10-05','PHYTOSANITARY','COMPLETED','Tratamiento',1000,1000,7,'PENDING')")
            database.execSQL("INSERT INTO phytosanitary_details (activity_id, workspace_id, product_name, active_substance, dose_value, dose_unit, reason, equipment_text, created_at, updated_at, version, sync_status) VALUES ('a','w','Cobre 50%','Oxicloruro de cobre',2.0,'kg/ha','Repilo','Atomizador',1000,1000,7,'PENDING')")
        }
        migrationHelper.runMigrationsAndValidate(TEST_DATABASE, 25, true, DatabaseMigrations.MIGRATION_24_25).use { database ->
            database.query(
                "SELECT product_name, active_substance, dose_value, dose_unit, reason, equipment_text, " +
                    "operator_person_id, application_machine_id, service_provider_organization_id, " +
                    "product_registration_number, product_source, product_source_version, product_fetched_at, " +
                    "authorization_context_snapshot, pest_problem_code, efficacy_code, treatment_observations " +
                    "FROM phytosanitary_details WHERE activity_id='a'",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Cobre 50%", cursor.getString(0))
                assertEquals("Oxicloruro de cobre", cursor.getString(1))
                assertEquals(2.0, cursor.getDouble(2), 0.0)
                assertEquals("kg/ha", cursor.getString(3))
                assertEquals("Repilo", cursor.getString(4))
                assertEquals("Atomizador", cursor.getString(5))
                for (column in 6..16) assertTrue(cursor.isNull(column))
            }
        }
    }

    @Test
    fun everySupportedVersionUpgradesToTheLatestKeepingItsWorkspace() {
        for (version in 1 until MaginaOlivoDatabase.VERSION) {
            context.deleteDatabase(TEST_DATABASE)
            migrationHelper.createDatabase(TEST_DATABASE, version).use { database ->
                database.execSQL("INSERT INTO workspaces (id, name, owner_user_id, country_code, timezone, locale, currency, created_at, updated_at, version, sync_status) VALUES ('w','Historical farm','owner','ES','Europe/Madrid','es-ES','EUR',1000,1000,7,'PENDING')")
            }
            migrationHelper.runMigrationsAndValidate(TEST_DATABASE, MaginaOlivoDatabase.VERSION, true, *DatabaseMigrations.all).use { database ->
                database.query("SELECT name, version, sync_status FROM workspaces WHERE id='w'").use { cursor ->
                    assertTrue("Missing workspace upgrading v$version", cursor.moveToFirst())
                    assertEquals("Historical farm", cursor.getString(0))
                    assertEquals(7, cursor.getInt(1))
                    assertEquals("PENDING", cursor.getString(2))
                }
            }
        }
    }

    @Test
    fun migration23To24AddsPhytosanitaryResourcesWithoutTouchingExistingMachines() {
        migrationHelper.createDatabase(TEST_DATABASE, 23).use { database ->
            database.execSQL("INSERT INTO workspaces (id, name, owner_user_id, country_code, timezone, locale, currency, created_at, updated_at, version, sync_status) VALUES ('w','Farm','owner','ES','Europe/Madrid','es-ES','EUR',1000,1000,1,'LOCAL_ONLY')")
            database.execSQL("INSERT INTO machines (id, workspace_id, name, category, registration_or_serial, status, created_at, updated_at, version, sync_status) VALUES ('m','w','Atomizador','ATOMIZER','SERIE-1','ACTIVE',1000,1000,4,'PENDING')")
        }
        migrationHelper.runMigrationsAndValidate(TEST_DATABASE, 24, true, DatabaseMigrations.MIGRATION_23_24).use { database ->
            database.query("SELECT name, category, registration_or_serial, version, sync_status FROM machines WHERE id='m'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Atomizador", cursor.getString(0))
                assertEquals("ATOMIZER", cursor.getString(1))
                assertEquals("SERIE-1", cursor.getString(2))
                assertEquals(4, cursor.getInt(3))
                assertEquals("PENDING", cursor.getString(4))
            }
            for (table in listOf(
                "agronomic_people",
                "agronomic_credentials",
                "phytosanitary_equipment_profiles",
                "phytosanitary_equipment_inspections",
            )) {
                database.query("SELECT COUNT(*) FROM $table").use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals(0, cursor.getInt(0))
                }
            }
        }
    }

    @Test
    fun migration22To23AddsOptionalActivityEndDateWithoutRewritingLegacyRows() {
        migrationHelper.createDatabase(TEST_DATABASE, 22).use { database ->
            database.execSQL("INSERT INTO workspaces (id, name, owner_user_id, country_code, timezone, locale, currency, created_at, updated_at, version, sync_status) VALUES ('w','Farm','owner','ES','Europe/Madrid','es-ES','EUR',1000,1000,1,'LOCAL_ONLY')")
            database.execSQL("INSERT INTO activities (id, workspace_id, farm_id, activity_date, type, status, description, created_at, updated_at, version, sync_status) VALUES ('a','w',NULL,'2026-10-05','PHYTOSANITARY','PLANNED','Tratamiento',1000,1000,7,'PENDING')")
        }
        migrationHelper.runMigrationsAndValidate(TEST_DATABASE, 23, true, DatabaseMigrations.MIGRATION_22_23).use { database ->
            database.query("SELECT activity_date, activity_end_date, description, version, sync_status FROM activities WHERE id='a'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("2026-10-05", cursor.getString(0))
                assertTrue(cursor.isNull(1))
                assertEquals("Tratamiento", cursor.getString(2))
                assertEquals(7, cursor.getInt(3))
                assertEquals("PENDING", cursor.getString(4))
            }
        }
    }

    @Test
    fun migration21To22PreservesLegacyEquipmentWithoutInventingPrice() {
        migrationHelper.createDatabase(TEST_DATABASE, 21).use { database ->
            database.execSQL("INSERT INTO workspaces (id, name, owner_user_id, country_code, timezone, locale, currency, created_at, updated_at, version, sync_status) VALUES ('w','Farm','owner','ES','Europe/Madrid','es-ES','EUR',1000,1000,1,'LOCAL_ONLY')")
            database.execSQL("INSERT INTO harvests (id, workspace_id, harvest_date, weight_grams, day_origin, created_at, updated_at, version, sync_status) VALUES ('h','w','2026-10-01',5000,'LEGACY',1000,1000,3,'LOCAL_ONLY')")
            database.execSQL("INSERT INTO harvest_equipment (id, workspace_id, harvest_id, type, quantity, created_at, updated_at, version, sync_status) VALUES ('e','w','h','SHAKER',2,1000,1000,7,'PENDING')")
        }
        migrationHelper.runMigrationsAndValidate(TEST_DATABASE, 22, true, DatabaseMigrations.MIGRATION_21_22).use { database ->
            database.query("SELECT type, quantity, applied_price_minor, applied_currency, applied_price_date, version FROM harvest_equipment WHERE id='e'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("SHAKER", cursor.getString(0))
                assertEquals(2, cursor.getInt(1))
                for (column in 2..4) assertTrue(cursor.isNull(column))
                assertEquals(7, cursor.getInt(5))
            }
        }
    }

    @Test
    fun migration20To21PreservesAnonymousHalfDayWithoutInventingPriceOrPayments() {
        migrationHelper.createDatabase(TEST_DATABASE, 20).use { database ->
            database.execSQL("INSERT INTO workspaces (id, name, owner_user_id, country_code, timezone, locale, currency, created_at, updated_at, version, sync_status) VALUES ('w','Farm','owner','ES','Europe/Madrid','es-ES','EUR',1000,1000,1,'LOCAL_ONLY')")
            database.execSQL("INSERT INTO harvests (id, workspace_id, harvest_date, weight_grams, day_origin, created_at, updated_at, version, sync_status) VALUES ('h','w','2026-10-01',5000,'LEGACY',1000,1000,3,'LOCAL_ONLY')")
            database.execSQL("INSERT INTO harvest_labour (id, workspace_id, harvest_id, quantity, unit, created_at, updated_at, version, sync_status) VALUES ('l','w','h',5,'HALF_DAY',1000,1000,7,'PENDING')")
        }
        migrationHelper.runMigrationsAndValidate(TEST_DATABASE, 21, true, DatabaseMigrations.MIGRATION_20_21).use { database ->
            database.query("SELECT quantity, unit, worker_id, applied_price_minor, applied_currency, applied_price_date, applied_basis, version FROM harvest_labour WHERE id='l'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(5, cursor.getInt(0))
                assertEquals("HALF_DAY", cursor.getString(1))
                for (column in 2..6) assertTrue(cursor.isNull(column))
                assertEquals(7, cursor.getInt(7))
            }
            database.query("SELECT COUNT(*) FROM labour_payments").use { cursor -> cursor.moveToFirst(); assertEquals(0, cursor.getInt(0)) }
            database.query("SELECT weight_grams FROM harvests WHERE id='h'").use { cursor -> cursor.moveToFirst(); assertEquals(5000, cursor.getInt(0)) }
        }
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

    @Test
    fun migration10To11KeepsParcelsAndLeavesTheirGroveDescriptionEmpty() {
        migrationHelper.createDatabase(TEST_DATABASE, 10).use { database ->
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
                INSERT INTO parcels (
                    id, workspace_id, display_name, source, managed_area_m2, status,
                    created_at, updated_at, deleted_at, version, sync_status,
                    remote_version, last_synced_at
                ) VALUES (
                    '33333333-3333-3333-3333-333333333333',
                    '11111111-1111-1111-1111-111111111111', 'Parcela Norte', 'MANUAL',
                    33800.0, 'ACTIVE', 1000, 1000, NULL, 3, 'SYNCED', 3, 1000
                )
                """.trimIndent(),
            )
        }

        migrationHelper
            .runMigrationsAndValidate(
                TEST_DATABASE,
                11,
                true,
                DatabaseMigrations.MIGRATION_10_11,
            ).use { database ->
                database.query(
                    "SELECT display_name, managed_area_m2, version, olive_tree_count, variety, " +
                        "irrigation_system, irrigation_network, irrigation_sector, irrigation_days FROM parcels",
                ).use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("Parcela Norte", cursor.getString(0))
                    assertEquals(33800.0, cursor.getDouble(1), 0.0)
                    // The migration adds columns only: no version bump, nothing invented.
                    assertEquals(3, cursor.getInt(2))
                    (3..8).forEach { column -> assertTrue(cursor.isNull(column)) }
                }
            }
    }

    @Test
    fun migration11To12BackfillsCatastroProvenanceAndKeepsManualRowsEmpty() {
        migrationHelper.createDatabase(TEST_DATABASE, 11).use { database ->
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
            fun insertParcel(id: String, source: String, reference: String?, createdAt: Long) {
                val ref = reference?.let { "'$it'" } ?: "NULL"
                database.execSQL(
                    """
                    INSERT INTO parcels (
                        id, workspace_id, display_name, cadastral_reference, source,
                        managed_area_m2, olive_tree_count, status, created_at, updated_at,
                        deleted_at, version, sync_status, remote_version, last_synced_at
                    ) VALUES (
                        '$id', '11111111-1111-1111-1111-111111111111', 'Parcela', $ref, '$source',
                        1200.0, 150, 'ACTIVE', $createdAt, $createdAt, NULL, 2, 'SYNCED', 2, $createdAt
                    )
                    """.trimIndent(),
                )
            }
            insertParcel("66666666-6666-6666-6666-666666666666", "CATASTRO", "23044A00400021", 5000)
            insertParcel("77777777-7777-7777-7777-777777777777", "MANUAL", null, 6000)
        }

        migrationHelper
            .runMigrationsAndValidate(TEST_DATABASE, 12, true, DatabaseMigrations.MIGRATION_11_12)
            .use { database ->
                database.query(
                    "SELECT source_provider, source_imported_at, olive_tree_count, version FROM parcels ORDER BY id",
                ).use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("ES_CATASTRO", cursor.getString(0))
                    assertEquals(5000L, cursor.getLong(1))
                    // v11 grove data survives and the backfill is not an edit: no version bump.
                    assertEquals(150, cursor.getInt(2))
                    assertEquals(2, cursor.getInt(3))
                    assertTrue(cursor.moveToNext())
                    assertTrue(cursor.isNull(0))
                    assertTrue(cursor.isNull(1))
                }
            }
    }

    @Test
    fun migration12To13KeepsEveryPesadaUnlinkedAndWithoutAnHour() {
        migrationHelper.createDatabase(TEST_DATABASE, 12).use { database ->
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
                INSERT INTO deliveries (
                    id, workspace_id, farm_id, campaign_id, delivery_date, destination_name,
                    net_grams, ticket_number, source, created_at, updated_at, deleted_at,
                    version, sync_status, remote_version, last_synced_at
                ) VALUES (
                    '88888888-8888-8888-8888-888888888888', '11111111-1111-1111-1111-111111111111',
                    '33333333-3333-3333-3333-333333333333', '44444444-4444-4444-4444-444444444444',
                    '2025-11-24', 'Coop. San Isidro', 2850000, 'V-118', 'MANUAL', 5000, 5000, NULL,
                    3, 'SYNCED', 3, 5000
                )
                """.trimIndent(),
            )
        }

        migrationHelper
            .runMigrationsAndValidate(TEST_DATABASE, 13, true, DatabaseMigrations.MIGRATION_12_13)
            .use { database ->
                database.query(
                    "SELECT harvest_id, delivery_time, net_grams, ticket_number, version FROM deliveries",
                ).use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertTrue(cursor.isNull(0))
                    assertTrue(cursor.isNull(1))
                    // The weighing itself is untouched and the migration is not an edit.
                    assertEquals(2850000L, cursor.getLong(2))
                    assertEquals("V-118", cursor.getString(3))
                    assertEquals(3, cursor.getInt(4))
                }
            }
    }

    @Test
    fun migration13To14AddsEmptyLabourTablesAndKeepsHarvests() {
        migrationHelper.createDatabase(TEST_DATABASE, 13).use { database ->
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
            .runMigrationsAndValidate(TEST_DATABASE, 14, true, DatabaseMigrations.MIGRATION_13_14)
            .use { database ->
                listOf("workers", "harvest_labour").forEach { table ->
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
    fun migration14To15AddsAnEmptyEquipmentTable() {
        migrationHelper.createDatabase(TEST_DATABASE, 14).close()
        migrationHelper
            .runMigrationsAndValidate(TEST_DATABASE, 15, true, DatabaseMigrations.MIGRATION_14_15)
            .use { database ->
                database.query("SELECT COUNT(*) FROM harvest_equipment").use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals(0, cursor.getInt(0))
                }
            }
    }

    @Test
    fun migration15To16KeepsEveryPesadaWithoutAnOrigin() {
        migrationHelper.createDatabase(TEST_DATABASE, 15).use { database ->
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
                INSERT INTO deliveries (
                    id, workspace_id, farm_id, campaign_id, delivery_date, destination_name,
                    net_grams, ticket_number, source, created_at, updated_at, deleted_at,
                    version, sync_status, remote_version, last_synced_at
                ) VALUES (
                    '88888888-8888-8888-8888-888888888888', '11111111-1111-1111-1111-111111111111',
                    '33333333-3333-3333-3333-333333333333', '44444444-4444-4444-4444-444444444444',
                    '2025-11-24', 'Bedmarense', 2390000, 'V-7', 'MANUAL', 5000, 5000, NULL,
                    2, 'SYNCED', 2, 5000
                )
                """.trimIndent(),
            )
        }

        migrationHelper
            .runMigrationsAndValidate(TEST_DATABASE, 16, true, DatabaseMigrations.MIGRATION_15_16)
            .use { database ->
                database.query("SELECT harvest_origin, net_grams, ticket_number, version FROM deliveries").use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    // Nothing is guessed for older Pesadas: they stay "sin indicar".
                    assertTrue(cursor.isNull(0))
                    assertEquals(2390000L, cursor.getLong(1))
                    assertEquals("V-7", cursor.getString(2))
                    assertEquals(2, cursor.getInt(3))
                }
            }
    }

    @Test
    fun migration16To17MarksOnlyUnweighedJornadasAsAutomaticDaysAndKeepsTypedKilos() {
        migrationHelper.createDatabase(TEST_DATABASE, 16).use { database ->
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
            // A hand-recorded Jornada (1.250 kg typed) and one opened before its first Pesada (0).
            listOf("'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 1250000", "'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 0").forEach { values ->
                database.execSQL(
                    """
                    INSERT INTO harvests (
                        id, weight_grams, workspace_id, campaign_id, farm_id, harvest_date,
                        created_at, updated_at, deleted_at, version, sync_status, remote_version, last_synced_at
                    ) VALUES (
                        $values, '11111111-1111-1111-1111-111111111111',
                        '44444444-4444-4444-4444-444444444444', '33333333-3333-3333-3333-333333333333',
                        '2025-11-24', 5000, 5000, NULL, 3, 'SYNCED', 3, 5000
                    )
                    """.trimIndent(),
                )
            }
        }

        migrationHelper
            .runMigrationsAndValidate(TEST_DATABASE, 17, true, DatabaseMigrations.MIGRATION_16_17)
            .use { database ->
                database.query("SELECT id, day_origin, weight_grams, version FROM harvests ORDER BY id").use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    // The typed kilos stay the farmer's own figure, untouched.
                    assertTrue(cursor.isNull(1))
                    assertEquals(1250000L, cursor.getLong(2))
                    assertEquals(3, cursor.getInt(3))
                    assertTrue(cursor.moveToNext())
                    assertEquals("AUTO_DAY", cursor.getString(1))
                    assertEquals(0L, cursor.getLong(2))
                }
            }
    }

    @Test
    fun migration17To18AddsAnEmptyPriceTableAndTouchesNoMoney() {
        migrationHelper.createDatabase(TEST_DATABASE, 17).close()
        migrationHelper
            .runMigrationsAndValidate(TEST_DATABASE, 18, true, DatabaseMigrations.MIGRATION_17_18)
            .use { database ->
                // No prices are guessed for any Farm: every calculation starts unknown.
                database.query("SELECT COUNT(*) FROM recollection_rates").use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals(0, cursor.getInt(0))
                }
            }
    }

    /** Phase 21A: v19 adds an empty profile table; no municipality or cooperative is guessed. */
    @Test
    fun migration18To19AddsAnEmptyProfileTable() {
        migrationHelper.createDatabase(TEST_DATABASE, 18).close()
        migrationHelper
            .runMigrationsAndValidate(TEST_DATABASE, 19, true, DatabaseMigrations.MIGRATION_18_19)
            .use { database ->
                database.query("SELECT COUNT(*) FROM profile_settings").use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals(0, cursor.getInt(0))
                }
            }
    }

    /** Phase 21B: v20 keeps the saved profile; reminders stay on and the day-before hour is 08:00. */
    @Test
    fun migration19To20KeepsTheProfileAndTurnsRemindersOnAt0800() {
        migrationHelper.createDatabase(TEST_DATABASE, 19).use { database ->
            database.execSQL(
                "INSERT INTO workspaces (id, name, owner_user_id, country_code, timezone, locale, currency, created_at, updated_at, version, sync_status) " +
                    "VALUES ('w-21b', 'Olivar', 'u-21b', 'ES', 'Europe/Madrid', 'es-ES', 'EUR', 0, 0, 1, 'LOCAL_ONLY')",
            )
            database.execSQL(
                "INSERT INTO profile_settings (id, workspace_id, municipality, province, preferred_organization_id, created_at, updated_at, version, sync_status) " +
                    "VALUES ('p-21b', 'w-21b', 'Bedmar', 'Jaén', NULL, 0, 0, 1, 'PENDING')",
            )
        }
        migrationHelper
            .runMigrationsAndValidate(TEST_DATABASE, 20, true, DatabaseMigrations.MIGRATION_19_20)
            .use { database ->
                database.query("SELECT municipality, reminders_enabled, previous_day_reminder_minute FROM profile_settings").use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("Bedmar", cursor.getString(0))
                    assertEquals(1, cursor.getInt(1))
                    assertEquals(480, cursor.getInt(2))
                }
            }
    }

    private companion object {
        const val TEST_DATABASE = "room-migration-test"
    }
}
