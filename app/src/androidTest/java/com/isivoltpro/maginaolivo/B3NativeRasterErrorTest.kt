package com.isivoltpro.maginaolivo

import android.content.Context
import android.graphics.Bitmap
import android.view.View
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.platform.app.InstrumentationRegistry
import com.isivoltpro.maginaolivo.app.AppearanceMode
import com.isivoltpro.maginaolivo.domain.parcel.Parcel
import com.isivoltpro.maginaolivo.domain.parcel.ParcelSource
import com.isivoltpro.maginaolivo.feature.maps.FarmMapScreen
import com.isivoltpro.maginaolivo.feature.maps.FarmMapState
import com.isivoltpro.maginaolivo.ui.theme.MaginaOlivoTheme
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import org.hamcrest.Matcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.storage.FileSource
import org.maplibre.android.tile.TileOperation

/** Native failure, native saved feature and explicit fallback; no Room/user records are changed. */
class B3NativeRasterErrorTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private var observedView: MapView? = null
    private var observer: MapView.OnTileActionListener? = null

    @Before fun failOnlyIgnRequests() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            MapLibre.getInstance(context)
            FileSource.getInstance(context).setResourceTransform { _, url ->
                if (url.startsWith("https://www.ign.es/wmts/ign-base?")) {
                    // Closed local TLS port produces a real native connection failure, independent of IGN.
                    "https://127.0.0.1:1/b3-unavailable/${url.hashCode()}"
                } else url
            }
        }
    }

    @After fun restoreNativeProcessState() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            observer?.let { observedView?.removeOnTileActionListener(it) }
        }
        FileSource.getInstance(context).setResourceTransform { _, url -> url }
    }

    @Test fun lightRasterFailureLeavesTheSavedParcelUsable() = expectSafeFallback(AppearanceMode.LIGHT)
    @Test fun darkRasterFailureLeavesTheSavedParcelUsable() = expectSafeFallback(AppearanceMode.DARK)

    private fun expectSafeFallback(mode: AppearanceMode) {
        val parcel = Parcel(
            id = UUID.fromString("71100000-0000-0000-0000-000000000003"),
            workspaceId = UUID.randomUUID(), farmId = UUID.randomUUID(), displayName = "Parcela conservada",
            cadastralReference = null, cadastralPolygon = null, cadastralParcel = null,
            municipality = "Huelma", province = "Jaén", source = ParcelSource.MANUAL,
            geometryGeoJson = GEOMETRY, cadastralAreaM2 = null, managedAreaM2 = 9000.0,
            notes = null, archivedAt = null, version = 1,
        )
        val nativeError = AtomicReference<String>()
        val nativeMap = AtomicReference<MapLibreMap>()
        compose.setContent {
            MaginaOlivoTheme(mode) {
                FarmMapScreen(
                    state = FarmMapState(parcels = listOf(parcel)),
                    onMode = {}, onSearchCoordinates = {}, onMyLocation = {},
                    onSearchPolygonParcel = { _, _, _, _ -> }, onSearchByReference = {},
                    onTapMap = { _, _ -> }, onTapParcel = {}, onImport = {},
                    onLink = {}, onOpenParcel = {},
                )
            }
        }
        onView(isAssignableFrom(MapView::class.java)).perform(object : ViewAction {
            override fun getConstraints(): Matcher<View> = isAssignableFrom(MapView::class.java)
            override fun getDescription() = "Observe actual MapLibre tile errors and rendered saved geometry"
            override fun perform(uiController: UiController, view: View) {
                val mapView = view as MapView
                observedView = mapView
                observer = MapView.OnTileActionListener { operation, _, _, _, _, _, source ->
                    if (operation == TileOperation.Error && source == "base") nativeError.set(source)
                }.also(mapView::addOnTileActionListener)
                mapView.getMapAsync { map ->
                    nativeMap.set(map)
                    // A new tile request after the observer is attached, still centred on the parcel.
                    map.moveCamera(CameraUpdateFactory.newLatLngZoom(CENTRE, 16.5))
                }
            }
        })
        compose.waitUntil(15_000) { nativeError.get() == "base" }
        // Baseline RED must be this assertion, after actual TileOperation.Error was observed.
        compose.onNodeWithTag("farm-map-tile-error").assertIsDisplayed()
        assertSavedGeometry(nativeMap, parcel.id.toString())
        compose.waitForIdle()
        saveWindow("b3-${mode.name.lowercase()}-native-error")
        compose.onNodeWithTag("farm-map-offline-fallback").assertIsDisplayed().performClick()
        compose.waitUntil(10_000) {
            var localOnly = false
            compose.runOnIdle { localOnly = nativeMap.get()?.style?.let { it.getSource("base") == null } == true }
            localOnly
        }
        compose.onNodeWithTag("farm-map-tile-error").assertDoesNotExist()
        assertSavedGeometry(nativeMap, parcel.id.toString())
        assertEquals(GEOMETRY, parcel.geometryGeoJson)
        saveWindow("b3-${mode.name.lowercase()}-native-fallback")
    }

    private fun assertSavedGeometry(reference: AtomicReference<MapLibreMap>, id: String) {
        var found = false
        compose.waitUntil(10_000) {
            compose.runOnIdle {
                val map = reference.get()
                found = map != null && map.queryRenderedFeatures(map.projection.toScreenLocation(CENTRE), "parcels-fill")
                    .any { it.getStringProperty("id") == id }
            }
            found
        }
        assertTrue("Native saved parcel remains rendered and hit-testable", found)
    }

    private fun saveWindow(name: String) {
        val output = File(context.filesDir, "b3-maps-evidence/$name.png").apply { parentFile!!.mkdirs() }
        val image = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        output.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    companion object {
        private val CENTRE = LatLng(37.636, -3.480)
        private const val GEOMETRY = """{"type":"Polygon","coordinates":[[[-3.482,37.634],[-3.478,37.634],[-3.478,37.638],[-3.482,37.638],[-3.482,37.634]]]}"""
    }
}
