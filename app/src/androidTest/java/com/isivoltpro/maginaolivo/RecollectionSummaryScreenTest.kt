package com.isivoltpro.maginaolivo

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.isivoltpro.maginaolivo.data.local.model.CampaignStatus
import com.isivoltpro.maginaolivo.domain.campaign.Campaign
import com.isivoltpro.maginaolivo.domain.delivery.Delivery
import com.isivoltpro.maginaolivo.domain.delivery.DeliverySource
import com.isivoltpro.maginaolivo.domain.delivery.Percent
import com.isivoltpro.maginaolivo.domain.delivery.YieldAnalysis
import com.isivoltpro.maginaolivo.domain.harvest.Weight
import com.isivoltpro.maginaolivo.domain.notebook.CampaignNotebook
import com.isivoltpro.maginaolivo.feature.notebook.CampaignView
import com.isivoltpro.maginaolivo.feature.notebook.NotebookActions
import com.isivoltpro.maginaolivo.feature.notebook.NotebookUiState
import com.isivoltpro.maginaolivo.ui.theme.MaginaOlivoTheme
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * #254 (254-B), CR-011 §10–11 — the Campaña view: «+ Nueva pesada» first while the Campaign
 * runs, then one summary read from the ledgers (no second recolección grid repeating it).
 */
class RecollectionSummaryScreenTest {
    @Test fun economicCardsOpenCanonicalDetailsWithoutDuplicateMoneyOrAnnualWork() {
        var labour = 0
        var expenses = 0
        show(CampaignNotebook.project(campaign, emptyList(), emptyList(), emptyList(), emptyList()),
            NotebookActions(onLabour = { labour++ }, onExpenses = { expenses++ }))
        composeRule.onNodeWithTag("notebook-summary-labour").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(1, labour) }
        composeRule.onNodeWithTag("notebook-summary-other").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(1, expenses) }
        assertEquals(0, composeRule.onAllNodesWithText("Trabajos").fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithText("Gastos de la campaña").fetchSemanticsNodes().size)
        assertEquals(1, composeRule.onAllNodesWithTag("dashboard-cost").fetchSemanticsNodes().size)
    }
    @get:Rule val composeRule = createComposeRule()

    private val workspace = UUID.randomUUID()
    private val farm = UUID.randomUUID()
    private val campaign = Campaign(
        id = UUID.randomUUID(), workspaceId = workspace, farmId = farm, name = "2026/27",
        startDate = LocalDate.of(2026, 9, 1), endDate = null, status = CampaignStatus.ACTIVE,
        notes = null, snapshots = emptyList(), version = 1,
    )

    @Test fun aRunningCampaignOffersNewPesadaFirstAndOneSummary() {
        val deliveries = listOf(
            delivery(2_000_000, LocalDate.of(2026, 11, 20), yieldHundredths = 2_000),
            delivery(1_000_000, LocalDate.of(2026, 11, 21), yieldHundredths = 2_300),
        )
        val notebook = CampaignNotebook.project(campaign, emptyList(), emptyList(), deliveries, emptyList())
        var newPesada = 0
        show(notebook, NotebookActions(onDeliveries = { newPesada++ }))

        composeRule.onNodeWithTag("notebook-open-deliveries").performClick()
        composeRule.runOnIdle { assertEquals(1, newPesada) }
        composeRule.onNodeWithTag("notebook-summary").performScrollTo()
        assertTrue(composeRule.onAllNodesWithText(Weight.format(3_000_000)).fetchSemanticsNodes().isNotEmpty())
        // (2.000 kg × 20 % + 1.000 kg × 23 %) / 3.000 kg = 21 %: weighted by kilos, not averaged.
        assertTrue(composeRule.onAllNodesWithText(Percent.format(2_100)).fetchSemanticsNodes().isNotEmpty())
        // The old recolección grid is gone: the figures are shown once.
        assertEquals(0, composeRule.onAllNodesWithTag("notebook-recollection-summary").fetchSemanticsNodes().size)
    }

    @Test fun aClosedCampaignTakesNoNewPesada() {
        val closed = campaign.copy(status = CampaignStatus.CLOSED, endDate = LocalDate.of(2026, 12, 20))
        show(CampaignNotebook.project(closed, emptyList(), emptyList(), emptyList(), emptyList()), NotebookActions())
        assertEquals(0, composeRule.onAllNodesWithTag("notebook-open-deliveries").fetchSemanticsNodes().size)
        composeRule.onNodeWithTag("notebook-summary").performScrollTo()
    }

    private fun show(notebook: CampaignNotebook, actions: NotebookActions) {
        composeRule.setContent {
            MaginaOlivoTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    CampaignView(notebook, NotebookUiState(isLoading = false, notebook = notebook), actions)
                }
            }
        }
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
}
