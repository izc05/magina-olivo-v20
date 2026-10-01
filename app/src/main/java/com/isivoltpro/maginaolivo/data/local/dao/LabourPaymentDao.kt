package com.isivoltpro.maginaolivo.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.isivoltpro.maginaolivo.data.local.entity.LabourPaymentEntity
import java.util.UUID
import kotlinx.coroutines.flow.Flow

@Dao
interface LabourPaymentDao {
    @Upsert suspend fun upsert(payment: LabourPaymentEntity)
    @Query("SELECT * FROM labour_payments WHERE id = :id")
    suspend fun find(id: UUID): LabourPaymentEntity?
    @Query("SELECT * FROM labour_payments WHERE campaign_id = :campaignId AND deleted_at IS NULL ORDER BY payment_date, created_at, id")
    fun observeForCampaign(campaignId: UUID): Flow<List<LabourPaymentEntity>>
    @Query("SELECT * FROM labour_payments WHERE campaign_id = :campaignId AND deleted_at IS NULL ORDER BY payment_date, created_at, id")
    suspend fun listForCampaign(campaignId: UUID): List<LabourPaymentEntity>
}
