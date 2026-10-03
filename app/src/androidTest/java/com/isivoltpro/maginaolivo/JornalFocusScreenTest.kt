package com.isivoltpro.maginaolivo

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.isivoltpro.maginaolivo.domain.harvest.Harvest
import com.isivoltpro.maginaolivo.feature.harvests.HarvestDetailScreen
import com.isivoltpro.maginaolivo.feature.harvests.HarvestDetailUiState
import com.isivoltpro.maginaolivo.ui.theme.MaginaOlivoTheme
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** #365: Cuaderno → Jornal lands on the day's Jornales; the day itself stays one screen. */
class JornalFocusScreenTest {
    @get:Rule val composeRule = createComposeRule()

    private val harvest = Harvest(
        id = UUID.randomUUID(), workspaceId = UUID.randomUUID(), farmId = UUID.randomUUID(), campaignId = UUID.randomUUID(),
        harvestDate = LocalDate.of(2026, 11, 27), totalGrams = 0, shares = emptyList(), collectionMethod = null,
        workerCount = null, machineryText = null, notes = null, version = 1,
    )

    @Test fun fromJornalTheDayOpensOnItsJornales() {
        var registered = 0
        composeRule.setContent {
            MaginaOlivoTheme {
                HarvestDetailScreen(
                    state = HarvestDetailUiState(isLoading = false, harvest = harvest, labourLoaded = true),
                    onUpdate = {}, onDelete = {},
                    labourActions = com.isivoltpro.maginaolivo.feature.harvests.LabourActions(onClear = { registered++ }),
                    initialResource = "labour",
                )
            }
        }
        // «Registrar jornales» is at hand without looking for it under the Pesadas.
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithTag("jornada-register-labour").fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithTag("jornada-no-labour").assertExists()
        composeRule.onNodeWithTag("jornada-register-labour").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(1, registered) }
    }

    @Test fun openedAnyOtherWayTheDayStartsOnItsSummary() {
        composeRule.setContent {
            MaginaOlivoTheme {
                HarvestDetailScreen(state = HarvestDetailUiState(isLoading = false, harvest = harvest, labourLoaded = true), onUpdate = {}, onDelete = {})
            }
        }
        composeRule.waitForIdle()
        assertEquals(0, composeRule.onAllNodesWithTag("jornada-register-labour").fetchSemanticsNodes().size)
        composeRule.onNodeWithTag("day-resource-labour").assertExists()
    }
}
