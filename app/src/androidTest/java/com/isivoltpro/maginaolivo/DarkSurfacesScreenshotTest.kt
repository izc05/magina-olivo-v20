package com.isivoltpro.maginaolivo

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.isivoltpro.maginaolivo.app.AppearanceMode
import com.isivoltpro.maginaolivo.domain.feed.FeedState
import com.isivoltpro.maginaolivo.domain.market.OilCategory
import com.isivoltpro.maginaolivo.domain.market.OilMarketSeries
import com.isivoltpro.maginaolivo.domain.market.OilObservation
import com.isivoltpro.maginaolivo.feature.home.*
import com.isivoltpro.maginaolivo.feature.profile.*
import com.isivoltpro.maginaolivo.ui.components.*
import com.isivoltpro.maginaolivo.ui.theme.MaginaOlivoTheme
import com.isivoltpro.maginaolivo.ui.theme.MoDarkColorScheme
import com.isivoltpro.maginaolivo.ui.theme.MoLightColorScheme
import java.io.File
import java.math.BigDecimal
import java.time.LocalTime
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Explicit DARK-2 harness; synthetic feeds, no Room writes, no production release-guard bypass. */
class DarkSurfacesScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private val mode = AppearanceMode.valueOf(
        InstrumentationRegistry.getArguments().getString("appearance", "DARK"),
    )

    @Test fun homePhotoAndForecastActionRemainReachable() {
        var alerts = 0
        show {
            HomeScreen(
                UiPolishFixtures.home.copy(
                    today = WeatherVisualFixtures.today,
                    weatherLocation = WeatherVisualFixtures.location,
                    weather = WeatherVisualFixtures.fresh,
                ),
                LocalTime.of(12, 0), {}, {}, {}, {},
                feedNow = WeatherVisualFixtures.now, weatherMotion = false,
                onAlerts = { alerts++ },
            )
        }
        compose.onNodeWithTag("home-open-alerts").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, alerts) }
        capture("home-reference-root", "home-fixture")
    }

    @Test fun forecastCardsAndAttributionRemainReachable() {
        show {
            WeatherWeekScreen(
                WeatherWeekUiState(location = WeatherVisualFixtures.location, weather = WeatherVisualFixtures.fresh),
                now = WeatherVisualFixtures.now,
                onBack = {}, onRadar = {},
            )
        }
        capture("weather-week-root", "forecast-top-fixture")
        compose.onNodeWithTag("weather-week-source").performScrollTo().assertIsDisplayed()
        capture("weather-week-root", "forecast-source-fixture")
    }

    @Test fun profileAndMunicipalitySheetUseTheActiveTheme() {
        show {
            ProfileScreen(
                "DARK-2 QA", true, {}, {},
                myProfile = {
                    MyProfileSection(MyProfileUiState(isLoading = false), { _, _ -> }, {}, {}, {})
                },
            )
        }
        capture("profile-root", "profile")
        compose.onNodeWithTag("profile-municipality").performScrollTo().performClick()
        compose.onNodeWithTag("profile-municipality-field").assertIsDisplayed()
        capture("profile-location-sheet", "profile-sheet")
    }

    @Test fun marketEmptyStateHasTheActiveBackground() {
        var official by mutableStateOf<FeedState<OilMarketSeries>>(FeedState.Unavailable)
        show { OilMarketScreen(official, pulse = null) }
        expectBackground("oil-market-root")
        capture("oil-market-root", "market-offline")
        val week = WeatherVisualFixtures.today.minusDays(7)
        val series = OilMarketSeries(
            "visual-fixture", "Datos ficticios para comprobar el diseño", "ES-AN", "Andalucía", "ALMAZARA_OR_BODEGA",
            listOf(OilCategory.AOVE, OilCategory.AOV, OilCategory.AOL).mapIndexed { index, category ->
                val price = BigDecimal("3.50").subtract(BigDecimal(index).movePointLeft(1))
                OilObservation(category, week, week.plusDays(6), price, price, "EUR_PER_KG")
            },
        )
        compose.runOnIdle { official = FeedState.Value(series, series.sourceName, WeatherVisualFixtures.now, stale = false) }
        compose.onNodeWithTag("oil-market-chart").performScrollTo().assertIsDisplayed()
        capture("oil-market-root", "market-chart-fixture")
    }

    @Test fun radarOfflineHasTheActiveBackground() {
        show { RadarScreen(RadarUiState.Unavailable, emptyList(), {}, {}) }
        expectBackground("radar-root")
        capture("radar-root", "radar-offline")
    }

    @Test fun commonControlsAndCentralActionRemainOperable() {
        var adds = 0
        show {
            Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag("dark2-controls")) {
                Column(Modifier.weight(1f).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MoPrimaryButton("Guardar", {})
                    MoSecondaryButton("Cancelar", {})
                    MoTextField("", {}, "Municipio")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MoIconBadge(MoIcons.Delivery)
                        MoIconBadge(MoIcons.Weather)
                        MoIconBadge(MoIcons.People)
                    }
                    MoStatusChip("Aviso", tone = MoStatusTone.Warning)
                    MoKpiMetric("Pesadas", "120 kg", kind = MoKpiKind.PESADAS, icon = MoIcons.Delivery)
                }
                MoBottomBar(
                    listOf(
                        MoBottomBarItem("Inicio", "", icon = MoIcons.Home),
                        MoBottomBarItem("Mi Campo", "", icon = MoIcons.Tree),
                        MoBottomBarItem("Cuaderno", "", icon = MoIcons.Document),
                        MoBottomBarItem("Perfil", "", icon = MoIcons.Person),
                    ), 0, {}, onAddRecord = { adds++ },
                )
            }
        }
        compose.onNodeWithTag("bottom-add-record").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, adds) }
        capture("dark2-controls", "components")
    }

    private fun show(content: @Composable () -> Unit) {
        compose.setContent { MaginaOlivoTheme(mode) { content() } }
        compose.waitForIdle()
    }

    private fun expectBackground(tag: String) {
        val bitmap = compose.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
        val scheme = if (mode == AppearanceMode.DARK) MoDarkColorScheme else MoLightColorScheme
        assertEquals("$tag background must follow appearance", scheme.background.toArgb(), bitmap.getPixel(bitmap.width - 2, bitmap.height - 2))
    }

    private fun capture(tag: String, name: String) {
        compose.waitForIdle()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = context.resources.configuration
        val output = File(context.filesDir, "dark2-evidence").apply { mkdirs() }
        val file = File(output, "$name-${mode.name.lowercase()}-${config.screenWidthDp}dp-font${(config.fontScale * 100).roundToInt()}.png")
        val bitmap = compose.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
        file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        check(file.length() > 0)
    }
}
