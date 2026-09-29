package com.isivoltpro.maginaolivo.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.isivoltpro.maginaolivo.data.local.entity.ProfileSettingsEntity
import java.util.UUID
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileSettingsDao {
    @Upsert suspend fun upsert(settings: ProfileSettingsEntity)

    @Query("SELECT * FROM profile_settings WHERE workspace_id = :workspaceId LIMIT 1")
    suspend fun findForWorkspace(workspaceId: UUID): ProfileSettingsEntity?

    @Query("SELECT * FROM profile_settings WHERE workspace_id = :workspaceId AND deleted_at IS NULL LIMIT 1")
    fun observeForWorkspace(workspaceId: UUID): Flow<ProfileSettingsEntity?>
}
