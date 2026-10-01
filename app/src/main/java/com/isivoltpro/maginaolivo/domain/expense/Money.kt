package com.isivoltpro.maginaolivo.domain.expense

import java.text.NumberFormat
import java.math.BigDecimal
import java.util.Currency
import java.util.Locale

/**
 * Money is integer minor units plus an ISO currency (`DATA-MODEL-RC1-FUTURE` §1). These
 * helpers read what a farmer types — "65", "65,5", "1.234,56 €", "1234.56" — and never fall
 * back to a guess: an unreadable amount is null, not zero.
 */
object Money {
    private val SPANISH = Locale.forLanguageTag("es-ES")
    fun parseMinor(text: String?, currency: String = "EUR"): Long? {
        val iso = runCatching { Currency.getInstance(currency) }.getOrNull() ?: return null
        val digits = iso.defaultFractionDigits.takeIf { it >= 0 } ?: return null
        val decimal = if (digits == 0) "" else "([.,]\\d{1,$digits})?"
        val groupedComma = Regex("^\\d{1,3}(\\.\\d{3})+" + if (digits == 0) "$" else "(,\\d{1,$digits})?$")
        val groupedDot = Regex("^\\d{1,3}(,\\d{3})+\\.\\d{1,${digits.coerceAtLeast(1)}}$")
        val plain = Regex("^\\d+$decimal$")
        val cleaned = text
            ?.replace(iso.getSymbol(SPANISH), "")
            ?.replace(currency, "", ignoreCase = true)
            ?.replace(' ', ' ')
            ?.replace(" ", "")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: return null
        val normalized = when {
            plain.matches(cleaned) -> cleaned.replace(',', '.')
            groupedComma.matches(cleaned) -> cleaned.replace(".", "").replace(',', '.')
            digits > 0 && groupedDot.matches(cleaned) -> cleaned.replace(",", "")
            else -> return null
        }
        val value = normalized.toBigDecimalOrNull() ?: return null
        return runCatching { value.movePointRight(digits).longValueExact() }.getOrNull()
    }

    fun format(minor: Long, currency: String = "EUR"): String {
        val iso = runCatching { Currency.getInstance(currency) }.getOrNull()
        val digits = iso?.defaultFractionDigits?.takeIf { it >= 0 }
            ?: return "Importe no disponible ($currency)"
        val format = NumberFormat.getCurrencyInstance(SPANISH)
        format.currency = iso
        format.minimumFractionDigits = digits
        format.maximumFractionDigits = digits
        return format.format(BigDecimal.valueOf(minor, digits))
    }

    /** "65,50" for an editable field: no symbol, no grouping. */
    fun editable(minor: Long?, currency: String = "EUR"): String {
        if (minor == null) return ""
        val digits = runCatching { Currency.getInstance(currency) }.getOrNull()
            ?.defaultFractionDigits?.takeIf { it >= 0 } ?: return ""
        val value = BigDecimal.valueOf(minor, digits)
        return (if (value.stripTrailingZeros().scale() <= 0) value.setScale(0) else value).toPlainString().replace('.', ',')
    }
}
