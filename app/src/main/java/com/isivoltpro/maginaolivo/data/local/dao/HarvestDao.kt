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
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow

@Dao
interface HarvestDao {
    @Upsert suspend fun upsert(harvest: HarvestEntity)

    @Query("SELECT * FROM harvests WHERE id = :id LIMIT 1")
    suspend fun findById(id: UUID): HarvestEntity?

    /**
     * CR-010 (note 3): the oldest live automatic day of this Farm and Campaign on [date]. A
     * Jornada recorded by hand is never returned: linking a Pesada to it would overwrite its kilos.
     */
    @Query(
        """
        SELECT * FROM harvests
        WHERE farm_id = :farmId AND campaign_id = :campaignId AND harvest_date = :date
          AND day_origin = 'AUTO_DAY' AND deleted_at IS NULL
        ORDER BY created_at, id LIMIT 1
        """,
    )
    suspend fun findAutoDay(farmId: UUID, campaignId: UUID, date: LocalDate): HarvestEntity?

    /**
     * #458: automatic days that no live Pesada supports but that still carry origin Parcels or
     * kilos — a presumption of earlier versions (every Parcel; kilos with no Pesada), never
     * something the farmer typed. Closed Campaigns included: the presumption was never history.
     */
    @Query(
        """
        SELECT h.* FROM harvests h
        WHERE h.day_origin = 'AUTO_DAY' AND h.deleted_at IS NULL
          AND (h.weight_grams != 0 OR EXISTS (SELECT 1 FROM harvest_parcels hp WHERE hp.harvest_id = h.id))
          AND NOT EXISTS (SELECT 1 FROM deliveries d WHERE d.harvest_id = h.id AND d.deleted_at IS NULL)
        """,
    )
    suspend fun listUnfoundedAutoDays(): List<HarvestEntity>

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
        WHERE workspace_id = :workspaceId AND deleted_at IS NULL
        ORDER BY harvest_date DESC, created_at DESC, id
        """,
    )
    fun observeAllForWorkspace(workspaceId: UUID): Flow<List<HarvestWithParcels>>

    @Transaction
    @Query(
        """
        SELECT * FROM harvests
        WHERE campaign_id = :campaignId AND deleted_at IS NULL
        ORDER BY harvest_date DESC, created_at DESC, id
        """,
    )
    fun observeForCampaign(campaignId: UUID): Flow<List<HarvestWithParcels>>

    @Query("SELECT * FROM harvests WHERE campaign_id = :campaignId AND deleted_at IS NULL")
    suspend fun listLiveForCampaign(campaignId: UUID): List<HarvestEntity>

    @Query("SELECT * FROM harvest_parcels WHERE harvest_id = :harvestId ORDER BY parcel_name_at_harvest COLLATE NOCASE, parcel_id")
    suspend fun listParcels(harvestId: UUID): List<HarvestParcelEntity>

    @Query("DELETE FROM harvest_parcels WHERE harvest_id = :harvestId")
    suspend fun deleteParcels(harvestId: UUID)

    @Query("DELETE FROM harvest_parcels WHERE id IN (:ids)")
    suspend fun deleteParcelsById(ids: List<UUID>)

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
        SELECT c.id AS campaignId, c.name AS campaignName, c.status AS campaignStatus,
               c.start_date AS campaignStart, f.id AS farmId, f.name AS farmName
        FROM campaigns c
        JOIN farms f ON f.id = c.farm_id
        WHERE c.workspace_id = :workspaceId
          AND c.deleted_at IS NULL AND c.status IN ('ACTIVE', 'HARVEST')
          AND f.deleted_at IS NULL AND f.status = 'ACTIVE'
        ORDER BY f.name COLLATE NOCASE, f.id
        """,
    )
    fun observeRunningCampaignsForWorkspace(workspaceId: UUID): Flow<List<RunningCampaignRow>>

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
