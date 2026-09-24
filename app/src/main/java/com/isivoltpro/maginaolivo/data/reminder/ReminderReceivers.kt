package com.isivoltpro.maginaolivo.data.reminder

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.isivoltpro.maginaolivo.core.time.SystemAppClock
import com.isivoltpro.maginaolivo.data.local.MaginaOlivoDatabase
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Receives a reminder alarm and shows its notification, entirely from local data. */
class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        val reminderId = intent.getStringExtra(EXTRA_REMINDER_ID)
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: return
        val pending = goAsync()
        scope.launch {
            try {
                val database = MaginaOlivoDatabase.getInstance(context)
                ReminderNotifier(context, database, SystemAppClock()).fire(reminderId)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_FIRE = "com.isivoltpro.maginaolivo.action.REMINDER"
        const val EXTRA_REMINDER_ID = "com.isivoltpro.maginaolivo.extra.REMINDER_ID"
    }
}

/**
 * Android drops every alarm on reboot and may shift them when the clock or the app
 * changes; the stored reminders are projected again each time.
 */
class ReminderRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED) return
        val pending = goAsync()
        scope.launch {
            try {
                ReminderCoordinator(
                    MaginaOlivoDatabase.getInstance(context),
                    AndroidReminderScheduler(context),
                    SystemAppClock(),
                ).reconcile()
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val HANDLED = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
        )
    }
}

private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
