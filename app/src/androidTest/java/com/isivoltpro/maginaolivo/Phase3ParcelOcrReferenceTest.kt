package com.isivoltpro.maginaolivo

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.isivoltpro.maginaolivo.ui.reference.ocr.DeliveryOcrReviewReferenceScreen
import com.isivoltpro.maginaolivo.ui.reference.parcel.ParcelDetailReferenceScreen
import com.isivoltpro.maginaolivo.ui.theme.MaginaOlivoTheme
import org.junit.Rule
import org.junit.Test

class Phase3ParcelOcrReferenceTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun parcelDetailReferenceRenders() {
        composeRule.setContent {
            MaginaOlivoTheme {
                ParcelDetailReferenceScreen()
            }
        }
        composeRule.onNodeWithTag("parcel-detail-reference-root").assertIsDisplayed()
        composeRule.onNodeWithText("Parcela Norte").assertIsDisplayed()
        composeRule.onNodeWithText("Histórico").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun ocrReviewReferenceRendersUnconfirmedState() {
        composeRule.setContent {
            MaginaOlivoTheme {
                DeliveryOcrReviewReferenceScreen()
            }
        }
        composeRule.onNodeWithTag("ocr-review-reference-root").assertIsDisplayed()
        composeRule.onNodeWithText("Por revisar").assertIsDisplayed()
        composeRule.onNodeWithText("Confirmar entrega").performScrollTo().assertIsDisplayed()
    }
}
