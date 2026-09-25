package com.isivoltpro.maginaolivo

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
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
import org.junit.Rule
import org.junit.Test

/**
 * Phase 20A — Gate 20 core: with every external feed failing, Inicio still shows the farm,
 * the campaign, the next work and the quick access; a feed value always shows its source,
 * its age and whether it is out of date.
 */
class HomeFeedsScreenTest {
    @get:Rule val composeRule = createComposeRule()

    private val now = Instant.parse("2026-11-26T13:00:00Z")
    private val bedmar = FeedLocation("Bedmar", "Jaén")

    @Test fun everyFeedFailingLeavesTheFarmFullyUsable() {
        show(UiPolishFixtures.home.copy(weatherLocation = bedmar, weather = FeedState.Unavailable))
        composeRule.onNodeWithTag("home-stats").assertIsDisplayed()
        composeRule.onNodeWithTag("home-campaign").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("home-quick-harvest").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("home-weather-unavailable").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("home-market").performScrollTo()
        // The card is a container; its texts are its children.
        composeRule.onNode(hasTestTag("home-market") and hasAnyDescendant(hasText("Sin fuente configurada."))).assertExists()
        composeRule.onNodeWithTag("home-cooperative").performScrollTo().assertIsDisplayed()
    }

    @Test fun aStaleValueShowsItsSourceAgeAndWarning() {
        val weather = WeatherNow(22, WeatherCondition.PARTLY_CLOUDY, 15, 11, now.minusSeconds(5 * 3600))
        show(
            UiPolishFixtures.home.copy(
                weatherLocation = bedmar,
                weather = FeedState.Value(weather, "AEMET", now.minusSeconds(5 * 3600), stale = true),
            ),
        )
        composeRule.onNodeWithTag("home-weather-value").performScrollTo()
        composeRule.onNode(hasTestTag("home-weather-value") and hasAnyDescendant(hasText("22 °C · Parcialmente nublado"))).assertExists()
        composeRule.onNodeWithTag("home-weather-stale").assertIsDisplayed()
        composeRule.onNodeWithTag("home-weather-source").assertTextContains("Fuente: AEMET · Actualizado hace 5 h")
    }

    @Test fun theFallbackProviderIsNamedAndCredited() {
        val weather = WeatherNow(
            17, WeatherCondition.RAIN, null, 14, now.minusSeconds(1800),
            updatedAt = now.minusSeconds(40 * 60),
            attribution = "Datos de MET Norway (Instituto Meteorológico de Noruega), licencia CC BY 4.0.",
        )
        show(UiPolishFixtures.home.copy(weatherLocation = bedmar, weather = FeedState.Value(weather, "MET Norway", now.minusSeconds(60), stale = false)))
        composeRule.onNodeWithTag("home-weather-source").performScrollTo()
            .assertTextContains("Fuente: MET Norway · Actualizado hace 40 min")
        composeRule.onNodeWithTag("home-weather-attribution").assertTextContains("CC BY 4.0", substring = true)
    }

    @Test fun noPlaceAsksForTheFarmMunicipality() {
        show(UiPolishFixtures.home.copy(weather = FeedState.NoLocation))
        composeRule.onNodeWithTag("home-weather-no-location").performScrollTo().assertIsDisplayed()
    }

    private fun show(state: HomeUiState) {
        composeRule.setContent {
            MaginaOlivoTheme {
                HomeScreen(state, LocalTime.of(10, 0), {}, {}, {}, {}, {}, {}, now)
            }
        }
    }
}
