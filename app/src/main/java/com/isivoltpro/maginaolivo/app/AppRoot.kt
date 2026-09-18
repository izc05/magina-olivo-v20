package com.isivoltpro.maginaolivo.app

import androidx.compose.runtime.Composable
import com.isivoltpro.maginaolivo.FoundationScreen
import com.isivoltpro.maginaolivo.ui.theme.MaginaOlivoTheme

@Composable
fun AppRoot(
    compositionRoot: AppCompositionRoot,
) {
    MaginaOlivoTheme {
        FoundationScreen(
            environment = compositionRoot.environment,
        )
    }
}
