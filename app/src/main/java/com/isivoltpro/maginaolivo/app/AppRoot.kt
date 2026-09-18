package com.isivoltpro.maginaolivo.app

import androidx.compose.runtime.Composable
import com.isivoltpro.maginaolivo.FoundationScreen
import com.isivoltpro.maginaolivo.ui.reference.Phase3GalleryScreen
import com.isivoltpro.maginaolivo.ui.theme.OlivarTheme

@Composable
fun AppRoot(
    compositionRoot: AppCompositionRoot,
) {
    OlivarTheme {
        if (compositionRoot.environment == AppEnvironment.DEV) {
            Phase3GalleryScreen()
        } else {
            FoundationScreen(
                environment = compositionRoot.environment,
            )
        }
    }
}
