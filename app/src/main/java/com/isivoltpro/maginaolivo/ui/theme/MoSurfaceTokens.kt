package com.isivoltpro.maginaolivo.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/** #706: surfaces follow the active Material scheme, including a future dark scheme. */
object MoSurfaceTokens {
    val appBackground: Color
        @Composable get() = MaterialTheme.colorScheme.background
    val cardSurface: Color
        @Composable get() = MaterialTheme.colorScheme.surface
    val cardStroke: Color
        @Composable get() = MaterialTheme.colorScheme.outline
    val cardElevated: Color
        @Composable get() = MaterialTheme.colorScheme.surfaceContainerHigh
    /** Opaque tint: its text contrast must not depend on the surrounding app background. */
    @Composable
    fun tintedCard(foreground: Color, alpha: Float): Color =
        opaqueSurfaceTint(cardSurface, foreground, alpha)

    val secondaryText: Color
        @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
}

/** Same subtle tint as alpha painting on a card, with a stable, opaque background. */
internal fun opaqueSurfaceTint(surface: Color, foreground: Color, alpha: Float): Color =
    lerp(surface.copy(alpha = 1f), foreground.copy(alpha = 1f), alpha).copy(alpha = 1f)
