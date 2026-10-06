package com.isivoltpro.maginaolivo.domain.labour

import com.isivoltpro.maginaolivo.data.local.model.CampaignStatus
import com.isivoltpro.maginaolivo.domain.expense.Expense
import com.isivoltpro.maginaolivo.domain.expense.ExpenseCategory
import com.isivoltpro.maginaolivo.domain.expense.ExpenseOrigin
import com.isivoltpro.maginaolivo.domain.expense.ExpenseStatus
import com.isivoltpro.maginaolivo.domain.expense.ExpenseSummary
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class LabourSettlementTest {
    private val worker = UUID.randomUUID()
    private val campaign = UUID.randomUUID()
    private val workspace = UUID.randomUUID()
    private val date = LocalDate.of(2026, 10, 1)
    private val rows = List(4) { pricedEntry(date.plusDays(it.toLong())) }
    private val expenses = rows.map { expense(it.harvestId, 6_000) }
    private val costs get() = expenses.flatMap { LabourLedgerAllocation.of(it, rows)!! }

    @Test fun zeroPaymentsLeaves240EurosPending() {
        val balance = balance()
        assertEquals(24_000L, balance.generatedMinor)
        assertEquals(0L, balance.paidMinor)
        assertEquals(24_000L, balance.pendingMinor)
        assertEquals(LabourPaymentState.PENDING, balance.state)
    }

    @Test fun aSingleSmallerPaymentIsPartial() {
        val balance = balance(listOf(payment(10_000)))
        assertEquals(10_000L, balance.paidMinor)
        assertEquals(14_000L, balance.pendingMinor)
        assertEquals(LabourPaymentState.PARTIAL, balance.state)
    }

    @Test fun severalPartialMovementsSumWithoutLosingTheirIdentity() {
        val first = payment(10_000, LocalDate.of(2026, 10, 5))
        val second = payment(8_000, LocalDate.of(2026, 10, 12))
        val movements = listOf(first, second)
        val balance = balance(movements)
        assertEquals(24_000L, balance.generatedMinor)
        assertEquals(18_000L, balance.paidMinor)
        assertEquals(6_000L, balance.pendingMinor)
        assertEquals(LabourPaymentState.PARTIAL, balance.state)
        assertNotEquals(first.id, second.id)
        assertEquals(LocalDate.of(2026, 10, 5), movements.first().paymentDate)
        assertEquals(LocalDate.of(2026, 10, 12), movements.last().paymentDate)
    }

    @Test fun exactFinalPaymentSettlesDebtWithoutAnotherExpense() {
        val movements = listOf(payment(10_000), payment(8_000))
        val final = payment(6_000)
        assertNull(LabourPaymentRules.validate(final, balance(movements), CampaignStatus.ACTIVE))
        val settled = balance(movements + final)
        assertEquals(24_000L, settled.paidMinor)
        assertEquals(0L, settled.pendingMinor)
        assertEquals(LabourPaymentState.PAID, settled.state)
        assertEquals(24_000L, ExpenseSummary.of(expenses, "EUR").totalMinor)
        assertEquals(4, expenses.size)
    }

    @Test fun overpaymentIsRejectedWithTheActualPendingBalance() {
        val balance = balance(listOf(payment(18_000)))
        assertEquals(6_000L, balance.pendingMinor)
        assertEquals("exceeds_pending", LabourPaymentRules.validate(payment(8_000), balance, CampaignStatus.ACTIVE)!!.code)
        assertNull(LabourPaymentRules.validate(payment(6_000), balance, CampaignStatus.ACTIVE))
        assertThrows(IllegalArgumentException::class.java) { balance(listOf(payment(24_001))) }
    }

    @Test fun closedCampaignAllowsLateSettlementButNeverCostEditing() {
        val balance = balance(listOf(payment(18_000)))
        assertNull(LabourPaymentRules.validate(payment(6_000, date.plusMonths(1)), balance, CampaignStatus.CLOSED))
        assertEquals("campaign_closed", LabourPaymentRules.validateGeneratedChange(balance, 24_000, CampaignStatus.CLOSED)!!.code)
        assertEquals(0L, balance(listOf(payment(18_000), payment(6_000, date.plusMonths(1)))).pendingMinor)
    }

    @Test fun costReductionBelowAlreadyPaidMustBeCorrectedBeforeEditing() {
        val balance = balance(listOf(payment(18_000)))
        assertEquals("below_paid", LabourPaymentRules.validateGeneratedChange(balance, 17_999, CampaignStatus.ACTIVE)!!.code)
        assertNull(LabourPaymentRules.validateGeneratedChange(balance, 18_000, CampaignStatus.ACTIVE))
        assertNull(LabourPaymentRules.validateGeneratedChange(balance, 25_000, CampaignStatus.HARVEST))
        assertEquals("not_active", LabourPaymentRules.validateGeneratedChange(balance, 25_000, CampaignStatus.PREPARATION)!!.code)
        assertEquals("not_positive", LabourPaymentRules.validateGeneratedChange(balance, -1, CampaignStatus.ACTIVE)!!.code)
    }

    @Test fun paymentValidationRejectsZeroNegativeAndWrongContext() {
        val balance = balance()
        assertEquals("not_positive", LabourPaymentRules.validate(payment(0), balance, CampaignStatus.ACTIVE)!!.code)
        assertEquals("not_positive", LabourPaymentRules.validate(payment(-100), balance, CampaignStatus.ACTIVE)!!.code)
        assertEquals("worker_mismatch", LabourPaymentRules.validate(payment(100).copy(workerId = UUID.randomUUID()), balance, CampaignStatus.ACTIVE)!!.code)
        assertEquals("campaign_mismatch", LabourPaymentRules.validate(payment(100).copy(campaignId = UUID.randomUUID()), balance, CampaignStatus.ACTIVE)!!.code)
        assertEquals("currency_mismatch", LabourPaymentRules.validate(payment(100).copy(currency = "USD"), balance, CampaignStatus.ACTIVE)!!.code)
        assertEquals("not_active", LabourPaymentRules.validate(payment(100), balance, CampaignStatus.PREPARATION)!!.code)
        assertNull(LabourPaymentRules.validate(payment(100), balance, CampaignStatus.HARVEST))
    }

    @Test fun debtsAndPaymentsAreScopedByWorkerCampaignAndCurrency() {
        val otherWorker = pricedEntry(date).copy(workerId = UUID.randomUUID())
        val otherWorkerCost = LabourLedgerAllocation.of(expense(otherWorker.harvestId, 6_000), listOf(otherWorker))!!
        val otherCampaignCost = LabourLedgerAllocation.of(expenses[0].copy(id = UUID.randomUUID(), campaignId = UUID.randomUUID()), listOf(rows[0]))!!
        val movements = listOf(
            payment(100).copy(workerId = otherWorker.workerId!!),
            payment(200).copy(campaignId = UUID.randomUUID()),
            payment(300).copy(currency = "USD"),
            payment(8_000),
        )
        val balance = LabourSettlement.of(worker, campaign, "EUR", costs + otherWorkerCost + otherCampaignCost, movements)
        assertEquals(24_000L, balance.generatedMinor)
        assertEquals(8_000L, balance.paidMinor)
        assertEquals(16_000L, balance.pendingMinor)
    }

    @Test fun repeatedMovementOrAllocationIdsCannotDoubleTheMoney() {
        val same = payment(100)
        assertThrows(IllegalArgumentException::class.java) { balance(listOf(same, same)) }
        assertThrows(IllegalArgumentException::class.java) {
            LabourSettlement.of(worker, campaign, "EUR", costs + costs.first(), emptyList())
        }
    }

    @Test fun invalidPersistedPaymentCannotBeSilentlySummed() {
        assertThrows(IllegalArgumentException::class.java) { balance(listOf(payment(-1))) }
        assertThrows(IllegalArgumentException::class.java) { balance(listOf(payment(0))) }
    }

    @Test fun threePeopleShareOnePostedExpenseWithoutDuplicatingIts180Euros() {
        val day = UUID.randomUUID()
        val people = List(3) { pricedEntry(date).copy(harvestId = day, workerId = UUID.randomUUID()) }
        val ledger = expense(day, 18_000)
        val assigned = LabourLedgerAllocation.of(ledger, people)!!
        assertEquals(18_000L, assigned.sumOf { it.amountMinor })
        assertEquals(setOf(ledger.id), assigned.map { it.expenseId }.toSet())
        people.forEach { person ->
            assertEquals(6_000L, LabourSettlement.of(person.workerId!!, campaign, "EUR", assigned, emptyList()).generatedMinor)
        }
        assertEquals(18_000L, ExpenseSummary.of(listOf(ledger), "EUR").totalMinor)
    }

    @Test fun draftExpenseAndUnpricedOrMismatchedLinesCannotBecomeDebt() {
        val ledger = expenses.first()
        val entries = listOf(rows.first())
        assertNull(LabourLedgerAllocation.of(ledger.copy(status = ExpenseStatus.DRAFT), entries))
        assertNull(LabourLedgerAllocation.of(ledger, entries.map { it.copy(appliedRate = null) }))
        assertNull(LabourLedgerAllocation.of(ledger.copy(amountMinor = 7_000), entries))
        assertNull(LabourLedgerAllocation.of(ledger.copy(currency = "USD"), entries))
        assertNull(LabourLedgerAllocation.of(ledger, entries + entries))
        assertNull(LabourLedgerAllocation.of(ledger, emptyList()))
    }

    /** #449 (Codex on #607): a person still without a price is pending; the priced ones keep their debt. */
    @Test fun aPersonWithoutPriceDoesNotEraseThePricedOnesDebt() {
        val ledger = expenses.first()
        val pending = rows.first().copy(id = UUID.randomUUID(), workerId = UUID.randomUUID(), appliedRate = null)
        val assigned = LabourLedgerAllocation.of(ledger, listOf(rows.first(), pending))!!
        assertEquals(listOf(rows.first().workerId), assigned.map { it.workerId })
        assertEquals(6_000L, assigned.single().amountMinor)
        // Never a guess: a posted amount that is not exactly the priced subtotal stays unallocated.
        assertNull(LabourLedgerAllocation.of(ledger.copy(amountMinor = 11_000), listOf(rows.first(), pending)))
    }

    @Test fun manualAndNonLabourCostsAreNeverGuessedIntoWorkerDebt() {
        val ledger = expenses.first()
        assertNull(LabourLedgerAllocation.of(ledger.copy(origin = ExpenseOrigin.MANUAL), listOf(rows.first())))
        assertNull(LabourLedgerAllocation.of(ledger.copy(category = ExpenseCategory.MACHINERY), listOf(rows.first())))
        assertNull(LabourLedgerAllocation.of(ledger.copy(campaignId = null), listOf(rows.first())))
        assertNull(LabourLedgerAllocation.of(ledger.copy(harvestId = null), listOf(rows.first())))
    }

    @Test fun legacyAnonymousCostIsNotDistributedToNamedPeople() {
        val anonymous = rows.first().copy(workerId = null, workerName = null, quantity = 5)
        val ledger = expense(anonymous.harvestId, 30_000)
        val assigned = LabourLedgerAllocation.of(ledger, listOf(anonymous))!!
        assertNull(assigned.single().workerId)
        assertEquals(0L, LabourSettlement.of(worker, campaign, "EUR", assigned, emptyList()).generatedMinor)
        assertEquals(30_000L, ExpenseSummary.of(listOf(ledger), "EUR").totalMinor)
        assertEquals("exceeds_pending", LabourPaymentRules.validate(
            payment(100), LabourSettlement.of(worker, campaign, "EUR", assigned, emptyList()), CampaignStatus.CLOSED,
        )!!.code)
    }

    @Test fun changingFarmRateNeverChangesHistoricalDebtOrPayments() {
        val history = rows.map {
            LabourPricing.capture(it, com.isivoltpro.maginaolivo.domain.expense.RecollectionRates(fullDayMinor = 6_500), date.plusDays(1))
        }
        val assigned = expenses.flatMap { LabourLedgerAllocation.of(it, history)!! }
        val paid = payment(18_000)
        val balance = LabourSettlement.of(worker, campaign, "EUR", assigned, listOf(paid))
        assertEquals(24_000L, balance.generatedMinor)
        assertEquals(18_000L, balance.paidMinor)
        assertEquals(6_000L, balance.pendingMinor)
    }

    private fun pricedEntry(on: LocalDate) = LabourEntry(
        UUID.randomUUID(), UUID.randomUUID(), worker, "Juan García López", 1, LabourUnit.FULL_DAY, null, 1,
        LabourRateSnapshot(6_000, "EUR", on, LabourRateBasis.DAY),
    )

    private fun expense(day: UUID, amount: Long) = Expense(
        UUID.randomUUID(), workspace, date, "Jornales (calculado)", ExpenseCategory.LABOR,
        amount, "EUR", ExpenseStatus.POSTED, ExpenseOrigin.DAY_LABOUR, campaignId = campaign, harvestId = day,
    )

    private fun payment(amount: Long, on: LocalDate = date) =
        LabourPayment(UUID.randomUUID(), worker, campaign, on, amount, "EUR")

    private fun balance(payments: List<LabourPayment> = emptyList()) =
        LabourSettlement.of(worker, campaign, "EUR", costs, payments)
}
