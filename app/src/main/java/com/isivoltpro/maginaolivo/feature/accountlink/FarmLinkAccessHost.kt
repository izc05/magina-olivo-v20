package com.isivoltpro.maginaolivo.feature.accountlink

import android.app.NotificationManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.isivoltpro.maginaolivo.data.repository.LocalFarmLinkRepository
import com.isivoltpro.maginaolivo.data.reminder.ReminderNotifier
import com.isivoltpro.maginaolivo.feature.account.AccountRepository
import com.isivoltpro.maginaolivo.feature.account.AccountRoute
import com.isivoltpro.maginaolivo.ui.theme.*

/** UI, navigation and ViewModels are discarded at an identity boundary; no cross-account restore. */
@Composable
fun FarmLinkAccessHost(repository: LocalFarmLinkRepository, account: AccountRepository, content: @Composable () -> Unit) {
    val identity by account.state.collectAsStateWithLifecycle()
    val local by repository.state.collectAsStateWithLifecycle()
    val allowed=repository.accessAllowed(identity.userId)
    val context=LocalContext.current
    LaunchedEffect(allowed) {
        if(!allowed) {
            val manager=context.getSystemService(NotificationManager::class.java)
            manager?.activeNotifications?.filter { it.notification.channelId==ReminderNotifier.CHANNEL_ID }
                ?.forEach { manager.cancel(it.id) }
        }
    }
    key(identity.userId,allowed) {
        val store=remember { ViewModelStore() }
        val owner=remember { object: ViewModelStoreOwner { override val viewModelStore=store } }
        DisposableEffect(store) { onDispose { store.clear() } }
        CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
            if(allowed) content()
            else Column(Modifier.fillMaxSize().background(MoSurfaceTokens.appBackground).statusBarsPadding().testTag("farm-link-locked")) {
                Column(Modifier.padding(MoSpacing.screen),verticalArrangement=Arrangement.spacedBy(MoSpacing.sm)) {
                    Text("Tus fincas están protegidas",style=MaterialTheme.typography.titleLarge,color=MoColors.current.primaryText)
                    Text(if(local.unreadable)"No se pudo comprobar la protección local. Tus registros se conservan; no borres los datos de la aplicación."
                        else "Inicia sesión con la cuenta que elegiste al preparar la vinculación. Tus registros siguen en este teléfono.",color=MoSurfaceTokens.secondaryText)
                }
                Box(Modifier.weight(1f)) { AccountRoute(account,onBack={},showBack=false) }
            }
        }
    }
}
