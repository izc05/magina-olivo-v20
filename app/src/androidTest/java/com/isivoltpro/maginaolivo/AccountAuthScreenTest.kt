package com.isivoltpro.maginaolivo

import android.content.Context
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.isivoltpro.maginaolivo.app.AppCompositionRoot
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.domain.farm.NewFarm
import com.isivoltpro.maginaolivo.feature.account.*
import com.isivoltpro.maginaolivo.app.AppearanceMode
import com.isivoltpro.maginaolivo.ui.theme.MaginaOlivoTheme
import java.io.File
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runners.model.Statement

/** Production repo/route with explicit simulated Auth, synthetic isolated Room and photo. */
class AccountAuthScreenTest {
    private val compose=createAndroidComposeRule<ComponentActivity>()
    private val context=ApplicationProvider.getApplicationContext<Context>()
    private val guard=TestRule { base,_ -> object:Statement() {
        override fun evaluate() {
            assertEquals("com.isivoltpro.maginaolivo.dev",context.packageName)
            android.os.ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("getprop ro.kernel.qemu"))
                .bufferedReader().use { assertEquals("1",it.readText().trim()) }
            base.evaluate()
        }
    } }
    @get:Rule val rules:TestRule=RuleChain.outerRule(guard).around(IsolatedAppStateRule()).around(compose)
    private val photo=File(context.noBackupFilesDir,"qa-account-photo-${UUID.randomUUID()}.bin")
    @After fun removeOwnPhoto() { check(photo.name.startsWith("qa-account-photo-"));photo.delete() }
    private inner class Api:AccountAuthApi {
        override val simulated=true
        override val redirectUri="com.isivoltpro.maginaolivo.dev://auth/callback"
        var calls=0
        private fun session(email:String="qa@example.com")=AuthSession(UUID.nameUUIDFromBytes(email.toByteArray()),email,"synthetic-access","synthetic-refresh",Instant.now().plusSeconds(3600))
        override suspend fun signIn(email:String,password:String):AuthSession { calls++;if(password=="incorrecta")throw AuthFailure(AuthFailure.Kind.CREDENTIALS);return session(email) }
        override suspend fun signUp(email:String,password:String,challenge:String):AuthSession? { calls++;return null }
        override suspend fun recover(email:String,challenge:String) { calls++ }
        override suspend fun exchange(code:String,verifier:String)=session()
        override suspend fun refresh(session:AuthSession)=session
        override suspend fun user(session:AuthSession)=session.userId to session.email
        override suspend fun changePassword(session:AuthSession,password:String) { calls++ }
        override suspend fun signOut(session:AuthSession) { calls++ }
    }
    @Test fun loginOfflineRotationAndLogoutKeepLocalFarmOwnerOutboxAndPhoto() = runBlocking {
        val root=AppCompositionRoot.createAndroid(context,"DEV");val local=checkNotNull(root.localPersistence)
        val workspace=(local.workspaceRepository.ensureLocalWorkspace() as AppResult.Success).value
        val farm=(local.farmRepository.create(NewFarm(workspace,"Synthetic retained farm")) as AppResult.Success).value
        photo.writeBytes(byteArrayOf(4,2,1,9));val before=fingerprint(local.database)
        val store=MemoryAccountSessionStore();val repo=AccountRepository(Api(),store);val online=MutableStateFlow(true)
        fun content() { compose.setContent { MaginaOlivoTheme(AppearanceMode.DARK) { Box(Modifier.fillMaxSize().systemBarsPadding()) { AccountRoute(repo,{},connectivity=online) } } } }
        content();waitFor("account-email")
        compose.onNodeWithTag("account-simulation").assertIsDisplayed()
        compose.onNodeWithTag("account-email").performScrollTo().performTextReplacement("qa@example.com")
        compose.onNodeWithTag("account-password").performScrollTo().performTextReplacement("password42")
        compose.onNodeWithTag("account-login").performScrollTo().performClick()
        waitFor("account-logout");assertNotNull(store.read().session);capture("signed-in")
        online.value=false
        compose.waitUntil(15_000) { repo.state.value.status==AccountStatus.OFFLINE }
        compose.activityRule.scenario.recreate();content();waitFor("account-logout");capture("offline-restored")
        compose.onNodeWithTag("account-logout").performScrollTo().performClick();waitFor("account-login")
        assertNull(store.read().session);assertEquals(before,fingerprint(local.database));assertArrayEquals(byteArrayOf(4,2,1,9),photo.readBytes())
        assertNotNull(local.database.farmDao().findById(farm));capture("logged-out")
    }
    @Test fun registrationRecoveryAndErrorsAreHonestAndDoNotDuplicateSession() {
        val api=Api();val store=MemoryAccountSessionStore();val repo=AccountRepository(api,store);val online=MutableStateFlow(true)
        compose.setContent { MaginaOlivoTheme(AppearanceMode.LIGHT) { Box(Modifier.fillMaxSize().systemBarsPadding()) { AccountRoute(repo,{},connectivity=online) } } }
        waitFor("account-email")
        compose.onNodeWithTag("account-email").performScrollTo().performTextReplacement("qa@example.com")
        compose.onNodeWithTag("account-password").performScrollTo().performTextReplacement("incorrecta")
        compose.onNodeWithTag("account-login").performScrollTo().performClick()
        compose.waitUntil(15_000) { repo.state.value.error!=null && !repo.state.value.busy };assertNull(store.read().session);assertEquals(1,api.calls)
        compose.onNodeWithTag("account-mode-register").performScrollTo().performClick()
        compose.onNodeWithTag("account-password").performScrollTo().performTextReplacement("password42")
        compose.onNodeWithTag("account-register").performScrollTo().performClick()
        compose.waitUntil(15_000) { store.read().pending?.purpose==LinkPurpose.SIGNUP }
        assertNull(store.read().session);capture("confirm-pending")
        compose.onNodeWithTag("account-mode-recover").performScrollTo().performClick()
        compose.onNodeWithTag("account-recover").performScrollTo().performClick()
        compose.waitUntil(15_000) { store.read().pending?.purpose==LinkPurpose.RECOVERY }
        runBlocking { repo.completeLink("${api.redirectUri}?code=synthetic-code",true) }
        waitFor("account-new-password")
        compose.onNodeWithTag("account-new-password").performScrollTo().performTextReplacement("New-password42")
        compose.onNodeWithTag("account-save-password").performScrollTo().performClick()
        compose.waitUntil(15_000) { store.read().recovery==null && repo.state.value.message?.contains("actualizada")==true }
        assertNull(store.read().session);capture("password-updated")
    }
    @Test fun unavailableProviderDoesNotPretendToRegister() {
        val repo=AccountRepository(null)
        compose.setContent { MaginaOlivoTheme { AccountRoute(repo,{}) } }
        waitFor("account-login");compose.onNodeWithTag("account-login").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("account-status").performScrollTo().assertTextContains("Configuración de pruebas pendiente")
    }
    private fun fingerprint(db:com.isivoltpro.maginaolivo.data.local.MaginaOlivoDatabase):String {
        val sqlite=db.openHelper.readableDatabase
        val tables=mutableListOf<String>();sqlite.query("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name").use { while(it.moveToNext())tables+=it.getString(0) }
        return tables.joinToString("\n") { table ->
            sqlite.query("SELECT * FROM \"$table\"").use { cursor ->
                val rows=mutableListOf<String>();while(cursor.moveToNext())rows+= (0 until cursor.columnCount).joinToString("|") { index -> if(cursor.isNull(index)) "<NULL>" else when(cursor.getType(index)) { android.database.Cursor.FIELD_TYPE_BLOB -> java.util.Base64.getEncoder().encodeToString(cursor.getBlob(index));else -> cursor.getString(index) } }
                "$table:${rows.sorted()}"
            }
        }
    }
    private fun waitFor(tag:String)=compose.waitUntil(15_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    private fun capture(stage:String) {
        compose.waitForIdle()
        val config=context.resources.configuration;val out=File(context.getExternalFilesDir(null),"account-auth-evidence/$stage-sw${config.smallestScreenWidthDp}dp-font${config.fontScale}.png")
        out.parentFile!!.mkdirs();InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()!!.let { bitmap -> out.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)) } }
    }
}
