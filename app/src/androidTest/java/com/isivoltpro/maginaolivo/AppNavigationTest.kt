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

        composeRule.onNodeWithText("Añadir").performScrollTo().performClick()
        composeRule.onNodeWithTag("parcel-name").performTextInput("Parcela Alta")
        composeRule.onNodeWithTag("save-parcel").performScrollTo().performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Parcela Alta").fetchSemanticsNodes().size == 1
        }
        composeRule.onNodeWithText("Parcela Alta").performClick()

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
    fun developerGalleryIsReachableFromDevProfile() {
        enterMainShell()
        composeRule.onNodeWithTag("bottom-Perfil").performClick()

        composeRule.onNodeWithText("Catálogo de diseño (DEV)").performClick()

        composeRule.onNodeWithTag("component-catalogue-root").assertIsDisplayed()
    }

    private fun enterMainShell() {
        composeRule.onNodeWithText("Saltar").performClick()
        composeRule.onNodeWithTag("home-reference-root").assertIsDisplayed()
    }
}
