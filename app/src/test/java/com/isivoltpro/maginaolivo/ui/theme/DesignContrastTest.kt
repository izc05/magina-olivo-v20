package com.isivoltpro.maginaolivo.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min

class DesignContrastTest {

    /** #706: actual Material surfaces, including form fields, need readable outlines outdoors. */
    @Test
    fun materialSurfaceOutlinesHaveNonTextContrast() {
        listOf(
            "app background" to MoLightColorScheme.background,
            "card" to MoLightColorScheme.surface,
            "soft field" to MoLightColorScheme.surfaceVariant,
            "elevated card" to MoLightColorScheme.surfaceContainerHigh,
            "selected filter chip" to MoLightColorScheme.secondaryContainer,
            "cloud forecast" to MoCloudTint,
            "rain forecast" to MoRainTint,
            "sky forecast" to MoSkyTint,
            "sun forecast" to MoSoftGoldTint,
            "offline warning on app background" to compositeOver(MoWarning, MoLightColorScheme.background, 0.12f),
            "source warning on app background" to compositeOver(MoWarning, MoLightColorScheme.background, 0.10f),
            "synced chip" to opaqueSurfaceTint(MoLightColorScheme.surface, MoOliveMid, 0.10f),
            "pending chip" to opaqueSurfaceTint(MoLightColorScheme.surface, MoWarningText, 0.10f),
            "local chip" to opaqueSurfaceTint(MoLightColorScheme.surface, MoLightColorScheme.onSurfaceVariant, 0.10f),
        ).forEach { (name, surface) ->
            assertContrastAtLeast("outline on $name", MoLightColorScheme.outline, surface, 3.0)
        }
    }

    @Test
    fun materialSecondaryAndErrorTextMeetAaOnAllContentSurfaces() {
        listOf(
            "app background" to MoLightColorScheme.background,
            "card" to MoLightColorScheme.surface,
            "soft field" to MoLightColorScheme.surfaceVariant,
            "elevated card" to MoLightColorScheme.surfaceContainerHigh,
        ).forEach { (name, surface) ->
            assertContrastAtLeast("secondary text on $name", MoLightColorScheme.onSurfaceVariant, surface, WCAG_AA_NORMAL_TEXT)
            assertContrastAtLeast("error text on $name", MoLightColorScheme.error, surface, WCAG_AA_NORMAL_TEXT)
        }
    }

    /** CR-011 §20/§21: every action accent reads on its own tint and on the card white. */
    @Test
    fun actionAccentsMeetWcagAaOnTintAndCard() {
        listOf(
            Triple("treatment", MoTreatmentText, MoTreatmentTint),
            Triple("labour", MoLabourText, MoLabourTint),
            Triple("money", MoMoneyText, MoMoneyTint),
            Triple("value", MoSoftGoldText, MoSoftGoldTint),
            Triple("water", MoInfoText, MoInfoTint),
            Triple("rain radar", MoRainText, MoRainTint),
        ).forEach { (name, line, tint) ->
            assertContrastAtLeast("$name on its tint", line, tint, WCAG_AA_NORMAL_TEXT)
            assertContrastAtLeast("$name on card white", line, MoWarmWhite, WCAG_AA_NORMAL_TEXT)
        }
    }

    @Test
    fun semanticStatusForegroundsMeetWcagAaOnTintedBackgrounds() {
        val tones = listOf(
            "neutral" to MoTextSecondary,
            "surface neutral" to MoLightColorScheme.onSurfaceVariant,
            "success" to MoOliveMid,
            "legacy success" to MoSuccessText,
            "info" to MoInfoText,
            "warning" to MoWarningText,
            "error" to MoErrorText,
        )

        listOf(MoWarmWhite, MoCream, MoLightColorScheme.background, MoLightColorScheme.surfaceContainerHigh).forEach { base ->
            tones.forEach { (name, foreground) ->
                // New app/elevated surfaces use the actual opaque chip tint. Legacy pairs
                // remain covered so compatibility constants retain their previous contrast.
                val visibleBackground = if (base == MoLightColorScheme.background ||
                    base == MoLightColorScheme.surfaceContainerHigh) {
                    opaqueSurfaceTint(MoLightColorScheme.surface, foreground, STATUS_TINT_ALPHA)
                        .also { assertTrue("chip tint must be opaque", it.alpha == 1f) }
                } else {
                    compositeOver(foreground, base, STATUS_TINT_ALPHA)
                }

                assertContrastAtLeast(
                    label = "$name status on tinted surface",
                    foreground = foreground,
                    background = visibleBackground,
                    minimum = WCAG_AA_NORMAL_TEXT,
                )
            }
        }
    }

    @Test
    fun coreOutdoorTextPairsMeetWcagAa() {
        val pairs = listOf(
            Triple("primary text on cream", MoOliveDark, MoCream),
            Triple("primary text on warm white", MoOliveDark, MoWarmWhite),
            Triple("secondary text on cream", MoTextSecondary, MoCream),
            Triple("secondary text on warm white", MoTextSecondary, MoWarmWhite),
            Triple("light text on olive primary", MoWarmWhite, MoOlivePrimary),
            Triple("dark text on sage", MoOliveDark, MoSage),
            Triple("body ink on cream", MoInk, MoCream),
            Triple("body ink on warm white", MoInk, MoWarmWhite),
            Triple("mid olive text action on cream", MoOliveMid, MoCream),
            Triple("mid olive text action on warm white", MoOliveMid, MoWarmWhite),
            Triple("mid olive chip on olive tint", MoOliveMid, MoOliveTint),
            Triple("light text on mid olive", MoWarmWhite, MoOliveMid),
            Triple("earth icon on earth tint", MoEarthText, MoEarthTint),
            Triple("info icon on info tint", MoInfoText, MoInfoTint),
            Triple("gold icon on gold tint", MoSoftGoldText, MoSoftGoldTint),
            Triple("warning icon on warning tint", MoWarningText, MoWarningTint),
            Triple("paid icon on success tint", MoSuccessText, MoSuccessTint),
        )

        pairs.forEach { (label, foreground, background) ->
            assertContrastAtLeast(
                label = label,
                foreground = foreground,
                background = background,
                minimum = WCAG_AA_NORMAL_TEXT,
            )
        }
    }

    @Test
    fun supportingSemanticLabelsMeetWcagAa() {
        assertContrastAtLeast(
            label = "info freshness label",
            foreground = MoInfoText,
            background = MoSurfaceSoft,
            minimum = WCAG_AA_NORMAL_TEXT,
        )
        assertContrastAtLeast(
            label = "warning label",
            foreground = MoWarningText,
            background = compositeOver(MoWarningText, MoWarmWhite, 0.10f),
            minimum = WCAG_AA_NORMAL_TEXT,
        )
        assertContrastAtLeast(
            label = "actuality gold label",
            foreground = MoSoftGoldText,
            background = MoWarmWhite,
            minimum = WCAG_AA_NORMAL_TEXT,
        )
    }

    private fun assertContrastAtLeast(
        label: String,
        foreground: Color,
        background: Color,
        minimum: Double,
    ) {
        val ratio = contrastRatio(foreground, background)
        assertTrue(
            "$label contrast was %.2f:1; expected at least %.1f:1".format(ratio, minimum),
            ratio >= minimum,
        )
    }

    private fun contrastRatio(first: Color, second: Color): Double {
        val firstLuminance = relativeLuminance(first)
        val secondLuminance = relativeLuminance(second)
        val lighter = max(firstLuminance, secondLuminance)
        val darker = min(firstLuminance, secondLuminance)
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun relativeLuminance(color: Color): Double {
        val red = linearize(color.red.toDouble())
        val green = linearize(color.green.toDouble())
        val blue = linearize(color.blue.toDouble())
        return 0.2126 * red + 0.7152 * green + 0.0722 * blue
    }

    private fun linearize(channel: Double): Double =
        if (channel <= 0.04045) {
            channel / 12.92
        } else {
            Math.pow((channel + 0.055) / 1.055, 2.4)
        }

    private fun compositeOver(
        foreground: Color,
        background: Color,
        foregroundAlpha: Float,
    ): Color {
        val inverseAlpha = 1f - foregroundAlpha
        return Color(
            red = foreground.red * foregroundAlpha + background.red * inverseAlpha,
            green = foreground.green * foregroundAlpha + background.green * inverseAlpha,
            blue = foreground.blue * foregroundAlpha + background.blue * inverseAlpha,
            alpha = 1f,
        )
    }

    private companion object {
        const val WCAG_AA_NORMAL_TEXT = 4.5
        const val STATUS_TINT_ALPHA = 0.12f
    }
}
