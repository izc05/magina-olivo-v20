package com.isivoltpro.maginaolivo

import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import android.view.View
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.platform.app.InstrumentationRegistry
import com.isivoltpro.maginaolivo.app.AppearanceMode
import com.isivoltpro.maginaolivo.domain.parcel.Parcel
import com.isivoltpro.maginaolivo.domain.parcel.ParcelSource
import com.isivoltpro.maginaolivo.feature.maps.*
import com.isivoltpro.maginaolivo.navigation.RootDestination
import com.isivoltpro.maginaolivo.navigation.bottomNavigationRoots
import com.isivoltpro.maginaolivo.ui.components.*
import com.isivoltpro.maginaolivo.ui.theme.MaginaOlivoTheme
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import org.hamcrest.Matcher
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView

/** Native focused point, actual IME and subsequent user's pan/frame; no Room records. */
class B3FocusedPointViewportTest {
    @get:Rule val compose = createComposeRule()

    @Test fun coordinateFocusClearsPanelsAndViewportChangesPreserveLaterPanAndFrame() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand(command)
        ).bufferedReader().use { it.readText() }
        val previousIme = shell("settings get secure show_ime_with_hard_keyboard").trim()
        shell("settings put secure show_ime_with_hard_keyboard 1")
        try {
            val point = GeoPoint(37.636, -3.480)
            val parcel = Parcel(
                id = UUID.fromString("71100000-0000-0000-0000-000000000004"),
                workspaceId = UUID.randomUUID(), farmId = UUID.randomUUID(), displayName = "Parcela conservada",
                cadastralReference = null, cadastralPolygon = null, cadastralParcel = null,
                municipality = "Huelma", province = "Jaén", source = ParcelSource.MANUAL,
                geometryGeoJson = GEOMETRY, cadastralAreaM2 = null, managedAreaM2 = 9000.0,
                notes = null, archivedAt = null, version = 1,
            )
            val state = mutableStateOf(FarmMapState(myLocation = point))
            val appearance = mutableStateOf(AppearanceMode.DARK)
            val native = AtomicReference<MapLibreMap>()
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
                MaginaOlivoTheme(appearance.value) {
                    Scaffold(Modifier.fillMaxSize(), bottomBar = {
                        MoBottomBar(items, selectedIndex = 1, onSelected = {}, onAddRecord = {})
                    }) { padding ->
                        androidx.compose.foundation.layout.Box(Modifier.padding(padding).consumeWindowInsets(padding)) {
                            FarmMapScreen(state.value, {}, {
                                state.value = state.value.copy(focus = MapFocus(point))
                            }, {}, { _, _, _, _ -> }, {}, { _, _ -> }, {}, {}, {}, {})
                        }
                    }
                }
            }
            onView(isAssignableFrom(MapView::class.java)).perform(object : ViewAction {
                override fun getConstraints(): Matcher<View> = isAssignableFrom(MapView::class.java)
                override fun getDescription() = "Observe actual native focus projection and camera intention"
                override fun perform(uiController: UiController, view: View) {
                    (view as MapView).getMapAsync(native::set)
                }
            })
            compose.waitUntil(10_000) { native.get() != null }
            compose.onNodeWithTag("farm-map-layer").performClick()
            compose.onNodeWithTag("farm-map-base-NONE").performClick()
            compose.onNodeWithTag("farm-map-search-toggle").performClick()
            compose.onNodeWithTag("farm-map-coordinates").performClick().performTextInput("37.636,-3.480")
            compose.waitUntil(15_000) { shell("dumpsys input_method").contains("mInputShown=true") }
            compose.onNodeWithTag("farm-map-go").performClick()
            compose.waitUntil(10_000) {
                var focused = false
                compose.runOnIdle {
                    val camera = native.get().cameraPosition
                    focused = abs(camera.zoom - 16.5) < 0.05 &&
                        abs(requireNotNull(camera.target).latitude - point.latitude) < 0.000001 &&
                        abs(requireNotNull(camera.target).longitude - point.longitude) < 0.000001
                }
                focused
            }
            compose.waitForIdle()
            val headerBottom = compose.onNodeWithTag("farm-map-polygon").fetchSemanticsNode().boundsInRoot.bottom
            val mapBounds = compose.onNodeWithTag("parcel-map-view").fetchSemanticsNode().boundsInRoot
            val noticeTop = compose.onNodeWithTag("farm-map-my-location-shown").fetchSemanticsNode().boundsInRoot.top
            var nativeY = 0f
            compose.runOnIdle {
                val projected = native.get().projection.toScreenLocation(LatLng(point.latitude, point.longitude))
                nativeY = mapBounds.top + projected.y
            }
            assertTrue("Native focus y=$nativeY must clear search header bottom=$headerBottom", nativeY > headerBottom + 8f)
            assertTrue("Native focus y=$nativeY must clear bottom notice top=$noticeTop", nativeY < noticeTop - 8f)
            compose.waitUntil(10_000) {
                var rendered = false
                compose.runOnIdle {
                    val projected = native.get().projection.toScreenLocation(LatLng(point.latitude, point.longitude))
                    rendered = native.get().queryRenderedFeatures(projected, "my-location").isNotEmpty()
                }
                rendered
            }
            capture("focused-point-keyboard")
            var previousStyle: org.maplibre.android.maps.Style? = null
            compose.runOnIdle { previousStyle = native.get().style; appearance.value = AppearanceMode.LIGHT }
            compose.waitUntil(10_000) {
                var rendered = false
                compose.runOnIdle {
                    val map = native.get()
                    val projected = map.projection.toScreenLocation(LatLng(point.latitude, point.longitude))
                    rendered = map.style != null && map.style !== previousStyle &&
                        map.queryRenderedFeatures(projected, "my-location").isNotEmpty()
                }
                rendered
            }

            val laterPan = LatLng(37.648, -3.490)
            compose.runOnIdle { native.get().moveCamera(CameraUpdateFactory.newLatLngZoom(laterPan, 15.25)) }
            shell("input keyevent 4")
            compose.waitUntil(15_000) { !shell("dumpsys input_method").contains("mInputShown=true") }
            compose.waitForIdle()
            compose.runOnIdle {
                val camera = native.get().cameraPosition
                assertEquals("Closing IME must retain user's later pan", laterPan.latitude, requireNotNull(camera.target).latitude, 0.000001)
                assertEquals(laterPan.longitude, requireNotNull(camera.target).longitude, 0.000001)
                assertEquals("Closing IME must retain user's later zoom", 15.25, camera.zoom, 0.0001)
            }
            compose.runOnIdle { state.value = state.value.copy(parcels = listOf(parcel)) }
            compose.onNodeWithTag("farm-map-go").performClick()
            compose.waitUntil(10_000) {
                var focused = false
                compose.runOnIdle { focused = abs(native.get().cameraPosition.zoom - 16.5) < 0.05 }
                focused
            }
            compose.onNodeWithContentDescription("Encuadrar mis parcelas").performClick()
            var framedTarget: LatLng? = null
            var framedZoom = 0.0
            compose.runOnIdle { framedTarget = native.get().cameraPosition.target; framedZoom = native.get().cameraPosition.zoom }
            compose.onNodeWithTag("farm-map-search-toggle").performClick()
            compose.waitForIdle()
            compose.runOnIdle {
                val camera = native.get().cameraPosition
                assertEquals("Closing search must retain later parcel framing", requireNotNull(framedTarget).latitude, requireNotNull(camera.target).latitude, 0.000001)
                assertEquals(requireNotNull(framedTarget).longitude, requireNotNull(camera.target).longitude, 0.000001)
                assertEquals(framedZoom, camera.zoom, 0.0001)
            }
            capture("focused-point-after-frame")
        } finally {
            shell("input keyevent 4")
            shell(if (previousIme == "null") "settings delete secure show_ime_with_hard_keyboard" else "settings put secure show_ime_with_hard_keyboard $previousIme")
        }
    }

    private fun capture(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val file = File(instrumentation.targetContext.filesDir, "b3-maps-evidence/$name.png").apply { parentFile!!.mkdirs() }
        val image = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    companion object {
        private const val GEOMETRY = """{"type":"Polygon","coordinates":[[[-3.492,37.646],[-3.488,37.646],[-3.488,37.650],[-3.492,37.650],[-3.492,37.646]]]}"""
    }
}
