package com.isivoltpro.maginaolivo

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.isivoltpro.maginaolivo.domain.activity.ActivityType
import com.isivoltpro.maginaolivo.domain.notebook.CampaignNotebook
import com.isivoltpro.maginaolivo.feature.notebook.NotebookActions
import com.isivoltpro.maginaolivo.feature.notebook.NotebookHomeScreen
import com.isivoltpro.maginaolivo.feature.notebook.NotebookQuickAction
import com.isivoltpro.maginaolivo.feature.notebook.NotebookUiState
import com.isivoltpro.maginaolivo.ui.theme.MaginaOlivoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** UX-C (Issue #246) — Mi Cuaderno: context, Registrar hoy, nine quick actions, four tabs. */
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

    @Test fun contextRegisterAndTheNineQuickActionsAreAlwaysAtHand() {
        val tapped = mutableListOf<NotebookQuickAction>()
        var registered = 0
        show(onQuickAction = { tapped += it }, onRegisterToday = { registered++ })
        composeRule.onNodeWithTag("notebook-context").assertTextContains("Finca de ejemplo · Campaña de ejemplo")
        composeRule.onNodeWithTag("notebook-register-today").performClick()
        NotebookQuickAction.entries.forEach { action ->
            composeRule.onNodeWithTag(action.tag).performScrollTo().performClick()
        }
        composeRule.runOnIdle {
            assertEquals(1, registered)
            assertEquals(NotebookQuickAction.entries.toList(), tapped)
        }
    }

    @Test fun theFourTabsShowTheSameRecords() {
        show()
        composeRule.onNodeWithTag("notebook-tab-diary").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("notebook-tab-phyto").performClick()
        composeRule.onAllNodesWithTag("notebook-phyto-empty").fetchSemanticsNodes().let { assertEquals(0, it.size) }
        composeRule.onNodeWithTag("notebook-tab-expenses").performClick()
        composeRule.onNodeWithTag("notebook-expenses-total").performScrollTo().assertTextContains("Sin gastos contabilizados")
        composeRule.onNodeWithTag("notebook-expenses-empty").assertIsDisplayed()
        composeRule.onNodeWithTag("notebook-tab-campaign").performClick()
    }

    @Test fun withoutFarmsRegisterStaysAndMiCampoIsOffered() {
        var toFields = 0
        composeRule.setContent {
            MaginaOlivoTheme {
                NotebookHomeScreen(
                    isLoading = false, error = null, farms = emptyList(), activeFarm = null, notebook = null, actions = null,
                    onSelectFarm = {}, onSelectCampaign = {}, onRegisterToday = {}, onQuickAction = {},
                    onGoToFields = { toFields++ },
                )
            }
        }
        composeRule.onNodeWithTag("notebook-register-today").assertIsDisplayed()
        composeRule.onNodeWithTag("notebook-root-no-farms").assertIsDisplayed()
        composeRule.onAllNodesWithTag(NotebookQuickAction.WORK.tag).fetchSemanticsNodes().let { assertEquals(0, it.size) }
    }

    @Test fun withoutACampaignTheNotebookSaysSo() {
        show(state = NotebookUiState(isLoading = false))
        composeRule.onNodeWithTag("notebook-context").assertTextContains("Sin campaña en marcha", substring = true)
        composeRule.onNodeWithTag("notebook-no-campaign").performScrollTo().assertIsDisplayed()
    }

    private fun show(
        state: NotebookUiState = NotebookUiState(isLoading = false, campaigns = listOf(campaign), selectedCampaignId = campaign.id, notebook = notebook),
        onQuickAction: (NotebookQuickAction) -> Unit = {},
        onRegisterToday: () -> Unit = {},
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
                    onRegisterToday = onRegisterToday,
                    onQuickAction = onQuickAction,
                )
            }
        }
    }
}
