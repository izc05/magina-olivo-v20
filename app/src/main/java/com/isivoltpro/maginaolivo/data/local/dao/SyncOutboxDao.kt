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
                AND NOT (sibling.entity_type = 'CAMPAIGN' AND sibling.status = 'BLOCKED'
                         AND sibling.last_error_code IN ('RESOLVED_CAS_CONFLICT', 'RESOLVED_NEEDS_REBASE'))
          )
          -- G2.4: a late ACK held after revocation freezes its whole Delivery until reconciled.
          AND NOT EXISTS (
              SELECT 1 FROM delivery_held_receipts held
              WHERE sync_outbox.entity_type = 'DELIVERY' AND held.delivery_id = sync_outbox.entity_id
                AND held.state != 'RECONCILED'
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

    /** A local edit may replace only a never prepared CAMPAIGN marker. */
    @Query("DELETE FROM sync_outbox WHERE entity_type = 'CAMPAIGN' AND entity_id = :campaignId AND operation = 'UPDATE' AND status = 'PENDING' AND attempt_count = 0 AND payload_json IS NULL AND account_id IS NULL")
    suspend fun deleteUnstagedCampaignMarkers(campaignId: UUID)

    @Query("""
        UPDATE sync_outbox SET account_id = :accountId, farm_id = :farmId,
            base_remote_version = :baseVersion, payload_json = :payloadJson,
            updated_at = :nowEpochMillis
        WHERE id = :operationId AND entity_type = 'CAMPAIGN' AND operation = 'UPDATE'
          AND status = 'PENDING' AND attempt_count = 0 AND payload_json IS NULL
          AND account_id IS NULL AND base_remote_version IS NULL
    """)
    suspend fun stageCampaignCas(operationId: UUID, accountId: String, farmId: UUID,
        baseVersion: Long, payloadJson: String, nowEpochMillis: Long): Int

    @Query("""
        UPDATE sync_outbox SET status = 'FAILED', attempt_count = attempt_count + 1,
            last_error_code = 'OUTCOME_UNKNOWN', last_error_message = NULL,
            updated_at = :nowEpochMillis
        WHERE id = :operationId AND entity_type = 'CAMPAIGN' AND operation = 'UPDATE'
          AND status IN ('PENDING', 'FAILED') AND account_id = :accountId
          AND payload_json = :payloadJson
    """)
    suspend fun markCampaignUnknown(operationId: UUID, accountId: String,
        payloadJson: String, nowEpochMillis: Long): Int

    @Query("""
        UPDATE sync_outbox SET status = 'BLOCKED', last_error_code = :reason,
            last_error_message = NULL, updated_at = :nowEpochMillis
        WHERE id = :operationId AND entity_type = 'CAMPAIGN' AND operation = 'UPDATE'
          AND status IN ('PENDING', 'FAILED') AND account_id = :accountId
          AND payload_json = :payloadJson
    """)
    suspend fun quarantineCampaignCas(operationId: UUID, accountId: String,
        payloadJson: String, reason: String, nowEpochMillis: Long): Int

    /** Keeps the old intent blocked and auditable; only an explicit decision can retire its blocker. */
    @Query("""
        UPDATE sync_outbox SET last_error_code = :resolvedReason, updated_at = :nowEpochMillis
        WHERE id = :operationId AND entity_type = 'CAMPAIGN' AND operation = 'UPDATE'
          AND status = 'BLOCKED' AND account_id = :accountId
          AND last_error_code IN ('CAS_CONFLICT', 'NEEDS_REBASE')
    """)
    suspend fun resolveCampaignBlock(operationId: UUID, accountId: String,
        resolvedReason: String, nowEpochMillis: Long): Int

    @Query("""
        UPDATE sync_outbox SET last_error_code = 'RESOLVED_NEEDS_REBASE', updated_at = :nowEpochMillis
        WHERE id = :operationId AND entity_type = 'CAMPAIGN' AND operation = 'UPDATE'
          AND status = 'BLOCKED' AND account_id IS NULL AND attempt_count = 0
          AND payload_json IS NULL AND last_error_code = 'NEEDS_REBASE'
    """)
    suspend fun resolveUnstagedCampaignBlock(operationId: UUID, nowEpochMillis: Long): Int

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
                AND NOT (sibling.entity_type = 'CAMPAIGN' AND sibling.status = 'BLOCKED'
                         AND sibling.last_error_code IN ('RESOLVED_CAS_CONFLICT', 'RESOLVED_NEEDS_REBASE'))
          )
          -- G2.4: a late ACK held after revocation freezes its whole Delivery until reconciled.
          AND NOT EXISTS (
              SELECT 1 FROM delivery_held_receipts held
              WHERE sync_outbox.entity_type = 'DELIVERY' AND held.delivery_id = sync_outbox.entity_id
                AND held.state != 'RECONCILED'
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
          AND NOT EXISTS (
              SELECT 1 FROM delivery_held_receipts held
              WHERE held.delivery_id = sync_outbox.entity_id AND held.state != 'RECONCILED'
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
          AND status = 'PROCESSING' AND last_error_code = :previousMarker
          AND :previousMarker != :currentMarker
          AND substr(last_error_code, 1, length(:namespacePrefix)) = :namespacePrefix
        """,
    )
    suspend fun recoverDeliveryLeaseClaim(
        operationId: UUID, accountId: String, farmId: UUID, previousMarker: String,
        currentMarker: String, namespacePrefix: String, nowEpochMillis: Long,
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

    /** Annotates a revoked operation with why it stays frozen; it remains BLOCKED. */
    @Query(
        """
        UPDATE sync_outbox SET last_error_code = :errorCode, last_error_message = NULL, updated_at = :nowEpochMillis
        WHERE id = :operationId AND entity_type = 'DELIVERY' AND status = 'BLOCKED'
        """,
    )
    suspend fun annotateBlockedDelivery(operationId: UUID, errorCode: String, nowEpochMillis: Long): Int

    /**
     * Reconciliation only: lets a HELD receipt be applied by the normal receipt path inside the
     * same transaction. Nothing else may move a BLOCKED Delivery operation back.
     */
    @Query(
        """
        UPDATE sync_outbox SET status = 'PROCESSING', updated_at = :nowEpochMillis
        WHERE id = :operationId AND account_id = :accountId AND farm_id = :farmId
          AND entity_type = 'DELIVERY' AND status = 'BLOCKED'
          AND EXISTS (
              SELECT 1 FROM delivery_held_receipts held
              WHERE held.operation_id = sync_outbox.id AND held.state = 'HELD'
          )
        """,
    )
    suspend fun releaseHeldDeliveryForReceipt(operationId: UUID, accountId: String, farmId: UUID, nowEpochMillis: Long): Int

    /**
     * Reconciliation of a BLOCKED Delivery whose server result is not known: the SAME UUID,
     * bytes and base version become eligible again. An attempted one is OUTCOME_UNKNOWN, so
     * the server's idempotency decides; a never-attempted one is simply pending again.
     */
    @Query(
        """
        UPDATE sync_outbox
        SET status = CASE WHEN attempt_count = 0 THEN 'PENDING' ELSE 'FAILED' END,
            last_error_code = CASE WHEN attempt_count = 0 THEN NULL ELSE 'OUTCOME_UNKNOWN' END,
            last_error_message = NULL, next_attempt_at = NULL, updated_at = :nowEpochMillis
        WHERE id = :operationId AND account_id = :accountId AND farm_id = :farmId
          AND account_id != '' AND payload_json IS NOT NULL AND payload_json != ''
          AND entity_type = 'DELIVERY' AND operation IN ('CREATE', 'UPDATE') AND status = 'BLOCKED'
          AND (last_error_code IS NULL OR last_error_code != 'RECEIPT_CONFLICT')
          AND NOT EXISTS (SELECT 1 FROM delivery_held_receipts held WHERE held.operation_id = sync_outbox.id)
        """,
    )
    suspend fun reopenBlockedDeliveryWithoutReceipt(operationId: UUID, accountId: String, farmId: UUID, nowEpochMillis: Long): Int

    /**
     * G2.4: claims a Delivery intent for dispatch at PREPARE time. Same guards as recovery,
     * but it starts from PENDING or FAILED, never from PROCESSING: a slow request still owns
     * its operation. The UUID, bytes and base version are preserved.
     */
    @Query(
        """
        UPDATE sync_outbox
        SET status = 'PROCESSING', updated_at = :nowEpochMillis
        WHERE id = :operationId AND account_id = :accountId AND farm_id = :farmId
          AND account_id != '' AND payload_json IS NOT NULL AND payload_json != ''
          AND entity_type = 'DELIVERY' AND operation IN ('CREATE', 'UPDATE')
          AND payload_version IN (1, 2) AND status IN ('PENDING', 'FAILED')
          AND NOT EXISTS (
              SELECT 1 FROM sync_outbox sibling
              WHERE sibling.entity_type = sync_outbox.entity_type
                AND sibling.entity_id = sync_outbox.entity_id AND sibling.id != sync_outbox.id
                AND sibling.status IN ('PENDING', 'FAILED', 'PROCESSING', 'BLOCKED')
          )
          AND NOT EXISTS (
              SELECT 1 FROM delivery_held_receipts held
              WHERE held.delivery_id = sync_outbox.entity_id AND held.state != 'RECONCILED'
          )
        """,
    )
    suspend fun claimPreparedDeliveryForDispatch(
        operationId: UUID,
        accountId: String,
        farmId: UUID,
        nowEpochMillis: Long,
    ): Int

    @Query("SELECT COUNT(*) FROM sync_outbox WHERE status IN ('PENDING', 'PROCESSING', 'FAILED')")
    suspend fun countActive(): Int
}
