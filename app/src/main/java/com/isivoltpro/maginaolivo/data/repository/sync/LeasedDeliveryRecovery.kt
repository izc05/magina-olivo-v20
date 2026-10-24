package com.isivoltpro.maginaolivo.data.repository.sync

import androidx.room.withTransaction
import com.isivoltpro.maginaolivo.data.local.MaginaOlivoDatabase
import java.time.Instant
import java.util.UUID

/**
 * Future dispatcher entry point, not upload authorization. Caller holds this lease until
 * HTTP has completed or cancellation has actually settled, including receipt persistence.
 * All participants use one fixed private no-backup directory; never unlink its lock file.
 */
internal suspend fun MaginaOlivoDatabase.claimDeliveryRecoveryWithLease(
    lease: DeliveryDispatchLease,
    operationId: UUID,
    accountId: String,
    farmId: UUID,
    isAuthorized: (String, UUID) -> Boolean,
    at: Instant,
): Boolean = withTransaction {
    val marker = lease.claimMarker()
    if (accountId.isBlank() || !isAuthorized(accountId, farmId)) return@withTransaction false
    val dao = syncOutboxDao()
    if (dao.claimFailedDeliveryForRecovery(operationId, accountId, farmId, at.toEpochMilli()) != 1) {
        return@withTransaction false
    }
    check(dao.markDeliveryLeaseClaim(operationId, accountId, farmId, marker) == 1)
    lease.requireHeld()
    true
}

/**
 * Reacquiring the OS lock proves that no conforming former sender still holds it.
 * Only claims marked by this protocol and namespace can become OUTCOME_UNKNOWN.
 * This does not assert the server rejected the operation: recover its authenticated
 * receipt or retry the SAME operation UUID/payload under fresh authorization.
 * Legacy/unmarked PROCESSING rows stay blocked for explicit reconciliation.
 */
internal suspend fun MaginaOlivoDatabase.recoverAbandonedDeliveryClaim(
    lease: DeliveryDispatchLease,
    operationId: UUID,
    accountId: String,
    farmId: UUID,
    isAuthorized: (String, UUID) -> Boolean,
    at: Instant,
): Boolean = withTransaction {
    val marker = lease.claimMarker()
    if (accountId.isBlank() || !isAuthorized(accountId, farmId)) return@withTransaction false
    val dao = syncOutboxDao()
    val previous = dao.findById(operationId)?.lastErrorCode
    if (!lease.canRecover(previous)) return@withTransaction false
    val changed = dao.recoverDeliveryLeaseClaim(
        operationId, accountId, farmId, previous!!, marker, lease.recoveryNamespace(), at.toEpochMilli(),
    )
    lease.requireHeld()
    changed == 1
}
