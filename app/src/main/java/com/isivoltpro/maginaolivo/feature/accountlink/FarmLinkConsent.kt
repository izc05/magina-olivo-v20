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

data class FarmLinkLedger(val consents: List<FarmLinkConsent>) {
    val subject: String get() = consents.first().subject
    val farmIds: Set<UUID> get() = consents.flatMap { it.farms }.map { it.id }.toSet()
}

interface FarmLinkLedgerStore {
    fun read(): FarmLinkLedger?
    fun append(consent: FarmLinkConsent): FarmLinkLedger
}
