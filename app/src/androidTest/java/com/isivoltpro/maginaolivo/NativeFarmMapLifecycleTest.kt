package com.isivoltpro.maginaolivo

import android.graphics.Bitmap
import java.io.File
import androidx.test.platform.app.InstrumentationRegistry
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.view.WindowCompat
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.GeneralClickAction
import androidx.test.espresso.action.Press
import androidx.test.espresso.action.Tap
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import com.isivoltpro.maginaolivo.app.AppearanceMode
import com.isivoltpro.maginaolivo.app.LocalPersistence
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.data.local.MaginaOlivoDatabase
import com.isivoltpro.maginaolivo.domain.farm.Farm
import com.isivoltpro.maginaolivo.domain.farm.FarmRepository
import com.isivoltpro.maginaolivo.domain.parcel.Parcel
import com.isivoltpro.maginaolivo.domain.parcel.ParcelRepository
import com.isivoltpro.maginaolivo.domain.parcel.ParcelSource
import com.isivoltpro.maginaolivo.domain.workspace.WorkspaceRepository
import com.isivoltpro.maginaolivo.feature.maps.FarmMapRoute
import com.isivoltpro.maginaolivo.navigation.RootDestination
import com.isivoltpro.maginaolivo.navigation.bottomNavigationRoots
import com.isivoltpro.maginaolivo.ui.components.MoBottomBarItem
import com.isivoltpro.maginaolivo.ui.components.MoIcons
import com.isivoltpro.maginaolivo.ui.components.MoBottomBar
import com.isivoltpro.maginaolivo.ui.theme.MaginaOlivoTheme
import java.lang.reflect.Proxy
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.sources.GeoJsonSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.hamcrest.Matcher
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView

/** Real route/ViewModels/native map and activity rotation, with read-only synthetic repositories. */
class NativeFarmMapLifecycleTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val workspace = UUID.randomUUID()
    private val farmA = farm("Lifecycle A")
    private val farmB = farm("Lifecycle B")
    private val a = parcel(farmA, "Contour A", 37.636, -3.480)
    private val b = parcel(farmB, "Contour B", 37.839, -3.352)
    private val currentFarm = mutableStateOf(farmA.id)
    private val currentLocate = mutableStateOf<UUID?>(null)
    private var appearance = AppearanceMode.LIGHT
    private lateinit var database: MaginaOlivoDatabase
    private lateinit var persistence: LocalPersistence

    @Before fun isolatedPersistence() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), MaginaOlivoDatabase::class.java).build()
        val farms = object : FarmRepository by rejectCalls<FarmRepository>() {
            override fun observeById(farmId: UUID): Flow<Farm?> = flowOf(when (farmId) {
                farmA.id -> farmA
                farmB.id -> farmB
                else -> throw AssertionError("Unknown test farm")
            })
        }
        val parcels = object : ParcelRepository by rejectCalls<ParcelRepository>() {
            override fun observeById(parcelId: UUID): Flow<Parcel?> = flowOf(when (parcelId) {
                a.id -> a
                b.id -> b
                else -> throw AssertionError("Unknown test parcel")
            })
            override fun observeActive(farmId: UUID): Flow<List<Parcel>> = flowOf(when (farmId) {
                farmA.id -> listOf(a)
                farmB.id -> listOf(b)
                else -> throw AssertionError("Unknown test farm")
            })
        }
        val workspaces = object : WorkspaceRepository {
            override suspend fun ensureLocalWorkspace(): AppResult<UUID> = AppResult.Success(workspace)
            override fun currentOwnerId(): UUID = workspace
        }
        persistence = LocalPersistence(database, farms, rejectCalls(), parcels, rejectCalls(),
            rejectCalls(), rejectCalls(), rejectCalls(), rejectCalls(), rejectCalls(), rejectCalls(), rejectCalls(),
            rejectCalls(), rejectCalls(), rejectCalls(), workspaces)
    }
    @After fun closeOnlyTestDatabase() {
        try { runCatching { capture("end-of-case") } } finally { database.close() }
    }
    @Test fun lightFarmChangeAndRealRotationKeepOnlyCurrentContour() = verify(AppearanceMode.LIGHT)
    @Test fun darkFarmChangeAndRealRotationKeepOnlyCurrentContour() = verify(AppearanceMode.DARK)

    private fun attachScene(mode: AppearanceMode) {
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
        compose.activityRule.scenario.onActivity { activity ->
            WindowCompat.setDecorFitsSystemWindows(activity.window, false)
            activity.setContent {
                MaginaOlivoTheme(mode) {
                    Scaffold(Modifier.fillMaxSize(), bottomBar = {
                        MoBottomBar(items, selectedIndex = 1, onSelected = {}, onAddRecord = {})
                    }) { padding ->
                        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
                            FarmMapRoute(currentFarm.value, persistence, {}, {}, locateParcelId = currentLocate.value)
                        }
                    }
                }
            }
        }
    }

    private fun verify(mode: AppearanceMode) {
        appearance = mode
        compose.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        compose.waitUntil(15_000) {
            var portrait = false
            compose.activityRule.scenario.onActivity { portrait = it.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT }
            portrait
        }
        attachScene(mode)
        compose.onNodeWithTag("farm-map-layer").performClick()
        compose.onNodeWithTag("farm-map-base-NONE").performClick()
        val first = nativeMap()
        waitForCurrentContour(first, a, null)
        val centre = LatLng(37.636, -3.480)
        onView(isAssignableFrom(MapView::class.java)).perform(GeneralClickAction(Tap.SINGLE, { view ->
            val point = first.get().projection.toScreenLocation(centre)
            val origin = IntArray(2)
            view.getLocationOnScreen(origin)
            floatArrayOf(origin[0] + point.x, origin[1] + point.y)
        }, Press.FINGER))
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("farm-map-parcel-card").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("farm-map-parcel-card").assertIsDisplayed()
        compose.onNodeWithText(a.displayName).assertIsDisplayed()
        compose.runOnIdle { currentFarm.value = farmB.id }
        compose.waitUntil(10_000) {
            runCatching { compose.onNodeWithText(farmB.name).fetchSemanticsNode() }.isSuccess
        }
        compose.onNodeWithTag("farm-map-parcel-card").assertDoesNotExist()
        waitForCurrentContour(first, b, a)
        for ((orientation, configuration) in listOf(
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE to Configuration.ORIENTATION_LANDSCAPE,
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT to Configuration.ORIENTATION_PORTRAIT)) {
            compose.activityRule.scenario.onActivity { it.requestedOrientation = orientation }
            compose.waitUntil(15_000) {
                var matches = false
                compose.activityRule.scenario.onActivity { matches = it.resources.configuration.orientation == configuration }
                matches
            }
            attachScene(mode)
            compose.onNodeWithText(farmB.name).assertIsDisplayed()
            compose.onNodeWithTag("farm-map-parcel-card").assertDoesNotExist()
            waitForCurrentContour(nativeMap(), b, a)
            if (configuration == Configuration.ORIENTATION_LANDSCAPE) {
                compose.onNodeWithTag("farm-map-layer").performClick()
                compose.onNodeWithTag("farm-map-mode-add").performClick()
                compose.waitUntil(10_000) { compose.onAllNodesWithTag("farm-map-mode-view").fetchSemanticsNodes().isNotEmpty() }
                val batchMap = nativeMap()
                waitForCurrentContour(batchMap, b, a)
                compose.onNodeWithTag("farm-map-mode-view").assertIsDisplayed().performClick()
                compose.runOnIdle { currentLocate.value = b.id }
                compose.waitUntil(10_000) {
                    runCatching { compose.onNodeWithText("Ubicar «${b.displayName}»").fetchSemanticsNode() }.isSuccess
                }
                waitForCurrentContour(nativeMap(), b, a)
                compose.runOnIdle { currentLocate.value = null }
                compose.waitUntil(10_000) {
                    runCatching { compose.onNodeWithText(farmB.name).fetchSemanticsNode() }.isSuccess
                }
            }
        }
    }

    private fun nativeMap(): AtomicReference<MapLibreMap> {
        val map = AtomicReference<MapLibreMap>()
        onView(isAssignableFrom(MapView::class.java)).perform(object : ViewAction {
            override fun getConstraints(): Matcher<View> = isAssignableFrom(MapView::class.java)
            override fun getDescription() = "Read the current native map after route/configuration changes"
            override fun perform(uiController: UiController, view: View) { (view as MapView).getMapAsync(map::set) }
        })
        compose.waitUntil(10_000) { map.get() != null }
        return map
    }
    private fun waitForCurrentContour(map: AtomicReference<MapLibreMap>, parcel: Parcel, previous: Parcel?) {
        waitForRenderedSource(map, parcel)
        capture(if (previous == null) "farm-a" else "farm-b-before-probe")
        val header = compose.onNodeWithTag("farm-map-header").fetchSemanticsNode().boundsInRoot
        val credit = compose.onNodeWithContentDescription("Atribución del mapa").fetchSemanticsNode().boundsInRoot
        val scale = compose.onNodeWithTag("map-scale").fetchSemanticsNode().boundsInRoot
        val footerTop = minOf(credit.top, scale.top)
        val mapBounds = compose.onNodeWithTag("parcel-map-view").fetchSemanticsNode().boundsInRoot
        val evidenceContext = ApplicationProvider.getApplicationContext<Context>()
        File(evidenceContext.getExternalFilesDir(null), "map-lifecycle-evidence/${appearance.name.lowercase()}-control-layout-${evidenceContext.resources.configuration.orientation}-font${evidenceContext.resources.configuration.fontScale}.txt")
            .writeText("header=$header credit=$credit scale=$scale map=$mapBounds\n" + compose.onRoot(useUnmergedTree = true).printToString())
        for (description in listOf("Acercar", "Alejar", "Encuadrar mis parcelas")) {
            val control = compose.onNodeWithContentDescription(description).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertTrue("$description must fit between the header and credit", control.top >= header.bottom && control.bottom <= footerTop)
            assertTrue("$description must fit inside the actual native viewport", control.left >= mapBounds.left && control.right <= mapBounds.right)
        }
        if (previous != null) {
            val framesAtOldFarm = AtomicInteger()
            val oldCentre = LatLng(37.636, -3.480)
            onView(isAssignableFrom(MapView::class.java)).perform(object : ViewAction {
                override fun getConstraints(): Matcher<View> = isAssignableFrom(MapView::class.java)
                override fun getDescription() = "Confirm a rendered frame at the previous farm before querying its source"
                override fun perform(uiController: UiController, view: View) {
                    (view as MapView).addOnDidFinishRenderingFrameListener { fully, _, _ ->
                        val camera = map.get().cameraPosition
                        if (fully && abs(camera.zoom - 17.0) < 0.000001 && camera.target?.let {
                            abs(it.latitude - oldCentre.latitude) < 0.000001 && abs(it.longitude - oldCentre.longitude) < 0.000001
                        } == true) framesAtOldFarm.incrementAndGet()
                    }
                }
            })
            var baseline = 0
            compose.runOnIdle {
                map.get().moveCamera(CameraUpdateFactory.newLatLngZoom(oldCentre, 17.0))
                baseline = framesAtOldFarm.get()
            }
            compose.waitUntil(15_000) { framesAtOldFarm.get() > baseline }
            compose.runOnIdle {
                val source = requireNotNull(map.get().style?.getSourceAs<GeoJsonSource>("saved-parcels"))
                val ids = source.querySourceFeatures(Expression.literal(true)).map { it.getStringProperty("id") }.toSet()
                assertTrue("The previous farm is absent even after rendering its own area", previous.id.toString() !in ids)
                assertTrue("No unrelated farm identity is present in the loaded source", ids.all { it == parcel.id.toString() })
            }
            compose.onNodeWithContentDescription("Encuadrar mis parcelas").performClick()
            waitForRenderedSource(map, parcel)
            capture("farm-b-after-frame")
        }
    }
    private fun waitForRenderedSource(map: AtomicReference<MapLibreMap>, parcel: Parcel) {
        var observation = "No native observation"
        try {
            compose.waitUntil(15_000) {
                var rendered = false
                compose.runOnIdle {
                    val centre = if (parcel.id == a.id) LatLng(37.636, -3.480) else LatLng(37.839, -3.352)
                    val point = map.get().projection.toScreenLocation(centre)
                    val hits = map.get().queryRenderedFeatures(point, "parcels-fill").map { it.getStringProperty("id") }.toSet()
                    val source = map.get().style?.getSourceAs<GeoJsonSource>("saved-parcels")
                    val sourceIds = source?.querySourceFeatures(Expression.literal(true))?.map { it.getStringProperty("id") }?.toSet()
                    val base = map.get().style?.getSource("base")
                    observation = "expected=${parcel.id} camera=${map.get().cameraPosition} projectedCentre=$point hits=$hits loadedSourceIds=$sourceIds hasBase=${base != null}"
                    rendered = hits == setOf(parcel.id.toString()) && sourceIds == setOf(parcel.id.toString()) && base == null
                }
                rendered
            }
        } catch (failure: androidx.compose.ui.test.ComposeTimeoutException) {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val config = context.resources.configuration
            val file = File(context.getExternalFilesDir(null), "map-lifecycle-evidence/${appearance.name.lowercase()}-render-timeout-${config.orientation}-sw${config.smallestScreenWidthDp}dp-font${config.fontScale}.txt")
            file.parentFile!!.mkdirs()
            file.writeText(observation + "\n" + compose.onRoot(useUnmergedTree = true).printToString())
            capture("render-timeout")
            throw failure
        }
    }
    private fun capture(stage: String) {
        compose.waitForIdle()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val config = context.resources.configuration
        val orientation = if (config.orientation == Configuration.ORIENTATION_LANDSCAPE) "landscape" else "portrait"
        val file = File(context.getExternalFilesDir(null), "map-lifecycle-evidence/${appearance.name.lowercase()}-$stage-${if (currentLocate.value != null) "locate" else if (compose.onAllNodesWithTag("farm-map-mode-view").fetchSemanticsNodes().isNotEmpty()) "add" else "view"}-$orientation-sw${config.smallestScreenWidthDp}dp-font${config.fontScale}.png")
        file.parentFile!!.mkdirs()
        val bitmap = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    private fun farm(name: String) = Farm(UUID.randomUUID(), workspace, name, null, "Bedmar", "Jaén", null, null, 1, 9000.0, null, null, 1)
    private fun parcel(farm: Farm, name: String, latitude: Double, longitude: Double): Parcel {
        val geometry = """{"type":"Polygon","coordinates":[[[${longitude - .0006},${latitude - .0006}],[${longitude + .0006},${latitude - .0006}],[${longitude + .0006},${latitude + .0006}],[${longitude - .0006},${latitude + .0006}],[${longitude - .0006},${latitude - .0006}]]]}"""
        return Parcel(UUID.randomUUID(), workspace, farm.id, name, null, null, null, "Bedmar", "Jaén", ParcelSource.MANUAL, geometry, null, 9000.0, null, null, 1)
    }
    private inline fun <reified T> rejectCalls(): T = Proxy.newProxyInstance(T::class.java.classLoader,
        arrayOf(T::class.java)) { _, method, _ -> throw AssertionError("No persistence writes or unrelated reads: ${method.name}") } as T
}
