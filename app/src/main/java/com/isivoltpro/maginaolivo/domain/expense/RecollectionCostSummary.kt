package com.isivoltpro.maginaolivo.domain.expense

import com.isivoltpro.maginaolivo.domain.delivery.Delivery
import java.math.BigDecimal
import java.math.MathContext
import java.util.Currency
import java.util.UUID

/** CR-012 Slice 1: precise campaign cost contract. No payment or attendance money is added. */
class RecollectionCostSummary private constructor(
    val totalMinor: Long,
    val currency: String,
    val weighedGrams: Long,
    val costPerKg: BigDecimal?,
) {
    companion object {
        fun of(
            campaignId: UUID,
            expenses: List<Expense>,
            deliveries: List<Delivery>,
            currency: String,
        ): RecollectionCostSummary {
            val fractionDigits = Currency.getInstance(currency).defaultFractionDigits
            require(fractionDigits >= 0) { "invalid_currency" }
            val posted = expenses.filter {
                it.campaignId == campaignId && it.currency == currency && it.status == ExpenseStatus.POSTED
            }
            val weighed = deliveries.filter { it.campaignId == campaignId }
            require(posted.map { it.id }.toSet().size == posted.size) { "duplicate_expense" }
            require(weighed.map { it.id }.toSet().size == weighed.size) { "duplicate_delivery" }
            require(posted.all { it.amountMinor >= 0 }) { "negative_expense" }
            require(weighed.all { it.netGrams >= 0 }) { "negative_weighing" }
            val total = posted.fold(0L) { sum, expense -> Math.addExact(sum, expense.amountMinor) }
            val grams = weighed.fold(0L) { sum, delivery -> Math.addExact(sum, delivery.netGrams) }
            val ratio = if (posted.isNotEmpty() && grams > 0) {
                BigDecimal.valueOf(total).movePointLeft(fractionDigits)
                    .multiply(BigDecimal.valueOf(1_000))
                    .divide(BigDecimal.valueOf(grams), MathContext.DECIMAL128)
            } else {
                null
            }
            return RecollectionCostSummary(total, currency, grams, ratio)
        }
    }
}
