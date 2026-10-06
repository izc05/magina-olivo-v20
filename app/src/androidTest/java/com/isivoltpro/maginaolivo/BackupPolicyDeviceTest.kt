package com.isivoltpro.maginaolivo

import android.content.pm.ApplicationInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** #461: the installed APK, not only its sources, opts out of Android backup. */
@RunWith(AndroidJUnit4::class)
class BackupPolicyDeviceTest {
    @Test
    fun theInstalledAppDoesNotAllowBackup() {
        val info = InstrumentationRegistry.getInstrumentation().targetContext.applicationInfo

        assertEquals(0, info.flags and ApplicationInfo.FLAG_ALLOW_BACKUP)
    }
}
