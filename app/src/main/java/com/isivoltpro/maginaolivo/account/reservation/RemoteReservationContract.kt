package com.isivoltpro.maginaolivo.account.reservation

import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.UUID

/** Local correlation only. A future server must derive actor identity from verified credentials. */
data class ReservationActor(val issuer: String, val userId: UUID) {
    init {
        require(issuer.isNotBlank() && issuer.length <= MAX_ISSUER_CHARS &&
            issuer.toByteArray(Charsets.UTF_8).size <= MAX_ISSUER_BYTES)
    }

    companion object {
        const val MAX_ISSUER_CHARS = 2048
        const val MAX_ISSUER_BYTES = 8192
    }
}

data class ReservationScope(val actor: ReservationActor, val operationId: UUID)

/** Exactly one selected farm; local workspace is a reference, never proof of remote permission. */
data class ReservationRequest(
    val operationId: UUID,
    val farmId: UUID,
    val localWorkspaceId: UUID,
    val consentId: UUID,
    val consentTextVersion: Int,
    val manifestSha256: String,
    val aggregateVersion: Long,
) {
    val payloadVersion: Int get() = 1

    init {
        require(Regex("[0-9a-f]{64}").matches(manifestSha256))
        require(aggregateVersion >= 1)
        require(consentTextVersion >= 1)
    }

    /** Version 1: ordered UTF-8 fields, each preceded by a 4-byte big-endian byte length. */
    fun payloadHash(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        listOf("magina-reservation", payloadVersion.toString(), operationId.toString(), farmId.toString(),
            localWorkspaceId.toString(), consentId.toString(), consentTextVersion.toString(), manifestSha256,
            aggregateVersion.toString()).forEach { field ->
            val bytes = field.toByteArray(Charsets.UTF_8)
            digest.update(ByteBuffer.allocate(4).putInt(bytes.size).array())
            digest.update(bytes)
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}

/** Opaque reservation receipt. This neither imports data nor grants agricultural access. */
data class ReservationReceipt(
    val scope: ReservationScope,
    val farmId: UUID,
    val payloadHash: String,
    val remoteWorkspaceId: UUID,
    val receiptId: UUID,
)

sealed interface ReservationResponse {
    data class Accepted(val receipt: ReservationReceipt) : ReservationResponse
    /** Includes occupied foreign/legacy UUIDs, altered replay and denied effective permission. No metadata. */
    data object Conflict : ReservationResponse
    /** Unknown commit outcome, expired credentials or temporary transport failure: retain the request. */
    data object RetrySameOperation : ReservationResponse
}

/**
 * No implementation is wired to the app. A future adapter must use fresh verified credentials,
 * atomically enforce immutable unique(actor, operation) receipts and unique farm reservations,
 * and return the same receipt on exact replay. It must never claim legacy farms or infer ownership
 * from names, email or Catastro. Actor correlation here is not a credential/body authority.
 */
interface ReservationTransport {
    fun reserve(scope: ReservationScope, request: ReservationRequest): ReservationResponse
}

enum class ReservationStatus { PENDING, AWAITING_ACTOR, RESERVED_REMOTE, CONFLICT, INVALID_RECEIPT }

/**
 * Pure recovery snapshot. The caller must durably persist the original scope/request BEFORE sending,
 * then atomically persist the returned snapshot. No journal, disk, G1b ledger or access grant is changed.
 * Reconstructing the same pending snapshot after an uncertain acknowledgement retries the same operation.
 */
data class ReservationRecovery(
    val scope: ReservationScope,
    val request: ReservationRequest,
    val status: ReservationStatus = ReservationStatus.PENDING,
    val receipt: ReservationReceipt? = null,
) {
    init {
        require(scope.operationId == request.operationId)
        require(receipt == null || (receipt.scope == scope && receipt.farmId == request.farmId &&
            receipt.payloadHash == request.payloadHash()))
        require(status !in setOf(ReservationStatus.RESERVED_REMOTE, ReservationStatus.AWAITING_ACTOR) || receipt != null)
    }

    fun attempt(currentActor: () -> ReservationActor?, transport: ReservationTransport): ReservationRecovery {
        val visibleActor = currentActor()
        if (visibleActor != scope.actor || status in terminalStates) return this
        receipt?.let { return accept(it, visibleActor) }
        return when (val result = transport.reserve(scope, request)) {
            is ReservationResponse.Accepted -> accept(result.receipt, currentActor())
            ReservationResponse.Conflict -> copy(status = ReservationStatus.CONFLICT)
            ReservationResponse.RetrySameOperation -> this
        }
    }

    /** Apply an asynchronous acknowledgement against the CURRENT visible actor, never a captured one. */
    fun accept(incoming: ReservationReceipt, visibleActor: ReservationActor?): ReservationRecovery {
        if (status == ReservationStatus.CONFLICT || status == ReservationStatus.INVALID_RECEIPT) return this
        if (incoming.scope != scope || incoming.farmId != request.farmId ||
            incoming.payloadHash != request.payloadHash() || (receipt != null && receipt != incoming)) {
            return copy(status = ReservationStatus.INVALID_RECEIPT)
        }
        return copy(status = if (visibleActor == scope.actor) ReservationStatus.RESERVED_REMOTE
            else ReservationStatus.AWAITING_ACTOR, receipt = incoming)
    }

    private companion object {
        val terminalStates = setOf(ReservationStatus.RESERVED_REMOTE, ReservationStatus.CONFLICT, ReservationStatus.INVALID_RECEIPT)
    }
}
