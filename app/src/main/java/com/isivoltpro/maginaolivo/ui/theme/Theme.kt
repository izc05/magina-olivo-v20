package com.isivoltpro.maginaolivo.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.isivoltpro.maginaolivo.app.AppearanceMode

@Composable
fun MaginaOlivoTheme(
    mode: AppearanceMode = AppearanceMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val dark = mode.isDark(isSystemInDarkTheme())
    AppearanceSystemBars(dark)
    CompositionLocalProvider(LocalMoPalette provides if (dark) MoDarkPalette else MoLightPalette) {
        MaterialTheme(
            colorScheme = if (dark) MoDarkColorScheme else MoLightColorScheme,
            typography = MoTypography,
            shapes = MoShapes,
            content = content,
        )
    }
}

/** The host window follows the confirmed theme, including three-button navigation. */
@Suppress("DEPRECATION")
@Composable
private fun AppearanceSystemBars(dark: Boolean) {
    val view = LocalView.current
    if (LocalInspectionMode.current) return
    val window = (view.parent as? DialogWindowProvider)?.window ?: view.context.activity()?.window ?: return
    val controller = WindowCompat.getInsetsController(window, view)
    DisposableEffect(window) {
        val statusLight = controller.isAppearanceLightStatusBars
        val navigationLight = controller.isAppearanceLightNavigationBars
        val statusColor = window.statusBarColor
        val navigationColor = window.navigationBarColor
        val navigationContrast = if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced else null
        onDispose {
            controller.isAppearanceLightStatusBars = statusLight
            controller.isAppearanceLightNavigationBars = navigationLight
            window.statusBarColor = statusColor
            window.navigationBarColor = navigationColor
            if (Build.VERSION.SDK_INT >= 29 && navigationContrast != null) window.isNavigationBarContrastEnforced = navigationContrast
        }
    }
    SideEffect {
        controller.isAppearanceLightStatusBars = !dark
        controller.isAppearanceLightNavigationBars = !dark
        val background = if (dark) MoDarkColorScheme.background else MoLightColorScheme.background
        window.statusBarColor = background.toArgb()
        window.navigationBarColor = background.toArgb()
        if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
    }
}

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}
