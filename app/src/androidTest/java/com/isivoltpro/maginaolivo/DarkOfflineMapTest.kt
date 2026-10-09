package com.isivoltpro.maginaolivo

import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.isivoltpro.maginaolivo.app.AppearanceMode
import com.isivoltpro.maginaolivo.feature.maps.MapBase
import com.isivoltpro.maginaolivo.feature.maps.ParcelMap
import com.isivoltpro.maginaolivo.ui.theme.MaginaOlivoTheme
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Actual native MapLibre pixels, offline, no remote sources or agricultural writes. */
class DarkOfflineMapTest {
    @get:Rule val compose = createComposeRule()

    @Test fun offlineMapUsesTheSelectedAppearance() {
        val mode = AppearanceMode.valueOf(InstrumentationRegistry.getArguments().getString("appearance", "DARK"))
        val snapshot = AtomicReference<Bitmap>()
        compose.setContent {
            MaginaOlivoTheme(mode) {
                ParcelMap(emptyList(), Modifier.fillMaxSize(), base = MapBase.NONE, onMapSnapshot = snapshot::set)
            }
        }
        compose.waitUntil(timeoutMillis = 25_000) { snapshot.get() != null }
        val bitmap = snapshot.get()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val output = File(context.filesDir, "dark3-evidence").apply { mkdirs() }
        File(output, "offline-map-${mode.name.lowercase()}.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        val expected = if (mode == AppearanceMode.DARK) 0xFF171914.toInt() else 0xFFF3F1E6.toInt()
        assertEquals("Native offline map background", expected, bitmap.getPixel(bitmap.width / 2, bitmap.height / 2))
    }
}
