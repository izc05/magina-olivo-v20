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
                "Rend. ${com.isivoltpro.maginaolivo.domain.delivery.Percent.format(summary.yieldHundredths!!)}",
            ),
            campaignFacts(summary).map { it.text },
        )
    }

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
