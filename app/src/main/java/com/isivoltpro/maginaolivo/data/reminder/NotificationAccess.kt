package com.isivoltpro.maginaolivo.data.reminder

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/** Permission, app and channel must all allow delivery; this does not guarantee sound. */
internal fun notificationsAllowed(context: Context, channelId: String): Boolean {
    val permitted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    if (!permitted || !NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
    val channel = context.getSystemService(NotificationManager::class.java).getNotificationChannel(channelId)
    // The notifier creates an absent channel before publishing its first notification.
    return channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE
}

/** Open the blocked channel directly when app permission is already available. */
internal fun notificationSettingsIntent(context: Context, channelId: String): Intent {
    val permitted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    val channelBlocked = context.getSystemService(NotificationManager::class.java)
        .getNotificationChannel(channelId)?.importance == NotificationManager.IMPORTANCE_NONE
    return if (permitted && NotificationManagerCompat.from(context).areNotificationsEnabled() && channelBlocked) {
        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
    } else {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
