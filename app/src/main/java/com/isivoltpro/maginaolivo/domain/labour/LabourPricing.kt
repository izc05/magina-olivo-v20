package com.isivoltpro.maginaolivo.domain.labour

import com.isivoltpro.maginaolivo.domain.expense.RecollectionRates
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.util.Currency

/** CR-012: the price actually agreed for this line, not a live reference to Farm preferences. */
enum class LabourRateBasis { DAY, HOUR }

data class LabourRateSnapshot(
    val unitPriceMinor: Long,
    val currency: String,
    val priceDate: LocalDate,
    val basis: LabourRateBasis,
) {
    init {
        require(unitPriceMinor >= 0) { "negative_labour_rate" }
        require(Currency.getInstance(currency).defaultFractionDigits >= 0) { "invalid_currency" }
    }
}

object LabourPricing {
    fun validateNewRecollection(entry: LabourEntry): LabourProblem? = when {
        entry.workerId == null -> LabourProblem("worker", "required")
        entry.quantity != 1 -> LabourProblem("quantity", "one_per_person")
        else -> LabourRules.validate(entry.quantity, entry.unit, entry.minutes)
    }

    fun capture(entry: LabourEntry, rates: RecollectionRates, priceDate: LocalDate): LabourEntry {
        if (entry.appliedRate != null) {
            require(entry.appliedRate.basis == entry.unit.rateBasis()) { "labour_rate_basis_mismatch" }
            return entry
        }
        val price = when (entry.unit) {
            LabourUnit.FULL_DAY, LabourUnit.HALF_DAY -> rates.fullDayMinor
            LabourUnit.HOURS -> rates.hourlyMinor
        } ?: return entry
        return entry.copy(appliedRate = LabourRateSnapshot(price, rates.currency, priceDate, entry.unit.rateBasis()))
    }

    /** Operational valuation only. Campaign totals still come exclusively from Expense POSTED. */
    fun amountMinor(entry: LabourEntry): Long? {
        require(LabourRules.validate(entry.quantity, entry.unit, entry.minutes) == null) { "invalid_labour" }
        val rate = entry.appliedRate ?: return null
        require(rate.basis == entry.unit.rateBasis()) { "labour_rate_basis_mismatch" }
        val price = BigDecimal.valueOf(rate.unitPriceMinor)
        val perPerson = when (entry.unit) {
            LabourUnit.FULL_DAY -> price
            LabourUnit.HALF_DAY -> price.divide(BigDecimal.valueOf(2), 0, RoundingMode.HALF_UP)
            LabourUnit.HOURS -> price.multiply(BigDecimal.valueOf(entry.minutes!!.toLong()))
                .divide(BigDecimal.valueOf(60), 0, RoundingMode.HALF_UP)
        }
        return perPerson.multiply(BigDecimal.valueOf(entry.quantity.toLong())).longValueExact()
    }
}

private fun LabourUnit.rateBasis(): LabourRateBasis = when (this) {
    LabourUnit.FULL_DAY, LabourUnit.HALF_DAY -> LabourRateBasis.DAY
    LabourUnit.HOURS -> LabourRateBasis.HOUR
}
