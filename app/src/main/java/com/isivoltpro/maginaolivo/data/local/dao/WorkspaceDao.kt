package com.isivoltpro.maginaolivo.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import androidx.room.ColumnInfo
import com.isivoltpro.maginaolivo.data.local.entity.WorkspaceEntity
import java.util.UUID

data class WorkspaceGrantRow(
    val issuer: String,
    @ColumnInfo(name = "account_id") val accountId: String,
    @ColumnInfo(name = "workspace_id") val workspaceId: UUID,
)

@Dao
interface WorkspaceDao {
    @Upsert
    suspend fun upsert(workspace: WorkspaceEntity)

    @Query("SELECT * FROM workspaces WHERE id = :id LIMIT 1")
    suspend fun findById(id: UUID): WorkspaceEntity?

    @Query("SELECT * FROM workspaces WHERE deleted_at IS NULL ORDER BY created_at, id LIMIT 1")
    suspend fun findFirstActive(): WorkspaceEntity?

    @Query("SELECT DISTINCT r.issuer, r.account_id, d.workspace_id FROM delivery_import_receipts r JOIN deliveries d ON d.id = r.delivery_id")
    suspend fun listVerifiedGrants(): List<WorkspaceGrantRow>

    @Query("""
        SELECT * FROM workspaces w WHERE w.deleted_at IS NULL
        AND (w.sync_status IN ('LOCAL_ONLY', 'PENDING', 'FAILED')
             OR EXISTS (SELECT 1 FROM sync_outbox o WHERE o.entity_type = 'WORKSPACE' AND o.entity_id = w.id))
        AND NOT EXISTS (SELECT 1 FROM deliveries d JOIN delivery_import_receipts r ON r.delivery_id = d.id WHERE d.workspace_id = w.id)
        ORDER BY w.created_at, w.id
    """)
    suspend fun listUnboundLocal(): List<WorkspaceEntity>
}
