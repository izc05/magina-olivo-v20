package com.isivoltpro.maginaolivo

import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.isivoltpro.maginaolivo.domain.labour.Worker
import com.isivoltpro.maginaolivo.feature.harvests.JornadaLabour
import com.isivoltpro.maginaolivo.feature.harvests.LabourSheet
import com.isivoltpro.maginaolivo.ui.theme.MaginaOlivoTheme
import java.util.UUID
import org.junit.Rule
import org.junit.Test

/** Owner's device check on build 680 (CR-011): jornales form and day summary. */
class LabourDeviceCheckTest {
    @get:Rule val composeRule = createComposeRule()

    private val workers = listOf("Antonio", "Paco", "Mari").map { Worker(UUID.randomUUID(), it) }

    /** The people ticked but not yet saved are still ticked after a rotation. */
    @Test fun selectedPersonSurvivesARotation() {
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent {
            MaginaOlivoTheme {
                LabourSheet(
                    workers = workers,
                    alreadyRecorded = emptySet(),
                    harvestId = UUID.fromString("00000000-0000-0000-0000-000000000001"),
                    campaignId = UUID.fromString("00000000-0000-0000-0000-000000000002"),
                    date = java.time.LocalDate.of(2026, 10, 1),
                    rates = null,
                    isSaving = false,
                    error = null,
                    onSaveCrew = {},

                    onAddWorker = {},
                    onCancel = {},
                )
            }
        }
        composeRule.onAllNodesWithTag("labour-worker")[0].performScrollTo().performClick()
        composeRule.onAllNodesWithTag("labour-worker")[2].performScrollTo().performClick()
        composeRule.onNodeWithTag("labour-selected-count").assertTextContains("1 seleccionada")

        restoration.emulateSavedInstanceStateRestore()

        composeRule.onNodeWithTag("labour-selected-count").assertTextContains("1 seleccionada")
    }

    /** While the day's jornales are still being read, the screen never claims there are none. */
    @Test fun aDayStillLoadingDoesNotSayItHasNoJornales() {
        composeRule.setContent {
            MaginaOlivoTheme {
                JornadaLabour(
                    labour = emptyList(),
                    editable = true,
                    message = null,
                    error = null,
                    onRegister = {},
                    onRemove = {},
                    loaded = false,
                )
            }
        }
        composeRule.onNodeWithTag("jornada-labour-loading").assertExists()
        composeRule.onNodeWithTag("jornada-no-labour").assertDoesNotExist()
    }

    /** Codex #304: a failed read is an error, never «Sin jornales anotados». */
    @Test fun aFailedReadIsShownAsAnErrorNotAsNoJornales() {
        composeRule.setContent {
            MaginaOlivoTheme {
                JornadaLabour(
                    labour = emptyList(),
                    editable = true,
                    message = null,
                    error = null,
                    onRegister = {},
                    onRemove = {},
                    loaded = true,
                    readFailed = true,
                )
            }
        }
        composeRule.onNodeWithTag("jornada-labour-read-error").assertExists()
        composeRule.onNodeWithTag("jornada-no-labour").assertDoesNotExist()
    }
}
