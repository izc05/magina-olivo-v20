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
    /**
     * The entity type the server reported for this operation. Deliberately has no default: the
     * server's historical receipt carries it, so the adapter must state what it actually read
     * instead of letting an omission pass as a Delivery.
     */
    val entityType: SyncEntityType,
    /**
     * The owner of the data. The server derives the actor from the verified session and does
     * NOT return it, so the adapter fills this from that session — never from response JSON.
     */
    val accountId: String,
    val remoteVersion: Long,
    val payloadHash: String,
    val payloadVersion: Int = 1,
)

internal fun verifiedRecoveredDeliveryReceipt(
    operation: com.isivoltpro.maginaolivo.data.local.entity.SyncOutboxEntity,
    recovered: RecoveredDeliveryReceipt,
    hashMatches: (String, String) -> Boolean = { payload, expected -> deliveryPayloadHash(payload, operation.payloadVersion) == expected },
): DeliverySyncReceipt? {
    // This is a pure binding guard, NOT an authenticated-server-ACK verifier.
    // Callers must establish a trusted server response. The default uses the restricted
    // JSONB-compatible DeliveryWriter hash; injected predicates are for isolated guard tests.
    val payload = operation.payloadJson ?: return null
    val farm = operation.farmId ?: return null
    if (recovered.accountId.isBlank() || recovered.remoteVersion < 1L ||
        payload.isBlank() || operation.payloadVersion !in 1..2 ||
        operation.payloadVersion != recovered.payloadVersion ||
        (operation.baseRemoteVersion != null && operation.baseRemoteVersion < 1L) ||
        (operation.operation == OutboxOperation.CREATE && operation.baseRemoteVersion != null) ||
        (operation.operation == OutboxOperation.UPDATE &&
            (operation.baseRemoteVersion == null || recovered.remoteVersion <= operation.baseRemoteVersion)) ||
        operation.id != recovered.operationId ||
        operation.entityType != SyncEntityType.DELIVERY ||
        // The server's own receipt must say DELIVERY too: a receipt for another entity that
        // happens to share this operation UUID is never adopted as this Pesada's.
        recovered.entityType != SyncEntityType.DELIVERY ||
        operation.operation !in setOf(OutboxOperation.CREATE, OutboxOperation.UPDATE) ||
        operation.entityId != recovered.deliveryId ||
        operation.accountId != recovered.accountId ||
        operation.status !in setOf(OutboxStatus.PENDING, OutboxStatus.FAILED, OutboxStatus.PROCESSING) ||
        !Regex("^[0-9a-f]{64}$").matches(recovered.payloadHash) ||
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
        buildDeliveryPayloadJson(current, op.baseRemoteVersion, deliveryDao().listParcels(current.id)) == op.payloadJson
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
