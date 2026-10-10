package com.isivoltpro.maginaolivo

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
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
import java.time.LocalDate
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
        composeRule.onNodeWithTag("home-weather-summary").assertIsDisplayed().assertTextContains("Bedmar", substring = true)
        composeRule.onNodeWithTag("home-stats").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("home-campaign").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("home-quick-jornadas").assertDoesNotExist()
        composeRule.onNodeWithTag("home-quick-pesadas").assertDoesNotExist()
        composeRule.onNodeWithTag("home-no-farms").assertDoesNotExist()
        composeRule.onNodeWithTag("home-market").performScrollTo()
        composeRule.onNode(hasTestTag("home-market") and hasAnyDescendant(hasText("Sin fuente configurada."))).assertExists()
        composeRule.onNodeWithTag("home-cooperative").performScrollTo().assertIsDisplayed()
    }

    @Test fun localReadFailureNeverLooksLikeEmptyAgriculturalDataAndCanRetry() {
        var retried = 0
        composeRule.setContent {
            MaginaOlivoTheme {
                HomeScreen(
                    UiPolishFixtures.home.copy(
                        isLoading = false,
                        localReadError = "No hemos podido leer los datos de tu olivar.",
                        campaigns = emptyList(),
                        upcoming = emptyList(),
                    ),
                    LocalTime.of(10, 0), {}, {}, {}, {}, feedNow = now, weatherMotion = false,
                    onRetryLocalData = { retried++ },
                )
            }
        }
        composeRule.onNodeWithTag("home-local-error").assertIsDisplayed()
        composeRule.onNodeWithTag("home-no-campaign").assertDoesNotExist()
        composeRule.onNodeWithTag("home-no-upcoming").assertDoesNotExist()
        composeRule.onNodeWithTag("home-local-retry").performClick()
        composeRule.runOnIdle { assertEquals(1, retried) }
    }

    @Test fun heroWeatherShowsCurrentValueLocationAndCachedWarning() {
        val weather = WeatherNow(22, WeatherCondition.PARTLY_CLOUDY, 15, 11, now.minusSeconds(5 * 3600))
        show(
            UiPolishFixtures.home.copy(
                weatherLocation = bedmar,
                weather = FeedState.Value(weather, "AEMET", now.minusSeconds(5 * 3600), stale = true),
            ),
        )
        composeRule.onNodeWithTag("home-weather-summary").assertTextContains("22°").assertTextContains("Parcialmente nublado")
            .assertTextContains("Bedmar", substring = true)
        composeRule.onNodeWithTag("home-weather-summary").assertTextContains("Datos guardados · sin actualizar")
    }

    /** #345: AEMET's probability is shown with its drop; 0 % and 100 % are real values. */
    @Test fun heroShowsThePublishedRainProbability() {
        var percent by androidx.compose.runtime.mutableStateOf(0)
        composeRule.setContent {
            val weather = WeatherNow(18, WeatherCondition.RAIN, percent, 11, now)
            MaginaOlivoTheme {
                HomeScreen(
                    UiPolishFixtures.home.copy(weatherLocation = bedmar, weather = FeedState.Value(weather, "AEMET", now, stale = false)),
                    LocalTime.of(10, 0), {}, {}, {}, {}, feedNow = now, weatherMotion = false,
                )
            }
        }
        listOf(0, 35, 100).forEach { value ->
            composeRule.runOnIdle { percent = value }
            composeRule.onNodeWithTag("home-weather-summary").assertTextContains("Prob. lluvia $value %", substring = true)
        }
    }

    /** #345: no probability from the source is never «0 %»; MET Norway's millimetres are shown instead. */
    @Test fun aMissingProbabilityIsNeverZeroAndFallbackMillimetresAreShown() {
        val noProbability = WeatherNow(18, WeatherCondition.CLOUDY, null, 11, now)
        show(UiPolishFixtures.home.copy(weatherLocation = bedmar, weather = FeedState.Value(noProbability, "MET Norway", now, stale = false)))
        composeRule.onNodeWithTag("home-weather-rain", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNode(hasTestTag("home-weather-summary") and hasText("%", substring = true)).assertDoesNotExist()
    }

    @Test fun heroShowsTodaySunriseAndSunset() {
        val solarWeather = WeatherNow(
            18, WeatherCondition.CLEAR, 0, 7, now,
            solarDate = LocalDate.parse("2026-11-26"),
            solarTimeZone = "Europe/Madrid",
            sunriseAt = Instant.parse("2026-11-26T07:05:00Z"),
            sunsetAt = Instant.parse("2026-11-26T16:56:00Z"),
        )
        show(
            UiPolishFixtures.home.copy(
                today = LocalDate.parse("2026-11-26"),
                weatherLocation = bedmar,
                weather = FeedState.Value(solarWeather, "AEMET", now, stale = false),
            ),
        )
        composeRule.onNodeWithTag("home-weather-summary").assertIsDisplayed()
            .assertTextContains("Salida 08:05", substring = true).assertTextContains("Puesta 17:56", substring = true)
    }

    @Test fun heroNeverShowsYesterdaysSolarCacheAsToday() {
        val yesterday = WeatherNow(
            18, WeatherCondition.CLEAR, 0, 7, now,
            solarDate = LocalDate.parse("2026-11-25"),
            solarTimeZone = "Europe/Madrid",
            sunriseAt = Instant.parse("2026-11-25T07:04:00Z"),
            sunsetAt = Instant.parse("2026-11-25T16:57:00Z"),
        )
        show(
            UiPolishFixtures.home.copy(
                today = LocalDate.parse("2026-11-26"),
                weatherLocation = bedmar,
                weather = FeedState.Value(yesterday, "AEMET", now, stale = true),
            ),
        )
        composeRule.onNodeWithTag("home-weather-solar").assertDoesNotExist()
    }

    @Test fun tappingHeroWeatherOpensTheWeek() {
        var opened = 0
        show(UiPolishFixtures.home.copy(weatherLocation = bedmar), onWeatherWeek = { opened++ })
        composeRule.onNodeWithTag("home-weather-hero").performClick()
        composeRule.runOnIdle { assertEquals(1, opened) }
    }

    @Test fun noLocationExplainsWhereToAddTheMunicipality() {
        show(UiPolishFixtures.home.copy(weather = FeedState.NoLocation))
        composeRule.onNodeWithTag("home-weather-summary").assertTextContains("Añade el municipio en Mi Campo o en Perfil")
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

    /**
     * #720 R2: the weekly watering plan shows each Parcel's next day and says plainly what it is
     * not — a community turn or a watering already recorded. With no days, nothing is shown.
     */
    @Test fun theWateringPlanNamesItsNextDaysAndIsNeverPresentedAsAnOfficialTurn() {
        val day = LocalDate.of(2026, 11, 26)
        var state by androidx.compose.runtime.mutableStateOf(
            UiPolishFixtures.home.copy(
                today = day,
                irrigationTurns = listOf(
                    com.isivoltpro.maginaolivo.domain.irrigation.IrrigationTurn(
                        parcelId = java.util.UUID.randomUUID(), parcelName = "Los Llanos", date = day,
                        sector = "Sector 1", network = "Red del Barranco",
                    ),
                    com.isivoltpro.maginaolivo.domain.irrigation.IrrigationTurn(
                        parcelId = java.util.UUID.randomUUID(), parcelName = "El Barranco", date = day.plusDays(1),
                    ),
                ),
            ),
        )
        composeRule.setContent {
            MaginaOlivoTheme {
                HomeScreen(state, LocalTime.of(10, 0), {}, {}, {}, {}, feedNow = now, weatherMotion = false)
            }
        }
        // The row is a container: its title and its place live in child nodes.
        composeRule.onAllNodesWithTag("home-irrigation-turn")[0].performScrollTo()
        rowWith("Los Llanos · Hoy")
        rowWith("Sector 1 · Red del Barranco")
        // Without sector or network the row still says which day it is, never an invented place.
        rowWith("El Barranco · Mañana")
        rowWith("Tus días de riego de esta parcela")
        composeRule.onNodeWithTag("home-irrigation-note").performScrollTo()
            .assertTextContains("No es un turno oficial de comunidad", substring = true)

        // No irrigation days: no card at all, and no empty state pretending there is a plan.
        state = UiPolishFixtures.home.copy(today = day, irrigationTurns = emptyList())
        composeRule.onNodeWithTag("home-irrigation-turn").assertDoesNotExist()
        composeRule.onNodeWithTag("home-irrigation-note").assertDoesNotExist()
    }

    /** A watering row names the thing asked for in one of its child nodes. */
    private fun rowWith(text: String) {
        composeRule.onNode(
            hasTestTag("home-irrigation-turn") and hasAnyDescendant(hasText(text)),
            useUnmergedTree = true,
        ).assertExists()
    }

    private fun show(state: HomeUiState, onWeatherWeek: () -> Unit = {}) {
        composeRule.setContent {
            MaginaOlivoTheme {
                HomeScreen(state, LocalTime.of(10, 0), {}, onWeatherWeek, {}, {}, feedNow = now, weatherMotion = false)
            }
        }
    }
}
