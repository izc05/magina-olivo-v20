package com.isivoltpro.maginaolivo.domain.ocr

import com.isivoltpro.maginaolivo.domain.expense.Money
import java.time.LocalDate

/**
 * Turns the raw text of an invoice or receipt into a [PurchaseProposal].
 *
 * Deliberately conservative: a field is proposed only when the text says it plainly, and
 * anything ambiguous is left empty for the person to fill in. A wrong guess costs a farmer
 * more than an empty field, because a guess looks like a fact.
 */
object PurchaseDocumentParser {
    private val AMOUNT = Regex("""\d{1,3}(?:[.\s]\d{3})+(?:,\d{1,2})?|\d+[.,]\d{2}|\d+""")
    private val DATE = Regex("""\b(\d{1,2})[/\-.](\d{1,2})[/\-.](\d{4}|\d{2})\b""")
    private val TAX_ID = Regex("""\b([A-HJNP-SUVW]-?\d{7}-?[0-9A-J]|\d{8}-?[A-Z]|[XYZ]-?\d{7}-?[A-Z])\b""")
    private val INVOICE_NUMBER = Regex(
        """(?i)(?:factura|fra\.?|ticket|albar[aá]n|n[ºo°]\s*(?:de\s*)?factura|n[uú]mero)\s*(?:n[ºo°.]*\s*)?[:#]?\s*([A-Z0-9][A-Z0-9/\-]{2,})""",
    )
    private val HEADER_WORDS = listOf(
        "factura", "fecha", "nif", "cif", "tel", "tfno", "ticket", "cliente", "total",
        "iva", "base", "albar", "direcci", "c/", "calle", "www", "@", "cp ",
    )

    fun parse(rawText: String): PurchaseProposal {
        val lines = rawText.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val currency = if (rawText.contains('€') || rawText.contains("EUR", ignoreCase = true)) "EUR" else null
        return PurchaseProposal(
            supplierName = supplierName(lines),
            supplierTaxId = TAX_ID.find(rawText.uppercase())?.value?.replace("-", ""),
            invoiceNumber = invoiceNumber(lines),
            invoiceDate = date(lines),
            subtotalMinor = amountOn(lines) { it.contains("base imponible") || it.contains("subtotal") },
            taxMinor = amountOn(lines) { (it.contains("iva") || it.contains("cuota")) && !it.contains("total") },
            totalMinor = total(lines),
            currency = currency,
        )
    }

    private fun supplierName(lines: List<String>): String? =
        lines.take(5).firstOrNull { line ->
            val lower = line.lowercase()
            line.count(Char::isLetter) >= 3 &&
                HEADER_WORDS.none { lower.contains(it) } &&
                DATE.find(line) == null &&
                line.count(Char::isDigit) <= line.length / 3
        }

    private fun invoiceNumber(lines: List<String>): String? =
        lines.firstNotNullOfOrNull { line ->
            INVOICE_NUMBER.find(line)?.groupValues?.get(1)?.takeIf { candidate ->
                candidate.any(Char::isDigit) && DATE.find(candidate) == null
            }
        }

    /** The first real date, preferring one on a line that says it is the date. */
    private fun date(lines: List<String>): LocalDate? {
        val labelled = lines.filter { it.contains("fecha", ignoreCase = true) }
        return (labelled + lines).firstNotNullOfOrNull { line ->
            DATE.findAll(line).firstNotNullOfOrNull { match ->
                val (day, month, year) = match.destructured
                val fullYear = if (year.length == 2) 2000 + year.toInt() else year.toInt()
                runCatching { LocalDate.of(fullYear, month.toInt(), day.toInt()) }.getOrNull()
            }
        }
    }

    private fun total(lines: List<String>): Long? {
        val strong = amountOn(lines) { lower ->
            (lower.contains("total a pagar") || lower.contains("importe total") || lower.contains("total factura"))
        }
        return strong ?: amountOn(lines) { lower ->
            lower.contains("total") && !lower.contains("subtotal") && !lower.contains("base")
        }
    }

    /**
     * The last amount on the last line matching [label]. Totals usually come last, and on a
     * line the figure follows its label. An amount without decimals must be on a labelled
     * line to count, so a quantity or a postcode is never read as money.
     */
    private fun amountOn(lines: List<String>, label: (String) -> Boolean): Long? =
        lines.lastOrNull { label(it.lowercase()) }?.let { line ->
            val afterLabel = line.substringAfter(':', line)
            AMOUNT.findAll(afterLabel).lastOrNull()?.value?.let(Money::parseMinor)
        }
}
