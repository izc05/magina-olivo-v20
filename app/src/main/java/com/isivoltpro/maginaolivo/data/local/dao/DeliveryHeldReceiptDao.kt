package com.isivoltpro.maginaolivo.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.isivoltpro.maginaolivo.data.local.entity.DeliveryHeldReceiptEntity
import java.util.UUID

@Dao
interface DeliveryHeldReceiptDao {
    /** ABORT: an existing receipt is never replaced, only compared. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(receipt: DeliveryHeldReceiptEntity)

    @Query("SELECT * FROM delivery_held_receipts WHERE operation_id = :operationId")
    suspend fun findByOperation(operationId: UUID): DeliveryHeldReceiptEntity?

    @Query("SELECT * FROM delivery_held_receipts WHERE delivery_id = :deliveryId ORDER BY received_at, operation_id")
    suspend fun listForDelivery(deliveryId: UUID): List<DeliveryHeldReceiptEntity>

    @Query("SELECT COUNT(*) FROM delivery_held_receipts WHERE delivery_id = :deliveryId AND state != 'RECONCILED'")
    suspend fun countUnreconciled(deliveryId: UUID): Int

    /** Records the first incompatible receipt beside the original; never overwrites the original. */
    @Query(
        """
        UPDATE delivery_held_receipts
        SET state = 'CONFLICT', conflict_remote_version = :remoteVersion, conflict_payload_hash = :payloadHash
        WHERE operation_id = :operationId AND state = 'HELD'
        """,
    )
    suspend fun markConflict(operationId: UUID, remoteVersion: Long, payloadHash: String): Int

    @Query(
        """
        UPDATE delivery_held_receipts SET state = 'RECONCILED', resolved_at = :nowEpochMillis
        WHERE operation_id = :operationId AND state = 'HELD'
        """,
    )
    suspend fun markReconciled(operationId: UUID, nowEpochMillis: Long): Int
}
