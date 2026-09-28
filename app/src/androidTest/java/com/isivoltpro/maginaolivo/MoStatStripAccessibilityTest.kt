package com.isivoltpro.maginaolivo

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.isivoltpro.maginaolivo.ui.components.MoIcons
import com.isivoltpro.maginaolivo.ui.components.MoStat
import com.isivoltpro.maginaolivo.ui.components.MoStatStrip
import com.isivoltpro.maginaolivo.ui.theme.MaginaOlivoTheme
import org.junit.Assert.assertEquals
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
                    Box(Modifier.requiredWidth(320.dp).testTag("strip")) {
                        MoStatStrip(listOf(
                            MoStat("Fincas", "2", MoIcons.Location),
                            MoStat("Parcelas", "4", MoIcons.Location),
                            MoStat("Superficie", "5,2 ha", MoIcons.Location),
                        ))
                    }
                }
            }
        }
        val strip = composeRule.onNodeWithTag("strip").fetchSemanticsNode().boundsInRoot
        for (text in listOf("Fincas", "Parcelas", "Superficie", "5,2 ha")) {
            val node = composeRule.onNodeWithText(text)
            val layouts = mutableListOf<TextLayoutResult>()
            node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertTrue("Missing layout for $text", layouts.isNotEmpty())
            layouts.forEach { layout ->
                // Complete = every character is laid out and shown, none replaced by an ellipsis.
                val last = layout.lineCount - 1
                assertEquals("Hidden characters in $text", text.length, layout.getLineEnd(last, visibleEnd = true))
                (0..last).forEach { line -> assertFalse("Ellipsized: $text", layout.isLineEllipsized(line)) }
                assertFalse("Wider than its box: $text", layout.didOverflowWidth)
            }
            // And the text sits inside the strip, not pushed past its right edge.
            val bounds = node.fetchSemanticsNode().boundsInRoot
            assertTrue("Outside the strip: $text", bounds.left >= strip.left && bounds.right <= strip.right + 0.5f)
        }
    }
}
