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
    @Test fun exposedCostPerKgHandlesHighAmountsEveryIsoPrecisionAndConfirmedZero() {
        listOf("EUR" to 53000L, "JPY" to 530L, "KWD" to 530000L, "EUR" to Long.MAX_VALUE, "EUR" to 0L).forEach { (currency, minor) ->
            val notebook = CampaignNotebook.project(current, emptyList(), emptyList(),
                listOf(delivery(current, 3200000, day(24), "Coop", null)),
                listOf(expense(current, minor, ExpenseStatus.POSTED).copy(currency = currency)))
            // #486: thousandths of the currency unit per kilo, whatever the currency's own decimals.
            val expected = java.math.BigDecimal.valueOf(minor)
                .movePointLeft(java.util.Currency.getInstance(currency).defaultFractionDigits)
                .multiply(java.math.BigDecimal.valueOf(1_000_000))
                .divide(java.math.BigDecimal.valueOf(3200000), 0, java.math.RoundingMode.HALF_UP).longValueExact()
            assertEquals(expected, CampaignDashboard.of(notebook, day(24)).costPerKgMilli)
            assertEquals(expected, CampaignComparison.of(listOf(notebook)).single().costPerKgMilli)
        }
    }

    @Test fun unsupportedCurrencyAndMissingWeighingStayUnavailableOnKpis() {
        val expense = expense(current, 1000, ExpenseStatus.POSTED).copy(currency = "ZZZ")
        val notebook = CampaignNotebook.project(current, emptyList(), emptyList(),
            listOf(delivery(current, 3200000, day(24), "Coop", null)), listOf(expense))
        assertNull(CampaignDashboard.of(notebook, day(24)).costPerKgMilli)
        assertNull(CampaignComparison.of(listOf(notebook)).single().costPerKgMilli)
        assertNull(CampaignDashboard.of(notebook.copy(deliveries = emptyList()), day(24)).costPerKgMilli)
    }
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
    fun cooperativeAnalyticsUsesOrganizationIdAndKeepsManualDestinationsSeparate() {
        val bedmar = UUID.randomUUID()
        val jodar = UUID.randomUUID()
        val notebook = CampaignNotebook.project(
            current, emptyList(), emptyList(),
            listOf(
                delivery(current, 1_000_000, day(23), "Cooperativa San Isidro", null, bedmar),
                delivery(current, 2_000_000, day(24), "S.C.A. San Isidro", null, bedmar),
                delivery(current, 3_000_000, day(25), "San Isidro", null, jodar),
                delivery(current, 4_000_000, day(26), "San Isidro", null, null),
            ),
            emptyList(),
        )

        val rows = CampaignSeries.of(notebook).cooperatives
        assertEquals(3, rows.size)
        assertEquals(3_000_000L, rows.single { it.name == "S.C.A. San Isidro" }.summary.deliveredGrams)
        assertEquals(listOf(3_000_000L, 4_000_000L), rows.filter { it.name == "San Isidro" }.mapNotNull { it.summary.deliveredGrams }.sorted())
    }

    @Test
    fun aJornadaAwaitingItsFirstPesadaAddsNoKilosAndNoChartPoint() {
        // Gate 20: opened before any Pesada, it stores 0 meaning "not weighed yet" — never a fake zero.
        val open = harvest(current, 0, day(27))
        assertTrue(open.awaitingPesadas)
        val onlyOpen = CampaignNotebook.project(current, emptyList(), listOf(open), emptyList(), emptyList())
        assertEquals(1, onlyOpen.harvestSummary.harvestCount)
        assertEquals(0, onlyOpen.harvestSummary.weighedCount)
        assertTrue(CampaignSeries.of(onlyOpen).isEmpty)
        assertNull(CampaignComparison.of(listOf(onlyOpen)).single().harvestedGrams)

        val mixed = CampaignNotebook.project(current, emptyList(), listOf(harvest(current, 3_000_000, day(24)), open), emptyList(), emptyList())
        assertEquals(3_000_000L, mixed.harvestSummary.totalGrams)
        assertEquals(1, mixed.harvestSummary.weighedCount)
        assertEquals(listOf(day(24)), CampaignSeries.of(mixed).days.map { it.date })
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
        assertEquals(300L, rows[1].costPerKgMilli)
        // No posted cost last year: no cost per kilo, never 0 €.
        assertNull(rows[0].costPerKgMilli)
        assertTrue(rows.all { it.yieldCoveragePercent == 100 })
    }

    /** #355: the history reads the comparison as it is; unknowns stay gaps, never zeros. */
    @Test fun theHistoryKeepsGapsAndReadsTheComparisonAsIs() {
        val older = campaign("2024/25", LocalDate.of(2024, 10, 1), CampaignStatus.CLOSED)
        val notebooks = listOf(
            // 2024/25: nothing weighed.
            CampaignNotebook.project(older, emptyList(), emptyList(), emptyList(), emptyList()),
            // 2025/26: weighed, no analysis, no cost.
            CampaignNotebook.project(last, emptyList(), emptyList(), listOf(delivery(last, 4_000_000, LocalDate.of(2025, 11, 20), "Coop", null)), emptyList()),
            // 2026/27: weighed, analysed and with a posted cost.
            CampaignNotebook.project(current, emptyList(), emptyList(), listOf(delivery(current, 2_000_000, day(24), "Coop", 2_100)),
                listOf(expense(current, 50_000, ExpenseStatus.POSTED))),
        )
        val comparison = CampaignComparison.of(notebooks)
        val history = CampaignHistory.of(comparison)
        assertEquals(listOf("2024/25", "2025/26", "2026/27"), history.points.map { it.name })
        assertEquals(listOf(null, 4_000_000L, 2_000_000L), history.points.map { it.deliveredGrams })
        assertEquals(listOf(null, null, 2_100), history.points.map { it.yieldHundredths })
        assertEquals(comparison.map { it.costPerKgMilli }, history.points.map { it.costPerKgMilli })
        assertEquals("EUR", history.costCurrency)
        assertTrue(history.otherCurrencyCampaigns.isEmpty())
    }

    /** #449: a campaign with a machine still unpriced keeps its cost per kilo, marked incomplete. */
    @Test fun anUnconfirmedCostMarksOnlyItsOwnCampaign() {
        val lastDay = harvest(last, 0, LocalDate.of(2025, 11, 20))
        val currentDay = harvest(current, 0, day(24))
        val unpriced = com.isivoltpro.maginaolivo.domain.equipment.EquipmentLine(UUID.randomUUID(), currentDay.id,
            com.isivoltpro.maginaolivo.domain.equipment.EquipmentType.TRAILER, null, 1, null, 1)
        val notebooks = listOf(last, current).map { campaign ->
            CampaignNotebook.project(campaign, emptyList(), listOf(lastDay, currentDay),
                listOf(delivery(last, 1_000_000, LocalDate.of(2025, 11, 20), "Coop", null), delivery(current, 1_000_000, day(24), "Coop", null)),
                listOf(expense(last, 10_000, ExpenseStatus.POSTED), expense(current, 20_000, ExpenseStatus.POSTED)),
                equipment = listOf(unpriced))
        }
        val comparison = CampaignComparison.of(notebooks)
        assertEquals(listOf(true, false), comparison.map { it.costComplete })
        assertEquals(200L, comparison.last().costPerKgMilli)
        val history = CampaignHistory.of(comparison)
        assertEquals(listOf(false, true), history.points.map { it.costIncomplete })
        assertEquals(200L, history.points.last().costPerKgMilli)
    }

    @Test fun theCostSeriesNeverMixesCurrencies() {
        val notebooks = listOf(
            CampaignNotebook.project(last, emptyList(), emptyList(), listOf(delivery(last, 1_000_000, LocalDate.of(2025, 11, 20), "Coop", null)),
                listOf(expense(last, 10_000, ExpenseStatus.POSTED).copy(currency = "USD"))),
            CampaignNotebook.project(current, emptyList(), emptyList(), listOf(delivery(current, 1_000_000, day(24), "Coop", null)),
                listOf(expense(current, 20_000, ExpenseStatus.POSTED))),
        )
        val history = CampaignHistory.of(CampaignComparison.of(notebooks))
        assertEquals("EUR", history.costCurrency)
        assertNull(history.points.first().costPerKgMilli)
        assertEquals(listOf("2025/26"), history.otherCurrencyCampaigns)
    }

    @Test
    fun overflowingHistoricalSeriesStayUnknownInsteadOfWrappingNegative() {
        val notebook = CampaignNotebook.project(
            current,
            emptyList(),
            listOf(
                harvest(current, Long.MAX_VALUE, day(24)),
                harvest(current, 1, day(25)),
            ),
            listOf(
                delivery(current, Long.MAX_VALUE, day(24), "Coop", null),
                delivery(current, 1, day(25), "Coop", null),
            ),
            emptyList(),
        )

        val series = CampaignSeries.of(notebook)
        assertNull(series.deliveredGrams)
        assertNull(series.harvestedGrams)
        assertEquals(Long.MAX_VALUE, series.days.first().cumulativeDeliveredGrams)
        assertNull(series.days.last().cumulativeDeliveredGrams)
        assertNull(CampaignComparison.of(listOf(notebook)).single().deliveredGrams)
    }

    @Test
    fun anOverflowingDayKeepsItsPesadasAndKnownAnalysis() {
        val notebook = CampaignNotebook.project(
            current, emptyList(), emptyList(),
            listOf(
                delivery(current, Long.MAX_VALUE, day(24), "Coop", null),
                delivery(current, 1, day(24), "Coop", 2_000),
            ),
            emptyList(),
        )
        val point = CampaignSeries.of(notebook).days.single()
        assertNull(point.deliveredGrams)
        assertEquals(2, point.deliveryCount)
        assertEquals(2_000, requireNotNull(point.fatYield).hundredths)
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

    private fun delivery(
        campaign: Campaign,
        grams: Long,
        date: LocalDate,
        destination: String,
        fat: Int?,
        organizationId: UUID? = null,
    ): Delivery {
        val id = UUID.randomUUID()
        return Delivery(
            id = id, workspaceId = workspace, farmId = farm, campaignId = campaign.id, deliveryDate = date,
            destinationOrganizationId = organizationId, destinationName = destination, netGrams = grams, grossGrams = null,
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
