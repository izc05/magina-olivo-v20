package com.isivoltpro.maginaolivo

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.isivoltpro.maginaolivo.domain.weather.RadarFrame
import com.isivoltpro.maginaolivo.domain.weather.RadarFrames
import com.isivoltpro.maginaolivo.feature.home.RadarScreen
import com.isivoltpro.maginaolivo.feature.home.RadarUiState
import com.isivoltpro.maginaolivo.feature.home.radarCenter
import com.isivoltpro.maginaolivo.feature.maps.MapParcel
import com.isivoltpro.maginaolivo.ui.theme.MaginaOlivoTheme
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Phase 20B-radar (spec §10): time and source always shown; offline says it needs signal. */
class RadarScreenTest {
    @get:Rule val composeRule = createComposeRule()

    private val radar = RadarFrames(
        provider = "RainViewer",
        attribution = "Radar: RainViewer (rainviewer.com)",
        updatedAt = Instant.parse("2026-09-25T11:05:26Z"),
        frames = listOf(
            RadarFrame(Instant.parse("2026-09-25T10:50:00Z"), "https://tilecache.rainviewer.com/v2/radar/5cdb09f96f18/256/{z}/{x}/{y}/2/1_1.png"),
            RadarFrame(Instant.parse("2026-09-25T11:00:00Z"), "https://tilecache.rainviewer.com/v2/radar/5415fe0e827c/256/{z}/{x}/{y}/2/1_1.png"),
        ),
    )

    @Test fun offlineTheRadarSaysItNeedsAConnectionAndCanRetry() {
        var retries = 0
        show(RadarUiState.Unavailable, onRetry = { retries++ })
        composeRule.onNode(hasTestTag("radar-offline") and hasAnyDescendant(hasText("El radar necesita conexión"))).assertExists()
        composeRule.onNodeWithTag("radar-map").assertDoesNotExist()
        composeRule.onNode(hasText("Reintentar")).performClick()
        composeRule.runOnIdle { assertEquals(1, retries) }
    }

    @Test fun withoutConfigurationItSaysSo() {
        show(RadarUiState.NotConfigured)
        composeRule.onNodeWithTag("radar-not-configured").assertIsDisplayed()
    }

    @Test fun thePictureShowsItsTimeSourceAndFrames() {
        var frame = -1
        show(RadarUiState.Ready(radar, index = 1), onFrame = { frame = it })
        composeRule.onNodeWithTag("radar-time").assertTextContains("Radar de las 13:00")
        composeRule.onNodeWithTag("radar-attribution").assertTextContains("Radar: RainViewer (rainviewer.com)")
        composeRule.onNodeWithTag("radar-map").assertExists()
        composeRule.onNodeWithTag("radar-frame-slider").assertExists()
        composeRule.onNodeWithTag("radar-refresh").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(-1, frame) }
    }

    @Test fun anOlderFrameSaysHowMuchOlderItIs() {
        show(RadarUiState.Ready(radar, index = 0))
        composeRule.onNodeWithTag("radar-time").assertTextContains("Radar de las 12:50 · 10 min antes del último")
    }

    @Test fun theRadarCentresOnTheFarmOrOnTheHomeArea() {
        val square = """{"type":"Polygon","coordinates":[[[-3.40,37.80],[-3.38,37.80],[-3.38,37.82],[-3.40,37.82],[-3.40,37.80]]]}"""
        val centre = radarCenter(listOf(MapParcel("p", "Norte", square)))
        assertEquals(37.81, centre.latitude, 0.001)
        assertEquals(-3.39, centre.longitude, 0.001)
        assertEquals(37.73, radarCenter(emptyList()).latitude, 0.0)
    }

    private fun show(state: RadarUiState, onRetry: () -> Unit = {}, onFrame: (Int) -> Unit = {}) {
        composeRule.setContent {
            MaginaOlivoTheme {
                RadarScreen(state, parcels = emptyList(), onRetry = onRetry, onFrame = onFrame, zone = ZoneId.of("Europe/Madrid"))
            }
        }
    }
}
