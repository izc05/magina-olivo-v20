package com.isivoltpro.maginaolivo

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
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
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** UX-C + #246 §4B — Mi Cuaderno: context, one Pesada action, and four views. */
class NotebookHomeScreenTest {
    @get:Rule val composeRule = createComposeRule()

    private val farm = UiPolishFixtures.farms.first()
    private val campaign = UiPolishFixtures.campaign
    private val treatment = UiPolishFixtures.activity.copy(
        type = ActivityType.PHYTOSANITARY,
        status = com.isivoltpro.maginaolivo.data.local.model.ActivityStatus.COMPLETED,
        campaignId = campaign.id,
        activityDate = campaign.startDate.plusDays(5),
        description = "Cobre de ejemplo",
    )
    private val notebook = CampaignNotebook.project(campaign, listOf(treatment), emptyList(), emptyList(), emptyList())

    @Test fun contextAndTheSixActionsAreAlwaysAtHandWithNoSecondMenu() {
        val tapped = mutableListOf<NotebookQuickAction>()
        show(onQuickAction = { tapped += it })
        // #351 (1): the Farm is the main datum and the campaign is its own chip, not running text.
        composeRule.onNodeWithTag("notebook-context").assertTextContains("Finca de ejemplo").assertTextContains("Campaña de ejemplo", substring = true)
        // The chip says the campaign's state in words, not only by colour.
        composeRule.onNode(
            hasTestTag("notebook-campaign-chip") and
                hasAnyDescendant(hasText(com.isivoltpro.maginaolivo.feature.notebook.campaignChipText(campaign.name, campaign.status))),
            useUnmergedTree = true,
        ).assertExists()
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

    /** #369: from a Farm or a Parcel the Farm stays fixed; only the Cuaderno tab offers «Cambiar finca». */
    @Test fun onlyTheCuadernoTabOffersToChangeTheFarm() {
        var canChange by androidx.compose.runtime.mutableStateOf(false)
        composeRule.setContent {
            MaginaOlivoTheme {
                NotebookHomeScreen(
                    isLoading = false, error = null, farms = UiPolishFixtures.farms, activeFarm = farm,
                    notebook = NotebookUiState(isLoading = false, campaigns = listOf(campaign), selectedCampaignId = campaign.id, notebook = notebook),
                    actions = NotebookActions(), onSelectFarm = {}, onSelectCampaign = {}, onQuickAction = {},
                    parcelContext = "Parcela Norte", canChangeFarm = canChange,
                )
            }
        }
        // The Farm and the Parcel are shown as the fixed context, with «Toda la finca» kept.
        composeRule.onNodeWithTag("notebook-context").assertTextContains(farm.name, substring = true)
        composeRule.onNodeWithTag("notebook-parcel-context").assertTextContains("Parcela Norte", substring = true)
        composeRule.onNodeWithTag("notebook-parcel-clear").assertExists()
        assertEquals(0, composeRule.onAllNodesWithTag("notebook-change-farm").fetchSemanticsNodes().size)

        canChange = true
        composeRule.waitForIdle()
        assertEquals(1, composeRule.onAllNodesWithTag("notebook-change-farm").fetchSemanticsNodes().size)
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

    @Test fun selectedHistoricalCampaignDoesNotReplaceRunningContext() {
        val closed = campaign.copy(
            id = java.util.UUID.randomUUID(),
            name = "2025/26",
            status = com.isivoltpro.maginaolivo.data.local.model.CampaignStatus.CLOSED,
            endDate = campaign.startDate.plusDays(45),
        )
        val running = campaign.copy(
            id = java.util.UUID.randomUUID(),
            name = "2026/27",
            startDate = campaign.startDate.plusYears(1),
            endDate = null,
            status = com.isivoltpro.maginaolivo.data.local.model.CampaignStatus.ACTIVE,
        )
        val selected = CampaignNotebook.project(closed, emptyList(), emptyList(), emptyList(), emptyList())
        var labourCampaign: java.util.UUID? = null
        composeRule.setContent {
            MaginaOlivoTheme {
                NotebookHomeScreen(
                    isLoading = false,
                    error = null,
                    farms = UiPolishFixtures.farms,
                    activeFarm = farm,
                    notebook = NotebookUiState(
                        isLoading = false,
                        campaigns = listOf(closed, running),
                        selectedCampaignId = closed.id,
                        notebook = selected,
                    ),
                    actions = NotebookActions(onLabour = { labourCampaign = it }),
                    onSelectFarm = {},
                    onSelectCampaign = {},
                    onQuickAction = {},
                )
            }
        }

        // The general header stays operational although the historical Campaign is selected.
        composeRule.onNodeWithTag("notebook-campaign-chip")
            .assertTextContains("2026/27", substring = true)

        composeRule.onNodeWithTag("notebook-tab-expenses").performScrollTo().performClick()
        composeRule.onNodeWithTag("notebook-open-labour")
            .performScrollTo()
            .assertTextContains("2026/27", substring = true)
            .performClick()
        composeRule.runOnIdle { assertEquals(running.id, labourCampaign) }

        // Only the Campaign tab says which historical Campaign is being consulted.
        composeRule.onNodeWithTag("notebook-tab-campaign").performScrollTo().performClick()
        composeRule.onNodeWithTag("notebook-selected-campaign-context")
            .performScrollTo()
            .assertTextContains("2025/26", substring = true)
            .assertTextContains("Cerrada", substring = true)
    }

    @Test fun withoutACampaignTheNotebookSaysSo() {
        show(state = NotebookUiState(isLoading = false))
        composeRule.onNode(
            hasTestTag("notebook-campaign-chip") and hasAnyDescendant(hasText("Sin campaña en marcha")),
            useUnmergedTree = true,
        ).assertExists()
        composeRule.onNodeWithTag("notebook-context").assertTextContains("Finca de ejemplo", substring = true)
        // #417: the Diario is the Farm's and opens without a Campaign; only Campaña asks for one.
        composeRule.onNodeWithTag("notebook-diary-empty").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("notebook-tab-campaign").performScrollTo().performClick()
        composeRule.onNodeWithTag("notebook-no-campaign").performScrollTo().assertIsDisplayed()
    }

    /** #417: with no Campaign at all, the Farm's work and costs are in Diario, Fitosanitario and Gastos. */
    @Test fun withoutACampaignTheFarmsRecordsAreStillThere() {
        val general = UiPolishFixtures.activity.copy(
            type = ActivityType.PHYTOSANITARY, status = com.isivoltpro.maginaolivo.data.local.model.ActivityStatus.COMPLETED,
            campaignId = null, description = "Cobre general",
        )
        val cost = com.isivoltpro.maginaolivo.domain.expense.Expense(
            java.util.UUID.randomUUID(), farm.workspaceId, general.activityDate, "Gasóleo general",
            com.isivoltpro.maginaolivo.domain.expense.ExpenseCategory.FUEL, 9_000, "EUR",
            com.isivoltpro.maginaolivo.domain.expense.ExpenseStatus.POSTED, com.isivoltpro.maginaolivo.domain.expense.ExpenseOrigin.MANUAL,
            farmId = farm.id,
        )
        val farmNotebook = com.isivoltpro.maginaolivo.domain.notebook.FarmNotebook.of(farm.id, listOf(general), emptyList(), emptyList(), listOf(cost))
        show(state = NotebookUiState(isLoading = false, farmNotebook = farmNotebook))
        composeRule.onNodeWithTag("notebook-work").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("notebook-tab-phyto").performScrollTo().performClick()
        composeRule.onNodeWithTag("notebook-phyto-record").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("notebook-tab-expenses").performScrollTo().performClick()
        composeRule.onNodeWithTag("notebook-costs-general").performScrollTo()
            .assertTextContains(com.isivoltpro.maginaolivo.domain.expense.Money.format(9_000, "EUR"), substring = true)
        composeRule.onNodeWithTag("notebook-costs-recollection").performScrollTo().assertTextContains("—", substring = true)
    }

    /** #378: without a running campaign Jornal is passed on (it opens the Farm's own labour), no notice. */
    @Test fun withoutACampaignJornalOpensTheFarmsLabour() {
        val tapped = mutableListOf<NotebookQuickAction>()
        show(state = NotebookUiState(isLoading = false), onQuickAction = { tapped += it })
        composeRule.onNodeWithTag(NotebookQuickAction.LABOUR.tag).performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(listOf(NotebookQuickAction.LABOUR), tapped) }
    }

    /** Codex #368: a notebook that failed to load does not count as «no campaign». */
    @Test fun aFailedLoadDoesNotSendJornalOutsideTheCampaign() {
        val tapped = mutableListOf<NotebookQuickAction>()
        show(state = NotebookUiState(isLoading = false, error = "No se pudo abrir"), onQuickAction = { tapped += it })
        composeRule.onNodeWithTag(NotebookQuickAction.LABOUR.tag).performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(emptyList<NotebookQuickAction>(), tapped) }
    }

    /** #350/#378: while the Farm's campaign is still loading, Jornal waits: which flow it is is never guessed. */
    @Test fun whileTheCampaignLoadsJornalWaits() {
        val tapped = mutableListOf<NotebookQuickAction>()
        show(state = NotebookUiState(isLoading = true), onQuickAction = { tapped += it })
        composeRule.onNodeWithTag(NotebookQuickAction.LABOUR.tag).performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(emptyList<NotebookQuickAction>(), tapped) }
    }

    /** Device check (build 683): at 360 dp with large text no action label wraps and every view is whole. */
    @Test fun at360dpWithLargeTextLabelsStayWholeAndEveryViewIsVisible() {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1.3f)) {
                MaginaOlivoTheme {
                    Box(Modifier.width(360.dp)) {
                        NotebookHomeScreen(
                            isLoading = false,
                            error = null,
                            farms = UiPolishFixtures.farms,
                            activeFarm = farm,
                            notebook = NotebookUiState(isLoading = false, campaigns = listOf(campaign), selectedCampaignId = campaign.id, notebook = notebook),
                            actions = NotebookActions(),
                            onSelectFarm = {},
                            onSelectCampaign = {},
                            onQuickAction = {},
                        )
                    }
                }
            }
        }
        listOf("Tratamiento", "Trabajo", "Pesada").forEach { label ->
            val layout = textLayout(composeRule.onNodeWithText(label, useUnmergedTree = true))
            assertEquals("«$label» in one line", 1, layout.lineCount)
        }
        NotebookHubTab.entries.forEach { view ->
            composeRule.onNodeWithTag(view.tag).performScrollTo().assertIsDisplayed()
            val layout = textLayout(composeRule.onAllNodesWithText(view.label, useUnmergedTree = true)[0])
            // Whole = one line, laid out at least as wide as the label needs (nothing clipped).
            assertEquals("«${view.label}» in one line", 1, layout.lineCount)
            assertTrue("«${view.label}» is whole", layout.size.width >= layout.multiParagraph.intrinsics.maxIntrinsicWidth - 1f)
        }
    }

    private fun textLayout(node: SemanticsNodeInteraction): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        node.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.single()
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
