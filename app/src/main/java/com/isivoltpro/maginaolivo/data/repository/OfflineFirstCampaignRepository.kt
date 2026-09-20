package com.isivoltpro.maginaolivo.data.repository

import androidx.room.withTransaction
import com.isivoltpro.maginaolivo.core.common.AppError
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.core.dispatchers.AppDispatchers
import com.isivoltpro.maginaolivo.core.id.IdGenerator
import com.isivoltpro.maginaolivo.core.time.AppClock
import com.isivoltpro.maginaolivo.data.local.MaginaOlivoDatabase
import com.isivoltpro.maginaolivo.data.local.entity.CampaignEntity
import com.isivoltpro.maginaolivo.data.local.entity.CampaignParcelSnapshotEntity
import com.isivoltpro.maginaolivo.data.local.entity.LocalMetadata
import com.isivoltpro.maginaolivo.data.local.entity.SyncOutboxEntity
import com.isivoltpro.maginaolivo.data.local.model.CampaignStatus
import com.isivoltpro.maginaolivo.data.local.model.CampaignWithSnapshots
import com.isivoltpro.maginaolivo.data.local.model.FarmStatus
import com.isivoltpro.maginaolivo.data.local.model.OutboxOperation
import com.isivoltpro.maginaolivo.data.local.model.RecordStatus
import com.isivoltpro.maginaolivo.data.local.model.SyncEntityType
import com.isivoltpro.maginaolivo.data.local.model.SyncStatus
import com.isivoltpro.maginaolivo.domain.campaign.Campaign
import com.isivoltpro.maginaolivo.domain.campaign.CampaignParcelSnapshot
import com.isivoltpro.maginaolivo.domain.campaign.CampaignParcelOption
import com.isivoltpro.maginaolivo.domain.campaign.CampaignPreparationChanges
import com.isivoltpro.maginaolivo.domain.campaign.CampaignRepository
import com.isivoltpro.maginaolivo.domain.campaign.NewCampaign
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class OfflineFirstCampaignRepository(
    private val database: MaginaOlivoDatabase,
    private val clock: AppClock,
    private val idGenerator: IdGenerator,
    private val dispatchers: AppDispatchers,
) : CampaignRepository {
    override fun observeSelectableParcels(farmId: UUID): Flow<List<CampaignParcelOption>> =
        database.parcelDao().observeActive(farmId).map { rows ->
            rows.map { CampaignParcelOption(it.parcel.id, it.parcel.displayName, it.parcel.managedAreaM2) }
        }.flowOn(dispatchers.io)

    override fun observeForFarm(farmId: UUID): Flow<List<Campaign>> =
        database.campaignDao().observeForFarm(farmId).map { rows ->
            rows.map { campaign -> CampaignWithSnapshots(campaign, database.campaignDao().listSnapshots(campaign.id)).toDomain() }
        }.flowOn(dispatchers.io)

    override fun observe(id: UUID): Flow<Campaign?> =
        database.campaignDao().observeWithSnapshots(id).map { it?.toDomain() }.flowOn(dispatchers.io)

    override suspend fun create(command: NewCampaign): AppResult<UUID> {
        val name = command.name.trim()
        if (name.isEmpty()) return AppResult.Failure(AppError.Validation("name", "blank"))
        return withContext(dispatchers.io) {
            safely("create_campaign") {
                val farm = database.farmDao().findById(command.farmId)
                    ?: return@safely AppResult.Failure(AppError.NotFound("farm"))
                if (farm.status != FarmStatus.ACTIVE || farm.metadata.deletedAt != null) {
                    return@safely AppResult.Failure(AppError.Conflict("archived_farm"))
                }
                val id = idGenerator.newId()
                val now = clock.nowInstant()
                database.campaignDao().upsert(CampaignEntity(id, farm.workspaceId, farm.id, name, command.startDate,
                    status = CampaignStatus.PREPARATION, notes = command.notes.normalized(), metadata = pending(now)))
                replaceSnapshots(id, command.parcelIds, now)
                enqueue(id, OutboxOperation.CREATE, now)
                AppResult.Success(id)
            }
        }
    }

    override suspend fun updatePreparation(id: UUID, changes: CampaignPreparationChanges): AppResult<Unit> {
        val name = changes.name.trim()
        if (name.isEmpty()) return AppResult.Failure(AppError.Validation("name", "blank"))
        return mutate(id, "update_campaign") { current, now ->
            if (current.status != CampaignStatus.PREPARATION) return@mutate conflict("campaign_not_in_preparation")
            database.campaignDao().upsert(current.copy(name = name, startDate = changes.startDate,
                notes = changes.notes.normalized(), metadata = current.metadata.next(now)))
            replaceSnapshots(id, changes.parcelIds, now)
            enqueue(id, OutboxOperation.UPDATE, now)
            AppResult.Success(Unit)
        }
    }

    override suspend fun activate(id: UUID): AppResult<Unit> = mutate(id, "activate_campaign") { current, now ->
        if (current.status != CampaignStatus.PREPARATION) return@mutate conflict("illegal_campaign_transition")
        val parcelIds = database.campaignDao().listSnapshots(id).map { it.parcelId }.toSet()
        if (parcelIds.isEmpty()) return@mutate AppResult.Failure(AppError.Validation("parcelIds", "empty"))
        if (database.campaignDao().countOtherCurrent(current.farmId, id) > 0) return@mutate conflict("active_campaign_exists")
        replaceSnapshots(id, parcelIds, now)
        database.campaignDao().upsert(current.copy(status = CampaignStatus.ACTIVE, metadata = current.metadata.next(now)))
        enqueue(id, OutboxOperation.UPDATE, now)
        AppResult.Success(Unit)
    }

    override suspend fun markHarvest(id: UUID) = transition(id, CampaignStatus.ACTIVE, CampaignStatus.HARVEST, "mark_harvest")

    override suspend fun close(id: UUID, endDate: LocalDate): AppResult<Unit> = mutate(id, "close_campaign") { current, now ->
        if (current.status !in setOf(CampaignStatus.ACTIVE, CampaignStatus.HARVEST)) return@mutate conflict("illegal_campaign_transition")
        if (endDate.isBefore(current.startDate)) return@mutate AppResult.Failure(AppError.Validation("endDate", "before_start"))
        database.campaignDao().upsert(current.copy(status = CampaignStatus.CLOSED, endDate = endDate, metadata = current.metadata.next(now)))
        enqueue(id, OutboxOperation.UPDATE, now)
        AppResult.Success(Unit)
    }

    override suspend fun reopen(id: UUID): AppResult<Unit> = mutate(id, "reopen_campaign") { current, now ->
        if (current.status != CampaignStatus.CLOSED) return@mutate conflict("illegal_campaign_transition")
        if (database.campaignDao().countOtherCurrent(current.farmId, id) > 0) return@mutate conflict("active_campaign_exists")
        database.campaignDao().upsert(current.copy(status = CampaignStatus.HARVEST, endDate = null, metadata = current.metadata.next(now)))
        enqueue(id, OutboxOperation.UPDATE, now)
        AppResult.Success(Unit)
    }

    override suspend fun archivePreparation(id: UUID): AppResult<Unit> = mutate(id, "archive_campaign") { current, now ->
        if (current.status != CampaignStatus.PREPARATION) return@mutate conflict("protected_campaign")
        database.campaignDao().upsert(current.copy(metadata = current.metadata.next(now).copy(deletedAt = now)))
        enqueue(id, OutboxOperation.DELETE, now)
        AppResult.Success(Unit)
    }

    private suspend fun transition(id: UUID, from: CampaignStatus, to: CampaignStatus, operation: String) = mutate(id, operation) { current, now ->
        if (current.status != from) return@mutate conflict("illegal_campaign_transition")
        database.campaignDao().upsert(current.copy(status = to, metadata = current.metadata.next(now)))
        enqueue(id, OutboxOperation.UPDATE, now)
        AppResult.Success(Unit)
    }

    private suspend fun replaceSnapshots(campaignId: UUID, parcelIds: Set<UUID>, now: Instant) {
        val campaign = database.campaignDao().findById(campaignId) ?: error("campaign missing")
        val farm = database.farmDao().findById(campaign.farmId) ?: error("farm missing")
        val rows = parcelIds.sortedBy(UUID::toString).map { parcelId ->
            val parcel = database.parcelDao().findById(parcelId) ?: throw InvalidSelection("parcel_not_found")
            val membership = database.parcelDao().findCurrentMembership(parcelId)
            if (membership?.farmId != farm.id || parcel.status != RecordStatus.ACTIVE || parcel.metadata.deletedAt != null || parcel.workspaceId != farm.workspaceId) {
                throw InvalidSelection("parcel_not_in_farm")
            }
            CampaignParcelSnapshotEntity(idGenerator.newId(), farm.workspaceId, campaignId, parcel.id, farm.id,
                farm.name, parcel.displayName, parcel.managedAreaM2, parcel.cadastralReference, parcel.geometryGeoJson, pending(now))
        }
        database.campaignDao().deleteSnapshots(campaignId)
        if (rows.isNotEmpty()) database.campaignDao().upsertSnapshots(rows)
    }

    private suspend fun enqueue(id: UUID, requested: OutboxOperation, now: Instant) {
        val existing = database.syncOutboxDao().listForEntity(SyncEntityType.CAMPAIGN, id)
        val operation = if (existing.any { it.operation == OutboxOperation.CREATE } && requested != OutboxOperation.DELETE) OutboxOperation.CREATE else requested
        database.syncOutboxDao().deletePendingForEntity(SyncEntityType.CAMPAIGN, id)
        database.syncOutboxDao().insert(SyncOutboxEntity(idGenerator.newId(), SyncEntityType.CAMPAIGN, id, operation, 1, createdAt = now, updatedAt = now))
    }

    private suspend fun mutate(id: UUID, operation: String, block: suspend (CampaignEntity, Instant) -> AppResult<Unit>): AppResult<Unit> =
        withContext(dispatchers.io) { safely(operation) {
            val current = database.campaignDao().findById(id) ?: return@safely AppResult.Failure(AppError.NotFound("campaign"))
            block(current, clock.nowInstant())
        } }

    private suspend fun <T> safely(operation: String, block: suspend () -> AppResult<T>): AppResult<T> =
        try { database.withTransaction { block() } }
        catch (error: InvalidSelection) { AppResult.Failure(AppError.Validation("parcelIds", error.message ?: "invalid")) }
        catch (error: Throwable) { AppResult.Failure(AppError.Storage(operation, error)) }

    private fun pending(now: Instant) = LocalMetadata(now, now, syncStatus = SyncStatus.PENDING)
    private fun LocalMetadata.next(now: Instant) = copy(updatedAt = now, version = version + 1, syncStatus = SyncStatus.PENDING)
    private fun String?.normalized() = this?.trim()?.ifEmpty { null }
    private fun conflict(code: String): AppResult.Failure = AppResult.Failure(AppError.Conflict(code))
    private class InvalidSelection(message: String) : RuntimeException(message)
    private fun CampaignWithSnapshots.toDomain() = Campaign(campaign.id, campaign.workspaceId, campaign.farmId,
        campaign.name, campaign.startDate, campaign.endDate, campaign.status, campaign.notes,
        snapshots.map { CampaignParcelSnapshot(it.parcelId, it.farmNameAtStart, it.parcelNameAtStart,
            it.managedAreaM2AtStart, it.cadastralReferenceAtStart, it.geometryGeoJsonSnapshot) }, campaign.metadata.version)
}
