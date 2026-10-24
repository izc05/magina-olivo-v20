package com.isivoltpro.maginaolivo.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.isivoltpro.maginaolivo.data.local.entity.SyncOutboxEntity
import com.isivoltpro.maginaolivo.data.local.model.SyncEntityType
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface SyncOutboxDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(operation: SyncOutboxEntity)

    @Query(
        """
        SELECT * FROM sync_outbox
        WHERE status IN ('PENDING', 'FAILED')
          AND (next_attempt_at IS NULL OR next_attempt_at <= :nowEpochMillis)
          -- A second unresolved snapshot of the same aggregate may carry a stale base version.
          -- Fail closed: G2.4 must reconcile/ACK/rebase before making either eligible.
          AND NOT EXISTS (
              SELECT 1 FROM sync_outbox sibling
              WHERE sibling.entity_type = sync_outbox.entity_type
                AND sibling.entity_id = sync_outbox.entity_id
                AND sibling.id != sync_outbox.id
                AND sibling.status IN ('PENDING', 'FAILED', 'PROCESSING', 'BLOCKED')
          )
        ORDER BY created_at, id
        """,
    )
    fun observeReady(nowEpochMillis: Long): Flow<List<SyncOutboxEntity>>

    @Query(
        """
        SELECT * FROM sync_outbox
        WHERE entity_type = :entityType AND entity_id = :entityId
        ORDER BY created_at, id
        """,
    )
    suspend fun listForEntity(
        entityType: SyncEntityType,
        entityId: UUID,
    ): List<SyncOutboxEntity>

    @Query("DELETE FROM sync_outbox WHERE entity_type = :entityType AND entity_id = :entityId AND status IN ('PENDING', 'FAILED')")
    suspend fun deletePendingForEntity(entityType: SyncEntityType, entityId: UUID)

    @Query("DELETE FROM sync_outbox WHERE entity_type = :entityType AND entity_id = :entityId AND status = 'PENDING'")
    suspend fun deleteOnlyPendingForEntity(entityType: SyncEntityType, entityId: UUID)

    /**
     * Records a failed attempt on the upload intents of one entity. The intent stays in the
     * queue, so a later attempt can retry it; nothing here touches the entity itself.
     */
    @Query(
        """
        UPDATE sync_outbox
        SET status = 'FAILED',
            attempt_count = attempt_count + 1,
            last_error_code = :errorCode,
            last_error_message = :errorMessage,
            updated_at = :nowEpochMillis
        WHERE entity_type = :entityType AND entity_id = :entityId
          AND operation = 'UPLOAD_ATTACHMENT'
          AND status IN ('PENDING', 'PROCESSING', 'FAILED')
        """,
    )
    suspend fun recordUploadFailure(
        entityType: SyncEntityType,
        entityId: UUID,
        errorCode: String,
        errorMessage: String?,
        nowEpochMillis: Long,
    ): Int

    @Query("SELECT * FROM sync_outbox WHERE id = :id")
    suspend fun findById(id: UUID): SyncOutboxEntity?

    @Query(
        """
        SELECT * FROM sync_outbox
        WHERE account_id = :accountId
          AND status IN ('PENDING', 'FAILED')
          AND (next_attempt_at IS NULL OR next_attempt_at <= :nowEpochMillis)
          -- Never present competing snapshots of one aggregate to the network worker.
          -- Check across ALL accounts: old account-bound intents must not be ignored.
          AND NOT EXISTS (
              SELECT 1 FROM sync_outbox sibling
              WHERE sibling.entity_type = sync_outbox.entity_type
                AND sibling.entity_id = sync_outbox.entity_id
                AND sibling.id != sync_outbox.id
                AND sibling.status IN ('PENDING', 'FAILED', 'PROCESSING', 'BLOCKED')
          )
        ORDER BY created_at, id
        """,
    )
    suspend fun listReadyForAccount(accountId: String, nowEpochMillis: Long): List<SyncOutboxEntity>

    /**
     * Recover one uncertain Delivery result before dispatching its newer snapshot.
     * This does not grant consent or authorize HTTP. The caller must check those afresh.
     * No age-based PROCESSING takeover: a slow request may still own that operation.
     * The atomic UPDATE preserves the old UUID, bytes and base version.
     */
    @Query(
        """
        UPDATE sync_outbox
        SET status = 'PROCESSING', attempt_count = attempt_count + 1,
            updated_at = :nowEpochMillis
        WHERE id = :operationId AND account_id = :accountId AND farm_id = :farmId
          AND account_id != '' AND payload_json IS NOT NULL AND payload_json != ''
          AND entity_type = 'DELIVERY' AND operation IN ('CREATE', 'UPDATE')
          AND payload_version IN (1, 2) AND status = 'FAILED'
          AND (next_attempt_at IS NULL OR next_attempt_at <= :nowEpochMillis)
          AND NOT EXISTS (
              SELECT 1 FROM sync_outbox sibling
              WHERE sibling.entity_type = sync_outbox.entity_type
                AND sibling.entity_id = sync_outbox.entity_id AND sibling.id != sync_outbox.id
                AND sibling.status != 'CONFIRMED'
                AND (sibling.status != 'PENDING' OR sibling.attempt_count != 0
                     OR sibling.account_id IS NULL OR sibling.account_id != :accountId
                     OR sibling.farm_id IS NULL OR sibling.farm_id != :farmId)
          )
        """,
    )
    suspend fun claimFailedDeliveryForRecovery(
        operationId: UUID,
        accountId: String,
        farmId: UUID,
        nowEpochMillis: Long,
    ): Int

    @Query(
        """
        UPDATE sync_outbox SET last_error_code = :claimMarker, last_error_message = NULL
        WHERE id = :operationId AND account_id = :accountId AND farm_id = :farmId
          AND entity_type = 'DELIVERY' AND operation IN ('CREATE', 'UPDATE') AND status = 'PROCESSING'
        """,
    )
    suspend fun markDeliveryLeaseClaim(operationId: UUID, accountId: String, farmId: UUID, claimMarker: String): Int

    /** Only a new owner of the SAME OS lock can resolve this protocol's interrupted claim. */
    @Query(
        """
        UPDATE sync_outbox SET status = 'FAILED', next_attempt_at = NULL,
            last_error_code = 'OUTCOME_UNKNOWN', last_error_message = NULL, updated_at = :nowEpochMillis
        WHERE id = :operationId AND account_id = :accountId AND farm_id = :farmId
          AND entity_type = 'DELIVERY' AND operation IN ('CREATE', 'UPDATE')
          AND status = 'PROCESSING' AND last_error_code = :claimMarker
        """,
    )
    suspend fun recoverDeliveryLeaseClaim(
        operationId: UUID, accountId: String, farmId: UUID, claimMarker: String, nowEpochMillis: Long,
    ): Int

    @Query(
        """
        SELECT * FROM sync_outbox
        WHERE farm_id = :farmId
          AND status IN ('PENDING', 'FAILED')
        ORDER BY created_at, id
        """,
    )
    suspend fun listPendingForFarm(farmId: UUID): List<SyncOutboxEntity>

    @Query(
        """
        UPDATE sync_outbox
        SET status = :status,
            updated_at = :nowEpochMillis
        WHERE farm_id = :farmId AND status IN ('PENDING', 'FAILED', 'PROCESSING')
        """,
    )
    suspend fun updateStatusForFarm(farmId: UUID, status: com.isivoltpro.maginaolivo.data.local.model.OutboxStatus, nowEpochMillis: Long): Int

    /**
     * Store a receipt only for an unresolved, dispatch-capable intent. A revoked BLOCKED
     * operation cannot be promoted by a late callback; a CONFIRMED operation is immutable.
     */
    @Query(
        """
        UPDATE sync_outbox SET status = 'CONFIRMED', updated_at = :nowEpochMillis
        WHERE id = :operationId AND status IN ('PENDING', 'PROCESSING', 'FAILED')
        """,
    )
    suspend fun markConfirmed(operationId: UUID, nowEpochMillis: Long): Int

    /**
     * Freeze newer/competing snapshots after receiving an ACK for an older one.
     * Rebuilding baseVersion and deciding what was applied belongs to G2.4 recovery.
     */
    @Query(
        """
        UPDATE sync_outbox SET status = 'BLOCKED', last_error_code = 'NEEDS_REBASE', updated_at = :nowEpochMillis
        WHERE entity_type = :entityType AND entity_id = :entityId AND id != :confirmedOperationId
          AND status IN ('PENDING', 'PROCESSING', 'FAILED')
        """,
    )
    suspend fun quarantineSiblings(
        entityType: SyncEntityType,
        entityId: UUID,
        confirmedOperationId: UUID,
        nowEpochMillis: Long,
    ): Int

    /** Only snapshots explicitly quarantined for rebase and never attempted may be replaced. */
    @Query(
        """
        DELETE FROM sync_outbox
        WHERE entity_type = 'DELIVERY' AND entity_id = :deliveryId
          AND account_id = :accountId AND farm_id = :farmId
          AND status = 'BLOCKED' AND last_error_code = 'NEEDS_REBASE' AND attempt_count = 0
        """,
    )
    suspend fun deleteUnattemptedRebaseSnapshots(deliveryId: UUID, accountId: String, farmId: UUID): Int

    @Query("SELECT COUNT(*) FROM sync_outbox WHERE status IN ('PENDING', 'PROCESSING', 'FAILED')")
    suspend fun countActive(): Int
}
