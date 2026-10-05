package com.isivoltpro.maginaolivo.data.repository

import com.isivoltpro.maginaolivo.core.id.IdGenerator
import com.isivoltpro.maginaolivo.data.local.MaginaOlivoDatabase
import com.isivoltpro.maginaolivo.data.local.entity.DeliveryEntity
import com.isivoltpro.maginaolivo.data.local.entity.HarvestEntity
import com.isivoltpro.maginaolivo.data.local.entity.HarvestParcelEntity
import com.isivoltpro.maginaolivo.data.local.entity.LocalMetadata
import com.isivoltpro.maginaolivo.data.local.model.OutboxOperation
import com.isivoltpro.maginaolivo.data.local.model.SyncEntityType
import com.isivoltpro.maginaolivo.data.local.model.SyncStatus
import com.isivoltpro.maginaolivo.domain.attachment.AttachmentOwnerType
import com.isivoltpro.maginaolivo.domain.harvest.AUTO_DAY
import com.isivoltpro.maginaolivo.domain.harvest.HarvestAllocation
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Phase 19B (CR-005 §7) and CR-010: the reconciliation between a day of recolección (Harvest)
 * and its Pesadas (Deliveries). Once a day has linked Pesadas, its kilos are the exact sum of
 * them, so the farmer never types the same kilograms twice and the two totals cannot disagree.
 *
 * The Pesada stays the canonical weighing: nothing here rewrites a Delivery's kilos, ticket,
 * cooperative or yield. Must run inside the caller's transaction.
 */
internal class JornadaLedger(
    private val database: MaginaOlivoDatabase,
    private val idGenerator: IdGenerator,
) {
    /**
     * CR-010 (§4, note 3): the automatic day a Pesada of [date] belongs to — the oldest live one
     * of this Farm and Campaign, else a new one. A Jornada recorded by hand is never reused.
     */
    suspend fun autoDay(workspaceId: UUID, farmId: UUID, campaignId: UUID, date: LocalDate, now: Instant): UUID =
        database.harvestDao().findAutoDay(farmId, campaignId, date)?.id
            ?: open(workspaceId, farmId, campaignId, date, now)

    /**
     * Creates an automatic day. Stored 0 is "not weighed yet" ([Harvest.awaitingPesadas]); until
     * its Pesadas say otherwise its origin is the whole Farm, with an unknown split: no kilos are
     * attributed to a Parcel. [reconcile] sets its kilos and Parcels from its Pesadas.
     */
    suspend fun open(workspaceId: UUID, farmId: UUID, campaignId: UUID, date: LocalDate, now: Instant): UUID {
        val id = idGenerator.newId()
        database.harvestDao().upsert(
            HarvestEntity(
                id = id,
                workspaceId = workspaceId,
                campaignId = campaignId,
                farmId = farmId,
                harvestDate = date,
                weightGrams = 0L,
                metadata = LocalMetadata(now, now, syncStatus = SyncStatus.PENDING),
                dayOrigin = AUTO_DAY,
            ),
        )
        database.harvestDao().upsertParcels(dayParcels(id, workspaceId, campaignId, null, now))
        database.enqueueCollapsed(idGenerator, SyncEntityType.HARVEST, id, OutboxOperation.CREATE, now)
        return id
    }

    /**
     * Brings a day in line with its live Pesadas after one joins, changes or leaves it.
     *
     * An automatic day (CR-010 A1) only ever holds the sum of its Pesadas and their origin: with
     * none left it goes back to «Kg pendientes de pesada», and it is removed only when nothing
     * else — jornales, equipment, expenses or attachments — still belongs to it.
     *
     * A Jornada recorded by hand keeps the Phase 19B rule: with Pesadas its kilos are their sum.
     * Once a Pesada linked, no typed figure is left, so when the last one leaves it too goes back
     * to 0 («Kg pendientes de pesada»); A2 would otherwise show those kilos as hand-typed history.
     */
    suspend fun reconcile(harvestId: UUID?, now: Instant) {
        if (harvestId == null) return
        val harvest = database.harvestDao().findById(harvestId)?.takeIf { it.metadata.deletedAt == null } ?: return
        val linked = database.deliveryDao().listLiveForHarvest(harvestId)
        if (harvest.dayOrigin == AUTO_DAY) {
            reconcileAutoDay(harvest, linked, now)
            return
        }
        val sum = linked.sumOf { it.netGrams }
        if (sum == harvest.weightGrams) return
        database.harvestDao().upsert(harvest.copy(weightGrams = sum, metadata = harvest.metadata.next(now)))
        val parcels = database.harvestDao().listParcels(harvestId)
        parcels.singleOrNull()?.takeIf { it.allocationMode == HarvestAllocation.EXACT.name }?.let { only ->
            val share = if (sum > 0) only.copy(weightGrams = sum) else only.copy(weightGrams = null, allocationMode = HarvestAllocation.UNALLOCATED.name)
            database.harvestDao().upsertParcels(listOf(share))
        }
        database.enqueueCollapsed(idGenerator, SyncEntityType.HARVEST, harvestId, OutboxOperation.UPDATE, now)
    }

    private suspend fun reconcileAutoDay(day: HarvestEntity, linked: List<DeliveryEntity>, now: Instant) {
        if (linked.isEmpty() && !ownsAnything(day)) {
            database.harvestDao().upsert(day.copy(weightGrams = 0L, metadata = day.metadata.next(now).copy(deletedAt = now)))
            database.enqueueCollapsed(idGenerator, SyncEntityType.HARVEST, day.id, OutboxOperation.DELETE, now)
            return
        }
        val sum = linked.sumOf { it.netGrams }
        // With no Pesada left, its origin is back to the whole Farm: nothing supports a subset any more.
        val parcelsChanged = replaceDayParcels(day, linked, now)
        if (sum == day.weightGrams && !parcelsChanged) return
        database.harvestDao().upsert(day.copy(weightGrams = sum, metadata = day.metadata.next(now)))
        database.enqueueCollapsed(idGenerator, SyncEntityType.HARVEST, day.id, OutboxOperation.UPDATE, now)
    }

    /**
     * CR-010 (note 2): a day's origin is the union of its Pesadas' Parcels, except that one
     * Pesada from the whole Farm (no Parcel chosen) makes the day farm-wide, and so does having no
     * Pesada at all. Always without a split: exact kilos stay on each Pesada and are never inferred
     * for the day.
     */
    private suspend fun replaceDayParcels(day: HarvestEntity, linked: List<DeliveryEntity>, now: Instant): Boolean {
        val origins = linked.map { pesada -> database.deliveryDao().listParcels(pesada.id).map { it.parcelId } }
        val wanted = if (origins.isEmpty() || origins.any { it.isEmpty() }) null else origins.flatten().toSet()
        val campaignId = day.campaignId ?: return false
        val rows = dayParcels(day.id, day.workspaceId, campaignId, wanted, now)
        val current = database.harvestDao().listParcels(day.id)
        val unchanged = current.map { it.parcelId }.toSet() == rows.map { it.parcelId }.toSet() &&
            current.all { it.allocationMode == HarvestAllocation.UNALLOCATED.name && it.weightGrams == null }
        if (unchanged) return false
        database.harvestDao().deleteParcels(day.id)
        database.harvestDao().upsertParcels(rows)
        return true
    }

    /** The day's origin rows: [parcelIds] of the Campaign, or all of them (the whole Farm) when null. */
    private suspend fun dayParcels(
        harvestId: UUID,
        workspaceId: UUID,
        campaignId: UUID,
        parcelIds: Set<UUID>?,
        now: Instant,
    ): List<HarvestParcelEntity> =
        database.harvestDao().listCampaignParcels(campaignId)
            .filter { parcelIds == null || it.parcelId in parcelIds }
            .sortedBy { it.parcelId.toString() }
            .map { parcel ->
                HarvestParcelEntity(
                    id = idGenerator.newId(),
                    workspaceId = workspaceId,
                    harvestId = harvestId,
                    parcelId = parcel.parcelId,
                    campaignParcelId = parcel.campaignParcelId,
                    parcelNameAtHarvest = parcel.name,
                    weightGrams = null,
                    allocationMode = HarvestAllocation.UNALLOCATED.name,
                    metadata = LocalMetadata(now, now, syncStatus = SyncStatus.PENDING),
                )
            }

    /**
     * Whether removing the day would lose something the farmer recorded on it: what they typed in
     * its own form (method, people, machinery, notes) or anything linked to it.
     */
    private suspend fun ownsAnything(day: HarvestEntity): Boolean {
        val harvestId = day.id
        if (day.collectionMethod != null || day.workerCount != null || day.machineryText != null || day.notes != null) return true
        return database.labourDao().listForHarvest(harvestId).isNotEmpty() ||
            database.equipmentDao().listForHarvest(harvestId).isNotEmpty() ||
            database.expenseDao().listForHarvest(harvestId).isNotEmpty() ||
            database.documentDao().countLiveForOwner(AttachmentOwnerType.HARVEST.name, harvestId) > 0
    }

    private fun LocalMetadata.next(now: Instant) =
        copy(updatedAt = now, version = version + 1, syncStatus = SyncStatus.PENDING)
}
