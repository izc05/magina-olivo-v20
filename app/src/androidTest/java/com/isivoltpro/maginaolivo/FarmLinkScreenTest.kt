package com.isivoltpro.maginaolivo

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.isivoltpro.maginaolivo.data.local.MaginaOlivoDatabase
import com.isivoltpro.maginaolivo.data.repository.*
import com.isivoltpro.maginaolivo.feature.account.*
import com.isivoltpro.maginaolivo.feature.accountlink.*
import com.isivoltpro.maginaolivo.ui.theme.MaginaOlivoTheme
import com.isivoltpro.maginaolivo.ui.theme.MoSurfaceTokens
import com.isivoltpro.maginaolivo.ui.components.MoTertiaryButton
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class FarmLinkScreenTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val context=ApplicationProvider.getApplicationContext<Context>()
    private val fixture=UUID.randomUUID()
    private val name="qa-link-screen-$fixture.db"
    private val database=MaginaOlivoDatabase.create(context,name)
    private val file=File(context.noBackupFilesDir,"qa-link-screen-$fixture.json")
    private val ledger=AndroidFarmLinkLedger(file)
    private val session=MemoryAccountSessionStore()
    private val signOutEntered=CompletableDeferred<Unit>()
    private val finishSignOut=CompletableDeferred<Unit>()
    private var slowSignOut=false
    @Volatile private var revoked=false
    private val api=object:AccountAuthApi by SimulatedAccountAuthApi() {
        override val simulated=false
        override suspend fun signOut(session:AuthSession) { if(slowSignOut) { signOutEntered.complete(Unit);finishSignOut.await();revoked=true } }
    }
    private val account=AccountRepository(api,session)
    private val repository=LocalFarmLinkRepository(context,database,account,"fixture-project",ledger)
    private val ids=List(3){UUID.randomUUID()}
    @After fun cleanup() {
        runCatching { ledger.read()?.consents?.forEach { File(context.noBackupFilesDir,"account-link-backups/${it.backupId}").deleteRecursively() } }
        database.close();context.deleteDatabase(name)
        listOf(file,File(file.path+".bak"),File(file.path+".new")).forEach { it.delete() }
    }
    private suspend fun seed() {
        assertEquals("com.isivoltpro.maginaolivo.dev",context.packageName)
        account.login("a@example.test","password42",true)
        val db=database.openHelper.writableDatabase;val workspace=UUID.randomUUID().toString()
        db.execSQL("INSERT INTO workspaces (id,name,owner_user_id,country_code,timezone,locale,currency,created_at,updated_at,version,sync_status) VALUES (?,'QA','local-owner','ES','Europe/Madrid','es-ES','EUR',1,1,1,'LOCAL_ONLY')",arrayOf(workspace))
        ids.forEachIndexed { i,id -> db.execSQL("INSERT INTO farms (id,workspace_id,name,status,created_at,updated_at,version,sync_status) VALUES (?, ?,?,'ACTIVE',1,1,1,'LOCAL_ONLY')",arrayOf(id.toString(),workspace,"Finca ${i+1}")) }
    }
    private fun content() {
        compose.runOnUiThread { compose.activity.setContent { MaginaOlivoTheme {
            FarmLinkAccessHost(repository,account) {
                val state by account.state.collectAsState()
                val model:AccountViewModel=viewModel(factory=viewModelFactory { initializer { AccountViewModel(account) } })
                Column(Modifier.fillMaxSize().background(MoSurfaceTokens.appBackground).statusBarsPadding().verticalScroll(rememberScrollState())) {
                    Text("Datos agrícolas de prueba",Modifier.testTag("private-agriculture"))
                    state.userId?.let { FarmLinkPanel(repository,it) }
                    MoTertiaryButton("Cerrar sesión",{model.logout(true)},Modifier.testTag("test-link-logout"))
                }
            }
        } } }
    }
    private fun waitFor(tag:String) = compose.waitUntil(20_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    private fun rotate(orientation:Int,configuration:Int) {
        val previous=compose.activity
        compose.activityRule.scenario.onActivity { it.requestedOrientation=orientation }
        compose.waitUntil(20_000) {
            var rotated=false
            compose.activityRule.scenario.onActivity {
                rotated=it!==previous && it.resources.configuration.orientation==configuration
            }
            rotated
        }
        // ComponentActivity is the test host: reattach the real access host after Android recreates it.
        content()
    }
    private fun capture(label:String) {
        compose.waitForIdle()
        val folder=File(context.getExternalFilesDir(null),"g1b-consent-qa").apply { mkdirs() }
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().let { bitmap ->
            File(folder,"$label.png").outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)) };bitmap.recycle()
        }
    }
    @Test fun onlySelectedFarmsArePreparedButConsentProtectsTheEntireLocalDataset()=runBlocking {
        seed();content();waitFor("farm-link-choice-${ids[0]}")
        compose.onNodeWithTag("farm-link-choice-${ids[0]}").performScrollTo().performClick()
        compose.onNodeWithTag("farm-link-confirm").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("farm-link-cancel").performScrollTo().performClick();assertNull(ledger.read())
        ids.take(2).forEach { compose.onNodeWithTag("farm-link-choice-$it").performScrollTo().performClick() }
        compose.onNodeWithText("Las fincas no seleccionadas también quedarán protegidas en este móvil. Todas se abrirán únicamente con esta cuenta. Si cierras sesión, tendrás que entrar con ella para volver a verlas. Con la sesión guardada podrás seguir trabajando sin conexión.")
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Acepto asociar las fincas seleccionadas y proteger todas las fincas de este móvil con esta cuenta.")
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("farm-link-consent").performScrollTo().performClick().assertIsOn();capture("selected")
        compose.onNodeWithTag("farm-link-confirm").performScrollTo().performClick();waitFor("farm-link-pending")
        assertEquals(ids.take(2).toSet(),ledger.read()!!.farmIds)
        assertFalse(ids[2] in ledger.read()!!.farmIds)
        assertEquals(2,ledger.read()!!.consents.single().textVersion)
        compose.onNodeWithTag("farm-link-protection-scope").performScrollTo()
            .assertTextContains("incluidas las no seleccionadas",substring=true).assertIsDisplayed()
        compose.onNodeWithTag("farm-link-pending").performScrollTo();capture("pending")
    }
    @Test fun logoutSwitchAndRecreatedActivityCannotShowPreviousAgriculture()=runBlocking {
        seed();repository.consent(account.state.value.userId!!,repository.choices().take(2),true)
        content();waitFor("private-agriculture")
        account.logout(false);waitFor("farm-link-locked")
        compose.onNodeWithTag("private-agriculture").assertDoesNotExist()
        compose.onNodeWithTag("farm-link-choice-${ids[2]}").assertDoesNotExist()
        account.login("b@example.test","password42",true);waitFor("farm-link-locked")
        compose.onNodeWithTag("farm-link-choice-${ids[2]}").assertDoesNotExist()
        compose.onNodeWithTag("private-agriculture").assertDoesNotExist();capture("other-account-locked")
        compose.activityRule.scenario.recreate();content();waitFor("farm-link-locked")
        compose.onNodeWithTag("private-agriculture").assertDoesNotExist()
        account.logout(false);account.login("a@example.test","password42",true);account.resume(false)
        waitFor("private-agriculture");compose.onNodeWithTag("farm-link-locked").assertDoesNotExist();capture("restored-offline")
    }
    @Test fun remoteLogoutSurvivesDestructionOfItsViewModel()=runBlocking {
        seed();repository.consent(account.state.value.userId!!,repository.choices().take(1),true)
        slowSignOut=true;content();waitFor("test-link-logout")
        compose.onNodeWithTag("test-link-logout").performScrollTo().performClick()
        waitFor("farm-link-locked")
        withTimeout(10_000) { signOutEntered.await() }
        finishSignOut.complete(Unit)
        compose.waitUntil(10_000) { revoked }
        assertNull(session.read().session)
    }
    @Test fun realRotationPreservesProtectionAcrossLogoutOtherAccountAndRestoredOwner()=runBlocking {
        seed();repository.consent(account.state.value.userId!!,repository.choices().take(1),true)
        // Establish a known starting orientation before observing actual Android recreations.
        compose.activityRule.scenario.onActivity { it.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        compose.waitUntil(20_000) {
            var portrait=false
            compose.activityRule.scenario.onActivity { portrait=it.resources.configuration.orientation==Configuration.ORIENTATION_PORTRAIT }
            portrait
        }
        content();waitFor("private-agriculture")
        rotate(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,Configuration.ORIENTATION_LANDSCAPE)
        waitFor("private-agriculture");compose.onNodeWithTag("farm-link-locked").assertDoesNotExist()
        account.logout(false);waitFor("farm-link-locked")
        rotate(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,Configuration.ORIENTATION_PORTRAIT)
        waitFor("farm-link-locked");compose.onNodeWithTag("private-agriculture").assertDoesNotExist()
        account.login("b@example.test","password42",true);waitFor("farm-link-locked")
        rotate(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,Configuration.ORIENTATION_LANDSCAPE)
        waitFor("farm-link-locked");compose.onNodeWithTag("private-agriculture").assertDoesNotExist()
        compose.onNodeWithTag("farm-link-choice-${ids[2]}").assertDoesNotExist()
        compose.activityRule.scenario.recreate();content();waitFor("farm-link-locked")
        compose.onNodeWithTag("private-agriculture").assertDoesNotExist();capture("rotation-other-account-locked")
        account.logout(false);account.login("a@example.test","password42",true);account.resume(false)
        waitFor("private-agriculture")
        rotate(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,Configuration.ORIENTATION_PORTRAIT)
        waitFor("private-agriculture");compose.onNodeWithTag("farm-link-locked").assertDoesNotExist()
        assertEquals(ids.take(1).toSet(),ledger.read()!!.farmIds)
        capture("rotation-restored-owner")
    }
}
