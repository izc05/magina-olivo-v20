package com.isivoltpro.maginaolivo.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.isivoltpro.maginaolivo.data.local.entity.HarvestEquipmentEntity
import java.util.UUID
import kotlinx.coroutines.flow.Flow

@Dao
interface EquipmentDao {
    @Upsert suspend fun upsert(rows: List<HarvestEquipmentEntity>)

    @Query("SELECT * FROM harvest_equipment WHERE harvest_id = :harvestId AND deleted_at IS NULL ORDER BY type, label COLLATE NOCASE, id")
    fun observeForHarvest(harvestId: UUID): Flow<List<HarvestEquipmentEntity>>

    @Query("SELECT * FROM harvest_equipment WHERE harvest_id = :harvestId AND deleted_at IS NULL")
    suspend fun listForHarvest(harvestId: UUID): List<HarvestEquipmentEntity>

    /** The live equipment of every live Jornada of one Campaign. */
    @Query(
        """
        SELECT harvest_equipment.* FROM harvest_equipment
        JOIN harvests ON harvests.id = harvest_equipment.harvest_id
        WHERE harvests.campaign_id = :campaignId AND harvests.deleted_at IS NULL AND harvest_equipment.deleted_at IS NULL
        """,
    )
    fun observeForCampaign(campaignId: UUID): Flow<List<HarvestEquipmentEntity>>
}
