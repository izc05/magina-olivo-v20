package com.isivoltpro.maginaolivo.feature.farms

import com.isivoltpro.maginaolivo.data.local.model.ActivityStatus
import com.isivoltpro.maginaolivo.domain.activity.Activity
import com.isivoltpro.maginaolivo.domain.activity.ActivityDetail
import com.isivoltpro.maginaolivo.domain.activity.ActivityParcelTarget
import com.isivoltpro.maginaolivo.domain.activity.ActivityType
import com.isivoltpro.maginaolivo.domain.expense.Expense
import com.isivoltpro.maginaolivo.domain.expense.ExpenseCategory
import com.isivoltpro.maginaolivo.domain.expense.ExpenseOrigin
import com.isivoltpro.maginaolivo.domain.expense.ExpenseStatus
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FarmDashboardTest {
    private val workspace = UUID.randomUUID()
    private val farm = UUID.randomUUID()
    private val campaign = UUID.randomUUID()
    private val target = ActivityParcelTarget(UUID.randomUUID(), "Norte", 10_000.0)
    private val other = ActivityParcelTarget(UUID.randomUUID(), "Sur", 5_000.0)
    private val complete = ActivityDetail.Phytosanitary(productName = "Cobre", totalQuantity = 3.0, unit = "kg", reason = "Repilo", productRegistrationNumber = "ES-1")

    private fun activity(
        date: LocalDate = LocalDate.of(2026, 10, 1),
        type: ActivityType = ActivityType.PHYTOSANITARY,
        status: ActivityStatus = ActivityStatus.COMPLETED,
        detail: ActivityDetail? = complete,
        targets: List<ActivityParcelTarget> = listOf(target, other),
        campaignId: UUID? = campaign,
    ) = Activity(UUID.randomUUID(), workspace, farm, campaignId, type, status, date, "x", null, targets, detail, 1)

    private fun expense(amount: Long, category: ExpenseCategory, status: ExpenseStatus = ExpenseStatus.POSTED, campaignId: UUID? = campaign) = Expense(
        UUID.randomUUID(), workspace, LocalDate.of(2026, 10, 1), "c", category, amount, "EUR", status, ExpenseOrigin.values().first(), farmId = farm, campaignId = campaignId,
    )

    private fun dashboard(activities: List<Activity> = emptyList(), expenses: List<Expense> = emptyList(), linked: Set<UUID> = activities.map { it.id }.toSet()) =
        FarmDashboard.of(campaign, "26/27", activities, emptyList(), emptyList(), expenses, 2,
            openCycle = FarmCycleView(UUID.randomUUID(), "2026/27", closed = false, activityIds = linked))

    @Test fun aTreatmentOverSeveralParcelsCountsOnceWithItsWholeArea() {
        val summary = dashboard(listOf(activity()))
        assertEquals(1, summary.treatmentsInOpenCycle)
        assertEquals(15_000.0, summary.lastTreatment!!.areaM2!!, 0.0)
        assertEquals(listOf("Norte", "Sur"), summary.lastTreatment!!.parcels)
        assertEquals(3.0, summary.lastTreatment!!.quantity!!, 0.0)
    }

    @Test fun aMissingParcelAreaGivesNoPartialSurface() {
        val summary = dashboard(listOf(activity(targets = listOf(target, other.copy(areaAffectedM2 = null)))))
        assertNull(summary.lastTreatment!!.areaM2)
    }

    @Test fun missingLegalDataIsCountedAsIncomplete() {
        assertEquals(0, dashboard(listOf(activity())).incompleteTreatments)
        assertEquals(1, dashboard(listOf(activity(detail = complete.copy(productRegistrationNumber = null)))).incompleteTreatments)
    }

    @Test fun plannedWorkIsNeverCountedAsDone() {
        val summary = dashboard(listOf(activity(status = ActivityStatus.PLANNED), activity(type = ActivityType.PRUNING, detail = null)))
        assertEquals(1, summary.pendingWorks)
        assertEquals(1, summary.doneWorks)
        assertEquals(0, summary.treatmentsInOpenCycle)
    }

    @Test fun aTreatmentIsCountedOnlyWhenLinkedToTheOpenCycle() {
        val linked = activity(campaignId = null)
        val unlinked = activity(campaignId = null)
        val summary = dashboard(listOf(linked, unlinked), linked = setOf(linked.id))
        assertEquals(1, summary.treatmentsInOpenCycle)
        assertEquals("2026/27", summary.openCycleLabel)
    }

    @Test fun theHarvestCampaignNeverCountsAsTheAgriculturalCycle() {
        val inHarvest = activity(campaignId = campaign)
        val summary = FarmDashboard.of(campaign, "26/27", listOf(inHarvest), emptyList(), emptyList(), emptyList(), 0)
        assertNull(summary.openCycleLabel)
        assertEquals(0, summary.treatmentsInOpenCycle)
        assertEquals("26/27", summary.campaignName)
    }

    @Test fun onlyPostedExpensesOfTheCampaignAreAdded() {
        val summary = dashboard(expenses = listOf(expense(1_000, ExpenseCategory.LABOR), expense(500, ExpenseCategory.PRODUCTS),
            expense(9_999, ExpenseCategory.LABOR, ExpenseStatus.DRAFT), expense(7_777, ExpenseCategory.LABOR, campaignId = UUID.randomUUID())))
        val money = summary.money.single()
        assertEquals(1_500L, money.totalMinor)
        assertEquals(1_000L, money.labourMinor)
        assertEquals(1, summary.draftExpenses)
    }

    @Test fun otherCategoriesCompleteTheBreakdown() {
        val money = dashboard(expenses = listOf(expense(6_000, ExpenseCategory.LABOR), expense(10_000, ExpenseCategory.MACHINERY), expense(2_000, ExpenseCategory.FUEL))).money.single()
        assertEquals(18_000L, money.totalMinor)
        assertEquals(2_000L, money.otherMinor)
        assertEquals(money.totalMinor, money.labourMinor!! + money.machineryMinor!! + money.productsMinor!! + money.otherMinor!!)
    }

    @Test fun historyKeepsOneRowPerTreatmentAndFiltersItsFarmRecords() {
        val first = activity(date = LocalDate.of(2026, 9, 1))
        val second = activity(date = LocalDate.of(2026, 10, 1), detail = complete.copy(productName = "Azufre"), targets = listOf(other), campaignId = UUID.randomUUID())
        val pruning = activity(type = ActivityType.PRUNING, detail = null)
        val all = listOf(first, second, pruning)
        assertEquals(listOf(second, first), filterFarmHistory(all, ActivityType.PHYTOSANITARY, null, null, "", true))
        assertEquals(listOf(first, second), filterFarmHistory(all, ActivityType.PHYTOSANITARY, null, null, "", false))
        assertEquals(listOf(first), filterFarmHistory(all, ActivityType.PHYTOSANITARY, setOf(first.id), null, "", true))
        assertEquals(listOf(first), filterFarmHistory(all, ActivityType.PHYTOSANITARY, null, target.parcelId, "", true))
        assertEquals(listOf(second), filterFarmHistory(all, ActivityType.PHYTOSANITARY, null, null, "azu", true))
    }

    @Test fun anOverflowingTotalIsUnavailableNotWrapped() {
        assertNull(dashboard(expenses = listOf(expense(Long.MAX_VALUE, ExpenseCategory.LABOR), expense(1, ExpenseCategory.LABOR))).money.single().totalMinor)
    }
}
