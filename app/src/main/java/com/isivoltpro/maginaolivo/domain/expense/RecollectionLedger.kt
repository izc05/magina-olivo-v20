package com.isivoltpro.maginaolivo.domain.expense

import com.isivoltpro.maginaolivo.domain.delivery.Delivery
import java.util.UUID

/** Mutually exclusive recollection buckets. Operational prices and payments never add money. */
enum class RecollectionBucket {
    LABOUR, EQUIPMENT, OTHER;

    companion object {
        fun of(expense: Expense): RecollectionBucket = when {
            expense.origin == ExpenseOrigin.DAY_LABOUR -> LABOUR
            expense.origin == ExpenseOrigin.DAY_EQUIPMENT -> EQUIPMENT
            DayCostKind.LABOUR.isReplacedBy(expense.category, expense.concept) -> LABOUR
            DayCostKind.EQUIPMENT.isReplacedBy(expense.category, expense.concept) -> EQUIPMENT
            expense.category == ExpenseCategory.HARVEST && JornadaExpenseKind.LABOUR.names(expense.concept) -> LABOUR
            expense.category == ExpenseCategory.HARVEST && JornadaExpenseKind.RENTAL.names(expense.concept) -> EQUIPMENT
            else -> OTHER
        }
    }
}

/** Separate currencies, including unsupported historical codes. Nothing is converted. */
data class RecollectionCurrency(
    val currency: String,
    val posted: List<Expense>,
    val summary: RecollectionCostSummary?,
) {
    fun hasPosted(bucket: RecollectionBucket): Boolean = posted.any { RecollectionBucket.of(it) == bucket }

    fun amount(bucket: RecollectionBucket? = null): Long? = runCatching {
        posted.filter { bucket == null || RecollectionBucket.of(it) == bucket }
            .takeIf { it.isNotEmpty() }
            ?.fold(0L) { total, expense -> Math.addExact(total, expense.amountMinor) }
    }.getOrNull()

    /** #486: thousandths of the currency unit per kilo (0,253 €/kg = 253), never cents. */
    val costPerKgMilli: Long? get() = summary?.costPerKg?.let(CostPerKg::milli)
}

object RecollectionLedger {
    fun of(campaignId: UUID, expenses: List<Expense>, deliveries: List<Delivery>): List<RecollectionCurrency> =
        posted(expenses.filter { it.campaignId == campaignId }).map { ledger ->
            ledger.copy(summary = runCatching {
                RecollectionCostSummary.of(campaignId, ledger.posted, deliveries, ledger.currency)
            }.getOrNull())
        }

    /** Input is already scoped by the caller (e.g. the day's repository). Never assigns a campaign. */
    fun posted(expenses: List<Expense>): List<RecollectionCurrency> =
        expenses.filter { it.status == ExpenseStatus.POSTED }.groupBy { it.currency }.toSortedMap()
            .map { (currency, rows) ->
                RecollectionCurrency(currency, rows, summary = null)
            }
}
