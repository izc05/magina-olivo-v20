package com.isivoltpro.maginaolivo.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.isivoltpro.maginaolivo.data.local.entity.FarmEntity
import com.isivoltpro.maginaolivo.data.local.entity.HarvestEntity
import com.isivoltpro.maginaolivo.data.local.entity.LocalMetadata
import com.isivoltpro.maginaolivo.data.local.entity.ParcelEntity
import com.isivoltpro.maginaolivo.data.local.entity.SyncOutboxEntity
import com.isivoltpro.maginaolivo.data.local.entity.WorkspaceEntity
import com.isivoltpro.maginaolivo.data.local.model.FarmStatus
import com.isivoltpro.maginaolivo.data.local.model.OutboxOperation
import com.isivoltpro.maginaolivo.data.local.model.OutboxStatus
import com.isivoltpro.maginaolivo.data.local.model.SyncEntityType
import com.isivoltpro.maginaolivo.data.local.model.SyncStatus
import com.isivoltpro.maginaolivo.data.repository.sync.DeliveryReceiveBundle
import com.isivoltpro.maginaolivo.data.repository.sync.DeliveryReceiveBundleParser
import com.isivoltpro.maginaolivo.data.repository.sync.DeliveryReceiveOutcome
import com.isivoltpro.maginaolivo.data.repository.sync.readDeliveryImportReceipt
import com.isivoltpro.maginaolivo.data.repository.sync.receiveDeliveryBundle
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumental Room tests for G2.5 [receiveDeliveryBundle].
 * Validates transactional reception into local SQLite/Room:
 *  - Empty Room: full 10-table graph + import receipt inserted in 1 transaction; 0 outbox operations.
 *  - Duplicate: idempotent, returns ALREADY_IMPORTED, 0 outbox operations, 0 duplicate rows.
 *  - Conflict: local modification or version mismatch returns CONFLICT, leaves local bytes untouched.
 *  - Conflict: pending outbox operation on any entity in graph returns CONFLICT, 0 rows written.
 *  - Rollback: failure before commit leaves 0 rows in database.
 */
@RunWith(AndroidJUnit4::class)
class DeliveryReceiveRoomTest {

    private lateinit var context: Context
    private lateinit var db: MaginaOlivoDatabase
    private val dbName = "test-g25-receive-${UUID.randomUUID()}.db"

    private val accountId = UUID.fromString("008ea900-5f3c-407f-8917-ad72bc9d955b")
    private val deliveryId = UUID.fromString("07db9e31-e48e-4041-8c82-bb875af0c5e4")
    private val farmId = UUID.fromString("72dc9b9e-8814-4d8c-b95d-00b986a3e66e")
    private val campaignId = UUID.fromString("1c053c81-294c-405b-8426-04ca8a18ecf9")
    private val parcelId = UUID.fromString("6243d355-6ce6-4189-a37e-8527fb233abe")
    private val workspaceId = UUID.fromString("7b89b229-4b74-4d30-a6d9-8ea914364495")
    private val harvestId = UUID.fromString("47d2eeff-2330-449d-bf95-9d78666abf69")
    private val membershipId = UUID.fromString("e0110000-0000-0000-0000-000000000001")
    private val campaignParcelId = UUID.fromString("e0110000-0000-0000-0000-000000000002")
    private val harvestParcelId = UUID.fromString("e0110000-0000-0000-0000-000000000003")
    private val deliveryParcelId = UUID.fromString("e0110000-0000-0000-0000-000000000004")

    private val headHash = "e5c3825c96a37a42186b800b7bebc59167fe68d6c738672e20921d2dd5380902"
    private val originEvidence = "1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef"
    private val canonicalIssuer = "https://zubpicdibqbmewxeurhm.supabase.co/auth/v1"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbName)
        db = Room.inMemoryDatabaseBuilder(context, MaginaOlivoDatabase::class.java).build()
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(dbName)
    }

    private fun count(table: String): Int {
        return db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }

    private fun bundleJson(
        version: Long = 1L,
        netGrams: Long = 1540000L,
        harvestGrams: Long = 1540000L,
        customDeliveryId: UUID = deliveryId,
    ): String = """
    {
      "receive_contract_version": 1,
      "issuer": "$canonicalIssuer",
      "account_id": "$accountId",
      "head_payload_hash": "$headHash",
      "head_version": $version,
      "workspace": {
        "id": "$workspaceId",
        "owner_user_id": "$accountId",
        "name": "Finca Principal",
        "country_code": "ES",
        "timezone": "Europe/Madrid",
        "locale": "es-ES",
        "currency": "EUR",
        "created_at": "2026-10-01T00:00:00Z",
        "updated_at": "2026-10-01T00:00:00Z",
        "version": 1
      },
      "farm": {
        "id": "$farmId",
        "workspace_id": "$workspaceId",
        "owner_id": "$accountId",
        "creator_user_id": "$accountId",
        "name": "Finca Sol",
        "description": "Finca de ensayo",
        "municipality": "Baeza",
        "province": "Jaén",
        "notes": null,
        "status": "ACTIVE",
        "created_at": "2026-10-01T00:00:00Z",
        "updated_at": "2026-10-01T00:00:00Z",
        "version": 1
      },
      "parcel": {
        "id": "$parcelId",
        "workspace_id": "$workspaceId",
        "display_name": "Parcela 1",
        "cadastral_reference": "23009A001000010000AA",
        "cadastral_polygon": "1",
        "cadastral_parcel": "1",
        "municipality": "Baeza",
        "province": "Jaén",
        "source": "MANUAL",
        "geometry_geojson": null,
        "source_provider": null,
        "source_imported_at": null,
        "cadastral_area_m2": 15000.0,
        "managed_area_m2": 15000.0,
        "notes": null,
        "status": "ACTIVE",
        "olive_tree_count": 300,
        "variety": "PICUAL",
        "irrigation_system": "DRIP",
        "irrigation_network": null,
        "irrigation_sector": null,
        "irrigation_days": null,
        "created_at": "2026-10-01T00:00:00Z",
        "updated_at": "2026-10-01T00:00:00Z",
        "version": 1
      },
      "membership": {
        "id": "$membershipId",
        "workspace_id": "$workspaceId",
        "farm_id": "$farmId",
        "parcel_id": "$parcelId",
        "valid_from": "2026-01-01T00:00:00Z",
        "valid_until": null,
        "created_at": "2026-10-01T00:00:00Z",
        "updated_at": "2026-10-01T00:00:00Z",
        "version": 1
      },
      "campaign": {
        "id": "$campaignId",
        "workspace_id": "$workspaceId",
        "farm_id": "$farmId",
        "name": "Campaña 2026/2027",
        "start_date": "2026-10-01",
        "end_date": "2027-09-30",
        "status": "ACTIVE",
        "snapshot_format": 1,
        "notes": null,
        "created_at": "2026-10-01T00:00:00Z",
        "updated_at": "2026-10-01T00:00:00Z",
        "version": 1
      },
      "campaign_parcel": {
        "id": "$campaignParcelId",
        "workspace_id": "$workspaceId",
        "campaign_id": "$campaignId",
        "parcel_id": "$parcelId",
        "farm_id_at_start": "$farmId",
        "farm_name_at_start": "Finca Sol",
        "parcel_name_at_start": "Parcela 1",
        "managed_area_m2_at_start": 15000.0,
        "cadastral_reference_at_start": "23009A001000010000AA",
        "geometry_geo_json_snapshot": null,
        "origin_created_at": "2026-10-27T10:00:00Z",
        "origin_updated_at": "2026-10-27T10:00:00Z",
        "origin_version": 1,
        "origin_evidence_sha256": "$originEvidence"
      },
      "harvest": {
        "id": "$harvestId",
        "workspace_id": "$workspaceId",
        "farm_id": "$farmId",
        "campaign_id": "$campaignId",
        "harvest_date": "2026-10-15",
        "weight_grams": $harvestGrams,
        "notes": "Recolección temprana",
        "created_at": "2026-10-15T00:00:00Z",
        "updated_at": "2026-10-15T00:00:00Z",
        "version": 1
      },
      "harvest_parcel": {
        "id": "$harvestParcelId",
        "workspace_id": "$workspaceId",
        "harvest_id": "$harvestId",
        "parcel_id": "$parcelId",
        "campaign_parcel_id": "$campaignParcelId",
        "parcel_name_at_harvest": "Parcela 1",
        "weight_grams": null,
        "allocation_mode": "UNALLOCATED",
        "origin_created_at": "2026-10-27T10:00:00Z",
        "origin_updated_at": "2026-10-27T10:00:00Z",
        "origin_version": 1,
        "origin_evidence_sha256": "$originEvidence"
      },
      "delivery": {
        "id": "$customDeliveryId",
        "workspace_id": "$workspaceId",
        "farm_id": "$farmId",
        "campaign_id": "$campaignId",
        "harvest_id": "$harvestId",
        "delivery_date": "2026-10-15",
        "destination_name": "Cooperativa San Isidro",
        "destination_type": "COOPERATIVE",
        "net_grams": $netGrams,
        "status": "DELIVERED",
        "notes": "Pesada QA 1540 kg",
        "snapshot_format": 2,
        "harvest_origin": "MANUAL",
        "created_at": "2026-10-15T14:00:00Z",
        "updated_at": "2026-10-15T14:00:00Z",
        "version": $version
      },
      "delivery_parcel": {
        "id": "$deliveryParcelId",
        "workspace_id": "$workspaceId",
        "delivery_id": "$customDeliveryId",
        "parcel_id": "$parcelId",
        "campaign_parcel_id": "$campaignParcelId",
        "parcel_name_at_delivery": "Parcela 1",
        "weight_grams": null,
        "allocation_mode": "UNALLOCATED",
        "origin_created_at": "2026-10-27T10:00:00Z",
        "origin_updated_at": "2026-10-27T10:00:00Z",
        "origin_version": 1,
        "origin_evidence_sha256": "$originEvidence"
      }
    }
    """.trimIndent()

    private fun parseBundle(json: String = bundleJson()): DeliveryReceiveBundle =
        DeliveryReceiveBundleParser.parse(json, accountId, canonicalIssuer)

    @Test
    fun emptyRoom_receivesBundle_insertsAllTenRowsAndReceipt_withZeroOutbox() = runBlocking {
        // Assert clean empty database
        val tables = listOf(
            "workspaces", "farms", "parcels", "farm_parcel_memberships",
            "campaigns", "campaign_parcels", "harvests", "harvest_parcels",
            "deliveries", "delivery_parcels",
        )
        for (table in tables) {
            assertEquals("Table $table must start empty", 0, count(table))
        }
        assertEquals("sync_outbox must be empty", 0, count("sync_outbox"))

        val bundle = parseBundle()
        val outcome = db.receiveDeliveryBundle(bundle)

        assertEquals(DeliveryReceiveOutcome.IMPORTED, outcome)

        // Verify each of the 10 tables now contains exactly 1 row
        for (table in tables) {
            assertEquals("Table $table must have exactly 1 row", 1, count(table))
        }

        // Verify receipt table created and populated
        assertEquals("Receipt table must have 1 row", 1, count("delivery_import_receipts"))
        val receipt = db.readDeliveryImportReceipt(deliveryId)
        assertNotNull(receipt)
        assertEquals(deliveryId, receipt!!.deliveryId)
        assertEquals(accountId.toString(), receipt.accountId)
        assertEquals(canonicalIssuer, receipt.issuer)
        assertEquals(headHash, receipt.headPayloadHash)
        assertEquals(1L, receipt.headVersion)

        // Verify delivery fields
        val delivery = db.deliveryDao().findById(deliveryId)
        assertNotNull(delivery)
        assertEquals(1540000L, delivery!!.netGrams)
        assertEquals(workspaceId, delivery.workspaceId)
        assertEquals(farmId, delivery.farmId)
        assertEquals(campaignId, delivery.campaignId)
        assertEquals(harvestId, delivery.harvestId)

        // CRITICAL CONSTRAINT: ZERO outbox operations generated
        assertEquals("Reception must generate zero outbox rows", 0, count("sync_outbox"))
    }

    @Test
    fun duplicateReceive_returnsAlreadyImported_leavesRowsAndOutboxUntouched() = runBlocking {
        val bundle = parseBundle()
        val outcome1 = db.receiveDeliveryBundle(bundle)
        assertEquals(DeliveryReceiveOutcome.IMPORTED, outcome1)
        assertEquals(1, count("deliveries"))
        assertEquals(0, count("sync_outbox"))

        // Re-receiving identical bundle
        val outcome2 = db.receiveDeliveryBundle(bundle)
        assertEquals(DeliveryReceiveOutcome.ALREADY_IMPORTED, outcome2)

        // No new rows, no outbox
        assertEquals(1, count("deliveries"))
        assertEquals(1, count("delivery_import_receipts"))
        assertEquals(0, count("sync_outbox"))
    }

    @Test
    fun duplicateReceive_afterLocalFarmEdit_returnsConflict() = runBlocking {
        val bundle = parseBundle()
        assertEquals(DeliveryReceiveOutcome.IMPORTED, db.receiveDeliveryBundle(bundle))
        val farm = db.farmDao().findById(farmId)!!
        db.farmDao().upsert(farm.copy(name = "Local edit"))

        assertEquals(DeliveryReceiveOutcome.CONFLICT, db.receiveDeliveryBundle(bundle))
        assertEquals("Local edit", db.farmDao().findById(farmId)!!.name)
        assertEquals(1, count("delivery_import_receipts"))
        assertEquals(0, count("sync_outbox"))
    }

    @Test
    fun duplicateReceive_afterLocalDeliveryNotesEdit_returnsConflict() = runBlocking {
        val bundle = parseBundle()
        assertEquals(DeliveryReceiveOutcome.IMPORTED, db.receiveDeliveryBundle(bundle))
        val delivery = db.deliveryDao().findById(deliveryId)!!
        db.deliveryDao().upsert(delivery.copy(notes = "Local note"))

        assertEquals(DeliveryReceiveOutcome.CONFLICT, db.receiveDeliveryBundle(bundle))
        assertEquals("Local note", db.deliveryDao().findById(deliveryId)!!.notes)
        assertEquals(0, count("sync_outbox"))
    }

    @Test
    fun conflictWithModifiedLocalDelivery_returnsConflict_andPreservesLocalRow() = runBlocking {
        val bundle = parseBundle()
        assertEquals(DeliveryReceiveOutcome.IMPORTED, db.receiveDeliveryBundle(bundle))

        // Locally modify the delivery
        val localDelivery = db.deliveryDao().findById(deliveryId)!!
        db.deliveryDao().upsert(
            localDelivery.copy(
                netGrams = 2000000L,
                metadata = localDelivery.metadata.copy(version = 2L),
            ),
        )

        // Attempting to re-receive bundle with head_version 1
        val outcome = db.receiveDeliveryBundle(bundle)
        assertEquals(DeliveryReceiveOutcome.CONFLICT, outcome)

        // Preserves modified local delivery data
        val preserved = db.deliveryDao().findById(deliveryId)!!
        assertEquals(2000000L, preserved.netGrams)
        assertEquals(2L, preserved.metadata.version)
        assertEquals(0, count("sync_outbox"))
    }

    @Test
    fun conflictWithPendingOutboxOperation_returnsConflict_withoutModifyingRoom() = runBlocking {
        // Insert a pending outbox operation for the farm
        db.syncOutboxDao().insert(
            SyncOutboxEntity(
                id = UUID.randomUUID(),
                entityType = SyncEntityType.FARM,
                entityId = farmId,
                operation = OutboxOperation.UPDATE,
                payloadVersion = 1,
                status = OutboxStatus.PENDING,
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
            ),
        )
        assertEquals(1, count("sync_outbox"))

        val bundle = parseBundle()
        val outcome = db.receiveDeliveryBundle(bundle)

        assertEquals(DeliveryReceiveOutcome.CONFLICT, outcome)

        // Delivery must not be imported
        assertEquals(0, count("deliveries"))
        assertEquals(0, count("farms"))
        assertNull(db.readDeliveryImportReceipt(deliveryId))
    }

    @Test
    fun rollbackOnException_leavesDatabaseCompletelyEmpty() = runBlocking {
        // Run an atomic transaction that fails inside
        try {
            db.openHelper.writableDatabase.beginTransaction()
            db.openHelper.writableDatabase.execSQL("INSERT INTO workspaces (id, owner_user_id, name, country_code, timezone, locale, currency, created_at, updated_at, version, sync_status, remote_version, last_synced_at) VALUES ('${UUID.randomUUID()}', '$accountId', 'Test', 'ES', 'Europe/Madrid', 'es-ES', 'EUR', 0, 0, 1, 'SYNCED', 1, 0)")
            throw RuntimeException("Simulated process crash or unhandled constraint before commit")
        } catch (_: RuntimeException) {
            db.openHelper.writableDatabase.endTransaction()
        }

        assertEquals(0, count("workspaces"))
        assertEquals(0, count("deliveries"))
        assertEquals(0, count("sync_outbox"))
    }

    @Test
    fun existingParentFarmWithDifferingAttributes_returnsConflict_andLeavesLocalRowsAndOutboxUntouched() = runBlocking {
        // Pre-insert workspace matching bundle
        db.workspaceDao().upsert(
            WorkspaceEntity(
                id = workspaceId,
                name = "Finca Principal",
                ownerUserId = accountId,
                countryCode = "ES",
                timezone = "Europe/Madrid",
                locale = "es-ES",
                currency = "EUR",
                metadata = LocalMetadata(
                    createdAt = Instant.parse("2026-10-01T00:00:00Z"),
                    updatedAt = Instant.parse("2026-10-01T00:00:00Z"),
                    version = 1,
                    syncStatus = SyncStatus.SYNCED,
                ),
            ),
        )

        // Pre-insert farm with differing name ("Finca Diferente" vs bundle's "Finca Sol")
        db.farmDao().upsert(
            FarmEntity(
                id = farmId,
                workspaceId = workspaceId,
                name = "Finca Diferente",
                description = "Finca de ensayo",
                municipality = "Baeza",
                province = "Jaén",
                coverDocumentId = null,
                notes = null,
                status = FarmStatus.ACTIVE,
                metadata = LocalMetadata(
                    createdAt = Instant.parse("2026-10-01T00:00:00Z"),
                    updatedAt = Instant.parse("2026-10-01T00:00:00Z"),
                    version = 1,
                    syncStatus = SyncStatus.SYNCED,
                ),
            ),
        )

        val bundle = parseBundle()
        val outcome = db.receiveDeliveryBundle(bundle)

        assertEquals(DeliveryReceiveOutcome.CONFLICT, outcome)

        // Verify existing farm was NOT overwritten
        val preservedFarm = db.farmDao().findById(farmId)!!
        assertEquals("Finca Diferente", preservedFarm.name)
        assertEquals(0, count("deliveries"))
        assertEquals(0, count("sync_outbox"))
        assertNull(db.readDeliveryImportReceipt(deliveryId))
    }

    @Test
    fun existingParentFarmWithDifferingVersion_returnsConflict_andLeavesLocalRowsAndOutboxUntouched() = runBlocking {
        db.workspaceDao().upsert(
            WorkspaceEntity(
                id = workspaceId,
                name = "Finca Principal",
                ownerUserId = accountId,
                countryCode = "ES",
                timezone = "Europe/Madrid",
                locale = "es-ES",
                currency = "EUR",
                metadata = LocalMetadata(
                    createdAt = Instant.parse("2026-10-01T00:00:00Z"),
                    updatedAt = Instant.parse("2026-10-01T00:00:00Z"),
                    version = 1,
                    syncStatus = SyncStatus.SYNCED,
                ),
            ),
        )

        // Farm exists with version 2 (bundle has version 1)
        db.farmDao().upsert(
            FarmEntity(
                id = farmId,
                workspaceId = workspaceId,
                name = "Finca Sol",
                description = "Finca de ensayo",
                municipality = "Baeza",
                province = "Jaén",
                coverDocumentId = null,
                notes = null,
                status = FarmStatus.ACTIVE,
                metadata = LocalMetadata(
                    createdAt = Instant.parse("2026-10-01T00:00:00Z"),
                    updatedAt = Instant.parse("2026-10-01T00:00:00Z"),
                    version = 2,
                    syncStatus = SyncStatus.SYNCED,
                ),
            ),
        )

        val bundle = parseBundle()
        val outcome = db.receiveDeliveryBundle(bundle)

        assertEquals(DeliveryReceiveOutcome.CONFLICT, outcome)

        val preservedFarm = db.farmDao().findById(farmId)!!
        assertEquals(2L, preservedFarm.metadata.version)
        assertEquals(0, count("deliveries"))
        assertEquals(0, count("sync_outbox"))
    }

    @Test
    fun existingParentEntitiesWithIdenticalAttributes_succeeds_andImportsDelivery_withZeroOutbox() = runBlocking {
        // Pre-insert identical workspace and farm
        db.workspaceDao().upsert(
            WorkspaceEntity(
                id = workspaceId,
                name = "Finca Principal",
                ownerUserId = accountId,
                countryCode = "ES",
                timezone = "Europe/Madrid",
                locale = "es-ES",
                currency = "EUR",
                metadata = LocalMetadata(
                    createdAt = Instant.parse("2026-10-01T00:00:00Z"),
                    updatedAt = Instant.parse("2026-10-01T00:00:00Z"),
                    version = 1,
                    syncStatus = SyncStatus.SYNCED,
                ),
            ),
        )

        db.farmDao().upsert(
            FarmEntity(
                id = farmId,
                workspaceId = workspaceId,
                name = "Finca Sol",
                description = "Finca de ensayo",
                municipality = "Baeza",
                province = "Jaén",
                coverDocumentId = null,
                notes = null,
                status = FarmStatus.ACTIVE,
                metadata = LocalMetadata(
                    createdAt = Instant.parse("2026-10-01T00:00:00Z"),
                    updatedAt = Instant.parse("2026-10-01T00:00:00Z"),
                    version = 1,
                    syncStatus = SyncStatus.SYNCED,
                ),
            ),
        )

        val bundle = parseBundle()
        val outcome = db.receiveDeliveryBundle(bundle)

        assertEquals(DeliveryReceiveOutcome.IMPORTED, outcome)

        // Verify delivery and receipt imported successfully
        assertEquals(1, count("deliveries"))
        assertEquals(1, count("delivery_import_receipts"))
        assertEquals(0, count("sync_outbox"))

        val preservedFarm = db.farmDao().findById(farmId)!!
        assertEquals("Finca Sol", preservedFarm.name)
        assertEquals(1L, preservedFarm.metadata.version)
    }

    @Test
    fun deletedParentEntity_returnsConflict_withoutModifyingRoom() = runBlocking {
        db.workspaceDao().upsert(
            WorkspaceEntity(
                id = workspaceId,
                name = "Finca Principal",
                ownerUserId = accountId,
                countryCode = "ES",
                timezone = "Europe/Madrid",
                locale = "es-ES",
                currency = "EUR",
                metadata = LocalMetadata(
                    createdAt = Instant.parse("2026-10-01T00:00:00Z"),
                    updatedAt = Instant.parse("2026-10-01T00:00:00Z"),
                    deletedAt = Instant.now(),
                    version = 1,
                    syncStatus = SyncStatus.SYNCED,
                ),
            ),
        )

        val bundle = parseBundle()
        val outcome = db.receiveDeliveryBundle(bundle)

        assertEquals(DeliveryReceiveOutcome.CONFLICT, outcome)
        assertEquals(0, count("deliveries"))
        assertEquals(0, count("sync_outbox"))
    }

    @Test
    fun existingHarvestWithDifferingWeight_returnsConflict_withoutModifyingRoom() = runBlocking {
        // Pre-insert workspace and farm
        db.workspaceDao().upsert(
            WorkspaceEntity(
                id = workspaceId,
                name = "Finca Principal",
                ownerUserId = accountId,
                countryCode = "ES",
                timezone = "Europe/Madrid",
                locale = "es-ES",
                currency = "EUR",
                metadata = LocalMetadata(
                    createdAt = Instant.parse("2026-10-01T00:00:00Z"),
                    updatedAt = Instant.parse("2026-10-01T00:00:00Z"),
                    version = 1,
                    syncStatus = SyncStatus.SYNCED,
                ),
            ),
        )

        db.farmDao().upsert(
            FarmEntity(
                id = farmId,
                workspaceId = workspaceId,
                name = "Finca Sol",
                description = "Finca de ensayo",
                municipality = "Baeza",
                province = "Jaén",
                coverDocumentId = null,
                notes = null,
                status = FarmStatus.ACTIVE,
                metadata = LocalMetadata(
                    createdAt = Instant.parse("2026-10-01T00:00:00Z"),
                    updatedAt = Instant.parse("2026-10-01T00:00:00Z"),
                    version = 1,
                    syncStatus = SyncStatus.SYNCED,
                ),
            ),
        )

        // Pre-insert harvest with 999999g instead of 1540000g
        db.harvestDao().upsert(
            HarvestEntity(
                id = harvestId,
                workspaceId = workspaceId,
                farmId = farmId,
                campaignId = campaignId,
                harvestDate = java.time.LocalDate.parse("2026-10-15"),
                weightGrams = 999999L,
                destination = null,
                notes = "Recolección temprana",
                collectionMethod = null,
                workerCount = null,
                machineryText = null,
                dayOrigin = null,
                metadata = LocalMetadata(
                    createdAt = Instant.parse("2026-10-15T00:00:00Z"),
                    updatedAt = Instant.parse("2026-10-15T00:00:00Z"),
                    version = 1,
                    syncStatus = SyncStatus.SYNCED,
                ),
            ),
        )

        val bundle = parseBundle()
        val outcome = db.receiveDeliveryBundle(bundle)

        assertEquals(DeliveryReceiveOutcome.CONFLICT, outcome)

        val preservedHarvest = db.harvestDao().findById(harvestId)!!
        assertEquals(999999L, preservedHarvest.weightGrams)
        assertEquals(0, count("deliveries"))
        assertEquals(0, count("sync_outbox"))
    }
}
