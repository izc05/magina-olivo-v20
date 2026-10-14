package com.isivoltpro.maginaolivo

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.isivoltpro.maginaolivo.app.AndroidOnboardingStateStore
import com.isivoltpro.maginaolivo.feature.account.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runners.model.Statement

/** Real exported callback/Activity; only the Auth transport is replaced under guarded QA. */
class AccountAuthMainActivityTest {
    private val compose=createAndroidComposeRule<MainActivity>()
    private val context=ApplicationProvider.getApplicationContext<Context>()
    private val delegate=SimulatedAccountAuthApi()
    private val gate=CompletableDeferred<Unit>()
    @Volatile private var exchanging=false
    private val api=object:AccountAuthApi by delegate {
        override suspend fun exchange(code:String,verifier:String):AuthSession { exchanging=true;gate.await();return delegate.exchange(code,verifier) }
    }
    private val store=MemoryAccountSessionStore()
    private val repository=AccountRepository(api,store)
    private val guard=TestRule { base,_ -> object:Statement() {
        override fun evaluate() {
            assertEquals("com.isivoltpro.maginaolivo.dev",context.packageName)
            assertTrue("Only unconfigured DEV QA may replace the process Auth owner",BuildConfig.DEBUG && BuildConfig.AUTH_PROJECT_URL.isEmpty())
            android.os.ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("getprop ro.kernel.qemu"))
                .bufferedReader().use { assertEquals("1",it.readText().trim()) }
            base.evaluate()
        }
    } }
    private val fixture=TestRule { base,_ -> object:Statement() {
        override fun evaluate() {
            // Privileged instrumentation, no production override API. Restore the exact owner.
            val field=AccountRuntime::class.java.getDeclaredField("repository").apply { isAccessible=true }
            val original=field.get(AccountRuntime)
            field.set(AccountRuntime,repository)
            check(context.getSharedPreferences(AndroidOnboardingStateStore.PREFERENCES_NAME,Context.MODE_PRIVATE).edit()
                .putBoolean(AndroidOnboardingStateStore.KEY_COMPLETED,true).commit())
            try { base.evaluate() } finally { gate.complete(Unit);field.set(AccountRuntime,original) }
        }
    } }
    @get:Rule val rules:TestRule=RuleChain.outerRule(guard).around(IsolatedAppStateRule()).around(fixture).around(compose)
    @Test fun callbackExchangeSurvivesActivityRecreation() {
        runBlocking { repository.requestRecovery("synthetic@example.test",true) }
        compose.activityRule.scenario.onActivity {
            it.startActivity(Intent(it,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .setData(Uri.parse("${api.redirectUri}?code=synthetic-code")))
        }
        compose.waitUntil(15_000) { exchanging && store.read().pending==null }
        compose.activityRule.scenario.recreate()
        gate.complete(Unit)
        compose.waitUntil(15_000) { repository.state.value.status==AccountStatus.RECOVERY }
        compose.onNodeWithTag("account-new-password").performScrollTo().assertIsDisplayed()
        assertNotNull(store.read().recovery);assertNull(store.read().session)
        compose.activityRule.scenario.onActivity { assertNull("Callback code not retained in Activity intent",it.intent.data) }
    }
    @Test fun accountIsNestedUnderProfileAndBackKeepsNavigation() {
        waitFor("bottom-Perfil");compose.onNodeWithTag("bottom-Perfil").performClick()
        waitFor("profile-account");compose.onNodeWithTag("profile-account").performScrollTo().performClick()
        waitFor("account-root");compose.onNodeWithTag("account-simulation").assertIsDisplayed()
        compose.onNodeWithTag("account-back").performScrollTo().performClick()
        waitFor("profile-root");assertNull(store.read().session)
    }
    private fun waitFor(tag:String)=compose.waitUntil(15_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
}
