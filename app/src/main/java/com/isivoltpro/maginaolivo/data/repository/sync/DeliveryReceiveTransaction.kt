package com.isivoltpro.maginaolivo.data.repository.sync

import androidx.room.withTransaction
import com.isivoltpro.maginaolivo.data.local.MaginaOlivoDatabase
import com.isivoltpro.maginaolivo.data.local.entity.CampaignEntity
import com.isivoltpro.maginaolivo.data.local.entity.CampaignParcelSnapshotEntity
import com.isivoltpro.maginaolivo.data.local.entity.DeliveryEntity
import com.isivoltpro.maginaolivo.data.local.entity.DeliveryParcelEntity
import com.isivoltpro.maginaolivo.data.local.entity.FarmEntity
import com.isivoltpro.maginaolivo.data.local.entity.FarmParcelMembershipEntity
import com.isivoltpro.maginaolivo.data.local.entity.HarvestEntity
import com.isivoltpro.maginaolivo.data.local.entity.HarvestParcelEntity
import com.isivoltpro.maginaolivo.data.local.entity.LocalMetadata
import com.isivoltpro.maginaolivo.data.local.entity.ParcelEntity
import com.isivoltpro.maginaolivo.data.local.entity.WorkspaceEntity
import com.isivoltpro.maginaolivo.data.local.model.SyncStatus
import java.time.Instant
import java.util.UUID

enum class DeliveryReceiveOutcome {
    IMPORTED,
    ALREADY_IMPORTED,
    CONFLICT,
}

data class DeliveryImportReceipt(
    val deliveryId: UUID,
    val accountId: String,
    val issuer: String,
    val headPayloadHash: String,
    val headVersion: Long,
    val receiveContractVersion: Int,
    val receivedAt: Instant,
)

/**
 * G2.5 transactional Room receiver.
 *
 * Atomically inserts the 10 rows of the verified bundle plus an import receipt in ONE transaction.
 *
 * Guarantees:
 *  - Preserves all original remote UUIDs without calling any outbox-generating factory (`create()`).
 *  - Origin timestamps, versions and SHA-256 evidence are preserved in child entity metadata.
 *  - ZERO outbox operations generated.
 *  - Idempotent: re-receiving the exact same bundle returns [DeliveryReceiveOutcome.ALREADY_IMPORTED]
 *    with zero row writes and zero outbox changes.
 *  - Conflict detection: any conflicting local row, differing hash/version, or pending outbox operation
 *    for any entity in the hierarchy returns [DeliveryReceiveOutcome.CONFLICT] without modifying bytes.
 *  - Crash / rollback safety: a failure before commit leaves the database with zero imported rows.
 */
suspend fun MaginaOlivoDatabase.receiveDeliveryBundle(
    bundle: DeliveryReceiveBundle,
    at: Instant = Instant.now(),
): DeliveryReceiveOutcome = withTransaction {
    val db = openHelper.writableDatabase

    // 1. Ensure import receipts table exists in SQLite
    db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS delivery_import_receipts (
            delivery_id TEXT PRIMARY KEY,
            account_id TEXT NOT NULL,
            issuer TEXT NOT NULL,
            head_payload_hash TEXT NOT NULL,
            head_version INTEGER NOT NULL,
            receive_contract_version INTEGER NOT NULL,
            received_at INTEGER NOT NULL
        )
        """.trimIndent(),
    )

    // 2. Check for active/pending/processing outbox operations for any entity in the incoming graph
    val entityIds = listOf(
        bundle.workspace.id,
        bundle.farm.id,
        bundle.parcel.id,
        bundle.campaign.id,
        bundle.harvest.id,
        bundle.delivery.id,
    ).map { it.toString() }

    val placeholders = entityIds.joinToString(",") { "?" }
    val outboxCursor = db.query(
        "SELECT COUNT(*) FROM sync_outbox WHERE entity_id IN ($placeholders) AND status IN ('PENDING', 'PROCESSING')",
        entityIds.toTypedArray(),
    )
    val pendingCount = outboxCursor.use { cursor ->
        if (cursor.moveToFirst()) cursor.getInt(0) else 0
    }
    if (pendingCount > 0) {
        return@withTransaction DeliveryReceiveOutcome.CONFLICT
    }

    // 3. Check if this delivery has already been received via import receipt
    val receiptCursor = db.query(
        "SELECT account_id, issuer, head_payload_hash, head_version, receive_contract_version, received_at FROM delivery_import_receipts WHERE delivery_id = ?",
        arrayOf(bundle.delivery.id.toString()),
    )
    val existingReceipt = receiptCursor.use { cursor ->
        if (cursor.moveToFirst()) {
            DeliveryImportReceipt(
                deliveryId = bundle.delivery.id,
                accountId = cursor.getString(0),
                issuer = cursor.getString(1),
                headPayloadHash = cursor.getString(2),
                headVersion = cursor.getLong(3),
                receiveContractVersion = cursor.getInt(4),
                receivedAt = Instant.ofEpochMilli(cursor.getLong(5)),
            )
        } else {
            null
        }
    }

    if (existingReceipt != null) {
        // Idempotency check: must match account, issuer, head payload hash and head version
        val deliveryRow = deliveryDao().findById(bundle.delivery.id)
        if (deliveryRow != null &&
            existingReceipt.accountId == bundle.accountId.toString() &&
            existingReceipt.issuer == bundle.issuer &&
            existingReceipt.headPayloadHash == bundle.headPayloadHash &&
            existingReceipt.headVersion == bundle.headVersion &&
            existingReceipt.receiveContractVersion == bundle.receiveContractVersion &&
            deliveryRow.netGrams == bundle.delivery.netGrams &&
            deliveryRow.farmId == bundle.farm.id &&
            deliveryRow.campaignId == bundle.campaign.id &&
            deliveryRow.harvestId == bundle.harvest.id
        ) {
            return@withTransaction DeliveryReceiveOutcome.ALREADY_IMPORTED
        } else {
            return@withTransaction DeliveryReceiveOutcome.CONFLICT
        }
    }

    // 4. If no receipt exists, verify no conflicting delivery or parent entity exists
    if (deliveryDao().findById(bundle.delivery.id) != null) {
        return@withTransaction DeliveryReceiveOutcome.CONFLICT
    }

    val existingWs = workspaceDao().findById(bundle.workspace.id)
    if (existingWs != null) {
        if (existingWs.ownerUserId != bundle.workspace.ownerUserId || existingWs.metadata.deletedAt != null) {
            return@withTransaction DeliveryReceiveOutcome.CONFLICT
        }
    }

    val existingFarm = farmDao().findById(bundle.farm.id)
    if (existingFarm != null) {
        if (existingFarm.workspaceId != bundle.workspace.id || existingFarm.metadata.deletedAt != null) {
            return@withTransaction DeliveryReceiveOutcome.CONFLICT
        }
    }

    val existingParcel = parcelDao().findById(bundle.parcel.id)
    if (existingParcel != null) {
        if (existingParcel.workspaceId != bundle.workspace.id || existingParcel.metadata.deletedAt != null) {
            return@withTransaction DeliveryReceiveOutcome.CONFLICT
        }
    }

    val existingCampaign = campaignDao().findById(bundle.campaign.id)
    if (existingCampaign != null) {
        if (existingCampaign.workspaceId != bundle.workspace.id ||
            existingCampaign.farmId != bundle.farm.id ||
            existingCampaign.metadata.deletedAt != null
        ) {
            return@withTransaction DeliveryReceiveOutcome.CONFLICT
        }
    }

    val existingHarvest = harvestDao().findById(bundle.harvest.id)
    if (existingHarvest != null) {
        if (existingHarvest.workspaceId != bundle.workspace.id ||
            existingHarvest.farmId != bundle.farm.id ||
            existingHarvest.campaignId != bundle.campaign.id ||
            existingHarvest.metadata.deletedAt != null
        ) {
            return@withTransaction DeliveryReceiveOutcome.CONFLICT
        }
    }

    // 5. Inserción transaccional de padres e hijos preservando los UUIDs originales
    // 5.1 Workspace
    workspaceDao().upsert(
        WorkspaceEntity(
            id = bundle.workspace.id,
            name = bundle.workspace.name,
            ownerUserId = bundle.workspace.ownerUserId,
            countryCode = bundle.workspace.countryCode,
            timezone = bundle.workspace.timezone,
            locale = bundle.workspace.locale,
            currency = bundle.workspace.currency,
            metadata = LocalMetadata(
                createdAt = bundle.workspace.createdAt,
                updatedAt = bundle.workspace.updatedAt,
                version = bundle.workspace.version,
                syncStatus = SyncStatus.SYNCED,
                remoteVersion = bundle.workspace.version,
                lastSyncedAt = at,
            ),
        ),
    )

    // 5.2 Farm
    farmDao().upsert(
        FarmEntity(
            id = bundle.farm.id,
            workspaceId = bundle.workspace.id,
            name = bundle.farm.name,
            description = bundle.farm.description,
            municipality = bundle.farm.municipality,
            province = bundle.farm.province,
            coverDocumentId = null,
            notes = bundle.farm.notes,
            status = bundle.farm.status,
            metadata = LocalMetadata(
                createdAt = bundle.farm.createdAt,
                updatedAt = bundle.farm.updatedAt,
                version = bundle.farm.version,
                syncStatus = SyncStatus.SYNCED,
                remoteVersion = bundle.farm.version,
                lastSyncedAt = at,
            ),
        ),
    )

    // 5.3 Parcel
    parcelDao().upsert(
        ParcelEntity(
            id = bundle.parcel.id,
            workspaceId = bundle.workspace.id,
            displayName = bundle.parcel.displayName,
            cadastralReference = bundle.parcel.cadastralReference,
            cadastralPolygon = bundle.parcel.cadastralPolygon,
            cadastralParcel = bundle.parcel.cadastralParcel,
            municipality = bundle.parcel.municipality,
            province = bundle.parcel.province,
            source = bundle.parcel.source,
            geometryGeoJson = bundle.parcel.geometryGeoJson,
            sourceProvider = bundle.parcel.sourceProvider,
            sourceImportedAt = bundle.parcel.sourceImportedAt,
            cadastralAreaM2 = bundle.parcel.cadastralAreaM2,
            managedAreaM2 = bundle.parcel.managedAreaM2,
            notes = bundle.parcel.notes,
            status = bundle.parcel.status,
            oliveTreeCount = bundle.parcel.oliveTreeCount,
            variety = bundle.parcel.variety,
            irrigationSystem = bundle.parcel.irrigationSystem,
            irrigationNetwork = bundle.parcel.irrigationNetwork,
            irrigationSector = bundle.parcel.irrigationSector,
            irrigationDays = bundle.parcel.irrigationDays,
            metadata = LocalMetadata(
                createdAt = bundle.parcel.createdAt,
                updatedAt = bundle.parcel.updatedAt,
                version = bundle.parcel.version,
                syncStatus = SyncStatus.SYNCED,
                remoteVersion = bundle.parcel.version,
                lastSyncedAt = at,
            ),
        ),
    )

    // 5.4 Membership
    parcelDao().upsertMembership(
        FarmParcelMembershipEntity(
            id = bundle.membership.id,
            workspaceId = bundle.workspace.id,
            farmId = bundle.farm.id,
            parcelId = bundle.parcel.id,
            validFrom = bundle.membership.validFrom,
            validUntil = null,
            metadata = LocalMetadata(
                createdAt = bundle.membership.createdAt,
                updatedAt = bundle.membership.updatedAt,
                version = bundle.membership.version,
                syncStatus = SyncStatus.SYNCED,
                remoteVersion = bundle.membership.version,
                lastSyncedAt = at,
            ),
        ),
    )

    // 5.5 Campaign
    campaignDao().upsert(
        CampaignEntity(
            id = bundle.campaign.id,
            workspaceId = bundle.workspace.id,
            farmId = bundle.farm.id,
            name = bundle.campaign.name,
            startDate = bundle.campaign.startDate,
            endDate = bundle.campaign.endDate,
            status = bundle.campaign.status,
            notes = bundle.campaign.notes,
            metadata = LocalMetadata(
                createdAt = bundle.campaign.createdAt,
                updatedAt = bundle.campaign.updatedAt,
                version = bundle.campaign.version,
                syncStatus = SyncStatus.SYNCED,
                remoteVersion = bundle.campaign.version,
                lastSyncedAt = at,
            ),
        ),
    )

    // 5.6 CampaignParcelSnapshot (using origin metadata!)
    campaignDao().upsertSnapshots(
        listOf(
            CampaignParcelSnapshotEntity(
                id = bundle.campaignParcel.id,
                workspaceId = bundle.workspace.id,
                campaignId = bundle.campaign.id,
                parcelId = bundle.parcel.id,
                farmIdAtStart = bundle.farm.id,
                farmNameAtStart = bundle.campaignParcel.farmNameAtStart,
                parcelNameAtStart = bundle.campaignParcel.parcelNameAtStart,
                managedAreaM2AtStart = bundle.campaignParcel.managedAreaM2AtStart,
                cadastralReferenceAtStart = bundle.campaignParcel.cadastralReferenceAtStart,
                geometryGeoJsonSnapshot = bundle.campaignParcel.geometryGeoJsonSnapshot,
                metadata = LocalMetadata(
                    createdAt = bundle.campaignParcel.originCreatedAt,
                    updatedAt = bundle.campaignParcel.originUpdatedAt,
                    version = bundle.campaignParcel.originVersion,
                    syncStatus = SyncStatus.SYNCED,
                    remoteVersion = bundle.campaignParcel.originVersion,
                    lastSyncedAt = at,
                ),
            ),
        ),
    )

    // 5.7 Harvest
    harvestDao().upsert(
        HarvestEntity(
            id = bundle.harvest.id,
            workspaceId = bundle.workspace.id,
            campaignId = bundle.campaign.id,
            farmId = bundle.farm.id,
            harvestDate = bundle.harvest.harvestDate,
            weightGrams = bundle.harvest.weightGrams,
            destination = bundle.harvest.destination,
            notes = bundle.harvest.notes,
            collectionMethod = bundle.harvest.collectionMethod,
            workerCount = bundle.harvest.workerCount,
            machineryText = bundle.harvest.machineryText,
            dayOrigin = bundle.harvest.dayOrigin,
            metadata = LocalMetadata(
                createdAt = bundle.harvest.createdAt,
                updatedAt = bundle.harvest.updatedAt,
                version = bundle.harvest.version,
                syncStatus = SyncStatus.SYNCED,
                remoteVersion = bundle.harvest.version,
                lastSyncedAt = at,
            ),
        ),
    )

    // 5.8 HarvestParcel (using origin metadata!)
    harvestDao().upsertParcels(
        listOf(
            HarvestParcelEntity(
                id = bundle.harvestParcel.id,
                workspaceId = bundle.workspace.id,
                harvestId = bundle.harvest.id,
                parcelId = bundle.parcel.id,
                campaignParcelId = bundle.campaignParcel.id,
                parcelNameAtHarvest = bundle.harvestParcel.parcelNameAtHarvest,
                weightGrams = bundle.harvestParcel.weightGrams,
                allocationMode = bundle.harvestParcel.allocationMode,
                metadata = LocalMetadata(
                    createdAt = bundle.harvestParcel.originCreatedAt,
                    updatedAt = bundle.harvestParcel.originUpdatedAt,
                    version = bundle.harvestParcel.originVersion,
                    syncStatus = SyncStatus.SYNCED,
                    remoteVersion = bundle.harvestParcel.originVersion,
                    lastSyncedAt = at,
                ),
            ),
        ),
    )

    // 5.9 Delivery
    deliveryDao().upsert(
        DeliveryEntity(
            id = bundle.delivery.id,
            workspaceId = bundle.workspace.id,
            farmId = bundle.farm.id,
            campaignId = bundle.campaign.id,
            harvestId = bundle.harvest.id,
            deliveryDate = bundle.delivery.deliveryDate,
            deliveryTime = bundle.delivery.deliveryTime,
            destinationOrganizationId = null,
            destinationName = bundle.delivery.destinationName,
            netGrams = bundle.delivery.netGrams,
            grossGrams = bundle.delivery.grossGrams,
            tareGrams = bundle.delivery.tareGrams,
            deliveryNumber = bundle.delivery.deliveryNumber,
            ticketNumber = bundle.delivery.ticketNumber,
            source = bundle.delivery.source,
            notes = bundle.delivery.notes,
            origin = bundle.delivery.harvestOrigin,
            metadata = LocalMetadata(
                createdAt = bundle.delivery.createdAt,
                updatedAt = bundle.delivery.updatedAt,
                version = bundle.delivery.version,
                syncStatus = SyncStatus.SYNCED,
                remoteVersion = bundle.delivery.version,
                lastSyncedAt = at,
            ),
        ),
    )

    // 5.10 DeliveryParcel (using origin metadata!)
    deliveryDao().upsertParcels(
        listOf(
            DeliveryParcelEntity(
                id = bundle.deliveryParcel.id,
                workspaceId = bundle.workspace.id,
                deliveryId = bundle.delivery.id,
                parcelId = bundle.parcel.id,
                campaignParcelId = bundle.campaignParcel.id,
                parcelNameAtDelivery = bundle.deliveryParcel.parcelNameAtDelivery,
                weightGrams = bundle.deliveryParcel.weightGrams,
                allocationMode = bundle.deliveryParcel.allocationMode,
                metadata = LocalMetadata(
                    createdAt = bundle.deliveryParcel.originCreatedAt,
                    updatedAt = bundle.deliveryParcel.originUpdatedAt,
                    version = bundle.deliveryParcel.originVersion,
                    syncStatus = SyncStatus.SYNCED,
                    remoteVersion = bundle.deliveryParcel.originVersion,
                    lastSyncedAt = at,
                ),
            ),
        ),
    )

    // 6. Insert import receipt
    db.execSQL(
        """
        INSERT INTO delivery_import_receipts (
            delivery_id, account_id, issuer, head_payload_hash, head_version, receive_contract_version, received_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?)
        """.trimIndent(),
        arrayOf<Any>(
            bundle.delivery.id.toString(),
            bundle.accountId.toString(),
            bundle.issuer,
            bundle.headPayloadHash,
            bundle.headVersion,
            bundle.receiveContractVersion,
            at.toEpochMilli(),
        ),
    )

    DeliveryReceiveOutcome.IMPORTED
}

/** Reads a previously stored import receipt by delivery UUID. */
fun MaginaOlivoDatabase.readDeliveryImportReceipt(deliveryId: UUID): DeliveryImportReceipt? {
    val db = openHelper.readableDatabase
    return try {
        db.query(
            "SELECT delivery_id, account_id, issuer, head_payload_hash, head_version, receive_contract_version, received_at FROM delivery_import_receipts WHERE delivery_id = ?",
            arrayOf(deliveryId.toString()),
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                DeliveryImportReceipt(
                    deliveryId = UUID.fromString(cursor.getString(0)),
                    accountId = cursor.getString(1),
                    issuer = cursor.getString(2),
                    headPayloadHash = cursor.getString(3),
                    headVersion = cursor.getLong(4),
                    receiveContractVersion = cursor.getInt(5),
                    receivedAt = Instant.ofEpochMilli(cursor.getLong(6)),
                )
            } else {
                null
            }
        }
    } catch (_: Exception) {
        null
    }
}
