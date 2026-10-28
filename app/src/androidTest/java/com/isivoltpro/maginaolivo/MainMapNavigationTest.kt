package com.isivoltpro.maginaolivo

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.platform.app.InstrumentationRegistry
import com.isivoltpro.maginaolivo.app.AndroidOnboardingStateStore
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.core.dispatchers.DefaultAppDispatchers
import com.isivoltpro.maginaolivo.core.id.UuidGenerator
import com.isivoltpro.maginaolivo.core.time.SystemAppClock
import com.isivoltpro.maginaolivo.data.local.MaginaOlivoDatabase
import com.isivoltpro.maginaolivo.data.local.entity.LocalMetadata
import com.isivoltpro.maginaolivo.data.local.entity.WorkspaceEntity
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstFarmRepository
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstParcelRepository
import com.isivoltpro.maginaolivo.domain.farm.NewFarm
import com.isivoltpro.maginaolivo.domain.parcel.NewParcel
import java.io.File
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.sources.GeoJsonSource

/** Actual MainActivity/NavController/Room; original DB/preferences are restored by the outer rule. */
class MainMapNavigationTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var farmA: UUID
    private lateinit var farmB: UUID
    private lateinit var parcelA: UUID
    private lateinit var parcelB: UUID
    private val seed = object : TestRule {
        override fun apply(base: Statement, description: Description): Statement = object : Statement() {
            override fun evaluate() {
                runBlocking {
                    val db = MaginaOlivoDatabase.getInstance(context)
                    val now = Instant.now()
                    val workspace = UUID.randomUUID()
                    db.workspaceDao().upsert(WorkspaceEntity(workspace, "Navegación mapa sintética", UUID.randomUUID(), "ES", "Europe/Madrid", "es-ES", "EUR", LocalMetadata(now, now)))
                    val farms = OfflineFirstFarmRepository(db, SystemAppClock(), UuidGenerator(), DefaultAppDispatchers())
                    val parcels = OfflineFirstParcelRepository(db, SystemAppClock(), UuidGenerator(), DefaultAppDispatchers())
                    farmA = ok(farms.create(NewFarm(workspace, "Finca navegación A sintética")))
                    farmB = ok(farms.create(NewFarm(workspace, "Finca navegación B sintética")))
                    parcelA = ok(parcels.create(NewParcel(farmA, "Parcela navegación A sintética", geometryGeoJson = square(37.636, -3.480))))
                    parcelB = ok(parcels.create(NewParcel(farmB, "Parcela navegación B sintética", geometryGeoJson = square(37.823, -3.411))))
                }
                check(context.getSharedPreferences(AndroidOnboardingStateStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
                    .edit().putBoolean(AndroidOnboardingStateStore.KEY_COMPLETED, true).commit())
                base.evaluate()
            }
        }
    }
    @get:Rule val rules: TestRule = RuleChain.outerRule(IsolatedAppStateRule()).around(seed).around(compose)

    @Test fun actualNavigationSwitchesFarmAndRecreatesOnlyItsOwnParcel() {
        waitTag("bottom-Mi Campo")
        compose.onNodeWithTag("bottom-Mi Campo").performClick()
        openMap(farmA, "Finca navegación A sintética")
        assertSource(nativeMap(), parcelA)
        capture("main-farm-a")
        backToFarmList()
        openMap(farmB, "Finca navegación B sintética")
        assertSource(nativeMap(), parcelB)
        compose.onNodeWithText("Parcela navegación A sintética").assertDoesNotExist()
        capture("main-farm-b")
        compose.activityRule.scenario.recreate()
        waitTag("farm-map-root")
        assertHeader("Finca navegación B sintética")
        assertSource(nativeMap(), parcelB)
        capture("main-farm-b-recreated")
        backToFarmList()
        waitTag("farm-" + farmA)
        waitTag("farm-" + farmB)
        runBlocking {
            val db = MaginaOlivoDatabase.getInstance(context)
            assertNotNull(db.farmDao().findById(farmA))
            assertNotNull(db.farmDao().findById(farmB))
            assertNotNull(db.parcelDao().findById(parcelA))
            assertNotNull(db.parcelDao().findById(parcelB))
        }
    }

    private fun openMap(farm: UUID, name: String) {
        waitTag("farms-root")
        // During return navigation the list root can exist before its cards are
        // available in the merged semantics tree. Await the exact interaction target.
        waitTag("farm-" + farm)
        compose.onNodeWithTag("farm-" + farm).performScrollTo().assertIsDisplayed().performClick()
        waitTag("farm-section-parcels")
        compose.onNodeWithTag("farm-section-parcels").performScrollTo().performClick()
        waitTag("add-parcel")
        compose.onNodeWithTag("add-parcel").performClick()
        waitTag("add-parcel-map")
        compose.onNodeWithTag("add-parcel-map").performClick()
        waitTag("farm-map-layer")
        assertHeader(name)
        compose.onNodeWithTag("farm-map-layer").performClick()
        compose.onNodeWithTag("farm-map-base-NONE").performClick()
    }

    private fun assertHeader(name: String) {
        compose.waitUntil(15_000) {
            compose.onAllNodes(hasText(name, substring = true) and hasAnyAncestor(hasTestTag("farm-map-header")), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun backToFarmList() {
        repeat(4) {
            if (runCatching { compose.onAllNodesWithTag("farms-root").fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)) return
            pressBack()
            compose.waitForIdle()
        }
        waitTag("farms-root")
    }

    private fun nativeMap(): AtomicReference<MapLibreMap> {
        val result = AtomicReference<MapLibreMap>()
        onView(isAssignableFrom(MapView::class.java)).check { view, error ->
            if (error != null) throw error
            (view as MapView).getMapAsync(result::set)
        }
        compose.waitUntil(15_000) { result.get() != null }
        return result
    }

    private fun assertSource(map: AtomicReference<MapLibreMap>, parcel: UUID) {
        compose.waitUntil(15_000) {
            var ids: Set<String>? = null
            var visible = false
            compose.runOnIdle {
                ids = map.get().style?.getSourceAs<GeoJsonSource>("saved-parcels")
                    ?.querySourceFeatures(Expression.literal(true))?.map { it.getStringProperty("id") }?.toSet()
                val centre = if (parcel == parcelA) LatLng(37.636, -3.480) else LatLng(37.823, -3.411)
                visible = map.get().queryRenderedFeatures(map.get().projection.toScreenLocation(centre), "parcels-fill")
                    .any { it.getStringProperty("id") == parcel.toString() }
                assertNull("The chosen local base survives actual navigation/recreation", map.get().style?.getSource("base"))
            }
            ids == setOf(parcel.toString()) && visible
        }
    }

    private fun waitTag(tag: String) = compose.waitUntil(15_000) {
        runCatching { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
    }

    private fun capture(stage: String) {
        compose.waitForIdle()
        val config = context.resources.configuration
        val file = File(context.getExternalFilesDir(null), "map-main-navigation-evidence/" + stage + "-sw" + config.smallestScreenWidthDp + "dp-font" + config.fontScale + ".png")
        file.parentFile!!.mkdirs()
        val image = checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        file.outputStream().use { check(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }
    private fun square(latitude: Double, longitude: Double): String =
        "{\"type\":\"Polygon\",\"coordinates\":[[[" + (longitude - .0006) + "," + (latitude - .0006) + "],[" + (longitude + .0006) + "," + (latitude - .0006) + "],[" + (longitude + .0006) + "," + (latitude + .0006) + "],[" + (longitude - .0006) + "," + (latitude + .0006) + "],[" + (longitude - .0006) + "," + (latitude - .0006) + "]]]}"
    private fun <T> ok(result: AppResult<T>): T = (result as AppResult.Success).value
}
