package com.isivoltpro.maginaolivo

import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.printToString
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppNavigationTest {
    val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain =
        RuleChain
            .outerRule(ClearOnboardingStateRule())
            .around(composeRule)

    @Test
    fun completedOnboardingStaysCompletedAfterActivityRecreation() {
        enterMainShell()

        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("onboarding-root").assertDoesNotExist()
        composeRule.onNodeWithTag("home-reference-root").assertIsDisplayed()
    }

    @Test
    fun allFrozenRootsAreReachableAndSelected() {
        enterMainShell()

        composeRule.onNodeWithTag("bottom-Mi Olivar").performClick().assertIsSelected()
        composeRule.onNodeWithTag("farms-root").assertIsDisplayed()

        composeRule.onNodeWithTag("bottom-Calendario").performClick().assertIsSelected()
        composeRule.onNodeWithTag("calendar-root").assertIsDisplayed()

        composeRule.onNodeWithTag("bottom-Perfil").performClick().assertIsSelected()
        composeRule.onNodeWithTag("profile-root").assertIsDisplayed()

        composeRule.onNodeWithTag("bottom-Inicio").performClick().assertIsSelected()
        composeRule.onNodeWithTag("home-reference-root").assertIsDisplayed()
    }

    @Test
    fun backFromAnotherRootReturnsToHome() {
        enterMainShell()
        composeRule.onNodeWithTag("bottom-Perfil").performClick()

        pressBack()

        composeRule.onNodeWithTag("home-reference-root").assertIsDisplayed()
        composeRule.onNodeWithTag("bottom-Inicio").assertIsSelected()
    }

    @Test
    fun activeRootSurvivesActivityRecreation() {
        enterMainShell()
        composeRule.onNodeWithTag("bottom-Calendario").performClick()

        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("calendar-root").assertIsDisplayed()
        composeRule.onNodeWithTag("bottom-Calendario").assertIsSelected()
    }

    @Test
    fun revisitingRootDoesNotAddDuplicateDestination() {
        enterMainShell()
        composeRule.onNodeWithTag("bottom-Mi Olivar").performClick()
        composeRule.onNodeWithTag("bottom-Inicio").performClick()
        composeRule.onNodeWithTag("bottom-Mi Olivar").performClick()

        pressBack()

        composeRule.onNodeWithTag("home-reference-root").assertIsDisplayed()
        composeRule.onNodeWithTag("bottom-Inicio").assertIsSelected()
    }

    @Test
    fun registerRootOpensContextSheetBeforeFlow() {
        enterMainShell()

        composeRule.onNodeWithTag("bottom-Registrar").performClick()
        composeRule.onNodeWithTag("register-action-sheet").assertIsDisplayed()
        composeRule.onNodeWithText("Registrar actuación").performClick()

        composeRule.onNodeWithTag("register-reference-root").assertIsDisplayed()
        composeRule.onNodeWithTag("bottom-Registrar").assertIsSelected()
    }

    @Test
    fun registerSheetCanBeCancelledWithoutChangingRoot() {
        enterMainShell()

        composeRule.onNodeWithTag("bottom-Registrar").performClick()
        composeRule.onNodeWithText("Cancelar").performClick()

        composeRule.onNodeWithTag("register-action-sheet").assertDoesNotExist()
        composeRule.onNodeWithTag("home-reference-root").assertIsDisplayed()
        composeRule.onNodeWithTag("bottom-Inicio").assertIsSelected()
    }

    @Test
    fun nestedFarmRouteReturnsToOlivar() {
        enterMainShell()
        composeRule.onNodeWithTag("bottom-Mi Olivar").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("add-farm").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("add-farm").performClick()
        composeRule.onNodeWithTag("farm-name").performTextInput("La Solana")
        composeRule.onNodeWithTag("save-farm").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("La Solana").fetchSemanticsNodes().size == 1
        }
        composeRule.onNodeWithText("La Solana").performClick()
        composeRule.onNodeWithTag("farm-detail-root").assertIsDisplayed()

        pressBack()
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("farms-root").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("farms-root").assertIsDisplayed()
        composeRule.onNodeWithTag("bottom-Mi Olivar").assertIsSelected()
    }

    @Test
    fun parcelCanBeCreatedAndOpenedFromItsFarm() {
        enterMainShell()
        composeRule.onNodeWithTag("bottom-Mi Olivar").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("add-farm").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("add-farm").performClick()
        composeRule.onNodeWithTag("farm-name").performTextInput("Los Llanos")
        composeRule.onNodeWithTag("save-farm").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Los Llanos").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Los Llanos").performClick()

        composeRule.onNodeWithTag("add-parcel").performScrollTo().performClick()
        composeRule.onNodeWithTag("parcel-name").performTextInput("Parcela Alta")
        composeRule.onNodeWithTag("save-parcel").performScrollTo().performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("save-parcel").fetchSemanticsNodes().isEmpty() &&
                composeRule.onAllNodesWithText("Parcela Alta").fetchSemanticsNodes().size == 1
        }
        composeRule.onNodeWithTag("parcel-row").performScrollTo().performClick()

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("parcel-detail-root").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("parcel-detail-root").assertIsDisplayed()
        composeRule.onNodeWithText("Entrada manual").assertIsDisplayed()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Sin registrar", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("bottom-Mi Olivar").assertIsSelected()
    }

    @Test
    fun farmParcelCampaignLifecyclePersistsAcrossRecreation() {
        enterMainShell()

        // Mi Olivar
        composeRule.onNodeWithTag("bottom-Mi Olivar").performClick()
        waitForTag("add-farm")

        // Farm: open the editor, fill it, save, and wait for the persisted row.
        composeRule.onNodeWithTag("add-farm").performClick()
        waitForTag("farm-name")
        composeRule.onNodeWithTag("farm-name").performTextInput("Finca Campaña E2E")
        waitForTag("save-farm")
        composeRule.onNodeWithTag("save-farm").performClick()
        waitForText("Finca Campaña E2E")

        // Farm detail
        composeRule.onNodeWithText("Finca Campaña E2E").performClick()
        waitForTag("add-parcel")

        // Parcel: open the editor, fill it, save, and wait for the persisted row.
        composeRule.onNodeWithTag("add-parcel").performScrollTo().performClick()
        waitForTag("parcel-name")
        composeRule.onNodeWithTag("parcel-name").performTextInput("Parcela Campaña E2E")
        waitForTag("save-parcel")
        composeRule.onNodeWithTag("save-parcel").performScrollTo().performClick()
        waitForText("Parcela Campaña E2E")

        // Campaign: open the editor, fill it, select the Parcel, save.
        waitForTag("add-campaign")
        composeRule.onNodeWithTag("add-campaign").performScrollTo().performClick()
        waitForTag("campaign-name")
        composeRule.onNodeWithTag("campaign-name").performTextInput("Campaña 2026/27 E2E")
        // Past start date: closing uses the device clock, so a future start would make
        // the legal close date depend on the day the suite runs.
        waitForTag("campaign-start-date")
        composeRule.onNodeWithTag("campaign-start-date").performTextInput("2026-01-01")
        waitForTag("campaign-parcel-option")
        composeRule.onNodeWithTag("campaign-parcel-option").performClick()
        waitForTag("save-campaign")
        composeRule.onNodeWithTag("save-campaign").performScrollTo().performClick()
        waitForText("Campaña 2026/27 E2E")

        // Campaign detail
        waitForTag("campaign-row")
        composeRule.onNodeWithTag("campaign-row").performScrollTo().performClick()
        waitForTag("campaign-detail-root")

        // PREPARATION -> ACTIVE
        clickLifecycleActionByTag("activate-campaign")
        confirmCampaignAction()

        // ACTIVE -> HARVEST
        clickLifecycleActionByText("Iniciar recolección")
        confirmCampaignAction()

        // HARVEST -> CLOSED
        clickLifecycleActionByTag("close-campaign")
        confirmCampaignAction()
        waitForText("Histórico protegido")

        // Restart the process and prove the aggregate survived.
        pressBack()
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
        waitForText("Campaña 2026/27 E2E")
        composeRule.onNodeWithText("Campaña 2026/27 E2E").performScrollTo().performClick()
        waitForTag("campaign-detail-root")
        waitForText("Parcela Campaña E2E")
        composeRule.onNodeWithText("Parcela Campaña E2E").assertIsDisplayed()
        composeRule.onNodeWithText("Finca Campaña E2E").assertIsDisplayed()
        composeRule.onNodeWithText("Sin datos").assertIsDisplayed()

        // A closed campaign stays protected after the restart, and reopening it is an
        // explicit, confirmed action that returns the aggregate to an editable state.
        waitForText("Histórico protegido")
        composeRule.onNodeWithText("Histórico protegido").assertIsDisplayed()

        // CLOSED -> HARVEST
        clickLifecycleActionByTag("reopen-campaign")
        confirmCampaignAction()

        // HARVEST -> CLOSED again
        clickLifecycleActionByTag("close-campaign")
        confirmCampaignAction()
        waitForText("Histórico protegido")
        composeRule.onNodeWithText("Parcela Campaña E2E").assertIsDisplayed()
    }

    @Test
    fun developerGalleryIsReachableFromDevProfile() {
        enterMainShell()
        composeRule.onNodeWithTag("bottom-Perfil").performClick()

        composeRule.onNodeWithText("Catálogo de diseño (DEV)").performClick()

        composeRule.onNodeWithTag("component-catalogue-root").assertIsDisplayed()
    }

    private fun enterMainShell() {
        waitForText("Saltar")
        composeRule.onNodeWithText("Saltar").performClick()
        waitForTag("home-reference-root")
        composeRule.onNodeWithTag("home-reference-root").assertIsDisplayed()
    }

    /**
     * Waits for observable UI state instead of relying on timing luck.
     *
     * CI emulators are much slower than a developer machine: cold Room initialisation,
     * the first composition of a ModalBottomSheet and activity recreation can each
     * exceed a 5s budget, which is why this E2E failed at a different point on every
     * run. Every asynchronous transition now waits for the state it depends on.
     */
    private fun waitForTag(tag: String, timeoutMillis: Long = UI_TIMEOUT_MS) {
        composeRule.waitUntil(timeoutMillis) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForText(text: String, timeoutMillis: Long = UI_TIMEOUT_MS) {
        composeRule.waitUntil(timeoutMillis) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /**
     * Waits for a node and, on timeout, fails with a named diagnostic.
     *
     * A ModalBottomSheet renders in its own window, so a silent no-op click on the screen
     * behind it and a sheet that opened without the expected child look identical from a
     * bare timeout. The diagnostic tells the two apart on the next CI run.
     */
    private fun waitForNodeOrDump(description: String, nodes: () -> SemanticsNodeInteractionCollection) {
        try {
            composeRule.waitUntil(UI_TIMEOUT_MS) { nodes().fetchSemanticsNodes().isNotEmpty() }
        } catch (timeout: ComposeTimeoutException) {
            throw AssertionError("Timed out waiting for $description.\n" + diagnostics(), timeout)
        }
    }

    /**
     * Built only from node collections this suite already uses, so the diagnostic can
     * never be the reason the instrumented sources fail to compile, and every lookup is
     * guarded so a missing node reports itself instead of masking the real failure.
     */
    private fun diagnostics(): String = buildString {
        appendLine("Campaign detail screen:")
        appendLine(dumpOrAbsent { composeRule.onAllNodesWithTag("campaign-detail-root", useUnmergedTree = true) })
        appendLine("Confirmation sheet title:")
        appendLine(dumpOrAbsent { composeRule.onAllNodesWithText("Confirmar cambio", useUnmergedTree = true) })
        appendLine("Confirmation button:")
        appendLine(dumpOrAbsent { composeRule.onAllNodesWithTag("confirm-campaign-action", useUnmergedTree = true) })
        appendLine("Lifecycle nodes present:")
        listOf("edit-campaign", "activate-campaign", "close-campaign", "reopen-campaign", "campaign-row")
            .forEach { tag ->
                appendLine("  $tag -> ${countOrZero { composeRule.onAllNodesWithTag(tag, useUnmergedTree = true) }}")
            }
    }

    private fun dumpOrAbsent(nodes: () -> SemanticsNodeInteractionCollection): String =
        runCatching { nodes().printToString(Int.MAX_VALUE) }.getOrElse { "  <not available: ${it.message}>" }

    private fun countOrZero(nodes: () -> SemanticsNodeInteractionCollection): Int =
        runCatching { nodes().fetchSemanticsNodes().size }.getOrElse { -1 }

    /**
     * Clicks a Campaign lifecycle button, proving first that the click can actually land.
     *
     * The detail screen is a verticalScroll Column, so a composed button can sit outside
     * the viewport: waitForTag then succeeds, performClick silently hits nothing and the
     * sheet never opens. Scrolling to it and asserting displayed/enabled/clickable turns
     * that silent no-op into a named failure.
     */
    private fun clickLifecycleActionByTag(tag: String) {
        waitForNodeOrDump("lifecycle action <$tag>") { composeRule.onAllNodesWithTag(tag) }
        composeRule.onNodeWithTag(tag).performScrollTo()
        composeRule.onNodeWithTag(tag)
            .assertIsDisplayed()
            .assertIsEnabled()
            .assertHasClickAction()
            .performClick()
    }

    private fun clickLifecycleActionByText(text: String) {
        waitForNodeOrDump("lifecycle action \"$text\"") { composeRule.onAllNodesWithText(text) }
        composeRule.onNodeWithText(text).performScrollTo()
        composeRule.onNodeWithText(text)
            .assertIsDisplayed()
            .assertIsEnabled()
            .assertHasClickAction()
            .performClick()
    }

    /** Confirms a Campaign lifecycle action once its ModalBottomSheet is actually composed. */
    private fun confirmCampaignAction() {
        // The sheet title proves the ModalBottomSheet window opened at all; only then is a
        // missing button a defect inside the sheet rather than a click that never landed.
        waitForNodeOrDump("confirmation sheet title") { composeRule.onAllNodesWithText("Confirmar cambio") }
        waitForNodeOrDump("confirm-campaign-action") {
            composeRule.onAllNodesWithTag("confirm-campaign-action", useUnmergedTree = true)
        }
        composeRule.onNodeWithTag("confirm-campaign-action", useUnmergedTree = true).performClick()
    }

    private companion object {
        /** Generous enough for a cold CI emulator, still bounded so a real hang fails. */
        const val UI_TIMEOUT_MS = 15_000L
    }
}
