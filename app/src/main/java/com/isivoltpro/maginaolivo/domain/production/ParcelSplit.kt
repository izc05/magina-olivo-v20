package com.isivoltpro.maginaolivo.domain.production

/**
 * How kilos are spread over origin Parcels — the same rule for a Harvest
 * (`DATA-MODEL-RC1-FUTURE` §11) and a Delivery (`DATA-MODEL-RC1.1-ADDENDUM` §9).
 *
 * `weights` has one entry per origin Parcel; null means "this Parcel's kilos are not
 * known". Exact kilos must reconcile with the total to the gram, and Parcels marked
 * unknown must still share something: a split is never invented to fill the gaps.
 */
object ParcelSplit {
    /** The problem code, or null when the split is truthful. */
    fun problem(totalGrams: Long, weights: List<Long?>): String? {
        if (weights.isEmpty()) return "empty"
        if (weights.any { (it ?: 1) <= 0 }) return "not_positive"
        val exact = weights.filterNotNull()
        val allocated = exact.sum()
        val someUnknown = exact.size < weights.size
        return when {
            allocated > totalGrams -> "exceeds_total"
            !someUnknown && allocated != totalGrams -> "does_not_reconcile"
            someUnknown && allocated == totalGrams -> "nothing_left_unallocated"
            else -> null
        }
    }
}
