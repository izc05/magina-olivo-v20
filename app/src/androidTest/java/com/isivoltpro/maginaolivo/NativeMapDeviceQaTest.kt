package com.isivoltpro.maginaolivo

import android.content.Context
import android.accessibilityservice.AccessibilityService
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.net.ConnectivityManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.WindowCompat
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.platform.app.InstrumentationRegistry
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
import com.isivoltpro.maginaolivo.ui.components.MoBottomBar
import com.isivoltpro.maginaolivo.ui.components.MoBottomBarItem
import com.isivoltpro.maginaolivo.ui.components.MoIcons
import com.isivoltpro.maginaolivo.ui.theme.MaginaOlivoTheme
import java.io.File
import java.lang.reflect.Proxy
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Point

/** One real route, explicit external device states. verify-map-device restores permissions.
 * The ordinary suite executes the baseline; CI additionally executes every device mode.
 * Test GPS replaces the emulator provider temporarily, never supplies a physical result.
 */
class NativeMapDeviceQaTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val mode = InstrumentationRegistry.getArguments().getString("mapDeviceQaMode") ?: "baseline"
    private val theme = if (InstrumentationRegistry.getArguments().getString("mapDeviceQaTheme") == "dark") AppearanceMode.DARK else AppearanceMode.LIGHT
    private val workspace = UUID.randomUUID()
    private val farm = Farm(UUID.randomUUID(), workspace, "Finca GPS sintética", null, null, null, null, null, 1, 9000.0, null, null, 1)
    private val geometry = """{"type":"Polygon","coordinates":[[[-3.4806,37.6354],[-3.4794,37.6354],[-3.4794,37.6366],[-3.4806,37.6366],[-3.4806,37.6354]]]}"""
    private val parcel = Parcel(UUID.randomUUID(), workspace, farm.id, "Parcela guardada sintética", null, null, null, null, null, ParcelSource.MANUAL, geometry, null, 9000.0, null, null, 1)
    private lateinit var database: MaginaOlivoDatabase
    private lateinit var persistence: LocalPersistence
    private var provider: LocationManager? = null
    private val updates = Handler(Looper.getMainLooper())

    @Before fun readOnlyFixture() {
        database = Room.inMemoryDatabaseBuilder(context, MaginaOlivoDatabase::class.java).build()
        val farms = object : FarmRepository by rejectCalls<FarmRepository>() {
            override fun observeById(farmId: UUID): Flow<Farm?> = flowOf(farm)
        }
        val parcels = object : ParcelRepository by rejectCalls<ParcelRepository>() {
            override fun observeActive(farmId: UUID): Flow<List<Parcel>> = flowOf(listOf(parcel))
        }
        val workspaces = object : WorkspaceRepository {
            override suspend fun ensureLocalWorkspace(): AppResult<UUID> = AppResult.Success(workspace)
            override fun currentOwnerId(): UUID = workspace
        }
        persistence = LocalPersistence(database, farms, rejectCalls(), parcels, rejectCalls(), rejectCalls(),
            rejectCalls(), rejectCalls(), rejectCalls(), rejectCalls(), rejectCalls(), rejectCalls(),
            rejectCalls(), rejectCalls(), rejectCalls(), workspaces)
    }

    @After fun restoreOnlyFixture() {
        try {
            if (mode != "restore-accuracy") runCatching { capture("end") }
        } finally {
            // The sender runs on the main looper and reposts itself. Removing callbacks
            // from the instrumentation thread can miss a sender already executing,
            // which then publishes after the test provider has been removed.
            instrumentation.runOnMainSync {
                updates.removeCallbacksAndMessages(null)
                provider?.removeTestProvider(LocationManager.GPS_PROVIDER)
                provider = null
            }
            database.close()
        }
    }

    @Test fun realMapForExplicitDeviceState() {
        if (mode == "restore-accuracy") {
            // Test APK only. AOSP's selected-location-accuracy bit is not accepted by `pm`
            // on API35; restore only this bit for this DEV package under shell permission.
            assertEquals("com.isivoltpro.maginaolivo.dev", context.packageName)
            android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("getprop ro.kernel.qemu"))
                .bufferedReader().use { assertEquals("1", it.readText().trim()) }
            val mask = 1 shl 19
            val value = InstrumentationRegistry.getArguments().getString("mapAccuracyFlag")!!.toInt()
            require(value == 0 || value == mask)
            instrumentation.uiAutomation.adoptShellPermissionIdentity("android.permission.GRANT_RUNTIME_PERMISSIONS", "android.permission.REVOKE_RUNTIME_PERMISSIONS")
            try {
                val manager = context.packageManager
                val update = manager.javaClass.getMethod("updatePermissionFlags", String::class.java, String::class.java,
                    Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, android.os.UserHandle::class.java)
                update.invoke(manager, android.Manifest.permission.ACCESS_FINE_LOCATION, context.packageName, mask, value, android.os.Process.myUserHandle())
                val read = manager.javaClass.getMethod("getPermissionFlags", String::class.java, String::class.java, android.os.UserHandle::class.java)
                val actual = read.invoke(manager, android.Manifest.permission.ACCESS_FINE_LOCATION, context.packageName, android.os.Process.myUserHandle()) as Int
                assertEquals("Restore exactly the original accuracy-selection bit", value, actual and mask)
            } finally {
                instrumentation.uiAutomation.dropShellPermissionIdentity()
            }
            return
        }
        if (mode in listOf("precise", "grant", "rotation", "coarse", "no-fix")) {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("getprop ro.kernel.qemu"))
                .bufferedReader().use { assertEquals("Synthetic GPS is allowed only on an emulator", "1", it.readText().trim()) }
            installTestGps(mode != "no-fix")
        }
        attachScene()
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("farm-map-layer").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("farm-map-layer").performClick()
        compose.onNodeWithTag("farm-map-base-NONE").performClick()
        val map = AtomicReference<MapLibreMap>()
        onView(isAssignableFrom(MapView::class.java)).check { view, error ->
            if (error != null) throw error
            (view as MapView).getMapAsync { map.set(it) }
        }
        compose.waitUntil(15_000) { map.get() != null && savedContourVisible(map) }
        if (mode == "baseline") {
            compose.onNodeWithContentDescription("Encuadrar mis parcelas").assertIsDisplayed()
            capture("saved-contour")
            return
        }
        if (mode == "offline") {
            assertNull("Airplane mode plus Wi-Fi off leaves no active network", context.getSystemService(ConnectivityManager::class.java).activeNetwork)
            compose.onNodeWithTag("farm-map-layer").performClick()
            compose.onNodeWithTag("farm-map-base-AERIAL").performClick()
            compose.waitUntil(15_000) { savedContourVisible(map) }
            capture("offline-aerial-saved-contour")
            compose.onNodeWithTag("farm-map-layer").performClick()
            compose.onNodeWithTag("farm-map-base-NONE").performClick()
            compose.waitUntil(15_000) { savedContourVisible(map) }
            compose.onNodeWithContentDescription("Encuadrar mis parcelas").assertIsDisplayed().performClick()
            assertTrue("No-network fallback keeps the selectable saved parcel", savedContourVisible(map))
            capture("offline-local-contour")
            return
        }
        val started = SystemClock.elapsedRealtime()
        compose.onNodeWithTag("farm-map-my-location").performClick()
        when (mode) {
            "denied" -> {
                compose.waitUntil(8_000) { denyPermissionDialog() }
                compose.onNodeWithTag("farm-map-location-problem").assertIsDisplayed()
                compose.onNodeWithText("Para centrar el mapa donde estás, permite el acceso a la ubicación.").assertIsDisplayed()
                compose.onNodeWithTag("farm-map-location-action").performClick()
                compose.waitUntil(8_000) { denyPermissionDialog() }
                compose.onNodeWithTag("farm-map-location-problem").assertIsDisplayed()
                compose.onNodeWithTag("farm-map-my-location-shown").assertDoesNotExist()
                compose.onNodeWithTag("farm-map-location-action").performClick()
                compose.waitUntil(8_000) { instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString() == "com.android.settings" }
                assertTrue(instrumentation.uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK))
                compose.waitUntil(8_000) { runCatching { compose.onAllNodesWithTag("farm-map-my-location").fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false) }
                compose.onNodeWithTag("farm-map-my-location-shown").assertDoesNotExist()
            }
            "off" -> {
                compose.onNodeWithText("La ubicación del teléfono está desactivada.").assertIsDisplayed()
                compose.onNodeWithTag("farm-map-my-location-shown").assertDoesNotExist()
            }
            "no-fix", "coarse" -> {
                compose.waitUntil(10_000) { compose.onAllNodesWithTag("farm-map-location-problem").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithText("No hemos podido obtener una ubicación actual. Muévete en el mapa o busca por polígono y parcela.").assertIsDisplayed()
                compose.onNodeWithTag("farm-map-my-location-shown").assertDoesNotExist()
                assertTrue("A failed fix must finish within the existing 8s location budget plus scheduling margin", SystemClock.elapsedRealtime() - started < 11_000)
            }
            "precise", "grant", "rotation" -> {
                if (mode == "grant") compose.waitUntil(8_000) { permissionDialogAction("permission_allow_foreground_only_button") }
                compose.waitUntil(10_000) { compose.onAllNodesWithTag("farm-map-my-location-shown").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithTag("farm-map-my-location-shown").assertIsDisplayed()
                compose.waitUntil(10_000) {
                    var correct = false
                    compose.runOnIdle {
                        val point = map.get().style?.getSourceAs<GeoJsonSource>("my-location")
                            ?.querySourceFeatures(Expression.literal(true))?.firstOrNull()?.geometry() as? Point
                        correct = point != null && kotlin.math.abs(point.latitude() - 37.636) < .000001 && kotlin.math.abs(point.longitude() + 3.480) < .000001
                    }
                    correct
                }
                if (mode == "rotation") {
                    compose.runOnIdle { map.get().moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(37.6361, -3.4801), 17.25)) }
                    capture("manual-camera-before-rotation")
                    for ((orientation, expected) in listOf(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE to Configuration.ORIENTATION_LANDSCAPE,
                        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT to Configuration.ORIENTATION_PORTRAIT)) {
                        compose.activityRule.scenario.onActivity { it.requestedOrientation = orientation }
                        compose.waitUntil(10_000) {
                            var changed = false
                            compose.activityRule.scenario.onActivity { changed = it.resources.configuration.orientation == expected }
                            changed
                        }
                        attachScene()
                        map.set(null)
                        onView(isAssignableFrom(MapView::class.java)).check { view, error ->
                            if (error != null) throw error
                            (view as MapView).getMapAsync(map::set)
                        }
                        compose.waitUntil(15_000) { map.get() != null && savedContourVisible(map) }
                        compose.onNodeWithTag("farm-map-my-location-shown").assertIsDisplayed()
                        compose.runOnIdle {
                            val camera = map.get().cameraPosition
                            assertEquals("Manual zoom survives actual Activity rotation", 17.25, camera.zoom, .000001)
                            assertEquals("Manual latitude survives rotation", 37.6361, camera.target!!.latitude, .000001)
                            assertEquals("Manual longitude survives rotation", -3.4801, camera.target!!.longitude, .000001)
                            assertNull("The chosen local base survives rotation", map.get().style?.getSource("base"))
                        }
                        capture("manual-camera-after-rotation-" + expected)
                    }
                }
            }
            else -> error("Unknown device QA mode: " + mode)
        }
        // Location can reload the style; GeoJSON and rendered tiles become
        // queryable asynchronously, independently of the Compose success notice.
        compose.waitUntil(15_000) { savedContourVisible(map) }
        assertTrue("Saved geometry survives location success or rejection", savedContourVisible(map))
        capture("result")
    }

    private fun attachScene() {
        compose.activityRule.scenario.onActivity { activity ->
            WindowCompat.setDecorFitsSystemWindows(activity.window, false)
            activity.setContent {
                MaginaOlivoTheme(theme) {
                    val items = bottomNavigationRoots.map { destination ->
                        MoBottomBarItem(destination.label, destination.symbol, destination.isPrimaryAction, when (destination) {
                            RootDestination.Home -> MoIcons.Home
                            RootDestination.Olivar -> MoIcons.Tree
                            RootDestination.Notebook -> MoIcons.Notebook
                            RootDestination.Alerts -> MoIcons.Bell
                            RootDestination.Profile -> MoIcons.Person
                        })
                    }
                    Scaffold(Modifier.fillMaxSize(), bottomBar = { MoBottomBar(items, selectedIndex = 1, onSelected = {}, onAddRecord = {}) }) { padding ->
                        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) { FarmMapRoute(farm.id, persistence, {}, {}) }
                    }
                }
            }
        }
    }

    private fun savedContourVisible(map: AtomicReference<MapLibreMap>): Boolean {
        var visible = false
        compose.runOnIdle {
            val current = map.get() ?: return@runOnIdle
            val point = current.projection.toScreenLocation(LatLng(37.636, -3.480))
            visible = current.queryRenderedFeatures(point, "parcels-fill").any { it.getStringProperty("id") == parcel.id.toString() }
        }
        return visible
    }

    @Suppress("DEPRECATION")
    private fun installTestGps(publish: Boolean) {
        val manager = context.getSystemService(LocationManager::class.java)
        manager.addTestProvider(LocationManager.GPS_PROVIDER, false, true, false, false, true, true, true, Criteria.POWER_LOW, Criteria.ACCURACY_FINE)
        provider = manager
        manager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true)
        if (publish) {
            val send = object : Runnable {
                override fun run() {
                    manager.setTestProviderLocation(LocationManager.GPS_PROVIDER, Location(LocationManager.GPS_PROVIDER).apply {
                        latitude = 37.636; longitude = -3.480; accuracy = 8f
                        time = System.currentTimeMillis(); elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
                    })
                    updates.postDelayed(this, 400)
                }
            }
            updates.post(send)
        }
    }

    private fun denyPermissionDialog(): Boolean {
        return permissionDialogAction("permission_deny_button", "permission_deny_and_dont_ask_again_button")
    }

    private fun permissionDialogAction(vararg ids: String): Boolean {
        // getRootInActiveWindow reports resource IDs only when this accessibility flag is
        // enabled. GPS cases initialise UiAutomation earlier through executeShellCommand.
        val automation = instrumentation.uiAutomation
        val info = automation.serviceInfo
        if (info.flags and android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS == 0) {
            info.flags = info.flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            automation.serviceInfo = info
        }
        fun find(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            node ?: return null
            if (ids.any { node.viewIdResourceName?.endsWith(":id/" + it) == true }) return node
            for (index in 0 until node.childCount) find(node.getChild(index))?.let { return it }
            return null
        }
        return find(instrumentation.uiAutomation.rootInActiveWindow)?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
    }

    private fun capture(stage: String) {
        compose.waitForIdle()
        val config = context.resources.configuration
        val name = theme.name.lowercase() + "-" + mode + "-" + stage + "-sw" + config.smallestScreenWidthDp + "dp-font" + config.fontScale + ".png"
        val file = File(context.getExternalFilesDir(null), "map-device-evidence/" + name)
        file.parentFile!!.mkdirs()
        checkNotNull(instrumentation.uiAutomation.takeScreenshot()).let { bitmap -> file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) } }
    }

    private inline fun <reified T> rejectCalls(): T = Proxy.newProxyInstance(T::class.java.classLoader,
        arrayOf(T::class.java)) { _, method, _ -> throw AssertionError("No operational writes or unrelated reads: " + method.name) } as T
}
