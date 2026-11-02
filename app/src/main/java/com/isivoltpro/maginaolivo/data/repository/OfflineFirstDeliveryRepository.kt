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
import com.isivoltpro.maginaolivo.feature.account.AuthFailure
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
    private val writeScope: DeliveryWriteScope = LocalOnlyDeliveryWriteScope,
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

    override suspend fun create(draft: DeliveryDraft): AppResult<UUID> = safeWrite {
        val active = when (val result = workspaceScope.resolve()) {
            is AppResult.Failure -> return@safeWrite result
            is AppResult.Success -> result.value
        }
        writeScope.withAccount(draft.farmId) { accountId ->
            inTransaction("create_delivery") {
                val farm = database.farmDao().findById(draft.farmId)
                    ?: return@inTransaction AppResult.Failure(AppError.NotFound("farm"))
                if (farm.workspaceId != active) return@inTransaction contextMismatch()
                workspaceScope.mismatch(farm.workspaceId)?.let { return@inTransaction it }
                AppResult.Success(
                    writer.insert(draft, DeliverySource.MANUAL, today(farm.workspaceId), clock.nowInstant(), accountId).id,
                )
            }
        }
    }

    override suspend fun update(id: UUID, draft: DeliveryDraft): AppResult<Unit> = safeWrite {
        val active = when (val result = workspaceScope.resolve()) {
            is AppResult.Failure -> return@safeWrite result
            is AppResult.Success -> result.value
        }
        writeScope.withAccount(draft.farmId) { accountId ->
            inTransaction("update_delivery") {
                val fresh = database.deliveryDao().findById(id)?.takeIf { it.metadata.deletedAt == null }
                    ?: return@inTransaction AppResult.Failure(AppError.NotFound("delivery"))
                if (fresh.workspaceId != active) return@inTransaction contextMismatch()
                workspaceScope.mismatch(fresh.workspaceId)?.let { return@inTransaction it }
                writer.rewrite(fresh, draft, today(fresh.workspaceId), clock.nowInstant(), accountId)
                AppResult.Success(Unit)
            }
        }
    }

    override suspend fun delete(id: UUID): AppResult<Unit> = safeWrite {
        val active = when (val result = workspaceScope.resolve()) {
            is AppResult.Failure -> return@safeWrite result
            is AppResult.Success -> result.value
        }
        val current = withContext(dispatchers.io) {
            database.deliveryDao().findById(id)
        } ?: return@safeWrite AppResult.Failure(AppError.NotFound("delivery"))
        writeScope.withAccount(current.farmId) { _ ->
            inTransaction("delete_delivery") {
                val fresh = database.deliveryDao().findById(id) ?: return@inTransaction AppResult.Success(Unit)
                if (fresh.metadata.deletedAt != null) return@inTransaction AppResult.Success(Unit)
                if (fresh.workspaceId != active) return@inTransaction contextMismatch()
                workspaceScope.mismatch(fresh.workspaceId)?.let { return@inTransaction it }
                writer.runningCampaign(fresh)
                val now = clock.nowInstant()
                database.deliveryDao().upsert(fresh.copy(metadata = fresh.metadata.next(now).copy(deletedAt = now)))
                database.enqueueCollapsed(
                    idGenerator, SyncEntityType.DELIVERY, id, OutboxOperation.DELETE, now,
                    farmId = fresh.farmId,
                )
                writer.afterDelete(fresh, now)
                // Its analysis only describes this Delivery: it goes with it.
                database.deliveryDao().findLiveAnalysis(id)?.let { analysis -> tombstone(analysis, now) }
                AppResult.Success(Unit)
            }
        }
    }

    override suspend fun recordYield(deliveryId: UUID, draft: YieldDraft): AppResult<UUID> = safeWrite {
        val active = when (val result = workspaceScope.resolve()) {
            is AppResult.Failure -> return@safeWrite result
            is AppResult.Success -> result.value
        }
        val delivery = withContext(dispatchers.io) {
            database.deliveryDao().findById(deliveryId)?.takeIf { it.metadata.deletedAt == null }
        } ?: return@safeWrite AppResult.Failure(AppError.NotFound("delivery"))
        YieldRules.validate(draft, today(delivery.workspaceId))
            ?.let { return@safeWrite AppResult.Failure(AppError.Validation(it.field, it.code)) }
        if (draft.analysisDate?.isBefore(delivery.deliveryDate) == true) {
            return@safeWrite AppResult.Failure(AppError.Validation("analysisDate", "before_delivery"))
        }
        writeScope.withAccount(delivery.farmId) { _ ->
            inTransaction("record_yield") {
                val freshDelivery = database.deliveryDao().findById(deliveryId)?.takeIf { it.metadata.deletedAt == null }
                    ?: return@inTransaction AppResult.Failure(AppError.NotFound("delivery"))
                if (freshDelivery.workspaceId != active) return@inTransaction contextMismatch()
                workspaceScope.mismatch(freshDelivery.workspaceId)?.let { return@inTransaction it }
                val freshCampaign = database.campaignDao().findById(freshDelivery.campaignId)
                    ?: return@inTransaction AppResult.Failure(AppError.NotFound("campaign"))
                val existing = database.deliveryDao().findLiveAnalysis(deliveryId)
                if (freshCampaign.status == CampaignStatus.CLOSED && existing != null) {
                    return@inTransaction AppResult.Failure(AppError.Conflict("closed_campaign_yield_confirmed"))
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
                    workspaceId = freshDelivery.workspaceId,
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
        }
    }

    override suspend fun removeYield(deliveryId: UUID): AppResult<Unit> = safeWrite {
        val active = when (val result = workspaceScope.resolve()) {
            is AppResult.Failure -> return@safeWrite result
            is AppResult.Success -> result.value
        }
        val delivery = withContext(dispatchers.io) {
            database.deliveryDao().findById(deliveryId)?.takeIf { it.metadata.deletedAt == null }
        } ?: return@safeWrite AppResult.Failure(AppError.NotFound("delivery"))
        writeScope.withAccount(delivery.farmId) { _ ->
            inTransaction("remove_yield") {
                val freshDelivery = database.deliveryDao().findById(deliveryId)?.takeIf { it.metadata.deletedAt == null }
                    ?: return@inTransaction AppResult.Failure(AppError.NotFound("delivery"))
                if (freshDelivery.workspaceId != active) return@inTransaction contextMismatch()
                workspaceScope.mismatch(freshDelivery.workspaceId)?.let { return@inTransaction it }
                val freshCampaign = database.campaignDao().findById(freshDelivery.campaignId)
                    ?: return@inTransaction AppResult.Failure(AppError.NotFound("campaign"))
                if (freshCampaign.status == CampaignStatus.CLOSED) {
                    return@inTransaction AppResult.Failure(AppError.Conflict("closed_campaign_yield_confirmed"))
                }
                val analysis = database.deliveryDao().findLiveAnalysis(deliveryId) ?: return@inTransaction AppResult.Success(Unit)
                tombstone(analysis, clock.nowInstant())
                AppResult.Success(Unit)
            }
        }
    }

    private suspend fun <T> safeWrite(block: suspend () -> AppResult<T>): AppResult<T> =
        try {
            block()
        } catch (e: AuthFailure) {
            AppResult.Failure(AppError.Permission("No hay sesión activa"))
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
