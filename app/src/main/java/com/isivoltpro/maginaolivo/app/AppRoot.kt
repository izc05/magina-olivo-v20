package com.isivoltpro.maginaolivo.app

import androidx.compose.runtime.Composable
import com.isivoltpro.maginaolivo.FoundationScreen

@Composable
fun AppRoot(
    compositionRoot: AppCompositionRoot,
) {
    FoundationScreen(
        environment = compositionRoot.environment,
    )
}
