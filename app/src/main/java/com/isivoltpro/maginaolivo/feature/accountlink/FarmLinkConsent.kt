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

/** A durable, local-only audit event. Never grants or removes server permissions. */
data class FarmLinkRevocation(
    val operationId: UUID,
    val subject: String,
    val farmId: UUID,
    val revokedAt: Instant,
)

data class FarmLinkLedger(
    val consents: List<FarmLinkConsent>,
    val revocations: List<FarmLinkRevocation> = emptyList(),
) {
    val subject: String get() = consents.first().subject
    /** Historical selection, never re-adopted by simply reopening the account. */
    val farmIds: Set<UUID> get() = consents.flatMap { it.farms }.map { it.id }.toSet()
    val revokedFarmIds: Set<UUID> get() = revocations.map { it.farmId }.toSet()
    val activeFarmIds: Set<UUID> get() = farmIds - revokedFarmIds
}

interface FarmLinkLedgerStore {
    fun read(): FarmLinkLedger?
    fun append(consent: FarmLinkConsent): FarmLinkLedger
    /** Other implementations must fail closed unless they deliberately support durable revocations. */
    fun revoke(revocation: FarmLinkRevocation): FarmLinkLedger =
        throw java.io.IOException("durable_farm_revocation_unavailable")
}
