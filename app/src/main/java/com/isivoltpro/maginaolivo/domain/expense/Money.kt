package com.isivoltpro.maginaolivo.domain.expense

import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * Money is integer minor units plus an ISO currency (`DATA-MODEL-RC1-FUTURE` §1). These
 * helpers read what a farmer types — "65", "65,5", "1.234,56 €", "1234.56" — and never fall
 * back to a guess: an unreadable amount is null, not zero.
 */
object Money {
    private val SPANISH = Locale.forLanguageTag("es-ES")
    private val GROUPED_COMMA_DECIMAL = Regex("""^\d{1,3}(\.\d{3})+(,\d{1,2})?$""")
    private val GROUPED_DOT_DECIMAL = Regex("""^\d{1,3}(,\d{3})+\.\d{1,2}$""")
    private val PLAIN = Regex("""^\d+([.,]\d{1,2})?$""")

    fun parseMinor(text: String?): Long? {
        val cleaned = text
            ?.replace("€", "")
            ?.replace("EUR", "", ignoreCase = true)
            ?.replace(' ', ' ')
            ?.replace(" ", "")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: return null
        val normalized = when {
            GROUPED_COMMA_DECIMAL.matches(cleaned) -> cleaned.replace(".", "").replace(',', '.')
            GROUPED_DOT_DECIMAL.matches(cleaned) -> cleaned.replace(",", "")
            PLAIN.matches(cleaned) -> cleaned.replace(',', '.')
            else -> return null
        }
        val value = normalized.toBigDecimalOrNull() ?: return null
        return value.movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).longValueExact()
    }

    fun format(minor: Long, currency: String = "EUR"): String {
        val format = NumberFormat.getCurrencyInstance(SPANISH)
        runCatching { format.currency = Currency.getInstance(currency) }
        return format.format(minor / 100.0)
    }

    /** "65,50" for an editable field: no symbol, no grouping. */
    fun editable(minor: Long?): String {
        if (minor == null) return ""
        val units = minor / 100
        val cents = kotlin.math.abs(minor % 100)
        return if (cents == 0L) units.toString() else "%d,%02d".format(units, cents)
    }
}
