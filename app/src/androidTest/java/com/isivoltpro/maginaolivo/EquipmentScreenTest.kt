package com.isivoltpro.maginaolivo

import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.isivoltpro.maginaolivo.domain.equipment.EquipmentDraftLine
import com.isivoltpro.maginaolivo.domain.equipment.EquipmentLine
import com.isivoltpro.maginaolivo.domain.equipment.EquipmentType
import com.isivoltpro.maginaolivo.domain.harvest.Harvest
import com.isivoltpro.maginaolivo.feature.harvests.HarvestDetailScreen
import com.isivoltpro.maginaolivo.feature.harvests.HarvestDetailUiState
import com.isivoltpro.maginaolivo.ui.theme.MaginaOlivoTheme
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Phase 19E — `2 vibradoras + 1 peine + 1 tractor` in a few taps, no Machine needed. */
class EquipmentScreenTest {
    @get:Rule val composeRule = createComposeRule()

    private val harvest = Harvest(
        id = UUID.randomUUID(), workspaceId = UUID.randomUUID(), farmId = UUID.randomUUID(), campaignId = UUID.randomUUID(),
        harvestDate = LocalDate.of(2026, 11, 26), totalGrams = 1_000_000, shares = emptyList(), collectionMethod = null,
        workerCount = null, machineryText = null, notes = null, version = 1,
    )

    @Test fun steppersBuildTheDaysEquipment() {
        var saved: List<EquipmentDraftLine>? = null
        composeRule.setContent {
            MaginaOlivoTheme {
                HarvestDetailScreen(
                    state = HarvestDetailUiState(isLoading = false, harvest = harvest),
                    onUpdate = {},
                    onDelete = {},
                    onSaveEquipment = { saved = it },
                )
            }
        }
        composeRule.onNodeWithTag("jornada-no-equipment").assertExists()
        composeRule.onNodeWithTag("jornada-edit-equipment").performScrollTo().performClick()
        composeRule.onNodeWithTag("equipment-SHAKER-plus").performClick()
        composeRule.onNodeWithTag("equipment-SHAKER-plus").performClick()
        composeRule.onNodeWithTag("equipment-SHAKER-value").assertTextContains("2")
        composeRule.onNodeWithTag("equipment-COMB-plus").performClick()
        composeRule.onNodeWithTag("equipment-TRACTOR-plus").performClick()
        composeRule.onNodeWithTag("equipment-save").performScrollTo().performClick()
        composeRule.runOnIdle {
            assertEquals(
                setOf(
                    EquipmentDraftLine(EquipmentType.SHAKER, 2),
                    EquipmentDraftLine(EquipmentType.COMB, 1),
                    EquipmentDraftLine(EquipmentType.TRACTOR, 1),
                ),
                saved!!.toSet(),
            )
        }
    }

    @Test fun recordedEquipmentReadsAsOneLine() {
        val lines = listOf(
            EquipmentLine(UUID.randomUUID(), harvest.id, EquipmentType.SHAKER, null, 2, null, 1),
            EquipmentLine(UUID.randomUUID(), harvest.id, EquipmentType.COMB, null, 1, null, 1),
            EquipmentLine(UUID.randomUUID(), harvest.id, EquipmentType.TRACTOR, null, 1, null, 1),
        )
        composeRule.setContent {
            MaginaOlivoTheme {
                HarvestDetailScreen(
                    state = HarvestDetailUiState(isLoading = false, harvest = harvest, equipment = lines),
                    onUpdate = {},
                    onDelete = {},
                )
            }
        }
        composeRule.onNodeWithTag("jornada-equipment-summary").assertTextContains("1 tractor · 2 vibradoras · 1 peine eléctrico")
    }
}
