package com.isivoltpro.maginaolivo

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.isivoltpro.maginaolivo.domain.expense.Expense
import com.isivoltpro.maginaolivo.domain.expense.ExpenseCategory
import com.isivoltpro.maginaolivo.domain.expense.ExpenseOrigin
import com.isivoltpro.maginaolivo.domain.expense.ExpenseStatus
import com.isivoltpro.maginaolivo.domain.expense.JornadaExpenseKind
import com.isivoltpro.maginaolivo.domain.harvest.Harvest
import com.isivoltpro.maginaolivo.feature.harvests.HarvestDetailScreen
import com.isivoltpro.maginaolivo.feature.harvests.HarvestDetailUiState
import com.isivoltpro.maginaolivo.ui.theme.MaginaOlivoTheme
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Phase 19F — a recollection cost in three taps, read back from the ledger. */
class JornadaCostScreenTest {
    @get:Rule val composeRule = createComposeRule()

    private val harvest = Harvest(
        id = UUID.randomUUID(), workspaceId = UUID.randomUUID(), farmId = UUID.randomUUID(), campaignId = UUID.randomUUID(),
        harvestDate = LocalDate.of(2026, 11, 27), totalGrams = 1_000_000, shares = emptyList(), collectionMethod = null,
        workerCount = null, machineryText = null, notes = null, version = 1,
    )

    @Test fun kindAmountSave() {
        var saved: Triple<JornadaExpenseKind, Long, Boolean>? = null
        composeRule.setContent {
            MaginaOlivoTheme {
                HarvestDetailScreen(
                    state = HarvestDetailUiState(isLoading = false, harvest = harvest),
                    onUpdate = {},
                    onDelete = {},
                    onAddCost = { kind, minor, _, photo -> saved = Triple(kind, minor, photo) },
                )
            }
        }
        composeRule.onNodeWithTag("day-resource-other").performScrollTo().performClick()
        composeRule.onNodeWithTag("jornada-add-cost").performScrollTo().performClick()
        composeRule.onNodeWithTag("cost-save").assertIsNotEnabled()
        // The sheet can still be settling: bring each control into view and let the choice land
        // before the next step, so a tap never falls outside the sheet.
        composeRule.onNodeWithTag("cost-kind-RENTAL").performScrollTo().performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasTestTag("cost-kind-RENTAL") and isSelected()).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("cost-amount").performScrollTo().performTextInput("120")
        composeRule.onNodeWithTag("cost-save").performScrollTo()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasTestTag("cost-save") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("cost-save").performClick()
        composeRule.waitUntil(5_000) { saved != null }
        composeRule.runOnIdle { assertEquals(Triple(JornadaExpenseKind.RENTAL, 12_000L, false), saved) }
    }

    @Test fun theTotalIsThePostedLedgerRowsOnly() {
        val costs = listOf(
            expense(4_550, ExpenseStatus.POSTED, "Gasoil"),
            expense(12_000, ExpenseStatus.POSTED, "Alquiler vibradora"),
            expense(9_999, ExpenseStatus.DRAFT, "Tique por revisar"),
        )
        composeRule.setContent {
            MaginaOlivoTheme {
                HarvestDetailScreen(state = HarvestDetailUiState(isLoading = false, harvest = harvest, costs = costs), onUpdate = {}, onDelete = {})
            }
        }
        composeRule.onNodeWithTag("day-resource-other").performScrollTo().performClick()
        composeRule.onNodeWithTag("jornada-cost-total").assertTextContains("165,50", substring = true)
        composeRule.onNodeWithTag("jornada-cost-total").assertTextContains("1 borrador sin contar", substring = true)
    }

    @Test fun historicalDetailKeepsUnsupportedCodeAndOverflowUnavailable() {
        val costs = listOf(expense(Long.MAX_VALUE, ExpenseStatus.POSTED, "Gasoil"),
            expense(1, ExpenseStatus.POSTED, "Transporte"),
            expense(123, ExpenseStatus.POSTED, "Histórico").copy(currency = "INVALID"))
        composeRule.setContent { MaginaOlivoTheme {
            HarvestDetailScreen(HarvestDetailUiState(isLoading = false, harvest = harvest, costs = costs), {}, {})
        } }
        composeRule.onNodeWithTag("day-resource-other").performScrollTo().performClick()
        composeRule.onNodeWithTag("jornada-cost-total").assertTextContains("Importe no disponible (EUR)", substring = true)
            .assertTextContains("Importe no disponible (INVALID)", substring = true)
        composeRule.onAllNodesWithTag("jornada-cost").assertCountEquals(3)
    }

    @Test fun failedCostReadHidesStaleRowsAndCollisionMutations() {
        val manual = expense(1000, ExpenseStatus.POSTED, "Jornales")
        val calculated = expense(2000, ExpenseStatus.DRAFT, "Calculado").copy(origin = ExpenseOrigin.DAY_LABOUR)
        composeRule.setContent { MaginaOlivoTheme {
            HarvestDetailScreen(HarvestDetailUiState(isLoading = false, harvest = harvest,
                costs = listOf(manual, calculated), unlinkedCosts = listOf(manual.copy(harvestId = null)),
                costsLoaded = true, costsReadFailed = true), {}, {})
        } }
        composeRule.onNodeWithTag("day-resource-other").performScrollTo().performClick()
        composeRule.onNodeWithTag("jornada-costs-read-error").assertExists()
        composeRule.onNodeWithTag("jornada-cost-total").assertDoesNotExist()
        composeRule.onAllNodesWithTag("jornada-cost").assertCountEquals(0)
        composeRule.onNodeWithTag("jornada-prefer-calculated").assertDoesNotExist()
        composeRule.onNodeWithTag("jornada-link-cost").assertDoesNotExist()
    }

    private fun expense(minor: Long, status: ExpenseStatus, concept: String) = Expense(
        id = UUID.randomUUID(), workspaceId = harvest.workspaceId, expenseDate = harvest.harvestDate, concept = concept,
        category = ExpenseCategory.FUEL, amountMinor = minor, currency = "EUR", status = status, origin = ExpenseOrigin.MANUAL,
        harvestId = harvest.id,
    )
}
