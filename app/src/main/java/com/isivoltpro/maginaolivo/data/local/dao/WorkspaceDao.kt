package com.isivoltpro.maginaolivo.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.isivoltpro.maginaolivo.data.local.entity.WorkspaceEntity
import java.util.UUID

@Dao
interface WorkspaceDao {
    @Upsert
    suspend fun upsert(workspace: WorkspaceEntity)

    @Query("SELECT * FROM workspaces WHERE id = :id LIMIT 1")
    suspend fun findById(id: UUID): WorkspaceEntity?

    @Query("SELECT * FROM workspaces WHERE deleted_at IS NULL ORDER BY created_at, id LIMIT 1")
    suspend fun findFirstActive(): WorkspaceEntity?
}
