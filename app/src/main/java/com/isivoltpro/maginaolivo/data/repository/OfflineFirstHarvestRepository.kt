package com.isivoltpro.maginaolivo.data.repository

import androidx.room.withTransaction
import com.isivoltpro.maginaolivo.core.common.AppError
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.core.dispatchers.AppDispatchers
import com.isivoltpro.maginaolivo.core.id.IdGenerator
import com.isivoltpro.maginaolivo.core.time.AppClock
import com.isivoltpro.maginaolivo.data.local.MaginaOlivoDatabase
import com.isivoltpro.maginaolivo.data.local.entity.HarvestEntity
import com.isivoltpro.maginaolivo.data.local.entity.HarvestParcelEntity
import com.isivoltpro.maginaolivo.data.local.entity.LocalMetadata
import com.isivoltpro.maginaolivo.data.local.model.CampaignStatus
import com.isivoltpro.maginaolivo.data.local.model.FarmStatus
import com.isivoltpro.maginaolivo.data.local.model.HarvestWithParcels
import com.isivoltpro.maginaolivo.data.local.model.OutboxOperation
import com.isivoltpro.maginaolivo.data.local.model.SyncEntityType
import com.isivoltpro.maginaolivo.data.local.model.SyncStatus
import com.isivoltpro.maginaolivo.domain.harvest.CollectionMethod
import com.isivoltpro.maginaolivo.domain.harvest.Harvest
import com.isivoltpro.maginaolivo.domain.harvest.HarvestAllocation
import com.isivoltpro.maginaolivo.domain.harvest.HarvestContext
import com.isivoltpro.maginaolivo.domain.harvest.HarvestDraft
import com.isivoltpro.maginaolivo.domain.harvest.HarvestParcelOption
import com.isivoltpro.maginaolivo.domain.harvest.HarvestRepository
import com.isivoltpro.maginaolivo.domain.harvest.HarvestRules
import com.isivoltpro.maginaolivo.domain.harvest.HarvestShare
import com.isivoltpro.maginaolivo.domain.harvest.HarvestShareInput
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Offline-first Harvest.
 *
 * A Harvest and its origin Parcels are one aggregate (`RC1-NORMATIVE-ADDENDUM` D6): they
 * are written in one transaction, share one version and one outbox intent. The split is
 * whatever the farmer knows — every Parcel exact, some, or none — and nothing here ever
 * fills in a Parcel's kilos. A Harvest belongs to its Farm's running Campaign; once that
 * Campaign is closed the Harvest is history and cannot be changed.
 */
class OfflineFirstHarvestRepository(
    private val database: MaginaOlivoDatabase,
    private val clock: AppClock,
    private val idGenerator: IdGenerator,
    private val dispatchers: AppDispatchers,
    private val zoneId: () -> ZoneId = ZoneId::systemDefault,
) : HarvestRepository {
    override fun observeAll(): Flow<List<Harvest>> =
        database.harvestDao().observeAll().map { rows -> rows.toDomain() }.flowOn(dispatchers.io)

    override fun observeForCampaign(campaignId: UUID): Flow<List<Harvest>> =
        database.harvestDao().observeForCampaign(campaignId).map { rows -> rows.toDomain() }.flowOn(dispatchers.io)

    override fun observe(id: UUID): Flow<Harvest?> =
        database.harvestDao().observeWithParcels(id).map { row -> row?.let { listOf(it).toDomain().single() } }
            .flowOn(dispatchers.io)

    override fun observeContexts(): Flow<List<HarvestContext>> =
        database.harvestDao().observeRunningCampaigns().map { rows ->
            rows.map { row ->
                HarvestContext(
                    farmId = row.farmId,
                    farmName = row.farmName,
                    campaignId = row.campaignId,
                    campaignName = row.campaignName,
                    campaignStatus = row.campaignStatus,
                    campaignStart = row.campaignStart,
                    parcels = database.harvestDao().listCampaignParcels(row.campaignId)
                        .map { HarvestParcelOption(it.parcelId, it.name) },
                )
            }
        }.flowOn(dispatchers.io)

    override suspend fun create(draft: HarvestDraft): AppResult<UUID> {
        validate(draft)?.let { return it }
        return inTransaction("create_harvest") {
            val farm = database.farmDao().findById(draft.farmId)
                ?: return@inTransaction AppResult.Failure(AppError.NotFound("farm"))
            if (farm.status != FarmStatus.ACTIVE || farm.metadata.deletedAt != null) {
                return@inTransaction conflict("archived_farm")
            }
            val campaign = database.campaignDao().findCurrent(farm.id)
                ?: return@inTransaction conflict("no_running_campaign")
            if (draft.harvestDate.isBefore(campaign.startDate)) {
                return@inTransaction AppResult.Failure(AppError.Validation("harvestDate", "before_campaign"))
            }
            val id = idGenerator.newId()
            val now = clock.nowInstant()
            database.harvestDao().upsert(
                HarvestEntity(
                    id = id,
                    workspaceId = farm.workspaceId,
                    campaignId = campaign.id,
                    farmId = farm.id,
                    harvestDate = draft.harvestDate,
                    weightGrams = draft.totalGrams!!,
                    notes = draft.notes.normalized(),
                    collectionMethod = draft.collectionMethod?.name,
                    workerCount = draft.workerCount,
                    machineryText = draft.machineryText.normalized(),
                    metadata = LocalMetadata(now, now, syncStatus = SyncStatus.PENDING),
                ),
            )
            replaceShares(id, farm.workspaceId, campaign.id, draft.shares, now)
            database.enqueueCollapsed(idGenerator, SyncEntityType.HARVEST, id, OutboxOperation.CREATE, now)
            AppResult.Success(id)
        }
    }

    override suspend fun update(id: UUID, draft: HarvestDraft): AppResult<Unit> {
        validate(draft)?.let { return it }
        return inTransaction("update_harvest") {
            val current = live(id) ?: return@inTransaction AppResult.Failure(AppError.NotFound("harvest"))
            if (current.farmId != draft.farmId) {
                return@inTransaction AppResult.Failure(AppError.Validation("farmId", "cannot_change"))
            }
            val campaign = current.campaignId?.let { database.campaignDao().findById(it) }
            if (campaign == null || campaign.status !in RUNNING) return@inTransaction conflict("closed_campaign")
            if (draft.harvestDate.isBefore(campaign.startDate)) {
                return@inTransaction AppResult.Failure(AppError.Validation("harvestDate", "before_campaign"))
            }
            val now = clock.nowInstant()
            database.harvestDao().upsert(
                current.copy(
                    harvestDate = draft.harvestDate,
                    weightGrams = draft.totalGrams!!,
                    notes = draft.notes.normalized(),
                    collectionMethod = draft.collectionMethod?.name,
                    workerCount = draft.workerCount,
                    machineryText = draft.machineryText.normalized(),
                    metadata = current.metadata.next(now),
                ),
            )
            replaceShares(id, current.workspaceId, campaign.id, draft.shares, now)
            database.enqueueCollapsed(idGenerator, SyncEntityType.HARVEST, id, OutboxOperation.UPDATE, now)
            AppResult.Success(Unit)
        }
    }

    override suspend fun delete(id: UUID): AppResult<Unit> =
        inTransaction("delete_harvest") {
            val current = database.harvestDao().findById(id)
                ?: return@inTransaction AppResult.Failure(AppError.NotFound("harvest"))
            if (current.metadata.deletedAt != null) return@inTransaction AppResult.Success(Unit)
            val campaign = current.campaignId?.let { database.campaignDao().findById(it) }
            if (campaign == null || campaign.status !in RUNNING) return@inTransaction conflict("closed_campaign")
            val now = clock.nowInstant()
            database.harvestDao().upsert(current.copy(metadata = current.metadata.next(now).copy(deletedAt = now)))
            database.enqueueCollapsed(idGenerator, SyncEntityType.HARVEST, id, OutboxOperation.DELETE, now)
            AppResult.Success(Unit)
        }

    private fun validate(draft: HarvestDraft): AppResult.Failure? =
        HarvestRules.validate(draft, clock.today(zoneId()))
            ?.let { AppResult.Failure(AppError.Validation(it.field, it.code)) }

    /**
     * Rewrites the origin Parcels with the Harvest, inside its transaction. A Parcel must
     * belong to the Harvest's Campaign; a share without kilos is stored `UNALLOCATED`
     * with no weight, never as zero and never as a computed part of the total.
     */
    private suspend fun replaceShares(
        harvestId: UUID,
        workspaceId: UUID,
        campaignId: UUID,
        shares: List<HarvestShareInput>,
        now: Instant,
    ) {
        val campaignParcels = database.harvestDao().listCampaignParcels(campaignId).associateBy { it.parcelId }
        val rows = shares.sortedBy { it.parcelId.toString() }.map { share ->
            val parcel = campaignParcels[share.parcelId] ?: throw InvalidShare("parcel_not_in_campaign")
            HarvestParcelEntity(
                id = idGenerator.newId(),
                workspaceId = workspaceId,
                harvestId = harvestId,
                parcelId = parcel.parcelId,
                campaignParcelId = parcel.campaignParcelId,
                parcelNameAtHarvest = parcel.name,
                weightGrams = share.weightGrams,
                allocationMode = if (share.weightGrams == null) {
                    HarvestAllocation.UNALLOCATED.name
                } else {
                    HarvestAllocation.EXACT.name
                },
                metadata = LocalMetadata(now, now, syncStatus = SyncStatus.PENDING),
            )
        }
        database.harvestDao().deleteParcels(harvestId)
        database.harvestDao().upsertParcels(rows)
    }

    private suspend fun live(id: UUID): HarvestEntity? =
        database.harvestDao().findById(id)?.takeIf { it.metadata.deletedAt == null }

    private suspend fun <T> inTransaction(operation: String, block: suspend () -> AppResult<T>): AppResult<T> =
        withContext(dispatchers.io) {
            try {
                database.withTransaction { block() }
            } catch (error: InvalidShare) {
                AppResult.Failure(AppError.Validation("parcels", error.message ?: "invalid"))
            } catch (error: Throwable) {
                AppResult.Failure(AppError.Storage(operation, error))
            }
        }

    /** Reads each Farm and Campaign once per emission, for the names a row shows. */
    private suspend fun List<HarvestWithParcels>.toDomain(): List<Harvest> {
        val farms = mapNotNull { it.harvest.farmId }.distinct()
            .associateWith { database.farmDao().findById(it)?.name }
        val campaigns = mapNotNull { it.harvest.campaignId }.distinct()
            .associateWith { database.campaignDao().findById(it) }
        return map { row ->
            val harvest = row.harvest
            val campaign = harvest.campaignId?.let(campaigns::get)
            Harvest(
                id = harvest.id,
                workspaceId = harvest.workspaceId,
                farmId = harvest.farmId,
                campaignId = harvest.campaignId,
                harvestDate = harvest.harvestDate,
                totalGrams = harvest.weightGrams,
                shares = row.parcels
                    .sortedBy { it.parcelNameAtHarvest.lowercase() }
                    .map {
                        HarvestShare(
                            parcelId = it.parcelId,
                            parcelName = it.parcelNameAtHarvest,
                            allocation = if (it.allocationMode == HarvestAllocation.EXACT.name) {
                                HarvestAllocation.EXACT
                            } else {
                                HarvestAllocation.UNALLOCATED
                            },
                            weightGrams = it.weightGrams,
                        )
                    },
                collectionMethod = harvest.collectionMethod
                    ?.let { runCatching { CollectionMethod.valueOf(it) }.getOrNull() },
                workerCount = harvest.workerCount,
                machineryText = harvest.machineryText,
                notes = harvest.notes,
                version = harvest.metadata.version,
                farmName = harvest.farmId?.let(farms::get),
                campaignName = campaign?.name,
                editable = campaign != null && campaign.status in RUNNING,
            )
        }
    }

    private fun LocalMetadata.next(now: Instant) =
        copy(updatedAt = now, version = version + 1, syncStatus = SyncStatus.PENDING)

    private fun String?.normalized() = this?.trim()?.ifEmpty { null }

    private fun conflict(code: String): AppResult.Failure = AppResult.Failure(AppError.Conflict(code))

    private class InvalidShare(message: String) : RuntimeException(message)

    private companion object {
        val RUNNING = setOf(CampaignStatus.ACTIVE, CampaignStatus.HARVEST)
    }
}
