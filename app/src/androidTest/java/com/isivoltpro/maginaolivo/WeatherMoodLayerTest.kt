package com.isivoltpro.maginaolivo

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.isivoltpro.maginaolivo.domain.weather.WeatherMood
import com.isivoltpro.maginaolivo.feature.home.WeatherMoodLayer
import com.isivoltpro.maginaolivo.ui.theme.MaginaOlivoTheme
import org.junit.Rule
import org.junit.Test

/** Phase 20C: reduced motion draws one still frame for every look; no weather draws nothing. */
class WeatherMoodLayerTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun reducedMotionDrawsEveryLookStill() {
        composeRule.setContent {
            MaginaOlivoTheme {
                WeatherMood.entries.forEach { mood -> WeatherMoodLayer(mood, Modifier.size(120.dp), animate = false) }
            }
        }
        WeatherMood.entries.forEach { mood ->
            composeRule.onNodeWithTag("home-weather-mood-${mood.name.lowercase()}-static").assertExists()
        }
    }

    @Test fun unknownWeatherDrawsNothing() {
        composeRule.setContent { MaginaOlivoTheme { WeatherMoodLayer(null, Modifier.size(120.dp), animate = false) } }
        WeatherMood.entries.forEach { mood ->
            composeRule.onNodeWithTag("home-weather-mood-${mood.name.lowercase()}-static").assertDoesNotExist()
        }
    }
}
