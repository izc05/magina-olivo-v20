package com.isivoltpro.maginaolivo.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.isivoltpro.maginaolivo.data.local.entity.FarmEntity
import com.isivoltpro.maginaolivo.data.local.model.FarmSummaryRow
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
        SELECT f.*,
            (SELECT COUNT(*)
             FROM farm_parcel_memberships m
             JOIN parcels p ON p.id = m.parcel_id
             WHERE m.farm_id = f.id AND m.valid_until IS NULL AND m.deleted_at IS NULL
               AND p.deleted_at IS NULL AND p.status = 'ACTIVE') AS parcel_count,
            (SELECT SUM(COALESCE(p.managed_area_m2, p.cadastral_area_m2))
             FROM farm_parcel_memberships m
             JOIN parcels p ON p.id = m.parcel_id
             WHERE m.farm_id = f.id AND m.valid_until IS NULL AND m.deleted_at IS NULL
               AND p.deleted_at IS NULL AND p.status = 'ACTIVE') AS total_area_m2,
            (SELECT c.name FROM campaigns c
             WHERE c.farm_id = f.id AND c.status = 'ACTIVE' AND c.deleted_at IS NULL
             ORDER BY c.start_date DESC, c.id LIMIT 1) AS active_campaign_name
        FROM farms f
        WHERE f.workspace_id = :workspaceId AND f.deleted_at IS NULL AND f.status = 'ACTIVE'
        ORDER BY f.name COLLATE NOCASE, f.id
        """,
    )
    fun observeActive(workspaceId: UUID): Flow<List<FarmSummaryRow>>

    @Query(
        """
        SELECT f.*,
            (SELECT COUNT(*) FROM farm_parcel_memberships m JOIN parcels p ON p.id = m.parcel_id
             WHERE m.farm_id = f.id AND m.valid_until IS NULL AND m.deleted_at IS NULL
               AND p.deleted_at IS NULL AND p.status = 'ACTIVE') AS parcel_count,
            (SELECT SUM(COALESCE(p.managed_area_m2, p.cadastral_area_m2))
             FROM farm_parcel_memberships m JOIN parcels p ON p.id = m.parcel_id
             WHERE m.farm_id = f.id AND m.valid_until IS NULL AND m.deleted_at IS NULL
               AND p.deleted_at IS NULL AND p.status = 'ACTIVE') AS total_area_m2,
            (SELECT c.name FROM campaigns c
             WHERE c.farm_id = f.id AND c.status = 'ACTIVE' AND c.deleted_at IS NULL
             ORDER BY c.start_date DESC, c.id LIMIT 1) AS active_campaign_name
        FROM farms f
        WHERE f.workspace_id = :workspaceId AND f.status = 'ARCHIVED'
        ORDER BY f.name COLLATE NOCASE, f.id
        """,
    )
    fun observeArchived(workspaceId: UUID): Flow<List<FarmSummaryRow>>

    @Query(
        """
        SELECT f.*,
            (SELECT COUNT(*) FROM farm_parcel_memberships m JOIN parcels p ON p.id = m.parcel_id
             WHERE m.farm_id = f.id AND m.valid_until IS NULL AND m.deleted_at IS NULL
               AND p.deleted_at IS NULL AND p.status = 'ACTIVE') AS parcel_count,
            (SELECT SUM(COALESCE(p.managed_area_m2, p.cadastral_area_m2))
             FROM farm_parcel_memberships m JOIN parcels p ON p.id = m.parcel_id
             WHERE m.farm_id = f.id AND m.valid_until IS NULL AND m.deleted_at IS NULL
               AND p.deleted_at IS NULL AND p.status = 'ACTIVE') AS total_area_m2,
            (SELECT c.name FROM campaigns c
             WHERE c.farm_id = f.id AND c.status = 'ACTIVE' AND c.deleted_at IS NULL
             ORDER BY c.start_date DESC, c.id LIMIT 1) AS active_campaign_name
        FROM farms f WHERE f.id = :id LIMIT 1
        """,
    )
    fun observeSummaryById(id: UUID): Flow<FarmSummaryRow?>

    @Query("SELECT * FROM farms WHERE workspace_id = :workspaceId ORDER BY created_at, id")
    suspend fun listIncludingDeleted(workspaceId: UUID): List<FarmEntity>
}
