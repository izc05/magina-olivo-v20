package com.isivoltpro.maginaolivo

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun devLauncherShowsPhase3DesignGallery() {
        composeRule.onNodeWithTag("phase3-gallery").assertIsDisplayed()
        composeRule.onNodeWithText("DEV · Galería de diseño").assertIsDisplayed()
        composeRule.onNodeWithText("Buenos días").assertIsDisplayed()
    }

    @Test
    fun bottomBarOpensReferenceRoots() {
        composeRule.onNodeWithContentDescription("Mi Olivar").performClick()
        composeRule.onNodeWithText("Tus fincas").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Registrar").performClick()
        composeRule.onNodeWithText("¿Qué quieres registrar?").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Calendario").performClick()
        composeRule.onNodeWithText("Septiembre 2026 · datos de demostración").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Perfil").performClick()
        composeRule.onNodeWithText("Preferencias de la aplicación").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Inicio").performClick()
        composeRule.onNodeWithText("Buenos días").assertIsDisplayed()
    }
}
