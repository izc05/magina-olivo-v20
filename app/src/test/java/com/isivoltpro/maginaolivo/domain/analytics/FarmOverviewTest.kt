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

    @Test fun twoCurrenciesKeepTwoTotalsAndNoGlobalCostPerKg() {
        val overview = FarmOverview.of("2026/27", listOf(estacas, cerro), listOf(estacasNow, cerroNow),
            listOf(delivery(estacasNow, 1_000_000, null), delivery(cerroNow, 1_000_000, null)),
            listOf(cost(estacasNow, 10_000), cost(cerroNow, 10_000).copy(currency = "USD")))
        assertEquals(listOf("EUR", "USD"), overview.costs.map { it.currency })
        assertNull(overview.costPerKgMinor)
    }

    @Test fun nothingWeighedIsUnknownNotZero() {
        val overview = FarmOverview.of("2026/27", listOf(estacas), listOf(estacasNow), emptyList(), emptyList())
        assertNull(overview.delivery.fatYield)
        assertNull(overview.costPerKgMinor)
        assertNull(overview.sharePercent(overview.farms.single()))
        assertEquals(emptyList<CurrencyTotal>(), overview.costs)
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
