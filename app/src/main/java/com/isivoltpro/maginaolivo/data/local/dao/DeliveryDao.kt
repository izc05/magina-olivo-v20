package com.isivoltpro.maginaolivo.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.isivoltpro.maginaolivo.data.local.entity.DeliveryEntity
import com.isivoltpro.maginaolivo.data.local.entity.DeliveryParcelEntity
import com.isivoltpro.maginaolivo.data.local.entity.DeliveryYieldAnalysisEntity
import com.isivoltpro.maginaolivo.data.local.model.DeliveryWithParcels
import java.util.UUID
import kotlinx.coroutines.flow.Flow

@Dao
interface DeliveryDao {
    @Upsert suspend fun upsert(delivery: DeliveryEntity)

    @Query("SELECT * FROM deliveries WHERE id = :id LIMIT 1")
    suspend fun findById(id: UUID): DeliveryEntity?

    @Transaction
    @Query("SELECT * FROM deliveries WHERE id = :id AND deleted_at IS NULL LIMIT 1")
    fun observeWithParcels(id: UUID): Flow<DeliveryWithParcels?>

    @Transaction
    @Query("SELECT * FROM deliveries WHERE deleted_at IS NULL ORDER BY delivery_date DESC, created_at DESC, id")
    fun observeAll(): Flow<List<DeliveryWithParcels>>

    @Transaction
    @Query(
        """
        SELECT * FROM deliveries
        WHERE campaign_id = :campaignId AND deleted_at IS NULL
        ORDER BY delivery_date DESC, created_at DESC, id
        """,
    )
    fun observeForCampaign(campaignId: UUID): Flow<List<DeliveryWithParcels>>

    @Query("SELECT * FROM delivery_parcels WHERE delivery_id = :deliveryId ORDER BY parcel_name_at_delivery COLLATE NOCASE, parcel_id")
    suspend fun listParcels(deliveryId: UUID): List<DeliveryParcelEntity>

    @Query("DELETE FROM delivery_parcels WHERE delivery_id = :deliveryId")
    suspend fun deleteParcels(deliveryId: UUID)

    @Upsert suspend fun upsertParcels(rows: List<DeliveryParcelEntity>)

    @Upsert suspend fun upsertAnalysis(analysis: DeliveryYieldAnalysisEntity)

    @Query("SELECT * FROM delivery_yield_analyses WHERE delivery_id = :deliveryId AND deleted_at IS NULL LIMIT 1")
    suspend fun findLiveAnalysis(deliveryId: UUID): DeliveryYieldAnalysisEntity?

    @Query("SELECT * FROM deliveries WHERE harvest_id = :harvestId AND deleted_at IS NULL")
    suspend fun listLiveForHarvest(harvestId: UUID): List<DeliveryEntity>
}
