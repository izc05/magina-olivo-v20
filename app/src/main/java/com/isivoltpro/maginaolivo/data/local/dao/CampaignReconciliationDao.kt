package com.isivoltpro.maginaolivo.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.isivoltpro.maginaolivo.data.local.entity.CampaignReconciliationEntity
import java.util.UUID

@Dao
interface CampaignReconciliationDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(row: CampaignReconciliationEntity)

    @Query("SELECT * FROM campaign_reconciliations WHERE original_operation_id = :operationId")
    suspend fun find(operationId: UUID): CampaignReconciliationEntity?

    @Query("UPDATE campaign_reconciliations SET resolved_operation_id = :newOperationId WHERE original_operation_id = :operationId AND resolved_operation_id IS NULL AND account_id = :accountId")
    suspend fun markResolved(operationId: UUID, accountId: String, newOperationId: UUID): Int
}
