package com.isivoltpro.maginaolivo

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.isivoltpro.maginaolivo.app.AndroidOnboardingStateStore
import com.isivoltpro.maginaolivo.data.local.MaginaOlivoDatabase
import com.isivoltpro.maginaolivo.data.local.entity.*
import com.isivoltpro.maginaolivo.data.local.model.ActivityStatus
import com.isivoltpro.maginaolivo.data.reminder.AndroidReminderScheduler
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runners.model.Statement

/** Runtime modes run in separate processes; the outer script restores original POST grants/flags. */
class ContextNotificationPermissionTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val mode = InstrumentationRegistry.getArguments().getString("notificationQaMode") ?: "baseline"
    private val workspace = UUID.randomUUID()
    private val work = UUID.randomUUID()
    private val reminder = UUID.randomUUID()
    private val notificationCode = reminder.hashCode()
    private var seeded = false

    private val emulatorOnly = TestRule { base, _ -> object : Statement() {
        override fun evaluate() {
            assertEquals("com.isivoltpro.maginaolivo.dev", context.packageName)
            android.os.ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("getprop ro.kernel.qemu"))
                .bufferedReader().use { assertEquals("Never change a physical phone automatically", "1", it.readText().trim()) }
            base.evaluate()
        }
    } }
    private val seed = TestRule { base, _ -> object : Statement() {
        override fun evaluate() {
            runBlocking {
                val db = MaginaOlivoDatabase.getInstance(context)
                val now = Instant.now()
                val metadata = LocalMetadata(now, now)
                db.workspaceDao().upsert(WorkspaceEntity(workspace, "Permisos sintéticos", UUID.randomUUID(), "ES", "Europe/Madrid", "es-ES", "EUR", metadata))
                db.profileSettingsDao().upsert(ProfileSettingsEntity(UUID.randomUUID(), workspace, remindersEnabled = false, metadata = metadata))
                db.activityDao().upsert(ActivityEntity(work, workspace, activityDate = LocalDate.now(ZoneId.of("Europe/Madrid")).plusDays(1), type = "OTHER", status = ActivityStatus.PLANNED, description = "Trabajo sintético conservado", metadata = metadata))
                db.agendaDao().upsertReminders(listOf(ReminderEntity(reminder, workspace, "ACTIVITY", work, now.plusSeconds(86_400), "CUSTOM", localNotificationId = notificationCode, metadata = metadata)))
                seeded = true
            }
            check(context.getSharedPreferences(AndroidOnboardingStateStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(AndroidOnboardingStateStore.KEY_COMPLETED, true).commit())
            base.evaluate()
        }
    } }
    @get:Rule val rules: TestRule = RuleChain.outerRule(emulatorOnly).around(IsolatedAppStateRule()).around(seed).around(compose)

    @After fun cancelOnlyTheSyntheticAlarm() { if (seeded) AndroidReminderScheduler(context).cancel(notificationCode) }

    @Test fun actualProfileRequestsOnlyAfterSuccessfulReminderActivation() {
        compose.waitUntil(15_000) { runCatching { compose.onAllNodesWithTag("top-profile-avatar").fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false) }
        compose.onNodeWithTag("top-profile-avatar").performClick()
        compose.waitUntil(15_000) {
            runCatching { compose.onNodeWithTag("profile-reminders-switch").fetchSemanticsNode().config[SemanticsProperties.ToggleableState] == ToggleableState.Off }.getOrDefault(false)
        }
        compose.onNodeWithTag("notification-permission-dialog").assertDoesNotExist()
        assertWorkPreserved()
        if (mode == "baseline") return
        assertTrue("Runtime modes require Android13+", Build.VERSION.SDK_INT >= 33)
        assertEquals(PackageManager.PERMISSION_DENIED, ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS))
        compose.onNodeWithTag("profile-reminders-switch").performScrollTo().performClick()
        compose.waitUntil(10_000) {
            runBlocking { MaginaOlivoDatabase.getInstance(context).profileSettingsDao().findForWorkspace(workspace)?.remindersEnabled == true }
        }
        compose.waitUntil(8_000) { compose.onAllNodesWithTag("notification-permission-dialog").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Para recibir avisos de los trabajos planificados, Android necesita permitir las notificaciones de Mágina Olivo.").assertIsDisplayed()
        capture("explanation")
        assertWorkPreserved()
        if (mode == "later") {
            compose.onNodeWithTag("notification-permission-later").performClick()
        } else {
            compose.onNodeWithTag("notification-permission-continue").performClick()
            val button = if (mode == "grant") "permission_allow_button" else "permission_deny_button"
            compose.waitUntil(10_000) { clickPermissionButton(button) }
            compose.waitUntil(10_000) {
                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                if (mode == "grant") granted && compose.onAllNodesWithText("Activados. Android controla el sonido y la vibración.").fetchSemanticsNodes().isNotEmpty()
                else !granted && compose.onAllNodesWithText("Android bloquea los avisos en este teléfono.").fetchSemanticsNodes().isNotEmpty()
            }
        }
        compose.onNodeWithTag("notification-permission-dialog").assertDoesNotExist()
        capture("result")
        assertWorkPreserved()
        compose.activityRule.scenario.recreate()
        // Recreation restores the visible Perfil entry, rather than navigating to Inicio.
        // Keep checking both navigation restoration and the persisted reminder choice.
        compose.waitUntil(15_000) {
            compose.onAllNodesWithTag("profile-root").fetchSemanticsNodes().isNotEmpty() &&
                runCatching {
                    compose.onNodeWithTag("profile-reminders-switch").fetchSemanticsNode()
                        .config[SemanticsProperties.ToggleableState] == ToggleableState.On
                }.getOrDefault(false)
        }
        compose.onNodeWithTag("profile-reminders-switch").performScrollTo().assertIsOn()
        compose.onNodeWithTag("notification-permission-dialog").assertDoesNotExist()
        assertWorkPreserved()
    }

    private fun clickPermissionButton(id: String): Boolean {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val info = automation.serviceInfo
        info.flags = info.flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        automation.serviceInfo = info
        fun find(node: android.view.accessibility.AccessibilityNodeInfo?): android.view.accessibility.AccessibilityNodeInfo? {
            node ?: return null
            if (node.viewIdResourceName?.endsWith(":id/$id") == true) return node
            for (index in 0 until node.childCount) find(node.getChild(index))?.let { return it }
            return null
        }
        return find(automation.rootInActiveWindow)?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK) == true
    }

    private fun capture(stage: String) {
        compose.waitForIdle()
        val config = context.resources.configuration
        val file = java.io.File(context.getExternalFilesDir(null), "notification-permission-evidence/$mode-$stage-sw${config.smallestScreenWidthDp}-font${config.fontScale}-night${config.uiMode and 48}.png")
        check(file.parentFile!!.mkdirs() || file.parentFile!!.isDirectory)
        val bitmap = checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        file.outputStream().use { check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)) }
    }

    private fun assertWorkPreserved() = runBlocking {
        val db = MaginaOlivoDatabase.getInstance(context)
        val row = checkNotNull(db.activityDao().findWithTargets(work))
        assertEquals(ActivityStatus.PLANNED, row.activity.status)
        assertEquals("Trabajo sintético conservado", row.activity.description)
        val notification = checkNotNull(db.agendaDao().findReminder(reminder))
        assertTrue(notification.enabled)
        assertNull(notification.firedAt)
    }
}
