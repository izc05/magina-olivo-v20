package com.isivoltpro.maginaolivo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.isivoltpro.maginaolivo.app.AppearanceMode
import com.isivoltpro.maginaolivo.ui.components.*
import com.isivoltpro.maginaolivo.ui.theme.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Explicit theme harness: production stays LIGHT until the remaining DARK-3 migration. */
class DarkComponentsContractTest {
    @get:Rule val compose = createComposeRule()
    private var mode by mutableStateOf(AppearanceMode.LIGHT)

    @Test fun primaryButtonChangesItsActualPaintAndReturnsToTheOriginalLightColor() {
        show()
        expectPaint("theme-button", MoLightPalette.primaryButton)
        compose.runOnIdle { mode = AppearanceMode.DARK }
        expectPaint("theme-button", MoDarkPalette.primaryButton)
        compose.runOnIdle { mode = AppearanceMode.LIGHT }
        expectPaint("theme-button", MoLightPalette.primaryButton)
    }

    @Test fun iconFamilyChangesItsActualContainerWithTheTheme() {
        show()
        expectPaint("theme-badge", MoLightPalette.valueTint)
        compose.runOnIdle { mode = AppearanceMode.DARK }
        expectPaint("theme-badge", MoDarkPalette.valueTint)
    }

    @Test fun warningChipUsesTheReadableNightForegroundToBuildItsOpaqueTint() {
        show()
        expectPaint("theme-chip", opaqueSurfaceTint(MoLightColorScheme.surface, MoLightPalette.warningText, .12f))
        compose.runOnIdle { mode = AppearanceMode.DARK }
        expectPaint("theme-chip", opaqueSurfaceTint(MoDarkColorScheme.surface, MoDarkPalette.warningText, .12f))
    }

    private fun show() {
        compose.setContent {
            MaginaOlivoTheme(mode) {
                Column(Modifier.background(MaterialTheme.colorScheme.background).padding(24.dp)) {
                    MoPrimaryButton("Guardar", {}, Modifier.testTag("theme-button"))
                    MoIconBadge(MoIcons.Delivery, Modifier.testTag("theme-badge"))
                    MoStatusChip("Aviso", Modifier.testTag("theme-chip"), MoStatusTone.Warning)
                }
            }
        }
    }

    private fun expectPaint(tag: String, expected: Color) {
        val bitmap = compose.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
        // Interior above the glyph/text, away from clipping, border and antialiased corners.
        assertEquals("$tag must use the active palette", expected.toArgb(), bitmap.getPixel(bitmap.width / 2, 8))
    }
}
