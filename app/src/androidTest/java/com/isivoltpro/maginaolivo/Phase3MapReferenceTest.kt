package com.isivoltpro.maginaolivo

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.isivoltpro.maginaolivo.ui.reference.map.MapCatastroReferenceScreen
import com.isivoltpro.maginaolivo.ui.theme.MaginaOlivoTheme
import org.junit.Rule
import org.junit.Test

class Phase3MapReferenceTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun mapReferenceRendersSelectionAndImportBoundary() {
        composeRule.setContent {
            MaginaOlivoTheme {
                MapCatastroReferenceScreen()
            }
        }

        composeRule.onNodeWithTag("map-catastro-reference-root").assertIsDisplayed()
        composeRule.onNodeWithText("Mapa y Catastro").assertIsDisplayed()
        composeRule.onNodeWithText("Parcela encontrada").assertIsDisplayed()
        composeRule.onNodeWithText("Incorporar a una finca").assertIsDisplayed()
    }
}
