package com.isivoltpro.maginaolivo.domain.report

import com.isivoltpro.maginaolivo.data.local.model.CampaignStatus
import com.isivoltpro.maginaolivo.domain.campaign.Campaign
import com.isivoltpro.maginaolivo.domain.delivery.Delivery
import com.isivoltpro.maginaolivo.domain.delivery.DeliverySource
import com.isivoltpro.maginaolivo.domain.delivery.PesadaOrigin
import com.isivoltpro.maginaolivo.domain.delivery.YieldAnalysis
import com.isivoltpro.maginaolivo.domain.expense.Expense
import com.isivoltpro.maginaolivo.domain.expense.ExpenseCategory
import com.isivoltpro.maginaolivo.domain.expense.ExpenseOrigin
import com.isivoltpro.maginaolivo.domain.expense.ExpenseStatus
import com.isivoltpro.maginaolivo.domain.farm.Farm
import com.isivoltpro.maginaolivo.domain.harvest.Harvest
import com.isivoltpro.maginaolivo.feature.campaigns.CampaignCardSummary
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Gate 25: the report reconciles with the canonical totals by construction — it reads the same
 * aggregates the campaign card reads, and never computes money of its own.
 */
class CampaignReportTest {
    private val workspace = UUID.randomUUID()
    private val farmId = UUID.randomUUID()
    private val campaignId = UUID.randomUUID()
    private val otherCampaign = UUID.randomUUID()
    private val start = LocalDate.of(2026, 9, 28)
    private val today = LocalDate.of(2026, 10, 10)

    @Test fun theReportSaysTheSameKilosYieldAndCostsAsTheCampaignCard() {
        val deliveries = listOf(
            delivery(campaignId, 1_850_000, 2_080, "DEMO-001", LocalDate.of(2026, 9, 28)),
            delivery(campaignId, 2_120_000, 2_160, "DEMO-002", LocalDate.of(2026, 10, 1)),
            delivery(otherCampaign, 9_000_000, 2_500, "AJENA", LocalDate.of(2026, 10, 1)),
        )
        val harvests = listOf(harvest(campaignId, LocalDate.of(2026, 9, 28), 1_850_000), harvest(otherCampaign, start, 10))
        val expenses = listOf(
            labour(campaignId, 24_500), labour(campaignId, 26_000),
            equipment(campaignId, 18_000),
            other(campaignId, 14_000),
            labour(campaignId, 5_000).copy(status = ExpenseStatus.DRAFT), // never counted
            labour(otherCampaign, 99_000),
        )
        val report = CampaignReport.of(campaign(), farm(), deliveries, harvests, expenses, today)
        val card = CampaignCardSummary.of(campaignId, deliveries, harvests, expenses)

        assertEquals("Campaña de recogida 2026/27", report.title)
        assertEquals("Finca Gate 25", report.farmName)
        assertEquals("Huelma · Jaén", report.place)
        assertEquals("28-09-2026 → en curso", report.period)

        val production = report.sections.single { it.title == "Producción" }
        // The card says 3.970 kg over two Pesadas and one day: so does the report, word for word.
        assertEquals(
            com.isivoltpro.maginaolivo.domain.harvest.Weight.format(card.deliveredGrams!!),
            production.lines.single { it.label == "Kilos pesados" }.value,
        )
        assertEquals(card.deliveryCount.toString(), production.lines.single { it.label == "Pesadas" }.value)
        assertEquals(card.dayCount.toString(), production.lines.single { it.label == "Días de recogida" }.value)
        assertEquals(
            com.isivoltpro.maginaolivo.domain.delivery.Percent.format(card.yieldHundredths!!),
            production.lines.single { it.label == "Rendimiento graso" }.value,
        )

        val costs = report.sections.single { it.title == "Costes de recogida" }
        // Jornales match the card's posted labour; the draft is in neither.
        assertEquals(
            com.isivoltpro.maginaolivo.domain.expense.Money.format(card.labour.single().second!!, "EUR"),
            costs.lines.single { it.label == "Jornales" }.value,
        )
        assertEquals(money(18_000), costs.lines.single { it.label == "Maquinaria" }.value)
        assertEquals(money(14_000), costs.lines.single { it.label == "Otros gastos" }.value)
        // 505 + 180 + 140 = 825,00 EUR, and 825 / 3.970 kg = 0,208 EUR/kg (#486: thousandths).
        assertEquals(money(82_500), costs.lines.single { it.label == "Total de recogida" }.value)
        assertEquals(
            com.isivoltpro.maginaolivo.domain.expense.CostPerKg.format(208, "EUR"),
            costs.lines.single { it.label == "Coste por kilo" }.value,
        )

        // Another campaign's Pesada, day and jornal are nowhere in the document.
        assertEquals(2, report.sections.single { it.title == "Pesadas" }.lines.size)
        assertEquals(1, report.sections.single { it.title == "Días de recogida" }.lines.size)
        assertTrue(report.warnings.isEmpty())
    }

    /** What is not known is said, never shown as zero. */
    @Test fun anEmptyCampaignSaysWhatIsMissingInsteadOfZeros() {
        val report = CampaignReport.of(campaign(), farm(), emptyList(), emptyList(), emptyList(), today)
        val production = report.sections.single { it.title == "Producción" }
        assertEquals("Sin pesadas registradas", production.lines.single { it.label == "Kilos pesados" }.value)
        assertEquals("Sin análisis", production.lines.single { it.label == "Rendimiento graso" }.value)
        assertEquals(listOf("La campaña aún no tiene pesadas."), report.warnings)
        // With no posted expense there is no cost section to read at all.
        assertTrue(report.sections.none { it.title.startsWith("Costes") })
    }

    /** Codex #404: a yield measured on part of the kilos is said in the document too. */
    @Test fun aPartialYieldCarriesItsCoverageIntoTheReport() {
        val deliveries = listOf(
            delivery(campaignId, 1_000_000, 2_000, "A", start),
            delivery(campaignId, 3_000_000, null, "B", start),
        )
        val report = CampaignReport.of(campaign(), farm(), deliveries, emptyList(), emptyList(), today)
        assertEquals(
            listOf("El rendimiento está medido sobre el 25 % de los kilos pesados."),
            report.warnings,
        )
    }

    /** A closed campaign prints its own period, and the Farm's name may be gone: the snapshot answers. */
    @Test fun aClosedCampaignPrintsItsPeriodAndKeepsItsFarmNameWithoutTheFarm() {
        val closed = campaign().copy(status = CampaignStatus.CLOSED, endDate = LocalDate.of(2026, 12, 15))
        val report = CampaignReport.of(closed, null, emptyList(), emptyList(), emptyList(), today)
        assertEquals("28-09-2026 → 15-12-2026", report.period)
        assertEquals("Finca", report.farmName)
        assertEquals(null, report.place)
    }

    private fun money(minor: Long) = com.isivoltpro.maginaolivo.domain.expense.Money.format(minor, "EUR")

    private fun campaign() = Campaign(
        id = campaignId, workspaceId = workspace, farmId = farmId, name = "Campaña de recogida 2026/27",
        startDate = start, endDate = null, status = CampaignStatus.ACTIVE, notes = null,
        snapshots = emptyList(), version = 1,
    )

    private fun farm() = Farm(
        id = farmId, workspaceId = workspace, name = "Finca Gate 25", description = null,
        municipality = "Huelma", province = "Jaén", notes = null, coverDocumentId = null,
        parcelCount = 3, totalAreaM2 = 32_000.0, activeCampaignName = null, archivedAt = null, version = 1,
    )

    private fun harvest(campaign: UUID, date: LocalDate, grams: Long) = Harvest(
        id = UUID.randomUUID(), workspaceId = workspace, farmId = farmId, campaignId = campaign,
        harvestDate = date, totalGrams = grams, shares = emptyList(), collectionMethod = null,
        workerCount = null, machineryText = null, notes = null, version = 1,
    )

    private fun delivery(campaign: UUID, grams: Long, fat: Int?, ticket: String, date: LocalDate): Delivery {
        val id = UUID.randomUUID()
        return Delivery(
            id = id, workspaceId = workspace, farmId = farmId, campaignId = campaign, deliveryDate = date,
            destinationOrganizationId = null, destinationName = "Cooperativa Demo", netGrams = grams,
            grossGrams = null, tareGrams = null, deliveryNumber = null, ticketNumber = ticket,
            source = DeliverySource.MANUAL, shares = emptyList(), notes = null, version = 1,
            analysis = fat?.let { YieldAnalysis(UUID.randomUUID(), id, date, it, null, null, 1) },
            origin = PesadaOrigin.TREE,
        )
    }

    private fun labour(campaign: UUID, minor: Long) = Expense(
        id = UUID.randomUUID(), workspaceId = workspace, expenseDate = start, concept = "Jornales",
        category = ExpenseCategory.LABOR, amountMinor = minor, currency = "EUR", status = ExpenseStatus.POSTED,
        origin = ExpenseOrigin.DAY_LABOUR, farmId = farmId, campaignId = campaign,
    )

    private fun equipment(campaign: UUID, minor: Long) = labour(campaign, minor).copy(
        concept = "Maquinaria", category = ExpenseCategory.MACHINERY, origin = ExpenseOrigin.DAY_EQUIPMENT,
    )

    private fun other(campaign: UUID, minor: Long) = labour(campaign, minor).copy(
        concept = "Gasóleo", category = ExpenseCategory.FUEL, origin = ExpenseOrigin.MANUAL,
    )
}
