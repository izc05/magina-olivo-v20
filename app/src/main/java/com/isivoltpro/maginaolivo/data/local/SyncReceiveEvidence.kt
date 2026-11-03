package com.isivoltpro.maginaolivo.data.local

import androidx.room.withTransaction
import com.isivoltpro.maginaolivo.data.local.dao.ReceiptRecordOutcome
import com.isivoltpro.maginaolivo.data.local.entity.SyncReceiveHoldEntity
import com.isivoltpro.maginaolivo.data.local.entity.SyncReceiveReceiptEntity

/** Hold cause for the same remote version received with contradictory evidence (design §5, REMOTE_REWRITTEN). */
const val RECEIVE_HOLD_REMOTE_REWRITTEN = "REMOTE_REWRITTEN"

/**
 * #808 R3-a correction: the ONE way the future receiver (R3-c) records evidence. In one transaction it calls
 * [com.isivoltpro.maginaolivo.data.local.dao.SyncReceiveReceiptDao.record] and, on [ReceiptRecordOutcome.CONFLICT],
 * leaves a verifiable hold (REMOTE_REWRITTEN) with both values: the stored head and hash in `detail`, the incoming hash
 * in `remote_hash`. The stored receipt is never replaced; existing receipts, holds and outbox are otherwise untouched.
 * Not wired in shipped code: no receiver exists yet.
 */
suspend fun MaginaOlivoDatabase.recordReceiveEvidence(receipt: SyncReceiveReceiptEntity): ReceiptRecordOutcome = withTransaction {
    val outcome = syncReceiveReceiptDao().record(receipt)
    if (outcome == ReceiptRecordOutcome.CONFLICT) {
        val stored = checkNotNull(syncReceiveReceiptDao().find(receipt.issuer, receipt.accountId, receipt.entityType, receipt.entityId))
        syncReceiveHoldDao().observe(SyncReceiveHoldEntity(
            issuer = receipt.issuer, accountId = receipt.accountId, entityType = receipt.entityType, entityId = receipt.entityId,
            cause = RECEIVE_HOLD_REMOTE_REWRITTEN, harvestId = receipt.harvestId ?: stored.harvestId,
            localVersion = stored.remoteVersion, remoteVersion = receipt.remoteVersion,
            remoteHash = receipt.headPayloadHash ?: receipt.ownFingerprint,
            detail = "stored_op=${stored.headOperationId};stored_hash=${stored.headPayloadHash ?: stored.ownFingerprint};" +
                "stored_deleted=${stored.deleted};incoming_op=${receipt.headOperationId};incoming_deleted=${receipt.deleted}",
            firstSeenAt = receipt.receivedAt, lastSeenAt = receipt.receivedAt,
        ))
    }
    outcome
}
