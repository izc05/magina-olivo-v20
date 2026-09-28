package com.isivoltpro.maginaolivo

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.isivoltpro.maginaolivo.domain.feed.FeedLocation
import com.isivoltpro.maginaolivo.domain.feed.FeedState
import com.isivoltpro.maginaolivo.domain.weather.WeatherCondition
import com.isivoltpro.maginaolivo.domain.weather.WeatherNow
import com.isivoltpro.maginaolivo.feature.home.HomeScreen
import com.isivoltpro.maginaolivo.feature.home.HomeUiState
import com.isivoltpro.maginaolivo.ui.theme.MaginaOlivoTheme
import java.time.Instant
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Home stays usable while feeds load; weather lives in the hero and redundant shortcuts are gone. */
class HomeFeedsScreenTest {
    @get:Rule val composeRule = createComposeRule()

    private val now = Instant.parse("2026-11-26T13:00:00Z")
    private val bedmar = FeedLocation("Bedmar", "Jaén")

    @Test fun externalFeedsDoNotReplaceFarmSummaryAndQuickAccessIsRemoved() {
        show(UiPolishFixtures.home.copy(weatherLocation = bedmar, weather = FeedState.Unavailable))
        composeRule.onNodeWithTag("home-stats").assertIsDisplayed()
        composeRule.onNodeWithTag("home-campaign").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("home-weather-hero").assertIsDisplayed().assertTextContains("Bedmar", substring = true)
        composeRule.onNodeWithTag("home-quick-jornadas").assertDoesNotExist()
        composeRule.onNodeWithTag("home-quick-pesadas").assertDoesNotExist()
        composeRule.onNodeWithTag("home-no-farms").assertDoesNotExist()
        composeRule.onNodeWithTag("home-market").performScrollTo()
        composeRule.onNode(hasTestTag("home-market") and hasAnyDescendant(hasText("Sin fuente configurada."))).assertExists()
        composeRule.onNodeWithTag("home-cooperative").performScrollTo().assertIsDisplayed()
    }

    @Test fun heroWeatherShowsCurrentValueLocationAndCachedWarning() {
        val weather = WeatherNow(22, WeatherCondition.PARTLY_CLOUDY, 15, 11, now.minusSeconds(5 * 3600))
        show(
            UiPolishFixtures.home.copy(
                weatherLocation = bedmar,
                weather = FeedState.Value(weather, "AEMET", now.minusSeconds(5 * 3600), stale = true),
            ),
        )
        composeRule.onNodeWithTag("home-weather-hero").assertTextContains("22 °C · Parcialmente nublado")
            .assertTextContains("Bedmar", substring = true)
        composeRule.onNodeWithTag("home-weather-hero").assertTextContains("Antiguo")
    }

    @Test fun tappingHeroWeatherOpensTheWeek() {
        var opened = 0
        show(UiPolishFixtures.home.copy(weatherLocation = bedmar), onWeatherWeek = { opened++ })
        composeRule.onNodeWithTag("home-weather-hero").performClick()
        composeRule.runOnIdle { assertEquals(1, opened) }
    }

    @Test fun noLocationExplainsWhereToAddTheMunicipality() {
        show(UiPolishFixtures.home.copy(weather = FeedState.NoLocation))
        composeRule.onNodeWithTag("home-weather-hero").assertTextContains("Añade el municipio en Mi Campo")
    }

    @Test fun theHeaderFollowsCurrentSkyAndStaysStillInTests() {
        val rain = WeatherNow(14, WeatherCondition.RAIN, 80, 12, now.minusSeconds(600))
        show(UiPolishFixtures.home.copy(weatherLocation = bedmar, weather = FeedState.Value(rain, "AEMET", now.minusSeconds(600), stale = false)))
        composeRule.onNodeWithTag("home-weather-mood-rain-static").assertExists()
    }

    @Test fun anOutOfDateSkyDrawsNothingOverThePhoto() {
        val rain = WeatherNow(14, WeatherCondition.RAIN, 80, 12, now.minusSeconds(5 * 3600))
        show(UiPolishFixtures.home.copy(weatherLocation = bedmar, weather = FeedState.Value(rain, "AEMET", now.minusSeconds(5 * 3600), stale = true)))
        composeRule.onNodeWithTag("home-weather-mood-rain-static").assertDoesNotExist()
        composeRule.onNodeWithTag("home-weather-mood-rain-animated").assertDoesNotExist()
    }

    private fun show(state: HomeUiState, onWeatherWeek: () -> Unit = {}) {
        composeRule.setContent {
            MaginaOlivoTheme {
                HomeScreen(state, LocalTime.of(10, 0), {}, onWeatherWeek, {}, {}, feedNow = now, weatherMotion = false)
            }
        }
    }
}
