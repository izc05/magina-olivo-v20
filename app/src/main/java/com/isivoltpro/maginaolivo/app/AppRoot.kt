package com.isivoltpro.maginaolivo.app

import androidx.compose.runtime.Composable
import com.isivoltpro.maginaolivo.navigation.AppNavigation
import com.isivoltpro.maginaolivo.ui.theme.MaginaOlivoTheme
import java.util.UUID

@Composable
fun AppRoot(
    compositionRoot: AppCompositionRoot,
    openActivityId: UUID? = null,
    onActivityOpened: () -> Unit = {},
) {
    MaginaOlivoTheme {
        AppNavigation(
            compositionRoot = compositionRoot,
            openActivityId = openActivityId,
            onActivityOpened = onActivityOpened,
        )
    }
}
