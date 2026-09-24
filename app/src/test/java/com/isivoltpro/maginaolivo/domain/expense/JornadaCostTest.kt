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
        assertEquals(16_550L, cost.postedMinor)
        assertEquals(1, cost.draftCount)
        assertEquals(mapOf(ExpenseCategory.FUEL to 4_550L, ExpenseCategory.MACHINERY to 12_000L), cost.summary.byCategory)
    }

    @Test
    fun everyQuickKindIsAnOrdinaryLedgerCategory() {
        assertEquals(ExpenseCategory.FUEL, JornadaExpenseKind.DIESEL.category)
        assertEquals(ExpenseCategory.FUEL, JornadaExpenseKind.PETROL.category)
        assertEquals(ExpenseCategory.LABOR, JornadaExpenseKind.LABOUR.category)
        assertEquals(ExpenseCategory.TRANSPORT, JornadaExpenseKind.TRANSPORT.category)
        assertEquals(7, JornadaExpenseKind.entries.size)
    }

    private fun expense(minor: Long, status: ExpenseStatus, category: ExpenseCategory) = Expense(
        id = UUID.randomUUID(), workspaceId = UUID.randomUUID(), expenseDate = LocalDate.of(2026, 11, 27),
        concept = category.name, category = category, amountMinor = minor, currency = "EUR", status = status,
        origin = ExpenseOrigin.MANUAL, harvestId = jornada,
    )
}
