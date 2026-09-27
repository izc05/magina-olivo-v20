package com.isivoltpro.maginaolivo

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.isivoltpro.maginaolivo.data.local.model.CampaignStatus
import com.isivoltpro.maginaolivo.domain.campaign.Campaign
import com.isivoltpro.maginaolivo.domain.delivery.Delivery
import com.isivoltpro.maginaolivo.domain.delivery.DeliverySource
import com.isivoltpro.maginaolivo.domain.delivery.Percent
import com.isivoltpro.maginaolivo.domain.delivery.YieldAnalysis
import com.isivoltpro.maginaolivo.domain.expense.Expense
import com.isivoltpro.maginaolivo.domain.expense.ExpenseCategory
import com.isivoltpro.maginaolivo.domain.expense.ExpenseOrigin
import com.isivoltpro.maginaolivo.domain.expense.ExpenseStatus
import com.isivoltpro.maginaolivo.domain.expense.Money
import com.isivoltpro.maginaolivo.domain.harvest.Weight
import com.isivoltpro.maginaolivo.domain.notebook.CampaignNotebook
import com.isivoltpro.maginaolivo.feature.notebook.NotebookActions
import com.isivoltpro.maginaolivo.feature.notebook.RecollectionTab
import com.isivoltpro.maginaolivo.ui.theme.MaginaOlivoTheme
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** #254 (254-B) — the recolección at a glance: kg, pesadas, weighted yield and costs, 2×2. */
class RecollectionSummaryScreenTest {
    @get:Rule val composeRule = createComposeRule()

    private val workspace = UUID.randomUUID()
    private val farm = UUID.randomUUID()
    private val campaign = Campaign(
        id = UUID.randomUUID(), workspaceId = workspace, farmId = farm, name = "2026/27",
        startDate = LocalDate.of(2026, 9, 1), endDate = null, status = CampaignStatus.HARVEST,
        notes = null, snapshots = emptyList(), version = 1,
    )

    @Test fun fourFiguresFromTheirLedgersAndNewPesadaFirst() {
        val deliveries = listOf(
            delivery(2_000_000, LocalDate.of(2026, 11, 20), yieldHundredths = 2_000),
            delivery(1_000_000, LocalDate.of(2026, 11, 21), yieldHundredths = 2_300),
        )
        val expenses = listOf(
            expense(12_000, ExpenseCategory.HARVEST, ExpenseStatus.POSTED),
            expense(9_999, ExpenseCategory.TRANSPORT, ExpenseStatus.DRAFT),
            expense(4_500, ExpenseCategory.FUEL, ExpenseStatus.POSTED), // not a recolección cost
        )
        val notebook = CampaignNotebook.project(campaign, emptyList(), emptyList(), deliveries, expenses)
        var newPesada = 0
        composeRule.setContent {
            MaginaOlivoTheme {
                Column { RecollectionTab(notebook, NotebookActions(onDeliveries = { newPesada++ })) }
            }
        }

        composeRule.onNodeWithTag("notebook-recollection-summary").assertIsDisplayed()
        composeRule.onNodeWithText(Weight.format(3_000_000)).assertIsDisplayed()
        composeRule.onNodeWithText("2").assertIsDisplayed()
        // (2.000 kg × 20 % + 1.000 kg × 23 %) / 3.000 kg = 21 %: weighted by kilos, not averaged.
        composeRule.onNodeWithText(Percent.format(2_100)).assertIsDisplayed()
        composeRule.onNodeWithText(Money.format(12_000)).assertIsDisplayed()
        composeRule.onNodeWithText("1 en borrador sin contar").assertIsDisplayed()

        composeRule.onNodeWithTag("notebook-open-deliveries").performClick()
        assertEquals(1, newPesada)
    }

    @Test fun anEmptyCampaignShowsDashesNotZeros() {
        val notebook = CampaignNotebook.project(campaign, emptyList(), emptyList(), emptyList(), emptyList())
        composeRule.setContent {
            MaginaOlivoTheme { Column { RecollectionTab(notebook, NotebookActions()) } }
        }
        composeRule.onNodeWithText("Rendimiento medio").assertIsDisplayed()
        composeRule.onNodeWithText("Gastos").assertIsDisplayed()
        composeRule.onNodeWithTag("notebook-open-deliveries").assertIsDisplayed()
    }

    private fun delivery(grams: Long, date: LocalDate, yieldHundredths: Int): Delivery {
        val id = UUID.randomUUID()
        return Delivery(
            id = id, workspaceId = workspace, farmId = farm, campaignId = campaign.id, deliveryDate = date,
            destinationOrganizationId = null, destinationName = "Coop. Bedmarense", netGrams = grams, grossGrams = null,
            tareGrams = null, deliveryNumber = null, ticketNumber = null, source = DeliverySource.MANUAL,
            shares = emptyList(), notes = null, version = 1,
            analysis = YieldAnalysis(UUID.randomUUID(), id, date.plusDays(3), yieldHundredths, null, null, 1),
        )
    }

    private fun expense(minor: Long, category: ExpenseCategory, status: ExpenseStatus) = Expense(
        id = UUID.randomUUID(), workspaceId = workspace, expenseDate = LocalDate.of(2026, 11, 20), concept = category.name,
        category = category, amountMinor = minor, currency = "EUR", status = status, origin = ExpenseOrigin.MANUAL,
        farmId = farm, campaignId = campaign.id,
    )
}
