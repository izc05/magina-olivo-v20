package com.isivoltpro.maginaolivo

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.isivoltpro.maginaolivo.domain.agenda.ReminderPreferences
import com.isivoltpro.maginaolivo.domain.organization.Organization
import com.isivoltpro.maginaolivo.domain.organization.OrganizationRole
import com.isivoltpro.maginaolivo.domain.profile.ProfileSettings
import com.isivoltpro.maginaolivo.feature.profile.MyProfileSection
import com.isivoltpro.maginaolivo.feature.profile.MyProfileUiState
import com.isivoltpro.maginaolivo.feature.profile.ReminderSettings
import com.isivoltpro.maginaolivo.ui.theme.MaginaOlivoTheme
import java.time.LocalTime
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Phase 21A — «Mi perfil» in Perfil: municipality and cooperative, each in a short sheet. */
class MyProfileScreenTest {
    @get:Rule val composeRule = createComposeRule()

    private val santaIsabel = Organization(UUID.randomUUID(), "Coop. Santa Isabel", setOf(OrganizationRole.COOPERATIVE))
    private val molino = Organization(UUID.randomUUID(), "Almazara El Molino", setOf(OrganizationRole.MILL))

    @Test fun anEmptyProfileSaysWhatEachRowIsFor() {
        show(MyProfileUiState(isLoading = false))
        composeRule.onNodeWithTag("profile-municipality").assertTextContains("Sin indicar", substring = true)
        composeRule.onNodeWithTag("profile-cooperative").assertTextContains("Sin elegir", substring = true)
    }

    @Test fun theMunicipalityIsTypedAndSaved() {
        val saved = mutableListOf<Pair<String, String>>()
        show(MyProfileUiState(isLoading = false), onSaveLocation = { m, p -> saved += m to p })
        composeRule.onNodeWithTag("profile-municipality").performClick()
        composeRule.onNodeWithTag("profile-municipality-field").performTextInput("Bedmar")
        composeRule.onNodeWithTag("profile-province-field").performTextInput("Jaén")
        composeRule.onNodeWithTag("profile-save-location").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(listOf("Bedmar" to "Jaén"), saved) }
    }

    @Test fun theCooperativeIsChosenFromTheFarmersOwnListOrAdded() {
        val chosen = mutableListOf<UUID?>()
        val created = mutableListOf<String>()
        show(
            MyProfileUiState(
                isLoading = false,
                settings = ProfileSettings("Bedmar", "Jaén", santaIsabel),
                cooperatives = listOf(santaIsabel, molino),
            ),
            onChooseCooperative = { chosen += it },
            onCreateCooperative = { created += it },
        )
        composeRule.onNodeWithTag("profile-municipality").assertTextContains("Bedmar · Jaén", substring = true)
        composeRule.onNodeWithTag("profile-cooperative").assertTextContains("Coop. Santa Isabel", substring = true)

        composeRule.onNodeWithTag("profile-cooperative").performClick()
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithTag("profile-cooperative-option").fetchSemanticsNodes().size == 2 }
        composeRule.onAllNodesWithTag("profile-cooperative-option")[1].performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(listOf<UUID?>(molino.id), chosen) }

        composeRule.onNodeWithTag("profile-cooperative-none").performScrollTo().performClick()
        composeRule.onNodeWithTag("profile-new-cooperative").performScrollTo().performTextInput("S.C.A. San Isidro")
        // «Añadir y elegir» is enabled once the name lands; tapping it earlier is lost on a slow emulator.
        composeRule.onNodeWithTag("profile-create-cooperative").performScrollTo()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasTestTag("profile-create-cooperative") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("profile-create-cooperative").performClick()
        composeRule.waitUntil(5_000) { created.isNotEmpty() }
        composeRule.runOnIdle {
            assertEquals(listOf<UUID?>(molino.id, null), chosen)
            assertEquals(listOf("S.C.A. San Isidro"), created)
        }
    }

    @Test fun anEnabledReminderPreferenceDoesNotGuaranteeSoundFromAndroid() {
        composeRule.setContent {
            MaginaOlivoTheme {
                ReminderSettings(ReminderPreferences(enabled = true), isSaving = false, notificationsOn = true, onNotifications = {}) { }
            }
        }
        composeRule.onNodeWithText("Suenan en este teléfono.").assertDoesNotExist()
    }

    @Test fun androidBlockingNotificationsDoesNotTurnOffTheStoredReminderPreference() {
        var openedSettings = false
        val changes = mutableListOf<ReminderPreferences>()
        composeRule.setContent {
            MaginaOlivoTheme {
                ReminderSettings(
                    ReminderPreferences(enabled = true), isSaving = false,
                    notificationsOn = false, onNotifications = { openedSettings = true },
                ) { changes += it }
            }
        }
        composeRule.onNodeWithTag("profile-reminders-switch").assertIsOn()
        composeRule.onNodeWithText("Android bloquea los avisos en este teléfono.").assertExists()
        composeRule.onNodeWithTag("profile-reminders-notifications").performClick()
        composeRule.runOnIdle {
            assertEquals(true, openedSettings)
            assertEquals(emptyList<ReminderPreferences>(), changes)
        }
    }

    @Test fun aDisabledReminderPreferenceDoesNotOfferAnAndroidPermissionFix() {
        composeRule.setContent {
            MaginaOlivoTheme {
                ReminderSettings(ReminderPreferences(enabled = false), isSaving = false, notificationsOn = false, onNotifications = {}) { }
            }
        }
        composeRule.onNodeWithTag("profile-reminders-notifications").assertDoesNotExist()
        composeRule.onNodeWithText("Android bloquea los avisos en este teléfono.").assertDoesNotExist()
    }

    /** Phase 21B: one switch for every reminder and the day-before hour, 08:00 by default. */
    @Test fun remindersCanBeSwitchedOffAndTheDayBeforeHourChosen() {
        val changes = mutableListOf<ReminderPreferences>()
        var preferences by mutableStateOf(ReminderPreferences())
        composeRule.setContent {
            MaginaOlivoTheme {
                ReminderSettings(preferences, isSaving = false, notificationsOn = true, onNotifications = {}) { changes += it; preferences = it }
            }
        }
        composeRule.onNodeWithText("08:00").assertIsSelected()
        composeRule.onNodeWithText("19:00").performClick()
        composeRule.onNodeWithTag("profile-reminders-switch").performClick()
        composeRule.runOnIdle {
            assertEquals(
                listOf(
                    ReminderPreferences(enabled = true, previousDayTime = LocalTime.of(19, 0)),
                    ReminderPreferences(enabled = false, previousDayTime = LocalTime.of(19, 0)),
                ),
                changes,
            )
        }
        // Switched off, no hour is offered.
        composeRule.onAllNodesWithTag("profile-reminder-hour").assertCountEquals(0)
    }

    private fun show(
        state: MyProfileUiState,
        onSaveLocation: (String, String) -> Unit = { _, _ -> },
        onChooseCooperative: (UUID?) -> Unit = {},
        onCreateCooperative: (String) -> Unit = {},
    ) {
        composeRule.setContent {
            MaginaOlivoTheme {
                Column {
                    MyProfileSection(
                        state = state,
                        onSaveLocation = onSaveLocation,
                        onChooseCooperative = onChooseCooperative,
                        onCreateCooperative = onCreateCooperative,
                        onClearError = {},
                    )
                }
            }
        }
    }
}
