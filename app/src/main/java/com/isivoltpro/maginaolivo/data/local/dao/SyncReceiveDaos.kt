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
     * Stores [receipt] only if it adds evidence. Identical evidence (everything but [SyncReceiveReceiptEntity.receivedAt])
     * writes nothing and returns false; new or changed evidence returns true.
     */
    @Transaction
    open suspend fun record(receipt: SyncReceiveReceiptEntity): Boolean {
        val existing = find(receipt.issuer, receipt.accountId, receipt.entityType, receipt.entityId)
        if (existing == null) { insert(receipt); return true }
        if (existing == receipt.copy(receivedAt = existing.receivedAt)) return false
        check(update(receipt) == 1)
        return true
    }
}

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
