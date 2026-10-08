package com.isivoltpro.maginaolivo.domain.expense

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyTest {
    @Test fun isoPrecisionRoundTripsAndRejectsExcessDigits() {
        assertEquals(1234L, Money.parseMinor("1234", "JPY"))
        assertEquals(1234L, Money.parseMinor("1,234", "KWD"))
        assertEquals("1234", Money.editable(1234, "JPY"))
        assertEquals("1,234", Money.editable(1234, "KWD"))
        assertNull(Money.parseMinor("1,2", "JPY"))
        assertNull(Money.parseMinor("1,2345", "KWD"))
        assertEquals(Long.MAX_VALUE, Money.parseMinor(Money.editable(Long.MAX_VALUE)))
        assertEquals(Long.MAX_VALUE, Money.parseMinor(Money.editable(Long.MAX_VALUE, "KWD"), "KWD"))
        assertNull(Money.parseMinor("9223372036854775,808", "KWD"))
        assertNull(Money.parseMinor("5", "XXX"))
    }
    @Test fun formatsHistoricalCurrenciesWithoutLosingMinorUnits() {
        assertEquals("1.234", Money.format(1234, "JPY").substringBefore(' '))
        assertEquals("1,234", Money.format(1234, "KWD").substringBefore(' '))
        assertEquals("92.233.720.368.547.758,07", Money.format(Long.MAX_VALUE).substringBefore(' '))
    }

    @Test fun unsupportedHistoricalCurrenciesExposeOriginalCodeWithoutGuessingAnAmount() {
        listOf("EURO", "XXX").forEach { code ->
            assertEquals("Importe no disponible ($code)", Money.format(6_500, code))
            assertEquals("", Money.editable(6_500, code))
            assertNull(Money.parseMinor("65", code))
        }
    }

    @Test fun overflowingInputIsRejectedInsteadOfThrowing() {
        assertNull(Money.parseMinor("92233720368547758,08"))
    }
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
            "EUR",
        )
        assertEquals(7_000L, summary.totalMinor)
        assertEquals(mapOf(ExpenseCategory.PRODUCTS to 5_000L, ExpenseCategory.FUEL to 2_000L), summary.byCategory)
        assertEquals(2, summary.postedCount)
        assertEquals(1, summary.draftCount)
    }

    /** #500: a ledger never turns an overflow into negative money; the total is simply unknown. */
    @Test
    fun anOverflowingSummaryIsUnknownNeverNegative() {
        val base = Expense(
            id = java.util.UUID.randomUUID(),
            workspaceId = java.util.UUID.randomUUID(),
            expenseDate = java.time.LocalDate.parse("2026-03-10"),
            concept = "Importado",
            category = ExpenseCategory.PRODUCTS,
            amountMinor = Long.MAX_VALUE - 10,
            currency = "EUR",
            status = ExpenseStatus.POSTED,
            origin = ExpenseOrigin.MANUAL,
        )
        val summary = ExpenseSummary.of(
            listOf(
                base,
                base.copy(id = java.util.UUID.randomUUID(), amountMinor = 100),
                base.copy(id = java.util.UUID.randomUUID(), amountMinor = 2_000, category = ExpenseCategory.FUEL),
            ),
            "EUR",
        )
        assertNull(summary.totalMinor)
        assertNull(summary.byCategory.getValue(ExpenseCategory.PRODUCTS))
        assertEquals(2_000L, summary.byCategory.getValue(ExpenseCategory.FUEL))
        assertEquals(3, summary.postedCount)
    }
}
