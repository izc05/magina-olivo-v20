package com.isivoltpro.maginaolivo

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
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
        waitForTag("activate-campaign")
        composeRule.onNodeWithTag("activate-campaign").performClick()
        confirmCampaignAction()

        // ACTIVE -> HARVEST
        waitForText("Iniciar recolección")
        composeRule.onNodeWithText("Iniciar recolección").performClick()
        confirmCampaignAction()

        // HARVEST -> CLOSED
        waitForTag("close-campaign")
        composeRule.onNodeWithTag("close-campaign").performClick()
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
        waitForTag("reopen-campaign")
        composeRule.onNodeWithTag("reopen-campaign").performScrollTo().performClick()
        confirmCampaignAction()

        // HARVEST -> CLOSED again
        waitForTag("close-campaign")
        composeRule.onNodeWithTag("close-campaign").performScrollTo().performClick()
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

    /** Confirms a Campaign lifecycle action once its ModalBottomSheet is actually composed. */
    private fun confirmCampaignAction() {
        waitForTag("confirm-campaign-action")
        composeRule.onNodeWithTag("confirm-campaign-action").performClick()
    }

    private companion object {
        /** Generous enough for a cold CI emulator, still bounded so a real hang fails. */
        const val UI_TIMEOUT_MS = 15_000L
    }
}
