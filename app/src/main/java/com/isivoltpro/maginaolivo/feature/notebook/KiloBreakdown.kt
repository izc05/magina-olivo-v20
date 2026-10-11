package com.isivoltpro.maginaolivo.feature.notebook

import com.isivoltpro.maginaolivo.domain.harvest.Weight

/**
 * #355 — how the campaign's weighed kilos divide between the parcels, **for reading only**.
 *
 * The defect this fixes was a sentence, not a sum. «Rendimiento por parcela» showed 2.710 kg and
 * 1.850 kg while the campaign showed 6.680 kg, and under them a line said that 6.680 kg were
 * «sin reparto por parcela». Both figures were right and measured different things:
 *
 * - a Pesada from a single Parcel is attributed to it whole, which is where 2.710 and 1.850 come
 *   from (4.560 kg of the campaign);
 * - `unallocatedGrams` counts the kilos with no **exact** weight written per Parcel, which in that
 *   campaign is every Pesada, so it read 6.680 kg.
 *
 * Nothing here recomputes a total: it only names the three figures already on screen — weighed,
 * attributed to a Parcel, and coming from Pesadas that mix Parcels with no exact split. Any
 * unknown figure (#497) leaves the rest unsaid rather than guessed.
 */
internal data class KiloBreakdown(
    /** Everything the campaign weighed. */
    val deliveredGrams: Long?,
    /** The part that belongs to one named Parcel. */
    val attributedGrams: Long?,
    /** The rest: Pesadas that mix Parcels without an exact weight for each. */
    val mixedGrams: Long?,
) {
    companion object {
        fun of(deliveredGrams: Long?, attributedPerParcel: List<Long>): KiloBreakdown {
            val delivered = deliveredGrams?.takeIf { it >= 0 }
            val attributed = attributedPerParcel.takeIf { it.isNotEmpty() }?.let(::checkedSum)
            val mixed = if (delivered != null && attributed != null && delivered >= attributed) {
                delivered - attributed
            } else {
                null
            }
            return KiloBreakdown(delivered, attributed, mixed)
        }

        /**
         * #497 — the same rule the aggregates follow: a negative figure or a sum that would not fit
         * in a Long is unknown, never a wrapped-around number. Imported or corrupt records reach
         * this screen too, and a silent overflow here would print a kilo figure that is a lie.
         */
        private fun checkedSum(values: List<Long>): Long? {
            var total = 0L
            for (value in values) {
                if (value < 0) return null
                total = try {
                    Math.addExact(total, value)
                } catch (_: ArithmeticException) {
                    return null
                }
            }
            return total
        }
    }
}

/**
 * The line under «Rendimiento por parcela», or null when there is nothing honest to say — the
 * campaign's kilos are unknown, or every kilo already has its Parcel.
 */
internal fun kiloBreakdownLine(breakdown: KiloBreakdown): String? {
    val delivered = breakdown.deliveredGrams ?: return null
    val attributed = breakdown.attributedGrams ?: return null
    val mixed = breakdown.mixedGrams ?: return null
    if (mixed <= 0L) {
        return "Los ${Weight.format(delivered)} pesados en la campaña están repartidos por parcela."
    }
    return "De los ${Weight.format(delivered)} pesados en la campaña, ${Weight.format(attributed)} " +
        "tienen parcela asignada y ${Weight.format(mixed)} vienen de pesadas que mezclan parcelas " +
        "sin reparto exacto: cuentan en el total, no en el rendimiento por parcela."
}
