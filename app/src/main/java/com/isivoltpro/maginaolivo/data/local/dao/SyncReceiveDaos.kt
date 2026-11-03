package com.isivoltpro.maginaolivo.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.isivoltpro.maginaolivo.data.local.entity.SyncReceiveHoldEntity
import com.isivoltpro.maginaolivo.data.local.entity.SyncReceiveReceiptEntity
import java.util.UUID

/** #808 R3-a. Every write is idempotent: the same evidence twice is zero writes. */
@Dao
abstract class SyncReceiveReceiptDao {
    @Query("""
        SELECT * FROM sync_receive_receipts
        WHERE issuer = :issuer AND account_id = :accountId AND entity_type = :entityType AND entity_id = :entityId
    """)
    abstract suspend fun find(issuer: String, accountId: String, entityType: String, entityId: UUID): SyncReceiveReceiptEntity?

    @Query("""
        SELECT * FROM sync_receive_receipts WHERE issuer = :issuer AND account_id = :accountId AND harvest_id = :harvestId
        ORDER BY entity_type, entity_id
    """)
    abstract suspend fun listForHarvest(issuer: String, accountId: String, harvestId: UUID): List<SyncReceiveReceiptEntity>

    @Query("SELECT COUNT(*) FROM sync_receive_receipts")
    abstract suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insert(receipt: SyncReceiveReceiptEntity)

    @Update
    protected abstract suspend fun update(receipt: SyncReceiveReceiptEntity): Int

    /**
     * Records verified receive evidence without ever going back (#808 R3-a correction, condition for R3-c):
     * - no receipt yet: [ReceiptRecordOutcome.INSERTED];
     * - a LOWER remote version than the stored one (an old bundle, a retried read): [ReceiptRecordOutcome.STALE], nothing written;
     * - the SAME version with identical evidence (everything but receivedAt): [ReceiptRecordOutcome.UNCHANGED], nothing written;
     * - the SAME version with contradictory evidence (head, hash, fingerprints, deleted, Jornada or contract):
     *   [ReceiptRecordOutcome.CONFLICT], nothing replaced (the caller records a verifiable hold, see recordReceiveEvidence);
     * - a HIGHER version: [ReceiptRecordOutcome.UPDATED];
     * - the SAME version where ONLY `derived_fingerprint` differs: [ReceiptRecordOutcome.UPDATED]. An AUTO_DAY's derived kilos
     *   and split change with its Pesadas without moving the Jornada version (R1-c contract), so this is new evidence, not
     *   a rewrite. Any other difference at the same version stays a CONFLICT.
     */
    @Transaction
    open suspend fun record(receipt: SyncReceiveReceiptEntity): ReceiptRecordOutcome {
        val existing = find(receipt.issuer, receipt.accountId, receipt.entityType, receipt.entityId)
            ?: run { insert(receipt); return ReceiptRecordOutcome.INSERTED }
        return when {
            receipt.remoteVersion < existing.remoteVersion -> ReceiptRecordOutcome.STALE
            receipt.remoteVersion == existing.remoteVersion -> when {
                existing == receipt.copy(receivedAt = existing.receivedAt) -> ReceiptRecordOutcome.UNCHANGED
                existing.copy(derivedFingerprint = receipt.derivedFingerprint, receivedAt = receipt.receivedAt) == receipt ->
                    { check(update(receipt) == 1); ReceiptRecordOutcome.UPDATED }
                else -> ReceiptRecordOutcome.CONFLICT
            }
            else -> { check(update(receipt) == 1); ReceiptRecordOutcome.UPDATED }
        }
    }
}

/** #808 R3-a: what [SyncReceiveReceiptDao.record] did. Only INSERTED and UPDATED wrote a receipt. */
enum class ReceiptRecordOutcome { INSERTED, UPDATED, UNCHANGED, STALE, CONFLICT }

/** #808 R3-a. Holds are observed, never resolved here: there is deliberately no method that sets `resolved_at`. */
@Dao
abstract class SyncReceiveHoldDao {
    @Query("""
        SELECT * FROM sync_receive_holds
        WHERE issuer = :issuer AND account_id = :accountId AND entity_type = :entityType AND entity_id = :entityId AND cause = :cause
    """)
    abstract suspend fun find(issuer: String, accountId: String, entityType: String, entityId: UUID, cause: String): SyncReceiveHoldEntity?

    @Query("""
        SELECT * FROM sync_receive_holds WHERE issuer = :issuer AND account_id = :accountId AND harvest_id = :harvestId
        ORDER BY entity_type, entity_id, cause
    """)
    abstract suspend fun listForHarvest(issuer: String, accountId: String, harvestId: UUID): List<SyncReceiveHoldEntity>

    @Query("""
        SELECT * FROM sync_receive_holds WHERE issuer = :issuer AND account_id = :accountId AND resolved_at IS NULL
        ORDER BY harvest_id, entity_type, entity_id, cause
    """)
    abstract suspend fun listOpen(issuer: String, accountId: String): List<SyncReceiveHoldEntity>

    @Query("SELECT COUNT(*) FROM sync_receive_holds")
    abstract suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insert(hold: SyncReceiveHoldEntity)

    @Update
    protected abstract suspend fun update(hold: SyncReceiveHoldEntity): Int

    /**
     * Records that [hold] was observed at [hold].lastSeenAt. A new hold is inserted. The same hold (same Jornada,
     * versions, remote hash and detail) writes nothing and returns false, not even `last_seen_at`. A changed one keeps
     * its `first_seen_at` and its `resolved_at` and updates the rest. The detail is cut to 512 characters.
     */
    @Transaction
    open suspend fun observe(hold: SyncReceiveHoldEntity): Boolean {
        val bounded = hold.copy(detail = hold.detail?.take(DETAIL_MAX), resolvedAt = null)
        val existing = find(bounded.issuer, bounded.accountId, bounded.entityType, bounded.entityId, bounded.cause)
        if (existing == null) { insert(bounded.copy(firstSeenAt = bounded.lastSeenAt)); return true }
        val same = existing.harvestId == bounded.harvestId && existing.localVersion == bounded.localVersion &&
            existing.remoteVersion == bounded.remoteVersion && existing.remoteHash == bounded.remoteHash && existing.detail == bounded.detail
        if (same) return false
        check(update(bounded.copy(firstSeenAt = existing.firstSeenAt, resolvedAt = existing.resolvedAt)) == 1)
        return true
    }

    companion object {
        const val DETAIL_MAX = 512
    }
}
