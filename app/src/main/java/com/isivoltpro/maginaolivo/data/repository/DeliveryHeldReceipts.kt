package com.isivoltpro.maginaolivo.data.repository

import androidx.room.withTransaction
import com.isivoltpro.maginaolivo.data.local.MaginaOlivoDatabase
import com.isivoltpro.maginaolivo.data.local.entity.DeliveryHeldReceiptEntity
import com.isivoltpro.maginaolivo.data.local.model.OutboxOperation
import com.isivoltpro.maginaolivo.data.local.model.OutboxStatus
import com.isivoltpro.maginaolivo.data.local.model.SyncEntityType
import java.time.Instant
import java.util.UUID

/**
 * A server ACK as received by the authenticated sender: [transportSubject] is the user id of
 * the session that made the request, [accountId] the owner the server reports. Building this
 * value does not authenticate it; only the future authenticated transport may construct one.
 */
internal data class AuthenticatedDeliveryAck(
    val operationId: UUID,
    val deliveryId: UUID,
    val farmId: UUID,
    val accountId: String,
    val transportSubject: String,
    val remoteVersion: Long,
    val payloadHash: String,
    val payloadVersion: Int,
)

internal enum class HeldReceiptOutcome { HELD, ALREADY_HELD, RECEIPT_CONFLICT, REJECTED }

private val payloadHashFormat = Regex("^[0-9a-f]{64}$")

/**
 * G2.4: an ACK that arrives after the farm's consent was revoked. In ONE transaction it is
 * validated against the exact BLOCKED operation and kept as evidence; the operation stays
 * BLOCKED. Kilos, field data, remoteVersion and syncStatus are never touched. An identical
 * duplicate is idempotent; an incompatible one never replaces the first receipt.
 */
internal suspend fun MaginaOlivoDatabase.retainAckAfterRevocation(
    ack: AuthenticatedDeliveryAck,
    revocationOperationId: UUID,
    at: Instant,
): HeldReceiptOutcome = withTransaction {
    val outbox = syncOutboxDao()
    val held = deliveryHeldReceiptDao()
    val op = outbox.findById(ack.operationId) ?: return@withTransaction HeldReceiptOutcome.REJECTED
    val payload = op.payloadJson ?: return@withTransaction HeldReceiptOutcome.REJECTED
    if (ack.transportSubject.isBlank() || ack.transportSubject != ack.accountId ||
        op.status != OutboxStatus.BLOCKED || op.entityType != SyncEntityType.DELIVERY ||
        op.operation !in setOf(OutboxOperation.CREATE, OutboxOperation.UPDATE) ||
        op.entityId != ack.deliveryId || op.accountId == null || op.accountId != ack.accountId ||
        op.farmId != ack.farmId || payload.isBlank() ||
        op.payloadVersion !in 1..2 || op.payloadVersion != ack.payloadVersion ||
        ack.remoteVersion < 1L || !payloadHashFormat.matches(ack.payloadHash) ||
        (op.operation == OutboxOperation.CREATE && op.baseRemoteVersion != null) ||
        (op.operation == OutboxOperation.UPDATE &&
            (op.baseRemoteVersion == null || ack.remoteVersion <= op.baseRemoteVersion)) ||
        deliveryDao().findById(ack.deliveryId)?.farmId != ack.farmId
    ) return@withTransaction HeldReceiptOutcome.REJECTED

    val millis = at.toEpochMilli()
    held.findByOperation(op.id)?.let { existing ->
        val same = existing.deliveryId == ack.deliveryId && existing.accountId == ack.accountId &&
            existing.farmId == ack.farmId && existing.remoteVersion == ack.remoteVersion &&
            existing.payloadHash == ack.payloadHash && existing.payloadVersion == ack.payloadVersion
        if (same && existing.state != DeliveryHeldReceiptEntity.CONFLICT) return@withTransaction HeldReceiptOutcome.ALREADY_HELD
        if (!same) held.markConflict(op.id, ack.remoteVersion, ack.payloadHash)
        outbox.annotateBlockedDelivery(op.id, RECEIPT_CONFLICT, millis)
        return@withTransaction HeldReceiptOutcome.RECEIPT_CONFLICT
    }
    if (deliveryPayloadHash(payload, op.payloadVersion) != ack.payloadHash) {
        // The server claims other bytes for this UUID: not a receipt for what was sent.
        outbox.annotateBlockedDelivery(op.id, RECEIPT_CONFLICT, millis)
        return@withTransaction HeldReceiptOutcome.RECEIPT_CONFLICT
    }
    held.insert(DeliveryHeldReceiptEntity(
        operationId = op.id, deliveryId = ack.deliveryId, accountId = ack.accountId, farmId = ack.farmId,
        remoteVersion = ack.remoteVersion, payloadHash = ack.payloadHash, payloadVersion = ack.payloadVersion,
        revocationOperationId = revocationOperationId, receivedAt = at,
    ))
    check(outbox.annotateBlockedDelivery(op.id, REVOKED_ACK_HELD, millis) == 1)
    HeldReceiptOutcome.HELD
}

internal const val REVOKED_ACK_HELD = "REVOKED_ACK_HELD"
internal const val RECEIPT_CONFLICT = "RECEIPT_CONFLICT"
