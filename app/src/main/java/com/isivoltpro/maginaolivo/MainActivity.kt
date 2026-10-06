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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import com.isivoltpro.maginaolivo.core.common.AppResult
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private companion object {
        /** #475: the one-time reading of existing day replacements, per process. */
        val existingReplacementsMarked = java.util.concurrent.atomic.AtomicBoolean(false)
    }

    /** The planned work a tapped reminder points at, until navigation has opened it. */
    private val openActivity = mutableStateOf<UUID?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val compositionRoot = AppCompositionRoot.createAndroid(
            context = applicationContext,
            environmentValue = BuildConfig.ENVIRONMENT,
        )
        // Alarms do not survive a reinstall or a force-stop; the stored reminders do.
        compositionRoot.localPersistence?.reminders?.let { reminders ->
            lifecycleScope.launch { runCatching { reminders.reconcile() } }
        }
        // #475: what counts today keeps counting once «Se añade / Sustituye» is explicit. It runs to
        // the end before any screen exists, so no day's cost is written under the new rule first
        // (Codex on #583); once per process, it is a short read of the drafted calculated costs.
        compositionRoot.localPersistence?.dayCostRepository?.let { dayCosts ->
            if (existingReplacementsMarked.compareAndSet(false, true)) {
                val result = runBlocking(Dispatchers.IO) { runCatching { dayCosts.markExistingReplacements() }.getOrNull() }
                if (result !is AppResult.Success) existingReplacementsMarked.set(false)
            }
        }
        // #458: automatic days without Pesadas stop being attributed to every Parcel.
        compositionRoot.localPersistence?.harvestRepository?.let { harvests ->
            lifecycleScope.launch { runCatching { harvests.clearUnfoundedDayOrigins() } }
        }
        if (savedInstanceState == null) openActivity.value = activityIdOf(intent)

        setContent {
            AppRoot(
                compositionRoot = compositionRoot,
                openActivityId = openActivity.value,
                onActivityOpened = { openActivity.value = null },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        activityIdOf(intent)?.let { openActivity.value = it }
    }

    private fun activityIdOf(intent: Intent?): UUID? =
        intent?.getStringExtra(ReminderNotifier.EXTRA_ACTIVITY_ID)
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
}
