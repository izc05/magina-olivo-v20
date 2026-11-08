package com.isivoltpro.maginaolivo

import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.isivoltpro.maginaolivo.feature.parcels.IrrigationCalculatorSheet
import com.isivoltpro.maginaolivo.ui.theme.MaginaOlivoTheme
import org.junit.Rule
import org.junit.Test

class IrrigationCalculatorSheetTest {
    @get:Rule val compose = createComposeRule()

    private fun type(tag: String, value: String) {
        compose.onNodeWithTag(tag).performScrollTo().performClick()
        compose.onNodeWithTag(tag).assertIsFocused().performTextInput(value)
        compose.onNodeWithTag(tag).assertTextContains(value, substring = true)
    }

    @Test fun emptyFieldsCalculateNothing() {
        compose.setContent { MaginaOlivoTheme { IrrigationCalculatorSheet(null) } }
        compose.onNodeWithTag("calc-incomplete").performScrollTo()
        compose.onNodeWithTag("calc-results").assertDoesNotExist()
    }

    @Test fun everyFieldTakesFocusAndTextAndTheResultStaysReachable() {
        compose.setContent { MaginaOlivoTheme { IrrigationCalculatorSheet(null) } }
        type("calc-trees", "123")
        type("calc-emitters", "4")
        type("calc-flow", "4,5")
        type("calc-hours", "1")
        type("calc-minutes", "30")
        type("calc-target", "40")
        type("calc-weekly", "3")
        compose.onNodeWithTag("calc-results").performScrollTo()
        compose.onNodeWithTag("calc-drip-only").assertDoesNotExist()
    }
}
