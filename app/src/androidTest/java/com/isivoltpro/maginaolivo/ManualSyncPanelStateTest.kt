package com.isivoltpro.maginaolivo

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.isivoltpro.maginaolivo.feature.sync.ManualSync
import com.isivoltpro.maginaolivo.feature.sync.ManualSyncPanel
import com.isivoltpro.maginaolivo.feature.sync.ManualSyncState
import com.isivoltpro.maginaolivo.ui.theme.MaginaOlivoTheme
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ManualSyncPanelStateTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val mutable = MutableStateFlow<ManualSyncState>(ManualSyncState.Idle)
    private var requests = 0
    private val sync = object : ManualSync {
        override val state: StateFlow<ManualSyncState> = mutable
        override fun requestSync() { requests++ }
    }

    private fun show(online: Boolean = true) {
        compose.activityRule.scenario.onActivity { it.setContent {
            MaginaOlivoTheme { ManualSyncPanel(sync, online) }
        } }
    }

    @Test fun idleAndOfflineExplainWhyNoServerCopyIsKnown() {
        show(false)
        compose.onNodeWithTag("manual-sync-idle").assertIsDisplayed()
        compose.onNodeWithTag("manual-sync-now").assertIsNotEnabled()
        compose.onNodeWithTag("manual-sync-scope").assertTextContains("no es una copia de seguridad", substring = true)
        assertEquals(0, requests)
    }

    @Test fun completedPendingAndFailureUseOnlyConfirmedRunState() {
        show()
        val at = Instant.parse("2026-10-20T10:00:00Z")
        compose.runOnUiThread { mutable.value = ManualSyncState.Completed(at, 2, 1) }
        compose.onNodeWithTag("manual-sync-status").assertTextContains("2 enviados, 1 recibidos", substring = true)
        compose.onNodeWithTag("manual-sync-now").assertIsEnabled()
        compose.onNodeWithTag("manual-sync-now").performClick()
        compose.waitUntil(5_000) { requests == 1 }
        compose.runOnUiThread { mutable.value = ManualSyncState.Pending(at, 1, 0, listOf("Falta consentimiento")) }
        compose.onNodeWithTag("manual-sync-reason").assertTextContains("Falta consentimiento", substring = true)
        compose.runOnUiThread { mutable.value = ManualSyncState.Failed(at, "Sin conexión con el servidor") }
        compose.onNodeWithTag("manual-sync-error").assertTextContains("Sin conexión con el servidor")
    }
}
