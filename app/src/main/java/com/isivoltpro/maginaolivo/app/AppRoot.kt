package com.isivoltpro.maginaolivo.app

import androidx.compose.runtime.Composable
import com.isivoltpro.maginaolivo.navigation.AppNavigation
import com.isivoltpro.maginaolivo.ui.theme.MaginaOlivoTheme

@Composable
fun AppRoot(
    compositionRoot: AppCompositionRoot,
) {
    MaginaOlivoTheme {
        AppNavigation(compositionRoot = compositionRoot)
    }
}
