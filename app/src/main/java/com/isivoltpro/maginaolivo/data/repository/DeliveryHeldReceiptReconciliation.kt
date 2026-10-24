package com.isivoltpro.maginaolivo.data.repository

import androidx.room.withTransaction
import com.isivoltpro.maginaolivo.core.id.IdGenerator
import com.isivoltpro.maginaolivo.data.local.MaginaOlivoDatabase
import com.isivoltpro.maginaolivo.data.local.entity.DeliveryHeldReceiptEntity
import com.isivoltpro.maginaolivo.data.local.model.OutboxOperation
import com.isivoltpro.maginaolivo.data.local.model.OutboxStatus
import com.isivoltpro.maginaolivo.data.local.model.SyncEntityType
import com.isivoltpro.maginaolivo.data.repository.sync.DeliveryDispatchLease
import com.isivoltpro.maginaolivo.feature.accountlink.FarmLinkLedger
import java.time.Instant
import java.util.UUID

/**
 * What the server says, read with the server-verified session: its record of this operation
 * UUID (null when not found or unknown) and the Delivery's current head.
 */
internal data class RemoteDeliveryState(
    val operationReceipt: RecoveredDeliveryReceipt?,
    val headVersion: Long?,
    val headPayloadHash: String?,
)

internal enum class HeldReconciliationOutcome {
    SYNCED, NEEDS_REBASE, REBASED, REOPENED,
    NOT_CONFIRMED, NOT_AUTHORIZED, REMOTE_CONFLICT, RECEIPT_CONFLICT, REJECTED,
}

/** Everything a reconciliation needs that is NOT in Room; each part is checked again here. */
internal class HeldReconciliationAuthority(
    /** The farmer explicitly asked to reconcile this Pesada; never set by a background job. */
    val farmerConfirmed: Boolean,
    /** Identity the server confirmed within this same identity generation. */
    val verifiedAccountId: String,
    /** The ledger subject that identity must own ("issuer|userId"). */
    val subject: String,
    /** Fresh durable ledger; null when missing, unreadable or another account's. */
    val ledger: FarmLinkLedger?,
    /** Held across the whole reconciliation: no other sender can have a request in flight. */
    val lease: DeliveryDispatchLease,
)

private class RolledBack(val outcome: HeldReconciliationOutcome) : Exception()

/**
 * G2.4: explicit reconciliation of a Delivery operation left BLOCKED by a consent revocation.
 * Requires the farmer's action, a server-verified identity, renewed consent that FOLLOWS the
 * blocking revocation in ledger order, and the dispatch lease. A held receipt is applied only
 * if the server head is still that receipt (otherwise REMOTE_CONFLICT). Without a receipt, an
 * authenticated server record is verified like a recovery receipt; when the result is unknown
 * the SAME UUID and payload become eligible again. Account-less edits are never adopted.
 */
internal suspend fun MaginaOlivoDatabase.reconcileBlockedDelivery(
    operationId: UUID,
    authority: HeldReconciliationAuthority,
    remote: RemoteDeliveryState,
    ids: IdGenerator,
    at: Instant,
): HeldReconciliationOutcome = try {
    withTransaction { reconcileInTransaction(operationId, authority, remote, ids, at) }
} catch (rolledBack: RolledBack) {
    rolledBack.outcome
}

private suspend fun MaginaOlivoDatabase.reconcileInTransaction(
    operationId: UUID,
    authority: HeldReconciliationAuthority,
    remote: RemoteDeliveryState,
    ids: IdGenerator,
    at: Instant,
): HeldReconciliationOutcome {
    if (!authority.farmerConfirmed) return HeldReconciliationOutcome.NOT_CONFIRMED
    authority.lease.requireOpen()
    val outbox = syncOutboxDao()
    val heldDao = deliveryHeldReceiptDao()
    val op = outbox.findById(operationId) ?: return HeldReconciliationOutcome.REJECTED
    val account = authority.verifiedAccountId
    val farm = op.farmId ?: return HeldReconciliationOutcome.REJECTED
    val payload = op.payloadJson ?: return HeldReconciliationOutcome.REJECTED
    if (op.status != OutboxStatus.BLOCKED || op.entityType != SyncEntityType.DELIVERY ||
        op.operation !in setOf(OutboxOperation.CREATE, OutboxOperation.UPDATE) ||
        account.isBlank() || op.accountId != account || payload.isBlank() ||
        op.lastErrorCode == "NEEDS_REBASE"
    ) return HeldReconciliationOutcome.REJECTED
    val ledger = authority.ledger
    if (ledger == null || ledger.subject != authority.subject || farm !in ledger.activeFarmIds) {
        return HeldReconciliationOutcome.NOT_AUTHORIZED
    }
    val authorized = { candidate: String, candidateFarm: UUID -> candidate == account && candidateFarm == farm }
    val millis = at.toEpochMilli()

    val held = heldDao.findByOperation(op.id)
    val receipt: DeliverySyncReceipt
    if (held != null) {
        when (held.state) {
            DeliveryHeldReceiptEntity.CONFLICT -> return HeldReconciliationOutcome.RECEIPT_CONFLICT
            DeliveryHeldReceiptEntity.HELD -> Unit
            else -> return HeldReconciliationOutcome.REJECTED
        }
        if (held.accountId != account || held.farmId != farm || held.deliveryId != op.entityId) {
            return HeldReconciliationOutcome.REJECTED
        }
        if (ledger.reconsentAfter(held.revocationOperationId) == null) return HeldReconciliationOutcome.NOT_AUTHORIZED
        if (remote.headVersion != held.remoteVersion || remote.headPayloadHash != held.payloadHash) {
            outbox.annotateBlockedDelivery(op.id, "REMOTE_CONFLICT", millis)
            return HeldReconciliationOutcome.REMOTE_CONFLICT
        }
        if (outbox.releaseHeldDeliveryForReceipt(op.id, account, farm, millis) != 1) return HeldReconciliationOutcome.REJECTED
        receipt = DeliverySyncReceipt(op.id, op.entityId, farm, account, payload, held.remoteVersion)
    } else {
        val renewed = ledger.revocations.any { it.farmId == farm && ledger.reconsentAfter(it.operationId) != null }
        if (!renewed) return HeldReconciliationOutcome.NOT_AUTHORIZED
        if (outbox.reopenBlockedDeliveryWithoutReceipt(op.id, account, farm, millis) != 1) {
            return HeldReconciliationOutcome.REJECTED
        }
        val recovered = remote.operationReceipt ?: return HeldReconciliationOutcome.REOPENED
        val reopened = checkNotNull(outbox.findById(op.id))
        receipt = verifiedRecoveredDeliveryReceipt(reopened, recovered)
            ?: throw RolledBack(HeldReconciliationOutcome.RECEIPT_CONFLICT)
        if (remote.headVersion != recovered.remoteVersion || remote.headPayloadHash != recovered.payloadHash) {
            throw RolledBack(HeldReconciliationOutcome.REMOTE_CONFLICT)
        }
    }

    val applied = applyConfirmedDeliveryReceipt(receipt, at)
    if (applied !in setOf(DeliveryReceiptOutcome.SYNCED, DeliveryReceiptOutcome.NEEDS_REBASE)) {
        throw RolledBack(HeldReconciliationOutcome.REJECTED)
    }
    if (held != null) check(heldDao.markReconciled(op.id, millis) == 1)
    if (applied == DeliveryReceiptOutcome.SYNCED) return HeldReconciliationOutcome.SYNCED
    if (op.payloadVersion != 2) return HeldReconciliationOutcome.NEEDS_REBASE
    // Newer edits bound to this same account are rebased onto the verified head; account-less
    // edits make the rebase refuse, so they stay BLOCKED and are never adopted.
    return when (rebaseConfirmedDeliveryEdit(
        receipt, receipt.remoteVersion, checkNotNull(remote.headPayloadHash), authorized, ids, at,
    )) {
        DeliveryRebaseOutcome.REBASED, DeliveryRebaseOutcome.ALREADY_REBASED -> HeldReconciliationOutcome.REBASED
        else -> HeldReconciliationOutcome.NEEDS_REBASE
    }
}
