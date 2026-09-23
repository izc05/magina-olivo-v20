package com.isivoltpro.maginaolivo.domain.harvest

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

/**
 * Harvest weight is stored as integer grams, so totals add up exactly. A farmer types
 * kilos — "2850", "2.850", "2850,5", "12.5" — and an ambiguous figure is refused rather
 * than read as something they did not mean: "2.850" is the Spanish 2 850 kg, and
 * "2850.500" is neither, so it is null.
 */
object Weight {
    private val SPANISH = Locale.forLanguageTag("es-ES")
    private val GROUPED = Regex("""^\d{1,3}(\.\d{3})+(,\d{1,3})?$""")
    private val COMMA_DECIMAL = Regex("""^\d+(,\d{1,3})?$""")
    private val DOT_DECIMAL = Regex("""^\d+\.\d{1,2}$""")

    fun parseGrams(text: String?): Long? {
        val cleaned = text
            ?.replace("kg", "", ignoreCase = true)
            ?.replace(' ', ' ')
            ?.replace(" ", "")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: return null
        val normalized = when {
            GROUPED.matches(cleaned) -> cleaned.replace(".", "").replace(',', '.')
            COMMA_DECIMAL.matches(cleaned) -> cleaned.replace(',', '.')
            DOT_DECIMAL.matches(cleaned) -> cleaned
            else -> return null
        }
        val kilos = normalized.toBigDecimalOrNull() ?: return null
        return kilos.movePointRight(3).setScale(0, RoundingMode.HALF_UP).longValueExact()
    }

    /** "2.850 kg", "2.850,5 kg". */
    fun format(grams: Long): String {
        val format = NumberFormat.getNumberInstance(SPANISH).apply {
            maximumFractionDigits = 3
            minimumFractionDigits = 0
        }
        return format.format(BigDecimal.valueOf(grams).movePointLeft(3)) + " kg"
    }

    /** "2850,5" for an editable field: no unit, no grouping. */
    fun editable(grams: Long?): String {
        if (grams == null) return ""
        val kilos = BigDecimal.valueOf(grams).movePointLeft(3).stripTrailingZeros()
        return kilos.toPlainString().replace('.', ',')
    }
}
