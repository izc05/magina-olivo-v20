package com.isivoltpro.maginaolivo

import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.isivoltpro.maginaolivo.feature.notebook.NotebookQuickAction
import com.isivoltpro.maginaolivo.feature.notebook.RegisterTodaySheet
import com.isivoltpro.maginaolivo.navigation.AppDestination
import com.isivoltpro.maginaolivo.navigation.RootDestination
import com.isivoltpro.maginaolivo.ui.theme.MaginaOlivoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** UX-D (Issue #246 §3) — ¿Qué has hecho hoy? with the context visible before saving. */
class RegisterTodaySheetTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun theContextIsShownAndEveryChoiceAnswers() {
        val chosen = mutableListOf<NotebookQuickAction>()
        composeRule.setContent {
            MaginaOlivoTheme {
                RegisterTodaySheet(context = "Finca de ejemplo · Campaña 2026/27", onChoose = { chosen += it }, onCancel = {})
            }
        }
        composeRule.onNodeWithTag("register-today-context").assertTextContains("Finca de ejemplo · Campaña 2026/27")
        NotebookQuickAction.entries.forEach { action ->
            composeRule.onNodeWithTag("register-today-${action.name.lowercase()}").performScrollTo().performClick()
        }
        composeRule.runOnIdle { assertEquals(NotebookQuickAction.entries.toList(), chosen) }
    }

    @Test fun aChosenTypeKeepsTheRegisterRouteUnderCuaderno() {
        assertEquals("register", AppDestination.register(null))
        assertEquals("register?type=IRRIGATION", AppDestination.register("IRRIGATION"))
        assertEquals(RootDestination.Notebook, AppDestination.rootForRoute(AppDestination.RegisterPattern))
    }
}
