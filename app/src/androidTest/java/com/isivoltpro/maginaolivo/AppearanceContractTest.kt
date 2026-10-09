package com.isivoltpro.maginaolivo

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.isivoltpro.maginaolivo.app.AndroidAppearanceStore
import com.isivoltpro.maginaolivo.app.AppearanceMode
import com.isivoltpro.maginaolivo.ui.theme.MaginaOlivoTheme
import com.isivoltpro.maginaolivo.ui.theme.MoColors
import com.isivoltpro.maginaolivo.ui.theme.MoDarkColorScheme
import com.isivoltpro.maginaolivo.ui.theme.MoDarkPalette
import com.isivoltpro.maginaolivo.ui.theme.MoLightColorScheme
import com.isivoltpro.maginaolivo.ui.theme.MoLightPalette
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class AppearanceContractTest {
    @get:Rule val compose = createComposeRule()

    @Test fun androidPreferenceSurvivesNewStoreWithoutTouchingAgriculturalPreferences() = runBlocking {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val isolatedName = "appearance-test-${UUID.randomUUID()}"
        // Redirect only this test's UI preference; never clear app state, Room or onboarding.
        val isolated = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int) = base.getSharedPreferences(isolatedName, mode)
        }
        try {
            val first = AndroidAppearanceStore(isolated)
            assertEquals(AppearanceMode.SYSTEM, first.mode.value)
            assertTrue(first.setMode(AppearanceMode.DARK))
            assertEquals(AppearanceMode.DARK, AndroidAppearanceStore(isolated).mode.value)
            assertTrue(first.setMode(AppearanceMode.LIGHT))
            assertEquals(AppearanceMode.LIGHT, AndroidAppearanceStore(isolated).mode.value)
            assertTrue(first.setMode(AppearanceMode.SYSTEM))
            assertEquals(AppearanceMode.SYSTEM, AndroidAppearanceStore(isolated).mode.value)
        } finally {
            base.deleteSharedPreferences(isolatedName)
        }
    }

    @Test fun modeAndSystemConfigurationChangeBothMaterialAndSemanticColorsImmediately() {
        var mode by mutableStateOf(AppearanceMode.SYSTEM)
        var night by mutableStateOf(false)
        compose.setContent {
            val config = Configuration(LocalConfiguration.current).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            CompositionLocalProvider(LocalConfiguration provides config) {
                MaginaOlivoTheme(mode) {
                    val dark = MaterialTheme.colorScheme.background == MoDarkColorScheme.background
                    val paletteMatches = MoColors.current == if (dark) MoDarkPalette else MoLightPalette
                    val schemeMatches = MaterialTheme.colorScheme.background ==
                        if (dark) MoDarkColorScheme.background else MoLightColorScheme.background
                    Text("dark=$dark palette=$paletteMatches scheme=$schemeMatches")
                }
            }
        }
        fun expect(dark: Boolean) = compose.onNodeWithText("dark=$dark palette=true scheme=true").assertExists()
        expect(false)
        compose.runOnIdle { night = true }
        expect(true)
        compose.runOnIdle { mode = AppearanceMode.LIGHT }
        expect(false)
        compose.runOnIdle { night = false; mode = AppearanceMode.DARK }
        expect(true)
        compose.runOnIdle { mode = AppearanceMode.SYSTEM }
        expect(false)
    }
}
