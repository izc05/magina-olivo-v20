package com.isivoltpro.maginaolivo

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
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
        composeRule.onNodeWithTag("jornada-add-cost").performScrollTo().performClick()
        composeRule.onNodeWithTag("cost-save").assertIsNotEnabled()
        composeRule.onNodeWithTag("cost-kind-RENTAL").performClick()
        composeRule.onNodeWithTag("cost-amount").performTextInput("120")
        composeRule.onNodeWithTag("cost-save").performScrollTo().performClick()
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
        composeRule.onNodeWithTag("jornada-cost-total").assertTextContains("165,50", substring = true)
        composeRule.onNodeWithTag("jornada-cost-total").assertTextContains("1 borrador sin contar", substring = true)
    }

    private fun expense(minor: Long, status: ExpenseStatus, concept: String) = Expense(
        id = UUID.randomUUID(), workspaceId = harvest.workspaceId, expenseDate = harvest.harvestDate, concept = concept,
        category = ExpenseCategory.FUEL, amountMinor = minor, currency = "EUR", status = status, origin = ExpenseOrigin.MANUAL,
        harvestId = harvest.id,
    )
}
