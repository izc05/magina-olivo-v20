package com.isivoltpro.maginaolivo.domain.delivery

import com.isivoltpro.maginaolivo.domain.harvest.HarvestAllocation
import com.isivoltpro.maginaolivo.domain.production.ParcelSplit
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.util.UUID

/** Where a Delivery's figures came from. A ticket read by OCR is still confirmed by a person. */
enum class DeliverySource { MANUAL, TICKET_OCR }

data class DeliveryShare(
    val parcelId: UUID,
    val parcelName: String,
    val allocation: HarvestAllocation,
    val weightGrams: Long?,
)

/**
 * A Delivery to a cooperative or mill (`MASTER-SPEC-RC1` §13). `netGrams` is the delivered
 * weight the farmer confirmed; gross and tare are kept only when the ticket shows them.
 */
data class Delivery(
    val id: UUID,
    val workspaceId: UUID,
    val farmId: UUID,
    val campaignId: UUID,
    val deliveryDate: LocalDate,
    val destinationOrganizationId: UUID?,
    val destinationName: String,
    val netGrams: Long,
    val grossGrams: Long?,
    val tareGrams: Long?,
    val deliveryNumber: String?,
    val ticketNumber: String?,
    val source: DeliverySource,
    val shares: List<DeliveryShare>,
    val notes: String?,
    val version: Long,
    val analysis: YieldAnalysis? = null,
    val farmName: String? = null,
    val campaignName: String? = null,
    val editable: Boolean = true,
) {
    val unallocatedGrams: Long
        get() = netGrams - shares.sumOf { if (it.allocation == HarvestAllocation.EXACT) it.weightGrams ?: 0 else 0 }
}

data class DeliveryShareInput(val parcelId: UUID, val weightGrams: Long?)

data class DeliveryDraft(
    val farmId: UUID,
    val deliveryDate: LocalDate,
    val destinationOrganizationId: UUID?,
    val destinationName: String?,
    val netGrams: Long?,
    val shares: List<DeliveryShareInput>,
    val grossGrams: Long? = null,
    val tareGrams: Long? = null,
    val deliveryNumber: String? = null,
    val ticketNumber: String? = null,
    val notes: String? = null,
)

data class DeliveryProblem(val field: String, val code: String)

object DeliveryRules {
    fun validate(draft: DeliveryDraft, today: LocalDate): DeliveryProblem? {
        val net = draft.netGrams ?: return DeliveryProblem("netGrams", "required")
        if (net <= 0) return DeliveryProblem("netGrams", "not_positive")
        if (draft.deliveryDate.isAfter(today)) return DeliveryProblem("deliveryDate", "future")
        if (draft.destinationOrganizationId == null && draft.destinationName.isNullOrBlank()) {
            return DeliveryProblem("destination", "required")
        }
        if ((draft.grossGrams ?: 1) <= 0 || (draft.tareGrams ?: 0) < 0) {
            return DeliveryProblem("grossGrams", "not_positive")
        }
        // Gross, tare and net are the ticket's own arithmetic: when all three are given they
        // must agree, and the confirmed net is never silently recomputed from the others.
        if (draft.grossGrams != null && draft.tareGrams != null && draft.grossGrams - draft.tareGrams != net) {
            return DeliveryProblem("netGrams", "gross_tare_mismatch")
        }
        if (draft.shares.map { it.parcelId }.toSet().size != draft.shares.size) {
            return DeliveryProblem("parcels", "duplicate")
        }
        return ParcelSplit.problem(net, draft.shares.map { it.weightGrams })?.let { DeliveryProblem("parcels", it) }
    }
}

/**
 * Yield percentages stored as integer hundredths of a percent ("21,35 %" → 2135), so
 * weighted averages are exact.
 */
object Percent {
    private val PLAIN = Regex("""^\d{1,2}([.,]\d{1,2})?$""")

    fun parseHundredths(text: String?): Int? {
        val cleaned = text?.replace("%", "")?.replace(" ", "")?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (!PLAIN.matches(cleaned)) return null
        val value = cleaned.replace(',', '.').toBigDecimalOrNull() ?: return null
        return value.movePointRight(2).setScale(0, RoundingMode.HALF_UP).intValueExact()
    }

    /** "21,35 %". */
    fun format(hundredths: Int): String = editable(hundredths) + " %"

    fun editable(hundredths: Int?): String {
        if (hundredths == null) return ""
        return BigDecimal.valueOf(hundredths.toLong()).movePointLeft(2).stripTrailingZeros().toPlainString().replace('.', ',')
    }
}

/**
 * The later laboratory result for one Delivery (`DATA-MODEL-RC1.1-ADDENDUM` §11). It is its
 * own record: adding or correcting it never touches the Delivery's date, kilos or ticket.
 */
data class YieldAnalysis(
    val id: UUID,
    val deliveryId: UUID,
    val analysisDate: LocalDate?,
    val fatYieldHundredths: Int?,
    val industrialYieldHundredths: Int?,
    val notes: String?,
    val version: Long,
)

data class YieldDraft(
    val analysisDate: LocalDate?,
    val fatYieldHundredths: Int?,
    val industrialYieldHundredths: Int?,
    val notes: String? = null,
)

object YieldRules {
    fun validate(draft: YieldDraft, today: LocalDate): DeliveryProblem? = when {
        draft.fatYieldHundredths == null && draft.industrialYieldHundredths == null ->
            DeliveryProblem("yield", "required")
        listOfNotNull(draft.fatYieldHundredths, draft.industrialYieldHundredths).any { it <= 0 || it > 10_000 } ->
            DeliveryProblem("yield", "out_of_range")
        draft.analysisDate?.isAfter(today) == true -> DeliveryProblem("analysisDate", "future")
        else -> null
    }
}

/** A weighted yield with the kilos it is based on. */
data class WeightedYield(val hundredths: Int, val analysedGrams: Long)

/**
 * Delivered kilos and their yield, weighted by kilos
 * (`SUM(kg × yield) / SUM(kg with a valid analysis)`), with the coverage the UI must show.
 * A Delivery without an analysis counts in the kilos and never in a yield.
 */
data class DeliverySummary(
    val deliveryCount: Int,
    val deliveredGrams: Long,
    val unallocatedGrams: Long,
    val fatYield: WeightedYield?,
    val industrialYield: WeightedYield?,
) {
    companion object {
        fun of(deliveries: List<Delivery>): DeliverySummary = DeliverySummary(
            deliveryCount = deliveries.size,
            deliveredGrams = deliveries.sumOf { it.netGrams },
            unallocatedGrams = deliveries.sumOf { it.unallocatedGrams },
            fatYield = weighted(deliveries) { it.fatYieldHundredths },
            industrialYield = weighted(deliveries) { it.industrialYieldHundredths },
        )

        private fun weighted(deliveries: List<Delivery>, value: (YieldAnalysis) -> Int?): WeightedYield? {
            val analysed = deliveries.mapNotNull { delivery ->
                delivery.analysis?.let(value)?.let { delivery.netGrams to it }
            }
            val grams = analysed.sumOf { it.first }
            if (grams == 0L) return null
            val weightedSum = analysed.fold(BigDecimal.ZERO) { sum, (kg, yield) ->
                sum + BigDecimal.valueOf(kg).multiply(BigDecimal.valueOf(yield.toLong()))
            }
            val average = weightedSum.divide(BigDecimal.valueOf(grams), 0, RoundingMode.HALF_UP).intValueExact()
            return WeightedYield(average, grams)
        }
    }

    /** Share of delivered kilos with this analysis, 0–100. */
    fun coveragePercent(yield: WeightedYield?): Int =
        if (yield == null || deliveredGrams == 0L) 0 else (yield.analysedGrams * 100 / deliveredGrams).toInt()
}
