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

    @Query("""
        SELECT * FROM workspaces w WHERE w.deleted_at IS NULL
        AND (w.sync_status IN ('LOCAL_ONLY', 'PENDING', 'FAILED')
             OR EXISTS (SELECT 1 FROM sync_outbox o WHERE o.entity_type = 'WORKSPACE' AND o.entity_id = w.id))
        AND NOT EXISTS (SELECT 1 FROM deliveries d JOIN delivery_import_receipts r ON r.delivery_id = d.id WHERE d.workspace_id = w.id)
        AND NOT EXISTS (SELECT 1 FROM sync_outbox o WHERE o.entity_type = 'WORKSPACE' AND o.entity_id = w.id AND o.account_id IS NOT NULL)
        ORDER BY w.created_at, w.id
    """)
    suspend fun listUnboundLocal(): List<WorkspaceEntity>

    /**
     * Integration #796 + #808 (D1): the ids of exactly the workspaces of [listUnboundLocal] (same criteria, kept in sync),
     * without mapping their rows, so that G1b farm choices never depend on the stored owner value.
     */
    @Query("""
        SELECT w.id FROM workspaces w WHERE w.deleted_at IS NULL
        AND (w.sync_status IN ('LOCAL_ONLY', 'PENDING', 'FAILED')
             OR EXISTS (SELECT 1 FROM sync_outbox o WHERE o.entity_type = 'WORKSPACE' AND o.entity_id = w.id))
        AND NOT EXISTS (SELECT 1 FROM deliveries d JOIN delivery_import_receipts r ON r.delivery_id = d.id WHERE d.workspace_id = w.id)
        AND NOT EXISTS (SELECT 1 FROM sync_outbox o WHERE o.entity_type = 'WORKSPACE' AND o.entity_id = w.id AND o.account_id IS NOT NULL)
        ORDER BY w.created_at, w.id
    """)
    suspend fun listUnboundLocalIds(): List<UUID>
}
