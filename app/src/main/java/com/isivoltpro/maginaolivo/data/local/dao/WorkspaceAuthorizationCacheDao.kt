package com.isivoltpro.maginaolivo.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.isivoltpro.maginaolivo.data.local.entity.WorkspaceAuthorizationCacheEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkspaceAuthorizationCacheDao {
    @Upsert suspend fun upsert(value: WorkspaceAuthorizationCacheEntity)

    @Query("SELECT * FROM workspace_authorization_cache WHERE issuer = :issuer AND account_id = :accountId LIMIT 1")
    suspend fun find(issuer: String, accountId: String): WorkspaceAuthorizationCacheEntity?

    @Query("SELECT * FROM workspace_authorization_cache")
    fun observeAll(): Flow<List<WorkspaceAuthorizationCacheEntity>>
}
