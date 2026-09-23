package com.isivoltpro.maginaolivo.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.isivoltpro.maginaolivo.data.local.entity.CampaignEntity
import com.isivoltpro.maginaolivo.data.local.entity.CampaignParcelSnapshotEntity
import com.isivoltpro.maginaolivo.data.local.model.CampaignWithSnapshots
import java.util.UUID
import kotlinx.coroutines.flow.Flow

@Dao
interface CampaignDao {
    @Upsert suspend fun upsert(campaign: CampaignEntity)

    @Upsert suspend fun upsertSnapshots(snapshots: List<CampaignParcelSnapshotEntity>)

    @Query("SELECT * FROM campaigns WHERE id = :id LIMIT 1")
    suspend fun findById(id: UUID): CampaignEntity?

    @Query("SELECT * FROM campaign_parcels WHERE campaign_id = :campaignId ORDER BY parcel_name_at_start COLLATE NOCASE, parcel_id")
    suspend fun listSnapshots(campaignId: UUID): List<CampaignParcelSnapshotEntity>

    @Query("DELETE FROM campaign_parcels WHERE campaign_id = :campaignId")
    suspend fun deleteSnapshots(campaignId: UUID)

    @Query("SELECT COUNT(*) FROM campaigns WHERE farm_id = :farmId AND status IN ('ACTIVE', 'HARVEST') AND deleted_at IS NULL AND id != :excludedId")
    suspend fun countOtherCurrent(farmId: UUID, excludedId: UUID): Int

    @Query("SELECT * FROM campaigns WHERE farm_id = :farmId AND deleted_at IS NULL ORDER BY start_date DESC, id")
    fun observeForFarm(farmId: UUID): Flow<List<CampaignEntity>>

    @Transaction
    @Query("SELECT * FROM campaigns WHERE id = :id AND deleted_at IS NULL LIMIT 1")
    fun observeWithSnapshots(id: UUID): Flow<CampaignWithSnapshots?>

    @Query(
        """
        SELECT * FROM campaigns
        WHERE farm_id = :farmId AND deleted_at IS NULL AND status IN ('ACTIVE', 'HARVEST')
        LIMIT 1
        """,
    )
    suspend fun findCurrent(farmId: UUID): CampaignEntity?
}
