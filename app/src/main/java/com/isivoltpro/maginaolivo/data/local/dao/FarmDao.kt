package com.isivoltpro.maginaolivo.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.isivoltpro.maginaolivo.data.local.entity.FarmEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface FarmDao {
    @Upsert
    suspend fun upsert(farm: FarmEntity)

    @Query("SELECT * FROM farms WHERE id = :id LIMIT 1")
    suspend fun findById(id: UUID): FarmEntity?

    @Query(
        """
        SELECT * FROM farms
        WHERE workspace_id = :workspaceId AND deleted_at IS NULL AND status = 'ACTIVE'
        ORDER BY name COLLATE NOCASE, id
        """,
    )
    fun observeActive(workspaceId: UUID): Flow<List<FarmEntity>>

    @Query("SELECT * FROM farms WHERE workspace_id = :workspaceId ORDER BY created_at, id")
    suspend fun listIncludingDeleted(workspaceId: UUID): List<FarmEntity>
}
