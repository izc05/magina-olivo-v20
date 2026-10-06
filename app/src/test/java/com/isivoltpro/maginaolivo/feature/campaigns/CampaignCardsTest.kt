package com.isivoltpro.maginaolivo.feature.campaigns

import com.isivoltpro.maginaolivo.domain.delivery.Delivery
import com.isivoltpro.maginaolivo.domain.delivery.DeliverySource
import com.isivoltpro.maginaolivo.domain.delivery.YieldAnalysis
import com.isivoltpro.maginaolivo.domain.expense.Expense
import com.isivoltpro.maginaolivo.domain.expense.ExpenseCategory
import com.isivoltpro.maginaolivo.domain.expense.ExpenseOrigin
import com.isivoltpro.maginaolivo.domain.expense.ExpenseStatus
import com.isivoltpro.maginaolivo.domain.expense.Money
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Test

/** #246: a campaign card's figures come from its own Pesadas, days and posted jornales. */
class CampaignCardsTest {
    private val workspace = UUID.randomUUID()
    private val farm = UUID.randomUUID()
    private val campaign = UUID.randomUUID()
    private val other = UUID.randomUUID()
    private val date = LocalDate.of(2026, 10, 1)

    @Test fun figuresAreThisCampaignsOnly() {
        val deliveries = listOf(delivery(campaign, 1_850_000, 2_080), delivery(campaign, 2_120_000, 2_160), delivery(other, 9_000_000, null))
        val expenses = listOf(
            labour(campaign, 39_000), labour(campaign, 26_000),
            labour(campaign, 5_000).copy(status = ExpenseStatus.DRAFT), // drafts never count
            labour(other, 99_000),
            labour(campaign, 14_000).copy(category = ExpenseCategory.FUEL, origin = ExpenseOrigin.MANUAL, concept = "Gasóleo"), // not labour
        )
        val summary = CampaignCardSummary.of(campaign, deliveries, emptyList(), expenses)

        assertEquals(3_970_000L, summary.deliveredGrams)
        assertEquals(2, summary.deliveryCount)
        assertEquals(listOf("EUR" to 65_000L), summary.labour)
        assertEquals(
            listOf(
                com.isivoltpro.maginaolivo.domain.harvest.Weight.format(3_970_000),
                "2 pesadas",
                "Jornales ${Money.format(65_000, "EUR")}",
                // #449: the draft jornal is never summed, but the card says a cost is pending.
                "Costes sin confirmar",
                "Rend. ${com.isivoltpro.maginaolivo.domain.delivery.Percent.format(summary.yieldHundredths!!)}",
            ),
            campaignFacts(summary).map { it.text },
        )
    }

    /** Codex #404: a yield measured on part of the kilos says so. */
    @Test fun aPartialYieldShowsItsCoverage() {
        val deliveries = listOf(delivery(campaign, 1_000_000, 2_000), delivery(campaign, 3_000_000, null))
        val summary = CampaignCardSummary.of(campaign, deliveries, emptyList(), emptyList())
        assertEquals(25, summary.yieldCoveragePercent)
        assertEquals(true, campaignFacts(summary).last().text.endsWith("· 25 % analizado"))
    }

    /** #449: a machine of this campaign without a price is said on the card; another campaign's is not. */
    @Test fun anUnconfirmedCostOfThisCampaignIsSaidOnTheCard() {
        val day = harvest(campaign)
        val elsewhere = harvest(other)
        val unpriced = { harvestId: UUID ->
            com.isivoltpro.maginaolivo.domain.equipment.EquipmentLine(UUID.randomUUID(), harvestId,
                com.isivoltpro.maginaolivo.domain.equipment.EquipmentType.TRAILER, null, 1, null, 1)
        }
        val complete = CampaignCardSummary.of(campaign, emptyList(), listOf(day, elsewhere), emptyList(), equipment = listOf(unpriced(elsewhere.id)))
        assertEquals(true, complete.costComplete)
        assertEquals(false, campaignFacts(complete).any { it.text == "Costes sin confirmar" })

        val pending = CampaignCardSummary.of(campaign, emptyList(), listOf(day, elsewhere), emptyList(), equipment = listOf(unpriced(day.id)))
        assertEquals(false, pending.costComplete)
        assertEquals(true, campaignFacts(pending).any { it.text == "Costes sin confirmar" })
    }

    private fun harvest(campaignId: UUID) = com.isivoltpro.maginaolivo.domain.harvest.Harvest(
        id = UUID.randomUUID(), workspaceId = workspace, farmId = farm, campaignId = campaignId, harvestDate = date,
        totalGrams = 0, shares = emptyList(), collectionMethod = null, workerCount = null, machineryText = null,
        notes = null, version = 1,
    )

    @Test fun nothingRecordedSaysSoWithoutZeros() {
        val facts = campaignFacts(CampaignCardSummary(0, 0, 0, emptyList(), null))
        assertEquals(listOf("Sin pesadas"), facts.map { it.text })
    }

    @Test fun oneOfEachIsSingular() {
        val facts = campaignFacts(CampaignCardSummary(1_000_000, 1, 1, emptyList(), null)).map { it.text }
        assertEquals(listOf("1 pesada", "1 día"), facts.drop(1))
    }

    private fun delivery(campaignId: UUID, grams: Long, fat: Int?): Delivery {
        val id = UUID.randomUUID()
        return Delivery(
            id = id, workspaceId = workspace, farmId = farm, campaignId = campaignId, deliveryDate = date,
            destinationOrganizationId = null, destinationName = "Coop", netGrams = grams, grossGrams = null,
            tareGrams = null, deliveryNumber = null, ticketNumber = null, source = DeliverySource.MANUAL,
            shares = emptyList(), notes = null, version = 1,
            analysis = fat?.let { YieldAnalysis(UUID.randomUUID(), id, date, it, null, null, 1) },
        )
    }

    private fun labour(campaignId: UUID, minor: Long) = Expense(
        id = UUID.randomUUID(), workspaceId = workspace, expenseDate = date, concept = "Jornales",
        category = ExpenseCategory.LABOR, amountMinor = minor, currency = "EUR", status = ExpenseStatus.POSTED,
        origin = ExpenseOrigin.DAY_LABOUR, farmId = farm, campaignId = campaignId,
    )
}
