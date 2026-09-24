package com.isivoltpro.maginaolivo.domain.delivery

import com.isivoltpro.maginaolivo.domain.harvest.HarvestAllocation
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.util.UUID

/** Phase 19C: whether a Pesada already has its later yield analysis. */
enum class YieldStatus { PENDING, WITH_YIELD }

/**
 * Phase 19C (CR-005 §6): what the farmer looks for 2–3 days after weighing, when the yield
 * arrives. Every field is optional; an empty query finds every Pesada.
 */
data class PesadaQuery(
    /** Ticket or albarán number, as the farmer remembers it ("45872", "#45872", "V-45872"). */
    val text: String = "",
    val status: YieldStatus? = null,
    /** A cooperative or mill, by its saved Organization or by the name copied on the Pesada. */
    val cooperative: String? = null,
    val farmId: UUID? = null,
    val from: LocalDate? = null,
    val to: LocalDate? = null,
) {
    val isEmpty: Boolean
        get() = text.isBlank() && status == null && cooperative == null && farmId == null && from == null && to == null
}

object PesadaSearch {
    fun statusOf(delivery: Delivery): YieldStatus =
        if (delivery.analysis?.let { it.fatYieldHundredths != null || it.industrialYieldHundredths != null } == true) {
            YieldStatus.WITH_YIELD
        } else {
            YieldStatus.PENDING
        }

    /** The cooperative a Pesada went to, as the key the filter uses. */
    fun cooperativeKey(delivery: Delivery): String =
        delivery.destinationOrganizationId?.toString() ?: delivery.destinationName.trim().lowercase()

    /** Newest first. Nothing is hidden except by the filters the farmer chose. */
    fun filter(deliveries: List<Delivery>, query: PesadaQuery): List<Delivery> {
        val needle = normalized(query.text)
        return deliveries.filter { delivery ->
            (needle.isEmpty() || matchesNumber(delivery, needle)) &&
                (query.status == null || statusOf(delivery) == query.status) &&
                (query.cooperative == null || cooperativeKey(delivery) == query.cooperative) &&
                (query.farmId == null || delivery.farmId == query.farmId) &&
                (query.from == null || !delivery.deliveryDate.isBefore(query.from)) &&
                (query.to == null || !delivery.deliveryDate.isAfter(query.to))
        }.sortedWith(compareByDescending<Delivery> { it.deliveryDate }.thenByDescending { it.deliveryTime })
    }

    /** "V-45872" is found by "45872", "v45872" or "#45872"; other text never matches loosely. */
    private fun matchesNumber(delivery: Delivery, needle: String): Boolean =
        listOfNotNull(delivery.ticketNumber, delivery.deliveryNumber).any { normalized(it).contains(needle) }

    private fun normalized(text: String): String =
        text.trim().lowercase().filter { it.isLetterOrDigit() }
}

/** One Parcel's yield, with the kilos it is based on; never an estimate from mixed loads. */
data class ParcelYield(
    val parcelId: UUID,
    val parcelName: String,
    val fatYield: WeightedYield?,
    /** Kilos attributable to this Parcel (single origin or exact split), analysed or not. */
    val attributedGrams: Long,
) {
    /** Share of this Parcel's attributed kilos that carry a fat-yield analysis, 0–100. */
    val coveragePercent: Int
        get() = if (fatYield == null || attributedGrams == 0L) 0 else (fatYield.analysedGrams * 100 / attributedGrams).toInt()

    companion object {
        /**
         * Phase 19C (Gate 19C): a Pesada counts for a Parcel only when its kilos there are
         * known — a single origin Parcel, or that Parcel's EXACT share. A mixed load with an
         * unknown split counts in the Farm/Campaign yield (`DeliverySummary`) and never here.
         */
        fun of(deliveries: List<Delivery>): List<ParcelYield> {
            data class Part(val parcelId: UUID, val name: String, val grams: Long, val fat: Int?)
            val parts = deliveries.flatMap { delivery ->
                val fat = delivery.analysis?.fatYieldHundredths
                when {
                    delivery.shares.size == 1 -> {
                        val share = delivery.shares.single()
                        listOf(Part(share.parcelId, share.parcelName, delivery.netGrams, fat))
                    }
                    else -> delivery.shares
                        .filter { it.allocation == HarvestAllocation.EXACT && (it.weightGrams ?: 0) > 0 }
                        .map { Part(it.parcelId, it.parcelName, it.weightGrams!!, fat) }
                }
            }
            return parts.groupBy { it.parcelId }.map { (parcelId, group) ->
                val analysed = group.filter { it.fat != null }
                val grams = analysed.sumOf { it.grams }
                val yield = if (grams == 0L) {
                    null
                } else {
                    val sum = analysed.fold(BigDecimal.ZERO) { acc, part ->
                        acc + BigDecimal.valueOf(part.grams).multiply(BigDecimal.valueOf(part.fat!!.toLong()))
                    }
                    WeightedYield(sum.divide(BigDecimal.valueOf(grams), 0, RoundingMode.HALF_UP).intValueExact(), grams)
                }
                ParcelYield(parcelId, group.first().name, yield, group.sumOf { it.grams })
            }.sortedBy { it.parcelName.lowercase() }
        }
    }
}
