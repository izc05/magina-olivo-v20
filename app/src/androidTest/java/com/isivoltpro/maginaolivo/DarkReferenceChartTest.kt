package com.isivoltpro.maginaolivo

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.isivoltpro.maginaolivo.app.AppearanceMode
import com.isivoltpro.maginaolivo.ui.reference.campaign.CampaignReferenceScreen
import com.isivoltpro.maginaolivo.ui.theme.MaginaOlivoTheme
import kotlin.math.pow
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DarkReferenceChartTest {
    @get:Rule val compose = createComposeRule()

    @Test fun theCampaignReferenceChartKeepsItsLastBarVisible() {
        compose.setContent { MaginaOlivoTheme(AppearanceMode.DARK) { CampaignReferenceScreen() } }
        val chart = compose.onNodeWithText("Evolución de producción").performScrollTo()
        val bitmap = chart.captureToImage().asAndroidBitmap()
        val density = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density
        // The clickable card contains the real 150dp Canvas. Middle of last bar vs middle gap.
        val y = bitmap.height - (16 * density).toInt() - (75 * density).toInt()
        val foreground = luminance(bitmap.getPixel((bitmap.width * 0.82).toInt(), y))
        val background = luminance(bitmap.getPixel(bitmap.width / 2, y))
        val contrast = (maxOf(foreground, background) + 0.05) / (minOf(foreground, background) + 0.05)
        assertTrue("Actual reference chart bar contrast $contrast must be >=3:1", contrast >= 3.0)
    }

    private fun luminance(argb: Int): Double {
        fun channel(shift: Int): Double {
            val value = ((argb ushr shift) and 255) / 255.0
            return if (value <= 0.04045) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }
}
