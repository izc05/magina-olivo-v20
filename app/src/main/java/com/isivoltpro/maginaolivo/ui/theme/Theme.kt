package com.isivoltpro.maginaolivo.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

@Composable
fun MaginaOlivoTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = MoLightColorScheme,
        typography = MoTypography,
        shapes = MoShapes,
        content = content,
    )
}
