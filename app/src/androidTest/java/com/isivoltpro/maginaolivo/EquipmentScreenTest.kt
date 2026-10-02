package com.isivoltpro.maginaolivo

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import com.isivoltpro.maginaolivo.domain.equipment.EquipmentDraftLine
import com.isivoltpro.maginaolivo.domain.equipment.EquipmentLine
import com.isivoltpro.maginaolivo.domain.equipment.EquipmentType
import com.isivoltpro.maginaolivo.domain.equipment.EquipmentPriceSnapshot
import com.isivoltpro.maginaolivo.domain.expense.RecollectionRates
import com.isivoltpro.maginaolivo.domain.harvest.Harvest
import com.isivoltpro.maginaolivo.feature.harvests.HarvestDetailScreen
import com.isivoltpro.maginaolivo.feature.harvests.HarvestDetailUiState
import com.isivoltpro.maginaolivo.ui.theme.MaginaOlivoTheme
import java.time.LocalDate
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
            EquipmentLine(UUID.randomUUID(), harvest.id, EquipmentType.SHAKER, null, 1, null, 1,
                EquipmentPriceSnapshot(7_000, "EUR", harvest.harvestDate)),
            EquipmentLine(UUID.randomUUID(), harvest.id, EquipmentType.COMB, null, 1, null, 1,
                EquipmentPriceSnapshot(2_000, "EUR", harvest.harvestDate)),
            EquipmentLine(UUID.randomUUID(), harvest.id, EquipmentType.TRAILER, null, 1, null, 1,
                EquipmentPriceSnapshot(3_000, "EUR", harvest.harvestDate)),
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
        composeRule.onNodeWithTag("jornada-equipment-summary").performScrollTo()
            .assertTextContains("1 vibradora · 1 peine eléctrico · 1 remolque")
        composeRule.onAllNodesWithTag("jornada-equipment-line")[2].performScrollTo()
        capture("cr012-slice3-day-resources.png")
    }

    @Test fun usualUnitPriceIsEditableAndPreviewMultipliesQuantityOnce() {
        var saved: List<EquipmentDraftLine>? = null
        composeRule.setContent {
            MaginaOlivoTheme {
                HarvestDetailScreen(
                    state = HarvestDetailUiState(isLoading = false, harvest = harvest,
                        rates = RecollectionRates(equipmentDayMinor = mapOf(EquipmentType.SHAKER to 7_000))),
                    onUpdate = {}, onDelete = {}, onSaveEquipment = { saved = it },
                )
            }
        }
        composeRule.onNodeWithTag("jornada-edit-equipment").performScrollTo().performClick()
        composeRule.onNodeWithTag("equipment-SHAKER-plus").performClick()
        composeRule.onNodeWithTag("equipment-SHAKER-plus").performClick()
        composeRule.onNodeWithTag("equipment-SHAKER-price").performScrollTo().assertTextContains("70", substring = true)
        composeRule.onNodeWithTag("equipment-SHAKER-total").assertTextContains("140", substring = true)
        composeRule.onNodeWithTag("equipment-SHAKER-price").performTextReplacement("80")
        composeRule.onNodeWithTag("equipment-SHAKER-total").assertTextContains("160", substring = true)
        composeRule.onNodeWithTag("equipment-save").performScrollTo().performClick()
        composeRule.runOnIdle {
            assertEquals(EquipmentPriceSnapshot(8_000, "EUR", harvest.harvestDate), saved!!.single().appliedPrice)
        }
    }

    @Test fun capturesUsualPriceSheet() {
        composeRule.setContent {
            MaginaOlivoTheme {
                HarvestDetailScreen(
                    state = HarvestDetailUiState(isLoading = false, harvest = harvest,
                        rates = RecollectionRates(equipmentDayMinor = mapOf(EquipmentType.SHAKER to 7_000))),
                    onUpdate = {}, onDelete = {},
                )
            }
        }
        composeRule.onNodeWithTag("jornada-edit-equipment").performScrollTo().performClick()
        composeRule.onNodeWithTag("equipment-SHAKER-plus").performClick()
        composeRule.onNodeWithTag("equipment-SHAKER-plus").performClick()
        composeRule.onNodeWithTag("equipment-SHAKER-total").performScrollTo().assertTextContains("140", substring = true)
        capture("cr012-slice3-equipment-sheet.png")
    }

    @Test fun overflowShowsErrorAndDisablesSave() {
        composeRule.setContent {
            MaginaOlivoTheme {
                HarvestDetailScreen(state = HarvestDetailUiState(isLoading = false, harvest = harvest),
                    onUpdate = {}, onDelete = {})
            }
        }
        composeRule.onNodeWithTag("jornada-edit-equipment").performScrollTo().performClick()
        composeRule.onNodeWithTag("equipment-SHAKER-plus").performClick()
        composeRule.onNodeWithTag("equipment-SHAKER-price").performScrollTo().performTextReplacement("999999999999999999999999")
        composeRule.onNodeWithTag("equipment-price-error").assertExists()
        composeRule.onNodeWithTag("equipment-save").assertIsNotEnabled()
    }

    @Test fun multiplicationOverflowShowsErrorAndDisablesSave() {
        composeRule.setContent {
            MaginaOlivoTheme {
                HarvestDetailScreen(state = HarvestDetailUiState(isLoading = false, harvest = harvest),
                    onUpdate = {}, onDelete = {})
            }
        }
        composeRule.onNodeWithTag("jornada-edit-equipment").performScrollTo().performClick()
        composeRule.onNodeWithTag("equipment-SHAKER-plus").performClick()
        composeRule.onNodeWithTag("equipment-SHAKER-plus").performClick()
        composeRule.onNodeWithTag("equipment-SHAKER-price").performScrollTo().performTextReplacement("92233720368547758,07")
        composeRule.onNodeWithTag("equipment-price-error").assertExists()
        composeRule.onNodeWithTag("equipment-save").assertIsNotEnabled()
    }

    @Test fun clearingAnAlreadyConfirmedPriceCannotSilentlyKeepIt() {
        val row = EquipmentLine(UUID.randomUUID(), harvest.id, EquipmentType.SHAKER, null, 1, null, 1,
            EquipmentPriceSnapshot(7_000, "EUR", harvest.harvestDate))
        composeRule.setContent {
            MaginaOlivoTheme {
                HarvestDetailScreen(state = HarvestDetailUiState(isLoading = false, harvest = harvest,
                    equipment = listOf(row)), onUpdate = {}, onDelete = {})
            }
        }
        composeRule.onNodeWithTag("jornada-edit-equipment").performScrollTo().performClick()
        composeRule.onNodeWithTag("equipment-SHAKER-price").performScrollTo().performTextReplacement("")
        composeRule.onNodeWithTag("equipment-price-error").assertExists()
        composeRule.onNodeWithTag("equipment-save").assertIsNotEnabled()
    }

    private fun capture(name: String) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.getExternalFilesDir(null), name)
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue("Emulator evidence PNG is empty", file.length() > 10_000)
    }
}
