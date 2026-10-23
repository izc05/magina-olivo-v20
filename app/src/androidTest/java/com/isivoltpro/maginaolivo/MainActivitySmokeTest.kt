package com.isivoltpro.maginaolivo

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {
    val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain =
        RuleChain
            .outerRule(ClearOnboardingStateRule())
            .around(composeRule)

    private fun awaitOnboarding() {
        // MainActivity performs Room/account startup asynchronously before navigation appears.
        // Wait for the actual onboarding screen; do not bypass its visibility assertions.
        composeRule.waitUntil(30_000) {
            composeRule.onAllNodesWithTag("onboarding-root").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun launcherShowsOnboardingReference() {
        awaitOnboarding()
        composeRule.onNodeWithTag("onboarding-root").assertIsDisplayed()
        // The official wordmark is an image (Codex brand pass); it is announced by its name.
        composeRule.onNodeWithContentDescription("Mágina Olivo").assertIsDisplayed()
        composeRule.onNodeWithText("Saltar").assertIsDisplayed()
    }

    @Test
    fun skipOnboardingShowsHomeReference() {
        awaitOnboarding()
        composeRule.onNodeWithText("Saltar").performClick()

        composeRule.onNodeWithTag("home-reference-root").assertIsDisplayed()
        composeRule.onNodeWithTag("bottom-Inicio").assertIsSelected()
        // Inicio grows once Room answers; scroll only after the loading block is gone.
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithTag("home-loading").fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithTag("home-market").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Inicio").assertIsDisplayed()
    }
}
