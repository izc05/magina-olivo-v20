package com.isivoltpro.maginaolivo.domain.labour

import com.isivoltpro.maginaolivo.data.local.model.CampaignStatus
import com.isivoltpro.maginaolivo.domain.expense.Expense
import com.isivoltpro.maginaolivo.domain.expense.ExpenseCategory
import com.isivoltpro.maginaolivo.domain.expense.ExpenseOrigin
import com.isivoltpro.maginaolivo.domain.expense.ExpenseStatus
import java.time.LocalDate
import java.util.UUID

/** CR-012 contract only: independent debt settlement; Room persistence belongs to Slice 2. */
data class LabourPayment(
    val id: UUID,
    val workerId: UUID,
    val campaignId: UUID,
    val paymentDate: LocalDate,
    val amountMinor: Long,
    val currency: String,
    val note: String? = null,
)

/** A reconciled share of an existing Expense, never an additional expense. */
class ConfirmedLabourCost internal constructor(
    val labourEntryId: UUID,
    val expenseId: UUID,
    val workerId: UUID?,
    val campaignId: UUID,
    val amountMinor: Long,
    val currency: String,
)

object LabourLedgerAllocation {
    /**
     * Reconcile the whole day's POSTED labour Expense before attributing any share. null means
     * unallocatable, not zero cost: keep the Expense in the campaign total. Legacy prices and
     * manual costs are never guessed. The caller supplies all live lines of the day.
     */
    fun of(expense: Expense, entries: List<LabourEntry>): List<ConfirmedLabourCost>? {
        val campaignId = expense.campaignId ?: return null
        val dayId = expense.harvestId ?: return null
        if (expense.status != ExpenseStatus.POSTED || expense.origin != ExpenseOrigin.DAY_LABOUR ||
            expense.category != ExpenseCategory.LABOR || expense.amountMinor < 0
        ) return null
        val day = entries.filter { it.harvestId == dayId }
        if (day.isEmpty() || day.map { it.id }.toSet().size != day.size) return null
        // #449: a person whose price is still unknown is pending, not 0 €: the posted amount is the
        // subtotal of the priced lines, and only if it reconciles exactly with them.
        val own = day.filter { it.appliedRate != null }
        if (own.isEmpty()) return null
        val costs = own.map { entry ->
            if (LabourRules.validate(entry.quantity, entry.unit, entry.minutes) != null ||
                (entry.workerId != null && entry.quantity != 1) ||
                entry.appliedRate?.currency != expense.currency
            ) return null
            val amount = LabourPricing.amountMinor(entry) ?: return null
            ConfirmedLabourCost(entry.id, expense.id, entry.workerId, campaignId, amount, expense.currency)
        }
        if (costs.fold(0L) { total, cost -> Math.addExact(total, cost.amountMinor) } != expense.amountMinor) return null
        return costs
    }
}

enum class LabourPaymentState { PENDING, PARTIAL, PAID }

class LabourSettlement private constructor(
    val workerId: UUID,
    val campaignId: UUID,
    val currency: String,
    val generatedMinor: Long,
    val paidMinor: Long,
) {
    val pendingMinor: Long get() = generatedMinor - paidMinor
    val state: LabourPaymentState get() = when {
        paidMinor == 0L -> LabourPaymentState.PENDING
        paidMinor == generatedMinor -> LabourPaymentState.PAID
        else -> LabourPaymentState.PARTIAL
    }

    companion object {
        fun of(
            workerId: UUID,
            campaignId: UUID,
            currency: String,
            costs: List<ConfirmedLabourCost>,
            payments: List<LabourPayment>,
        ): LabourSettlement {
            val ownCosts = costs.filter { it.workerId == workerId && it.campaignId == campaignId && it.currency == currency }
            val ownPayments = payments.filter { it.workerId == workerId && it.campaignId == campaignId && it.currency == currency }
            require(ownCosts.map { it.labourEntryId }.toSet().size == ownCosts.size) { "duplicate_labour_cost" }
            require(ownPayments.map { it.id }.toSet().size == ownPayments.size) { "duplicate_labour_payment" }
            require(ownCosts.all { it.amountMinor >= 0 }) { "negative_labour_cost" }
            require(ownPayments.all { it.amountMinor > 0 }) { "invalid_labour_payment" }
            val generated = ownCosts.fold(0L) { total, cost -> Math.addExact(total, cost.amountMinor) }
            val paid = ownPayments.fold(0L) { total, payment -> Math.addExact(total, payment.amountMinor) }
            require(paid <= generated) { "labour_overpayment" }
            return LabourSettlement(workerId, campaignId, currency, generated, paid)
        }
    }
}

object LabourPaymentRules {
    /** A registered payment is a fact that already happened, never a scheduled future movement. */
    fun validateDate(paymentDate: LocalDate, today: LocalDate): LabourProblem? =
        if (paymentDate.isAfter(today)) LabourProblem("paymentDate", "future") else null

    /** Validate against the current balance inside the repository's write transaction. */
    fun validate(payment: LabourPayment, balance: LabourSettlement, campaignStatus: CampaignStatus): LabourProblem? = when {
        payment.workerId != balance.workerId -> LabourProblem("worker", "worker_mismatch")
        payment.campaignId != balance.campaignId -> LabourProblem("campaign", "campaign_mismatch")
        payment.currency != balance.currency -> LabourProblem("currency", "currency_mismatch")
        campaignStatus == CampaignStatus.PREPARATION -> LabourProblem("campaign", "not_active")
        payment.amountMinor <= 0 -> LabourProblem("amount", "not_positive")
        payment.amountMinor > balance.pendingMinor -> LabourProblem("amount", "exceeds_pending")
        else -> null
    }

    /** Editing a price/duration must preserve paid debt and the closed campaign's cost history. */
    fun validateGeneratedChange(balance: LabourSettlement, generatedMinor: Long, campaignStatus: CampaignStatus): LabourProblem? = when {
        campaignStatus == CampaignStatus.CLOSED -> LabourProblem("campaign", "campaign_closed")
        campaignStatus == CampaignStatus.PREPARATION -> LabourProblem("campaign", "not_active")
        generatedMinor < 0 -> LabourProblem("amount", "not_positive")
        generatedMinor < balance.paidMinor -> LabourProblem("amount", "below_paid")
        else -> null
    }
}
