package com.isivoltpro.maginaolivo.data.repository.sync

import com.isivoltpro.maginaolivo.data.local.MaginaOlivoDatabase
import com.isivoltpro.maginaolivo.data.local.model.SyncEntityType
import com.isivoltpro.maginaolivo.data.repository.AckCorrelationOutcome
import com.isivoltpro.maginaolivo.data.repository.AuthenticatedDeliveryAck
import com.isivoltpro.maginaolivo.data.repository.LocalFarmLinkRepository
import com.isivoltpro.maginaolivo.data.repository.PrepareOutcome
import com.isivoltpro.maginaolivo.feature.account.AuthFailure
import java.time.Instant
import java.util.UUID

/**
 * G2.4 sender. It adds no new rule: it only orders the pieces that already exist, so that the
 * durable record always precedes the request and the request can never outlive the ownership
 * that authorized it.
 *
 * The order, and why:
 *  1. **PREPARE before HTTP.** `prepareDeliveryForDispatch` writes the operation, the Pesada,
 *     the farm, the account, the issuer, the subject, the consent and the exact payload hash,
 *     and claims the operation, all inside one Room transaction. Nothing is sent before it
 *     returns PREPARED or ALREADY_PREPARED, because an ACK without a PREPARE record is refused.
 *  2. **One `withActiveRequest` around everything that belongs to the request**: the HTTP call,
 *     its cancellation, the confirmation read and the persistence of the ACK. The lease cannot
 *     release while any of that is still running, so no later epoch can recover an operation
 *     whose request is still in flight.
 *  3. **The authorized hash comes from the server.** After the write, the receipt is read with
 *     `check_sync_operations` and the ACK is built from THAT, so the hash compared against the
 *     PREPARE record is the server's, not a local recomputation of what we just sent.
 *  4. **An unknown outcome stays unknown.** A cut connection, a timeout or a non-contract answer
 *     never becomes a failed dispatch: the PREPARE stays unsettled, the UUID and the bytes are
 *     untouched, and [recoverLostResponse] asks the server what actually happened.
 *
 * Identity is not rechecked here by hand: every entry point of [LocalFarmLinkRepository] used
 * below goes through `withServerVerifiedIdentity`, which refuses a missing or expired session,
 * verifies the account against the server within the same identity generation, and refuses to
 * commit if the account changed while the request was in flight. Those three cases surface as
 * [DispatchOutcome.NOT_AUTHORIZED] with nothing written.
 *
 * This class issues no HTTP by itself: it talks to a [DeliveryRpcAdapter], whose transport is
 * injected. Production wiring of the real HTTPS transport is a separate authorized step.
 */
internal enum class DispatchOutcome {
    /** The server's receipt was correlated and applied. */
    SYNCED,

    /** Applied, but the local snapshot moved on: the operation is quarantined for rebase. */
    NEEDS_REBASE,

    /** The receipt exists and is kept as evidence; consent was revoked. The Pesada is untouched. */
    HELD_AFTER_REVOCATION,

    /** The server's record and the local intent disagree. Nothing is marked as synchronized. */
    CONFLICT,

    /** No session, an expired token, a changed account, or no consent for that farm. */
    NOT_AUTHORIZED,

    /** The operation is not dispatchable: wrong type, wrong state, missing payload or farm. */
    REJECTED,

    /**
     * The request's outcome is genuinely unknown. The durable PREPARE stays unsettled, the same
     * UUID and bytes remain, and [DeliveryDispatchWorker.recoverLostResponse] resolves it later.
     * This is NOT a failure.
     */
    OUTCOME_UNKNOWN,
}

internal class DeliveryDispatchWorker(
    private val database: MaginaOlivoDatabase,
    private val links: LocalFarmLinkRepository,
    private val rpc: DeliveryRpcAdapter,
    private val now: () -> Instant = Instant::now,
) {

    /**
     * Sends one prepared Pesada. [lease] must already be held by this process and stay open for
     * the whole call; this method never acquires or releases it, because ownership belongs to
     * whoever decided to dispatch.
     */
    suspend fun dispatch(
        operationId: UUID,
        lease: DeliveryDispatchLease,
        remoteWorkspaceId: UUID? = null,
    ): DispatchOutcome {
        lease.requireOpen()

        // 1. Durable record first. Anything other than these two outcomes means we do not send.
        val prepared = try {
            links.prepareDeliveryForDispatch(operationId, lease, remoteWorkspaceId, now())
        } catch (failure: AuthFailure) {
            return notAuthorized(failure)
        }
        when (prepared) {
            PrepareOutcome.PREPARED, PrepareOutcome.ALREADY_PREPARED -> Unit
            PrepareOutcome.NOT_AUTHORIZED -> return DispatchOutcome.NOT_AUTHORIZED
            PrepareOutcome.PREPARE_CONFLICT -> return DispatchOutcome.CONFLICT
            PrepareOutcome.REJECTED -> return DispatchOutcome.REJECTED
        }

        val operation = database.syncOutboxDao().findById(operationId)
            ?: return DispatchOutcome.REJECTED
        val payload = operation.payloadJson ?: return DispatchOutcome.REJECTED
        if (operation.entityType != SyncEntityType.DELIVERY) return DispatchOutcome.REJECTED
        val deliveryId = operation.entityId

        // 2. The request, its confirmation and the persistence of its result are ONE unit of
        //    ownership. The lease cannot be released while any part of this is running.
        return lease.withActiveRequest {
            sendAndCorrelate(operationId, deliveryId, payload)
        }
    }

    /**
     * Resolves an operation whose response was lost, by the SAME operation UUID. It never creates
     * another operation and never edits the payload. Called after a restart, or after
     * [DispatchOutcome.OUTCOME_UNKNOWN].
     */
    suspend fun recoverLostResponse(operationId: UUID, lease: DeliveryDispatchLease): DispatchOutcome {
        lease.requireOpen()
        val operation = database.syncOutboxDao().findById(operationId) ?: return DispatchOutcome.REJECTED
        if (operation.entityType != SyncEntityType.DELIVERY) return DispatchOutcome.REJECTED
        return lease.withActiveRequest {
            sendAndCorrelate(operationId, operation.entityId, payload = null)
        }
    }

    /**
     * Reads the server's historical receipt for this operation and lets G2.4 correlate it. The
     * receipt's own `entity_type`, version and hash are checked against the PREPARE record and
     * the exact persisted bytes inside `recordAuthenticatedDeliveryAck`; a revocation in the
     * meantime retains it as evidence instead of applying it.
     *
     * No receipt is not a failed dispatch: the head is consulted, and any mismatch or a null head
     * hash is a conflict, never a confirmed synchronization.
     */
    private suspend fun sendAndCorrelate(
        operationId: UUID,
        deliveryId: UUID,
        payload: String?,
    ): DispatchOutcome {
        var sawReceipt = false
        var wrongEntity = false
        var transportFailure: DeliveryRpcFailure? = null
        val farmId = database.syncOutboxDao().findById(operationId)?.farmId

        val outcome = try {
            links.recordAuthenticatedDeliveryAck({ session ->
                // The write, when there is one, happens INSIDE the verified-identity window and
                // inside the active request. `withServerVerifiedIdentity` has already refused a
                // missing or expired session and checked the account against the server, and it
                // refuses to commit below if the account changed while this was in flight.
                if (payload != null) {
                    try {
                        val written = rpc.syncOwnDelivery(session, operationId, payload)
                        if (written.entityId != deliveryId) {
                            wrongEntity = true
                            return@recordAuthenticatedDeliveryAck null
                        }
                    } catch (failure: DeliveryRpcFailure) {
                        // A duplicate means the server already holds it: fall through and read
                        // what it recorded. Anything else stops here with nothing written.
                        if (failure.error != DeliveryRpcError.CONFLICT) {
                            transportFailure = failure
                            return@recordAuthenticatedDeliveryAck null
                        }
                    }
                }

                val receipt = try {
                    rpc.checkSyncOperations(session, listOf(operationId))[operationId]
                } catch (failure: DeliveryRpcFailure) {
                    transportFailure = failure
                    return@recordAuthenticatedDeliveryAck null
                }
                if (receipt == null || farmId == null) {
                    null
                } else {
                    sawReceipt = true
                    AuthenticatedDeliveryAck(
                        operationId = receipt.operationId,
                        deliveryId = receipt.deliveryId,
                        farmId = farmId,
                        accountId = receipt.accountId,
                        transportSubject = receipt.accountId,
                        remoteVersion = receipt.remoteVersion,
                        payloadHash = receipt.payloadHash,
                        payloadVersion = receipt.payloadVersion,
                    )
                }
            }, now())
        } catch (failure: AuthFailure) {
            return notAuthorized(failure)
        }

        if (wrongEntity) return DispatchOutcome.CONFLICT
        transportFailure?.let { failure ->
            return when {
                failure.outcomeUnknown -> DispatchOutcome.OUTCOME_UNKNOWN
                failure.error == DeliveryRpcError.FORBIDDEN -> DispatchOutcome.NOT_AUTHORIZED
                failure.error == DeliveryRpcError.INVALID_PAYLOAD -> DispatchOutcome.REJECTED
                else -> DispatchOutcome.OUTCOME_UNKNOWN
            }
        }
        if (!sawReceipt) {
            // Absence of a receipt proves nothing about the request: the operation stays
            // unsettled under the live lease, with the same UUID and the same bytes.
            return DispatchOutcome.OUTCOME_UNKNOWN
        }
        return when (outcome) {
            AckCorrelationOutcome.APPLIED_SYNCED -> DispatchOutcome.SYNCED
            AckCorrelationOutcome.APPLIED_NEEDS_REBASE -> DispatchOutcome.NEEDS_REBASE
            AckCorrelationOutcome.HELD_AFTER_REVOCATION -> DispatchOutcome.HELD_AFTER_REVOCATION
            AckCorrelationOutcome.ALREADY_SETTLED -> DispatchOutcome.SYNCED
            AckCorrelationOutcome.RECEIPT_CONFLICT,
            AckCorrelationOutcome.PREPARE_MISMATCH,
            -> DispatchOutcome.CONFLICT
            AckCorrelationOutcome.NOT_PREPARED,
            AckCorrelationOutcome.REJECTED,
            -> DispatchOutcome.REJECTED
        }
    }

    /**
     * Settles the intents left behind by a process that died owning them, then reports how many.
     * Acquiring the lock is the proof that no conforming sender still owns them.
     */
    suspend fun settleInterrupted(farmId: UUID, lease: DeliveryDispatchLease): Int {
        lease.requireOpen()
        return links.settleInterruptedDeliveryDispatches(farmId, lease, now())
    }

    private fun notAuthorized(failure: AuthFailure): DispatchOutcome = when (failure.kind) {
        AuthFailure.Kind.CREDENTIALS, AuthFailure.Kind.EXPIRED, AuthFailure.Kind.CONFIGURATION ->
            DispatchOutcome.NOT_AUTHORIZED
        // A transport-level auth problem leaves the outcome of the request unknown.
        AuthFailure.Kind.NETWORK, AuthFailure.Kind.PROTOCOL -> DispatchOutcome.OUTCOME_UNKNOWN
        else -> DispatchOutcome.NOT_AUTHORIZED
    }
}
