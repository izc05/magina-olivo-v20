package com.isivoltpro.maginaolivo.data.repository

import androidx.room.withTransaction
import com.isivoltpro.maginaolivo.data.local.MaginaOlivoDatabase
import com.isivoltpro.maginaolivo.data.local.entity.DeliveryDispatchPrepareEntity
import com.isivoltpro.maginaolivo.data.local.model.OutboxOperation
import com.isivoltpro.maginaolivo.data.local.model.OutboxStatus
import com.isivoltpro.maginaolivo.data.local.model.SyncEntityType
import com.isivoltpro.maginaolivo.data.repository.sync.DeliveryDispatchLease
import com.isivoltpro.maginaolivo.feature.accountlink.FarmLinkLedger
import java.time.Instant
import java.util.UUID

/**
 * The server-verified session as the sender knows it. Building this value does not
 * authenticate it: only a transport that actually validated the session may construct one.
 * G2.4 stays mono-titular, so [accountId] is both the owner of the data and its author;
 * delegated roles are out of scope until `farm_grants` exists (G2.5).
 */
internal data class DispatchIdentity(
    val accountId: String,
    val issuer: String,
    val subject: String,
    val localWorkspaceId: UUID,
    val remoteWorkspaceId: UUID? = null,
)

internal enum class PrepareOutcome {
    PREPARED, ALREADY_PREPARED, PREPARE_CONFLICT, NOT_AUTHORIZED, REJECTED,
}

/**
 * Parity with the file journal's strict UTF-8 handling: it encoded text with
 * CodingErrorAction.REPORT, so an unpaired surrogate failed loudly instead of being stored.
 *
 * Measured, not assumed: SQLite does NOT corrupt such text — a probe in DeliveryContractTest
 * round-trips U+D800 unchanged, so there is no silent loss to prevent here. The guard exists
 * so the canonical store refuses exactly what the journal refused, and so malformed identity
 * evidence cannot reach a durable row and travel on to a server that may be stricter. Also
 * caps text at the journal's 8 KiB per-field limit rather than storing something unbounded.
 */
private fun wellFormedEvidenceText(value: String): Boolean {
    if (value.isEmpty()) return false
    var index = 0
    var bytes = 0
    while (index < value.length) {
        val character = value[index]
        when {
            character.isHighSurrogate() -> {
                if (index + 1 >= value.length || !value[index + 1].isLowSurrogate()) return false
                bytes += 4
                index += 2
            }
            character.isLowSurrogate() -> return false
            else -> {
                bytes += when {
                    character.code < 0x80 -> 1
                    character.code < 0x800 -> 2
                    else -> 3
                }
                index++
            }
        }
        if (bytes > 8192) return false
    }
    return true
}

internal enum class AckCorrelationOutcome {
    NOT_PREPARED, PREPARE_MISMATCH, ALREADY_SETTLED,
    APPLIED_SYNCED, APPLIED_NEEDS_REBASE,
    HELD_AFTER_REVOCATION, RECEIPT_CONFLICT, REJECTED,
}

/**
 * G2.4: writes the durable PREPARE record and claims the operation, in ONE transaction,
 * BEFORE any request may leave the device.
 *
 * Nothing here sends anything. Its purpose is that a later ACK can be correlated against
 * what this device actually committed to send: authenticated subject and issuer, account,
 * local/remote workspace, the consent event and its id, farm, intent UUID, Pesada, immutable
 * payload version and hash, and baseVersion. A transport that skips this step cannot have a
 * receipt applied, because [correlateAuthenticatedDeliveryAck] refuses an uncorrelated ACK.
 *
 * Re-preparing the same intent with the same bytes is idempotent; with different bytes it is
 * a conflict and the original record is never replaced.
 */
internal suspend fun MaginaOlivoDatabase.prepareDeliveryDispatch(
    lease: DeliveryDispatchLease,
    operationId: UUID,
    identity: DispatchIdentity,
    ledger: FarmLinkLedger?,
    at: Instant,
): PrepareOutcome = withTransaction {
    lease.requireOpen()
    val marker = lease.claimMarker()
    val outbox = syncOutboxDao()
    val prepareDao = deliveryDispatchPrepareDao()

    if (identity.accountId.isBlank() || identity.issuer.isBlank() || identity.subject.isBlank() ||
        !wellFormedEvidenceText(identity.accountId) || !wellFormedEvidenceText(identity.issuer) ||
        !wellFormedEvidenceText(identity.subject)
    ) {
        return@withTransaction PrepareOutcome.REJECTED
    }
    val op = outbox.findById(operationId) ?: return@withTransaction PrepareOutcome.REJECTED
    val payload = op.payloadJson ?: return@withTransaction PrepareOutcome.REJECTED
    val farm = op.farmId ?: return@withTransaction PrepareOutcome.REJECTED
    // The dispatchable statuses are enforced by claimPreparedDeliveryForDispatch below, so a
    // revoked (BLOCKED) operation still reaches the consent check and is reported as what it
    // really is — not authorized — instead of a generic rejection.
    if (op.entityType != SyncEntityType.DELIVERY ||
        op.operation !in setOf(OutboxOperation.CREATE, OutboxOperation.UPDATE) ||
        op.accountId == null || op.accountId != identity.accountId ||
        payload.isBlank() || op.payloadVersion !in 1..2 ||
        (op.operation == OutboxOperation.CREATE && op.baseRemoteVersion != null) ||
        (op.operation == OutboxOperation.UPDATE &&
            (op.baseRemoteVersion == null || op.baseRemoteVersion < 1L))
    ) {
        return@withTransaction PrepareOutcome.REJECTED
    }
    val hash = deliveryPayloadHash(payload, op.payloadVersion)
        ?: return@withTransaction PrepareOutcome.REJECTED

    // The Pesada must still exist, belong to that farm, and live in the declared workspace.
    val delivery = deliveryDao().findById(op.entityId) ?: return@withTransaction PrepareOutcome.REJECTED
    val farmRow = farmDao().findById(farm) ?: return@withTransaction PrepareOutcome.REJECTED
    if (delivery.farmId != farm || delivery.metadata.deletedAt != null ||
        delivery.workspaceId != farmRow.workspaceId ||
        farmRow.workspaceId != identity.localWorkspaceId
    ) {
        return@withTransaction PrepareOutcome.REJECTED
    }

    // Consent, re-checked here and named in the record: a revoked farm is never prepared.
    if (ledger == null || ledger.subject != identity.subject ||
        farm !in ledger.activeFarmIds || ledger.latestRevocation(farm) != null
    ) {
        return@withTransaction PrepareOutcome.NOT_AUTHORIZED
    }
    val consent = ledger.consents.lastOrNull { consent -> consent.farms.any { it.id == farm } }
        ?: return@withTransaction PrepareOutcome.NOT_AUTHORIZED
    // The workspace recorded in the PREPARE must be the one the farmer consented for.
    val consentedFarm = consent.farms.firstOrNull { it.id == farm }
        ?: return@withTransaction PrepareOutcome.NOT_AUTHORIZED
    if (consentedFarm.workspaceId != identity.localWorkspaceId) {
        return@withTransaction PrepareOutcome.NOT_AUTHORIZED
    }

    // A frozen Delivery is never prepared again until its held receipt is reconciled.
    if (deliveryHeldReceiptDao().countUnreconciled(op.entityId) != 0) {
        return@withTransaction PrepareOutcome.REJECTED
    }

    val record = DeliveryDispatchPrepareEntity(
        operationId = op.id,
        deliveryId = op.entityId,
        farmId = farm,
        accountId = identity.accountId,
        issuer = identity.issuer,
        subject = identity.subject,
        localWorkspaceId = identity.localWorkspaceId,
        remoteWorkspaceId = identity.remoteWorkspaceId,
        consentOperationId = consent.operationId,
        consentTextVersion = consent.textVersion,
        payloadVersion = op.payloadVersion,
        payloadHash = hash,
        baseRemoteVersion = op.baseRemoteVersion,
        leaseMarker = marker,
        preparedAt = at,
    )

    prepareDao.findByOperation(op.id)?.let { existing ->
        val same = existing.deliveryId == record.deliveryId && existing.farmId == record.farmId &&
            existing.accountId == record.accountId && existing.issuer == record.issuer &&
            existing.subject == record.subject &&
            existing.localWorkspaceId == record.localWorkspaceId &&
            existing.consentOperationId == record.consentOperationId &&
            existing.payloadVersion == record.payloadVersion &&
            existing.payloadHash == record.payloadHash &&
            existing.baseRemoteVersion == record.baseRemoteVersion
        if (!same) return@withTransaction PrepareOutcome.PREPARE_CONFLICT
        if (existing.settledAt != null) return@withTransaction PrepareOutcome.PREPARE_CONFLICT
        // Already claimed under THIS lease: idempotent, and the claim is not touched again.
        if (op.status == OutboxStatus.PROCESSING && op.lastErrorCode == marker) {
            lease.requireHeld()
            return@withTransaction PrepareOutcome.ALREADY_PREPARED
        }
        if (outbox.claimPreparedDeliveryForDispatch(op.id, identity.accountId, farm, at.toEpochMilli()) != 1) {
            return@withTransaction PrepareOutcome.REJECTED
        }
        check(outbox.markDeliveryLeaseClaim(op.id, identity.accountId, farm, marker) == 1)
        lease.requireHeld()
        return@withTransaction PrepareOutcome.ALREADY_PREPARED
    }

    if (outbox.claimPreparedDeliveryForDispatch(op.id, identity.accountId, farm, at.toEpochMilli()) != 1) {
        return@withTransaction PrepareOutcome.REJECTED
    }
    prepareDao.insert(record)
    check(outbox.markDeliveryLeaseClaim(op.id, identity.accountId, farm, marker) == 1)
    lease.requireHeld()
    PrepareOutcome.PREPARED
}

/**
 * G2.4: the only way a server ACK becomes local truth.
 *
 * The receipt is matched against the durable PREPARE record of the same intent UUID. An ACK
 * for an operation this device never prepared is [AckCorrelationOutcome.NOT_PREPARED] and
 * changes nothing — that is what makes the wiring demonstrable rather than assumed. When the
 * farm's consent was revoked meanwhile, the ACK is retained as evidence and the operation
 * stays BLOCKED; otherwise the normal receipt path applies it. Kilos and field data are never
 * written from here.
 */
internal suspend fun MaginaOlivoDatabase.correlateAuthenticatedDeliveryAck(
    ack: AuthenticatedDeliveryAck,
    identity: DispatchIdentity,
    ledger: FarmLinkLedger?,
    at: Instant,
): AckCorrelationOutcome = withTransaction {
    val prepareDao = deliveryDispatchPrepareDao()
    val prepared = prepareDao.findByOperation(ack.operationId)
        ?: return@withTransaction AckCorrelationOutcome.NOT_PREPARED
    if (prepared.settledAt != null) return@withTransaction AckCorrelationOutcome.ALREADY_SETTLED

    // Mono-titular rule preserved: the authenticated sender is the account owner.
    if (ack.transportSubject.isBlank() || ack.transportSubject != ack.accountId ||
        identity.accountId != prepared.accountId || identity.issuer != prepared.issuer ||
        identity.subject != prepared.subject ||
        identity.localWorkspaceId != prepared.localWorkspaceId ||
        ack.accountId != prepared.accountId || ack.deliveryId != prepared.deliveryId ||
        ack.farmId != prepared.farmId || ack.payloadVersion != prepared.payloadVersion ||
        ack.payloadHash != prepared.payloadHash || ack.remoteVersion < 1L ||
        (prepared.baseRemoteVersion != null && ack.remoteVersion <= prepared.baseRemoteVersion)
    ) {
        return@withTransaction AckCorrelationOutcome.PREPARE_MISMATCH
    }

    val millis = at.toEpochMilli()
    val revocation = ledger?.latestRevocation(prepared.farmId)
    if (revocation != null) {
        // Consent was withdrawn while the request was in flight: keep the ACK as evidence.
        return@withTransaction when (retainAckAfterRevocation(ack, revocation.operationId, at)) {
            HeldReceiptOutcome.HELD, HeldReceiptOutcome.ALREADY_HELD -> {
                prepareDao.settle(ack.operationId, DeliveryDispatchPrepareEntity.SETTLED_HELD, millis)
                AckCorrelationOutcome.HELD_AFTER_REVOCATION
            }
            HeldReceiptOutcome.RECEIPT_CONFLICT -> {
                prepareDao.settle(ack.operationId, DeliveryDispatchPrepareEntity.SETTLED_CONFLICT, millis)
                AckCorrelationOutcome.RECEIPT_CONFLICT
            }
            HeldReceiptOutcome.REJECTED -> AckCorrelationOutcome.REJECTED
        }
    }

    if (ledger == null || ledger.subject != prepared.subject ||
        prepared.farmId !in ledger.activeFarmIds
    ) {
        return@withTransaction AckCorrelationOutcome.REJECTED
    }

    val op = syncOutboxDao().findById(ack.operationId)
        ?: return@withTransaction AckCorrelationOutcome.REJECTED
    val payload = op.payloadJson ?: return@withTransaction AckCorrelationOutcome.REJECTED
    if (deliveryPayloadHash(payload, op.payloadVersion) != prepared.payloadHash) {
        // The prepared bytes are not the bytes now on disk: never confirm the difference.
        return@withTransaction AckCorrelationOutcome.PREPARE_MISMATCH
    }
    val receipt = DeliverySyncReceipt(
        operationId = prepared.operationId,
        deliveryId = prepared.deliveryId,
        farmId = prepared.farmId,
        accountId = prepared.accountId,
        payloadJson = payload,
        remoteVersion = ack.remoteVersion,
    )
    when (applyConfirmedDeliveryReceipt(receipt, at)) {
        DeliveryReceiptOutcome.SYNCED -> {
            check(prepareDao.settle(ack.operationId, DeliveryDispatchPrepareEntity.SETTLED_CONFIRMED, millis) == 1)
            AckCorrelationOutcome.APPLIED_SYNCED
        }
        DeliveryReceiptOutcome.NEEDS_REBASE -> {
            check(prepareDao.settle(ack.operationId, DeliveryDispatchPrepareEntity.SETTLED_CONFIRMED, millis) == 1)
            AckCorrelationOutcome.APPLIED_NEEDS_REBASE
        }
        DeliveryReceiptOutcome.ALREADY_CONFIRMED -> AckCorrelationOutcome.ALREADY_SETTLED
        DeliveryReceiptOutcome.REJECTED -> AckCorrelationOutcome.REJECTED
    }
}

/**
 * Reacquiring the OS lock proves no conforming sender still owns these intents, so their
 * outcome is unknown rather than failed. The PREPARE record is kept and annotated; the SAME
 * UUID, bytes and base version stay eligible so the server's idempotency decides.
 */
internal suspend fun MaginaOlivoDatabase.settleAbandonedPreparedDispatches(
    lease: DeliveryDispatchLease,
    accountId: String,
    farmId: UUID,
    at: Instant,
): Int = withTransaction {
    lease.requireOpen()
    if (accountId.isBlank()) return@withTransaction 0
    val prepareDao = deliveryDispatchPrepareDao()
    var settled = 0
    for (row in prepareDao.listUnsettled(accountId, farmId)) {
        if (!lease.canRecover(row.leaseMarker)) continue
        if (prepareDao.settle(row.operationId, DeliveryDispatchPrepareEntity.SETTLED_UNKNOWN, at.toEpochMilli()) == 1) {
            settled++
        }
    }
    lease.requireHeld()
    settled
}
