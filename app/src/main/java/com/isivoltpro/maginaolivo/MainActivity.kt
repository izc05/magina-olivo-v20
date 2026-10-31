package com.isivoltpro.maginaolivo

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import com.isivoltpro.maginaolivo.app.AppCompositionRoot
import com.isivoltpro.maginaolivo.app.AppRoot
import com.isivoltpro.maginaolivo.data.reminder.ReminderNotifier
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import com.isivoltpro.maginaolivo.core.common.AppResult
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private companion object {
        /** #475: the one-time reading of existing day replacements, per process. */
        val existingReplacements = com.isivoltpro.maginaolivo.app.LocalStartupCorrection()
        const val OPEN_ACCOUNT_PENDING = "open_account_pending"
    }

    /** The planned work a tapped reminder points at, until navigation has opened it. */
    private val openActivity = mutableStateOf<UUID?>(null)
    private val openAccount = mutableStateOf(false)
    private val localDataReady = mutableStateOf(false)
    private lateinit var account: com.isivoltpro.maginaolivo.feature.account.AccountRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val compositionRoot = AppCompositionRoot.createAndroid(
            context = applicationContext,
            environmentValue = BuildConfig.ENVIRONMENT,
        )
        account = compositionRoot.accountRepository
        openAccount.value = savedInstanceState?.getBoolean(OPEN_ACCOUNT_PENDING) ?: false
        if(savedInstanceState==null) handleAccountIntent(intent)
        // Restore cached identity off the main thread: callback exchange can survive recreation
        // while holding the Auth mutex. Never block Activity.onCreate waiting for that exchange.
        lifecycleScope.launch(Dispatchers.IO) {
            account.resume(false)
            val hasOfflineVerifiedAccess = !account.state.value.online && account.state.value.userId != null
            val isAuthorized = when {
                compositionRoot.environment in setOf(
                    com.isivoltpro.maginaolivo.app.AppEnvironment.PRODUCTION,
                    com.isivoltpro.maginaolivo.app.AppEnvironment.STAGING
                ) -> {
                    account.isConfigured && (
                        account.state.value.status in setOf(
                            com.isivoltpro.maginaolivo.feature.account.AccountStatus.SIGNED_IN,
                            com.isivoltpro.maginaolivo.feature.account.AccountStatus.OFFLINE
                        ) || hasOfflineVerifiedAccess
                    )
                }
                account.isConfigured && !account.state.value.simulated -> {
                    account.state.value.status in setOf(
                        com.isivoltpro.maginaolivo.feature.account.AccountStatus.SIGNED_IN,
                        com.isivoltpro.maginaolivo.feature.account.AccountStatus.OFFLINE
                    ) || hasOfflineVerifiedAccess
                }
                else -> true
            }
            if(isAuthorized && compositionRoot.farmLinks?.accessAllowed()!=false) {
                compositionRoot.localPersistence?.reminders?.let { runCatching { it.reconcile() } }
                // #475: complete this one-time correction before any agricultural screen exists.
                compositionRoot.localPersistence?.dayCostRepository?.let { dayCosts ->
                    existingReplacements.ensure {
                        val result=try { dayCosts.markExistingReplacements() }
                        catch(e:kotlinx.coroutines.CancellationException) { throw e }
                        catch(_:Exception) { null }
                        result is AppResult.Success
                    }
                }
                // #458: automatic days without Pesadas are not attributed to every Parcel.
                compositionRoot.localPersistence?.harvestRepository?.let { runCatching { it.clearUnfoundedDayOrigins() } }
            }
            kotlinx.coroutines.withContext(Dispatchers.Main) { localDataReady.value=true }
        }
        if (savedInstanceState == null) openActivity.value = activityIdOf(intent)

        setContent {
            AppRoot(
                privateDataReady = localDataReady.value,
                compositionRoot = compositionRoot,
                openActivityId = openActivity.value,
                onActivityOpened = { openActivity.value = null },
                openAccount = openAccount.value,
                onAccountOpened = { openAccount.value = false },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleAccountIntent(intent)
        activityIdOf(intent)?.let { openActivity.value = it }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        // Navigation may not have consumed the callback when rotation saves the back stack.
        // Save only this pending action, never the callback URI/code or session credentials.
        outState.putBoolean(OPEN_ACCOUNT_PENDING, openAccount.value)
        super.onSaveInstanceState(outState)
    }

    private fun activityIdOf(intent: Intent?): UUID? =
        intent?.getStringExtra(ReminderNotifier.EXTRA_ACTIVITY_ID)
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
    private fun handleAccountIntent(incoming: Intent?) {
        val url=incoming?.dataString ?: return
        if(!account.acceptsLink(url)) return
        // Retain neither callback codes nor bearer tokens in a saved Activity intent.
        setIntent(Intent(intent).setData(null))
        openAccount.value=true
        val connectivity=getSystemService(android.net.ConnectivityManager::class.java)
        val online=connectivity?.getNetworkCapabilities(connectivity.activeNetwork)?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED)==true
        account.receiveLink(url,online)
    }
}
