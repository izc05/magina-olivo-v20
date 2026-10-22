package com.isivoltpro.maginaolivo.data.repository

import androidx.room.withTransaction
import com.isivoltpro.maginaolivo.data.local.MaginaOlivoDatabase
import com.isivoltpro.maginaolivo.data.local.model.OutboxOperation
import com.isivoltpro.maginaolivo.data.local.model.OutboxStatus
import com.isivoltpro.maginaolivo.data.local.model.SyncEntityType
import com.isivoltpro.maginaolivo.data.local.model.SyncStatus
import java.time.Instant
import java.util.UUID

/**
 * Local-only G2.4 receipt processing. This DOES NOT issue HTTP requests, infer consent,
 * or authorize uploads. Only feed this with a server-confirmed receipt for the exact
 * immutable payload previously submitted by an authenticated, consent-gated worker.
 */
internal data class DeliverySyncReceipt(
    val operationId: UUID,
    val deliveryId: UUID,
    val farmId: UUID,
    val accountId: String,
    val payloadJson: String,
    val remoteVersion: Long,
)

/**
 * Server recovery receipts contain a payload hash, not the original JSON snapshot.
 * Validate the hash against the exact persisted UTF-8 bytes before constructing a
 * local receipt. No network call, permission grant or JSON reserialization occurs here.
 */
internal data class RecoveredDeliveryReceipt(
    val operationId: UUID,
    val deliveryId: UUID,
    val accountId: String,
    val remoteVersion: Long,
    val payloadHash: String,
)

internal fun verifiedRecoveredDeliveryReceipt(
    operation: com.isivoltpro.maginaolivo.data.local.entity.SyncOutboxEntity,
    recovered: RecoveredDeliveryReceipt,
    hashMatches: (String, String) -> Boolean,
): DeliverySyncReceipt? {
    val payload = operation.payloadJson ?: return null
    val farm = operation.farmId ?: return null
    if (recovered.accountId.isBlank() || recovered.remoteVersion < 1L ||
        operation.id != recovered.operationId ||
        operation.entityType != SyncEntityType.DELIVERY ||
        operation.operation !in setOf(OutboxOperation.CREATE, OutboxOperation.UPDATE) ||
        operation.entityId != recovered.deliveryId ||
        operation.accountId != recovered.accountId ||
        operation.status !in setOf(OutboxStatus.PENDING, OutboxStatus.FAILED, OutboxStatus.PROCESSING) ||
        recovered.payloadHash.isBlank() ||
        !hashMatches(payload, recovered.payloadHash)
    ) return null

    return DeliverySyncReceipt(
        operationId = operation.id,
        deliveryId = operation.entityId,
        farmId = farm,
        accountId = recovered.accountId,
        payloadJson = payload,
        remoteVersion = recovered.remoteVersion,
    )
}

internal enum class DeliveryReceiptOutcome {
    SYNCED,
    NEEDS_REBASE,
    ALREADY_CONFIRMED,
    REJECTED,
}

/**
 * One Room transaction: verify the operation binding and exact submitted snapshot, then
 * persist the server ACK. A stale ACK never overwrites local kilos, campaign or notes.
 * If the farmer edited meanwhile, competing snapshots are quarantined until an explicit
 * recovery pass checks server operation history and rebuilds their baseVersion.
 */
internal suspend fun MaginaOlivoDatabase.applyConfirmedDeliveryReceipt(
    receipt: DeliverySyncReceipt,
    at: Instant,
): DeliveryReceiptOutcome = withTransaction {
    val outbox = syncOutboxDao()
    val op = outbox.findById(receipt.operationId)
        ?: return@withTransaction DeliveryReceiptOutcome.REJECTED

    if (receipt.accountId.isBlank() || receipt.remoteVersion < 1L ||
        op.entityType != SyncEntityType.DELIVERY ||
        op.operation !in setOf(OutboxOperation.CREATE, OutboxOperation.UPDATE) ||
        op.entityId != receipt.deliveryId || op.farmId != receipt.farmId ||
        op.accountId == null || op.accountId != receipt.accountId ||
        op.payloadJson == null || op.payloadJson != receipt.payloadJson ||
        (op.operation == OutboxOperation.CREATE && op.baseRemoteVersion != null) ||
        (op.operation == OutboxOperation.UPDATE &&
            (op.baseRemoteVersion == null || receipt.remoteVersion <= op.baseRemoteVersion))
    ) {
        return@withTransaction DeliveryReceiptOutcome.REJECTED
    }

    if (op.status == OutboxStatus.CONFIRMED) {
        // Never replay a receipt onto the current record again. A late callback may belong
        // to an older snapshot, already superseded by a newer remote or local version.
        return@withTransaction DeliveryReceiptOutcome.ALREADY_CONFIRMED
    }
    if (op.status !in setOf(OutboxStatus.PENDING, OutboxStatus.FAILED, OutboxStatus.PROCESSING)) {
        // In particular: a local revocation marked BLOCKED cannot be unblocked by an ACK.
        return@withTransaction DeliveryReceiptOutcome.REJECTED
    }

    val current = deliveryDao().findById(receipt.deliveryId)
        ?: return@withTransaction DeliveryReceiptOutcome.REJECTED
    if (current.farmId != receipt.farmId || current.workspaceId !=
        farmDao().findById(receipt.farmId)?.workspaceId
    ) {
        return@withTransaction DeliveryReceiptOutcome.REJECTED
    }

    val siblings = outbox.listForEntity(SyncEntityType.DELIVERY, receipt.deliveryId)
        .any { it.id != op.id && it.status != OutboxStatus.CONFIRMED }
    val snapshotStillCurrent = current.metadata.deletedAt == null &&
        buildDeliveryPayloadJson(current, op.baseRemoteVersion) == op.payloadJson
    val remoteNotSuperseded = (current.metadata.remoteVersion ?: 0L) <= receipt.remoteVersion

    if (outbox.markConfirmed(op.id, at.toEpochMilli()) != 1) {
        return@withTransaction DeliveryReceiptOutcome.REJECTED
    }

    val newRemoteVersion = maxOf(current.metadata.remoteVersion ?: 0L, receipt.remoteVersion)
    if (siblings || !snapshotStillCurrent || !remoteNotSuperseded) {
        outbox.quarantineSiblings(SyncEntityType.DELIVERY, receipt.deliveryId, op.id, at.toEpochMilli())
        // Never change local field data, tombstones, edit version, or the audit timestamps.
        deliveryDao().upsert(current.copy(metadata = current.metadata.copy(
            remoteVersion = newRemoteVersion,
            syncStatus = SyncStatus.CONFLICT,
            lastSyncedAt = at,
        )))
        return@withTransaction DeliveryReceiptOutcome.NEEDS_REBASE
    }

    deliveryDao().upsert(current.copy(metadata = current.metadata.copy(
        remoteVersion = newRemoteVersion,
        syncStatus = SyncStatus.SYNCED,
        lastSyncedAt = at,
    )))
    DeliveryReceiptOutcome.SYNCED
}
