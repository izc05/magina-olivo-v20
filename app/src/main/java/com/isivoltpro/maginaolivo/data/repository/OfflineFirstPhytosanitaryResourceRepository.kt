package com.isivoltpro.maginaolivo.data.repository

import androidx.room.withTransaction
import com.isivoltpro.maginaolivo.core.common.AppError
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.core.dispatchers.AppDispatchers
import com.isivoltpro.maginaolivo.core.id.IdGenerator
import com.isivoltpro.maginaolivo.core.time.AppClock
import com.isivoltpro.maginaolivo.data.local.MaginaOlivoDatabase
import com.isivoltpro.maginaolivo.data.local.entity.AgronomicPersonEntity
import com.isivoltpro.maginaolivo.data.local.entity.LocalMetadata
import com.isivoltpro.maginaolivo.data.local.entity.PhytosanitaryEquipmentProfileEntity
import com.isivoltpro.maginaolivo.data.local.model.OutboxOperation
import com.isivoltpro.maginaolivo.data.local.model.SyncEntityType
import com.isivoltpro.maginaolivo.data.local.model.SyncStatus
import com.isivoltpro.maginaolivo.domain.phytosanitary.AgronomicPerson
import com.isivoltpro.maginaolivo.domain.phytosanitary.AgronomicPersonDraft
import com.isivoltpro.maginaolivo.domain.phytosanitary.PhytosanitaryEquipmentProfile
import com.isivoltpro.maginaolivo.domain.phytosanitary.PhytosanitaryEquipmentProfileDraft
import com.isivoltpro.maginaolivo.domain.phytosanitary.PhytosanitaryResourceRepository
import com.isivoltpro.maginaolivo.domain.phytosanitary.RegulatoryResourceSource
import com.isivoltpro.maginaolivo.domain.workspace.WorkspaceRepository
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Offline-first reusable resources for CUE phytosanitary records.
 *
 * Worker is intentionally not involved: harvest labour aliases and legal applicator identities
 * have different semantics and lifecycles.
 */
class OfflineFirstPhytosanitaryResourceRepository(
    private val database: MaginaOlivoDatabase,
    private val workspaceRepository: WorkspaceRepository,
    private val clock: AppClock,
    private val idGenerator: IdGenerator,
    private val dispatchers: AppDispatchers,
) : PhytosanitaryResourceRepository {

    override fun observeActivePeople(): Flow<List<AgronomicPerson>> =
        scoped { workspaceId ->
            database.phytosanitaryResourceDao().observeActivePeople(workspaceId)
                .map { rows -> rows.map { it.toDomain() } }
        }

    override fun observeEquipmentProfile(machineId: UUID): Flow<PhytosanitaryEquipmentProfile?> =
        scoped { workspaceId ->
            database.phytosanitaryResourceDao().observeEquipmentProfile(workspaceId, machineId)
                .map { it?.toDomain() }
        }

    override suspend fun createPerson(draft: AgronomicPersonDraft): AppResult<UUID> {
        validatePerson(draft)?.let { return it }
        val workspaceId = activeWorkspace() ?: return AppResult.Failure(AppError.NotFound("workspace"))
        return safely("create_agronomic_person") {
            val name = draft.displayName.trim()
            if (database.phytosanitaryResourceDao().findActivePersonByName(workspaceId, name) != null) {
                return@safely AppResult.Failure(AppError.Conflict("duplicate_agronomic_person"))
            }
            val now = clock.nowInstant()
            val id = idGenerator.newId()
            database.phytosanitaryResourceDao().upsertPerson(
                draft.toEntity(
                    id = id,
                    workspaceId = workspaceId,
                    status = ACTIVE,
                    metadata = LocalMetadata(now, now, syncStatus = SyncStatus.PENDING),
                ),
            )
            database.enqueueCollapsed(
                idGenerator,
                SyncEntityType.AGRONOMIC_PERSON,
                id,
                OutboxOperation.CREATE,
                now,
            )
            AppResult.Success(id)
        }
    }

    override suspend fun updatePerson(id: UUID, draft: AgronomicPersonDraft): AppResult<Unit> {
        validatePerson(draft)?.let { return it }
        val workspaceId = activeWorkspace() ?: return AppResult.Failure(AppError.NotFound("workspace"))
        return safely("update_agronomic_person") {
            val current = database.phytosanitaryResourceDao().findPersonById(id)
                ?: return@safely AppResult.Failure(AppError.NotFound("agronomic_person"))
            if (current.workspaceId != workspaceId) {
                return@safely AppResult.Failure(AppError.Validation("person", "context_mismatch"))
            }
            if (current.status != ACTIVE || current.metadata.deletedAt != null) {
                return@safely AppResult.Failure(AppError.Conflict("archived_agronomic_person"))
            }
            val name = draft.displayName.trim()
            val duplicate = database.phytosanitaryResourceDao().findActivePersonByName(workspaceId, name)
            if (duplicate != null && duplicate.id != id) {
                return@safely AppResult.Failure(AppError.Conflict("duplicate_agronomic_person"))
            }
            val now = clock.nowInstant()
            database.phytosanitaryResourceDao().upsertPerson(
                draft.toEntity(
                    id = id,
                    workspaceId = workspaceId,
                    status = current.status,
                    metadata = current.metadata.next(now),
                ),
            )
            database.enqueueCollapsed(
                idGenerator,
                SyncEntityType.AGRONOMIC_PERSON,
                id,
                OutboxOperation.UPDATE,
                now,
            )
            AppResult.Success(Unit)
        }
    }

    override suspend fun archivePerson(id: UUID): AppResult<Unit> {
        val workspaceId = activeWorkspace() ?: return AppResult.Failure(AppError.NotFound("workspace"))
        return safely("archive_agronomic_person") {
            val current = database.phytosanitaryResourceDao().findPersonById(id)
                ?: return@safely AppResult.Failure(AppError.NotFound("agronomic_person"))
            if (current.workspaceId != workspaceId) {
                return@safely AppResult.Failure(AppError.Validation("person", "context_mismatch"))
            }
            if (current.status == ARCHIVED) return@safely AppResult.Success(Unit)
            val now = clock.nowInstant()
            database.phytosanitaryResourceDao().upsertPerson(
                current.copy(status = ARCHIVED, metadata = current.metadata.next(now)),
            )
            database.enqueueCollapsed(
                idGenerator,
                SyncEntityType.AGRONOMIC_PERSON,
                id,
                OutboxOperation.UPDATE,
                now,
            )
            AppResult.Success(Unit)
        }
    }

    override suspend fun saveEquipmentProfile(
        machineId: UUID,
        draft: PhytosanitaryEquipmentProfileDraft,
    ): AppResult<Unit> {
        val workspaceId = activeWorkspace() ?: return AppResult.Failure(AppError.NotFound("workspace"))
        return safely("save_phytosanitary_equipment_profile") {
            val machine = database.machineDao().findById(machineId)
                ?: return@safely AppResult.Failure(AppError.NotFound("machine"))
            if (machine.workspaceId != workspaceId || machine.metadata.deletedAt != null) {
                return@safely AppResult.Failure(AppError.Validation("machine", "context_mismatch"))
            }
            val current = database.phytosanitaryResourceDao().findEquipmentProfile(machineId)
            val now = clock.nowInstant()
            val metadata = current?.metadata?.next(now)
                ?: LocalMetadata(now, now, syncStatus = SyncStatus.PENDING)
            database.phytosanitaryResourceDao().upsertEquipmentProfile(
                PhytosanitaryEquipmentProfileEntity(
                    machineId = machineId,
                    workspaceId = workspaceId,
                    romaRegistration = draft.romaRegistration.normalized(),
                    censusReference = draft.censusReference.normalized(),
                    acquisitionDate = draft.acquisitionDate,
                    lastInspectionDate = draft.lastInspectionDate,
                    regulatoryTypeCode = draft.regulatoryTypeCode.normalized(),
                    source = draft.source.name,
                    externalId = draft.externalId.normalized(),
                    sourceVersion = draft.sourceVersion.normalized(),
                    fetchedAt = draft.fetchedAt,
                    metadata = metadata,
                ),
            )
            database.enqueueCollapsed(
                idGenerator,
                SyncEntityType.PHYTO_EQUIPMENT_PROFILE,
                machineId,
                if (current == null) OutboxOperation.CREATE else OutboxOperation.UPDATE,
                now,
            )
            AppResult.Success(Unit)
        }
    }

    private suspend fun activeWorkspace(): UUID? = when (val workspace = workspaceRepository.ensureLocalWorkspace()) {
        is AppResult.Success -> workspace.value
        is AppResult.Failure -> null
    }

    private fun validatePerson(draft: AgronomicPersonDraft): AppResult.Failure? = when {
        draft.displayName.isBlank() ->
            AppResult.Failure(AppError.Validation("displayName", "blank"))
        draft.validFrom != null && draft.validUntil != null && draft.validUntil.isBefore(draft.validFrom) ->
            AppResult.Failure(AppError.Validation("validUntil", "before_valid_from"))
        else -> null
    }

    private suspend fun <T> safely(operation: String, block: suspend () -> AppResult<T>): AppResult<T> =
        withContext(dispatchers.io) {
            try {
                database.withTransaction { block() }
            } catch (error: Throwable) {
                AppResult.Failure(AppError.Storage(operation, error))
            }
        }

    private fun <T> scoped(source: (UUID) -> Flow<T>): Flow<T> =
        flow {
            when (val workspace = workspaceRepository.ensureLocalWorkspace()) {
                is AppResult.Success -> emitAll(source(workspace.value))
                is AppResult.Failure -> return@flow
            }
        }.flowOn(dispatchers.io)

    private fun AgronomicPersonDraft.toEntity(
        id: UUID,
        workspaceId: UUID,
        status: String,
        metadata: LocalMetadata,
    ) = AgronomicPersonEntity(
        id = id,
        workspaceId = workspaceId,
        displayName = displayName.trim(),
        givenName = givenName.normalized(),
        familyName = familyName.normalized(),
        taxId = taxId.normalized(),
        ropoOrCardNumber = ropoOrCardNumber.normalized(),
        cardTypeCode = cardTypeCode.normalized(),
        isAdvisor = isAdvisor,
        validFrom = validFrom,
        validUntil = validUntil,
        source = source.name,
        externalId = externalId.normalized(),
        sourceVersion = sourceVersion.normalized(),
        fetchedAt = fetchedAt,
        status = status,
        metadata = metadata,
    )

    private fun AgronomicPersonEntity.toDomain() = AgronomicPerson(
        id = id,
        displayName = displayName,
        givenName = givenName,
        familyName = familyName,
        taxId = taxId,
        ropoOrCardNumber = ropoOrCardNumber,
        cardTypeCode = cardTypeCode,
        isAdvisor = isAdvisor,
        validFrom = validFrom,
        validUntil = validUntil,
        source = RegulatoryResourceSource.entries.firstOrNull { it.name == source } ?: RegulatoryResourceSource.MANUAL,
        externalId = externalId,
        sourceVersion = sourceVersion,
        fetchedAt = fetchedAt,
        archived = status != ACTIVE,
        version = metadata.version,
    )

    private fun PhytosanitaryEquipmentProfileEntity.toDomain() = PhytosanitaryEquipmentProfile(
        machineId = machineId,
        romaRegistration = romaRegistration,
        censusReference = censusReference,
        acquisitionDate = acquisitionDate,
        lastInspectionDate = lastInspectionDate,
        regulatoryTypeCode = regulatoryTypeCode,
        source = RegulatoryResourceSource.entries.firstOrNull { it.name == source } ?: RegulatoryResourceSource.MANUAL,
        externalId = externalId,
        sourceVersion = sourceVersion,
        fetchedAt = fetchedAt,
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
