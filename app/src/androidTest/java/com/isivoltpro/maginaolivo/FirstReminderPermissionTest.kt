package com.isivoltpro.maginaolivo

import com.isivoltpro.maginaolivo.data.local.FixtureWorkspace

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import com.isivoltpro.maginaolivo.ui.theme.MoSurfaceTokens
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.isivoltpro.maginaolivo.app.AppCompositionRoot
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.core.dispatchers.DefaultAppDispatchers
import com.isivoltpro.maginaolivo.core.id.UuidGenerator
import com.isivoltpro.maginaolivo.core.time.SystemAppClock
import com.isivoltpro.maginaolivo.data.local.MaginaOlivoDatabase
import com.isivoltpro.maginaolivo.data.local.entity.*
import com.isivoltpro.maginaolivo.data.local.model.ActivityStatus
import com.isivoltpro.maginaolivo.data.reminder.AndroidReminderScheduler
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstFarmRepository
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstParcelRepository
import com.isivoltpro.maginaolivo.domain.activity.ActivityType
import com.isivoltpro.maginaolivo.domain.agenda.ReminderKind
import com.isivoltpro.maginaolivo.domain.agenda.ReminderRequest
import com.isivoltpro.maginaolivo.domain.farm.NewFarm
import com.isivoltpro.maginaolivo.domain.parcel.NewParcel
import com.isivoltpro.maginaolivo.feature.activities.ActivityDraft
import com.isivoltpro.maginaolivo.feature.activities.FarmActivitiesRoute
import com.isivoltpro.maginaolivo.feature.notifications.NotificationPermissionHost
import com.isivoltpro.maginaolivo.feature.notifications.hasUpcomingReminder
import com.isivoltpro.maginaolivo.ui.theme.MaginaOlivoTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runners.model.Statement

/** Production creation form, ViewModel and Room. Android grants are restored by the outer script. */
class FirstReminderPermissionTest {
    private val compose = createAndroidComposeRule<ComponentActivity>()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val workspace = UUID.randomUUID()
    private lateinit var farm: UUID
    private lateinit var parcel: UUID
    private val clock = SystemAppClock()
    private val mode = InstrumentationRegistry.getArguments().getString("notificationQaMode") ?: "baseline"
    private val guard = TestRule { base, _ -> object : Statement() {
        override fun evaluate() {
            assertEquals("com.isivoltpro.maginaolivo.dev", context.packageName)
            android.os.ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("getprop ro.kernel.qemu"))
                .bufferedReader().use { assertEquals("Emulator only", "1", it.readText().trim()) }
            base.evaluate()
        }
    } }
    private val seed = TestRule { base, _ -> object : Statement() {
        override fun evaluate() {
            runBlocking {
                val db = MaginaOlivoDatabase.getInstance(context)
                val now = Instant.now()
                val metadata = LocalMetadata(now, now)
                db.workspaceDao().upsert(WorkspaceEntity(workspace, "Primer aviso sintético", UUID.randomUUID(), "ES", "Europe/Madrid", "es-ES", "EUR", metadata))
                db.profileSettingsDao().upsert(ProfileSettingsEntity(UUID.randomUUID(), workspace, remindersEnabled = true, metadata = metadata))
                farm = (OfflineFirstFarmRepository(db, clock, UuidGenerator(), DefaultAppDispatchers(), workspaceRepository = FixtureWorkspace { db }).create(NewFarm(workspace, "Finca sintética")) as AppResult.Success).value
                parcel = (OfflineFirstParcelRepository(db, clock, UuidGenerator(), DefaultAppDispatchers(), workspaceRepository = FixtureWorkspace { db }).create(NewParcel(farm, "Parcela sintética")) as AppResult.Success).value
            }
            base.evaluate()
        }
    } }
    @get:Rule val rules: TestRule = RuleChain.outerRule(guard).around(IsolatedAppStateRule()).around(seed).around(compose)

    @After fun cancelOnlyFixtureAlarms() = runBlocking {
        if (::farm.isInitialized) {
            val db = MaginaOlivoDatabase.getInstance(context)
            db.activityDao().observeForFarm(farm).first().forEach { row ->
                db.agendaDao().listForOwner("ACTIVITY", row.activity.id).forEach { AndroidReminderScheduler(context).cancel(it.localNotificationId) }
            }
        }
    }

    @Test fun firstSavedReminderRequestsPermissionAndPreservesWork() {
        val root = AppCompositionRoot.createAndroid(context, BuildConfig.ENVIRONMENT)
        val local = checkNotNull(root.localPersistence)
        compose.setContent {
            MaginaOlivoTheme {
                NotificationPermissionHost(local, root.clock) {
                    androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.fillMaxSize()
                        .background(MoSurfaceTokens.appBackground).statusBarsPadding().padding(horizontal = MoSpacing.screen),
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(MoSpacing.md)) {
                    FarmActivitiesRoute(farm, local, {}, startWithEditor = true, editorAsScreen = true,
                        initialDraft = ActivityDraft(type = ActivityType.OTHER,
                            activityDate = LocalDate.now(ZoneId.of("Europe/Madrid")).plusDays(3),
                            description = "Trabajo y aviso conservados", parcelIds = setOf(parcel),
                            reminders = listOf(ReminderRequest(ReminderKind.SAME_DAY))))
                    }
                }
            }
        }
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("save-activity").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("notification-permission-dialog").assertDoesNotExist()
        assertEquals(0, runBlocking { local.database.activityDao().countPlannedForFarm(farm) })
        compose.onNodeWithTag("save-activity").performScrollTo().performClick()
        compose.waitUntil(15_000) { runBlocking { local.database.activityDao().countPlannedForFarm(farm) == 1 } }
        val id = runBlocking { local.database.activityDao().observeForFarm(farm).first().single().activity.id }
        val denied = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (denied) {
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("notification-permission-dialog").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Para avisarte de este trabajo, Android necesita permitir las notificaciones de Mágina Olivo.").assertIsDisplayed()
            capture("explanation")
            assertPersisted(id)
            if (mode == "baseline" || mode == "later") compose.onNodeWithTag("notification-permission-later").performClick()
            else {
                compose.onNodeWithTag("notification-permission-continue").performClick()
                compose.waitUntil(10_000) { clickPermissionButton(if (mode == "grant") "permission_allow_button" else "permission_deny_button") }
                compose.waitUntil(10_000) {
                    (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) == (mode == "grant")
                }
            }
        } else assertEquals("External runtime modes must start denied", "baseline", mode)
        compose.onNodeWithTag("notification-permission-dialog").assertDoesNotExist()
        assertPersisted(id)
        compose.onNodeWithTag("add-activity").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("save-activity").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("save-activity").performScrollTo().performClick()
        compose.waitUntil(15_000) { runBlocking { local.database.activityDao().countPlannedForFarm(farm) == 2 } }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("activities-saved").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("notification-permission-dialog").assertDoesNotExist()
        assertPersisted(id)
        val second = runBlocking { local.database.activityDao().observeForFarm(farm).first().single { it.activity.id != id }.activity.id }
        assertPersisted(second)
        assertTrue(runBlocking { hasUpcomingReminder(local.database, second, clock) })
        capture("saved")
    }

    @Test fun onlyLiveFuturePlannedRemindersAreEligible() = runBlocking {
        val db = MaginaOlivoDatabase.getInstance(context)
        val id = UUID.randomUUID()
        val now = Instant.now()
        val metadata = LocalMetadata(now, now)
        val activity = ActivityEntity(id, workspace, farmId = farm, activityDate = LocalDate.now().plusDays(3), type = "OTHER", status = ActivityStatus.PLANNED, description = "Elegibilidad sintética", metadata = metadata)
        val reminderId = UUID.randomUUID()
        val reminder = ReminderEntity(reminderId, workspace, "ACTIVITY", id, now.plusSeconds(86_400), "CUSTOM", localNotificationId = reminderId.hashCode(), metadata = metadata)
        db.activityDao().upsert(activity)
        db.agendaDao().upsertReminders(listOf(reminder))
        assertTrue(hasUpcomingReminder(db, id, clock))
        for (status in listOf(ActivityStatus.DRAFT, ActivityStatus.COMPLETED, ActivityStatus.CANCELLED)) {
            db.activityDao().upsert(activity.copy(status = status)); assertFalse(hasUpcomingReminder(db, id, clock))
        }
        db.activityDao().upsert(activity.copy(metadata = metadata.copy(deletedAt = now)))
        assertFalse(hasUpcomingReminder(db, id, clock))
        db.activityDao().upsert(activity)
        for (invalid in listOf(reminder.copy(enabled = false), reminder.copy(firedAt = now), reminder.copy(triggerAt = now.minusSeconds(1)), reminder.copy(metadata = metadata.copy(deletedAt = now)))) {
            db.agendaDao().upsertReminders(listOf(invalid)); assertFalse(hasUpcomingReminder(db, id, clock))
        }
        db.agendaDao().upsertReminders(listOf(reminder))
        val preferences = checkNotNull(db.profileSettingsDao().findForWorkspace(workspace))
        db.profileSettingsDao().upsert(preferences.copy(remindersEnabled = false))
        assertFalse(hasUpcomingReminder(db, id, clock))
        assertFalse(hasUpcomingReminder(db, UUID.randomUUID(), clock))
    }

    @Test fun saveAcrossRecreationStillRequestsPermission() {
        val root = AppCompositionRoot.createAndroid(context, BuildConfig.ENVIRONMENT)
        val local = checkNotNull(root.localPersistence)
        fun installContent() { compose.setContent {
            MaginaOlivoTheme { NotificationPermissionHost(local, root.clock) {
                androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.fillMaxSize().statusBarsPadding()) {
                    FarmActivitiesRoute(farm, local, {}, startWithEditor = true, editorAsScreen = true,
                        initialDraft = ActivityDraft(type = ActivityType.OTHER, activityDate = LocalDate.now().plusDays(3),
                            description = "Guardado durante recreación", parcelIds = setOf(parcel), reminders = listOf(ReminderRequest(ReminderKind.SAME_DAY))))
                }
            } }
        } }
        installContent()
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("save-activity").fetchSemanticsNodes().isNotEmpty() }
        val name = checkNotNull(local.database.openHelper.databaseName)
        assertTrue(name.startsWith("isolated-main-"))
        val writer = android.database.sqlite.SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null,
            android.database.sqlite.SQLiteDatabase.OPEN_READWRITE or android.database.sqlite.SQLiteDatabase.ENABLE_WRITE_AHEAD_LOGGING)
        try {
            writer.beginTransactionNonExclusive()
            compose.onNodeWithTag("save-activity").performScrollTo().performClick()
            compose.onNodeWithTag("save-activity").assertIsNotEnabled()
            compose.activityRule.scenario.recreate()
            installContent()
        } finally { if (writer.inTransaction()) writer.endTransaction(); writer.close() }
        compose.waitUntil(15_000) { runBlocking { local.database.activityDao().countPlannedForFarm(farm) == 1 } }
        val id = runBlocking { local.database.activityDao().observeForFarm(farm).first().single().activity.id }
        assertPersisted(id)
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("notification-permission-dialog").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("notification-permission-later").performClick()
        }
        assertPersisted(id)
    }

    private fun assertPersisted(id: UUID) = runBlocking {
        val db = MaginaOlivoDatabase.getInstance(context)
        assertEquals(ActivityStatus.PLANNED, checkNotNull(db.activityDao().findWithTargets(id)).activity.status)
        val reminder = db.agendaDao().listForOwner("ACTIVITY", id).single()
        assertTrue(reminder.enabled); assertNull(reminder.firedAt)
        assertTrue(reminder.triggerAt > Instant.now())
    }
    private fun clickPermissionButton(id: String): Boolean {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val info = automation.serviceInfo
        info.flags = info.flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        automation.serviceInfo = info
        fun find(node: android.view.accessibility.AccessibilityNodeInfo?): android.view.accessibility.AccessibilityNodeInfo? {
            node ?: return null
            if (node.viewIdResourceName?.endsWith(":id/$id") == true) return node
            for (i in 0 until node.childCount) find(node.getChild(i))?.let { return it }
            return null
        }
        return find(automation.rootInActiveWindow)?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK) == true
    }
    private fun capture(stage: String) {
        compose.waitForIdle()
        val config = context.resources.configuration
        val file = java.io.File(context.getExternalFilesDir(null), "first-reminder-evidence/$mode-$stage-sw${config.smallestScreenWidthDp}-font${config.fontScale}-night${config.uiMode and 48}.png")
        check(file.parentFile!!.mkdirs() || file.parentFile!!.isDirectory)
        file.outputStream().use { check(checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()).compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)) }
    }
}
