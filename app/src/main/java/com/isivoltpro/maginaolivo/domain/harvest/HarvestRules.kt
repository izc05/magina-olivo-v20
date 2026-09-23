package com.isivoltpro.maginaolivo.domain.harvest

import com.isivoltpro.maginaolivo.domain.production.ParcelSplit
import java.time.LocalDate
import java.util.UUID

/** How a Harvest's kilos are spread over its origin Parcels. */
enum class HarvestAllocation {
    /** This Parcel's kilos are known. */
    EXACT,

    /** This Parcel contributed an unknown share. Never read as zero. */
    UNALLOCATED,
}

/** The whole Harvest, derived from its rows. */
enum class HarvestAllocationMode {
    /** Every origin Parcel has its kilos, and they add up to the total. */
    EXACT,

    /** Some Parcels have their kilos; the rest of the total is shared among the others. */
    PARTIAL,

    /** "No conozco el reparto exacto": only the total is known. */
    UNALLOCATED,
}

enum class CollectionMethod {
    MANUAL,
    TRUNK_SHAKER,
    UMBRELLA_SHAKER,
    STRADDLE_HARVESTER,
    OTHER,
}

data class HarvestShare(
    val parcelId: UUID,
    val parcelName: String,
    val allocation: HarvestAllocation,
    val weightGrams: Long?,
)

data class Harvest(
    val id: UUID,
    val workspaceId: UUID,
    val farmId: UUID?,
    val campaignId: UUID?,
    val harvestDate: LocalDate,
    val totalGrams: Long,
    val shares: List<HarvestShare>,
    val collectionMethod: CollectionMethod?,
    val workerCount: Int?,
    val machineryText: String?,
    val notes: String?,
    val version: Long,
    val farmName: String? = null,
    val campaignName: String? = null,
    /** False once its Campaign is closed: the Harvest is then history, read-only. */
    val editable: Boolean = true,
) {
    val allocatedGrams: Long get() = shares.sumOf { if (it.allocation == HarvestAllocation.EXACT) it.weightGrams ?: 0 else 0 }

    val unallocatedGrams: Long get() = totalGrams - allocatedGrams

    val allocationMode: HarvestAllocationMode
        get() = when {
            shares.isNotEmpty() && shares.all { it.allocation == HarvestAllocation.EXACT } -> HarvestAllocationMode.EXACT
            shares.any { it.allocation == HarvestAllocation.EXACT } -> HarvestAllocationMode.PARTIAL
            else -> HarvestAllocationMode.UNALLOCATED
        }
}

/**
 * One origin Parcel as the form sends it. `weightGrams == null` means "I do not know
 * this Parcel's kilos" — it becomes an `UNALLOCATED` row, never zero.
 */
data class HarvestShareInput(val parcelId: UUID, val weightGrams: Long?)

data class HarvestDraft(
    val farmId: UUID,
    val harvestDate: LocalDate,
    val totalGrams: Long?,
    val shares: List<HarvestShareInput>,
    val collectionMethod: CollectionMethod? = null,
    val workerCount: Int? = null,
    val machineryText: String? = null,
    val notes: String? = null,
)

data class HarvestProblem(val field: String, val code: String)

/**
 * The Gate 13 rules, in one place so the form and the repository cannot disagree.
 *
 * `DATA-MODEL-RC1-FUTURE` §11: exact parcel weights must reconcile with the total, and an
 * unknown distribution stays `UNALLOCATED`. Reconciliation is exact, in grams: a
 * tolerance would be a small fabricated split.
 */
object HarvestRules {
    fun validate(draft: HarvestDraft, today: LocalDate): HarvestProblem? {
        val total = draft.totalGrams ?: return HarvestProblem("totalGrams", "required")
        if (total <= 0) return HarvestProblem("totalGrams", "not_positive")
        if (draft.harvestDate.isAfter(today)) return HarvestProblem("harvestDate", "future")
        if (draft.shares.isEmpty()) return HarvestProblem("parcels", "empty")
        if (draft.shares.map { it.parcelId }.toSet().size != draft.shares.size) {
            return HarvestProblem("parcels", "duplicate")
        }
        if ((draft.workerCount ?: 0) < 0) return HarvestProblem("workerCount", "negative")
        return ParcelSplit.problem(total, draft.shares.map { it.weightGrams })?.let { HarvestProblem("parcels", it) }
    }
}

/** What one Parcel is known to have produced across several Harvests. */
data class ParcelHarvestTotal(
    val parcelId: UUID,
    val parcelName: String,
    val exactGrams: Long,
    /** The Parcel also took part in Harvests whose split is unknown. */
    val sharesUnallocated: Boolean,
)

/**
 * Truthful totals: the harvested kilos, the part known per Parcel, and the part that is
 * known only in total. `total == Σ exact + unallocated` always holds, and no share of
 * `unallocated` is ever attributed to a Parcel.
 */
data class HarvestSummary(
    val harvestCount: Int,
    val totalGrams: Long,
    val unallocatedGrams: Long,
    val parcels: List<ParcelHarvestTotal>,
) {
    companion object {
        fun of(harvests: List<Harvest>): HarvestSummary {
            val byParcel = linkedMapOf<UUID, ParcelHarvestTotal>()
            harvests.forEach { harvest ->
                harvest.shares.forEach { share ->
                    val current = byParcel[share.parcelId]
                        ?: ParcelHarvestTotal(share.parcelId, share.parcelName, 0, false)
                    byParcel[share.parcelId] = when (share.allocation) {
                        HarvestAllocation.EXACT -> current.copy(exactGrams = current.exactGrams + (share.weightGrams ?: 0))
                        HarvestAllocation.UNALLOCATED -> current.copy(sharesUnallocated = true)
                    }
                }
            }
            return HarvestSummary(
                harvestCount = harvests.size,
                totalGrams = harvests.sumOf { it.totalGrams },
                unallocatedGrams = harvests.sumOf { it.unallocatedGrams },
                parcels = byParcel.values.sortedBy { it.parcelName.lowercase() },
            )
        }
    }
}
