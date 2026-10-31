package com.isivoltpro.maginaolivo.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.isivoltpro.maginaolivo.data.local.entity.DeliveryImportReceiptEntity
import java.util.UUID

@Dao
interface DeliveryImportReceiptDao {
    /** ABORT: an existing receipt is never overwritten, preserving original import evidence. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(receipt: DeliveryImportReceiptEntity)

    @Query("SELECT * FROM delivery_import_receipts WHERE delivery_id = :deliveryId")
    suspend fun findById(deliveryId: UUID): DeliveryImportReceiptEntity?
}
