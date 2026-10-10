package com.isivoltpro.maginaolivo

import android.graphics.Bitmap
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.isivoltpro.maginaolivo.app.AppearanceMode
import com.isivoltpro.maginaolivo.feature.maps.FarmMapScreen
import com.isivoltpro.maginaolivo.feature.maps.FarmMapState
import com.isivoltpro.maginaolivo.feature.maps.LocationProblem
import com.isivoltpro.maginaolivo.navigation.RootDestination
import com.isivoltpro.maginaolivo.navigation.bottomNavigationRoots
import com.isivoltpro.maginaolivo.ui.components.MoBottomBar
import com.isivoltpro.maginaolivo.ui.components.MoBottomBarItem
import com.isivoltpro.maginaolivo.ui.components.MoIcons
import com.isivoltpro.maginaolivo.ui.theme.MaginaOlivoTheme
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Real screen and production Scaffold/bottom bar; no arbitrary simulated viewport padding. */
class B3MapViewportTest {
    @get:Rule val compose = createComposeRule()

    @Test fun deniedGpsKeepsTheNativeFrameControlOutsideItsPanel() {
        show(FarmMapState(locationProblem = LocationProblem.PERMISSION))
        compose.onNodeWithTag("farm-map-location-problem").assertIsDisplayed()
        compose.waitForIdle()
        capture("gps-denied-controls")
        val panel = compose.onNodeWithTag("farm-map-location-problem").fetchSemanticsNode().boundsInRoot
        val frame = compose.onNodeWithContentDescription("Encuadrar mis parcelas").fetchSemanticsNode().boundsInRoot
        assertTrue("Frame control $frame must remain above the location panel $panel", frame.bottom <= panel.top)
    }

    @Test fun searchKeepsTheMapMenuAsHighAsTheOtherActions() {
        show(FarmMapState())
        compose.onNodeWithTag("farm-map-search-toggle").performClick()
        compose.onNodeWithTag("farm-map-coordinates").assertIsDisplayed()
        compose.waitForIdle()
        capture("search-controls")
        val location = compose.onNodeWithTag("farm-map-my-location").fetchSemanticsNode().boundsInRoot
        val menu = compose.onNodeWithTag("farm-map-layer").fetchSemanticsNode().boundsInRoot
        assertTrue("Map menu $menu must not wrap taller than the location action $location", menu.height <= location.height + 1f)
        compose.onNodeWithTag("farm-map-search-toggle").performClick()
        compose.onNodeWithTag("farm-map-coordinates").assertDoesNotExist()
    }

    @Test fun sigpacIsAnExplicitReferenceAndDisabledInLocalOnlyMode() {
        show(FarmMapState())
        compose.onNodeWithTag("farm-map-layer").performClick()
        compose.onNodeWithText("Ver recintos SIGPAC (referencia)").assertIsDisplayed().performClick()
        compose.onNodeWithText("FEGA/MAPA", substring = true).assertIsDisplayed()
        compose.onNodeWithTag("farm-map-layer").performClick()
        compose.onNodeWithText("Ocultar recintos SIGPAC (referencia)").assertIsDisplayed()
        compose.onNodeWithTag("farm-map-base-NONE").performClick()
        compose.waitForIdle()
        val location = compose.onNodeWithTag("farm-map-my-location").fetchSemanticsNode().boundsInRoot
        val menu = compose.onNodeWithTag("farm-map-layer").fetchSemanticsNode().boundsInRoot
        assertTrue("Local-only menu $menu must fit the action row $location", menu.height <= location.height + 1f)
        compose.onNodeWithTag("farm-map-layer").performClick()
        compose.onNodeWithText("Ocultar recintos SIGPAC (referencia)").assertIsNotEnabled()
        compose.onNodeWithText("FEGA/MAPA", substring = true).assertDoesNotExist()
    }

    private fun show(state: FarmMapState) {
        val items = bottomNavigationRoots.map { destination ->
            MoBottomBarItem(destination.label, destination.symbol, destination.isPrimaryAction,
                when (destination) {
                    RootDestination.Home -> MoIcons.Home
                    RootDestination.Olivar -> MoIcons.Tree
                    RootDestination.Notebook -> MoIcons.Notebook
                    RootDestination.Alerts -> MoIcons.Bell
                    RootDestination.Profile -> MoIcons.Person
                })
        }
        compose.setContent {
            MaginaOlivoTheme(AppearanceMode.DARK) {
                Scaffold(Modifier.fillMaxSize(), bottomBar = {
                    MoBottomBar(items, selectedIndex = 1, onSelected = {}, onAddRecord = {})
                }) { padding ->
                    androidx.compose.foundation.layout.Box(Modifier.padding(padding).consumeWindowInsets(padding)) {
                        FarmMapScreen(state, {}, {}, {}, { _, _, _, _ -> }, {}, { _, _ -> }, {}, {}, {}, {})
                    }
                }
            }
        }
    }

    private fun capture(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.filesDir, "b3-maps-evidence/$name.png").apply { parentFile!!.mkdirs() }
        val image = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
