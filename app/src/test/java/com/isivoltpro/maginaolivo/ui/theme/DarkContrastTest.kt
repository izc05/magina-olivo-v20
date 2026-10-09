package com.isivoltpro.maginaolivo.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min

class DarkContrastTest {
    @Test fun textAndControlsRemainReadableOnEveryDarkSurface() {
        val scheme = MoDarkColorScheme
        listOf(scheme.background, scheme.surface, scheme.surfaceVariant,
            scheme.surfaceContainerLowest, scheme.surfaceContainerLow, scheme.surfaceContainer,
            scheme.surfaceContainerHigh, scheme.surfaceContainerHighest).forEach { surface ->
            listOf(scheme.onSurface, scheme.onSurfaceVariant, scheme.error,
                MoDarkPalette.primaryText, MoDarkPalette.actionText).forEach { assertContrast(it, surface, 4.5) }
            assertContrast(scheme.outline, surface, 3.0)
        }
        assertContrast(scheme.onPrimary, scheme.primary, 4.5)
        assertContrast(scheme.onSecondaryContainer, scheme.secondaryContainer, 4.5)
    }

    @Test fun everyAgriculturalAccentHasReadableTextOnItsTintAndCards() {
        val colors = MoDarkPalette
        listOf(colors.actionText to colors.actionTint, colors.infoText to colors.infoTint,
            colors.warningText to colors.warningTint, colors.errorText to colors.errorTint,
            colors.earthText to colors.earthTint, colors.valueText to colors.valueTint,
            colors.treatmentText to colors.treatmentTint, colors.labourText to colors.labourTint,
            colors.moneyText to colors.moneyTint, colors.rainText to colors.rainTint,
            colors.cloudText to colors.cloudTint).forEach { (text, tint) ->
            assertContrast(text, tint, 4.5)
            assertContrast(text, MoDarkColorScheme.surface, 4.5)
        }
        assertContrast(colors.onPrimaryButton, colors.primaryButton, 4.5)
    }

    private fun assertContrast(foreground: Color, background: Color, minimum: Double) {
        val first = luminance(foreground)
        val second = luminance(background)
        val ratio = (max(first, second) + 0.05) / (min(first, second) + 0.05)
        assertTrue("$foreground on $background: $ratio < $minimum", ratio >= minimum)
    }

    private fun luminance(color: Color): Double {
        fun linear(value: Float): Double = if (value <= .04045f) value / 12.92 else Math.pow((value + .055) / 1.055, 2.4)
        return .2126 * linear(color.red) + .7152 * linear(color.green) + .0722 * linear(color.blue)
    }
}
