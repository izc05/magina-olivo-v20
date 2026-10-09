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
    // #707 DARK-1/2: fixed light text remains until DARK-3; never ship unreadable night screens.
    MaginaOlivoTheme(mode = productionAppearanceMode(appearance)) {
        AppNavigation(
            compositionRoot = compositionRoot,
            openActivityId = openActivityId,
            onActivityOpened = onActivityOpened,
        )
    }
}
