package com.isivoltpro.maginaolivo

import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
        composeRule.onNodeWithTag("weather-week-current").assertTextContains("22°").assertTextContains("Parcialmente nublado")
        composeRule.onNodeWithTag("weather-week-day-0").assertTextContains("13°", substring = true).assertTextContains("25°", substring = true)
            .assertTextContains("30 %", substring = true).assertTextContains("1,2 mm", substring = true)
        composeRule.onNodeWithTag("weather-week-day-1").assertTextContains("—", substring = true)
        composeRule.onNodeWithTag("weather-week-source").performScrollTo().assertIsDisplayed().assertTextContains("AEMET", substring = true)
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
        composeRule.onNodeWithTag("weather-week-radar").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(1, radarOpens) }
    }

    @Test fun unresolvedLocationIsExplained() {
        show(WeatherWeekUiState(locationAmbiguous = true, weather = FeedState.NoLocation))
        composeRule.onNodeWithText("Tus fincas están en varios municipios", substring = true).assertIsDisplayed()
    }

    @Test fun missingCacheExplainsThatConnectivityIsNeeded() {
        composeRule.setContent {
            MaginaOlivoTheme {
                WeatherWeekScreen(WeatherWeekUiState(weather = FeedState.Unavailable), now = now, onRadar = null)
            }
        }
        composeRule.onNodeWithText("Conéctate para cargar la previsión").assertIsDisplayed()
    }

    /** #345: the radar access is its own block (icon + title + description), not a plain button. */
    @Test fun radarAccessHasItsOwnIdentityAtLargeText() {
        val forecast = WeatherNow(18, WeatherCondition.RAIN, 70, 20, now)
        composeRule.setContent {
            val density = androidx.compose.ui.platform.LocalDensity.current
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density, fontScale = 1.3f),
            ) {
                MaginaOlivoTheme {
                    androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.width(360.dp)) {
                        WeatherWeekScreen(WeatherWeekUiState(location = location, weather = FeedState.Value(forecast, "AEMET", now, stale = false)), now = now, onRadar = {})
                    }
                }
            }
        }
        composeRule.onNodeWithTag("weather-week-radar").performScrollTo().assertIsDisplayed()
            .assertTextContains("Radar de lluvia", substring = true)
            .assertTextContains("Dónde llueve ahora", substring = true)
    }

    /** #315: «Actualizar» is offered with a place; a failed refresh keeps the saved value and says so. */
    @Test fun manualRefreshIsOfferedAndAFailureKeepsTheSavedValue() {
        val forecast = WeatherNow(18, WeatherCondition.CLOUDY, null, 20, now.minusSeconds(2 * 3600))
        var refreshes = 0
        composeRule.setContent {
            MaginaOlivoTheme {
                WeatherWeekScreen(
                    WeatherWeekUiState(location = location, weather = FeedState.Value(forecast, "MET Norway", now.minusSeconds(2 * 3600), stale = true), refreshFailed = true),
                    now = now, onRefresh = { refreshes++ },
                )
            }
        }
        composeRule.onNodeWithTag("weather-week-current").assertTextContains("18°")
        composeRule.onNodeWithTag("weather-week-refresh-failed").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("weather-week-refresh").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(1, refreshes) }
    }

    private fun show(state: WeatherWeekUiState, onRadar: (() -> Unit)? = null) {
        composeRule.setContent {
            MaginaOlivoTheme { WeatherWeekScreen(state, now = now, onRadar = onRadar) }
        }
    }
}
