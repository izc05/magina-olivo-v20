package com.isivoltpro.maginaolivo.domain.expense

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * #486: cost per kilo is a ratio, not a payment. It keeps thousandths of the currency unit per
 * kilo («0,253 €/kg»), never the currency's cents: 1.440 € / 5.700 kg reads 0,253, not 0,25.
 * The money itself stays in minor units in the Expense ledger; only this ratio has its own scale.
 */
object CostPerKg {
    const val SCALE = 3

    /** Thousandths of the currency unit per kilo, HALF_UP. Null when unrepresentable. */
    fun milli(perKg: BigDecimal): Long? =
        runCatching { perKg.setScale(SCALE, RoundingMode.HALF_UP).movePointRight(SCALE).longValueExact() }.getOrNull()

    /** From a posted total in minor units and weighed grams; null without kilos or with an unknown currency. */
    fun milli(amountMinor: Long, currency: String, grams: Long): Long? {
        if (grams <= 0) return null
        val digits = runCatching { Currency.getInstance(currency).defaultFractionDigits }.getOrNull()?.takeIf { it >= 0 } ?: return null
        val perKg = BigDecimal.valueOf(amountMinor).movePointLeft(digits)
            .multiply(BigDecimal.valueOf(1_000))
            .divide(BigDecimal.valueOf(grams), MathContext.DECIMAL128)
        return milli(perKg)
    }

    /** «0,253 €/kg» — always three decimals so campaigns compare at a glance. */
    fun format(milli: Long, currency: String): String {
        val iso = runCatching { Currency.getInstance(currency) }.getOrNull()
        val number = BigDecimal.valueOf(milli, SCALE)
        if (iso == null) return "${number.toPlainString().replace('.', ',')} $currency/kg"
        val format = NumberFormat.getCurrencyInstance(SPANISH)
        format.currency = iso
        format.minimumFractionDigits = SCALE
        format.maximumFractionDigits = SCALE
        return format.format(number) + "/kg"
    }

    private val SPANISH: Locale = Locale.forLanguageTag("es-ES")
}
