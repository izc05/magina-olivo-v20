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
import com.isivoltpro.maginaolivo.domain.harvest.AutoDaySplit
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
     * a Pesada says where its olives came from, its origin is not determined (#458): it has no
     * Parcel rows, so it is attributed to no Parcel. [reconcile] sets its kilos and Parcels from
     * its Pesadas.
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
     * A Jornada recorded by hand (MANUAL) is never touched here (CR-010 amendment J, J-D2): its typed kilos and
     * split are the farmer's, whatever Pesadas are linked to it. (Before amendment J its kilos followed the sum of
     * its Pesadas, the Phase 19B rule; values altered that way are retained for review by the sender, J-D3.)
     */
    suspend fun reconcile(harvestId: UUID?, now: Instant) {
        if (harvestId == null) return
        val harvest = database.harvestDao().findById(harvestId)?.takeIf { it.metadata.deletedAt == null } ?: return
        // CR-010 amendment J (J-D2): a hand-recorded Jornada keeps the kilos and split the farmer typed. Linking,
        // editing or deleting a Pesada never overwrites, sums or zeroes them and never queues a Jornada update; the
        // linked Pesadas are only an informative figure.
        if (harvest.dayOrigin != AUTO_DAY) return
        reconcileAutoDay(harvest, database.deliveryDao().listLiveForHarvest(harvestId), now)
    }

    /**
     * #458 upgrade path: an automatic day only ever holds the sum of its Pesadas and their origin.
     * One left without a live Pesada by an earlier version keeps its own record (jornales, notes,
     * method…) but loses what was only presumed: its origin Parcels and any kilos, back to «Kg
     * pendientes de pesada». In closed Campaigns too: that presumption was never the farmer's
     * history. Idempotent.
     */
    suspend fun clearUnfoundedOrigins(now: Instant) {
        database.harvestDao().listUnfoundedAutoDays().forEach { day ->
            database.harvestDao().deleteParcels(day.id)
            database.harvestDao().upsert(day.copy(weightGrams = 0L, metadata = day.metadata.next(now)))
            database.enqueueCollapsed(idGenerator, SyncEntityType.HARVEST, day.id, OutboxOperation.UPDATE, now)
        }
    }

    /**
     * #502: after a day's jornal, machinery, Gasto or attachment goes, an automatic day keeps
     * only what still backs it: with nothing left at all it is removed, as when its last Pesada
     * goes. A Jornada recorded by hand is never touched here (its kilos are the farmer's). Call it
     * after the day's calculated costs are settled, so it sees the final ledger.
     */
    suspend fun reconcileAutomatic(harvestId: UUID?, now: Instant) {
        if (harvestId == null) return
        val day = database.harvestDao().findById(harvestId)?.takeIf { it.metadata.deletedAt == null } ?: return
        if (day.dayOrigin != AUTO_DAY) return
        reconcileAutoDay(day, database.deliveryDao().listLiveForHarvest(day.id), now)
    }

    private suspend fun reconcileAutoDay(day: HarvestEntity, linked: List<DeliveryEntity>, now: Instant) {
        if (linked.isEmpty() && !ownsAnything(day)) {
            database.harvestDao().upsert(day.copy(weightGrams = 0L, metadata = day.metadata.next(now).copy(deletedAt = now)))
            database.enqueueCollapsed(idGenerator, SyncEntityType.HARVEST, day.id, OutboxOperation.DELETE, now)
            return
        }
        val sum = linkedGrams(linked)
        // With no Pesada left, its origin is not determined again: no Parcel is presumed (#458).
        val parcelsChanged = replaceDayParcels(day, linked, now)
        if (sum == day.weightGrams && !parcelsChanged) return
        database.harvestDao().upsert(day.copy(weightGrams = sum, metadata = day.metadata.next(now)))
        database.enqueueCollapsed(idGenerator, SyncEntityType.HARVEST, day.id, OutboxOperation.UPDATE, now)
    }

    /** #497: never persist a wrapped/negative day total if historical/imported rows are extreme. */
    private fun linkedGrams(linked: List<DeliveryEntity>): Long {
        var total = 0L
        for (delivery in linked) {
            if (delivery.netGrams < 0) throw InvalidDelivery("netGrams", "invalid_stored_value")
            total = try {
                Math.addExact(total, delivery.netGrams)
            } catch (_: ArithmeticException) {
                throw InvalidDelivery("netGrams", "day_total_overflow")
            }
        }
        return total
    }

    /**
     * CR-010 amendment J (J-D1): the day's Parcels follow the server rule ([AutoDaySplit], as `derive_auto_day`):
     * per Parcel, EXACT with the sum only when every live share is EXACT, else UNALLOCATED without kilos; a Pesada
     * without Parcels adds no Parcel. With no Pesada the origin is not determined: no Parcel rows at all (#458).
     *
     * Only the difference is written: a Parcel already in the day keeps its row, id and `parcelNameAtHarvest`; a
     * Parcel joining takes its current name; one leaving loses only its row. Never an outbox operation by itself.
     */
    private suspend fun replaceDayParcels(day: HarvestEntity, linked: List<DeliveryEntity>, now: Instant): Boolean {
        val campaignId = day.campaignId ?: return false
        val shares = linked.flatMap { pesada ->
            database.deliveryDao().listParcels(pesada.id).map { share ->
                AutoDaySplit.Share(share.parcelId,
                    if (share.allocationMode == HarvestAllocation.EXACT.name) HarvestAllocation.EXACT else HarvestAllocation.UNALLOCATED,
                    share.weightGrams)
            }
        }
        val wanted = AutoDaySplit.derive(shares).associateBy { it.parcelId }
        val current = database.harvestDao().listParcels(day.id)
        val removed = current.filter { it.parcelId !in wanted }
        val changed = current.mapNotNull { row ->
            val target = wanted[row.parcelId] ?: return@mapNotNull null
            if (row.allocationMode == target.allocation.name && row.weightGrams == target.weightGrams) null
            else row.copy(weightGrams = target.weightGrams, allocationMode = target.allocation.name, metadata = row.metadata.next(now))
        }
        val missing = wanted.keys - current.map { it.parcelId }.toSet()
        val added = if (missing.isEmpty()) emptyList() else dayParcels(day.id, day.workspaceId, campaignId, missing, now).map { row ->
            val target = wanted.getValue(row.parcelId)
            row.copy(weightGrams = target.weightGrams, allocationMode = target.allocation.name)
        }
        if (removed.isEmpty() && changed.isEmpty() && added.isEmpty()) return false
        if (removed.isNotEmpty()) database.harvestDao().deleteParcelsById(removed.map { it.id })
        if (changed.isNotEmpty() || added.isNotEmpty()) database.harvestDao().upsertParcels(changed + added)
        return true
    }

    /** New origin rows for [parcelIds] of the Campaign, each named as the Parcel is called now. */
    private suspend fun dayParcels(
        harvestId: UUID,
        workspaceId: UUID,
        campaignId: UUID,
        parcelIds: Set<UUID>,
        now: Instant,
    ): List<HarvestParcelEntity> =
        database.harvestDao().listCampaignParcels(campaignId)
            .filter { it.parcelId in parcelIds }
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
