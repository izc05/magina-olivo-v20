package com.isivoltpro.maginaolivo

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.isivoltpro.maginaolivo.ui.reference.components.ComponentCatalogueReferenceScreen
import com.isivoltpro.maginaolivo.ui.theme.MaginaOlivoTheme
import org.junit.Rule
import org.junit.Test

class Phase3ComponentCatalogueTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun catalogueRendersCoreStates() {
        composeRule.setContent {
            MaginaOlivoTheme {
                ComponentCatalogueReferenceScreen()
            }
        }

        composeRule.onNodeWithTag("component-catalogue-root").assertIsDisplayed()
        composeRule.onNodeWithText("Botones").assertIsDisplayed()
        composeRule.onNodeWithText("Estados").performScrollTo().assertIsDisplayed()
    }
}
