package com.isivoltpro.maginaolivo.domain.ocr

import com.isivoltpro.maginaolivo.domain.harvest.Weight
import java.time.LocalDate

/**
 * What a weight ticket seems to say (`DATA-MODEL-RC1.2-ADDENDUM` §4, delivery ticket
 * proposal). Every field is a proposal; the delivered kilos become true only when the
 * farmer confirms them.
 */
data class DeliveryTicketProposal(
    val organizationName: String? = null,
    val ticketNumber: String? = null,
    val deliveryDate: LocalDate? = null,
    val grossGrams: Long? = null,
    val tareGrams: Long? = null,
    val netGrams: Long? = null,
    val memberReference: String? = null,
    val vehicleReference: String? = null,
) {
    val hasEssentials: Boolean get() = netGrams != null && deliveryDate != null

    /** The ticket's own arithmetic, shown to the person — never used to fill a gap. */
    val weightsDisagree: Boolean
        get() = grossGrams != null && tareGrams != null && netGrams != null && grossGrams - tareGrams != netGrams
}

/**
 * Reads the text of a cooperative or mill weight ticket. Conservative like the purchase
 * parser: a weight is proposed only on a line that names it (bruto / tara / neto), and a
 * net weight is never computed from gross and tare.
 */
object DeliveryTicketParser {
    private val DATE = Regex("""\b(\d{1,2})[/\-.](\d{1,2})[/\-.](\d{4}|\d{2})\b""")
    private val NUMBER = Regex("""\d{1,3}(?:\.\d{3})+(?:,\d{1,3})?|\d+(?:,\d{1,3})?""")
    private val TICKET = Regex(
        """(?i)(?:ticket|vale|albar[aá]n|entrada|pesada|n[ºo°]\s*(?:de\s*)?(?:ticket|vale|entrada))\s*(?:n[ºo°.]*\s*)?[:#]?\s*([A-Z0-9][A-Z0-9/\-]{1,})""",
    )
    private val MEMBER = Regex("""(?i)(?:socio|n[ºo°]\s*socio|c[oó]d\.?\s*socio)\s*[:#]?\s*([A-Z0-9\-/]{1,})""")
    private val VEHICLE = Regex("""(?i)(?:matr[ií]cula|veh[ií]culo)\s*[:#]?\s*([A-Z0-9\- ]{4,12})""")
    private val ORGANIZATION_WORDS = listOf("cooperativa", "coop", "almazara", "sca", "s.c.a", "aceites", "oleo", "olivarera")

    fun parse(rawText: String): DeliveryTicketProposal {
        val lines = rawText.lines().map { it.trim() }.filter { it.isNotEmpty() }
        return DeliveryTicketProposal(
            organizationName = lines.take(6).firstOrNull { line ->
                val lower = line.lowercase()
                ORGANIZATION_WORDS.any { lower.contains(it) } && DATE.find(line) == null
            },
            ticketNumber = lines.firstNotNullOfOrNull { line ->
                TICKET.find(line)?.groupValues?.get(1)?.takeIf { it.any(Char::isDigit) && DATE.find(it) == null }
            },
            deliveryDate = date(lines),
            grossGrams = weightOn(lines, "bruto"),
            tareGrams = weightOn(lines, "tara"),
            netGrams = weightOn(lines, "neto"),
            memberReference = lines.firstNotNullOfOrNull { MEMBER.find(it)?.groupValues?.get(1) },
            vehicleReference = lines.firstNotNullOfOrNull { VEHICLE.find(it)?.groupValues?.get(1)?.trim() },
        )
    }

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

    /** The first number after the label on the first line naming it, read as kilos. */
    private fun weightOn(lines: List<String>, label: String): Long? =
        lines.firstOrNull { it.lowercase().contains(label) }?.let { line ->
            val after = line.substring(line.lowercase().indexOf(label) + label.length)
            NUMBER.find(after)?.value?.let(Weight::parseGrams)
        }
}
