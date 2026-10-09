package com.isivoltpro.maginaolivo

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.WindowCompat
import com.isivoltpro.maginaolivo.app.*
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import java.io.File
import java.util.UUID
import kotlin.math.roundToInt
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Production AppRoot/navigation/Perfil, isolated device store, no Room or onboarding writes. */
class AppearanceSettingsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun profileChangesAppearanceImmediatelyAndRestoresLight() {
        val store = InMemoryAppearanceStore()
        showProfile(store)
        compose.onNodeWithTag("profile-appearance").performScrollTo().performClick()
        compose.onNodeWithTag("appearance-option-SYSTEM").assertIsSelected()
        compose.onNodeWithTag("appearance-option-LIGHT").assertExists()
        capture("appearance-choices")
        compose.onNodeWithTag("appearance-option-DARK").performClick()
        compose.waitUntil(5_000) { store.mode.value == AppearanceMode.DARK }
        expectBackground(0xFF171914.toInt(), lightIcons = false)
        expectSummary("Oscuro")
        capture("appearance-dark-profile")
        compose.onNodeWithTag("profile-appearance").performClick()
        compose.onNodeWithTag("appearance-option-DARK").assertIsSelected()
        compose.onNodeWithTag("appearance-option-LIGHT").performClick()
        compose.waitUntil(5_000) { store.mode.value == AppearanceMode.LIGHT }
        expectBackground(0xFFF1ECDF.toInt(), lightIcons = true)
        expectSummary("Claro")
        capture("appearance-light-profile")
    }

    @Test fun cancellingTheSheetKeepsTheConfirmedPreference() {
        val store = InMemoryAppearanceStore(AppearanceMode.LIGHT)
        showProfile(store)
        compose.onNodeWithTag("profile-appearance").performScrollTo().performClick()
        compose.onNodeWithTag("appearance-cancel").performClick()
        compose.onNodeWithTag("appearance-sheet").assertDoesNotExist()
        assertEquals(AppearanceMode.LIGHT, store.mode.value)
        expectSummary("Claro")
    }

    @Test fun rejectedWriteKeepsLightAndShowsAnActionableError() {
        val store = object : AppearanceStore {
            override val mode = MutableStateFlow(AppearanceMode.LIGHT)
            override suspend fun setMode(mode: AppearanceMode) = false
        }
        showProfile(store)
        compose.onNodeWithTag("profile-appearance").performScrollTo().performClick()
        compose.onNodeWithTag("appearance-option-DARK").performClick()
        compose.onNodeWithText("No se pudo guardar la apariencia. Inténtalo de nuevo.").assertIsDisplayed()
        capture("appearance-write-error")
        compose.onNodeWithTag("appearance-option-LIGHT").assertIsSelected()
        assertEquals(AppearanceMode.LIGHT, store.mode.value)
        compose.onNodeWithTag("appearance-cancel").performScrollTo().assertIsDisplayed().performClick()
        expectBackground(0xFFF1ECDF.toInt(), lightIcons = true)
        compose.onNodeWithTag("bottom-Mi Campo").performClick().assertIsSelected()
    }

    @Test fun productionUsesAnAlreadyConfirmedDarkMode() {
        showProfile(InMemoryAppearanceStore(AppearanceMode.DARK))
        expectBackground(0xFF171914.toInt(), lightIcons = false)
    }

    @Test fun savedSelectionSurvivesActivityRecreationAndReopening() {
        val base = compose.activity.applicationContext
        val isolatedName = "appearance-selector-${UUID.randomUUID()}"
        val isolated = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int) = base.getSharedPreferences(isolatedName, mode)
        }
        try {
            val store = AndroidAppearanceStore(isolated)
            showProfile(store)
            compose.onNodeWithTag("profile-appearance").performScrollTo().performClick()
            compose.onNodeWithTag("appearance-option-DARK").performClick()
            compose.waitUntil(5_000) { store.mode.value == AppearanceMode.DARK }
            compose.activityRule.scenario.recreate()
            compose.activityRule.scenario.onActivity { activity ->
                val restoredRoot = root(AndroidAppearanceStore(isolated))
                activity.setContent { AppRoot(restoredRoot) }
            }
            compose.onNodeWithTag("bottom-Perfil").performClick()
            compose.onNodeWithTag("profile-appearance").performScrollTo()
            expectBackground(0xFF171914.toInt(), lightIcons = false)
            expectSummary("Oscuro")
            compose.onNodeWithTag("profile-appearance").performClick()
            compose.onNodeWithTag("appearance-option-DARK").assertIsSelected()
            compose.onNodeWithTag("appearance-cancel").performClick()
            assertEquals(AppearanceMode.DARK, AndroidAppearanceStore(isolated).mode.value)
        } finally {
            base.deleteSharedPreferences(isolatedName)
        }
    }

    @Test fun systemPreferenceTracksNightConfigurationInProduction() {
        val store = InMemoryAppearanceStore()
        val root = root(store)
        var night by mutableStateOf(false)
        compose.setContent {
            val config = Configuration(LocalConfiguration.current).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            CompositionLocalProvider(LocalConfiguration provides config) { AppRoot(root) }
        }
        compose.onNodeWithTag("bottom-Perfil").performClick()
        expectBackground(0xFFF1ECDF.toInt(), lightIcons = true)
        compose.runOnIdle { night = true }
        expectBackground(0xFF171914.toInt(), lightIcons = false)
        compose.runOnIdle { night = false }
        expectBackground(0xFFF1ECDF.toInt(), lightIcons = true)
        assertEquals(AppearanceMode.SYSTEM, store.mode.value)
        compose.onNodeWithTag("profile-appearance").performScrollTo()
        expectSummary("Seguir sistema")
    }

    private fun showProfile(store: AppearanceStore) {
        val root = root(store)
        compose.setContent { AppRoot(root) }
        compose.onNodeWithTag("bottom-Perfil").performClick()
        compose.onNodeWithTag("profile-root").assertIsDisplayed()
    }

    private fun root(store: AppearanceStore) = AppCompositionRoot.createDefault("DEV").copy(
            onboardingStateStore = InMemoryOnboardingStateStore(completed = true),
            appearanceStore = store,
        )

    private fun expectSummary(text: String) {
        compose.onNode(hasText(text) and hasAnyAncestor(hasTestTag("profile-appearance")), useUnmergedTree = true).assertIsDisplayed()
    }

    private fun expectBackground(argb: Int, lightIcons: Boolean) {
        compose.waitForIdle()
        val bitmap = compose.onNodeWithTag("profile-root").captureToImage().asAndroidBitmap()
        assertEquals("Production Perfil background", argb, bitmap.getPixel(2, bitmap.height / 2))
        compose.runOnIdle {
            val controller = WindowCompat.getInsetsController(compose.activity.window, compose.activity.window.decorView)
            assertEquals("Status bar uses dark icons on a light surface", lightIcons, controller.isAppearanceLightStatusBars)
            assertEquals("Navigation bar uses dark icons on a light surface", lightIcons, controller.isAppearanceLightNavigationBars)
        }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val config = context.resources.configuration
        val output = File(context.filesDir, "dark3-evidence").apply { mkdirs() }
        val file = File(output, "$name-${config.screenWidthDp}dp-font${(config.fontScale * 100).roundToInt()}.png")
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }
}
