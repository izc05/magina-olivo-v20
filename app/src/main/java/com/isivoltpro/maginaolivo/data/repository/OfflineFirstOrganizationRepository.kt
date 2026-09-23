package com.isivoltpro.maginaolivo.data.repository

import androidx.room.withTransaction
import com.isivoltpro.maginaolivo.core.common.AppError
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.core.dispatchers.AppDispatchers
import com.isivoltpro.maginaolivo.core.id.IdGenerator
import com.isivoltpro.maginaolivo.core.time.AppClock
import com.isivoltpro.maginaolivo.data.local.MaginaOlivoDatabase
import com.isivoltpro.maginaolivo.data.local.entity.AgriculturalOrganizationEntity
import com.isivoltpro.maginaolivo.data.local.entity.LocalMetadata
import com.isivoltpro.maginaolivo.data.local.entity.OrganizationRoleEntity
import com.isivoltpro.maginaolivo.data.local.model.OutboxOperation
import com.isivoltpro.maginaolivo.data.local.model.SyncEntityType
import com.isivoltpro.maginaolivo.data.local.model.SyncStatus
import com.isivoltpro.maginaolivo.domain.organization.Organization
import com.isivoltpro.maginaolivo.domain.organization.OrganizationDraft
import com.isivoltpro.maginaolivo.domain.organization.OrganizationRepository
import com.isivoltpro.maginaolivo.domain.organization.OrganizationRole
import com.isivoltpro.maginaolivo.domain.workspace.WorkspaceRepository
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * Organizations and their roles form one aggregate: roles are replaced in the same
 * transaction as the organization and never synchronize on their own. A second
 * organization with the same name is refused, because the whole point is reuse.
 */
class OfflineFirstOrganizationRepository(
    private val database: MaginaOlivoDatabase,
    private val workspaceRepository: WorkspaceRepository,
    private val clock: AppClock,
    private val idGenerator: IdGenerator,
    private val dispatchers: AppDispatchers,
) : OrganizationRepository {
    override fun observeAll(): Flow<List<Organization>> =
        combine(database.organizationDao().observeActive(), database.organizationDao().observeRoles()) { rows, roles ->
            rows.toDomain(roles)
        }.flowOn(dispatchers.io)

    override fun observeWithAnyRole(roles: Set<OrganizationRole>): Flow<List<Organization>> =
        combine(
            database.organizationDao().observeWithAnyRole(roles.map { it.name }),
            database.organizationDao().observeRoles(),
        ) { rows, allRoles -> rows.toDomain(allRoles) }.flowOn(dispatchers.io)

    override suspend fun create(draft: OrganizationDraft): AppResult<UUID> {
        validate(draft)?.let { return it }
        val workspaceId = when (val workspace = workspaceRepository.ensureLocalWorkspace()) {
            is AppResult.Failure -> return workspace
            is AppResult.Success -> workspace.value
        }
        return withContext(dispatchers.io) {
            safely("create_organization") {
                val name = draft.name.trim()
                if (database.organizationDao().findActiveByName(workspaceId, name) != null) {
                    return@safely AppResult.Failure(AppError.Conflict("duplicate_organization"))
                }
                val id = idGenerator.newId()
                val now = clock.nowInstant()
                database.organizationDao().upsert(
                    draft.toEntity(id, workspaceId, LocalMetadata(now, now, syncStatus = SyncStatus.PENDING)),
                )
                replaceRoles(id, draft.roles)
                database.enqueueCollapsed(idGenerator, SyncEntityType.ORGANIZATION, id, OutboxOperation.CREATE, now)
                AppResult.Success(id)
            }
        }
    }

    override suspend fun update(id: UUID, draft: OrganizationDraft): AppResult<Unit> {
        validate(draft)?.let { return it }
        return withContext(dispatchers.io) {
            safely("update_organization") {
                val current = database.organizationDao().findById(id)
                    ?: return@safely AppResult.Failure(AppError.NotFound("organization"))
                if (current.metadata.deletedAt != null) return@safely AppResult.Failure(AppError.Conflict("archived_organization"))
                val duplicate = database.organizationDao().findActiveByName(current.workspaceId, draft.name.trim())
                if (duplicate != null && duplicate.id != id) {
                    return@safely AppResult.Failure(AppError.Conflict("duplicate_organization"))
                }
                val now = clock.nowInstant()
                database.organizationDao().upsert(
                    draft.toEntity(
                        id,
                        current.workspaceId,
                        current.metadata.copy(updatedAt = now, version = current.metadata.version + 1, syncStatus = SyncStatus.PENDING),
                    ),
                )
                replaceRoles(id, draft.roles)
                database.enqueueCollapsed(idGenerator, SyncEntityType.ORGANIZATION, id, OutboxOperation.UPDATE, now)
                AppResult.Success(Unit)
            }
        }
    }

    override suspend fun archive(id: UUID): AppResult<Unit> = withContext(dispatchers.io) {
        safely("archive_organization") {
            val current = database.organizationDao().findById(id)
                ?: return@safely AppResult.Failure(AppError.NotFound("organization"))
            if (current.metadata.deletedAt != null) return@safely AppResult.Success(Unit)
            val now = clock.nowInstant()
            // Expenses keep their supplier name as written; archiving never rewrites history.
            database.organizationDao().upsert(
                current.copy(
                    metadata = current.metadata.copy(
                        updatedAt = now,
                        deletedAt = now,
                        version = current.metadata.version + 1,
                        syncStatus = SyncStatus.PENDING,
                    ),
                ),
            )
            database.enqueueCollapsed(idGenerator, SyncEntityType.ORGANIZATION, id, OutboxOperation.DELETE, now)
            AppResult.Success(Unit)
        }
    }

    private suspend fun replaceRoles(id: UUID, roles: Set<OrganizationRole>) {
        database.organizationDao().deleteRoles(id)
        database.organizationDao().insertRoles(roles.map { OrganizationRoleEntity(id, it.name) })
    }

    private fun validate(draft: OrganizationDraft): AppResult.Failure? = when {
        draft.name.isBlank() -> AppResult.Failure(AppError.Validation("name", "blank"))
        draft.roles.isEmpty() -> AppResult.Failure(AppError.Validation("roles", "empty"))
        else -> null
    }

    private suspend fun <T> safely(operation: String, block: suspend () -> AppResult<T>): AppResult<T> =
        try {
            database.withTransaction { block() }
        } catch (error: Throwable) {
            AppResult.Failure(AppError.Storage(operation, error))
        }

    private fun OrganizationDraft.toEntity(id: UUID, workspaceId: UUID, metadata: LocalMetadata) =
        AgriculturalOrganizationEntity(
            id = id,
            workspaceId = workspaceId,
            name = name.trim(),
            taxId = taxId.normalized(),
            municipality = municipality.normalized(),
            province = province.normalized(),
            phone = phone.normalized(),
            notes = notes.normalized(),
            metadata = metadata,
        )

    private fun List<AgriculturalOrganizationEntity>.toDomain(roles: List<OrganizationRoleEntity>): List<Organization> {
        val byOrganization = roles.groupBy({ it.organizationId }) { role ->
            OrganizationRole.entries.firstOrNull { it.name == role.role } ?: OrganizationRole.OTHER
        }
        return map { row ->
            Organization(
                id = row.id,
                name = row.name,
                roles = byOrganization[row.id].orEmpty().toSet(),
                taxId = row.taxId,
                municipality = row.municipality,
                province = row.province,
                phone = row.phone,
                notes = row.notes,
            )
        }
    }

    private fun String?.normalized() = this?.trim()?.ifEmpty { null }
}
