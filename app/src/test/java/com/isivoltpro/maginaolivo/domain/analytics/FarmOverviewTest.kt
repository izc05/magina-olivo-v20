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
import com.isivoltpro.maginaolivo.domain.farm.Farm
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** #359: the holding's season is the sum of each Farm's own figures, never an average of averages. */
class FarmOverviewTest {
    private val workspace = UUID.randomUUID()
    private val estacas = farm("Estacas")
    private val cerro = farm("El Cerro")
    private val llanos = farm("Los Llanos")
    private val estacasNow = campaign(estacas, "2026/27", LocalDate.of(2026, 10, 1))
    private val cerroNow = campaign(cerro, "Campaña 26-27", LocalDate.of(2026, 11, 5))
    private val estacasBefore = campaign(estacas, "2025/26", LocalDate.of(2025, 10, 1))

    @Test fun seasonsRunFromSeptemberToAugust() {
        assertEquals("2026/27", OliveSeason.of(LocalDate.of(2026, 9, 1)))
        assertEquals("2026/27", OliveSeason.of(LocalDate.of(2027, 8, 31)))
        assertEquals("2099/00", OliveSeason.of(LocalDate.of(2099, 10, 1)))
        assertEquals(listOf("2026/27", "2025/26"), OliveSeason.available(listOf(estacasBefore, estacasNow, cerroNow)))
    }

    @Test fun totalsAreSumsAndCostPerKgIsTotalOverTotal() {
        val deliveries = listOf(
            delivery(estacasNow, 3_000_000, 2_000), // 3.000 kg at 20 %
            delivery(cerroNow, 1_000_000, null),    // 1.000 kg, no analysis
            delivery(estacasBefore, 9_000_000, 2_500), // another season: never counted
        )
        val expenses = listOf(cost(estacasNow, 30_000), cost(cerroNow, 50_000), cost(estacasBefore, 99_999),
            cost(cerroNow, 7_000, ExpenseStatus.DRAFT))
        val overview = FarmOverview.of("2026/27", listOf(estacas, cerro, llanos), listOf(estacasNow, cerroNow, estacasBefore), deliveries, expenses)

        assertEquals(4_000_000L, overview.delivery.deliveredGrams)
        assertEquals(2_000, overview.delivery.fatYield!!.hundredths)
        assertEquals(75, overview.yieldCoveragePercent)
        assertEquals(80_000L, overview.costs.single().amountMinor)
        // 800 € / 4.000 kg = 0,20 €/kg — not the average of 0,10 and 0,50.
        assertEquals(20L, overview.costPerKgMinor)
        assertEquals(listOf("Estacas", "El Cerro"), overview.farms.map { it.farmName })
        assertEquals(75, overview.sharePercent(overview.farms.first()))
        assertEquals(listOf("Los Llanos"), overview.farmsWithoutCampaign)
        assertEquals(overview.delivery.deliveredGrams, overview.farms.sumOf { it.delivery.deliveredGrams })
    }

    /** #616: archiving is operational only; it cannot rewrite a season that already happened. */
    @Test fun archivedFarmKeepsHistoricalKilosCostsAndGeneralExpenses() {
        val archivedEstacas = estacas.copy(archivedAt = java.time.Instant.parse("2026-10-06T12:00:00Z"))
        val deliveries = listOf(
            delivery(estacasNow, 4_000_000, 2_000),
            delivery(cerroNow, 6_000_000, 2_000),
        )
        val general = Expense(
            id = UUID.randomUUID(),
            workspaceId = workspace,
            expenseDate = LocalDate.of(2027, 2, 10),
            concept = "Poda histórica",
            category = ExpenseCategory.LABOR,
            amountMinor = 12_000,
            currency = "EUR",
            status = ExpenseStatus.POSTED,
            origin = ExpenseOrigin.MANUAL,
            farmId = estacas.id,
        )
        val expenses = listOf(cost(estacasNow, 40_000), cost(cerroNow, 60_000), general)

        val overview = FarmOverview.of(
            "2026/27",
            listOf(archivedEstacas, cerro),
            listOf(estacasNow, cerroNow),
            deliveries,
            expenses,
        )

        assertEquals(10_000_000L, overview.delivery.deliveredGrams)
        assertEquals(100_000L, overview.costs.single().amountMinor)
        assertEquals(12_000L, overview.generalCosts.single().amountMinor)
        assertEquals(true, overview.farms.first { it.farmId == estacas.id }.archived)
        assertEquals(false, overview.farms.first { it.farmId == cerro.id }.archived)
        assertEquals(emptyList<String>(), overview.farmsWithoutCampaign)
    }

    @Test fun archivedFarmWithoutCurrentCampaignIsNotReportedAsMissingOperationalCampaign() {
        val archived = llanos.copy(archivedAt = java.time.Instant.parse("2026-10-06T12:00:00Z"))
        val overview = FarmOverview.of(
            "2026/27",
            listOf(estacas, archived),
            listOf(estacasNow),
            listOf(delivery(estacasNow, 1_000_000, null)),
            emptyList(),
        )
        assertEquals(emptyList<String>(), overview.farmsWithoutCampaign)
        assertEquals(listOf("Estacas"), overview.farms.map { it.farmName })
    }

    /** #449: an incomplete campaign marks its Farm and the season, never another Farm or season. */
    @Test fun anIncompleteCampaignMarksItsFarmAndSeasonOnly() {
        val deliveries = listOf(delivery(estacasNow, 1_000_000, null), delivery(cerroNow, 1_000_000, null))
        val expenses = listOf(cost(estacasNow, 10_000), cost(cerroNow, 10_000))
        val all = listOf(estacasNow, cerroNow, estacasBefore)
        val now = FarmOverview.of("2026/27", listOf(estacas, cerro), all, deliveries, expenses, incompleteCampaigns = setOf(cerroNow.id))
        assertEquals(false, now.costComplete)
        assertEquals(mapOf("Estacas" to true, "El Cerro" to false), now.farms.associate { it.farmName to it.costComplete })
        // The money is unchanged: still the posted ledger.
        assertEquals(20_000L, now.costs.single().amountMinor)
        val before = FarmOverview.of("2025/26", listOf(estacas, cerro), all, deliveries, expenses, incompleteCampaigns = setOf(cerroNow.id))
        assertEquals(true, before.costComplete)
    }

    @Test fun twoCurrenciesKeepTwoTotalsAndNoGlobalCostPerKg() {
        val overview = FarmOverview.of("2026/27", listOf(estacas, cerro), listOf(estacasNow, cerroNow),
            listOf(delivery(estacasNow, 1_000_000, null), delivery(cerroNow, 1_000_000, null)),
            listOf(cost(estacasNow, 10_000), cost(cerroNow, 10_000).copy(currency = "USD")))
        assertEquals(listOf("EUR", "USD"), overview.costs.map { it.currency })
        assertNull(overview.costPerKgMinor)
    }

    /** Codex #384: a closed season keeps the Farm's frozen name; a running one shows today's. */
    @Test fun aClosedSeasonKeepsTheFarmsFrozenName() {
        val renamed = estacas.copy(name = "Estacas Nuevas")
        val closed = estacasBefore.copy(status = CampaignStatus.CLOSED,
            snapshots = listOf(com.isivoltpro.maginaolivo.domain.campaign.CampaignParcelSnapshot(UUID.randomUUID(), "Estacas", "Haza", null, null, null)))
        assertEquals(listOf("Estacas"), FarmOverview.of("2025/26", listOf(renamed), listOf(closed), emptyList(), emptyList()).farms.map { it.farmName })
        assertEquals(listOf("Estacas Nuevas"), FarmOverview.of("2026/27", listOf(renamed), listOf(estacasNow), emptyList(), emptyList()).farms.map { it.farmName })
    }

    /** Codex #384: a labour subtotal too large to add makes the labour total unknown, never smaller. */
    @Test fun anOverflowingLabourTotalIsUnknown() {
        val labour = { campaign: Campaign, minor: Long -> cost(campaign, minor).copy(category = ExpenseCategory.LABOR, origin = ExpenseOrigin.DAY_LABOUR) }
        val overview = FarmOverview.of("2026/27", listOf(estacas, cerro), listOf(estacasNow, cerroNow), emptyList(),
            listOf(labour(estacasNow, Long.MAX_VALUE), labour(estacasNow, 1), labour(cerroNow, 6_500)))
        assertNull(overview.costs.single().labourMinor)
        assertNull(overview.costs.single().amountMinor)
    }

    @Test fun nothingWeighedIsUnknownNotZero() {
        val overview = FarmOverview.of("2026/27", listOf(estacas), listOf(estacasNow), emptyList(), emptyList())
        assertNull(overview.delivery.fatYield)
        assertNull(overview.costPerKgMinor)
        assertNull(overview.sharePercent(overview.farms.single()))
        assertEquals(emptyList<CurrencyTotal>(), overview.costs)
    }

    /** #359 follow-up: costs linked to no campaign are counted apart and only in their season. */
    @Test fun generalCostsStayOutOfTheRecollectionCostAndAddToTheTotal() {
        val general = { farm: Farm, minor: Long, date: LocalDate -> Expense(
            id = UUID.randomUUID(), workspaceId = workspace, expenseDate = date, concept = "Poda",
            category = ExpenseCategory.LABOR, amountMinor = minor, currency = "EUR", status = ExpenseStatus.POSTED,
            origin = ExpenseOrigin.MANUAL, farmId = farm.id,
        ) }
        val expenses = listOf(
            cost(estacasNow, 144_000),                                   // recollection 1.440 €
            general(estacas, 52_000, LocalDate.of(2027, 2, 10)),         // in season
            general(cerro, 66_500, LocalDate.of(2026, 9, 1)),            // first day of the season
            general(estacas, 99_000, LocalDate.of(2026, 8, 31)),         // previous season: out
            general(estacas, 7_000, LocalDate.of(2027, 1, 5)).copy(status = ExpenseStatus.DRAFT), // draft: out
            // Codex #402: an unassigned expense of another workspace in the same store: out.
            general(estacas, 50_000, LocalDate.of(2027, 1, 5)).copy(farmId = null, workspaceId = UUID.randomUUID()),
        )
        val overview = FarmOverview.of("2026/27", listOf(estacas, cerro), listOf(estacasNow, cerroNow),
            listOf(delivery(estacasNow, 5_700_000, null)), expenses)

        assertEquals(144_000L, overview.costs.single().amountMinor)
        assertEquals(25L, overview.costPerKgMinor) // 1.440 € / 5.700 kg, general costs never inside
        assertEquals(118_500L, overview.generalCosts.single().amountMinor)
        assertEquals(262_500L, overview.totalCosts.single().amountMinor)
        assertEquals(46L, overview.totalCostPerKgMinor) // 2.625 € / 5.700 kg
    }

    @Test fun generalCostsInAnotherCurrencyGiveNoTotalCostPerKg() {
        val usd = Expense(
            id = UUID.randomUUID(), workspaceId = workspace, expenseDate = LocalDate.of(2027, 3, 1), concept = "Riego",
            category = ExpenseCategory.FUEL, amountMinor = 5_000, currency = "USD", status = ExpenseStatus.POSTED,
            origin = ExpenseOrigin.MANUAL, farmId = estacas.id,
        )
        val overview = FarmOverview.of("2026/27", listOf(estacas), listOf(estacasNow),
            listOf(delivery(estacasNow, 1_000_000, null)), listOf(cost(estacasNow, 10_000), usd))
        assertEquals(listOf("EUR", "USD"), overview.totalCosts.map { it.currency })
        assertEquals(10L, overview.costPerKgMinor)
        assertNull(overview.totalCostPerKgMinor)
    }

    @Test fun withoutKilosTheTotalShowsButNotPerKg() {
        val overview = FarmOverview.of("2026/27", listOf(estacas), listOf(estacasNow), emptyList(), listOf(cost(estacasNow, 10_000)))
        assertEquals(10_000L, overview.totalCosts.single().amountMinor)
        assertNull(overview.totalCostPerKgMinor)
    }

    private fun farm(name: String) = Farm(UUID.randomUUID(), workspace, name, null, null, null, null, null, 1, null, null, null, 1)

    private fun campaign(farm: Farm, name: String, start: LocalDate) = Campaign(
        id = UUID.randomUUID(), workspaceId = workspace, farmId = farm.id, name = name, startDate = start,
        endDate = null, status = CampaignStatus.HARVEST, notes = null, snapshots = emptyList(), version = 1,
    )

    private fun delivery(campaign: Campaign, grams: Long, fat: Int?): Delivery {
        val id = UUID.randomUUID()
        val date = campaign.startDate.plusDays(30)
        return Delivery(
            id = id, workspaceId = workspace, farmId = campaign.farmId, campaignId = campaign.id, deliveryDate = date,
            destinationOrganizationId = null, destinationName = "Coop", netGrams = grams, grossGrams = null,
            tareGrams = null, deliveryNumber = null, ticketNumber = null, source = DeliverySource.MANUAL,
            shares = emptyList(), notes = null, version = 1,
            analysis = fat?.let { YieldAnalysis(UUID.randomUUID(), id, date, it, null, null, 1) },
        )
    }

    private fun cost(campaign: Campaign, minor: Long, status: ExpenseStatus = ExpenseStatus.POSTED) = Expense(
        id = UUID.randomUUID(), workspaceId = workspace, expenseDate = campaign.startDate.plusDays(30), concept = "Gasto",
        category = ExpenseCategory.FUEL, amountMinor = minor, currency = "EUR", status = status,
        origin = ExpenseOrigin.MANUAL, farmId = campaign.farmId, campaignId = campaign.id,
    )
}
