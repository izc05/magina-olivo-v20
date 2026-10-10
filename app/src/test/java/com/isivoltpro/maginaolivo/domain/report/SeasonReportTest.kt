package com.isivoltpro.maginaolivo.domain.report

import com.isivoltpro.maginaolivo.data.local.model.CampaignStatus
import com.isivoltpro.maginaolivo.domain.analytics.FarmOverview
import com.isivoltpro.maginaolivo.domain.campaign.Campaign
import com.isivoltpro.maginaolivo.domain.delivery.Delivery
import com.isivoltpro.maginaolivo.domain.delivery.DeliverySource
import com.isivoltpro.maginaolivo.domain.delivery.YieldAnalysis
import com.isivoltpro.maginaolivo.domain.expense.Expense
import com.isivoltpro.maginaolivo.domain.expense.ExpenseCategory
import com.isivoltpro.maginaolivo.domain.expense.ExpenseOrigin
import com.isivoltpro.maginaolivo.domain.expense.ExpenseStatus
import com.isivoltpro.maginaolivo.domain.farm.Farm
import com.isivoltpro.maginaolivo.feature.farms.overviewCost
import com.isivoltpro.maginaolivo.feature.farms.overviewCostPerKg
import com.isivoltpro.maginaolivo.feature.farms.overviewKilos
import com.isivoltpro.maginaolivo.feature.farms.overviewTotalCostPerKg
import com.isivoltpro.maginaolivo.feature.farms.overviewYield
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Gate 25: the season's report says exactly what «Resumen de la explotación» shows — the test
 * compares it against the screen's own formatters over the same [FarmOverview].
 */
class SeasonReportTest {
    private val workspace = UUID.randomUUID()
    private val farmA = UUID.randomUUID()
    private val farmB = UUID.randomUUID()
    private val start = LocalDate.of(2026, 9, 28)
    private val today = LocalDate.of(2026, 10, 10)

    @Test fun theDocumentSaysTheSameFiguresAsTheHoldingSummary() {
        val campaigns = listOf(campaign(farmA, "A 2026/27"), campaign(farmB, "B 2026/27"))
        val deliveries = listOf(
            delivery(campaigns[0].id, farmA, 3_000_000, 2_000),
            delivery(campaigns[1].id, farmB, 1_000_000, 2_400),
        )
        val expenses = listOf(
            posted(campaigns[0].id, farmA, 60_000),
            posted(campaigns[1].id, farmB, 22_000),
            posted(null, farmA, 18_500),                               // general: apart
            posted(campaigns[0].id, farmA, 9_000).copy(status = ExpenseStatus.DRAFT), // never counted
        )
        val overview = FarmOverview.of("2026/27", listOf(farm(farmA, "Los Olivos"), farm(farmB, "La Loma")), campaigns, deliveries, expenses)
        val report = SeasonReport.of(overview, today)

        val production = report.sections.single { it.title == "Producción de la temporada" }
        assertEquals(overviewKilos(overview.delivery), production.lines.single { it.label == "Kilos pesados" }.value)
        assertEquals(overviewYield(overview.delivery), production.lines.single { it.label == "Rendimiento graso" }.value)
        assertEquals("2", production.lines.single { it.label == "Pesadas" }.value)
        assertEquals("2 de 2", production.lines.single { it.label == "Fincas con campaña" }.value)

        val costs = report.sections.single { it.title == "Costes de la temporada" }
        assertEquals(overviewCost(overview.costs), costs.lines.single { it.label == "Coste de recogida" }.value)
        assertEquals(
            overviewCostPerKg(overview.costPerKgMilli, overview.costs, overview.costComplete),
            costs.lines.single { it.label == "Coste de recogida por kilo" }.value,
        )
        assertEquals(overviewCost(overview.generalCosts), costs.lines.single { it.label == "Gastos generales (sin campaña)" }.value)
        assertEquals(overviewCost(overview.totalCosts), costs.lines.single { it.label == "Coste total" }.value)
        assertEquals(overviewTotalCostPerKg(overview), costs.lines.single { it.label == "Coste total por kilo" }.value)

        // Per farm, biggest first, each with its share of the season's kilos.
        val perFarm = report.sections.single { it.title == "Por finca" }
        assertEquals(listOf("Los Olivos", "La Loma"), perFarm.lines.map { it.label })
        assertTrue(perFarm.lines.first().value.contains("75 % del total"))

        val period = report.sections.single { it.title == "Periodo" }
        assertEquals("01-09-2026", period.lines.single { it.label == "Desde" }.value)
        assertEquals("31-08-2027", period.lines.single { it.label == "Hasta" }.value)

        val document = report.document()
        assertEquals("Resumen de la temporada 2026/27", document.title)
        assertTrue(document.period.startsWith("Temporada 2026/27"))
        assertTrue(document.footer.contains("No es un certificado"))
        assertTrue(report.warnings.isEmpty())
    }

    /** A season nobody worked says so, with dashes instead of zeros. */
    @Test fun aSeasonWithoutCampaignsSaysSoAndShowsNoZeros() {
        val overview = FarmOverview.of("2025/26", listOf(farm(farmA, "Los Olivos")), emptyList(), emptyList(), emptyList())
        val report = SeasonReport.of(overview, today)
        val production = report.sections.single { it.title == "Producción de la temporada" }
        assertEquals("—", production.lines.single { it.label == "Kilos pesados" }.value)
        assertEquals("—", production.lines.single { it.label == "Rendimiento graso" }.value)
        assertEquals("0 de 1", production.lines.single { it.label == "Fincas con campaña" }.value)
        assertEquals(
            listOf(
                "Ninguna finca tuvo campaña en esta temporada.",
                "Sin campaña en la temporada: Los Olivos.",
            ),
            report.warnings,
        )
        assertTrue(report.sections.none { it.title == "Por finca" })
    }

    /** #449: unconfirmed costs travel into the document instead of being hidden. */
    @Test fun unconfirmedCostsAreSaidInTheDocument() {
        val campaign = campaign(farmA, "A 2026/27")
        val deliveries = listOf(delivery(campaign.id, farmA, 1_000_000, 2_000))
        val overview = FarmOverview.of(
            "2026/27", listOf(farm(farmA, "Los Olivos")), listOf(campaign), deliveries,
            listOf(posted(campaign.id, farmA, 10_000)),
            incompleteCampaigns = setOf(campaign.id),
        )
        val report = SeasonReport.of(overview, today)
        assertTrue(report.warnings.any { it.contains("costes sin confirmar") })
        val costs = report.sections.single { it.title == "Costes de la temporada" }
        assertTrue(costs.lines.single { it.label == "Coste de recogida por kilo" }.value.endsWith("(incompleto)"))
    }

    private fun campaign(farmId: UUID, name: String) = Campaign(
        id = UUID.randomUUID(), workspaceId = workspace, farmId = farmId, name = name, startDate = start,
        endDate = null, status = CampaignStatus.ACTIVE, notes = null, snapshots = emptyList(), version = 1,
    )

    private fun farm(id: UUID, name: String) = Farm(
        id = id, workspaceId = workspace, name = name, description = null, municipality = "Bedmar",
        province = "Jaén", notes = null, coverDocumentId = null, parcelCount = 1, totalAreaM2 = 10_000.0,
        activeCampaignName = null, archivedAt = null, version = 1,
    )

    private fun delivery(campaignId: UUID, farmId: UUID, grams: Long, fat: Int?): Delivery {
        val id = UUID.randomUUID()
        return Delivery(
            id = id, workspaceId = workspace, farmId = farmId, campaignId = campaignId, deliveryDate = start,
            destinationOrganizationId = null, destinationName = "Coop", netGrams = grams, grossGrams = null,
            tareGrams = null, deliveryNumber = null, ticketNumber = null, source = DeliverySource.MANUAL,
            shares = emptyList(), notes = null, version = 1,
            analysis = fat?.let { YieldAnalysis(UUID.randomUUID(), id, start, it, null, null, 1) },
        )
    }

    private fun posted(campaignId: UUID?, farmId: UUID, minor: Long) = Expense(
        id = UUID.randomUUID(), workspaceId = workspace, expenseDate = start, concept = "Jornales",
        category = ExpenseCategory.LABOR, amountMinor = minor, currency = "EUR", status = ExpenseStatus.POSTED,
        origin = ExpenseOrigin.DAY_LABOUR, farmId = farmId, campaignId = campaignId,
    )
}
