package com.isivoltpro.maginaolivo.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.isivoltpro.maginaolivo.navigation.AppNavigation
import com.isivoltpro.maginaolivo.ui.theme.MaginaOlivoTheme
import java.util.UUID

@Composable
fun AppRoot(
    compositionRoot: AppCompositionRoot,
    openActivityId: UUID? = null,
    onActivityOpened: () -> Unit = {},
) {
    val appearance by compositionRoot.appearanceStore.mode.collectAsStateWithLifecycle()
    MaginaOlivoTheme(mode = appearance) {
        AppNavigation(
            compositionRoot = compositionRoot,
            openActivityId = openActivityId,
            onActivityOpened = onActivityOpened,
        )
    }
}
