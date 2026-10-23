package com.isivoltpro.maginaolivo.account.bootstrap

import com.isivoltpro.maginaolivo.account.reservation.ReservationActor
import java.util.UUID
import java.util.Collections

enum class BootstrapEntity { WORKSPACE, FARM, PARCEL, CAMPAIGN }

/** Prepared immutable request, not a permission to send. Hash uses the server's JSONB contract. */
data class BootstrapStep(
    val operationId: UUID,
    val entityId: UUID,
    val entity: BootstrapEntity,
    val payloadJson: String,
    val payloadHash: String,
    val payloadVersion: Int,
    val dependencies: Set<UUID>,
    /** Includes EVERY farm referenced by parcel membership history. */
    val requiredFarmIds: Set<UUID>,
    val requiredParcelIds: Set<UUID> = emptySet(),
)

data class BootstrapReceipt(
    val actor: ReservationActor,
    val operationId: UUID,
    val entityId: UUID,
    val entity: BootstrapEntity,
    val payloadHash: String,
    val payloadVersion: Int,
    val remoteVersion: Long,
)

/**
 * Local preparation only. No HTTP, account inference or mutation of Room sync state.
 * A future durable adapter must persist the complete plan before exposing any ready step.
 * Request codecs, reservation proof, fresh Auth and current consent remain sender prerequisites.
 */
class BootstrapPlan(
    val actor: ReservationActor,
    val remoteWorkspaceId: UUID,
    val consentId: UUID,
    consentedFarmIds: Set<UUID>,
    steps: List<BootstrapStep>,
) {
    // Defensive copies prevent callers changing a prepared request's dependency/consent scope.
    val consentedFarmIds: Set<UUID> = Collections.unmodifiableSet(consentedFarmIds.toSet())
    val steps: List<BootstrapStep> = Collections.unmodifiableList(steps.map {
        it.copy(dependencies = Collections.unmodifiableSet(it.dependencies.toSet()),
            requiredFarmIds = Collections.unmodifiableSet(it.requiredFarmIds.toSet()),
            requiredParcelIds = Collections.unmodifiableSet(it.requiredParcelIds.toSet()))
    })

    init {
        require(this.steps.isNotEmpty() && this.steps.size <= 10000)
        require(this.consentedFarmIds.isNotEmpty())
        val preceding = mutableSetOf<UUID>()
        val parentOperations = mutableMapOf<Pair<BootstrapEntity, UUID>, UUID>()
        val entities = mutableSetOf<Pair<BootstrapEntity, UUID>>()
        this.steps.forEach { step ->
            require(step.payloadJson.isNotBlank() && step.payloadJson.toByteArray(Charsets.UTF_8).size <= 4 * 1024 * 1024)
            require(Regex("[0-9a-f]{64}").matches(step.payloadHash) && step.payloadVersion >= 1)
            require(preceding.containsAll(step.dependencies)) { "Dependencies must precede the operation" }
            require(preceding.add(step.operationId) && entities.add(step.entity to step.entityId))
            require(this.consentedFarmIds.containsAll(step.requiredFarmIds)) { "Unconsented farm history" }
            require(step.entity == BootstrapEntity.WORKSPACE || step.requiredFarmIds.isNotEmpty())
            if (step.entity == BootstrapEntity.FARM) require(step.requiredFarmIds == setOf(step.entityId))
            if (step.entity == BootstrapEntity.WORKSPACE) require(step.requiredFarmIds.isEmpty())
            if (step.entity != BootstrapEntity.CAMPAIGN) require(step.requiredParcelIds.isEmpty())
            if (step.entity in setOf(BootstrapEntity.PARCEL, BootstrapEntity.CAMPAIGN)) {
                step.requiredFarmIds.forEach { farm ->
                    require(parentOperations[BootstrapEntity.FARM to farm] in step.dependencies) { "Farm receipt is required" }
                }
            }
            step.requiredParcelIds.forEach { parcel ->
                require(parentOperations[BootstrapEntity.PARCEL to parcel] in step.dependencies) { "Parcel receipt is required" }
            }
            parentOperations[step.entity to step.entityId] = step.operationId
        }
        require(this.steps.count { it.entity == BootstrapEntity.WORKSPACE } == 1)
        require(this.steps.first().entity == BootstrapEntity.WORKSPACE && this.steps.first().entityId == remoteWorkspaceId)
        val root = this.steps.first().operationId
        this.steps.drop(1).forEach { require(root in it.dependencies) { "Workspace receipt is required" } }
    }

    /** Unknown, altered and duplicate receipts fail closed; no current-row version substitution. */
    fun ready(
        visibleActor: ReservationActor?,
        currentConsentId: UUID?,
        currentlyConsentedFarmIds: Set<UUID>,
        receipts: List<BootstrapReceipt>,
    ): List<BootstrapStep> {
        if (visibleActor != actor || currentConsentId != consentId ||
            !currentlyConsentedFarmIds.containsAll(consentedFarmIds)) return emptyList()
        val byOperation = steps.associateBy { it.operationId }
        val confirmed = mutableSetOf<UUID>()
        receipts.forEach { receipt ->
            val step = byOperation[receipt.operationId] ?: return emptyList()
            if (receipt.actor != actor || receipt.entityId != step.entityId || receipt.entity != step.entity ||
                receipt.payloadHash != step.payloadHash || receipt.payloadVersion != step.payloadVersion ||
                receipt.remoteVersion <= 0 || !confirmed.add(receipt.operationId)) return emptyList()
        }
        // A descendant receipt without its prerequisite receipts is an incomplete recovery snapshot.
        if (steps.any { it.operationId in confirmed && !confirmed.containsAll(it.dependencies) }) return emptyList()
        return steps.filter { it.operationId !in confirmed && confirmed.containsAll(it.dependencies) }
    }
}
