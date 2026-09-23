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
    suspend fun insert(draft: DeliveryDraft, source: DeliverySource, today: LocalDate, now: Instant): DeliveryEntity {
        DeliveryRules.validate(draft, today)?.let { throw InvalidDelivery(it.field, it.code) }
        val farm = database.farmDao().findById(draft.farmId) ?: throw InvalidDelivery("farmId", "not_found")
        if (farm.status != FarmStatus.ACTIVE || farm.metadata.deletedAt != null) throw DeliveryConflict("archived_farm")
        val campaign = database.campaignDao().findCurrent(farm.id) ?: throw DeliveryConflict("no_running_campaign")
        checkDate(draft, campaign)
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
        )
        database.deliveryDao().upsert(row)
        replaceShares(row, draft, now)
        database.enqueueCollapsed(idGenerator, SyncEntityType.DELIVERY, row.id, OutboxOperation.CREATE, now)
        return row
    }

    suspend fun rewrite(current: DeliveryEntity, draft: DeliveryDraft, today: LocalDate, now: Instant) {
        DeliveryRules.validate(draft, today)?.let { throw InvalidDelivery(it.field, it.code) }
        if (current.farmId != draft.farmId) throw InvalidDelivery("farmId", "cannot_change")
        val campaign = runningCampaign(current)
        checkDate(draft, campaign)
        val row = current.copy(
            deliveryDate = draft.deliveryDate,
            destinationOrganizationId = draft.destinationOrganizationId,
            destinationName = destinationName(draft, current.workspaceId),
            netGrams = draft.netGrams!!,
            grossGrams = draft.grossGrams,
            tareGrams = draft.tareGrams,
            deliveryNumber = draft.deliveryNumber.normalized(),
            ticketNumber = draft.ticketNumber.normalized(),
            notes = draft.notes.normalized(),
            metadata = current.metadata.next(now),
        )
        database.deliveryDao().upsert(row)
        replaceShares(row, draft, now)
        database.enqueueCollapsed(idGenerator, SyncEntityType.DELIVERY, row.id, OutboxOperation.UPDATE, now)
    }

    /** A Delivery of a closed Campaign is history: it is neither edited nor deleted. */
    suspend fun runningCampaign(current: DeliveryEntity): CampaignEntity {
        val campaign = database.campaignDao().findById(current.campaignId)
        if (campaign == null || campaign.status !in RUNNING) throw DeliveryConflict("closed_campaign")
        return campaign
    }

    private fun checkDate(draft: DeliveryDraft, campaign: CampaignEntity) {
        if (draft.deliveryDate.isBefore(campaign.startDate)) throw InvalidDelivery("deliveryDate", "before_campaign")
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

    private suspend fun replaceShares(delivery: DeliveryEntity, draft: DeliveryDraft, now: Instant) {
        val campaignParcels = database.harvestDao().listCampaignParcels(delivery.campaignId).associateBy { it.parcelId }
        val rows = draft.shares.sortedBy { it.parcelId.toString() }.map { share ->
            val parcel = campaignParcels[share.parcelId] ?: throw InvalidDelivery("parcels", "parcel_not_in_campaign")
            DeliveryParcelEntity(
                id = idGenerator.newId(),
                workspaceId = delivery.workspaceId,
                deliveryId = delivery.id,
                parcelId = parcel.parcelId,
                campaignParcelId = parcel.campaignParcelId,
                parcelNameAtDelivery = parcel.name,
                weightGrams = share.weightGrams,
                allocationMode = if (share.weightGrams == null) HarvestAllocation.UNALLOCATED.name else HarvestAllocation.EXACT.name,
                metadata = LocalMetadata(now, now, syncStatus = SyncStatus.PENDING),
            )
        }
        database.deliveryDao().deleteParcels(delivery.id)
        database.deliveryDao().upsertParcels(rows)
    }

    private fun LocalMetadata.next(now: Instant) =
        copy(updatedAt = now, version = version + 1, syncStatus = SyncStatus.PENDING)

    private fun String?.normalized() = this?.trim()?.ifEmpty { null }

    private companion object {
        val RUNNING = setOf(CampaignStatus.ACTIVE, CampaignStatus.HARVEST)
    }
}
