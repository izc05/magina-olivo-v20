package com.isivoltpro.maginaolivo.account.bootstrap

import com.isivoltpro.maginaolivo.account.reservation.ReservationActor
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class BootstrapPlanTest {
    private fun id() = UUID.randomUUID()
    private val actor = ReservationActor("https://example.test/auth/v1", id())
    private val workspace = id()
    private val farm = id()
    private val consent = id()
    private val root = BootstrapStep(id(), workspace, BootstrapEntity.WORKSPACE, "{}", "a".repeat(64), 1, emptySet(), emptySet())
    private val farmStep = BootstrapStep(id(), farm, BootstrapEntity.FARM, "{\"name\":\"Olivo\"}", "b".repeat(64), 1, setOf(root.operationId), setOf(farm))
    private val parcel = BootstrapStep(id(), id(), BootstrapEntity.PARCEL, "{}", "c".repeat(64), 1, setOf(root.operationId, farmStep.operationId), setOf(farm))
    private fun plan(steps: List<BootstrapStep> = listOf(root, farmStep, parcel)) = BootstrapPlan(actor, workspace, consent, setOf(farm), steps)
    private fun receipt(step: BootstrapStep) = BootstrapReceipt(actor, step.operationId, step.entityId, step.entity, step.payloadHash, step.payloadVersion, 1)
    private fun ready(receipts: List<BootstrapReceipt>) = plan().ready(actor, consent, setOf(farm), receipts)

    @Test fun prerequisitesAdvanceOnlyAfterExactReceipts() {
        assertEquals(listOf(root), ready(emptyList()))
        assertEquals(listOf(farmStep), ready(listOf(receipt(root))))
        assertEquals(listOf(parcel), ready(listOf(receipt(root), receipt(farmStep))))
        assertTrue(ready(listOf(receipt(root), receipt(farmStep), receipt(parcel))).isEmpty())
    }

    @Test fun lostAcknowledgementKeepsSameOperationAndSnapshot() {
        assertEquals(ready(emptyList()), ready(emptyList()))
        assertEquals(root.operationId, ready(emptyList()).single().operationId)
        assertEquals(root.payloadJson, ready(emptyList()).single().payloadJson)
    }

    @Test fun accountSwitchRevocationAndConsentReplacementPauseAllSteps() {
        val p = plan()
        assertTrue(p.ready(actor.copy(userId = id()), consent, setOf(farm), emptyList()).isEmpty())
        assertTrue(p.ready(null, consent, setOf(farm), emptyList()).isEmpty())
        assertTrue(p.ready(actor, id(), setOf(farm), emptyList()).isEmpty())
        assertTrue(p.ready(actor, consent, emptySet(), emptyList()).isEmpty())
    }

    @Test fun alteredForeignDuplicateAndIncompleteReceiptsFailClosed() {
        val good = receipt(root)
        listOf(good.copy(actor = actor.copy(userId = id())), good.copy(entityId = id()),
            good.copy(entity = BootstrapEntity.FARM), good.copy(payloadHash = "d".repeat(64)),
            good.copy(payloadVersion = 2), good.copy(remoteVersion = 0), good.copy(operationId = id()))
            .forEach { assertTrue(ready(listOf(it)).isEmpty()) }
        assertTrue(ready(listOf(good, good)).isEmpty())
        assertTrue(ready(listOf(receipt(farmStep))).isEmpty())
    }

    @Test fun refusesHistoryFromAnUnconsentedFarm() {
        assertThrows(IllegalArgumentException::class.java) {
            plan(listOf(root, farmStep, parcel.copy(requiredFarmIds = setOf(farm, id()))))
        }
    }

    @Test fun refusesCyclesDuplicateOperationsAndAnotherWorkspace() {
        assertThrows(IllegalArgumentException::class.java) { plan(listOf(root, farmStep.copy(dependencies = setOf(parcel.operationId)), parcel)) }
        assertThrows(IllegalArgumentException::class.java) { plan(listOf(root, farmStep.copy(operationId = root.operationId))) }
        assertThrows(IllegalArgumentException::class.java) { plan(listOf(root.copy(entityId = id()), farmStep)) }
    }

    @Test fun callerCollectionChangesCannotRewritePreparedPlan() {
        val farms = mutableSetOf(farm)
        val dependencies = mutableSetOf(root.operationId)
        val steps = mutableListOf(root, farmStep.copy(dependencies = dependencies))
        val p = BootstrapPlan(actor, workspace, consent, farms, steps)
        farms.clear(); dependencies.clear(); steps.clear()
        assertEquals(listOf(farmStep), p.ready(actor, consent, setOf(farm), listOf(receipt(root))))
    }
    @Test fun refusesOmittedFarmAndParcelPrerequisites() {
        assertThrows(IllegalArgumentException::class.java) { plan(listOf(root, farmStep, parcel.copy(dependencies = setOf(root.operationId)))) }
        val campaign = BootstrapStep(id(), id(), BootstrapEntity.CAMPAIGN, "{}", "d".repeat(64), 1,
            setOf(root.operationId, farmStep.operationId), setOf(farm), setOf(parcel.entityId))
        assertThrows(IllegalArgumentException::class.java) { plan(listOf(root, farmStep, parcel, campaign)) }
        assertEquals(listOf(campaign.copy(dependencies = campaign.dependencies + parcel.operationId)), plan(listOf(root, farmStep, parcel,
            campaign.copy(dependencies = campaign.dependencies + parcel.operationId)))
            .ready(actor, consent, setOf(farm), listOf(receipt(root), receipt(farmStep), receipt(parcel))))
    }

    @Test fun exposedCollectionsCannotBeMutated() {
        val p = plan()
        assertThrows(UnsupportedOperationException::class.java) { (p.steps as MutableList).clear() }
        assertThrows(UnsupportedOperationException::class.java) { (p.consentedFarmIds as MutableSet).clear() }
        assertThrows(UnsupportedOperationException::class.java) { (p.steps.last().dependencies as MutableSet).clear() }
        assertThrows(UnsupportedOperationException::class.java) { (p.steps.last().requiredFarmIds as MutableSet).clear() }
    }

}
