package com.isivoltpro.maginaolivo.feature.home

import com.isivoltpro.maginaolivo.feature.maps.MapBase
import com.isivoltpro.maginaolivo.feature.maps.overlayTileSize
import com.isivoltpro.maginaolivo.feature.maps.parcelStyle
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** #360: a natural hero photo that still keeps its text readable, and a sharper radar. */
class HeroShadeTest {
    @Test fun whiteTextStaysReadableOverAWhitePhotograph() {
        // Worst case: the shade at the first text stop over a completely white photograph.
        val ratio = contrast(1.0, luminanceOverWhite(HERO_SHADE_TEXT))
        assertTrue("contrast $ratio", ratio >= 4.5)
        // Codex #376: reached before the greeting can start, even with large text (~18 %).
        assertTrue(HERO_TEXT_STOP <= 0.15f)
        // And darker further down, where the rest of the text sits.
        assertTrue(contrast(1.0, luminanceOverWhite(HERO_SHADE_BOTTOM)) > ratio)
    }

    @Test fun theShadeIsNeutralNotOlive() {
        val channels = listOf(HERO_SHADE.red, HERO_SHADE.green, HERO_SHADE.blue)
        assertTrue(channels.maxOf { it } - channels.minOf { it } < 0.03f)
    }

    @Test fun radarAsksForRainViewersSharperPicture() {
        val template = "https://tilecache.rainviewer.com/v2/radar/1727950800/256/{z}/{x}/{y}/2/1_1.png"
        val sharp = sharpRadarTiles(template)
        assertEquals("https://tilecache.rainviewer.com/v2/radar/1727950800/512/{z}/{x}/{y}/2/1_1.png", sharp)
        assertEquals(512, overlayTileSize(sharp))
        assertTrue(parcelStyle(MapBase.MAP, cadastreLines = false, overlayTiles = sharp).contains("\"tileSize\":512,\"maxzoom\":7"))
        // Another provider's template is left as it is, at 256.
        val other = "https://radar.example.org/256/{z}/{x}/{y}.png"
        assertEquals(other, sharpRadarTiles(other))
        assertEquals(256, overlayTileSize(other))
    }

    /** sRGB alpha blend of the shade over white, then WCAG relative luminance. */
    private fun luminanceOverWhite(alpha: Float): Double {
        fun linear(c: Double) = if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        val r = (1 - alpha) * 1.0 + alpha * HERO_SHADE.red
        val g = (1 - alpha) * 1.0 + alpha * HERO_SHADE.green
        val b = (1 - alpha) * 1.0 + alpha * HERO_SHADE.blue
        return 0.2126 * linear(r) + 0.7152 * linear(g) + 0.0722 * linear(b)
    }

    private fun contrast(a: Double, b: Double): Double {
        val (light, dark) = if (a > b) a to b else b to a
        return (light + 0.05) / (dark + 0.05)
    }

}
