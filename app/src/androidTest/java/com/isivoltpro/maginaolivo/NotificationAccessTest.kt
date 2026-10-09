package com.isivoltpro.maginaolivo

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.isivoltpro.maginaolivo.data.reminder.notificationSettingsIntent
import com.isivoltpro.maginaolivo.data.reminder.notificationsAllowed
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Unique fixture channels never edit or delete the farmer's planned-work channel. */
class NotificationAccessTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val manager = context.getSystemService(NotificationManager::class.java)

    @Before fun grantNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            InstrumentationRegistry.getInstrumentation().uiAutomation
                .grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    @Test fun aBlockedChannelRejectsDeliveryEvenWithAppPermissionGranted() {
        withChannel(NotificationManager.IMPORTANCE_NONE) { id ->
            assertFalse(notificationsAllowed(context, id))
            val settings = notificationSettingsIntent(context, id)
            assertEquals(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS, settings.action)
            assertEquals(context.packageName, settings.getStringExtra(Settings.EXTRA_APP_PACKAGE))
            assertEquals(id, settings.getStringExtra(Settings.EXTRA_CHANNEL_ID))
        }
    }

    @Test fun anEnabledChannelAllowsDeliveryAndOffersAppSettings() {
        withChannel(NotificationManager.IMPORTANCE_DEFAULT) { id ->
            assertTrue(notificationsAllowed(context, id))
            assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, notificationSettingsIntent(context, id).action)
        }
    }

    @Test fun aNotYetCreatedChannelDoesNotBlockTheFirstReminder() {
        assertTrue(notificationsAllowed(context, "notification-access-test-${UUID.randomUUID()}"))
    }

    private fun withChannel(importance: Int, check: (String) -> Unit) {
        val id = "notification-access-test-${UUID.randomUUID()}"
        try {
            manager.createNotificationChannel(NotificationChannel(id, "QA fixture", importance))
            check(id)
        } finally {
            manager.deleteNotificationChannel(id)
        }
    }
}
