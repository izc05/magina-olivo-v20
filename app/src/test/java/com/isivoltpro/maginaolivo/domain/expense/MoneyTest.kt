package com.isivoltpro.maginaolivo.domain.expense

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyTest {
    @Test
    fun readsWhatAFarmerTypes() {
        assertEquals(6_500L, Money.parseMinor("65"))
        assertEquals(6_550L, Money.parseMinor("65,5"))
        assertEquals(6_550L, Money.parseMinor("65,50"))
        assertEquals(6_550L, Money.parseMinor("65.50"))
        assertEquals(123_456L, Money.parseMinor("1.234,56 €"))
        assertEquals(123_456L, Money.parseMinor("1 234,56"))
        assertEquals(123_400L, Money.parseMinor("1.234"))
        assertEquals(123_456L, Money.parseMinor("1,234.56"))
        assertEquals(1_000L, Money.parseMinor("10 EUR"))
    }

    @Test
    fun anUnreadableAmountIsNullNeverZero() {
        listOf(null, "", "  ", "abc", "12,345,6", "-5", "1,2,3", "65,555").forEach { text ->
            assertNull(text, Money.parseMinor(text))
        }
    }

    @Test
    fun editableTextRoundTrips() {
        assertEquals("65", Money.editable(6_500))
        assertEquals("65,05", Money.editable(6_505))
        assertEquals("", Money.editable(null))
        assertEquals(6_505L, Money.parseMinor(Money.editable(6_505)))
    }

    @Test
    fun onlyPostedMoneyIsSummed() {
        val base = Expense(
            id = java.util.UUID.randomUUID(),
            workspaceId = java.util.UUID.randomUUID(),
            expenseDate = java.time.LocalDate.parse("2026-03-10"),
            concept = "Abono",
            category = ExpenseCategory.PRODUCTS,
            amountMinor = 5_000,
            currency = "EUR",
            status = ExpenseStatus.POSTED,
            origin = ExpenseOrigin.MANUAL,
        )
        val summary = ExpenseSummary.of(
            listOf(
                base,
                base.copy(id = java.util.UUID.randomUUID(), amountMinor = 2_000, category = ExpenseCategory.FUEL),
                base.copy(id = java.util.UUID.randomUUID(), amountMinor = 99_999, status = ExpenseStatus.DRAFT),
            ),
        )
        assertEquals(7_000L, summary.totalMinor)
        assertEquals(mapOf(ExpenseCategory.PRODUCTS to 5_000L, ExpenseCategory.FUEL to 2_000L), summary.byCategory)
        assertEquals(2, summary.postedCount)
        assertEquals(1, summary.draftCount)
    }
}
