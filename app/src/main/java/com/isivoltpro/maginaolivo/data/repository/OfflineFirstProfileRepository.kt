package com.isivoltpro.maginaolivo.data.repository

import androidx.room.withTransaction
import com.isivoltpro.maginaolivo.core.common.AppError
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.core.dispatchers.AppDispatchers
import com.isivoltpro.maginaolivo.core.id.IdGenerator
import com.isivoltpro.maginaolivo.core.time.AppClock
import com.isivoltpro.maginaolivo.data.local.MaginaOlivoDatabase
import com.isivoltpro.maginaolivo.data.local.entity.LocalMetadata
import com.isivoltpro.maginaolivo.data.local.entity.ProfileSettingsEntity
import com.isivoltpro.maginaolivo.data.local.model.OutboxOperation
import com.isivoltpro.maginaolivo.data.local.model.SyncEntityType
import com.isivoltpro.maginaolivo.data.local.model.SyncStatus
import com.isivoltpro.maginaolivo.domain.agenda.ReminderPreferences
import com.isivoltpro.maginaolivo.domain.agenda.ReminderPreferencesSource
import com.isivoltpro.maginaolivo.domain.organization.OrganizationRepository
import com.isivoltpro.maginaolivo.domain.profile.PREFERRED_COOPERATIVE_ROLES
import com.isivoltpro.maginaolivo.domain.profile.ProfileDraft
import com.isivoltpro.maginaolivo.domain.profile.ProfileRepository
import com.isivoltpro.maginaolivo.domain.profile.ProfileSettings
import com.isivoltpro.maginaolivo.domain.workspace.WorkspaceRepository
import java.time.LocalTime
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * Phase 21A: «Mi perfil» on the phone first. The preferred cooperative is read through the live
 * Organizations, so a rename shows at once and an archived one — or one that no longer plays a
 * cooperative/mill role — is no longer offered as the preferred cooperative. Nothing is copied.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OfflineFirstProfileRepository(
    private val database: MaginaOlivoDatabase,
    private val workspaceRepository: WorkspaceRepository,
    private val organizations: OrganizationRepository,
    private val clock: AppClock,
    private val idGenerator: IdGenerator,
    private val dispatchers: AppDispatchers,
) : ProfileRepository {
    override fun observe(): Flow<ProfileSettings> =
        flow { emit(workspaceRepository.ensureLocalWorkspace()) }.flatMapLatest { workspace ->
            when (workspace) {
                is AppResult.Failure -> flowOf(ProfileSettings())
                is AppResult.Success -> combine(
                    database.profileSettingsDao().observeForWorkspace(workspace.value),
                    organizations.observeWithAnyRole(PREFERRED_COOPERATIVE_ROLES),
                ) { row, cooperatives ->
                    ProfileSettings(
                        municipality = row?.municipality,
                        province = row?.province,
                        preferredCooperative = row?.preferredOrganizationId?.let { id -> cooperatives.firstOrNull { it.id == id } },
                        reminders = row?.reminderPreferences() ?: ReminderPreferences(),
                    )
                }
            }
        }.flowOn(dispatchers.io)

    override suspend fun save(draft: ProfileDraft): AppResult<Unit> {
        val municipality = draft.municipality?.trim()?.takeIf(String::isNotEmpty)
        val province = draft.province?.trim()?.takeIf(String::isNotEmpty)
        if (province != null && municipality == null) return AppResult.Failure(AppError.Validation("municipality", "blank"))
        val workspaceId = when (val workspace = workspaceRepository.ensureLocalWorkspace()) {
            is AppResult.Failure -> return workspace
            is AppResult.Success -> workspace.value
        }
        return withContext(dispatchers.io) {
            try {
                database.withTransaction {
                    draft.preferredOrganizationId?.let { id ->
                        val organization = database.organizationDao().findById(id)
                        if (organization == null || organization.metadata.deletedAt != null) {
                            return@withTransaction AppResult.Failure(AppError.NotFound("organization"))
                        }
                        val roles = database.organizationDao().listRoles(id)
                        if (PREFERRED_COOPERATIVE_ROLES.none { it.name in roles }) {
                            return@withTransaction AppResult.Failure(AppError.Validation("organization", "not_cooperative"))
                        }
                    }
                    val current = database.profileSettingsDao().findForWorkspace(workspaceId)
                    write(workspaceId, current) {
                        it.copy(municipality = municipality, province = province, preferredOrganizationId = draft.preferredOrganizationId)
                    }
                    AppResult.Success(Unit)
                }
            } catch (error: Throwable) {
                AppResult.Failure(AppError.Storage("save_profile", error))
            }
        }
    }

    override suspend fun saveReminders(preferences: ReminderPreferences): AppResult<Unit> {
        if (preferences.previousDayTime.second != 0 || preferences.previousDayTime.nano != 0) {
            return AppResult.Failure(AppError.Validation("previousDayTime", "whole_minutes"))
        }
        val workspaceId = when (val workspace = workspaceRepository.ensureLocalWorkspace()) {
            is AppResult.Failure -> return workspace
            is AppResult.Success -> workspace.value
        }
        return withContext(dispatchers.io) {
            try {
                database.withTransaction {
                    val current = database.profileSettingsDao().findForWorkspace(workspaceId)
                    write(workspaceId, current) {
                        it.copy(
                            remindersEnabled = preferences.enabled,
                            previousDayReminderMinute = preferences.previousDayTime.toSecondOfDay() / 60,
                        )
                    }
                    AppResult.Success(Unit)
                }
            } catch (error: Throwable) {
                AppResult.Failure(AppError.Storage("save_reminder_preferences", error))
            }
        }
    }

    /** One row per workspace, updated in place and queued once for sync. */
    private suspend fun write(
        workspaceId: UUID,
        current: ProfileSettingsEntity?,
        change: (ProfileSettingsEntity) -> ProfileSettingsEntity,
    ) {
        val now = clock.nowInstant()
        val base = current?.copy(
            metadata = current.metadata.copy(
                updatedAt = now,
                deletedAt = null,
                version = current.metadata.version + 1,
                syncStatus = SyncStatus.PENDING,
            ),
        ) ?: ProfileSettingsEntity(
            id = idGenerator.newId(),
            workspaceId = workspaceId,
            metadata = LocalMetadata(now, now, syncStatus = SyncStatus.PENDING),
        )
        val row = change(base)
        database.profileSettingsDao().upsert(row)
        database.enqueueCollapsed(
            idGenerator, SyncEntityType.PROFILE_SETTINGS, row.id,
            if (current == null) OutboxOperation.CREATE else OutboxOperation.UPDATE, now,
        )
    }
}

/** Phase 21B: the reminder engine reads Perfil → Avisos from the phone's profile row. */
class RoomReminderPreferences(private val database: MaginaOlivoDatabase) : ReminderPreferencesSource {
    override suspend fun current(): ReminderPreferences =
        database.profileSettingsDao().findFirst()?.reminderPreferences() ?: ReminderPreferences()
}

internal fun ProfileSettingsEntity.reminderPreferences() = ReminderPreferences(
    enabled = remindersEnabled,
    previousDayTime = LocalTime.ofSecondOfDay((previousDayReminderMinute.coerceIn(0, 24 * 60 - 1) * 60).toLong()),
)
