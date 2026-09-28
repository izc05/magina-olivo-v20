package com.isivoltpro.maginaolivo

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.isivoltpro.maginaolivo.domain.feed.FeedLocation
import com.isivoltpro.maginaolivo.domain.feed.FeedState
import com.isivoltpro.maginaolivo.domain.weather.WeatherCondition
import com.isivoltpro.maginaolivo.domain.weather.WeatherDayForecast
import com.isivoltpro.maginaolivo.domain.weather.WeatherNow
import com.isivoltpro.maginaolivo.feature.home.WeatherWeekScreen
import com.isivoltpro.maginaolivo.feature.home.WeatherWeekUiState
import com.isivoltpro.maginaolivo.ui.theme.MaginaOlivoTheme
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class WeatherWeekScreenTest {
    @get:Rule val composeRule = createComposeRule()
    private val now = Instant.parse("2026-09-28T10:00:00Z")
    private val location = FeedLocation("Bedmar y Garcíez", "Jaén")

    @Test fun showsOnlyReceivedMeasuresAndAttributesTheirSource() {
        val today = LocalDate.of(2026, 9, 28)
        val forecast = WeatherNow(
            22, WeatherCondition.PARTLY_CLOUDY, null, 14, now,
            updatedAt = now.minusSeconds(1800),
            attribution = "© AEMET",
            daily = listOf(
                WeatherDayForecast(today, 13, 25, WeatherCondition.PARTLY_CLOUDY, 30, 1.2, 18),
                WeatherDayForecast(today.plusDays(1), null, null, null, null, null, null),
            ),
        )
        show(WeatherWeekUiState(location = location, weather = FeedState.Value(forecast, "AEMET", now, stale = false)))
        composeRule.onNodeWithTag("weather-week-root").assertIsDisplayed()
        composeRule.onNodeWithTag("weather-week-location").assertTextContains("Bedmar", substring = true)
        composeRule.onNodeWithTag("weather-week-day-0").assertTextContains("13°", substring = true).assertTextContains("25°", substring = true)
            .assertTextContains("30 %", substring = true).assertTextContains("1,2 mm", substring = true)
        composeRule.onNodeWithTag("weather-week-day-1").assertTextContains("—", substring = true)
        composeRule.onNodeWithTag("weather-week-source").assertTextContains("AEMET", substring = true)
            .assertTextContains("© AEMET", substring = true)
    }

    @Test fun cachedWeekSaysItIsOldAndRadarOpensTheExistingRoute() {
        val forecast = WeatherNow(
            18, WeatherCondition.RAIN, null, 20, now.minusSeconds(5 * 3600),
            daily = listOf(WeatherDayForecast(LocalDate.of(2026, 9, 28), 14, 20, WeatherCondition.RAIN, null, null, 20)),
        )
        var radarOpens = 0
        show(WeatherWeekUiState(location = location, weather = FeedState.Value(forecast, "MET Norway", now.minusSeconds(5 * 3600), stale = true)), onRadar = { radarOpens++ })
        composeRule.onNodeWithTag("weather-week-stale").assertIsDisplayed()
        composeRule.onNodeWithTag("weather-week-radar").performClick()
        composeRule.runOnIdle { assertEquals(1, radarOpens) }
    }

    @Test fun unresolvedLocationIsExplained() {
        show(WeatherWeekUiState(locationAmbiguous = true, weather = FeedState.NoLocation))
        composeRule.onNodeWithText("No se puede elegir un único municipio", substring = true).assertIsDisplayed()
    }

    @Test fun missingCacheExplainsThatConnectivityIsNeeded() {
        composeRule.setContent {
            MaginaOlivoTheme {
                WeatherWeekScreen(WeatherWeekUiState(weather = FeedState.Unavailable), now = now, onRadar = null)
            }
        }
        composeRule.onNodeWithText("Conéctate para cargar la previsión").assertIsDisplayed()
    }

    private fun show(state: WeatherWeekUiState, onRadar: (() -> Unit)? = null) {
        composeRule.setContent {
            MaginaOlivoTheme { WeatherWeekScreen(state, now = now, onRadar = onRadar) }
        }
    }
}
