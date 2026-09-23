package com.isivoltpro.maginaolivo.data.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Registers reminders with [AlarmManager]. Alarms fire with the phone offline and idle.
 *
 * An exact alarm is used when Android allows it; otherwise the reminder still fires, only
 * inexactly — the app never asks for more than it needs to warn about tomorrow's work.
 */
class AndroidReminderScheduler(context: Context) : ReminderScheduler {
    private val context = context.applicationContext
    private val alarms = this.context.getSystemService(AlarmManager::class.java)

    override fun schedule(reminder: ScheduledReminder) {
        val operation = PendingIntent.getBroadcast(
            context,
            reminder.requestCode,
            intent().putExtra(ReminderAlarmReceiver.EXTRA_REMINDER_ID, reminder.reminderId.toString()),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val at = reminder.triggerAt.toEpochMilli()
        val exactAllowed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) alarms.canScheduleExactAlarms() else true
        try {
            if (exactAllowed) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
            } else {
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
            }
        } catch (_: SecurityException) {
            // The exact-alarm grant was withdrawn between the check and the call.
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
        }
    }

    override fun cancel(requestCode: Int) {
        val operation = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent(),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        alarms.cancel(operation)
        operation.cancel()
    }

    /** True when an alarm is registered for [requestCode]. Used by the Gate evidence. */
    fun isScheduled(requestCode: Int): Boolean =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            intent(),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) != null

    private fun intent() = Intent(context, ReminderAlarmReceiver::class.java).setAction(ReminderAlarmReceiver.ACTION_FIRE)
}
