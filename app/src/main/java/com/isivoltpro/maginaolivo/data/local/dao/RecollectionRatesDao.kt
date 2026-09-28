package com.isivoltpro.maginaolivo.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.isivoltpro.maginaolivo.data.local.entity.RecollectionRatesEntity
import java.util.UUID
import kotlinx.coroutines.flow.Flow

@Dao
interface RecollectionRatesDao {
    @Upsert suspend fun upsert(rates: RecollectionRatesEntity)

    @Query("SELECT * FROM recollection_rates WHERE farm_id = :farmId AND deleted_at IS NULL LIMIT 1")
    suspend fun findForFarm(farmId: UUID): RecollectionRatesEntity?

    @Query("SELECT * FROM recollection_rates WHERE farm_id = :farmId AND deleted_at IS NULL LIMIT 1")
    fun observeForFarm(farmId: UUID): Flow<RecollectionRatesEntity?>
}
