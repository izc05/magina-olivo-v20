package com.isivoltpro.maginaolivo.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val OlivarLightColorScheme = lightColorScheme(
    primary = OlivarColors.Olive700,
    onPrimary = OlivarColors.White,
    primaryContainer = OlivarColors.Sage200,
    onPrimaryContainer = OlivarColors.Olive900,
    secondary = OlivarColors.Sage600,
    onSecondary = OlivarColors.White,
    secondaryContainer = OlivarColors.Cream200,
    onSecondaryContainer = OlivarColors.Charcoal900,
    tertiary = OlivarColors.Earth600,
    background = OlivarColors.Cream50,
    onBackground = OlivarColors.Charcoal900,
    surface = OlivarColors.White,
    onSurface = OlivarColors.Charcoal900,
    surfaceVariant = OlivarColors.Cream100,
    onSurfaceVariant = OlivarColors.Charcoal700,
    outline = OlivarColors.Line200,
    error = OlivarColors.Error,
)

@Composable
fun OlivarTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = OlivarLightColorScheme,
        typography = OlivarTypography,
        shapes = OlivarShapes,
        content = content,
    )
}
