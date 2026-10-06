package com.isivoltpro.maginaolivo.domain.expense

import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Test

class JornadaCostTest {
    private val jornada = UUID.randomUUID()

    @Test
    fun onlyPostedMoneyCountsAndDraftsAreNamed() {
        val cost = JornadaCost.of(
            listOf(
                expense(4_550, ExpenseStatus.POSTED, ExpenseCategory.FUEL),
                expense(12_000, ExpenseStatus.POSTED, ExpenseCategory.MACHINERY),
                expense(9_999, ExpenseStatus.DRAFT, ExpenseCategory.OTHER),
            ),
        )
        assertEquals(16_550L, cost.byCurrency.single().amount())
        assertEquals(1, cost.draftCount)
        assertEquals(
            mapOf(ExpenseCategory.FUEL to 4_550L, ExpenseCategory.MACHINERY to 12_000L),
            cost.byCurrency.single().posted.groupBy { it.category }.mapValues { (_, rows) -> rows.sumOf { it.amountMinor } },
        )
    }

    /** #450: a day's cost in another currency is shown apart, never hidden nor added to the euros. */
    @Test
    fun aDayWithTwoCurrenciesHasTwoTotals() {
        val usd = expense(3_000, ExpenseStatus.POSTED, ExpenseCategory.TRANSPORT).copy(currency = "USD")
        val cost = JornadaCost.of(listOf(expense(4_550, ExpenseStatus.POSTED, ExpenseCategory.FUEL), usd))
        assertEquals(listOf("EUR" to 4_550L, "USD" to 3_000L), cost.byCurrency.map { it.currency to it.amount() })
        assertEquals(listOf("USD" to 3_000L), JornadaCost.of(listOf(usd)).byCurrency.map { it.currency to it.amount() })
        // No single total is made up across currencies.
        assertEquals(null, cost.postedMinor)
    }

    @Test
    fun everyQuickKindIsAnOrdinaryLedgerCategory() {
        assertEquals(ExpenseCategory.FUEL, JornadaExpenseKind.DIESEL.category)
        assertEquals(ExpenseCategory.FUEL, JornadaExpenseKind.PETROL.category)
        assertEquals(ExpenseCategory.LABOR, JornadaExpenseKind.LABOUR.category)
        assertEquals(ExpenseCategory.TRANSPORT, JornadaExpenseKind.TRANSPORT.category)
        assertEquals(ExpenseCategory.REPAIR, JornadaExpenseKind.REPAIR.category)
        assertEquals(8, JornadaExpenseKind.entries.size)
    }

    private fun expense(minor: Long, status: ExpenseStatus, category: ExpenseCategory) = Expense(
        id = UUID.randomUUID(), workspaceId = UUID.randomUUID(), expenseDate = LocalDate.of(2026, 11, 27),
        concept = category.name, category = category, amountMinor = minor, currency = "EUR", status = status,
        origin = ExpenseOrigin.MANUAL, harvestId = jornada,
    )
}
