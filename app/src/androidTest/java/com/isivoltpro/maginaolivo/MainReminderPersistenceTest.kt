package com.isivoltpro.maginaolivo

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.isivoltpro.maginaolivo.app.AndroidOnboardingStateStore
import com.isivoltpro.maginaolivo.app.AppCompositionRoot
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.data.local.MaginaOlivoDatabase
import com.isivoltpro.maginaolivo.data.local.entity.*
import com.isivoltpro.maginaolivo.data.local.model.ActivityStatus
import com.isivoltpro.maginaolivo.data.reminder.AndroidReminderScheduler
import com.isivoltpro.maginaolivo.data.reminder.ReminderNotifier
import com.isivoltpro.maginaolivo.domain.activity.*
import com.isivoltpro.maginaolivo.domain.agenda.*
import com.isivoltpro.maginaolivo.domain.farm.NewFarm
import com.isivoltpro.maginaolivo.domain.parcel.NewParcel
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runners.model.Statement

/** Real MainActivity detail, failed Room transaction, retry and pending-dialog recreation. */
class MainReminderPersistenceTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var work: UUID
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
                val root = AppCompositionRoot.createAndroid(context, BuildConfig.ENVIRONMENT)
                val local = checkNotNull(root.localPersistence)
                val workspace = UUID.randomUUID()
                val metadata = LocalMetadata(Instant.now(), Instant.now())
                local.database.workspaceDao().upsert(WorkspaceEntity(workspace,"Synthetic",UUID.randomUUID(),"ES","Europe/Madrid","es-ES","EUR",metadata))
                local.database.profileSettingsDao().upsert(ProfileSettingsEntity(UUID.randomUUID(),workspace,remindersEnabled=true,metadata=metadata))
                val farm = (local.farmRepository.create(NewFarm(workspace,"Synthetic")) as AppResult.Success).value
                val parcel = (local.parcelRepository.create(NewParcel(farm,"Synthetic")) as AppResult.Success).value
                work = (local.activityRepository.create(NewActivity(farmId=farm,type=ActivityType.OTHER,activityDate=LocalDate.now().plusDays(3),
                    description="Aviso conservado",parcelIds=setOf(parcel),asDraft=true,reminders=listOf(ReminderRequest(ReminderKind.SAME_DAY)))) as AppResult.Success).value
            }
            check(context.getSharedPreferences(AndroidOnboardingStateStore.PREFERENCES_NAME,Context.MODE_PRIVATE).edit()
                .putBoolean(AndroidOnboardingStateStore.KEY_COMPLETED,true).commit())
            base.evaluate()
        }
    } }
    @get:Rule val rules: TestRule = RuleChain.outerRule(guard).around(IsolatedAppStateRule()).around(seed).around(compose)
    @After fun cancelSyntheticAlarms() = runBlocking {
        if (::work.isInitialized) MaginaOlivoDatabase.getInstance(context).agendaDao().listForOwner("ACTIVITY",work)
            .forEach { AndroidReminderScheduler(context).cancel(it.localNotificationId) }
    }
    @Test fun failedPlanRetryAndRecreationPreserveReminder() {
        val db = MaginaOlivoDatabase.getInstance(context)
        assertTrue(checkNotNull(db.openHelper.databaseName).startsWith("isolated-main-"))
        compose.activityRule.scenario.onActivity { it.startActivity(Intent(it, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra(ReminderNotifier.EXTRA_ACTIVITY_ID,work.toString())) }
        waitFor("plan-activity")
        compose.onNodeWithTag("notification-permission-dialog").assertDoesNotExist()
        val reminderBefore = read { db.agendaDao().listForOwner("ACTIVITY",work).single() }
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER qa_fail_plan BEFORE UPDATE ON activities BEGIN SELECT RAISE(ABORT,'qa_expected_write_failure'); END")
        try {
            plan()
            compose.waitUntil(15_000) { compose.onAllNodesWithText("Trabajo planificado").fetchSemanticsNodes().isEmpty() &&
                compose.onAllNodesWithTag("plan-activity").fetchSemanticsNodes().any { it.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled).not() } }
            compose.waitForIdle()
            assertEquals(ActivityStatus.DRAFT,read { db.activityDao().findWithTargets(work)!!.activity.status })
            assertEquals(reminderBefore,read { db.agendaDao().listForOwner("ACTIVITY",work).single() })
            compose.onNodeWithTag("notification-permission-dialog").assertDoesNotExist()
        } finally { db.openHelper.writableDatabase.execSQL("DROP TRIGGER IF EXISTS qa_fail_plan") }
        plan()
        compose.waitUntil(15_000) { read { db.activityDao().findWithTargets(work)!!.activity.status == ActivityStatus.PLANNED } }
        val denied = androidx.core.content.ContextCompat.checkSelfPermission(context,android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        if (denied) {
            waitFor("notification-permission-dialog")
            compose.activityRule.scenario.recreate()
            waitFor("notification-permission-dialog")
            compose.onNodeWithText("Para avisarte de este trabajo, Android necesita permitir las notificaciones de Mágina Olivo.").assertIsDisplayed()
            compose.onNodeWithTag("notification-permission-later").performClick()
        } else compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        assertEquals(ActivityStatus.PLANNED,read { db.activityDao().findWithTargets(work)!!.activity.status })
        assertEquals(reminderBefore,read { db.agendaDao().listForOwner("ACTIVITY",work).single() })
        // Navigation restores the detail after recreation. Sending its deep link again
        // would rebuild that back stack asynchronously beneath the next click.
        waitFor("edit-activity")
        compose.onNodeWithTag("edit-activity").performScrollTo().performClick()
        waitFor("activity-description")
        compose.onNodeWithTag("activity-description").performScrollTo().performTextReplacement("Aviso editado sin duplicados")
        // The original tap and the original 15-second Room assertion are unchanged. Only the
        // failing path gains a classification, so a lost save names its own cause instead of
        // leaving a bare timeout: nothing is retried, relaxed or hidden here.
        val saveEnabledBeforeClick = compose.onAllNodesWithTag("save-activity").fetchSemanticsNodes()
            .firstOrNull()?.let { !it.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled) }
        // The injected tap lands at the node's centre, and scrolling to the end parks Guardar at
        // the bottom of the viewport. Assert the centre is clear of the keyboard before tapping,
        // so a layout or an inset arriving late that puts it under the IME fails here naming the
        // overlap, instead of as a mute 15-second Room timeout.
        compose.onNodeWithTag("save-activity").performScrollTo()
        assertSaveTapClearOfKeyboard(compose)
        // Read-only, right before the original tap: where the button is and where the scrolling
        // container stands. Compared in the failure report, they tell a gesture taken as a scroll
        // from a tap aimed at a node that has since moved. Nothing here changes the tap.
        val boundsBeforeClick = compose.saveBounds()
        val scrollBeforeClick = compose.verticalScrollOffset()
        compose.onNodeWithTag("save-activity").performClick()
        try {
            compose.waitUntil(15_000) { read { db.activityDao().findWithTargets(work)!!.activity.description == "Aviso editado sin duplicados" } }
        } catch (failure: Throwable) {
            val stored = runCatching { read { db.activityDao().findWithTargets(work)?.activity } }
            failure.addSuppressed(AssertionError(compose.lostSaveReport(
                expectedText = "Aviso editado sin duplicados",
                storedDescription = stored.getOrNull()?.description,
                storedUpdatedAt = stored.getOrNull()?.metadata?.updatedAt?.toString(),
                saveEnabledBeforeClick = saveEnabledBeforeClick,
                boundsBeforeClick = boundsBeforeClick,
                scrollBeforeClick = scrollBeforeClick,
            )))
            throw failure
        }
        val editedReminder = read { db.agendaDao().listForOwner("ACTIVITY",work).single() }
        assertEquals(reminderBefore.id,editedReminder.id)
        assertTrue(editedReminder.enabled); assertNull(editedReminder.firedAt)
        assertEquals(reminderBefore.triggerAt,editedReminder.triggerAt)
        compose.onNodeWithTag("notification-permission-dialog").assertDoesNotExist()
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        compose.onNodeWithTag("notification-permission-dialog").assertDoesNotExist()
        // A previously requested permission must lead the explicit settings action
        // to Android settings, without another runtime dialog or changing Room.
        check(context.getSharedPreferences("notification_permission_history",Context.MODE_PRIVATE).edit()
            .putBoolean("runtime_requested",true).commit())
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        waitFor("bottom-Perfil")
        compose.onNodeWithTag("bottom-Perfil").performClick()
        waitFor("profile-notifications")
        compose.onNodeWithTag("profile-notifications").performScrollTo().performClick()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        compose.waitUntil(15_000) { automation.rootInActiveWindow?.packageName?.toString() == "com.android.settings" }
        automation.executeShellCommand("input keyevent KEYCODE_BACK").close()
        waitFor("profile-notifications")
        compose.onNodeWithTag("notification-permission-dialog").assertDoesNotExist()
        assertEquals(ActivityStatus.PLANNED,read { db.activityDao().findWithTargets(work)!!.activity.status })
        assertEquals(editedReminder,read { db.agendaDao().listForOwner("ACTIVITY",work).single() })
        val manager = context.getSystemService(android.app.NotificationManager::class.java)
        val channel = "notification-access-test-${UUID.randomUUID()}"
        try {
            manager.createNotificationChannel(android.app.NotificationChannel(channel,"QA fixture",android.app.NotificationManager.IMPORTANCE_NONE))
            val settings = com.isivoltpro.maginaolivo.data.reminder.notificationSettingsIntent(context,channel)
            if (!denied && androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                assertEquals(android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS,settings.action)
                assertEquals(channel,settings.getStringExtra(android.provider.Settings.EXTRA_CHANNEL_ID))
            } else {
                assertEquals(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS,settings.action)
                assertNull(settings.getStringExtra(android.provider.Settings.EXTRA_CHANNEL_ID))
            }
            compose.activityRule.scenario.onActivity { it.startActivity(settings) }
            compose.waitUntil(15_000) { automation.rootInActiveWindow?.packageName?.toString() == "com.android.settings" }
            automation.executeShellCommand("input keyevent KEYCODE_BACK").close()
            waitFor("profile-notifications")
            assertEquals(android.app.NotificationManager.IMPORTANCE_NONE,manager.getNotificationChannel(channel).importance)
            assertEquals(editedReminder,read { db.agendaDao().listForOwner("ACTIVITY",work).single() })
        } finally { manager.deleteNotificationChannel(channel) }
    }
    private fun plan() {
        compose.onNodeWithTag("plan-activity").performScrollTo().performClick()
        waitFor("confirm-activity-action")
        compose.onNodeWithTag("confirm-activity-action").performClick()
    }
    // The deep link relaunches MainActivity, and between the old hierarchy going away and the new
    // setContent there is a moment with no Compose hierarchy at all, where fetchSemanticsNodes
    // throws instead of returning nothing. That moment means "not ready yet", not "broken": treat
    // it as a condition still unmet. The same 15-second limit and the same tag decide the outcome,
    // so a screen that never arrives still fails here, by timeout, with nothing relaxed.
    // Room resolves its queries on its own disk-IO threads. When the test blocks on them from the
    // test thread, a Compose recomposition can end up running on one of those threads under the
    // test frame clock, and composing the permission dialog there throws because that thread has
    // no Looper. Reading inside a dedicated thread keeps every Room continuation away from the
    // Compose dispatchers. The queries, the assertions and the waits are the same.
    private fun <T> read(block: suspend () -> T): T {
        var outcome: Result<T>? = null
        val worker = Thread({ outcome = runCatching { runBlocking { block() } } }, "qa-room-read")
        worker.start()
        worker.join()
        return checkNotNull(outcome).getOrThrow()
    }
    private fun waitFor(tag: String) = compose.waitUntil(15_000) {
        runCatching { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
    }
}
