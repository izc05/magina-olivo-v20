package com.isivoltpro.maginaolivo.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.isivoltpro.maginaolivo.data.local.entity.WorkspaceSelectionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkspaceSelectionDao {
    @Upsert suspend fun upsert(selection: WorkspaceSelectionEntity)

    @Query("SELECT * FROM workspace_selections WHERE issuer = :issuer AND account_id = :accountId LIMIT 1")
    suspend fun find(issuer: String, accountId: String): WorkspaceSelectionEntity?

    @Query("SELECT * FROM workspace_selections")
    fun observeAll(): Flow<List<WorkspaceSelectionEntity>>
}
