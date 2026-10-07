package com.isivoltpro.maginaolivo.data.repository

import androidx.room.withTransaction
import com.isivoltpro.maginaolivo.core.common.AppError
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.core.dispatchers.AppDispatchers
import com.isivoltpro.maginaolivo.core.id.IdGenerator
import com.isivoltpro.maginaolivo.core.time.AppClock
import com.isivoltpro.maginaolivo.data.local.MaginaOlivoDatabase
import com.isivoltpro.maginaolivo.data.local.entity.DeliveryYieldAnalysisEntity
import com.isivoltpro.maginaolivo.data.local.entity.LocalMetadata
import com.isivoltpro.maginaolivo.data.local.model.CampaignStatus
import com.isivoltpro.maginaolivo.data.local.model.DeliveryWithParcels
import com.isivoltpro.maginaolivo.data.local.model.OutboxOperation
import com.isivoltpro.maginaolivo.data.local.model.SyncEntityType
import com.isivoltpro.maginaolivo.data.local.model.SyncStatus
import com.isivoltpro.maginaolivo.domain.delivery.Delivery
import com.isivoltpro.maginaolivo.domain.delivery.DeliveryDraft
import com.isivoltpro.maginaolivo.domain.delivery.DeliveryRepository
import com.isivoltpro.maginaolivo.domain.delivery.DeliveryShare
import com.isivoltpro.maginaolivo.domain.delivery.DeliverySource
import com.isivoltpro.maginaolivo.domain.delivery.PesadaOrigin
import com.isivoltpro.maginaolivo.domain.delivery.YieldAnalysis
import com.isivoltpro.maginaolivo.domain.delivery.YieldDraft
import com.isivoltpro.maginaolivo.domain.delivery.YieldRules
import com.isivoltpro.maginaolivo.domain.harvest.HarvestAllocation
import com.isivoltpro.maginaolivo.domain.harvest.HarvestContext
import com.isivoltpro.maginaolivo.domain.harvest.HarvestParcelOption
import com.isivoltpro.maginaolivo.domain.workspace.WorkspaceRepository
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Offline-first Deliveries and their later yield analyses.
 *
 * A Delivery and its origin Parcels are one aggregate with one outbox intent. The yield
 * analysis is a separate record with its own intent, so recording or correcting it never
 * changes the Delivery's row, version or intent (`DATA-MODEL-RC1.1-ADDENDUM` §11, Gate 14).
 */
class OfflineFirstDeliveryRepository(
    private val database: MaginaOlivoDatabase,
    private val clock: AppClock,
    private val idGenerator: IdGenerator,
    private val dispatchers: AppDispatchers,
    private val workspaceRepository: WorkspaceRepository? = null,
    /** Test override only; production derives the calendar from the persisted Workspace. */
    private val zoneId: (() -> ZoneId)? = null,
) : DeliveryRepository {
    private val writer = DeliveryWriter(database, idGenerator)
    private val workspaceScope = ActiveWorkspaceScope(database, workspaceRepository)

    override fun observeAll(): Flow<List<Delivery>> =
        flow {
            val active = when (val result = workspaceScope.resolve()) {
                is AppResult.Failure -> return@flow emit(emptyList())
                is AppResult.Success -> result.value
            }
            emitAll(
                database.deliveryDao().observeAllForWorkspace(active)
                    .map { rows -> toDomain(rows) },
            )
        }.flowOn(dispatchers.io)

    override fun observeForCampaign(campaignId: UUID): Flow<List<Delivery>> =
        flow {
            val active = when (val result = workspaceScope.resolve()) {
                is AppResult.Failure -> return@flow emit(emptyList())
                is AppResult.Success -> result.value
            }
            val campaign = database.campaignDao().findById(campaignId)
            if (campaign == null || campaign.workspaceId != active) {
                emit(emptyList())
                return@flow
            }
            emitAll(database.deliveryDao().observeForCampaign(campaignId).map { rows -> toDomain(rows) })
        }.flowOn(dispatchers.io)

    override fun observe(id: UUID): Flow<Delivery?> =
        flow {
            val active = when (val result = workspaceScope.resolve()) {
                is AppResult.Failure -> return@flow emit(null)
                is AppResult.Success -> result.value
            }
            emitAll(
                database.deliveryDao().observeWithParcels(id).map { row ->
                    row?.takeIf { it.delivery.workspaceId == active }?.let { toDomain(listOf(it)).single() }
                },
            )
        }.flowOn(dispatchers.io)

    override fun observeContexts(): Flow<List<HarvestContext>> =
        flow {
            val active = when (val result = workspaceScope.resolve()) {
                is AppResult.Failure -> return@flow emit(emptyList())
                is AppResult.Success -> result.value
            }
            emitAll(
                database.harvestDao().observeRunningCampaignsForWorkspace(active).map { rows ->
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
                },
            )
        }.flowOn(dispatchers.io)

    override suspend fun create(draft: DeliveryDraft): AppResult<UUID> =
        inTransaction("create_delivery") {
            val active = when (val result = workspaceScope.resolve()) {
                is AppResult.Failure -> return@inTransaction result
                is AppResult.Success -> result.value
            }
            val workspaceId = database.farmDao().findById(draft.farmId)?.workspaceId
                ?: return@inTransaction AppResult.Failure(AppError.NotFound("farm"))
            if (workspaceId != active) return@inTransaction contextMismatch()
            AppResult.Success(
                writer.insert(draft, DeliverySource.MANUAL, today(workspaceId), clock.nowInstant()).id,
            )
        }

    override suspend fun update(id: UUID, draft: DeliveryDraft): AppResult<Unit> =
        inTransaction("update_delivery") {
            val current = database.deliveryDao().findById(id)?.takeIf { it.metadata.deletedAt == null }
                ?: return@inTransaction AppResult.Failure(AppError.NotFound("delivery"))
            workspaceScope.mismatch(current.workspaceId)?.let { return@inTransaction it }
            writer.rewrite(current, draft, today(current.workspaceId), clock.nowInstant())
            AppResult.Success(Unit)
        }

    override suspend fun delete(id: UUID): AppResult<Unit> =
        inTransaction("delete_delivery") {
            val current = database.deliveryDao().findById(id)
                ?: return@inTransaction AppResult.Failure(AppError.NotFound("delivery"))
            workspaceScope.mismatch(current.workspaceId)?.let { return@inTransaction it }
            if (current.metadata.deletedAt != null) return@inTransaction AppResult.Success(Unit)
            writer.runningCampaign(current)
            val now = clock.nowInstant()
            database.deliveryDao().upsert(current.copy(metadata = current.metadata.next(now).copy(deletedAt = now)))
            database.enqueueCollapsed(idGenerator, SyncEntityType.DELIVERY, id, OutboxOperation.DELETE, now)
            writer.afterDelete(current, now)
            // Its analysis only describes this Delivery: it goes with it.
            database.deliveryDao().findLiveAnalysis(id)?.let { analysis -> tombstone(analysis, now) }
            AppResult.Success(Unit)
        }

    override suspend fun recordYield(deliveryId: UUID, draft: YieldDraft): AppResult<UUID> =
        inTransaction("record_yield") {
            val delivery = database.deliveryDao().findById(deliveryId)?.takeIf { it.metadata.deletedAt == null }
                ?: return@inTransaction AppResult.Failure(AppError.NotFound("delivery"))
            workspaceScope.mismatch(delivery.workspaceId)?.let { return@inTransaction it }
            val campaign = database.campaignDao().findById(delivery.campaignId)
                ?: return@inTransaction AppResult.Failure(AppError.NotFound("campaign"))
            val existing = database.deliveryDao().findLiveAnalysis(deliveryId)
            if (campaign.status == CampaignStatus.CLOSED && existing != null) {
                return@inTransaction AppResult.Failure(AppError.Conflict("closed_campaign_yield_confirmed"))
            }
            YieldRules.validate(draft, today(delivery.workspaceId))
                ?.let { return@inTransaction AppResult.Failure(AppError.Validation(it.field, it.code)) }
            if (draft.analysisDate?.isBefore(delivery.deliveryDate) == true) {
                return@inTransaction AppResult.Failure(AppError.Validation("analysisDate", "before_delivery"))
            }
            val now = clock.nowInstant()
            val row = existing?.copy(
                analysisDate = draft.analysisDate,
                fatYieldHundredths = draft.fatYieldHundredths,
                industrialYieldHundredths = draft.industrialYieldHundredths,
                notes = draft.notes?.trim()?.ifEmpty { null },
                metadata = existing.metadata.next(now),
            ) ?: DeliveryYieldAnalysisEntity(
                id = idGenerator.newId(),
                workspaceId = delivery.workspaceId,
                deliveryId = deliveryId,
                analysisDate = draft.analysisDate,
                fatYieldHundredths = draft.fatYieldHundredths,
                industrialYieldHundredths = draft.industrialYieldHundredths,
                notes = draft.notes?.trim()?.ifEmpty { null },
                metadata = LocalMetadata(now, now, syncStatus = SyncStatus.PENDING),
            )
            database.deliveryDao().upsertAnalysis(row)
            database.enqueueCollapsed(
                idGenerator,
                SyncEntityType.DELIVERY_YIELD,
                row.id,
                if (existing == null) OutboxOperation.CREATE else OutboxOperation.UPDATE,
                now,
            )
            AppResult.Success(row.id)
        }

    override suspend fun removeYield(deliveryId: UUID): AppResult<Unit> =
        inTransaction("remove_yield") {
            val delivery = database.deliveryDao().findById(deliveryId)?.takeIf { it.metadata.deletedAt == null }
                ?: return@inTransaction AppResult.Failure(AppError.NotFound("delivery"))
            workspaceScope.mismatch(delivery.workspaceId)?.let { return@inTransaction it }
            val analysis = database.deliveryDao().findLiveAnalysis(deliveryId) ?: return@inTransaction AppResult.Success(Unit)
            val campaign = database.campaignDao().findById(delivery.campaignId)
                ?: return@inTransaction AppResult.Failure(AppError.NotFound("campaign"))
            if (campaign.status == CampaignStatus.CLOSED) {
                return@inTransaction AppResult.Failure(AppError.Conflict("closed_campaign_yield_confirmed"))
            }
            tombstone(analysis, clock.nowInstant())
            AppResult.Success(Unit)
        }

    private suspend fun tombstone(analysis: DeliveryYieldAnalysisEntity, now: Instant) {
        database.deliveryDao().upsertAnalysis(analysis.copy(metadata = analysis.metadata.next(now).copy(deletedAt = now)))
        database.enqueueCollapsed(idGenerator, SyncEntityType.DELIVERY_YIELD, analysis.id, OutboxOperation.DELETE, now)
    }

    private suspend fun today(workspaceId: UUID) = database.todayForWorkspace(workspaceId, clock, zoneId)

    private fun contextMismatch() =
        AppResult.Failure(AppError.Validation("workspaceId", "context_mismatch"))


    private suspend fun <T> inTransaction(operation: String, block: suspend () -> AppResult<T>): AppResult<T> =
        withContext(dispatchers.io) {
            try {
                database.withTransaction { block() }
            } catch (error: InvalidDelivery) {
                AppResult.Failure(AppError.Validation(error.field, error.code))
            } catch (error: DeliveryConflict) {
                AppResult.Failure(AppError.Conflict(error.code))
            } catch (error: Throwable) {
                AppResult.Failure(AppError.Storage(operation, error))
            }
        }

    private suspend fun toDomain(rows: List<DeliveryWithParcels>): List<Delivery> {
        val farms = rows.map { it.delivery.farmId }.distinct().associateWith { database.farmDao().findById(it)?.name }
        val campaigns = rows.map { it.delivery.campaignId }.distinct().associateWith { database.campaignDao().findById(it) }
        return rows.map { row -> row.toDomain(farms[row.delivery.farmId], campaigns[row.delivery.campaignId]) }
    }

    private fun DeliveryWithParcels.toDomain(
        farmName: String?,
        campaign: com.isivoltpro.maginaolivo.data.local.entity.CampaignEntity?,
    ) = Delivery(
        id = delivery.id,
        workspaceId = delivery.workspaceId,
        farmId = delivery.farmId,
        campaignId = delivery.campaignId,
        deliveryDate = delivery.deliveryDate,
        destinationOrganizationId = delivery.destinationOrganizationId,
        destinationName = delivery.destinationName,
        netGrams = delivery.netGrams,
        grossGrams = delivery.grossGrams,
        tareGrams = delivery.tareGrams,
        deliveryNumber = delivery.deliveryNumber,
        ticketNumber = delivery.ticketNumber,
        source = DeliverySource.entries.firstOrNull { it.name == delivery.source } ?: DeliverySource.MANUAL,
        shares = parcels.sortedBy { it.parcelNameAtDelivery.lowercase() }.map {
            DeliveryShare(
                parcelId = it.parcelId,
                parcelName = it.parcelNameAtDelivery,
                allocation = if (it.allocationMode == HarvestAllocation.EXACT.name) HarvestAllocation.EXACT else HarvestAllocation.UNALLOCATED,
                weightGrams = it.weightGrams,
            )
        },
        notes = delivery.notes,
        version = delivery.metadata.version,
        analysis = analyses.firstOrNull { it.metadata.deletedAt == null }?.let {
            YieldAnalysis(it.id, it.deliveryId, it.analysisDate, it.fatYieldHundredths, it.industrialYieldHundredths, it.notes, it.metadata.version)
        },
        farmName = farmName,
        campaignName = campaign?.name,
        editable = campaign != null && campaign.status in setOf(CampaignStatus.ACTIVE, CampaignStatus.HARVEST),
        harvestId = delivery.harvestId,
        deliveryTime = delivery.deliveryTime?.let { runCatching { LocalTime.parse(it) }.getOrNull() },
        origin = PesadaOrigin.entries.firstOrNull { it.name == delivery.origin },
    )

    private fun LocalMetadata.next(now: Instant) =
        copy(updatedAt = now, version = version + 1, syncStatus = SyncStatus.PENDING)
}
