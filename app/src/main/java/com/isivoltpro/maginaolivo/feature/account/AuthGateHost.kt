package com.isivoltpro.maginaolivo.feature.account

import android.app.NotificationManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.isivoltpro.maginaolivo.app.AppEnvironment
import com.isivoltpro.maginaolivo.data.reminder.ReminderNotifier
import com.isivoltpro.maginaolivo.data.repository.LocalFarmLinkRepository
import com.isivoltpro.maginaolivo.ui.theme.MoColors
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing
import com.isivoltpro.maginaolivo.ui.theme.MoSurfaceTokens

/**
 * Mandatory Access Gate for Mágina Olivo 1.0.
 *
 * Rules:
 * - In PRODUCTION: Strict fail-closed. No agricultural content is EVER accessible unless the user
 *   has an authenticated session (SIGNED_IN or OFFLINE). If credentials are missing or unconfigured,
 *   access is blocked with a clear warning without mounting agricultural navigation.
 * - In STAGING or configured DEV: Access requires an authenticated session (SIGNED_IN or OFFLINE).
 * - In unconfigured DEV only: Access is preserved for emulator evidence and smoke test harnesses.
 *
 * Prevents back navigation, bottom bar clicks, and deep link leaks when unauthenticated.
 */
@Composable
fun AuthGateHost(
    accountRepository: AccountRepository,
    farmLinks: LocalFarmLinkRepository? = null,
    simulationAllowed: Boolean = false,
    environment: AppEnvironment = AppEnvironment.DEV,
    content: @Composable () -> Unit
) {
    val identity by accountRepository.state.collectAsStateWithLifecycle()
    val hasOfflineVerifiedAccess = !identity.online && identity.userId != null
    val isAuthorized = when {
        environment in setOf(AppEnvironment.PRODUCTION, AppEnvironment.STAGING) -> {
            // Strict fail-closed in production and staging: ALWAYS require authenticated session or verified offline access
            accountRepository.isConfigured && (
                identity.status in setOf(AccountStatus.SIGNED_IN, AccountStatus.OFFLINE) || hasOfflineVerifiedAccess
            )
        }
        accountRepository.isConfigured && !identity.simulated -> {
            identity.status in setOf(AccountStatus.SIGNED_IN, AccountStatus.OFFLINE) || hasOfflineVerifiedAccess
        }
        else -> {
            // Unconfigured DEV harness or simulation QA only
            true
        }
    }
    val context = LocalContext.current

    LaunchedEffect(isAuthorized) {
        if (!isAuthorized) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager?.activeNotifications
                ?.filter { it.notification.channelId == ReminderNotifier.CHANNEL_ID }
                ?.forEach { manager.cancel(it.id) }
        }
    }

    if (accountRepository.isConfigured && identity.status == AccountStatus.CHECKING) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MoSurfaceTokens.appBackground)
                .statusBarsPadding(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(modifier = Modifier.testTag("auth-gate-loading"))
        }
    } else if (isAuthorized) {
        content()
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .testTag("auth-gate-root")
        ) {
            if (environment in setOf(AppEnvironment.PRODUCTION, AppEnvironment.STAGING) && !accountRepository.isConfigured) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MoSurfaceTokens.appBackground)
                        .statusBarsPadding()
                        .padding(MoSpacing.screen),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Acceso restringido",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MoColors.current.primaryText
                    )
                    Spacer(Modifier.height(MoSpacing.md))
                    Text(
                        text = "El servicio de autenticación no está disponible o no ha sido configurado. Por seguridad, el acceso a las funciones agrícolas permanece protegido.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MoSurfaceTokens.secondaryText,
                        modifier = Modifier.testTag("auth-gate-unconfigured-message")
                    )
                }
            } else {
                AccountRoute(
                    repository = accountRepository,
                    onBack = {},
                    showBack = false,
                    simulationAllowed = simulationAllowed,
                    farmLinks = farmLinks
                )
            }
        }
    }
}
