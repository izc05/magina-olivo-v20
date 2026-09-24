package com.isivoltpro.maginaolivo.domain.notebook

import com.isivoltpro.maginaolivo.data.local.model.ActivityStatus
import com.isivoltpro.maginaolivo.data.local.model.CampaignStatus
import com.isivoltpro.maginaolivo.domain.activity.Activity
import com.isivoltpro.maginaolivo.domain.activity.ActivityType
import com.isivoltpro.maginaolivo.domain.campaign.Campaign
import com.isivoltpro.maginaolivo.domain.delivery.Delivery
import com.isivoltpro.maginaolivo.domain.delivery.DeliverySource
import com.isivoltpro.maginaolivo.domain.delivery.DeliverySummary
import com.isivoltpro.maginaolivo.domain.delivery.YieldAnalysis
import com.isivoltpro.maginaolivo.domain.expense.Expense
import com.isivoltpro.maginaolivo.domain.expense.ExpenseCategory
import com.isivoltpro.maginaolivo.domain.expense.ExpenseOrigin
import com.isivoltpro.maginaolivo.domain.expense.ExpenseStatus
import com.isivoltpro.maginaolivo.domain.expense.ExpenseSummary
import com.isivoltpro.maginaolivo.domain.harvest.Harvest
import com.isivoltpro.maginaolivo.domain.harvest.HarvestSummary
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CampaignNotebookTest {
    private val workspace = UUID.randomUUID()
    private val farm = UUID.randomUUID()
    private val otherFarm = UUID.randomUUID()
    private val campaign = Campaign(
        id = UUID.randomUUID(), workspaceId = workspace, farmId = farm, name = "2026/27",
        startDate = LocalDate.of(2026, 9, 1), endDate = null, status = CampaignStatus.HARVEST,
        notes = null, snapshots = emptyList(), version = 1,
    )
    private val otherCampaign = UUID.randomUUID()

    @Test fun onlyThisCampaignsRecordsAndTheFarmsUnassignedOnesInsideItsDates() {
        val notebook = CampaignNotebook.project(
            campaign,
            activities = listOf(
                activity(ActivityType.PRUNING, LocalDate.of(2026, 10, 2), campaign.id),
                activity(ActivityType.PHYTOSANITARY, LocalDate.of(2026, 9, 20), null), // unassigned, inside dates
                activity(ActivityType.IRRIGATION, LocalDate.of(2026, 8, 1), null), // before the campaign
                activity(ActivityType.PRUNING, LocalDate.of(2026, 10, 3), otherCampaign), // another campaign
                activity(ActivityType.PRUNING, LocalDate.of(2026, 10, 3), null, farmId = otherFarm), // another farm
                activity(ActivityType.HARVEST_DAY, LocalDate.of(2026, 11, 24), campaign.id),
            ),
            harvests = emptyList(), deliveries = emptyList(), expenses = emptyList(),
        )
        assertEquals(listOf(ActivityType.PRUNING, ActivityType.PHYTOSANITARY), notebook.works.map { it.type })
        assertEquals(1, notebook.harvestDays.size)
        assertEquals(1, notebook.recollectionDays.size)
    }

    @Test fun totalsAreTheSameSummariesTheirOwnScreensShow() {
        val harvests = listOf(harvest(2_850_000, LocalDate.of(2026, 11, 20)), harvest(1_920_000, LocalDate.of(2026, 11, 21)))
        val deliveries = listOf(
            delivery(1_840_000, LocalDate.of(2026, 11, 20), "Coop. San Isidro", yieldHundredths = 2_280),
            delivery(2_000_000, LocalDate.of(2026, 11, 21), "Almazara La Loma", yieldHundredths = null),
        )
        val expenses = listOf(
            expense(12_000, ExpenseCategory.HARVEST, ExpenseStatus.POSTED, LocalDate.of(2026, 11, 20)),
            expense(4_500, ExpenseCategory.FUEL, ExpenseStatus.POSTED, LocalDate.of(2026, 10, 2)),
            expense(9_999, ExpenseCategory.TRANSPORT, ExpenseStatus.DRAFT, LocalDate.of(2026, 11, 21)),
        )
        val notebook = CampaignNotebook.project(campaign, emptyList(), harvests, deliveries, expenses)

        assertEquals(HarvestSummary.of(harvests), notebook.harvestSummary)
        assertEquals(DeliverySummary.of(deliveries), notebook.deliverySummary)
        assertEquals(ExpenseSummary.of(expenses), notebook.expenseSummary)
        assertEquals(16_500, notebook.expenseSummary.totalMinor) // the draft is listed, never summed
        assertEquals(12_000, notebook.recollectionExpenseSummary.totalMinor)
        // Yield only from analysed kilos, with its coverage; the pending delivery never counts as 0 %.
        assertEquals(2_280, notebook.deliverySummary.fatYield!!.hundredths)
        assertEquals(47, notebook.deliverySummary.coveragePercent(notebook.deliverySummary.fatYield))
        // Two recolección days, newest first, each row the canonical record.
        assertEquals(listOf(LocalDate.of(2026, 11, 21), LocalDate.of(2026, 11, 20)), notebook.recollectionDays.map { it.date })
        assertTrue(notebook.recollectionDays.first().items.first() is RecollectionItem.HarvestItem)
    }

    private fun activity(type: ActivityType, date: LocalDate, campaignId: UUID?, farmId: UUID = farm) = Activity(
        id = UUID.randomUUID(), workspaceId = workspace, farmId = farmId, campaignId = campaignId, type = type,
        status = ActivityStatus.COMPLETED, activityDate = date, description = type.name, notes = null,
        targets = emptyList(), version = 1,
    )

    private fun harvest(grams: Long, date: LocalDate) = Harvest(
        id = UUID.randomUUID(), workspaceId = workspace, farmId = farm, campaignId = campaign.id, harvestDate = date,
        totalGrams = grams, shares = emptyList(), collectionMethod = null, workerCount = null, machineryText = null,
        notes = null, version = 1,
    )

    private fun delivery(grams: Long, date: LocalDate, destination: String, yieldHundredths: Int?): Delivery {
        val id = UUID.randomUUID()
        return Delivery(
            id = id, workspaceId = workspace, farmId = farm, campaignId = campaign.id, deliveryDate = date,
            destinationOrganizationId = null, destinationName = destination, netGrams = grams, grossGrams = null,
            tareGrams = null, deliveryNumber = null, ticketNumber = "45872", source = DeliverySource.MANUAL,
            shares = emptyList(), notes = null, version = 1,
            analysis = yieldHundredths?.let { YieldAnalysis(UUID.randomUUID(), id, date.plusDays(3), it, null, null, 1) },
        )
    }

    private fun expense(minor: Long, category: ExpenseCategory, status: ExpenseStatus, date: LocalDate) = Expense(
        id = UUID.randomUUID(), workspaceId = workspace, expenseDate = date, concept = category.name, category = category,
        amountMinor = minor, currency = "EUR", status = status, origin = ExpenseOrigin.MANUAL, farmId = farm,
        campaignId = campaign.id,
    )
}
