package com.isivoltpro.maginaolivo.data.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.isivoltpro.maginaolivo.R
import com.isivoltpro.maginaolivo.core.time.AppClock
import com.isivoltpro.maginaolivo.data.local.MaginaOlivoDatabase
import com.isivoltpro.maginaolivo.data.local.model.ActivityStatus
import com.isivoltpro.maginaolivo.domain.agenda.ActivityPlanning
import com.isivoltpro.maginaolivo.domain.agenda.ReminderMessage
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

/** Outcome of an alarm, reported for the Gate evidence. */
enum class ReminderOutcome { POSTED, NOTIFICATIONS_OFF, NOT_DUE }

/**
 * Turns a fired alarm into a notification, reading everything from the local database.
 * A reminder whose work was completed, cancelled or archived since it was scheduled stays
 * silent; one that fired is marked so it never rings twice.
 */
class ReminderNotifier(
    context: Context,
    private val database: MaginaOlivoDatabase,
    private val clock: AppClock,
) {
    private val context = context.applicationContext

    suspend fun fire(reminderId: UUID): ReminderOutcome {
        val reminder = database.agendaDao().findReminder(reminderId) ?: return ReminderOutcome.NOT_DUE
        if (!reminder.enabled || reminder.firedAt != null) return ReminderOutcome.NOT_DUE
        val row = database.activityDao().findWithTargets(reminder.ownerId) ?: return ReminderOutcome.NOT_DUE
        val activity = row.activity
        if (activity.status != ActivityStatus.PLANNED || activity.metadata.deletedAt != null) return ReminderOutcome.NOT_DUE

        val zone = ZoneId.systemDefault()
        val message = ReminderMessage.of(
            description = activity.description,
            date = activity.activityDate,
            today = clock.today(zone),
            planning = row.planning?.let {
                ActivityPlanning(
                    startTime = it.plannedStartTime?.let { time -> runCatching { LocalTime.parse(time) }.getOrNull() },
                    expectedDurationMinutes = it.expectedDurationMinutes,
                    expectedPeopleCount = it.expectedPeopleCount,
                    crewText = it.crewText,
                )
            },
            farmName = activity.farmId?.let { database.farmDao().findById(it)?.name },
            parcelNames = row.targets.map { it.parcelNameAtTarget },
        )
        database.agendaDao().markFired(reminder.id, clock.nowInstant())
        return if (post(reminder.localNotificationId, activity.id, message)) {
            ReminderOutcome.POSTED
        } else {
            ReminderOutcome.NOTIFICATIONS_OFF
        }
    }

    private fun post(notificationId: Int, activityId: UUID, message: ReminderMessage): Boolean {
        val permitted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val manager = NotificationManagerCompat.from(context)
        if (!permitted || !manager.areNotificationsEnabled()) return false
        ensureChannel(context)
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            ?.putExtra(EXTRA_ACTIVITY_ID, activityId.toString())
        val content = open?.let {
            PendingIntent.getActivity(context, notificationId, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle(message.title)
            .setContentText(message.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message.text))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(content)
            .build()
        return try {
            manager.notify(notificationId, notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }

    companion object {
        const val CHANNEL_ID = "planned_work"
        const val EXTRA_ACTIVITY_ID = "com.isivoltpro.maginaolivo.extra.ACTIVITY_ID"

        fun ensureChannel(context: Context) {
            val channel = NotificationChannel(CHANNEL_ID, "Trabajos planificados", NotificationManager.IMPORTANCE_DEFAULT)
            channel.description = "Avisos de los trabajos que has planificado en tu olivar."
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}
