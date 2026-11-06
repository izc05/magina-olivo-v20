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
     * Records the outcome. A row is settleable while its outcome is still unresolved: either
     * never settled, or settled as OUTCOME_UNKNOWN, which states only that this device does
     * not know what the server did. A terminal outcome (CONFIRMED, HELD, CONFLICT) is final,
     * so a late duplicate callback can never rewrite history.
     */
    @Query(
        """
        UPDATE delivery_dispatch_prepare SET settled_at = :nowEpochMillis, settled_outcome = :outcome
        WHERE operation_id = :operationId
          AND (settled_at IS NULL OR settled_outcome = 'OUTCOME_UNKNOWN')
        """,
    )
    suspend fun settle(operationId: UUID, outcome: String, nowEpochMillis: Long): Int

    /**
     * The claim marker follows the CURRENT holder, so it is the one column that is meant to
     * change: a retry after a crash runs under a new acquisition. Recovery matches the outbox
     * claim against this value, so leaving a dead sender's epoch here strands the operation.
     * Only an in-flight row adopts a marker; a settled one keeps the epoch that produced it.
     */
    @Query(
        """
        UPDATE delivery_dispatch_prepare SET lease_marker = :marker
        WHERE operation_id = :operationId AND settled_at IS NULL
        """,
    )
    suspend fun adoptLeaseMarker(operationId: UUID, marker: String): Int

    /**
     * Puts an operation whose outcome was never known back in flight, so the SAME intent UUID,
     * bytes and base version can be retried. Only an OUTCOME_UNKNOWN row reopens: a terminal
     * outcome stays terminal and an in-flight row is never stolen from a live sender.
     */
    @Query(
        """
        UPDATE delivery_dispatch_prepare SET settled_at = NULL, settled_outcome = NULL
        WHERE operation_id = :operationId AND settled_outcome = 'OUTCOME_UNKNOWN'
        """,
    )
    suspend fun reopenUnknownSettlement(operationId: UUID): Int
}
