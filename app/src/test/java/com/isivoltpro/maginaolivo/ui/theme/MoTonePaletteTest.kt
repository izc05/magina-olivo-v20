package com.isivoltpro.maginaolivo.ui.theme

import androidx.compose.ui.graphics.Color
import com.isivoltpro.maginaolivo.ui.components.MoIconTone
import com.isivoltpro.maginaolivo.ui.components.MoKpiKind
import com.isivoltpro.maginaolivo.ui.components.MoToneColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min

class MoTonePaletteTest {
    @Test fun lightIconFamiliesKeepTheirApprovedColors() {
        val expected = listOf(
            MoOliveMid to MoOliveTint, MoEarthText to MoEarthTint, MoInfoText to MoInfoTint,
            MoSoftGoldText to MoSoftGoldTint, MoWarningText to MoWarningTint,
            MoTreatmentText to MoTreatmentTint, MoLabourText to MoLabourTint, MoMoneyText to MoMoneyTint,
        )
        assertEquals(expected.size, MoIconTone.entries.size)
        MoIconTone.entries.zip(expected).forEach { (tone, pair) ->
            assertEquals(tone.name, MoToneColors(pair.first, pair.second), tone.colors(MoLightPalette))
        }
        assertEquals(MoToneColors(MoSuccessText, MoSuccessTint), MoKpiKind.YIELD.colors(MoLightPalette))
        assertEquals(MoToneColors(MoOliveDark, MoOliveTint), MoKpiKind.CAMPAIGN.colors(MoLightPalette))
    }

    @Test fun allActualIconAndKpiNightPairsHaveReadableContrast() {
        val pairs = MoIconTone.entries.map { it.colors(MoDarkPalette) } +
            MoKpiKind.entries.map { it.colors(MoDarkPalette) }
        pairs.forEach {
            assertContrast(it.tint, it.container)
            assertContrast(it.tint, MoDarkColorScheme.surface)
        }
    }

    private fun assertContrast(first: Color, second: Color) {
        fun luminance(color: Color): Double {
            fun linear(value: Float): Double = if (value <= .04045f) value / 12.92 else Math.pow((value + .055) / 1.055, 2.4)
            return .2126 * linear(color.red) + .7152 * linear(color.green) + .0722 * linear(color.blue)
        }
        val firstL = luminance(first)
        val secondL = luminance(second)
        assertTrue("Unreadable pair $first on $second", (max(firstL, secondL) + .05) / (min(firstL, secondL) + .05) >= 4.5)
    }
}
