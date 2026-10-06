package com.isivoltpro.maginaolivo

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.isivoltpro.maginaolivo.domain.expense.Expense
import com.isivoltpro.maginaolivo.domain.expense.ExpenseCategory
import com.isivoltpro.maginaolivo.domain.expense.ExpenseOrigin
import com.isivoltpro.maginaolivo.domain.expense.ExpenseStatus
import com.isivoltpro.maginaolivo.domain.expense.Money
import com.isivoltpro.maginaolivo.feature.expenses.ExpensesScreen
import com.isivoltpro.maginaolivo.feature.expenses.ExpensesUiState
import com.isivoltpro.maginaolivo.ui.theme.MaginaOlivoTheme
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** #450: the general Gastos screen never hides a currency nor mixes them in one total or share. */
class ExpensesCurrencyTest {
    @get:Rule val rule = createComposeRule()

    private val today = LocalDate.of(2026, 11, 24)

    @Test fun eurAndUsdAreBothVisibleAndNeverAdded() {
        val rows = listOf(
            spent(80_000, "EUR", ExpenseCategory.PRODUCTS),
            spent(20_000, "EUR", ExpenseCategory.FUEL),
            spent(30_000, "USD", ExpenseCategory.TRANSPORT),
        )
        rule.setContent {
            MaginaOlivoTheme { ExpensesScreen(ExpensesUiState(isLoading = false, expenses = rows), today, {}, {}, { _, _ -> }, {}, {}, {}, {}) }
        }
        val eur = Money.format(100_000, "EUR")
        val usd = Money.format(30_000, "USD")
        // Total and «Este mes»: both currencies, each on its own.
        assertTrue(rule.onAllNodesWithText(eur, substring = true).fetchSemanticsNodes().size >= 2)
        assertTrue(rule.onAllNodesWithText(usd, substring = true).fetchSemanticsNodes().size >= 2)
        rule.onAllNodesWithText(Money.format(130_000, "EUR"), substring = true).fetchSemanticsNodes().let { assertTrue(it.isEmpty()) }
        // Categories: one list per currency, shares within that currency only.
        rule.onNodeWithTag("expenses-categories-EUR").performScrollTo()
        rule.onNodeWithTag("expenses-categories-USD").performScrollTo()
        rule.onNodeWithText("80 %").performScrollTo()
        rule.onNodeWithText("100 %").performScrollTo()
    }

    private fun spent(minor: Long, currency: String, category: ExpenseCategory) = Expense(
        id = UUID.randomUUID(), workspaceId = UUID.randomUUID(), expenseDate = today, concept = category.name,
        category = category, amountMinor = minor, currency = currency, status = ExpenseStatus.POSTED,
        origin = ExpenseOrigin.MANUAL,
    )
}
