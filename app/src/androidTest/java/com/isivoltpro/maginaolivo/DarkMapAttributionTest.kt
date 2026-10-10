package com.isivoltpro.maginaolivo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.isivoltpro.maginaolivo.app.AppearanceMode
import com.isivoltpro.maginaolivo.feature.maps.MapAttribution
import com.isivoltpro.maginaolivo.ui.theme.MaginaOlivoTheme
import kotlin.math.pow
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Real map attribution over a bright raster stand-in, without a live network dependency. */
class DarkMapAttributionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun darkAttributionRemainsReadableOverBrightImagery() = expectReadable(AppearanceMode.DARK)
    @Test fun lightAttributionRemainsReadableOverBrightImagery() = expectReadable(AppearanceMode.LIGHT)

    private fun expectReadable(mode: AppearanceMode) {
        compose.setContent {
            MaginaOlivoTheme(mode) {
                Box(Modifier.fillMaxSize().background(Color.White)) {
                    MapAttribution("© IGN · PNOA · © DG Catastro")
                }
            }
        }
        val bitmap = compose.onNodeWithText("© IGN · PNOA · © DG Catastro", useUnmergedTree = true)
            .captureToImage().asAndroidBitmap()
        val background = luminance(bitmap.getPixel(bitmap.width - 1, 0))
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val strongest = pixels.maxOf {
            val foreground = luminance(it)
            (maxOf(foreground, background) + 0.05) / (minOf(foreground, background) + 0.05)
        }
        assertTrue("$mode map attribution over bright imagery contrast $strongest must be >=4.5:1", strongest >= 4.5)
    }

    private fun luminance(argb: Int): Double {
        fun channel(shift: Int): Double {
            val value = ((argb ushr shift) and 255) / 255.0
            return if (value <= 0.04045) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }
}
