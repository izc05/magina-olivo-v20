package com.isivoltpro.maginaolivo.data.repository

import androidx.room.withTransaction
import com.isivoltpro.maginaolivo.data.local.MaginaOlivoDatabase
import com.isivoltpro.maginaolivo.data.local.entity.DeliveryDispatchPrepareEntity
import com.isivoltpro.maginaolivo.data.local.model.OutboxStatus
import com.isivoltpro.maginaolivo.data.local.model.SyncEntityType
import com.isivoltpro.maginaolivo.data.repository.sync.DeliveryDispatchLease
import com.isivoltpro.maginaolivo.feature.accountlink.FarmLinkLedger
import java.time.Instant
import java.util.UUID

/**
 * #808 recovery of a v3 Pesada held as [DeliveryRetentionCodes.V3_ACK_AWAITING_HEAD].
 *
 * What the authenticated reads established, before any local write. [Confirm] only when ALL of
 * these held at once, under one verified session: the server confirmed the session OWNS the
 * workspace right now; this actor's own DELIVERY receipt names this operation, this Pesada,
 * payload version 3 and EXACTLY the prepared hash; and `get_own_delivery_head_v3` names this same
 * operation as the head, at the receipt's version, with version 3 and the same hash, not deleted.
 * Anything else is [Hold] with a traceable cause: no ACK, nothing re-sent, nothing deleted.
 */
internal sealed interface V3HeadEvidence {
    data class Confirm(val remoteVersion: Long) : V3HeadEvidence
    data class Hold(val cause: String) : V3HeadEvidence
}

/** Causes kept in `last_error_message` of a still-held operation (its code stays V3_ACK_AWAITING_HEAD). */
internal object V3RecoveryCauses {
    const val NOT_OWNER = "NOT_OWNER"
    const val NO_RECEIPT = "NO_RECEIPT"
    const val RECEIPT_MISMATCH = "RECEIPT_MISMATCH"
    const val HEAD_NULL = "HEAD_NULL"
    const val HEAD_SUPERSEDED = "HEAD_SUPERSEDED"
    const val HEAD_MISMATCH = "HEAD_MISMATCH"
    const val DELETED = "DELETED"
    const val CONSENT_REVOKED = "CONSENT_REVOKED"
    const val PREPARE_MISMATCH = "PREPARE_MISMATCH"
}

internal enum class V3ResolveOutcome { SYNCED, NEEDS_REBASE, ALREADY_CONFIRMED, HELD, NOT_AUTHORIZED, REJECTED }

private class V3ConfirmationRolledBack : RuntimeException("v3 confirmation rolled back")

/**
 * ONE transaction under the live lease. The SAME operation (UUID, bytes, version, RPC, PREPARE) is
 * either confirmed through the normal receipt path, or stays BLOCKED with its cause recorded. A
 * confirmation that the receipt writer refuses rolls back entirely, so the hold is never lost.
 */
internal suspend fun MaginaOlivoDatabase.resolveAwaitingV3Delivery(
    lease: DeliveryDispatchLease,
    operationId: UUID,
    identity: DispatchIdentity,
    ledger: FarmLinkLedger?,
    evidence: V3HeadEvidence,
    at: Instant,
): V3ResolveOutcome = try {
    withTransaction {
        lease.requireOpen()
        val prepareDao = deliveryDispatchPrepareDao()
        val outbox = syncOutboxDao()
        val prepared = prepareDao.findByOperation(operationId) ?: return@withTransaction V3ResolveOutcome.REJECTED
        val op = outbox.findById(operationId) ?: return@withTransaction V3ResolveOutcome.REJECTED
        if (op.status == OutboxStatus.CONFIRMED && prepared.settledOutcome == DeliveryDispatchPrepareEntity.SETTLED_CONFIRMED) {
            return@withTransaction V3ResolveOutcome.ALREADY_CONFIRMED
        }
        val payload = op.payloadJson ?: return@withTransaction V3ResolveOutcome.REJECTED
        // Same account, issuer, subject and workspace that prepared it; otherwise it is not ours to touch.
        if (op.entityType != SyncEntityType.DELIVERY || op.payloadVersion != 3 || prepared.payloadVersion != 3 ||
            op.status != OutboxStatus.BLOCKED || op.lastErrorCode != DeliveryRetentionCodes.V3_ACK_AWAITING_HEAD ||
            identity.accountId != prepared.accountId || op.accountId != prepared.accountId ||
            identity.issuer != prepared.issuer || identity.subject != prepared.subject ||
            identity.localWorkspaceId != prepared.localWorkspaceId || op.entityId != prepared.deliveryId ||
            op.farmId != prepared.farmId
        ) {
            return@withTransaction V3ResolveOutcome.REJECTED
        }
        val millis = at.toEpochMilli()
        val cause = when {
            deliveryPayloadHash(payload, 3) != prepared.payloadHash -> V3RecoveryCauses.PREPARE_MISMATCH
            ledger == null || ledger.subject != prepared.subject || prepared.farmId !in ledger.activeFarmIds ||
                ledger.latestRevocation(prepared.farmId) != null -> V3RecoveryCauses.CONSENT_REVOKED
            evidence is V3HeadEvidence.Hold -> evidence.cause
            evidence is V3HeadEvidence.Confirm && evidence.remoteVersion < 1L -> V3RecoveryCauses.RECEIPT_MISMATCH
            else -> null
        }
        if (cause != null) {
            check(outbox.annotateAwaitingHead(operationId, prepared.accountId, cause, millis) == 1)
            lease.requireHeld()
            return@withTransaction V3ResolveOutcome.HELD
        }
        val remoteVersion = (evidence as V3HeadEvidence.Confirm).remoteVersion
        check(outbox.reopenAwaitingHeadForConfirmation(operationId, prepared.accountId, millis) == 1)
        val receipt = DeliverySyncReceipt(
            operationId = prepared.operationId,
            deliveryId = prepared.deliveryId,
            farmId = prepared.farmId,
            accountId = prepared.accountId,
            payloadJson = payload,
            remoteVersion = remoteVersion,
        )
        val outcome = when (applyConfirmedDeliveryReceipt(receipt, at)) {
            DeliveryReceiptOutcome.SYNCED -> V3ResolveOutcome.SYNCED
            DeliveryReceiptOutcome.NEEDS_REBASE -> V3ResolveOutcome.NEEDS_REBASE
            DeliveryReceiptOutcome.ALREADY_CONFIRMED, DeliveryReceiptOutcome.REJECTED -> throw V3ConfirmationRolledBack()
        }
        check(prepareDao.settle(operationId, DeliveryDispatchPrepareEntity.SETTLED_CONFIRMED, millis) == 1)
        lease.requireHeld()
        outcome
    }
} catch (_: V3ConfirmationRolledBack) {
    V3ResolveOutcome.REJECTED
}
