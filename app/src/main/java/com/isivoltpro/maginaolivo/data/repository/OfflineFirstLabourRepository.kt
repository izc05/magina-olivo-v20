package com.isivoltpro.maginaolivo.data.repository

import androidx.room.withTransaction
import com.isivoltpro.maginaolivo.core.common.AppError
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.core.dispatchers.AppDispatchers
import com.isivoltpro.maginaolivo.core.id.IdGenerator
import com.isivoltpro.maginaolivo.core.time.AppClock
import com.isivoltpro.maginaolivo.data.local.MaginaOlivoDatabase
import com.isivoltpro.maginaolivo.data.local.entity.HarvestEntity
import com.isivoltpro.maginaolivo.data.local.entity.HarvestLabourEntity
import com.isivoltpro.maginaolivo.data.local.entity.LocalMetadata
import com.isivoltpro.maginaolivo.data.local.entity.WorkerEntity
import com.isivoltpro.maginaolivo.data.local.model.CampaignStatus
import com.isivoltpro.maginaolivo.data.local.model.OutboxOperation
import com.isivoltpro.maginaolivo.data.local.model.SyncEntityType
import com.isivoltpro.maginaolivo.data.local.model.SyncStatus
import com.isivoltpro.maginaolivo.domain.labour.CountDraft
import com.isivoltpro.maginaolivo.domain.labour.CrewDraft
import com.isivoltpro.maginaolivo.domain.labour.LabourChange
import com.isivoltpro.maginaolivo.domain.labour.LabourEntry
import com.isivoltpro.maginaolivo.domain.labour.LabourRepository
import com.isivoltpro.maginaolivo.domain.labour.LabourRules
import com.isivoltpro.maginaolivo.domain.labour.LabourUnit
import com.isivoltpro.maginaolivo.domain.labour.Worker
import com.isivoltpro.maginaolivo.domain.workspace.WorkspaceRepository
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

private class LabourInvalid(val field: String, val code: String) : RuntimeException("$field:$code")

private class LabourConflict(val code: String) : RuntimeException(code)

/**
 * Phase 19D — offline-first jornales. Each labour line and each person is its own record with
 * its own outbox intent; a Jornada of a closed Campaign is history and takes no new labour.
 * No money is written here: a labour cost goes to the Expense ledger.
 */
class OfflineFirstLabourRepository(
    private val database: MaginaOlivoDatabase,
    private val workspaceRepository: WorkspaceRepository,
    private val clock: AppClock,
    private val idGenerator: IdGenerator,
    private val dispatchers: AppDispatchers,
) : LabourRepository {
    override fun observeWorkers(): Flow<List<Worker>> =
        database.labourDao().observeWorkers().map { rows -> rows.map { Worker(it.id, it.name) } }.flowOn(dispatchers.io)

    override suspend fun addWorker(name: String): AppResult<UUID> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return AppResult.Failure(AppError.Validation("name", "required"))
        if (trimmed.length > 60) return AppResult.Failure(AppError.Validation("name", "too_long"))
        val workspaceId = when (val workspace = workspaceRepository.ensureLocalWorkspace()) {
            is AppResult.Failure -> return workspace
            is AppResult.Success -> workspace.value
        }
        return inTransaction("add_worker") {
            database.labourDao().findWorkerByName(workspaceId, trimmed)?.let { return@inTransaction AppResult.Success(it.id) }
            val now = clock.nowInstant()
            val id = idGenerator.newId()
            database.labourDao().upsertWorker(WorkerEntity(id, workspaceId, trimmed, LocalMetadata(now, now, syncStatus = SyncStatus.PENDING)))
            database.enqueueCollapsed(idGenerator, SyncEntityType.WORKER, id, OutboxOperation.CREATE, now)
            AppResult.Success(id)
        }
    }

    override fun observeForHarvest(harvestId: UUID): Flow<List<LabourEntry>> =
        database.labourDao().observeForHarvest(harvestId).map { rows -> rows.map { it.toDomain() } }.flowOn(dispatchers.io)

    override fun observeForCampaign(campaignId: UUID): Flow<List<LabourEntry>> =
        database.labourDao().observeForCampaign(campaignId).map { rows -> rows.map { it.toDomain() } }.flowOn(dispatchers.io)

    override suspend fun recordCrew(draft: CrewDraft): AppResult<Int> {
        LabourRules.validate(draft)?.let { return AppResult.Failure(AppError.Validation(it.field, it.code)) }
        return inTransaction("record_crew") {
            val harvest = runningJornada(draft.harvestId)
            val already = database.labourDao().listForHarvest(harvest.id).mapNotNull { it.workerId }.toSet()
            if (draft.workerIds.any { it in already }) throw LabourInvalid("workers", "already_recorded")
            val now = clock.nowInstant()
            val rows = draft.workerIds.map { workerId ->
                val worker = database.labourDao().findWorker(workerId)
                    ?.takeIf { it.metadata.deletedAt == null && it.workspaceId == harvest.workspaceId }
                    ?: throw LabourInvalid("workers", "not_found")
                HarvestLabourEntity(
                    id = idGenerator.newId(),
                    workspaceId = harvest.workspaceId,
                    harvestId = harvest.id,
                    workerId = worker.id,
                    workerName = worker.name,
                    quantity = 1,
                    unit = draft.unit.name,
                    minutes = draft.minutes,
                    metadata = LocalMetadata(now, now, syncStatus = SyncStatus.PENDING),
                )
            }
            database.labourDao().upsertLabour(rows)
            rows.forEach { database.enqueueCollapsed(idGenerator, SyncEntityType.HARVEST_LABOUR, it.id, OutboxOperation.CREATE, now) }
            AppResult.Success(rows.size)
        }
    }

    override suspend fun recordCount(draft: CountDraft): AppResult<UUID> {
        LabourRules.validate(draft)?.let { return AppResult.Failure(AppError.Validation(it.field, it.code)) }
        return inTransaction("record_count") {
            val harvest = runningJornada(draft.harvestId)
            val now = clock.nowInstant()
            val row = HarvestLabourEntity(
                id = idGenerator.newId(),
                workspaceId = harvest.workspaceId,
                harvestId = harvest.id,
                quantity = draft.count,
                unit = draft.unit.name,
                minutes = draft.minutes,
                metadata = LocalMetadata(now, now, syncStatus = SyncStatus.PENDING),
            )
            database.labourDao().upsertLabour(listOf(row))
            database.enqueueCollapsed(idGenerator, SyncEntityType.HARVEST_LABOUR, row.id, OutboxOperation.CREATE, now)
            AppResult.Success(row.id)
        }
    }

    override suspend fun update(entryId: UUID, change: LabourChange): AppResult<Unit> {
        LabourRules.validate(change.quantity, change.unit, change.minutes)
            ?.let { return AppResult.Failure(AppError.Validation(it.field, it.code)) }
        return inTransaction("update_labour") {
            val current = liveEntry(entryId) ?: return@inTransaction AppResult.Failure(AppError.NotFound("labour"))
            runningJornada(current.harvestId)
            // A named line is one person: only its unit and hours change.
            if (current.workerId != null && change.quantity != 1) throw LabourInvalid("quantity", "one_person")
            val now = clock.nowInstant()
            database.labourDao().upsertLabour(
                listOf(
                    current.copy(
                        quantity = change.quantity,
                        unit = change.unit.name,
                        minutes = change.minutes,
                        metadata = current.metadata.next(now),
                    ),
                ),
            )
            database.enqueueCollapsed(idGenerator, SyncEntityType.HARVEST_LABOUR, entryId, OutboxOperation.UPDATE, now)
            AppResult.Success(Unit)
        }
    }

    override suspend fun remove(entryId: UUID): AppResult<Unit> =
        inTransaction("remove_labour") {
            val current = liveEntry(entryId) ?: return@inTransaction AppResult.Success(Unit)
            runningJornada(current.harvestId)
            val now = clock.nowInstant()
            database.labourDao().upsertLabour(listOf(current.copy(metadata = current.metadata.next(now).copy(deletedAt = now))))
            database.enqueueCollapsed(idGenerator, SyncEntityType.HARVEST_LABOUR, entryId, OutboxOperation.DELETE, now)
            AppResult.Success(Unit)
        }

    override suspend fun previousCrew(harvestId: UUID): List<UUID> = withContext(dispatchers.io) {
        val harvest = database.harvestDao().findById(harvestId) ?: return@withContext emptyList()
        val farmId = harvest.farmId ?: return@withContext emptyList()
        val rows = database.labourDao().listNamedCrewsBefore(farmId, harvestId, harvest.harvestDate)
        val latest = rows.firstOrNull()?.harvestId ?: return@withContext emptyList()
        rows.filter { it.harvestId == latest }.mapNotNull { it.workerId }.distinct()
            .filter { id -> database.labourDao().findWorker(id)?.metadata?.deletedAt == null }
    }

    private suspend fun runningJornada(harvestId: UUID): HarvestEntity {
        val harvest = database.harvestDao().findById(harvestId)?.takeIf { it.metadata.deletedAt == null }
            ?: throw LabourInvalid("harvestId", "not_found")
        val campaign = harvest.campaignId?.let { database.campaignDao().findById(it) }
        if (campaign == null || campaign.status !in RUNNING) throw LabourConflict("closed_campaign")
        return harvest
    }

    private suspend fun liveEntry(id: UUID): HarvestLabourEntity? =
        database.labourDao().findLabour(id)?.takeIf { it.metadata.deletedAt == null }

    private suspend fun <T> inTransaction(operation: String, block: suspend () -> AppResult<T>): AppResult<T> =
        withContext(dispatchers.io) {
            try {
                database.withTransaction { block() }
            } catch (error: LabourInvalid) {
                AppResult.Failure(AppError.Validation(error.field, error.code))
            } catch (error: LabourConflict) {
                AppResult.Failure(AppError.Conflict(error.code))
            } catch (error: Throwable) {
                AppResult.Failure(AppError.Storage(operation, error))
            }
        }

    private fun HarvestLabourEntity.toDomain() = LabourEntry(
        id = id,
        harvestId = harvestId,
        workerId = workerId,
        workerName = workerName,
        quantity = quantity,
        unit = LabourUnit.entries.firstOrNull { it.name == unit } ?: LabourUnit.FULL_DAY,
        minutes = minutes,
        version = metadata.version,
    )

    private fun LocalMetadata.next(now: Instant) =
        copy(updatedAt = now, version = version + 1, syncStatus = SyncStatus.PENDING)

    private companion object {
        val RUNNING = setOf(CampaignStatus.ACTIVE, CampaignStatus.HARVEST)
    }
}
