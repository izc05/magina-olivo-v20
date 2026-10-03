package com.isivoltpro.maginaolivo.domain.expense

import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class RecollectionLedgerTest {
    private val campaign = UUID.randomUUID()
    private fun expense(category: ExpenseCategory, amount: Long = 5000, currency: String = "EUR", status: ExpenseStatus = ExpenseStatus.POSTED) =
        Expense(UUID.randomUUID(), UUID.randomUUID(), LocalDate.of(2026, 10, 2), category.name,
            category, amount, currency, status, ExpenseOrigin.MANUAL, campaignId = campaign)

    @Test fun postedOtherDoesNotConfirmDraftLabourOrEquipmentAsZero() {
        val ledger = RecollectionLedger.of(campaign, listOf(expense(ExpenseCategory.FUEL),
            expense(ExpenseCategory.LABOR, status = ExpenseStatus.DRAFT),
            expense(ExpenseCategory.MACHINERY, status = ExpenseStatus.DRAFT)), emptyList()).single()
        assertEquals(5000L, ledger.amount())
        assertNull(ledger.amount(RecollectionBucket.LABOUR))
        assertNull(ledger.amount(RecollectionBucket.EQUIPMENT))
    }

    @Test fun postedZeroIsAvailableButOtherCurrencyDoesNotSupplyMissingBuckets() {
        val ledger = RecollectionLedger.of(campaign, listOf(expense(ExpenseCategory.LABOR, 0),
            expense(ExpenseCategory.MACHINERY, 0), expense(ExpenseCategory.FUEL, 1000, "JPY")), emptyList())
        assertEquals(0L, ledger.first { it.currency == "EUR" }.amount(RecollectionBucket.LABOUR))
        assertEquals(0L, ledger.first { it.currency == "EUR" }.amount(RecollectionBucket.EQUIPMENT))
        assertNull(ledger.first { it.currency == "JPY" }.amount(RecollectionBucket.LABOUR))
        assertNull(ledger.first { it.currency == "EUR" }.amount(RecollectionBucket.OTHER))
    }

    @Test fun bucketOverflowIsUnavailableWithoutHidingAnotherCurrency() {
        val ledger = RecollectionLedger.of(campaign, listOf(expense(ExpenseCategory.FUEL, Long.MAX_VALUE),
            expense(ExpenseCategory.FUEL, 1), expense(ExpenseCategory.FUEL, 1000, "JPY")), emptyList())
        assertNull(ledger.first { it.currency == "EUR" }.amount(RecollectionBucket.OTHER))
        assertEquals(1000L, ledger.first { it.currency == "JPY" }.amount(RecollectionBucket.OTHER))
    }
}
