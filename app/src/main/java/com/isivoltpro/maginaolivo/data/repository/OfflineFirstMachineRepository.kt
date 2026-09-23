package com.isivoltpro.maginaolivo.data.repository

import androidx.room.withTransaction
import com.isivoltpro.maginaolivo.core.common.AppError
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.core.dispatchers.AppDispatchers
import com.isivoltpro.maginaolivo.core.id.IdGenerator
import com.isivoltpro.maginaolivo.core.time.AppClock
import com.isivoltpro.maginaolivo.data.local.MaginaOlivoDatabase
import com.isivoltpro.maginaolivo.data.local.entity.LocalMetadata
import com.isivoltpro.maginaolivo.data.local.entity.MachineEntity
import com.isivoltpro.maginaolivo.data.local.model.OutboxOperation
import com.isivoltpro.maginaolivo.data.local.model.SyncEntityType
import com.isivoltpro.maginaolivo.data.local.model.SyncStatus
import com.isivoltpro.maginaolivo.domain.machinery.Machine
import com.isivoltpro.maginaolivo.domain.machinery.MachineCategory
import com.isivoltpro.maginaolivo.domain.machinery.MachineDraft
import com.isivoltpro.maginaolivo.domain.machinery.MachineRepository
import com.isivoltpro.maginaolivo.domain.machinery.MachineRules
import com.isivoltpro.maginaolivo.domain.machinery.MachineUse
import com.isivoltpro.maginaolivo.domain.workspace.WorkspaceRepository
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Offline-first machinery. A machine is its own aggregate with one outbox intent; it is
 * retired (archived) rather than deleted, so every Activity that named it still reads true.
 */
class OfflineFirstMachineRepository(
    private val database: MaginaOlivoDatabase,
    private val workspaceRepository: WorkspaceRepository,
    private val clock: AppClock,
    private val idGenerator: IdGenerator,
    private val dispatchers: AppDispatchers,
) : MachineRepository {
    override fun observeActive(): Flow<List<Machine>> =
        database.machineDao().observeByStatus(ACTIVE).map { rows -> rows.map { it.toDomain() } }.flowOn(dispatchers.io)

    override fun observeArchived(): Flow<List<Machine>> =
        database.machineDao().observeByStatus(ARCHIVED).map { rows -> rows.map { it.toDomain() } }.flowOn(dispatchers.io)

    override fun observe(id: UUID): Flow<Machine?> =
        database.machineDao().observeById(id).map { it?.toDomain() }.flowOn(dispatchers.io)

    override fun observeUses(id: UUID): Flow<List<MachineUse>> =
        database.machineDao().observeUses(id).map { rows ->
            rows.map { row ->
                MachineUse(
                    activityId = row.activityId,
                    activityDate = row.activityDate,
                    description = row.description,
                    hoursUsed = row.usageHours
                        ?: if (row.startHours != null && row.endHours != null) row.endHours - row.startHours else null,
                )
            }
        }.flowOn(dispatchers.io)

    override suspend fun create(draft: MachineDraft): AppResult<UUID> {
        MachineRules.validate(draft)?.let { return AppResult.Failure(AppError.Validation(it.field, it.code)) }
        val workspaceId = when (val workspace = workspaceRepository.ensureLocalWorkspace()) {
            is AppResult.Failure -> return workspace
            is AppResult.Success -> workspace.value
        }
        return inTransaction("create_machine") {
            val name = draft.name.trim()
            if (database.machineDao().findByName(workspaceId, name) != null) {
                return@inTransaction AppResult.Failure(AppError.Conflict("duplicate_machine"))
            }
            val now = clock.nowInstant()
            val id = idGenerator.newId()
            database.machineDao().upsert(
                MachineEntity(
                    id = id,
                    workspaceId = workspaceId,
                    name = name,
                    category = draft.category.name,
                    make = draft.make.normalized(),
                    model = draft.model.normalized(),
                    registrationOrSerial = draft.registrationOrSerial.normalized(),
                    currentHours = draft.currentHours,
                    notes = draft.notes.normalized(),
                    status = ACTIVE,
                    metadata = LocalMetadata(now, now, syncStatus = SyncStatus.PENDING),
                ),
            )
            database.enqueueCollapsed(idGenerator, SyncEntityType.MACHINE, id, OutboxOperation.CREATE, now)
            AppResult.Success(id)
        }
    }

    override suspend fun update(id: UUID, draft: MachineDraft): AppResult<Unit> {
        MachineRules.validate(draft)?.let { return AppResult.Failure(AppError.Validation(it.field, it.code)) }
        return mutate(id, "update_machine") { current, now ->
            val name = draft.name.trim()
            val clash = database.machineDao().findByName(current.workspaceId, name)
            if (clash != null && clash.id != id) return@mutate AppResult.Failure(AppError.Conflict("duplicate_machine"))
            write(
                current.copy(
                    name = name,
                    category = draft.category.name,
                    make = draft.make.normalized(),
                    model = draft.model.normalized(),
                    registrationOrSerial = draft.registrationOrSerial.normalized(),
                    currentHours = draft.currentHours,
                    notes = draft.notes.normalized(),
                    metadata = current.metadata.next(now),
                ),
            )
        }
    }

    override suspend fun archive(id: UUID): AppResult<Unit> = setStatus(id, ARCHIVED, "archive_machine")

    override suspend fun restore(id: UUID): AppResult<Unit> = setStatus(id, ACTIVE, "restore_machine")

    private suspend fun setStatus(id: UUID, status: String, operation: String) = mutate(id, operation) { current, now ->
        if (current.status == status) return@mutate AppResult.Success(Unit)
        write(current.copy(status = status, metadata = current.metadata.next(now)))
    }

    private suspend fun write(row: MachineEntity): AppResult<Unit> {
        database.machineDao().upsert(row)
        database.enqueueCollapsed(idGenerator, SyncEntityType.MACHINE, row.id, OutboxOperation.UPDATE, row.metadata.updatedAt)
        return AppResult.Success(Unit)
    }

    private suspend fun mutate(
        id: UUID,
        operation: String,
        block: suspend (MachineEntity, Instant) -> AppResult<Unit>,
    ): AppResult<Unit> = inTransaction(operation) {
        val current = database.machineDao().findById(id)?.takeIf { it.metadata.deletedAt == null }
            ?: return@inTransaction AppResult.Failure(AppError.NotFound("machine"))
        block(current, clock.nowInstant())
    }

    private suspend fun <T> inTransaction(operation: String, block: suspend () -> AppResult<T>): AppResult<T> =
        withContext(dispatchers.io) {
            try {
                database.withTransaction { block() }
            } catch (error: Throwable) {
                AppResult.Failure(AppError.Storage(operation, error))
            }
        }

    private fun MachineEntity.toDomain() = Machine(
        id = id,
        name = name,
        category = MachineCategory.entries.firstOrNull { it.name == category } ?: MachineCategory.OTHER,
        make = make,
        model = model,
        registrationOrSerial = registrationOrSerial,
        currentHours = currentHours,
        notes = notes,
        archived = status != ACTIVE,
        version = metadata.version,
    )

    private fun LocalMetadata.next(now: Instant) =
        copy(updatedAt = now, version = version + 1, syncStatus = SyncStatus.PENDING)

    private fun String?.normalized() = this?.trim()?.ifEmpty { null }

    private companion object {
        const val ACTIVE = "ACTIVE"
        const val ARCHIVED = "ARCHIVED"
    }
}
