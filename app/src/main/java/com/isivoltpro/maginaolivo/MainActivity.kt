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
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
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
