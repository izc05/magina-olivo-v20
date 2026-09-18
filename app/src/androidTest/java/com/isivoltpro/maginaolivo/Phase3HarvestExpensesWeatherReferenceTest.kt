package com.isivoltpro.maginaolivo

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.isivoltpro.maginaolivo.ui.reference.expenses.ExpensesDocumentsReferenceScreen
import com.isivoltpro.maginaolivo.ui.reference.harvest.HarvestReferenceScreen
import com.isivoltpro.maginaolivo.ui.reference.weather.WeatherMarketReferenceScreen
import com.isivoltpro.maginaolivo.ui.theme.MaginaOlivoTheme
import org.junit.Rule
import org.junit.Test

class Phase3HarvestExpensesWeatherReferenceTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun harvestReferenceRenders() {
        composeRule.setContent {
            MaginaOlivoTheme {
                HarvestReferenceScreen()
            }
        }
        composeRule.onNodeWithTag("harvest-reference-root").assertIsDisplayed()
        composeRule.onNodeWithText("Cosecha").assertIsDisplayed()
        composeRule.onNodeWithText("Registrar entrega").assertIsDisplayed()
    }

    @Test
    fun expensesReferenceRenders() {
        composeRule.setContent {
            MaginaOlivoTheme {
                ExpensesDocumentsReferenceScreen()
            }
        }
        composeRule.onNodeWithTag("expenses-reference-root").assertIsDisplayed()
        composeRule.onNodeWithText("Gastos y documentos").assertIsDisplayed()
        composeRule.onNodeWithText("Documentos recientes").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun weatherReferenceRenders() {
        composeRule.setContent {
            MaginaOlivoTheme {
                WeatherMarketReferenceScreen()
            }
        }
        composeRule.onNodeWithTag("weather-market-reference-root").assertIsDisplayed()
        composeRule.onNodeWithText("Tiempo y mercado").assertIsDisplayed()
        composeRule.onNodeWithText("Mercado del aceite").assertIsDisplayed()
    }
}
