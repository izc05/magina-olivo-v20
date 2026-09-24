package com.isivoltpro.maginaolivo.domain.analytics

import com.isivoltpro.maginaolivo.data.local.model.CampaignStatus
import com.isivoltpro.maginaolivo.domain.campaign.Campaign
import com.isivoltpro.maginaolivo.domain.delivery.Delivery
import com.isivoltpro.maginaolivo.domain.delivery.DeliverySource
import com.isivoltpro.maginaolivo.domain.delivery.YieldAnalysis
import com.isivoltpro.maginaolivo.domain.expense.Expense
import com.isivoltpro.maginaolivo.domain.expense.ExpenseCategory
import com.isivoltpro.maginaolivo.domain.expense.ExpenseOrigin
import com.isivoltpro.maginaolivo.domain.expense.ExpenseStatus
import com.isivoltpro.maginaolivo.domain.harvest.Harvest
import com.isivoltpro.maginaolivo.domain.notebook.CampaignNotebook
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CampaignAnalyticsTest {
    private val workspace = UUID.randomUUID()
    private val farm = UUID.randomUUID()
    private val last = campaign("2025/26", LocalDate.of(2025, 10, 1), CampaignStatus.CLOSED)
    private val current = campaign("2026/27", LocalDate.of(2026, 10, 1), CampaignStatus.HARVEST)
    private fun day(d: Int) = LocalDate.of(2026, 11, d)

    @Test
    fun theSeriesReconcilesWithTheRecordsAndLeavesUnknownYieldEmpty() {
        val notebook = CampaignNotebook.project(
            current, emptyList(),
            listOf(harvest(current, 3_000_000, day(24)), harvest(current, 2_000_000, day(25))),
            listOf(
                delivery(current, 2_000_000, day(24), "Coop. San Isidro", 2_200),
                delivery(current, 1_000_000, day(24), "Almazara El Molino", null),
                delivery(current, 1_500_000, day(26), "Coop. San Isidro", null),
            ),
            emptyList(),
        )
        val series = CampaignSeries.of(notebook)
        assertEquals(listOf(day(24), day(25), day(26)), series.days.map { it.date })
        assertEquals(notebook.deliverySummary.deliveredGrams, series.deliveredGrams)
        assertEquals(notebook.harvestSummary.totalGrams, series.harvestedGrams)
        assertEquals(listOf(3_000_000L, 3_000_000L, 4_500_000L), series.days.map { it.cumulativeDeliveredGrams })
        // 24 Nov: only the analysed 2.000 kg count in the day's yield; 25 has no delivery; 26 no analysis.
        assertEquals(2_200, series.days[0].fatYield!!.hundredths)
        assertEquals(2_000_000L, series.days[0].fatYield!!.analysedGrams)
        assertNull(series.days[1].fatYield)
        assertNull(series.days[2].fatYield)
        val isidro = series.cooperatives.first { it.name == "Coop. San Isidro" }
        assertEquals(3_500_000L, isidro.summary.deliveredGrams)
        assertEquals(57, isidro.coveragePercent)
    }

    @Test
    fun yearOverYearAndCostPerKgOnlyWhenBothSidesExist() {
        val deliveries = listOf(
            delivery(last, 10_000_000, LocalDate.of(2025, 11, 20), "Coop", 2_000),
            delivery(current, 12_000_000, day(24), "Coop", 2_100),
        )
        val expenses = listOf(
            expense(current, 360_000, ExpenseStatus.POSTED),
            expense(current, 99_999, ExpenseStatus.DRAFT),
        )
        val rows = CampaignComparison.of(
            listOf(current, last).map { CampaignNotebook.project(it, emptyList(), emptyList(), deliveries, expenses) },
        )
        assertEquals(listOf("2025/26", "2026/27"), rows.map { it.campaign.name })
        assertNull(rows[0].deliveredChangePercent)
        assertEquals(20, rows[1].deliveredChangePercent)
        // 3.600 € posted over 12.000 kg = 0,30 €/kg; the draft is never counted.
        assertEquals(30L, rows[1].costPerKgMinor)
        // No posted cost last year: no cost per kilo, never 0 €.
        assertNull(rows[0].costPerKgMinor)
        assertTrue(rows.all { it.yieldCoveragePercent == 100 })
    }

    private fun campaign(name: String, start: LocalDate, status: CampaignStatus) = Campaign(
        id = UUID.randomUUID(), workspaceId = workspace, farmId = farm, name = name, startDate = start,
        endDate = if (status == CampaignStatus.CLOSED) start.plusMonths(9) else null, status = status,
        notes = null, snapshots = emptyList(), version = 1,
    )

    private fun harvest(campaign: Campaign, grams: Long, date: LocalDate) = Harvest(
        id = UUID.randomUUID(), workspaceId = workspace, farmId = farm, campaignId = campaign.id, harvestDate = date,
        totalGrams = grams, shares = emptyList(), collectionMethod = null, workerCount = null, machineryText = null,
        notes = null, version = 1,
    )

    private fun delivery(campaign: Campaign, grams: Long, date: LocalDate, destination: String, fat: Int?): Delivery {
        val id = UUID.randomUUID()
        return Delivery(
            id = id, workspaceId = workspace, farmId = farm, campaignId = campaign.id, deliveryDate = date,
            destinationOrganizationId = null, destinationName = destination, netGrams = grams, grossGrams = null,
            tareGrams = null, deliveryNumber = null, ticketNumber = null, source = DeliverySource.MANUAL,
            shares = emptyList(), notes = null, version = 1,
            analysis = fat?.let { YieldAnalysis(UUID.randomUUID(), id, date, it, null, null, 1) },
        )
    }

    private fun expense(campaign: Campaign, minor: Long, status: ExpenseStatus) = Expense(
        id = UUID.randomUUID(), workspaceId = workspace, expenseDate = day(20), concept = "Gasto",
        category = ExpenseCategory.FUEL, amountMinor = minor, currency = "EUR", status = status,
        origin = ExpenseOrigin.MANUAL, farmId = farm, campaignId = campaign.id,
    )
}
