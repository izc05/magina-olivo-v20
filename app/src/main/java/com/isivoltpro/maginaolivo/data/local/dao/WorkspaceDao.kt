package com.isivoltpro.maginaolivo.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.isivoltpro.maginaolivo.data.local.entity.WorkspaceEntity
import java.util.UUID
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkspaceDao {
    @Upsert
    suspend fun upsert(workspace: WorkspaceEntity)

    @Query("SELECT * FROM workspaces WHERE id = :id LIMIT 1")
    suspend fun findById(id: UUID): WorkspaceEntity?

    @Query("SELECT * FROM workspaces WHERE deleted_at IS NULL ORDER BY created_at, id LIMIT 1")
    suspend fun findFirstActive(): WorkspaceEntity?

    @Query("SELECT * FROM workspaces WHERE deleted_at IS NULL ORDER BY created_at, id LIMIT 1")
    fun observeFirstActive(): Flow<WorkspaceEntity?>

    @Query("SELECT * FROM workspaces WHERE deleted_at IS NULL AND owner_user_id = :ownerUserId ORDER BY created_at, id LIMIT 1")
    suspend fun findActiveByOwner(ownerUserId: UUID): WorkspaceEntity?
}
