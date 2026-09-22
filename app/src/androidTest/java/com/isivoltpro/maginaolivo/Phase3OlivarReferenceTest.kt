package com.isivoltpro.maginaolivo

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.isivoltpro.maginaolivo.ui.reference.farm.FarmDetailReferenceScreen
import com.isivoltpro.maginaolivo.ui.reference.olivar.OlivarReferenceScreen
import com.isivoltpro.maginaolivo.ui.theme.MaginaOlivoTheme
import org.junit.Rule
import org.junit.Test

class Phase3OlivarReferenceTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun olivarReferenceRendersCanonicalContent() {
        composeRule.setContent {
            MaginaOlivoTheme {
                OlivarReferenceScreen()
            }
        }

        composeRule.onNodeWithTag("olivar-reference-root").assertIsDisplayed()
        composeRule.onNodeWithText("Mis fincas").assertIsDisplayed()
        composeRule.onNodeWithText("La Solana").assertIsDisplayed()
    }

    @Test
    fun farmDetailReferenceRendersCanonicalContent() {
        composeRule.setContent {
            MaginaOlivoTheme {
                FarmDetailReferenceScreen()
            }
        }

        composeRule.onNodeWithTag("farm-detail-reference-root").assertIsDisplayed()
        composeRule.onNodeWithText("Parcela Norte").performScrollTo().assertIsDisplayed()
    }
}
