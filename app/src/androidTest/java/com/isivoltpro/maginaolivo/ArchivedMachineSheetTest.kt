package com.isivoltpro.maginaolivo

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.isivoltpro.maginaolivo.domain.equipment.EquipmentDraftLine
import com.isivoltpro.maginaolivo.domain.equipment.EquipmentLine
import com.isivoltpro.maginaolivo.domain.equipment.EquipmentPriceSnapshot
import com.isivoltpro.maginaolivo.domain.equipment.EquipmentType
import com.isivoltpro.maginaolivo.feature.harvests.EquipmentSheet
import com.isivoltpro.maginaolivo.ui.theme.MaginaOlivoTheme
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** #446: a machine archived since stays part of its day's form, with what it was recorded with. */
class ArchivedMachineSheetTest {
    @get:Rule val rule = createComposeRule()

    @Test fun anArchivedMachineOfTheDayIsKeptWhenSavingTheDay() {
        val machine = UUID.randomUUID()
        val date = LocalDate.parse("2026-11-24")
        val line = EquipmentLine(
            id = UUID.randomUUID(), harvestId = UUID.randomUUID(), type = EquipmentType.TRACTOR, label = "Fendt 209",
            quantity = 1, machineId = machine, version = 1, appliedPrice = EquipmentPriceSnapshot(9_000, "EUR", date),
        )
        var saved: List<EquipmentDraftLine>? = null
        rule.setContent {
            MaginaOlivoTheme {
                EquipmentSheet(
                    current = listOf(line), machines = emptyList(), rates = null, currency = "EUR", currencyError = null,
                    priceDate = date, isSaving = false, onSave = { saved = it }, onCancel = {},
                )
            }
        }
        rule.onNodeWithTag("equipment-machine-archived").performScrollTo()
            .assertTextContains("Fendt 209 · Archivada", substring = true).assertIsSelected()
        rule.onNodeWithTag("equipment-save").performScrollTo().performClick()
        rule.runOnIdle {
            val kept = saved!!.single { it.machineId == machine }
            assertEquals(EquipmentType.TRACTOR, kept.type)
            assertEquals("Fendt 209", kept.label)
            assertEquals(1, kept.quantity)
        }
    }
}
