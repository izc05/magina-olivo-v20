package com.isivoltpro.maginaolivo

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun launcherShowsOnboardingReference() {
        composeRule.onNodeWithTag("onboarding-root").assertIsDisplayed()
        composeRule.onNodeWithText("Mágina\nOlivo").assertIsDisplayed()
        composeRule.onNodeWithText("Saltar").assertIsDisplayed()
    }

    @Test
    fun skipOnboardingShowsHomeReference() {
        composeRule.onNodeWithText("Saltar").performClick()

        composeRule.onNodeWithTag("home-reference-root").assertIsDisplayed()
        composeRule.onNodeWithTag("bottom-Inicio").assertIsSelected()
        composeRule.onNodeWithText("Mercado del aceite").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Inicio").assertIsDisplayed()
    }
}
