package com.isivoltpro.maginaolivo.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.isivoltpro.maginaolivo.data.local.entity.HarvestEntity
import com.isivoltpro.maginaolivo.data.local.entity.HarvestParcelEntity
import com.isivoltpro.maginaolivo.data.local.model.CampaignParcelRow
import com.isivoltpro.maginaolivo.data.local.model.HarvestWithParcels
import com.isivoltpro.maginaolivo.data.local.model.RunningCampaignRow
import java.util.UUID
import kotlinx.coroutines.flow.Flow

@Dao
interface HarvestDao {
    @Upsert suspend fun upsert(harvest: HarvestEntity)

    @Query("SELECT * FROM harvests WHERE id = :id LIMIT 1")
    suspend fun findById(id: UUID): HarvestEntity?

    @Transaction
    @Query("SELECT * FROM harvests WHERE id = :id AND deleted_at IS NULL LIMIT 1")
    fun observeWithParcels(id: UUID): Flow<HarvestWithParcels?>

    @Transaction
    @Query(
        """
        SELECT * FROM harvests
        WHERE deleted_at IS NULL
        ORDER BY harvest_date DESC, created_at DESC, id
        """,
    )
    fun observeAll(): Flow<List<HarvestWithParcels>>

    @Transaction
    @Query(
        """
        SELECT * FROM harvests
        WHERE campaign_id = :campaignId AND deleted_at IS NULL
        ORDER BY harvest_date DESC, created_at DESC, id
        """,
    )
    fun observeForCampaign(campaignId: UUID): Flow<List<HarvestWithParcels>>

    @Query("SELECT * FROM harvest_parcels WHERE harvest_id = :harvestId ORDER BY parcel_name_at_harvest COLLATE NOCASE, parcel_id")
    suspend fun listParcels(harvestId: UUID): List<HarvestParcelEntity>

    @Query("DELETE FROM harvest_parcels WHERE harvest_id = :harvestId")
    suspend fun deleteParcels(harvestId: UUID)

    @Upsert suspend fun upsertParcels(rows: List<HarvestParcelEntity>)

    @Query(
        """
        SELECT c.id AS campaignId, c.name AS campaignName, c.status AS campaignStatus,
               c.start_date AS campaignStart, f.id AS farmId, f.name AS farmName
        FROM campaigns c
        JOIN farms f ON f.id = c.farm_id
        WHERE c.deleted_at IS NULL AND c.status IN ('ACTIVE', 'HARVEST')
          AND f.deleted_at IS NULL AND f.status = 'ACTIVE'
        ORDER BY f.name COLLATE NOCASE, f.id
        """,
    )
    fun observeRunningCampaigns(): Flow<List<RunningCampaignRow>>

    @Query(
        """
        SELECT cp.id AS campaignParcelId, cp.parcel_id AS parcelId,
               COALESCE(p.display_name, cp.parcel_name_at_start) AS name
        FROM campaign_parcels cp
        LEFT JOIN parcels p ON p.id = cp.parcel_id
        WHERE cp.campaign_id = :campaignId AND cp.deleted_at IS NULL
        ORDER BY name COLLATE NOCASE, cp.parcel_id
        """,
    )
    suspend fun listCampaignParcels(campaignId: UUID): List<CampaignParcelRow>
}
