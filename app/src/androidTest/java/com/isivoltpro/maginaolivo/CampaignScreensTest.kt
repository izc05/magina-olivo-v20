package com.isivoltpro.maginaolivo

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.isivoltpro.maginaolivo.data.local.model.CampaignStatus
import com.isivoltpro.maginaolivo.domain.campaign.Campaign
import com.isivoltpro.maginaolivo.domain.campaign.CampaignParcelOption
import com.isivoltpro.maginaolivo.domain.campaign.CampaignParcelSnapshot
import com.isivoltpro.maginaolivo.feature.campaigns.CampaignDetailScreen
import com.isivoltpro.maginaolivo.feature.campaigns.CampaignDetailUiState
import com.isivoltpro.maginaolivo.feature.campaigns.CampaignEditor
import com.isivoltpro.maginaolivo.feature.deliveries.DeliveriesScreen
import com.isivoltpro.maginaolivo.feature.deliveries.DeliveriesUiState
import com.isivoltpro.maginaolivo.ui.theme.MaginaOlivoTheme
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CampaignScreensTest {
    @get:Rule val compose = createComposeRule()

    @Test fun emptyStateAndCreateValidationAreAccessible() {
        var saved = false
        compose.setContent { MaginaOlivoTheme {
            CampaignEditor(emptyList(), null, null, false, { saved = true }, {})
        } }
        compose.onNodeWithText("Nueva campaña").assertIsDisplayed()
        compose.onNodeWithTag("campaign-name").assertIsDisplayed()
        compose.onNodeWithTag("save-campaign").performClick()
        assertTrue(saved)
    }

    @Test fun closedDetailRendersFrozenSnapshotAndUnknownMetrics() {
        compose.setContent { MaginaOlivoTheme {
            CampaignDetailScreen(CampaignDetailUiState(isLoading = false, campaign = campaign()), {}, {}, {}, {}, {}, {})
        } }
        compose.onNodeWithText("Parcela histórica").assertIsDisplayed()
        compose.onNodeWithText("Finca histórica").assertIsDisplayed()
        compose.onNodeWithText("Histórico protegido").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Aún no hay kilos pesados").assertIsDisplayed()
        compose.onNodeWithText("Aún no hay pesadas").assertIsDisplayed()
        compose.onAllNodesWithText("Sin datos").assertCountEquals(0)
        compose.onNodeWithTag("reopen-campaign").performScrollTo().performClick()
        compose.onNodeWithText("Confirmar cambio").assertIsDisplayed()
    }

    @Test fun preparationWithoutParcelsGuidesToTheSelectorInsteadOfActivation() {
        val parcelId = UUID.randomUUID()
        val draft = Campaign(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "2026/27",
            LocalDate.parse("2026-10-01"), null, CampaignStatus.PREPARATION, null, emptyList(), 1,
        )
        compose.setContent { MaginaOlivoTheme {
            CampaignDetailScreen(
                CampaignDetailUiState(
                    isLoading = false,
                    campaign = draft,
                    parcels = listOf(CampaignParcelOption(parcelId, "Parcela nueva", 2_000.0)),
                ),
                {}, {}, {}, {}, {}, {},
            )
        } }

        compose.onNodeWithText("Para activar la campaña, selecciona al menos una parcela.")
            .performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("Activar campaña").assertCountEquals(0)
        compose.onNodeWithTag("campaign-select-parcels").performScrollTo().performClick()
        compose.onNodeWithText("Editar campaña").assertIsDisplayed()
        compose.onNodeWithTag("campaign-parcel-option").assertIsDisplayed()
    }

    @Test fun preparationWithoutAnyFarmParcelExplainsTheRealPrerequisite() {
        val draft = Campaign(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "2026/27",
            LocalDate.parse("2026-10-01"), null, CampaignStatus.PREPARATION, null, emptyList(), 1,
        )
        compose.setContent { MaginaOlivoTheme {
            CampaignDetailScreen(
                CampaignDetailUiState(isLoading = false, campaign = draft, parcels = emptyList()),
                {}, {}, {}, {}, {}, {},
            )
        } }

        compose.onNodeWithText("Esta finca todavía no tiene parcelas. Añade una parcela antes de activar la campaña.")
            .performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("Activar campaña").assertCountEquals(0)
        compose.onAllNodesWithText("Seleccionar parcelas").assertCountEquals(0)
    }

    /** #365: Jornales sits under Pesadas with its summary and opens the campaign's Jornales detail. */
    @Test fun jornalesSitsUnderPesadasAndOpensItsDetail() {
        var opened = 0
        compose.setContent { MaginaOlivoTheme {
            CampaignDetailScreen(
                CampaignDetailUiState(isLoading = false, campaign = campaign()), {}, {}, {}, {}, {}, {},
                summary = com.isivoltpro.maginaolivo.feature.campaigns.CampaignSummaryUi(labourLine = "1 persona · 1 jornada · 65,00 €"),
                onLabour = { opened++ },
            )
        } }
        compose.onNodeWithTag("campaign-open-labour").performScrollTo()
        compose.onNodeWithText("1 persona · 1 jornada · 65,00 €").assertIsDisplayed()
        val deliveries = compose.onNodeWithTag("campaign-open-deliveries").fetchSemanticsNode().boundsInRoot.top
        val labour = compose.onNodeWithTag("campaign-open-labour").fetchSemanticsNode().boundsInRoot.top
        assertTrue("Jornales goes under Pesadas", labour > deliveries)
        compose.onNodeWithTag("campaign-open-labour").performClick()
        compose.runOnIdle { assertEquals(1, opened) }
    }

    @Test fun closedCampaignScopedPesadasHideTheWriteAction() {
        compose.setContent {
            MaginaOlivoTheme {
                DeliveriesScreen(
                    state = DeliveriesUiState(isLoading = false, contextsLoaded = true),
                    today = LocalDate.parse("2026-10-07"),
                    onCreate = {},
                    onProblem = {},
                    onDeliverySelected = {},
                    onTicketSelected = {},
                    allowCreate = false,
                )
            }
        }
        compose.onAllNodesWithText("+ Nueva pesada").assertCountEquals(0)
        compose.onAllNodesWithText("Pesadas").assertCountEquals(1)
    }

    @Test fun campaignPesadasCardAlwaysOpensItsListCallback() {
        var opened = 0
        var state by androidx.compose.runtime.mutableStateOf(
            CampaignDetailUiState(isLoading = false, campaign = campaign()),
        )
        compose.setContent {
            MaginaOlivoTheme {
                CampaignDetailScreen(
                    state, {}, {}, {}, {}, {}, {},
                    onDeliveries = { opened++ },
                )
            }
        }
        compose.onNodeWithTag("campaign-open-deliveries").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, opened) }

        state = state.copy(campaign = campaign().copy(status = CampaignStatus.ACTIVE, endDate = null))
        compose.onNodeWithTag("campaign-open-deliveries").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(2, opened) }
    }

    /** #450: a Campaign with EUR and GBP shows both totals; one with only GBP never says «sin gastos». */
    @Test fun campaignExpensesShowEveryCurrency() {
        fun spent(minor: Long, currency: String) = com.isivoltpro.maginaolivo.domain.expense.Expense(
            id = UUID.randomUUID(), workspaceId = UUID.randomUUID(), expenseDate = LocalDate.of(2026, 11, 20),
            concept = "Gasto", category = com.isivoltpro.maginaolivo.domain.expense.ExpenseCategory.OTHER,
            amountMinor = minor, currency = currency, status = com.isivoltpro.maginaolivo.domain.expense.ExpenseStatus.POSTED,
            origin = com.isivoltpro.maginaolivo.domain.expense.ExpenseOrigin.MANUAL,
        )
        val ledger = com.isivoltpro.maginaolivo.domain.expense.RecollectionLedger.posted(listOf(spent(80_000, "EUR"), spent(30_000, "GBP")))
        var summary by androidx.compose.runtime.mutableStateOf(com.isivoltpro.maginaolivo.feature.campaigns.CampaignSummaryUi(expenses = ledger))
        compose.setContent { MaginaOlivoTheme {
            CampaignDetailScreen(CampaignDetailUiState(isLoading = false, campaign = campaign()), {}, {}, {}, {}, {}, {}, summary = summary)
        } }
        val eur = com.isivoltpro.maginaolivo.domain.expense.Money.format(80_000, "EUR")
        val gbp = com.isivoltpro.maginaolivo.domain.expense.Money.format(30_000, "GBP")
        compose.onNodeWithTag("campaign-metric-expenses").performScrollTo()
        compose.onNodeWithText("$eur · $gbp", substring = true).assertExists()
        compose.onNodeWithText("Varias monedas", substring = true).assertExists()

        compose.runOnIdle { summary = summary.copy(expenses = ledger.filter { it.currency == "GBP" }) }
        compose.onNodeWithText(gbp, substring = true).assertExists()
        compose.onAllNodesWithText("Aún no hay gastos de esta campaña").assertCountEquals(0)
    }

    /** #246: each campaign card shows its figures with icon and words, closed or running. */
    @Test fun campaignCardsSummariseWithoutOpening() {
        val running = campaign().copy(id = UUID.randomUUID(), name = "2026/27", status = CampaignStatus.ACTIVE, endDate = null)
        val closed = campaign()
        val summaries = mapOf(
            running.id to com.isivoltpro.maginaolivo.feature.campaigns.CampaignCardSummary(5_700_000, 3, 3, listOf("EUR" to 65_000L), 2_082),
            closed.id to com.isivoltpro.maginaolivo.feature.campaigns.CampaignCardSummary(0, 0, 0, emptyList(), null),
        )
        compose.setContent { MaginaOlivoTheme {
            androidx.compose.foundation.layout.Column {
                com.isivoltpro.maginaolivo.feature.campaigns.FarmCampaignsSection(
                    com.isivoltpro.maginaolivo.feature.campaigns.FarmCampaignsUiState(isLoading = false, current = listOf(running), history = listOf(closed)),
                    {}, {}, summaries,
                )
            }
        } }
        compose.onNodeWithText("3 pesadas", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("3 días", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Rend. 20,82 %", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Jornales " + com.isivoltpro.maginaolivo.domain.expense.Money.format(65_000, "EUR"), useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Sin pesadas", useUnmergedTree = true).assertIsDisplayed()
    }

    /** #449: closing is allowed with unconfirmed costs, and the farmer is told what that means. */
    @Test fun closingWithUnconfirmedCostsWarnsButStillCloses() {
        val active = campaign().copy(status = CampaignStatus.ACTIVE, endDate = null)
        var complete by androidx.compose.runtime.mutableStateOf<Boolean?>(false)
        var closed = false
        compose.setContent { MaginaOlivoTheme {
            CampaignDetailScreen(CampaignDetailUiState(isLoading = false, campaign = active), {}, {}, {}, { closed = true }, {}, {},
                summary = com.isivoltpro.maginaolivo.feature.campaigns.CampaignSummaryUi(costComplete = complete))
        } }
        compose.onNodeWithTag("close-campaign").performScrollTo().performClick()
        compose.onNodeWithTag("close-cost-warning").assertIsDisplayed()
        compose.onNodeWithText("Hay costes sin confirmar", substring = true).assertIsDisplayed()
        complete = true
        compose.onNodeWithTag("close-cost-warning").assertDoesNotExist()
        complete = null
        compose.onNodeWithTag("close-cost-warning").assertDoesNotExist()
        compose.onNodeWithTag("confirm-campaign-action").performClick()
        compose.runOnIdle { assertTrue(closed) }
    }

    private fun campaign() = Campaign(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "2025/26",
        LocalDate.parse("2025-10-01"), LocalDate.parse("2026-02-01"), CampaignStatus.CLOSED, null,
        listOf(CampaignParcelSnapshot(UUID.randomUUID(), "Finca histórica", "Parcela histórica", 1000.0, null, null)), 3)
}
