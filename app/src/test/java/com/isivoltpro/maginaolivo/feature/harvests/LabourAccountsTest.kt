package com.isivoltpro.maginaolivo.feature.harvests

import com.isivoltpro.maginaolivo.domain.expense.*
import com.isivoltpro.maginaolivo.domain.labour.*
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class LabourAccountsTest {
    private val campaign = UUID.randomUUID()
    private val worker = UUID.randomUUID()
    private val day = UUID.randomUUID()
    private val date = LocalDate.of(2026, 10, 1)
    private val line = LabourEntry(UUID.randomUUID(), day, worker, "Juan García López", 1, LabourUnit.FULL_DAY, null, 1, LabourRateSnapshot(24000, "EUR", date, LabourRateBasis.DAY))
    private val expense = Expense(UUID.randomUUID(), UUID.randomUUID(), date, "Jornales", ExpenseCategory.LABOR, 24000, "EUR", ExpenseStatus.POSTED, ExpenseOrigin.DAY_LABOUR, campaignId = campaign, harvestId = day)
    private fun payment(amount: Long) = LabourPayment(UUID.randomUUID(), worker, campaign, date, amount, "EUR")

    @Test fun personUsesReconciledLedgerAndSeparatePayments() {
        val account = labourAccounts(campaign, listOf(line), listOf(expense), listOf(payment(10000), payment(8000))).single()
        assertEquals(24000L, account.balances.single().generatedMinor)
        assertEquals(18000L, account.balances.single().paidMinor)
        assertEquals(6000L, account.balances.single().pendingMinor)
        assertEquals(LabourPaymentState.PARTIAL, account.balances.single().state)
        assertFalse(account.unconfirmed)
    }
    @Test fun missingSnapshotAndDuplicateLedgersNeverInventAZeroBalance() {
        assertTrue(labourAccounts(campaign, listOf(line.copy(appliedRate = null)), listOf(expense), emptyList()).single().unconfirmed)
        val duplicate = labourAccounts(campaign, listOf(line), listOf(expense, expense.copy(id = UUID.randomUUID())), emptyList()).single()
        assertTrue(duplicate.unconfirmed)
        assertTrue(duplicate.balances.isEmpty())
    }
    @Test fun historicalCurrenciesAreSeparateAndAnonymousRowsStayUnassigned() {
        val kwdDay = UUID.randomUUID()
        val kwd = line.copy(id = UUID.randomUUID(), harvestId = kwdDay, appliedRate = line.appliedRate!!.copy(currency = "KWD", unitPriceMinor = 1234))
        val accounts = labourAccounts(campaign, listOf(line, kwd, line.copy(id = UUID.randomUUID(), workerId = null, workerName = null)), listOf(expense.copy(amountMinor = 48000), expense.copy(id = UUID.randomUUID(), harvestId = kwdDay, currency = "KWD", amountMinor = 1234)), emptyList())
        assertEquals(1, accounts.size)
        assertEquals(setOf("EUR", "KWD"), accounts.single().balances.map { it.currency }.toSet())
        assertEquals(24000L, accounts.single().balances.first { it.currency == "EUR" }.generatedMinor)
    }
}
