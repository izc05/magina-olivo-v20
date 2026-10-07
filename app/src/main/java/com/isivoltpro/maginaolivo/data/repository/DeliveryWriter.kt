package com.isivoltpro.maginaolivo.data.repository

import com.isivoltpro.maginaolivo.core.id.IdGenerator
import com.isivoltpro.maginaolivo.data.local.MaginaOlivoDatabase
import com.isivoltpro.maginaolivo.data.local.entity.CampaignEntity
import com.isivoltpro.maginaolivo.data.local.entity.DeliveryEntity
import com.isivoltpro.maginaolivo.data.local.entity.DeliveryParcelEntity
import com.isivoltpro.maginaolivo.data.local.entity.LocalMetadata
import com.isivoltpro.maginaolivo.data.local.model.CampaignStatus
import com.isivoltpro.maginaolivo.data.local.model.FarmStatus
import com.isivoltpro.maginaolivo.data.local.model.OutboxOperation
import com.isivoltpro.maginaolivo.data.local.model.SyncEntityType
import com.isivoltpro.maginaolivo.data.local.model.SyncStatus
import com.isivoltpro.maginaolivo.domain.delivery.DeliveryDraft
import com.isivoltpro.maginaolivo.domain.delivery.DeliveryRules
import com.isivoltpro.maginaolivo.domain.delivery.DeliverySource
import com.isivoltpro.maginaolivo.domain.harvest.HarvestAllocation
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID

internal class InvalidDelivery(val field: String, val code: String) : RuntimeException("$field:$code")

internal class DeliveryConflict(val code: String) : RuntimeException(code)

/**
 * The only code that writes `deliveries` and `delivery_parcels`, used by the Delivery form
 * and by a confirmed weight ticket alike. Must run inside the caller's transaction; the
 * Delivery and its origin Parcels share one version and one outbox intent (D10).
 */
internal class DeliveryWriter(
    private val database: MaginaOlivoDatabase,
    private val idGenerator: IdGenerator,
) {
    private val jornadas = JornadaLedger(database, idGenerator)

    suspend fun insert(draft: DeliveryDraft, source: DeliverySource, today: LocalDate, now: Instant): DeliveryEntity {
        DeliveryRules.validateNew(draft, today)?.let { throw InvalidDelivery(it.field, it.code) }
        val farm = database.farmDao().findById(draft.farmId) ?: throw InvalidDelivery("farmId", "not_found")
        if (farm.status != FarmStatus.ACTIVE || farm.metadata.deletedAt != null) throw DeliveryConflict("archived_farm")
        val campaign = database.campaignDao().findCurrent(farm.id) ?: throw DeliveryConflict("no_running_campaign")
        checkDate(draft, campaign)
        // CR-010 §4: the day is found or created by Farm + Campaign + date; nobody opens it by hand.
        val harvestId = jornadas.autoDay(farm.workspaceId, farm.id, campaign.id, draft.deliveryDate, now)
        val row = DeliveryEntity(
            id = idGenerator.newId(),
            workspaceId = farm.workspaceId,
            farmId = farm.id,
            campaignId = campaign.id,
            deliveryDate = draft.deliveryDate,
            destinationOrganizationId = draft.destinationOrganizationId,
            destinationName = destinationName(draft, farm.workspaceId),
            netGrams = draft.netGrams!!,
            grossGrams = draft.grossGrams,
            tareGrams = draft.tareGrams,
            deliveryNumber = draft.deliveryNumber.normalized(),
            ticketNumber = draft.ticketNumber.normalized(),
            source = source.name,
            notes = draft.notes.normalized(),
            metadata = LocalMetadata(now, now, syncStatus = SyncStatus.PENDING),
            harvestId = harvestId,
            deliveryTime = draft.deliveryTime?.let { formatTime(it) },
            origin = draft.origin?.name,
        )
        database.deliveryDao().upsert(row)
        replaceShares(row, draft, now)
        database.enqueueCollapsed(idGenerator, SyncEntityType.DELIVERY, row.id, OutboxOperation.CREATE, now)
        jornadas.reconcile(row.harvestId, now)
        return row
    }

    suspend fun rewrite(current: DeliveryEntity, draft: DeliveryDraft, today: LocalDate, now: Instant) {
        DeliveryRules.validate(draft, today)?.let { throw InvalidDelivery(it.field, it.code) }
        if (current.farmId != draft.farmId) throw InvalidDelivery("farmId", "cannot_change")
        val campaign = runningCampaign(current)
        checkDate(draft, campaign)
        // #455: a Pesada is never dated after its own yield analysis; an analysis without a
        // date asks nothing.
        if (draft.deliveryDate != current.deliveryDate) {
            val analysisDate = database.deliveryDao().findLiveAnalysis(current.id)?.analysisDate
            if (analysisDate != null && analysisDate.isBefore(draft.deliveryDate)) {
                throw InvalidDelivery("deliveryDate", "after_analysis")
            }
        }
        // CR-010 (note 2): a Pesada whose date is kept stays in its day (a link to a hand-recorded
        // Jornada made before CR-010 included); a new date moves it to that date's automatic day.
        val keptDay = current.harvestId?.takeIf { draft.deliveryDate == current.deliveryDate && liveDay(it) }
        val harvestId = keptDay
            ?: jornadas.autoDay(current.workspaceId, current.farmId, campaign.id, draft.deliveryDate, now)
        val row = current.copy(
            deliveryDate = draft.deliveryDate,
            destinationOrganizationId = draft.destinationOrganizationId,
            destinationName = keptDestinationName(current, draft) ?: destinationName(draft, current.workspaceId),
            netGrams = draft.netGrams!!,
            grossGrams = draft.grossGrams,
            tareGrams = draft.tareGrams,
            deliveryNumber = draft.deliveryNumber.normalized(),
            ticketNumber = draft.ticketNumber.normalized(),
            notes = draft.notes.normalized(),
            metadata = current.metadata.next(now),
            harvestId = harvestId,
            deliveryTime = draft.deliveryTime?.let { formatTime(it) },
            // An edit without an origin (older callers) keeps the one stored.
            origin = draft.origin?.name ?: current.origin,
        )
        database.deliveryDao().upsert(row)
        replaceShares(row, draft, now)
        database.enqueueCollapsed(idGenerator, SyncEntityType.DELIVERY, row.id, OutboxOperation.UPDATE, now)
        jornadas.reconcile(row.harvestId, now)
        // A1: the day it left follows the Pesadas that remain there.
        if (current.harvestId != row.harvestId) jornadas.reconcile(current.harvestId, now)
    }

    private suspend fun liveDay(harvestId: UUID): Boolean =
        database.harvestDao().findById(harvestId)?.let { it.metadata.deletedAt == null } == true

    /** After a Pesada is removed, its day's kilos follow the Pesadas that remain (A1). */
    suspend fun afterDelete(current: DeliveryEntity, now: Instant) = jornadas.reconcile(current.harvestId, now)

    /** A Delivery of a closed Campaign is history: it is neither edited nor deleted. */
    suspend fun runningCampaign(current: DeliveryEntity): CampaignEntity {
        val campaign = database.campaignDao().findById(current.campaignId)
        if (campaign == null || campaign.status !in RUNNING) throw DeliveryConflict("closed_campaign")
        return campaign
    }

    private fun checkDate(draft: DeliveryDraft, campaign: CampaignEntity) {
        if (draft.deliveryDate.isBefore(campaign.startDate)) throw InvalidDelivery("deliveryDate", "before_campaign")
    }

    /**
     * #451: a Pesada that keeps its cooperative keeps the name it was recorded with, even if the
     * cooperative was renamed or archived since. Only choosing another one takes a new name.
     */
    private fun keptDestinationName(current: DeliveryEntity, draft: DeliveryDraft): String? =
        current.destinationName.takeIf {
            draft.destinationOrganizationId != null && draft.destinationOrganizationId == current.destinationOrganizationId
        }

    /** A chosen organization is copied by name, so the Delivery reads the same if it is renamed. */
    private suspend fun destinationName(draft: DeliveryDraft, workspaceId: UUID): String {
        val organizationId = draft.destinationOrganizationId ?: return draft.destinationName!!.trim()
        val organization = database.organizationDao().findById(organizationId)
        if (organization == null || organization.metadata.deletedAt != null || organization.workspaceId != workspaceId) {
            throw InvalidDelivery("destination", "not_found")
        }
        return organization.name
    }

    /**
     * Writes the origin Parcels of a Pesada (#454). A Parcel it already had keeps its row: id,
     * `parcelNameAtDelivery` and Campaign link stay as recorded, and only a real change of its
     * kilos touches it. Only a Parcel added now takes today's name; a Parcel taken out loses
     * only its own row.
     */
    private suspend fun replaceShares(delivery: DeliveryEntity, draft: DeliveryDraft, now: Instant) {
        val existing = database.deliveryDao().listParcels(delivery.id).associateBy { it.parcelId }
        val wanted = draft.shares.map { it.parcelId }.toSet()
        val removed = existing.values.filter { it.parcelId !in wanted }.map { it.id }
        if (removed.isNotEmpty()) database.deliveryDao().deleteParcelsById(removed)
        val added = draft.shares.filter { it.parcelId !in existing }
        val campaignParcels = if (added.isEmpty()) {
            emptyMap()
        } else {
            database.harvestDao().listSelectableCampaignParcels(delivery.campaignId).associateBy { it.parcelId }
        }
        val rows = draft.shares.sortedBy { it.parcelId.toString() }.mapNotNull { share ->
            val mode = if (share.weightGrams == null) HarvestAllocation.UNALLOCATED.name else HarvestAllocation.EXACT.name
            val kept = existing[share.parcelId]
            when {
                kept == null -> {
                    val parcel = campaignParcels[share.parcelId] ?: throw InvalidDelivery("parcels", "parcel_not_in_campaign")
                    DeliveryParcelEntity(
                        id = idGenerator.newId(),
                        workspaceId = delivery.workspaceId,
                        deliveryId = delivery.id,
                        parcelId = parcel.parcelId,
                        campaignParcelId = parcel.campaignParcelId,
                        parcelNameAtDelivery = parcel.name,
                        weightGrams = share.weightGrams,
                        allocationMode = mode,
                        metadata = LocalMetadata(now, now, syncStatus = SyncStatus.PENDING),
                    )
                }
                kept.weightGrams == share.weightGrams && kept.allocationMode == mode -> null
                else -> kept.copy(weightGrams = share.weightGrams, allocationMode = mode, metadata = kept.metadata.next(now))
            }
        }
        if (rows.isNotEmpty()) database.deliveryDao().upsertParcels(rows)
    }

    private fun LocalMetadata.next(now: Instant) =
        copy(updatedAt = now, version = version + 1, syncStatus = SyncStatus.PENDING)

    private fun String?.normalized() = this?.trim()?.ifEmpty { null }

    private fun formatTime(time: LocalTime): String = time.format(HOUR)

    private companion object {
        val RUNNING = setOf(CampaignStatus.ACTIVE, CampaignStatus.HARVEST)
        val HOUR: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}
