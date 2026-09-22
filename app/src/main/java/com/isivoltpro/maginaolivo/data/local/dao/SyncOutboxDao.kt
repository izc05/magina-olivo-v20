package com.isivoltpro.maginaolivo.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.isivoltpro.maginaolivo.data.local.entity.SyncOutboxEntity
import com.isivoltpro.maginaolivo.data.local.model.SyncEntityType
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface SyncOutboxDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(operation: SyncOutboxEntity)

    @Query(
        """
        SELECT * FROM sync_outbox
        WHERE status IN ('PENDING', 'FAILED')
          AND (next_attempt_at IS NULL OR next_attempt_at <= :nowEpochMillis)
        ORDER BY created_at, id
        """,
    )
    fun observeReady(nowEpochMillis: Long): Flow<List<SyncOutboxEntity>>

    @Query(
        """
        SELECT * FROM sync_outbox
        WHERE entity_type = :entityType AND entity_id = :entityId
        ORDER BY created_at, id
        """,
    )
    suspend fun listForEntity(
        entityType: SyncEntityType,
        entityId: UUID,
    ): List<SyncOutboxEntity>

    @Query("DELETE FROM sync_outbox WHERE entity_type = :entityType AND entity_id = :entityId AND status IN ('PENDING', 'FAILED')")
    suspend fun deletePendingForEntity(entityType: SyncEntityType, entityId: UUID)
}
