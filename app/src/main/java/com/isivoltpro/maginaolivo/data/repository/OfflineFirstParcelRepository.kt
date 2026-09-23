package com.isivoltpro.maginaolivo.data.repository

import androidx.room.withTransaction
import com.isivoltpro.maginaolivo.core.common.AppError
import com.isivoltpro.maginaolivo.core.common.AppPreconditions
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.core.dispatchers.AppDispatchers
import com.isivoltpro.maginaolivo.core.id.IdGenerator
import com.isivoltpro.maginaolivo.core.time.AppClock
import com.isivoltpro.maginaolivo.data.local.MaginaOlivoDatabase
import com.isivoltpro.maginaolivo.data.local.entity.FarmParcelMembershipEntity
import com.isivoltpro.maginaolivo.data.local.entity.LocalMetadata
import com.isivoltpro.maginaolivo.data.local.entity.ParcelEntity
import com.isivoltpro.maginaolivo.data.local.entity.SyncOutboxEntity
import com.isivoltpro.maginaolivo.data.local.model.FarmStatus
import com.isivoltpro.maginaolivo.data.local.model.OutboxOperation
import com.isivoltpro.maginaolivo.data.local.model.ParcelRow
import com.isivoltpro.maginaolivo.data.local.model.RecordStatus
import com.isivoltpro.maginaolivo.data.local.model.SyncEntityType
import com.isivoltpro.maginaolivo.data.local.model.SyncStatus
import com.isivoltpro.maginaolivo.domain.parcel.NewParcel
import com.isivoltpro.maginaolivo.domain.parcel.Parcel
import com.isivoltpro.maginaolivo.domain.parcel.ParcelChanges
import com.isivoltpro.maginaolivo.domain.parcel.ParcelMembership
import com.isivoltpro.maginaolivo.domain.parcel.ParcelRepository
import com.isivoltpro.maginaolivo.domain.parcel.ParcelSource
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class OfflineFirstParcelRepository(
    private val database: MaginaOlivoDatabase,
    private val clock: AppClock,
    private val idGenerator: IdGenerator,
    private val dispatchers: AppDispatchers,
) : ParcelRepository {
    override fun observeActive(farmId: UUID): Flow<List<Parcel>> =
        database.parcelDao().observeActive(farmId).map { rows -> rows.map { it.toDomain() } }.flowOn(dispatchers.io)

    override fun observeArchived(farmId: UUID): Flow<List<Parcel>> =
        database.parcelDao().observeArchived(farmId).map { rows -> rows.map { it.toDomain() } }.flowOn(dispatchers.io)

    override fun observeById(parcelId: UUID): Flow<Parcel?> =
        database.parcelDao().observeById(parcelId).map { it?.toDomain() }.flowOn(dispatchers.io)

    override suspend fun create(command: NewParcel): AppResult<UUID> {
        val name = normalizedName(command.displayName)
        if (name is AppResult.Failure) return name
        validateArea(command.cadastralAreaM2)?.let { return it }
        validateArea(command.managedAreaM2)?.let { return it }
        validateGeometry(command.geometryGeoJson)?.let { return it }
        if (command.source == ParcelSource.CATASTRO &&
            (command.cadastralReference.isNullOrBlank() || command.geometryGeoJson.isNullOrBlank())
        ) return AppResult.Failure(AppError.Validation("catastro", "identity_and_geometry_required"))
        return withContext(dispatchers.io) {
            val now = clock.nowInstant()
            runCatching {
                database.withTransaction {
                    val farm = database.farmDao().findById(command.farmId)
                        ?: return@withTransaction AppResult.Failure(AppError.NotFound("farm"))
                    if (farm.status != FarmStatus.ACTIVE || farm.metadata.deletedAt != null) {
                        return@withTransaction AppResult.Failure(AppError.Conflict("archived_farm"))
                    }
                    if (command.source == ParcelSource.CATASTRO &&
                        database.parcelDao().findActiveByCadastralReference(
                            farm.workspaceId,
                            command.cadastralReference!!.trim().uppercase(),
                        ) != null
                    ) return@withTransaction AppResult.Failure(AppError.Conflict("duplicate_cadastral_reference"))
                    val parcelId = idGenerator.newId()
                    database.parcelDao().upsert(
                        ParcelEntity(
                            id = parcelId,
                            workspaceId = farm.workspaceId,
                            displayName = (name as AppResult.Success).value,
                            cadastralReference = command.cadastralReference.normalized()?.let {
                                if (command.source == ParcelSource.CATASTRO) it.uppercase() else it
                            },
                            cadastralPolygon = command.cadastralPolygon.normalized(),
                            cadastralParcel = command.cadastralParcel.normalized(),
                            municipality = command.municipality.normalized(),
                            province = command.province.normalized(),
                            source = command.source.name,
                            geometryGeoJson = command.geometryGeoJson.normalized(),
                            cadastralAreaM2 = command.cadastralAreaM2,
                            managedAreaM2 = command.managedAreaM2,
                            notes = command.notes.normalized(),
                            metadata = pendingMetadata(now),
                        ),
                    )
                    database.parcelDao().upsertMembership(
                        FarmParcelMembershipEntity(
                            id = idGenerator.newId(),
                            workspaceId = farm.workspaceId,
                            farmId = farm.id,
                            parcelId = parcelId,
                            validFrom = now,
                            metadata = pendingMetadata(now),
                        ),
                    )
                    enqueue(parcelId, OutboxOperation.CREATE, now)
                    AppResult.Success(parcelId)
                }
            }.getOrElse { AppResult.Failure(AppError.Storage("create_parcel", it)) }
        }
    }

    override suspend fun update(parcelId: UUID, changes: ParcelChanges): AppResult<Unit> {
        val name = normalizedName(changes.displayName)
        if (name is AppResult.Failure) return name
        validateArea(changes.cadastralAreaM2)?.let { return it }
        validateArea(changes.managedAreaM2)?.let { return it }
        validateGeometry(changes.geometryGeoJson)?.let { return it }
        return mutate(parcelId, "update_parcel") { current, now ->
            if (current.metadata.deletedAt != null) return@mutate AppResult.Failure(AppError.Conflict("archived_parcel"))
            database.parcelDao().upsert(
                current.copy(
                    displayName = (name as AppResult.Success).value,
                    cadastralReference = changes.cadastralReference.normalized(),
                    cadastralPolygon = changes.cadastralPolygon.normalized(),
                    cadastralParcel = changes.cadastralParcel.normalized(),
                    municipality = changes.municipality.normalized(),
                    province = changes.province.normalized(),
                    geometryGeoJson = changes.geometryGeoJson.normalized(),
                    cadastralAreaM2 = changes.cadastralAreaM2,
                    managedAreaM2 = changes.managedAreaM2,
                    notes = changes.notes.normalized(),
                    metadata = current.metadata.next(now),
                ),
            )
            enqueue(parcelId, OutboxOperation.UPDATE, now)
            AppResult.Success(Unit)
        }
    }

    override suspend fun archive(parcelId: UUID): AppResult<Unit> = mutate(parcelId, "archive_parcel") { current, now ->
        if (current.metadata.deletedAt != null) return@mutate AppResult.Success(Unit)
        database.parcelDao().findCurrentMembership(parcelId)?.let { membership ->
            database.parcelDao().upsertMembership(
                membership.copy(validUntil = now, metadata = membership.metadata.next(now)),
            )
        }
        database.parcelDao().upsert(
            current.copy(
                status = RecordStatus.ARCHIVED,
                metadata = current.metadata.next(now).copy(deletedAt = now),
            ),
        )
        enqueue(parcelId, OutboxOperation.DELETE, now)
        AppResult.Success(Unit)
    }

    override suspend fun restore(parcelId: UUID, farmId: UUID): AppResult<Unit> = mutate(parcelId, "restore_parcel") { current, now ->
        val farm = database.farmDao().findById(farmId)
            ?: return@mutate AppResult.Failure(AppError.NotFound("farm"))
        if (farm.status != FarmStatus.ACTIVE || farm.metadata.deletedAt != null || farm.workspaceId != current.workspaceId) {
            return@mutate AppResult.Failure(AppError.Conflict("invalid_farm"))
        }
        database.parcelDao().findCurrentMembership(parcelId)?.let { membership ->
            database.parcelDao().upsertMembership(
                membership.copy(validUntil = now, metadata = membership.metadata.next(now)),
            )
        }
        database.parcelDao().upsertMembership(
            FarmParcelMembershipEntity(
                id = idGenerator.newId(),
                workspaceId = current.workspaceId,
                farmId = farmId,
                parcelId = parcelId,
                validFrom = now,
                metadata = pendingMetadata(now),
            ),
        )
        database.parcelDao().upsert(
            current.copy(status = RecordStatus.ACTIVE, metadata = current.metadata.next(now).copy(deletedAt = null)),
        )
        enqueue(parcelId, OutboxOperation.UPDATE, now)
        AppResult.Success(Unit)
    }

    override suspend fun membershipHistory(parcelId: UUID): List<ParcelMembership> = withContext(dispatchers.io) {
        database.parcelDao().listMemberships(parcelId).map {
            ParcelMembership(it.id, it.farmId, it.parcelId, it.validFrom, it.validUntil)
        }
    }

    private suspend fun mutate(
        parcelId: UUID,
        operation: String,
        block: suspend (ParcelEntity, Instant) -> AppResult<Unit>,
    ): AppResult<Unit> = withContext(dispatchers.io) {
        runCatching {
            database.withTransaction {
                val current = database.parcelDao().findById(parcelId)
                    ?: return@withTransaction AppResult.Failure(AppError.NotFound("parcel"))
                block(current, clock.nowInstant())
            }
        }.getOrElse { AppResult.Failure(AppError.Storage(operation, it)) }
    }

    private suspend fun enqueue(parcelId: UUID, operation: OutboxOperation, now: Instant) {
        database.syncOutboxDao().insert(
            SyncOutboxEntity(
                id = idGenerator.newId(), entityType = SyncEntityType.PARCEL, entityId = parcelId,
                operation = operation, payloadVersion = 1, createdAt = now, updatedAt = now,
            ),
        )
    }

    private fun normalizedName(value: String) = AppPreconditions.nonBlank(value, "displayName")
    private fun String?.normalized() = this?.trim()?.ifEmpty { null }
    private fun validateArea(value: Double?): AppResult.Failure? =
        if (value != null && (!value.isFinite() || value <= 0.0)) AppResult.Failure(AppError.Validation("area", "not_positive")) else null
    private fun validateGeometry(value: String?): AppResult.Failure? {
        val geometry = value.normalized() ?: return null
        return if ("\"Polygon\"" !in geometry && "\"MultiPolygon\"" !in geometry) {
            AppResult.Failure(AppError.Validation("geometry", "polygon_required"))
        } else null
    }
    private fun pendingMetadata(now: Instant) = LocalMetadata(now, now, syncStatus = SyncStatus.PENDING)
    private fun LocalMetadata.next(now: Instant) = copy(updatedAt = now, version = version + 1, syncStatus = SyncStatus.PENDING)
    private fun ParcelRow.toDomain() = Parcel(
        id = parcel.id, workspaceId = parcel.workspaceId, farmId = currentFarmId,
        displayName = parcel.displayName, cadastralReference = parcel.cadastralReference,
        cadastralPolygon = parcel.cadastralPolygon, cadastralParcel = parcel.cadastralParcel,
        municipality = parcel.municipality, province = parcel.province,
        source = ParcelSource.valueOf(parcel.source), geometryGeoJson = parcel.geometryGeoJson,
        cadastralAreaM2 = parcel.cadastralAreaM2, managedAreaM2 = parcel.managedAreaM2,
        notes = parcel.notes, archivedAt = parcel.metadata.deletedAt, version = parcel.metadata.version,
    )
}
