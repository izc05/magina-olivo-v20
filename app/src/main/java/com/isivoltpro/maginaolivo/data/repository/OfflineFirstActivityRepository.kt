package com.isivoltpro.maginaolivo.data.repository

import androidx.room.withTransaction
import com.isivoltpro.maginaolivo.core.common.AppError
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.core.dispatchers.AppDispatchers
import com.isivoltpro.maginaolivo.core.id.IdGenerator
import com.isivoltpro.maginaolivo.core.time.AppClock
import com.isivoltpro.maginaolivo.data.local.MaginaOlivoDatabase
import com.isivoltpro.maginaolivo.data.local.entity.ActivityEntity
import com.isivoltpro.maginaolivo.data.local.entity.ActivityParcelTargetEntity
import com.isivoltpro.maginaolivo.data.local.entity.LocalMetadata
import com.isivoltpro.maginaolivo.data.local.entity.SyncOutboxEntity
import com.isivoltpro.maginaolivo.data.local.model.ActivityStatus
import com.isivoltpro.maginaolivo.data.local.model.ActivityWithTargets
import com.isivoltpro.maginaolivo.data.local.model.FarmStatus
import com.isivoltpro.maginaolivo.data.local.model.OutboxOperation
import com.isivoltpro.maginaolivo.data.local.model.RecordStatus
import com.isivoltpro.maginaolivo.data.local.model.SyncEntityType
import com.isivoltpro.maginaolivo.data.local.model.SyncStatus
import com.isivoltpro.maginaolivo.domain.activity.Activity
import com.isivoltpro.maginaolivo.domain.activity.ActivityChanges
import com.isivoltpro.maginaolivo.domain.activity.ActivityParcelOption
import com.isivoltpro.maginaolivo.domain.activity.ActivityParcelTarget
import com.isivoltpro.maginaolivo.domain.activity.ActivityRepository
import com.isivoltpro.maginaolivo.domain.activity.ActivityType
import com.isivoltpro.maginaolivo.domain.activity.NewActivity
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Offline-first Activity engine.
 *
 * One canonical Activity may target many Parcels through `activity_parcels`; a
 * multi-parcel operation never duplicates the Activity header. The Activity and its
 * targets form one aggregate: they mutate in the same transaction, share one version
 * and collapse into a single outbox intent (RC1-NORMATIVE-ADDENDUM D5).
 */
class OfflineFirstActivityRepository(
    private val database: MaginaOlivoDatabase,
    private val clock: AppClock,
    private val idGenerator: IdGenerator,
    private val dispatchers: AppDispatchers,
) : ActivityRepository {
    override fun observeSelectableParcels(farmId: UUID): Flow<List<ActivityParcelOption>> =
        database.parcelDao().observeActive(farmId).map { rows ->
            rows.map { ActivityParcelOption(it.parcel.id, it.parcel.displayName, it.parcel.managedAreaM2) }
        }.flowOn(dispatchers.io)

    override fun observeForFarm(farmId: UUID): Flow<List<Activity>> =
        database.activityDao().observeForFarm(farmId).map { rows -> rows.map { it.toDomain() } }.flowOn(dispatchers.io)

    override fun observeForParcel(parcelId: UUID): Flow<List<Activity>> =
        database.activityDao().observeForParcel(parcelId).map { rows -> rows.map { it.toDomain() } }.flowOn(dispatchers.io)

    override fun observe(id: UUID): Flow<Activity?> =
        database.activityDao().observeWithTargets(id).map { it?.toDomain() }.flowOn(dispatchers.io)

    override suspend fun create(command: NewActivity): AppResult<UUID> {
        val description = command.description.trim()
        if (description.isEmpty()) return AppResult.Failure(AppError.Validation("description", "blank"))
        if (!command.asDraft && command.parcelIds.isEmpty()) {
            return AppResult.Failure(AppError.Validation("parcelIds", "empty"))
        }
        return withContext(dispatchers.io) {
            safely("create_activity") {
                val farm = database.farmDao().findById(command.farmId)
                    ?: return@safely AppResult.Failure(AppError.NotFound("farm"))
                if (farm.status != FarmStatus.ACTIVE || farm.metadata.deletedAt != null) {
                    return@safely AppResult.Failure(AppError.Conflict("archived_farm"))
                }
                val id = idGenerator.newId()
                val now = clock.nowInstant()
                database.activityDao().upsert(
                    ActivityEntity(
                        id = id,
                        workspaceId = farm.workspaceId,
                        campaignId = command.campaignId,
                        farmId = farm.id,
                        activityDate = command.activityDate,
                        type = command.type.name,
                        status = if (command.asDraft) ActivityStatus.DRAFT else ActivityStatus.PLANNED,
                        description = description,
                        notes = command.notes.normalized(),
                        metadata = pending(now),
                    ),
                )
                replaceTargets(id, command.parcelIds, now)
                enqueue(id, OutboxOperation.CREATE, now)
                AppResult.Success(id)
            }
        }
    }

    override suspend fun update(id: UUID, changes: ActivityChanges): AppResult<Unit> {
        val description = changes.description.trim()
        if (description.isEmpty()) return AppResult.Failure(AppError.Validation("description", "blank"))
        return mutate(id, "update_activity") { current, now ->
            if (current.status !in EDITABLE) return@mutate conflict("protected_activity")
            if (current.status == ActivityStatus.PLANNED && changes.parcelIds.isEmpty()) {
                return@mutate AppResult.Failure(AppError.Validation("parcelIds", "empty"))
            }
            database.activityDao().upsert(
                current.copy(
                    type = changes.type.name,
                    activityDate = changes.activityDate,
                    description = description,
                    notes = changes.notes.normalized(),
                    metadata = current.metadata.next(now),
                ),
            )
            replaceTargets(id, changes.parcelIds, now)
            enqueue(id, OutboxOperation.UPDATE, now)
            AppResult.Success(Unit)
        }
    }

    override suspend fun plan(id: UUID): AppResult<Unit> = transition(id, setOf(ActivityStatus.DRAFT), ActivityStatus.PLANNED, "plan_activity", requireTargets = true)

    override suspend fun complete(id: UUID): AppResult<Unit> = transition(id, setOf(ActivityStatus.PLANNED), ActivityStatus.COMPLETED, "complete_activity", requireTargets = true)

    override suspend fun cancel(id: UUID): AppResult<Unit> = transition(id, setOf(ActivityStatus.DRAFT, ActivityStatus.PLANNED), ActivityStatus.CANCELLED, "cancel_activity", requireTargets = false)

    override suspend fun reopen(id: UUID): AppResult<Unit> = transition(id, setOf(ActivityStatus.COMPLETED, ActivityStatus.CANCELLED), ActivityStatus.PLANNED, "reopen_activity", requireTargets = true)

    override suspend fun archive(id: UUID): AppResult<Unit> = mutate(id, "archive_activity", allowArchived = true) { current, now ->
        if (current.metadata.deletedAt != null) return@mutate AppResult.Success(Unit)
        if (current.status !in ARCHIVABLE) return@mutate conflict("protected_activity")
        database.activityDao().upsert(current.copy(metadata = current.metadata.next(now).copy(deletedAt = now)))
        enqueue(id, OutboxOperation.DELETE, now)
        AppResult.Success(Unit)
    }

    private suspend fun transition(
        id: UUID,
        from: Set<ActivityStatus>,
        to: ActivityStatus,
        operation: String,
        requireTargets: Boolean,
    ) = mutate(id, operation) { current, now ->
        if (current.status !in from) return@mutate conflict("illegal_activity_transition")
        if (requireTargets && database.activityDao().countTargets(id) == 0) {
            return@mutate AppResult.Failure(AppError.Validation("parcelIds", "empty"))
        }
        database.activityDao().upsert(current.copy(status = to, metadata = current.metadata.next(now)))
        enqueue(id, OutboxOperation.UPDATE, now)
        AppResult.Success(Unit)
    }

    private suspend fun replaceTargets(activityId: UUID, parcelIds: Set<UUID>, now: Instant) {
        val activity = database.activityDao().findById(activityId) ?: error("activity missing")
        val farmId = activity.farmId ?: throw InvalidSelection("activity_without_farm")
        val rows = parcelIds.sortedBy(UUID::toString).map { parcelId ->
            val parcel = database.parcelDao().findById(parcelId) ?: throw InvalidSelection("parcel_not_found")
            val membership = database.parcelDao().findCurrentMembership(parcelId)
            if (membership?.farmId != farmId || parcel.status != RecordStatus.ACTIVE ||
                parcel.metadata.deletedAt != null || parcel.workspaceId != activity.workspaceId
            ) {
                throw InvalidSelection("parcel_not_in_farm")
            }
            ActivityParcelTargetEntity(
                id = idGenerator.newId(),
                workspaceId = activity.workspaceId,
                activityId = activityId,
                parcelId = parcel.id,
                parcelNameAtTarget = parcel.displayName,
                areaAffectedM2 = parcel.managedAreaM2,
                metadata = pending(now),
            )
        }
        database.activityDao().deleteTargets(activityId)
        if (rows.isNotEmpty()) database.activityDao().upsertTargets(rows)
    }

    private suspend fun enqueue(id: UUID, requested: OutboxOperation, now: Instant) {
        val existing = database.syncOutboxDao().listForEntity(SyncEntityType.ACTIVITY, id)
        val operation =
            if (existing.any { it.operation == OutboxOperation.CREATE } && requested != OutboxOperation.DELETE) {
                OutboxOperation.CREATE
            } else {
                requested
            }
        database.syncOutboxDao().deletePendingForEntity(SyncEntityType.ACTIVITY, id)
        database.syncOutboxDao().insert(
            SyncOutboxEntity(idGenerator.newId(), SyncEntityType.ACTIVITY, id, operation, 1, createdAt = now, updatedAt = now),
        )
    }

    private suspend fun mutate(
        id: UUID,
        operation: String,
        allowArchived: Boolean = false,
        block: suspend (ActivityEntity, Instant) -> AppResult<Unit>,
    ): AppResult<Unit> =
        withContext(dispatchers.io) {
            safely(operation) {
                val current = database.activityDao().findById(id)
                    ?: return@safely AppResult.Failure(AppError.NotFound("activity"))
                if (!allowArchived && current.metadata.deletedAt != null) return@safely conflict("archived_activity")
                block(current, clock.nowInstant())
            }
        }

    private suspend fun <T> safely(operation: String, block: suspend () -> AppResult<T>): AppResult<T> =
        try {
            database.withTransaction { block() }
        } catch (error: InvalidSelection) {
            AppResult.Failure(AppError.Validation("parcelIds", error.message ?: "invalid"))
        } catch (error: Throwable) {
            AppResult.Failure(AppError.Storage(operation, error))
        }

    private fun pending(now: Instant) = LocalMetadata(now, now, syncStatus = SyncStatus.PENDING)

    private fun LocalMetadata.next(now: Instant) = copy(updatedAt = now, version = version + 1, syncStatus = SyncStatus.PENDING)

    private fun String?.normalized() = this?.trim()?.ifEmpty { null }

    private fun conflict(code: String): AppResult.Failure = AppResult.Failure(AppError.Conflict(code))

    private class InvalidSelection(message: String) : RuntimeException(message)

    private fun ActivityWithTargets.toDomain() =
        Activity(
            id = activity.id,
            workspaceId = activity.workspaceId,
            farmId = activity.farmId,
            campaignId = activity.campaignId,
            type = runCatching { ActivityType.valueOf(activity.type) }.getOrDefault(ActivityType.OTHER),
            status = activity.status,
            activityDate = activity.activityDate,
            description = activity.description,
            notes = activity.notes,
            targets = targets.map {
                ActivityParcelTarget(it.parcelId, it.parcelNameAtTarget, it.areaAffectedM2, it.notes)
            },
            version = activity.metadata.version,
        )

    private companion object {
        val EDITABLE = setOf(ActivityStatus.DRAFT, ActivityStatus.PLANNED)
        val ARCHIVABLE = setOf(ActivityStatus.DRAFT, ActivityStatus.CANCELLED)
    }
}
