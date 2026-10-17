package com.isivoltpro.maginaolivo

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.isivoltpro.maginaolivo.app.AppearanceMode
import com.isivoltpro.maginaolivo.feature.maps.FarmMapScreen
import com.isivoltpro.maginaolivo.feature.maps.FarmMapState
import com.isivoltpro.maginaolivo.feature.maps.GeoPoint
import com.isivoltpro.maginaolivo.feature.maps.MapFocus
import com.isivoltpro.maginaolivo.feature.maps.localMapPlaces
import com.isivoltpro.maginaolivo.ui.theme.MaginaOlivoTheme
import com.isivoltpro.maginaolivo.ui.theme.MoSurfaceTokens
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

/** Production screen/native map, synthetic search outcomes; no records or live place lookups. */
class MapSearchScreenTest {
    private val compose = createComposeRule()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(compose)
        .around(FailureEvidenceRule { compose.onRoot(useUnmergedTree = true).printToString() })
    @Test fun lightSearchKeepsErrorsAndCollapsesOnlyAfterResolution() = exercise(AppearanceMode.LIGHT)
    @Test fun darkSearchKeepsErrorsAndCollapsesOnlyAfterResolution() = exercise(AppearanceMode.DARK)

    private fun exercise(mode: AppearanceMode) {
        val state = mutableStateOf(FarmMapState())
        var allowResolution = false
        var coordinateCalls = 0
        compose.setContent {
            MaginaOlivoTheme(mode) {
                Box(Modifier.fillMaxSize().background(MoSurfaceTokens.appBackground).systemBarsPadding()) {
                    FarmMapScreen(state.value, onMode = {}, onSearchCoordinates = {
                        coordinateCalls++
                        state.value = state.value.copy(focus = MapFocus(GeoPoint(37.636, -3.480)))
                    }, onMyLocation = {}, onSearchPolygonParcel = { _, _, _, _ -> }, onSearchByReference = {},
                        onTapMap = { _, _ -> }, onTapParcel = {}, onImport = {}, onLink = {}, onOpenParcel = {},
                        onSearchPlace = { query ->
                            state.value = if (allowResolution) state.value.copy(
                                placeError = null, focus = MapFocus(requireNotNull(localMapPlaces(query).single().point)),
                            ) else state.value.copy(placeError = "No se puede consultar ese lugar ahora.")
                        },
                    )
                }
            }
        }
        compose.onNodeWithTag("farm-map-search-toggle").performClick()
        compose.onNodeWithTag("farm-map-place-query").performScrollTo().performTextReplacement("BEDMAR")
        compose.onNodeWithTag("farm-map-place-go").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithTag("farm-map-place-error").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("farm-map-place-query").assertTextContains("BEDMAR")
        assertEquals(0, coordinateCalls)
        capture(mode, "error-keeps-query")
        allowResolution = true
        compose.onNodeWithTag("farm-map-place-go").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("farm-map-place-query").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("farm-map-place-query").assertDoesNotExist()
        compose.onNodeWithTag("farm-map-search-toggle").assertIsDisplayed()
        capture(mode, "place-resolved-collapsed")
        compose.onNodeWithTag("farm-map-search-toggle").performClick()
        compose.onNodeWithTag("farm-map-coordinates").performScrollTo().performTextReplacement("37.636, -3.480")
        compose.onNodeWithTag("farm-map-go").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("farm-map-coordinates").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("farm-map-coordinates").assertDoesNotExist()
        assertEquals(1, coordinateCalls)
        capture(mode, "coordinates-resolved-collapsed")
    }

    private fun capture(mode: AppearanceMode, state: String) {
        compose.waitForIdle()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val metrics = context.resources.displayMetrics
        val width = (metrics.widthPixels / metrics.density).toInt()
        val scale = context.resources.configuration.fontScale
        val output = File(context.getExternalFilesDir(null), "map-search-evidence/${mode.name.lowercase()}-$state-${width}dp-font$scale.png")
        output.parentFile!!.mkdirs()
        val screenshot = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        output.outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
