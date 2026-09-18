package com.isivoltpro.maginaolivo

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun launcherShowsFoundationContent() {
        composeRule.onNodeWithTag("foundation-root").assertIsDisplayed()
        composeRule.onNodeWithText("Olivar").assertIsDisplayed()
        composeRule.onNodeWithText("DEV").assertIsDisplayed()
    }
}
