package com.isivoltpro.maginaolivo.app

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.isivoltpro.maginaolivo.navigation.AppNavigation
import com.isivoltpro.maginaolivo.feature.notifications.NotificationPermissionHost
import com.isivoltpro.maginaolivo.ui.theme.MaginaOlivoTheme
import java.util.UUID

@Composable
fun AppRoot(
    compositionRoot: AppCompositionRoot,
    openActivityId: UUID? = null,
    onActivityOpened: () -> Unit = {},
    openAccount: Boolean = false,
    onAccountOpened: () -> Unit = {},
    privateDataReady: Boolean = true,
) {
    val appearance by compositionRoot.appearanceStore.mode.collectAsStateWithLifecycle()
    MaginaOlivoTheme(mode = appearance) {
        if(!privateDataReady) {
            androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize(),contentAlignment=androidx.compose.ui.Alignment.Center) {
                androidx.compose.material3.CircularProgressIndicator()
            }
        } else {
        val protectedContent: @Composable () -> Unit = {
        NotificationPermissionHost(compositionRoot.localPersistence, compositionRoot.clock) {
            AppNavigation(
                compositionRoot = compositionRoot,
                openActivityId = openActivityId,
                onActivityOpened = onActivityOpened,
                openAccount = openAccount,
                onAccountOpened = onAccountOpened,
            )
        }
        }
        val links=compositionRoot.farmLinks
        if(links==null) protectedContent()
        else com.isivoltpro.maginaolivo.feature.accountlink.FarmLinkAccessHost(links,compositionRoot.accountRepository,protectedContent)
        }
    }
}
