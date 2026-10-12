package com.isivoltpro.maginaolivo.domain.analytics

import com.isivoltpro.maginaolivo.domain.delivery.Delivery
import com.isivoltpro.maginaolivo.domain.expense.Expense
import com.isivoltpro.maginaolivo.domain.expense.ExpenseCategory
import com.isivoltpro.maginaolivo.domain.expense.RecollectionBucket
import com.isivoltpro.maginaolivo.domain.expense.RecollectionLedger
import java.util.UUID

/**
 * #355 Fase 2, bloque A — what the campaign panel draws, with no Android and no Compose in it, so
 * every rule below is checked by a unit test instead of by looking at a screen.
 *
 * Two things are worked out here and nothing else: how the campaign's weighed kilos divide between
 * its Parcels, and how its posted costs divide between jornales, maquinaria, combustible and the
 * rest. Both are **readings of records that already exist**: no total is recomputed, no kilo is
 * shared out and no amount is converted.
 */

// ---------------------------------------------------------------------------------------------
// Kilos per Parcel
// ---------------------------------------------------------------------------------------------

/** One slice of the kilos donut. [parcelId] is null on the slice of the mixed Pesadas. */
data class ParcelKiloSlice(val parcelId: UUID?, val label: String, val grams: Long) {
    val isMixed: Boolean get() = parcelId == null
}

/**
 * The campaign's weighed kilos as slices.
 *
 * A Pesada from a single Parcel belongs to it whole; a Pesada that mixes Parcels without an exact
 * weight for each belongs to **no** Parcel and keeps a slice of its own. That slice is the heart of
 * this: the alternative — sharing those kilos out by surface, by guess or by proportion — would
 * print a figure the farmer never weighed. The DEMO campaign is 2.710 + 1.850 attributed and 2.120
 * mixed, and those 2.120 kg stay together under their own name.
 */
data class ParcelKiloSplit(
    val slices: List<ParcelKiloSlice>,
    /** Everything the campaign weighed, or null when that total is not known (#497). */
    val deliveredGrams: Long?,
) {
    /** Unknown when there are no slices: a refused split has no attributed kilos, not zero of them. */
    val attributedGrams: Long?
        get() = slices.filterNot { it.isMixed }.map { it.grams }.takeIf { slices.isNotEmpty() }?.checkedSum()
    val mixedGrams: Long? get() = slices.firstOrNull { it.isMixed }?.grams
    val isEmpty: Boolean get() = slices.isEmpty()

    companion object {
        const val MIXED_LABEL = "Pesadas mezcladas"

        /**
         * [attributed] is the kilos each Parcel already has by name, as «Rendimiento por parcela»
         * works them out. The mixed slice is the remainder, and it only appears when the total is
         * known and is not smaller than what the Parcels already hold — otherwise the records
         * disagree (corrupt or imported data) and the donut is not drawn at all rather than drawn
         * wrong.
         */
        fun of(deliveredGrams: Long?, attributed: List<ParcelKiloSlice>): ParcelKiloSplit {
            val total = deliveredGrams?.takeIf { it >= 0 }
            // A Parcel with negative kilos is a broken record, not an empty one. Dropping it would
            // quietly hand its kilos to «Pesadas mezcladas», inventing mixed kilos out of a bad
            // figure, so the whole split is refused instead.
            if (attributed.any { it.grams < 0 }) return ParcelKiloSplit(emptyList(), total)
            val named = attributed.filter { it.grams > 0 && it.parcelId != null }
            val namedTotal = named.map { it.grams }.checkedSum()
            if (total == null || namedTotal == null || namedTotal > total) {
                return ParcelKiloSplit(emptyList(), total)
            }
            val mixed = total - namedTotal
            return ParcelKiloSplit(
                slices = named + listOfNotNull(
                    mixed.takeIf { it > 0 }?.let { ParcelKiloSlice(null, MIXED_LABEL, it) },
                ),
                deliveredGrams = total,
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Costs per category
// ---------------------------------------------------------------------------------------------

/**
 * The four slices of the costs donut. They come from [RecollectionBucket], which is already
 * mutually exclusive, so **every posted cost lands in exactly one of them and is counted once**.
 */
enum class ExpenseSlice(val label: String) {
    LABOUR("Jornales"),
    EQUIPMENT("Maquinaria"),
    FUEL("Combustible"),
    OTHER("Otros");

    companion object {
        /**
         * Fuel is taken **only** from what the ledger already calls OTHER, and **only** when the
         * farmer classified that cost as [ExpenseCategory.FUEL]. Nothing is reclassified by reading
         * its description, and nothing is taken away from jornales or maquinaria — so the cost per
         * kilo and the totals on screen keep giving exactly the same figures as before.
         */
        fun of(expense: Expense): ExpenseSlice = when (RecollectionBucket.of(expense)) {
            RecollectionBucket.LABOUR -> LABOUR
            RecollectionBucket.EQUIPMENT -> EQUIPMENT
            RecollectionBucket.OTHER -> if (expense.category == ExpenseCategory.FUEL) FUEL else OTHER
        }
    }
}

/** The costs of one currency; currencies are never added together nor converted. */
data class ExpenseSplit(
    val currency: String,
    val slices: List<Pair<ExpenseSlice, Long>>,
) {
    /** The posted total of this currency, or null if it would not fit in a Long. */
    val totalMinor: Long? get() = slices.map { it.second }.checkedSum()
    val isEmpty: Boolean get() = slices.isEmpty()
}

object CampaignCostSplit {
    /**
     * The campaign's posted costs by category, one list per currency.
     *
     * Everything that must stay out — drafts, unconfirmed proposals and costs belonging to another
     * campaign — is already out: [RecollectionLedger.of] keeps only `POSTED` expenses whose
     * `campaignId` is this campaign, and groups them by currency. This function adds no filter of
     * its own, so there is one place where that rule lives.
     */
    fun of(campaignId: UUID, expenses: List<Expense>, deliveries: List<Delivery>): List<ExpenseSplit> =
        RecollectionLedger.of(campaignId, expenses, deliveries).map { ledger ->
            val bySlice = ledger.posted.groupBy(ExpenseSlice::of)
            val amounts = ExpenseSlice.entries.associateWith { slice ->
                bySlice[slice]?.map { it.amountMinor }?.checkedSum()
            }
            // One unusable category makes the whole donut of this currency unusable: drawing the
            // sound categories alone would show parts that do not add up to the total, which reads
            // as a smaller cost than the real one. The other currencies are unaffected.
            val unusable = ExpenseSlice.entries.any { bySlice[it] != null && amounts[it] == null }
            ExpenseSplit(
                currency = ledger.currency,
                slices = if (unusable) {
                    emptyList()
                } else {
                    amounts.entries.mapNotNull { (slice, amount) ->
                        amount?.takeIf { it > 0 }?.let { slice to it }
                    }
                },
            )
        }.filterNot { it.isEmpty }
}

/**
 * #497 — a negative figure or a sum that would not fit in a Long is **unknown**, never a number
 * that wrapped around. A chart drawn from a wrapped figure is worse than no chart.
 */
internal fun List<Long>.checkedSum(): Long? {
    var total = 0L
    for (value in this) {
        if (value < 0) return null
        total = try {
            Math.addExact(total, value)
        } catch (_: ArithmeticException) {
            return null
        }
    }
    return total
}
