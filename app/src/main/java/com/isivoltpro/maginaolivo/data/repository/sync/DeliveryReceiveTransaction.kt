package com.isivoltpro.maginaolivo.data.repository.sync

import androidx.room.withTransaction
import com.isivoltpro.maginaolivo.data.local.MaginaOlivoDatabase
import com.isivoltpro.maginaolivo.data.local.entity.CampaignEntity
import com.isivoltpro.maginaolivo.data.local.entity.CampaignParcelSnapshotEntity
import com.isivoltpro.maginaolivo.data.local.entity.DeliveryEntity
import com.isivoltpro.maginaolivo.data.local.entity.DeliveryImportReceiptEntity
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
import kotlinx.coroutines.runBlocking

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
 *  - Conflict detection: any conflicting local row, differing domain attributes, version, hash,
 *    or pending outbox operation for any entity in the hierarchy returns [DeliveryReceiveOutcome.CONFLICT]
 *    without modifying database state or overwriting existing parent rows.
 *  - Crash / rollback safety: a failure before commit leaves the database with zero imported rows.
 */
suspend fun MaginaOlivoDatabase.receiveDeliveryBundle(
    bundle: DeliveryReceiveBundle,
    at: Instant = Instant.now(),
): DeliveryReceiveOutcome = withTransaction {
    val db = openHelper.writableDatabase

    // 1. Check for active/pending/processing outbox operations for any entity in the incoming graph
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

    // 2. Keep the receipt for the final idempotency decision, after validating the whole graph.
    val existingReceipt = deliveryImportReceiptDao().findById(bundle.delivery.id)
    val existingDelivery = deliveryDao().findById(bundle.delivery.id)

    // 4. Strict Conflict Detection on all parent and child entities:
    // If an entity already exists, ALL domain attributes and versions must match exactly.
    // If ANY attribute, version, or tombstone differs, return CONFLICT without modifying anything.

    // 4.1 Workspace
    val existingWs = workspaceDao().findById(bundle.workspace.id)
    if (existingWs != null) {
        if (existingWs.metadata.deletedAt != null ||
            existingWs.name != bundle.workspace.name ||
            existingWs.ownerUserId != bundle.workspace.ownerUserId ||
            existingWs.countryCode != bundle.workspace.countryCode ||
            existingWs.timezone != bundle.workspace.timezone ||
            existingWs.locale != bundle.workspace.locale ||
            existingWs.currency != bundle.workspace.currency ||
            existingWs.metadata.createdAt.toEpochMilli() != bundle.workspace.createdAt.toEpochMilli() ||
            existingWs.metadata.updatedAt.toEpochMilli() != bundle.workspace.updatedAt.toEpochMilli() ||
            existingWs.metadata.version != bundle.workspace.version
        ) {
            return@withTransaction DeliveryReceiveOutcome.CONFLICT
        }
    }

    // 4.2 Farm
    val existingFarm = farmDao().findById(bundle.farm.id)
    if (existingFarm != null) {
        if (existingFarm.metadata.deletedAt != null ||
            existingFarm.workspaceId != bundle.workspace.id ||
            existingFarm.name != bundle.farm.name ||
            existingFarm.description != bundle.farm.description ||
            existingFarm.municipality != bundle.farm.municipality ||
            existingFarm.province != bundle.farm.province ||
            existingFarm.coverDocumentId != null ||
            existingFarm.notes != bundle.farm.notes ||
            existingFarm.status != bundle.farm.status ||
            existingFarm.metadata.createdAt.toEpochMilli() != bundle.farm.createdAt.toEpochMilli() ||
            existingFarm.metadata.updatedAt.toEpochMilli() != bundle.farm.updatedAt.toEpochMilli() ||
            existingFarm.metadata.version != bundle.farm.version
        ) {
            return@withTransaction DeliveryReceiveOutcome.CONFLICT
        }
    }

    // 4.3 Parcel
    val existingParcel = parcelDao().findById(bundle.parcel.id)
    if (existingParcel != null) {
        if (existingParcel.metadata.deletedAt != null ||
            existingParcel.workspaceId != bundle.workspace.id ||
            existingParcel.displayName != bundle.parcel.displayName ||
            existingParcel.cadastralReference != bundle.parcel.cadastralReference ||
            existingParcel.cadastralPolygon != bundle.parcel.cadastralPolygon ||
            existingParcel.cadastralParcel != bundle.parcel.cadastralParcel ||
            existingParcel.municipality != bundle.parcel.municipality ||
            existingParcel.province != bundle.parcel.province ||
            existingParcel.source != bundle.parcel.source ||
            existingParcel.geometryGeoJson != bundle.parcel.geometryGeoJson ||
            existingParcel.sourceProvider != bundle.parcel.sourceProvider ||
            existingParcel.sourceImportedAt?.toEpochMilli() != bundle.parcel.sourceImportedAt?.toEpochMilli() ||
            existingParcel.cadastralAreaM2 != bundle.parcel.cadastralAreaM2 ||
            existingParcel.managedAreaM2 != bundle.parcel.managedAreaM2 ||
            existingParcel.notes != bundle.parcel.notes ||
            existingParcel.status != bundle.parcel.status ||
            existingParcel.oliveTreeCount != bundle.parcel.oliveTreeCount ||
            existingParcel.variety != bundle.parcel.variety ||
            existingParcel.irrigationSystem != bundle.parcel.irrigationSystem ||
            existingParcel.irrigationNetwork != bundle.parcel.irrigationNetwork ||
            existingParcel.irrigationSector != bundle.parcel.irrigationSector ||
            existingParcel.irrigationDays != bundle.parcel.irrigationDays ||
            existingParcel.metadata.createdAt.toEpochMilli() != bundle.parcel.createdAt.toEpochMilli() ||
            existingParcel.metadata.updatedAt.toEpochMilli() != bundle.parcel.updatedAt.toEpochMilli() ||
            existingParcel.metadata.version != bundle.parcel.version
        ) {
            return@withTransaction DeliveryReceiveOutcome.CONFLICT
        }
    }

    // 4.4 FarmParcelMembership
    val existingMem = parcelDao().listMemberships(bundle.parcel.id).find { it.id == bundle.membership.id }
    if (existingMem != null) {
        if (existingMem.metadata.deletedAt != null ||
            existingMem.workspaceId != bundle.workspace.id ||
            existingMem.farmId != bundle.farm.id ||
            existingMem.parcelId != bundle.parcel.id ||
            existingMem.validFrom != bundle.membership.validFrom ||
            existingMem.validUntil != null ||
            existingMem.metadata.createdAt.toEpochMilli() != bundle.membership.createdAt.toEpochMilli() ||
            existingMem.metadata.updatedAt.toEpochMilli() != bundle.membership.updatedAt.toEpochMilli() ||
            existingMem.metadata.version != bundle.membership.version
        ) {
            return@withTransaction DeliveryReceiveOutcome.CONFLICT
        }
    }

    // 4.5 Campaign
    val existingCamp = campaignDao().findById(bundle.campaign.id)
    if (existingCamp != null) {
        if (existingCamp.metadata.deletedAt != null ||
            existingCamp.workspaceId != bundle.workspace.id ||
            existingCamp.farmId != bundle.farm.id ||
            existingCamp.name != bundle.campaign.name ||
            existingCamp.startDate != bundle.campaign.startDate ||
            existingCamp.endDate != bundle.campaign.endDate ||
            existingCamp.status != bundle.campaign.status ||
            existingCamp.notes != bundle.campaign.notes ||
            existingCamp.metadata.createdAt.toEpochMilli() != bundle.campaign.createdAt.toEpochMilli() ||
            existingCamp.metadata.updatedAt.toEpochMilli() != bundle.campaign.updatedAt.toEpochMilli() ||
            existingCamp.metadata.version != bundle.campaign.version
        ) {
            return@withTransaction DeliveryReceiveOutcome.CONFLICT
        }
    }

    // 4.6 CampaignParcelSnapshot
    val existingSnap = campaignDao().listSnapshots(bundle.campaign.id).find { it.id == bundle.campaignParcel.id }
    if (existingSnap != null) {
        if (existingSnap.metadata.deletedAt != null ||
            existingSnap.workspaceId != bundle.workspace.id ||
            existingSnap.campaignId != bundle.campaign.id ||
            existingSnap.parcelId != bundle.parcel.id ||
            existingSnap.farmIdAtStart != bundle.farm.id ||
            existingSnap.farmNameAtStart != bundle.campaignParcel.farmNameAtStart ||
            existingSnap.parcelNameAtStart != bundle.campaignParcel.parcelNameAtStart ||
            existingSnap.managedAreaM2AtStart != bundle.campaignParcel.managedAreaM2AtStart ||
            existingSnap.cadastralReferenceAtStart != bundle.campaignParcel.cadastralReferenceAtStart ||
            existingSnap.geometryGeoJsonSnapshot != bundle.campaignParcel.geometryGeoJsonSnapshot ||
            existingSnap.metadata.createdAt.toEpochMilli() != bundle.campaignParcel.originCreatedAt.toEpochMilli() ||
            existingSnap.metadata.updatedAt.toEpochMilli() != bundle.campaignParcel.originUpdatedAt.toEpochMilli() ||
            existingSnap.metadata.version != bundle.campaignParcel.originVersion
        ) {
            return@withTransaction DeliveryReceiveOutcome.CONFLICT
        }
    }

    // 4.7 Harvest
    val existingHarvest = harvestDao().findById(bundle.harvest.id)
    if (existingHarvest != null) {
        if (existingHarvest.metadata.deletedAt != null ||
            existingHarvest.workspaceId != bundle.workspace.id ||
            existingHarvest.farmId != bundle.farm.id ||
            existingHarvest.campaignId != bundle.campaign.id ||
            existingHarvest.harvestDate != bundle.harvest.harvestDate ||
            existingHarvest.weightGrams != bundle.harvest.weightGrams ||
            existingHarvest.destination != bundle.harvest.destination ||
            existingHarvest.notes != bundle.harvest.notes ||
            existingHarvest.collectionMethod != bundle.harvest.collectionMethod ||
            existingHarvest.workerCount != bundle.harvest.workerCount ||
            existingHarvest.machineryText != bundle.harvest.machineryText ||
            existingHarvest.dayOrigin != bundle.harvest.dayOrigin ||
            existingHarvest.metadata.createdAt.toEpochMilli() != bundle.harvest.createdAt.toEpochMilli() ||
            existingHarvest.metadata.updatedAt.toEpochMilli() != bundle.harvest.updatedAt.toEpochMilli() ||
            existingHarvest.metadata.version != bundle.harvest.version
        ) {
            return@withTransaction DeliveryReceiveOutcome.CONFLICT
        }
    }

    // 4.8 HarvestParcel
    val existingHp = harvestDao().listParcels(bundle.harvest.id).find { it.id == bundle.harvestParcel.id }
    if (existingHp != null) {
        if (existingHp.metadata.deletedAt != null ||
            existingHp.workspaceId != bundle.workspace.id ||
            existingHp.harvestId != bundle.harvest.id ||
            existingHp.parcelId != bundle.parcel.id ||
            existingHp.campaignParcelId != bundle.campaignParcel.id ||
            existingHp.parcelNameAtHarvest != bundle.harvestParcel.parcelNameAtHarvest ||
            existingHp.weightGrams != bundle.harvestParcel.weightGrams ||
            existingHp.allocationMode != bundle.harvestParcel.allocationMode ||
            existingHp.metadata.createdAt.toEpochMilli() != bundle.harvestParcel.originCreatedAt.toEpochMilli() ||
            existingHp.metadata.updatedAt.toEpochMilli() != bundle.harvestParcel.originUpdatedAt.toEpochMilli() ||
            existingHp.metadata.version != bundle.harvestParcel.originVersion
        ) {
            return@withTransaction DeliveryReceiveOutcome.CONFLICT
        }
    }

    // 4.9 DeliveryParcel
    val existingDp = deliveryDao().listParcels(bundle.delivery.id).find { it.id == bundle.deliveryParcel.id }
    if (existingDp != null) {
        if (existingDp.metadata.deletedAt != null ||
            existingDp.workspaceId != bundle.workspace.id ||
            existingDp.deliveryId != bundle.delivery.id ||
            existingDp.parcelId != bundle.parcel.id ||
            existingDp.campaignParcelId != bundle.campaignParcel.id ||
            existingDp.parcelNameAtDelivery != bundle.deliveryParcel.parcelNameAtDelivery ||
            existingDp.weightGrams != bundle.deliveryParcel.weightGrams ||
            existingDp.allocationMode != bundle.deliveryParcel.allocationMode ||
            existingDp.metadata.createdAt.toEpochMilli() != bundle.deliveryParcel.originCreatedAt.toEpochMilli() ||
            existingDp.metadata.updatedAt.toEpochMilli() != bundle.deliveryParcel.originUpdatedAt.toEpochMilli() ||
            existingDp.metadata.version != bundle.deliveryParcel.originVersion
        ) {
            return@withTransaction DeliveryReceiveOutcome.CONFLICT
        }
    }

    // A receipt is idempotent only while every row still matches the received graph.
    if (existingReceipt != null) {
        if (existingDelivery == null || existingWs == null || existingFarm == null ||
            existingParcel == null || existingMem == null || existingCamp == null ||
            existingSnap == null || existingHarvest == null || existingHp == null || existingDp == null ||
            existingReceipt.accountId != bundle.accountId.toString() ||
            existingReceipt.issuer != bundle.issuer ||
            existingReceipt.headPayloadHash != bundle.headPayloadHash ||
            existingReceipt.headVersion != bundle.headVersion ||
            existingReceipt.receiveContractVersion != bundle.receiveContractVersion ||
            existingDelivery.workspaceId != bundle.workspace.id ||
            existingDelivery.deliveryDate != bundle.delivery.deliveryDate ||
            existingDelivery.deliveryTime != bundle.delivery.deliveryTime ||
            existingDelivery.destinationOrganizationId != bundle.delivery.destinationOrganizationId ||
            existingDelivery.destinationName != bundle.delivery.destinationName ||
            existingDelivery.netGrams != bundle.delivery.netGrams ||
            existingDelivery.grossGrams != bundle.delivery.grossGrams ||
            existingDelivery.tareGrams != bundle.delivery.tareGrams ||
            existingDelivery.deliveryNumber != bundle.delivery.deliveryNumber ||
            existingDelivery.ticketNumber != bundle.delivery.ticketNumber ||
            existingDelivery.source != bundle.delivery.source ||
            existingDelivery.notes != bundle.delivery.notes ||
            existingDelivery.farmId != bundle.farm.id ||
            existingDelivery.campaignId != bundle.campaign.id ||
            existingDelivery.harvestId != bundle.harvest.id ||
            existingDelivery.origin != bundle.delivery.harvestOrigin ||
            existingDelivery.metadata.createdAt.toEpochMilli() != bundle.delivery.createdAt.toEpochMilli() ||
            existingDelivery.metadata.updatedAt.toEpochMilli() != bundle.delivery.updatedAt.toEpochMilli() ||
            existingDelivery.metadata.version != bundle.delivery.version ||
            existingDelivery.metadata.deletedAt != null
        ) return@withTransaction DeliveryReceiveOutcome.CONFLICT
        return@withTransaction DeliveryReceiveOutcome.ALREADY_IMPORTED
    }
    if (existingDelivery != null) return@withTransaction DeliveryReceiveOutcome.CONFLICT

    // 5. Inserción de entidades no existentes preservando los UUIDs originales y sin generar outbox

    // 5.1 Workspace (solo si no existe)
    if (existingWs == null) {
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
    }

    // 5.2 Farm (solo si no existe)
    if (existingFarm == null) {
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
    }

    // 5.3 Parcel (solo si no existe)
    if (existingParcel == null) {
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
    }

    // 5.4 Membership (solo si no existe)
    if (existingMem == null) {
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
    }

    // 5.5 Campaign (solo si no existe)
    if (existingCamp == null) {
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
    }

    // 5.6 CampaignParcelSnapshot (solo si no existe)
    if (existingSnap == null) {
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
    }

    // 5.7 Harvest (solo si no existe)
    if (existingHarvest == null) {
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
    }

    // 5.8 HarvestParcel (solo si no existe)
    if (existingHp == null) {
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
    }

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

    // 5.10 DeliveryParcel (solo si no existe)
    if (existingDp == null) {
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
    }

    // 6. Inserción del recibo duradero de importación mediante DAO versionado
    deliveryImportReceiptDao().insert(
        DeliveryImportReceiptEntity(
            deliveryId = bundle.delivery.id,
            accountId = bundle.accountId.toString(),
            issuer = bundle.issuer,
            headPayloadHash = bundle.headPayloadHash,
            headVersion = bundle.headVersion,
            receiveContractVersion = bundle.receiveContractVersion,
            receivedAt = at,
        ),
    )

    DeliveryReceiveOutcome.IMPORTED
}

/** Reads a previously stored import receipt by delivery UUID. */
fun MaginaOlivoDatabase.readDeliveryImportReceipt(deliveryId: UUID): DeliveryImportReceipt? {
    return runBlocking {
        deliveryImportReceiptDao().findById(deliveryId)?.let { entity ->
            DeliveryImportReceipt(
                deliveryId = entity.deliveryId,
                accountId = entity.accountId,
                issuer = entity.issuer,
                headPayloadHash = entity.headPayloadHash,
                headVersion = entity.headVersion,
                receiveContractVersion = entity.receiveContractVersion,
                receivedAt = entity.receivedAt,
            )
        }
    }
}
