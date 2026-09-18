package com.isivoltpro.maginaolivo

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.isivoltpro.maginaolivo.ui.reference.campaign.CampaignReferenceScreen
import com.isivoltpro.maginaolivo.ui.reference.register.RegisterActivityReferenceScreen
import com.isivoltpro.maginaolivo.ui.theme.MaginaOlivoTheme
import org.junit.Rule
import org.junit.Test

class Phase3CampaignRegisterReferenceTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun campaignReferenceRendersCanonicalSections() {
        composeRule.setContent {
            MaginaOlivoTheme {
                CampaignReferenceScreen()
            }
        }

        composeRule.onNodeWithTag("campaign-reference-root").assertIsDisplayed()
        composeRule.onNodeWithText("Campaña actual").assertIsDisplayed()
        composeRule.onNodeWithText("Evolución de producción").assertIsDisplayed()
    }

    @Test
    fun registerReferenceRendersTypedForm() {
        composeRule.setContent {
            MaginaOlivoTheme {
                RegisterActivityReferenceScreen()
            }
        }

        composeRule.onNodeWithTag("register-reference-root").assertIsDisplayed()
        composeRule.onNodeWithTag("activity-type-Treatment").assertIsSelected()
        composeRule.onNodeWithText("Tipo de actuación").assertIsDisplayed()
        composeRule.onNodeWithText("Guardar actuación").performScrollTo().assertIsDisplayed()
    }
}
