package com.isivoltpro.maginaolivo

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.isivoltpro.maginaolivo.domain.harvest.Harvest
import com.isivoltpro.maginaolivo.domain.labour.LabourEntry
import com.isivoltpro.maginaolivo.domain.labour.LabourUnit
import com.isivoltpro.maginaolivo.domain.labour.Worker
import com.isivoltpro.maginaolivo.feature.harvests.HarvestDetailScreen
import com.isivoltpro.maginaolivo.feature.harvests.HarvestDetailUiState
import com.isivoltpro.maginaolivo.feature.harvests.LabourActions
import com.isivoltpro.maginaolivo.ui.theme.MaginaOlivoTheme
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Phase 19D — five people in a few taps: repeat yesterday's crew, add one, save once. */
class LabourScreenTest {
    @get:Rule val composeRule = createComposeRule()

    private val harvest = Harvest(
        id = UUID.randomUUID(), workspaceId = UUID.randomUUID(), farmId = UUID.randomUUID(), campaignId = UUID.randomUUID(),
        harvestDate = LocalDate.of(2026, 11, 25), totalGrams = 1_000_000, shares = emptyList(), collectionMethod = null,
        workerCount = null, machineryText = null, notes = null, version = 1,
    )
    private val workers = listOf("Antonio", "Paco", "Mari", "Juan", "El Rubio").map { Worker(UUID.randomUUID(), it) }

    @Test fun repeatingYesterdaysCrewPlusOneSavesFiveJornalesAtOnce() {
        var saved: Pair<List<UUID>, LabourUnit>? = null
        composeRule.setContent {
            MaginaOlivoTheme {
                HarvestDetailScreen(
                    state = HarvestDetailUiState(
                        isLoading = false,
                        harvest = harvest,
                        workers = workers,
                        previousCrew = workers.take(4).map { it.id },
                    ),
                    onUpdate = {},
                    onDelete = {},
                    labourActions = LabourActions(onSaveCrew = { ids, unit, _ -> saved = ids to unit }),
                )
            }
        }
        composeRule.onNodeWithTag("jornada-no-labour").assertExists()
        composeRule.onNodeWithTag("jornada-register-labour").performScrollTo().performClick()
        composeRule.onNodeWithTag("labour-save").assertIsNotEnabled()
        composeRule.onNodeWithTag("labour-repeat-crew").performClick()
        composeRule.onAllNodesWithTag("labour-worker")[4].performClick()
        composeRule.onNodeWithTag("labour-selected-count").assertTextContains("5 seleccionadas")
        composeRule.onNodeWithTag("labour-save").performScrollTo().assertIsEnabled().assertTextContains("Guardar 5 jornales").performClick()
        composeRule.runOnIdle {
            assertEquals(workers.map { it.id }.toSet(), saved!!.first.toSet())
            assertEquals(LabourUnit.FULL_DAY, saved!!.second)
        }
    }

    @Test fun recordedJornalesShowTheirDeterministicSummary() {
        val lines = listOf(
            LabourEntry(UUID.randomUUID(), harvest.id, workers[0].id, "Antonio", 1, LabourUnit.FULL_DAY, null, 1),
            LabourEntry(UUID.randomUUID(), harvest.id, null, null, 3, LabourUnit.HALF_DAY, null, 1),
        )
        composeRule.setContent {
            MaginaOlivoTheme {
                HarvestDetailScreen(
                    state = HarvestDetailUiState(isLoading = false, harvest = harvest, labour = lines),
                    onUpdate = {},
                    onDelete = {},
                )
            }
        }
        composeRule.onNodeWithTag("jornada-labour-summary").assertTextContains("4 personas · 1 jornada · 3 medias")
    }
}
