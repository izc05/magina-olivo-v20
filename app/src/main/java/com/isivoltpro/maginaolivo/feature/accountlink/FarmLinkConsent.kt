package com.isivoltpro.maginaolivo.feature.accountlink

import java.time.Instant
import java.util.UUID

data class FarmChoice(val id: UUID, val workspaceId: UUID, val name: String, val version: Long)

/** Local consent only: never a server grant, legal title or acknowledgement of sync. */
data class FarmLinkConsent(
    val operationId: UUID,
    val subject: String,
    val farms: List<FarmChoice>,
    val backupId: UUID,
    val backupManifestHash: String,
    val acceptedAt: Instant,
    val textVersion: Int = 2,
    val state: String = "PENDING_REMOTE_RESERVATION",
)

/**
 * A durable, local-only audit event. Never grants or removes server permissions.
 * [sequence] is the event's position in the ledger, assigned by the store; never by a clock.
 */
data class FarmLinkRevocation(
    val operationId: UUID,
    val subject: String,
    val farmId: UUID,
    val revokedAt: Instant,
    val sequence: Long = 0,
)

/**
 * Explicit renewed consent for ONE previously revoked farm. It names the revocation it
 * follows, and the store only accepts it while that revocation is the farm's latest event.
 */
data class FarmLinkReconsent(
    val operationId: UUID,
    val subject: String,
    val farmId: UUID,
    val afterRevocation: UUID,
    val acceptedAt: Instant,
    val textVersion: Int = 2,
    val sequence: Long = 0,
)

data class FarmLinkLedger(
    val consents: List<FarmLinkConsent>,
    val revocations: List<FarmLinkRevocation> = emptyList(),
    val reconsents: List<FarmLinkReconsent> = emptyList(),
) {
    val subject: String get() = consents.first().subject
    /** Historical selection, never re-adopted by simply reopening the account. */
    val farmIds: Set<UUID> get() = consents.flatMap { it.farms }.map { it.id }.toSet()
    /** Farms whose LATEST ledger event is a revocation. */
    val revokedFarmIds: Set<UUID> get() = revocations.map { it.farmId }.filter { latestRevocation(it) != null }.toSet()
    val activeFarmIds: Set<UUID> get() = farmIds - revokedFarmIds

    /** The farm's revocation that is still in force, by ledger order; null when none is. */
    fun latestRevocation(farmId: UUID): FarmLinkRevocation? {
        val revocation = revocations.filter { it.farmId == farmId }.maxByOrNull { it.sequence } ?: return null
        val renewed = reconsents.any {
            it.farmId == farmId && it.afterRevocation == revocation.operationId && it.sequence > revocation.sequence
        }
        return revocation.takeUnless { renewed }
    }

    /**
     * The renewed consent that follows [revocationId] in ledger order, while the farm is still
     * consented now. Timestamps are not compared: device clocks do not decide the order.
     */
    fun reconsentAfter(revocationId: UUID): FarmLinkReconsent? {
        val revocation = revocations.singleOrNull { it.operationId == revocationId } ?: return null
        if (revocation.farmId !in activeFarmIds) return null
        return reconsents.singleOrNull {
            it.farmId == revocation.farmId && it.afterRevocation == revocation.operationId &&
                it.sequence > revocation.sequence
        }
    }
}

interface FarmLinkLedgerStore {
    fun read(): FarmLinkLedger?
    fun append(consent: FarmLinkConsent): FarmLinkLedger
    /** Other implementations must fail closed unless they deliberately support durable revocations. */
    fun revoke(revocation: FarmLinkRevocation): FarmLinkLedger =
        throw java.io.IOException("durable_farm_revocation_unavailable")
    /** Other implementations must fail closed unless they deliberately support ordered renewals. */
    fun reconsent(reconsent: FarmLinkReconsent): FarmLinkLedger =
        throw java.io.IOException("durable_farm_reconsent_unavailable")
}
