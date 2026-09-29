package com.isivoltpro.maginaolivo

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.isivoltpro.maginaolivo.domain.activity.ActivityType
import com.isivoltpro.maginaolivo.domain.notebook.CampaignNotebook
import com.isivoltpro.maginaolivo.feature.notebook.NotebookActions
import com.isivoltpro.maginaolivo.feature.notebook.NotebookHomeScreen
import com.isivoltpro.maginaolivo.feature.notebook.NotebookHubTab
import com.isivoltpro.maginaolivo.feature.notebook.NotebookQuickAction
import com.isivoltpro.maginaolivo.feature.notebook.NotebookUiState
import com.isivoltpro.maginaolivo.ui.theme.MaginaOlivoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** UX-C + #246 §4B — Mi Cuaderno: context, one Pesada action, and four views. */
class NotebookHomeScreenTest {
    @get:Rule val composeRule = createComposeRule()

    private val farm = UiPolishFixtures.farms.first()
    private val campaign = UiPolishFixtures.campaign
    private val treatment = UiPolishFixtures.activity.copy(
        type = ActivityType.PHYTOSANITARY,
        campaignId = campaign.id,
        activityDate = campaign.startDate.plusDays(5),
        description = "Cobre de ejemplo",
    )
    private val notebook = CampaignNotebook.project(campaign, listOf(treatment), emptyList(), emptyList(), emptyList())

    @Test fun contextAndTheSixActionsAreAlwaysAtHandWithNoSecondMenu() {
        val tapped = mutableListOf<NotebookQuickAction>()
        show(onQuickAction = { tapped += it })
        composeRule.onNodeWithTag("notebook-context").assertTextContains("Finca de ejemplo · Campaña de ejemplo")
        // CR-011 §4: the actions are shown directly; no «Registrar hoy» repeats them.
        composeRule.onAllNodesWithTag("notebook-register-today").fetchSemanticsNodes().let { assertEquals(0, it.size) }
        NotebookQuickAction.entries.forEach { action ->
            composeRule.onNodeWithTag(action.tag).performScrollTo().performClick()
        }
        composeRule.runOnIdle {
            assertEquals(NotebookQuickAction.entries.toList(), tapped)
            // CR-011 §5: Trabajo · Riego · Tratamiento · Pesada · Jornal · Gasto, nothing else.
            assertEquals(
                listOf("Trabajo", "Riego", "Tratamiento", "Pesada", "Jornal", "Gasto"),
                NotebookQuickAction.entries.map { it.label },
            )
        }
    }

    @Test fun aParcelFromMiCampoIsShownAndCanBeDropped() {
        var cleared = 0
        composeRule.setContent {
            MaginaOlivoTheme {
                NotebookHomeScreen(
                    isLoading = false, error = null, farms = UiPolishFixtures.farms, activeFarm = farm,
                    notebook = NotebookUiState(isLoading = false, campaigns = listOf(campaign), selectedCampaignId = campaign.id, notebook = notebook),
                    actions = NotebookActions(), onSelectFarm = {}, onSelectCampaign = {}, onQuickAction = {},
                    parcelContext = "Parcela Norte", onClearParcel = { cleared++ },
                )
            }
        }
        composeRule.onNodeWithTag("notebook-parcel-context").assertTextContains("Parcela Norte", substring = true)
        composeRule.onNodeWithTag("notebook-parcel-clear").performClick()
        composeRule.runOnIdle { assertEquals(1, cleared) }
    }

    @Test fun inicioCanOpenTheCampaignView() {
        var handled = 0
        composeRule.setContent {
            MaginaOlivoTheme {
                NotebookHomeScreen(
                    isLoading = false, error = null, farms = UiPolishFixtures.farms, activeFarm = farm,
                    notebook = NotebookUiState(isLoading = false, campaigns = listOf(campaign), selectedCampaignId = campaign.id, notebook = notebook),
                    actions = NotebookActions(), onSelectFarm = {}, onSelectCampaign = {}, onQuickAction = {},
                    tabRequest = NotebookHubTab.CAMPAIGN, onTabRequestHandled = { handled++ },
                )
            }
        }
        composeRule.onNodeWithTag("notebook-summary").performScrollTo().assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(1, handled) }
    }

    @Test fun theFourTabsShowTheSameRecords() {
        show()
        // UX-E Diario: the treatment is in the one timeline, under its day.
        composeRule.onNodeWithTag("notebook-tab-diary").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("notebook-diary").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("notebook-work").performScrollTo().assertIsDisplayed()
        // Fitosanitario: the same Activity read as a treatment entry.
        composeRule.onNodeWithTag("notebook-tab-phyto").performScrollTo().performClick()
        composeRule.onAllNodesWithTag("notebook-phyto-empty").fetchSemanticsNodes().let { assertEquals(0, it.size) }
        composeRule.onNodeWithTag("notebook-phyto-summary").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("notebook-phyto-record").performScrollTo().assertIsDisplayed()
        // Gastos: the one ledger, grouped by people, machines and papers.
        composeRule.onNodeWithTag("notebook-tab-expenses").performScrollTo().performClick()
        composeRule.onNodeWithTag("notebook-expenses-total").performScrollTo().assertTextContains("Sin gastos contabilizados")
        composeRule.onNodeWithTag("notebook-costs-labour").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("notebook-costs-machinery").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("notebook-costs-documents").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("notebook-expenses-empty").performScrollTo().assertIsDisplayed()
        // Campaña: the summary derived from the same records.
        composeRule.onNodeWithTag("notebook-tab-campaign").performScrollTo().performClick()
        composeRule.onNodeWithTag("notebook-summary").performScrollTo().assertIsDisplayed()
    }

    @Test fun anEmptyCampaignSaysSoInTheDiary() {
        show(state = NotebookUiState(
            isLoading = false, campaigns = listOf(campaign), selectedCampaignId = campaign.id,
            notebook = CampaignNotebook.project(campaign, emptyList(), emptyList(), emptyList(), emptyList()),
        ))
        composeRule.onNodeWithTag("notebook-diary-empty").performScrollTo().assertIsDisplayed()
    }

    /** UX-G accessibility: icon + text, never an icon alone; every control is a real button. */
    @Test fun quickActionsAndTabsAreLabelledControls() {
        show()
        NotebookQuickAction.entries.forEach { action ->
            composeRule.onNodeWithTag(action.tag).performScrollTo().assertHasClickAction().assertTextContains(action.label)
        }
        NotebookHubTab.entries.forEach { tab ->
            composeRule.onNodeWithTag(tab.tag).performScrollTo().assertHasClickAction().assertTextContains(tab.label)
        }
    }

    @Test fun withoutFarmsMiCampoIsOffered() {
        var toFields = 0
        composeRule.setContent {
            MaginaOlivoTheme {
                NotebookHomeScreen(
                    isLoading = false, error = null, farms = emptyList(), activeFarm = null, notebook = null, actions = null,
                    onSelectFarm = {}, onSelectCampaign = {}, onQuickAction = {},
                    onGoToFields = { toFields++ },
                )
            }
        }
        composeRule.onNodeWithTag("notebook-root-no-farms").assertIsDisplayed()
        composeRule.onAllNodesWithTag(NotebookQuickAction.WORK.tag).fetchSemanticsNodes().let { assertEquals(0, it.size) }
        composeRule.onNodeWithText("Ir a Mi Campo").performClick()
        composeRule.runOnIdle { assertEquals(1, toFields) }
    }

    @Test fun withoutACampaignTheNotebookSaysSo() {
        show(state = NotebookUiState(isLoading = false))
        composeRule.onNodeWithTag("notebook-context").assertTextContains("Sin campaña en marcha", substring = true)
        composeRule.onNodeWithTag("notebook-no-campaign").performScrollTo().assertIsDisplayed()
    }

    private fun show(
        state: NotebookUiState = NotebookUiState(isLoading = false, campaigns = listOf(campaign), selectedCampaignId = campaign.id, notebook = notebook),
        onQuickAction: (NotebookQuickAction) -> Unit = {},
    ) {
        composeRule.setContent {
            MaginaOlivoTheme {
                NotebookHomeScreen(
                    isLoading = false,
                    error = null,
                    farms = UiPolishFixtures.farms,
                    activeFarm = farm,
                    notebook = state,
                    actions = NotebookActions(),
                    onSelectFarm = {},
                    onSelectCampaign = {},
                    onQuickAction = onQuickAction,
                )
            }
        }
    }
}
