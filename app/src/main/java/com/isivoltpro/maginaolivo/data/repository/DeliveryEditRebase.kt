package com.isivoltpro.maginaolivo.data.repository

import androidx.room.withTransaction
import com.isivoltpro.maginaolivo.core.id.IdGenerator
import com.isivoltpro.maginaolivo.data.local.MaginaOlivoDatabase
import com.isivoltpro.maginaolivo.data.local.entity.SyncOutboxEntity
import com.isivoltpro.maginaolivo.data.local.model.OutboxOperation
import com.isivoltpro.maginaolivo.data.local.model.OutboxStatus
import com.isivoltpro.maginaolivo.data.local.model.SyncEntityType
import com.isivoltpro.maginaolivo.data.local.model.SyncStatus
import java.time.Instant
import java.util.UUID

internal enum class DeliveryRebaseOutcome {
    REBASED, ALREADY_REBASED, NOT_AUTHORIZED, REMOTE_CONFLICT, REJECTED,
}

/**
 * Local reconciliation after applyConfirmedDeliveryReceipt has durably recorded an ACK.
 * Both receipt and remote head observation MUST originate from authenticated transport;
 * accepting arguments here does not authenticate them. No transport is installed here.
 * A future send must recheck authorization and use server CAS against the new base version.
 * Never accepts a fabricated version, adopts account-less edits, or dispatches DELETE.
 */
internal suspend fun MaginaOlivoDatabase.rebaseConfirmedDeliveryEdit(
    receipt: DeliverySyncReceipt,
    remoteHeadVersion: Long,
    remoteHeadPayloadHash: String,
    isAuthorized: (String, UUID) -> Boolean,
    ids: IdGenerator,
    at: Instant,
): DeliveryRebaseOutcome = withTransaction {
    if (!isAuthorized(receipt.accountId, receipt.farmId)) {
        return@withTransaction DeliveryRebaseOutcome.NOT_AUTHORIZED
    }
    val dao = syncOutboxDao()
    val op = dao.findById(receipt.operationId)
        ?: return@withTransaction DeliveryRebaseOutcome.REJECTED
    if (receipt.accountId.isBlank() || receipt.remoteVersion < 1 ||
        op.status != OutboxStatus.CONFIRMED || op.payloadVersion != 2 ||
        op.entityType != SyncEntityType.DELIVERY || op.entityId != receipt.deliveryId ||
        op.accountId != receipt.accountId || op.farmId != receipt.farmId ||
        op.payloadJson != receipt.payloadJson ||
        op.operation !in setOf(OutboxOperation.CREATE, OutboxOperation.UPDATE) ||
        (op.operation == OutboxOperation.CREATE && op.baseRemoteVersion != null) ||
        (op.operation == OutboxOperation.UPDATE &&
            (op.baseRemoteVersion == null || receipt.remoteVersion <= op.baseRemoteVersion))
    ) return@withTransaction DeliveryRebaseOutcome.REJECTED

    val expectedHash = deliveryPayloadHash(receipt.payloadJson, op.payloadVersion)
    if (remoteHeadVersion != receipt.remoteVersion || expectedHash == null ||
        remoteHeadPayloadHash != expectedHash
    ) return@withTransaction DeliveryRebaseOutcome.REMOTE_CONFLICT

    val current = deliveryDao().findById(receipt.deliveryId)
        ?: return@withTransaction DeliveryRebaseOutcome.REJECTED
    val farm = farmDao().findById(receipt.farmId)
        ?: return@withTransaction DeliveryRebaseOutcome.REJECTED
    if (current.farmId != receipt.farmId || current.workspaceId != farm.workspaceId ||
        farm.metadata.deletedAt != null || current.metadata.deletedAt != null
    ) return@withTransaction DeliveryRebaseOutcome.REJECTED
    if (current.metadata.remoteVersion != receipt.remoteVersion) {
        return@withTransaction DeliveryRebaseOutcome.REMOTE_CONFLICT
    }

    val payload = buildDeliveryPayloadJson(current, receipt.remoteVersion, deliveryDao().listParcels(current.id))
    val unresolved = dao.listForEntity(SyncEntityType.DELIVERY, current.id)
        .filter { it.status != OutboxStatus.CONFIRMED }
    if (unresolved.size == 1 && unresolved.single().let {
            it.accountId == receipt.accountId && it.farmId == receipt.farmId &&
                it.operation == OutboxOperation.UPDATE && it.payloadVersion == 2 &&
                it.baseRemoteVersion == receipt.remoteVersion && it.payloadJson == payload &&
                it.status in setOf(OutboxStatus.PENDING, OutboxStatus.PROCESSING, OutboxStatus.FAILED)
        }
    ) return@withTransaction DeliveryRebaseOutcome.ALREADY_REBASED

    if (unresolved.isEmpty() || unresolved.any {
            it.accountId != receipt.accountId || it.farmId != receipt.farmId ||
                it.status != OutboxStatus.BLOCKED || it.lastErrorCode != "NEEDS_REBASE" ||
                it.attemptCount != 0 || it.payloadVersion != 2 ||
                it.operation !in setOf(OutboxOperation.CREATE, OutboxOperation.UPDATE)
        }
    ) return@withTransaction DeliveryRebaseOutcome.REJECTED
    // Recheck immediately before replacing local-only snapshots. A future sender must
    // also coordinate identity/revocation with dispatch; this callback is not that worker.
    if (!isAuthorized(receipt.accountId, receipt.farmId)) {
        return@withTransaction DeliveryRebaseOutcome.NOT_AUTHORIZED
    }
    check(dao.deleteUnattemptedRebaseSnapshots(current.id, receipt.accountId, receipt.farmId) == unresolved.size)
    dao.insert(SyncOutboxEntity(
        id = ids.newId(), entityType = SyncEntityType.DELIVERY, entityId = current.id,
        operation = OutboxOperation.UPDATE, payloadVersion = 2,
        baseRemoteVersion = receipt.remoteVersion, accountId = receipt.accountId,
        farmId = receipt.farmId, payloadJson = payload, createdAt = at, updatedAt = at,
    ))
    deliveryDao().upsert(current.copy(metadata = current.metadata.copy(syncStatus = SyncStatus.PENDING)))
    DeliveryRebaseOutcome.REBASED
}
