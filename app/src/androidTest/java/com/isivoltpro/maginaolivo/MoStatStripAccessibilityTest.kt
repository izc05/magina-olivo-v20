package com.isivoltpro.maginaolivo

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.isivoltpro.maginaolivo.ui.components.MoIcons
import com.isivoltpro.maginaolivo.ui.components.MoStat
import com.isivoltpro.maginaolivo.ui.components.MoStatStrip
import com.isivoltpro.maginaolivo.ui.theme.MaginaOlivoTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MoStatStripAccessibilityTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun narrowScreenAndLargeTextKeepLabelsAndUnitsComplete() {
        composeRule.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale = 1.3f)) {
                MaginaOlivoTheme {
                    // requiredWidth: the test root may impose its own minimum width on a plain width().
                    Box(Modifier.requiredWidth(320.dp)) {
                        MoStatStrip(listOf(
                            MoStat("Fincas", "2", MoIcons.Location),
                            MoStat("Parcelas", "4", MoIcons.Location),
                            MoStat("Superficie", "5,2 ha", MoIcons.Location),
                        ))
                    }
                }
            }
        }
        for (text in listOf("Fincas", "Parcelas", "Superficie", "5,2 ha")) {
            val layouts = mutableListOf<TextLayoutResult>()
            composeRule.onNodeWithText(text).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertTrue("Missing layout for $text", layouts.isNotEmpty())
            layouts.forEach { assertFalse("Clipped label or unit: $text", it.hasVisualOverflow) }
        }
    }
}
