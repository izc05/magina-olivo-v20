package com.isivoltpro.maginaolivo.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.isivoltpro.maginaolivo.data.local.entity.DeliveryDispatchPrepareEntity
import java.util.UUID

@Dao
interface DeliveryDispatchPrepareDao {
    /** ABORT: a PREPARE is written once. Re-preparing compares, it never replaces. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(prepare: DeliveryDispatchPrepareEntity)

    @Query("SELECT * FROM delivery_dispatch_prepare WHERE operation_id = :operationId")
    suspend fun findByOperation(operationId: UUID): DeliveryDispatchPrepareEntity?

    @Query(
        """
        SELECT * FROM delivery_dispatch_prepare
        WHERE delivery_id = :deliveryId ORDER BY prepared_at, operation_id
        """,
    )
    suspend fun listForDelivery(deliveryId: UUID): List<DeliveryDispatchPrepareEntity>

    /** In-flight or interrupted senders: prepared but never settled. */
    @Query(
        """
        SELECT * FROM delivery_dispatch_prepare
        WHERE settled_at IS NULL AND account_id = :accountId AND farm_id = :farmId
        ORDER BY prepared_at, operation_id
        """,
    )
    suspend fun listUnsettled(accountId: String, farmId: UUID): List<DeliveryDispatchPrepareEntity>

    @Query("SELECT COUNT(*) FROM delivery_dispatch_prepare WHERE settled_at IS NULL")
    suspend fun countUnsettled(): Int

    /**
     * Terminal annotation. Only an unsettled row can settle, so an outcome is recorded once
     * and a late duplicate callback can never rewrite history.
     */
    @Query(
        """
        UPDATE delivery_dispatch_prepare SET settled_at = :nowEpochMillis, settled_outcome = :outcome
        WHERE operation_id = :operationId AND settled_at IS NULL
        """,
    )
    suspend fun settle(operationId: UUID, outcome: String, nowEpochMillis: Long): Int
}
