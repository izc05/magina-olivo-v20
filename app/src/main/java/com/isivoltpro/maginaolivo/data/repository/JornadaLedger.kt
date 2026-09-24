package com.isivoltpro.maginaolivo.data.repository

import com.isivoltpro.maginaolivo.core.id.IdGenerator
import com.isivoltpro.maginaolivo.data.local.MaginaOlivoDatabase
import com.isivoltpro.maginaolivo.data.local.entity.HarvestEntity
import com.isivoltpro.maginaolivo.data.local.entity.HarvestParcelEntity
import com.isivoltpro.maginaolivo.data.local.entity.LocalMetadata
import com.isivoltpro.maginaolivo.data.local.model.OutboxOperation
import com.isivoltpro.maginaolivo.data.local.model.SyncEntityType
import com.isivoltpro.maginaolivo.data.local.model.SyncStatus
import com.isivoltpro.maginaolivo.domain.harvest.HarvestAllocation
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Phase 19B (CR-005 §7): the reconciliation between a Jornada (Harvest) and its Pesadas
 * (Deliveries). Once a Jornada has linked Pesadas, its kilos are the exact sum of them, so
 * the farmer never types the same kilograms twice and the two totals cannot disagree.
 *
 * The Pesada stays the canonical weighing: nothing here rewrites a Delivery's kilos, ticket,
 * cooperative or yield. Must run inside the caller's transaction.
 */
internal class JornadaLedger(
    private val database: MaginaOlivoDatabase,
    private val idGenerator: IdGenerator,
) {
    /**
     * The Jornada a Pesada may join: live, of the same Farm and Campaign, not after the Pesada,
     * and without an exact split among several Parcels (it could no longer add up once the
     * total follows the Pesadas).
     */
    suspend fun checkLink(harvestId: UUID, farmId: UUID, campaignId: UUID, deliveryDate: LocalDate): HarvestEntity {
        val harvest = database.harvestDao().findById(harvestId)?.takeIf { it.metadata.deletedAt == null }
            ?: throw InvalidDelivery("harvestId", "not_found")
        if (harvest.farmId != farmId || harvest.campaignId != campaignId) throw InvalidDelivery("harvestId", "other_campaign")
        if (deliveryDate.isBefore(harvest.harvestDate)) throw InvalidDelivery("harvestId", "before_jornada")
        // A single Parcel carries the whole total (a fact, not a split) and follows it.
        val parcels = database.harvestDao().listParcels(harvestId)
        if (parcels.size > 1 && parcels.any { it.allocationMode == HarvestAllocation.EXACT.name }) throw InvalidDelivery("harvestId", "exact_split")
        return harvest
    }

    /**
     * Opens a Jornada for one Pesada's day. Its Parcels are the Pesada's origin Parcels, or
     * the whole Farm when none were chosen, and their split is unknown: no kilos are
     * attributed to a Parcel. Its kilos are set by [reconcile] right after.
     */
    suspend fun open(
        workspaceId: UUID,
        farmId: UUID,
        campaignId: UUID,
        date: LocalDate,
        parcelIds: List<UUID>,
        firstGrams: Long,
        now: Instant,
    ): UUID {
        val campaignParcels = database.harvestDao().listCampaignParcels(campaignId)
        val chosen = if (parcelIds.isEmpty()) campaignParcels else campaignParcels.filter { it.parcelId in parcelIds }
        if (chosen.isEmpty()) throw InvalidDelivery("harvestId", "no_parcels")
        val id = idGenerator.newId()
        database.harvestDao().upsert(
            HarvestEntity(
                id = id,
                workspaceId = workspaceId,
                campaignId = campaignId,
                farmId = farmId,
                harvestDate = date,
                weightGrams = firstGrams,
                metadata = LocalMetadata(now, now, syncStatus = SyncStatus.PENDING),
            ),
        )
        database.harvestDao().upsertParcels(
            chosen.sortedBy { it.parcelId.toString() }.map { parcel ->
                HarvestParcelEntity(
                    id = idGenerator.newId(),
                    workspaceId = workspaceId,
                    harvestId = id,
                    parcelId = parcel.parcelId,
                    campaignParcelId = parcel.campaignParcelId,
                    parcelNameAtHarvest = parcel.name,
                    weightGrams = null,
                    allocationMode = HarvestAllocation.UNALLOCATED.name,
                    metadata = LocalMetadata(now, now, syncStatus = SyncStatus.PENDING),
                )
            },
        )
        database.enqueueCollapsed(idGenerator, SyncEntityType.HARVEST, id, OutboxOperation.CREATE, now)
        return id
    }

    /**
     * Sets the Jornada's kilos to the sum of its live Pesadas. When its last Pesada leaves,
     * the Jornada keeps its kilos as its own figure, editable again; they are never zeroed.
     */
    suspend fun reconcile(harvestId: UUID?, now: Instant) {
        if (harvestId == null) return
        val harvest = database.harvestDao().findById(harvestId)?.takeIf { it.metadata.deletedAt == null } ?: return
        val linked = database.deliveryDao().listLiveForHarvest(harvestId)
        if (linked.isEmpty()) return
        val sum = linked.sumOf { it.netGrams }
        if (sum == harvest.weightGrams) return
        database.harvestDao().upsert(
            harvest.copy(
                weightGrams = sum,
                metadata = harvest.metadata.copy(
                    updatedAt = now,
                    version = harvest.metadata.version + 1,
                    syncStatus = SyncStatus.PENDING,
                ),
            ),
        )
        val parcels = database.harvestDao().listParcels(harvestId)
        parcels.singleOrNull()?.takeIf { it.allocationMode == HarvestAllocation.EXACT.name }?.let { only ->
            database.harvestDao().upsertParcels(listOf(only.copy(weightGrams = sum)))
        }
        database.enqueueCollapsed(idGenerator, SyncEntityType.HARVEST, harvestId, OutboxOperation.UPDATE, now)
    }

    /** A removed Jornada releases its Pesadas: they stay, unlinked, with every figure intact. */
    suspend fun release(harvestId: UUID, now: Instant) {
        database.deliveryDao().listLiveForHarvest(harvestId).forEach { delivery ->
            database.deliveryDao().upsert(
                delivery.copy(
                    harvestId = null,
                    metadata = delivery.metadata.copy(
                        updatedAt = now,
                        version = delivery.metadata.version + 1,
                        syncStatus = SyncStatus.PENDING,
                    ),
                ),
            )
            database.enqueueCollapsed(idGenerator, SyncEntityType.DELIVERY, delivery.id, OutboxOperation.UPDATE, now)
        }
    }
}
