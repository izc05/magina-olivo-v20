package com.isivoltpro.maginaolivo.feature.expenses

import com.isivoltpro.maginaolivo.data.local.model.ActivityStatus
import com.isivoltpro.maginaolivo.domain.activity.Activity
import com.isivoltpro.maginaolivo.domain.activity.ActivityParcelTarget
import com.isivoltpro.maginaolivo.domain.activity.ActivityType
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** #433: choosing a work in Gasto clears every relation that would no longer fit it. */
class ExpenseRelationsTest {
    private val farm = UUID.randomUUID()
    private val parcelA = UUID.randomUUID()
    private val parcelB = UUID.randomUUID()
    private val parcelC = UUID.randomUUID()
    private val campaign = UUID.randomUUID()
    private val day = UUID.randomUUID()
    private val base = ExpenseForm(date = "2026-03-10", amount = "40", concept = "Gasoil", farmId = farm)

    @Test fun generalWorkTakesTheExpenseOutOfTheRecollection() {
        val form = base.copy(campaignId = campaign, harvestId = day).withActivity(work(campaignId = null, parcelA))
        assertNull(form.campaignId)
        assertNull(form.harvestId)
    }

    @Test fun campaignWorkGivesItsCampaign() {
        val form = base.withActivity(work(campaignId = campaign, parcelA))
        assertEquals(campaign, form.campaignId)
    }

    @Test fun aParcelTheWorkWasNotDoneOnIsCleared() {
        assertNull(base.copy(parcelId = parcelC).withActivity(work(null, parcelA, parcelB)).parcelId)
    }

    @Test fun aParcelOfTheWorkStays() {
        assertEquals(parcelA, base.copy(parcelId = parcelA).withActivity(work(null, parcelA, parcelB)).parcelId)
    }

    @Test fun aWholeFarmWorkKeepsNoParcel() {
        assertNull(base.copy(parcelId = parcelA).withActivity(work(null)).parcelId)
    }

    @Test fun droppingTheWorkLeavesTheCampaignToChooseAgain() {
        val tied = base.withActivity(work(campaign, parcelA))
        val dropped = tied.withActivity(null)
        assertNull(dropped.activityId)
        assertNull(dropped.campaignId)
        // A recolección day still gives its Campaign.
        assertEquals(campaign, base.copy(harvestId = day, campaignId = campaign).withActivity(null).campaignId)
    }

    private fun work(campaignId: UUID?, vararg parcels: UUID) = Activity(
        id = UUID.randomUUID(), workspaceId = UUID.randomUUID(), farmId = farm, campaignId = campaignId,
        type = ActivityType.PRUNING, status = ActivityStatus.COMPLETED, activityDate = LocalDate.of(2026, 3, 10),
        description = "Poda", notes = null, targets = parcels.map { ActivityParcelTarget(it, it.toString().take(4)) }, version = 1,
    )
}
