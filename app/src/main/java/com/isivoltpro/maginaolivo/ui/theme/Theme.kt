package com.isivoltpro.maginaolivo.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.isivoltpro.maginaolivo.app.AppearanceMode

@Composable
fun MaginaOlivoTheme(
    mode: AppearanceMode = AppearanceMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val dark = mode.isDark(isSystemInDarkTheme())
    CompositionLocalProvider(LocalMoPalette provides if (dark) MoDarkPalette else MoLightPalette) {
        MaterialTheme(
            colorScheme = if (dark) MoDarkColorScheme else MoLightColorScheme,
            typography = MoTypography,
            shapes = MoShapes,
            content = content,
        )
    }
}
