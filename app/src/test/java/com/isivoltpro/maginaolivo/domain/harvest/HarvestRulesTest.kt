package com.isivoltpro.maginaolivo.domain.harvest

import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HarvestRulesTest {
    private val today = LocalDate.of(2026, 11, 20)
    private val north = UUID.fromString("00000000-0000-0000-0000-00000000000a")
    private val south = UUID.fromString("00000000-0000-0000-0000-00000000000b")
    private val east = UUID.fromString("00000000-0000-0000-0000-00000000000c")

    private fun draft(total: Long?, vararg shares: Pair<UUID, Long?>) = HarvestDraft(
        farmId = UUID.randomUUID(),
        harvestDate = today,
        totalGrams = total,
        shares = shares.map { HarvestShareInput(it.first, it.second) },
    )

    @Test
    fun exactWeightsMustAddUpToTheTotalToTheGram() {
        assertNull(HarvestRules.validate(draft(5_000_000, north to 3_000_000, south to 2_000_000), today))
        assertEquals(
            HarvestProblem("parcels", "does_not_reconcile"),
            HarvestRules.validate(draft(5_000_000, north to 3_000_000, south to 1_999_999), today),
        )
        assertEquals(
            HarvestProblem("parcels", "exceeds_total"),
            HarvestRules.validate(draft(5_000_000, north to 3_000_000, south to 2_000_001), today),
        )
    }

    @Test
    fun anUnknownSplitIsValidAndNeedsOnlyTheTotal() {
        assertNull(HarvestRules.validate(draft(5_000_000, north to null, south to null), today))
    }

    @Test
    fun aPartialSplitLeavesSomethingForTheUnknownParcels() {
        assertNull(HarvestRules.validate(draft(5_000_000, north to 3_000_000, south to null), today))
        assertEquals(
            HarvestProblem("parcels", "nothing_left_unallocated"),
            HarvestRules.validate(draft(5_000_000, north to 5_000_000, south to null), today),
        )
    }

    @Test
    fun aHarvestNeedsAPositiveTotalOriginParcelsAndAPastDate() {
        assertEquals(HarvestProblem("totalGrams", "required"), HarvestRules.validate(draft(null, north to null), today))
        assertEquals(HarvestProblem("totalGrams", "not_positive"), HarvestRules.validate(draft(0, north to null), today))
        assertEquals(HarvestProblem("parcels", "empty"), HarvestRules.validate(draft(1_000), today))
        assertEquals(
            HarvestProblem("parcels", "duplicate"),
            HarvestRules.validate(draft(2_000, north to 1_000, north to 1_000), today),
        )
        assertEquals(
            HarvestProblem("parcels", "not_positive"),
            HarvestRules.validate(draft(2_000, north to 0, south to null), today),
        )
        assertEquals(
            HarvestProblem("harvestDate", "future"),
            HarvestRules.validate(draft(1_000, north to 1_000).copy(harvestDate = today.plusDays(1)), today),
        )
        assertEquals(
            HarvestProblem("workerCount", "negative"),
            HarvestRules.validate(draft(1_000, north to 1_000).copy(workerCount = -1), today),
        )
    }

    @Test
    fun theSummaryNeverAttributesUnallocatedKilosToAParcel() {
        val exact = harvest(5_000_000, share(north, "Norte", 3_000_000), share(south, "Sur", 2_000_000))
        val mixed = harvest(4_000_000, share(north, "Norte", null), share(east, "Este", null))
        val partial = harvest(1_500_000, share(east, "Este", 500_000), share(south, "Sur", null))

        val summary = HarvestSummary.of(listOf(exact, mixed, partial))

        assertEquals(3, summary.harvestCount)
        assertEquals(10_500_000L, summary.totalGrams)
        assertEquals(5_000_000L, summary.unallocatedGrams)
        assertEquals(
            listOf(
                ParcelHarvestTotal(east, "Este", 500_000, sharesUnallocated = true),
                ParcelHarvestTotal(north, "Norte", 3_000_000, sharesUnallocated = true),
                ParcelHarvestTotal(south, "Sur", 2_000_000, sharesUnallocated = true),
            ),
            summary.parcels,
        )
        // Nothing is invented: the parcel kilos plus the unallocated kilos are the total.
        assertEquals(summary.totalGrams, summary.parcels.sumOf { it.exactGrams } + summary.unallocatedGrams)
    }

    @Test
    fun theAllocationModeIsReadFromTheRows() {
        assertEquals(
            HarvestAllocationMode.EXACT,
            harvest(1_000, share(north, "Norte", 1_000)).allocationMode,
        )
        assertEquals(
            HarvestAllocationMode.PARTIAL,
            harvest(2_000, share(north, "Norte", 1_000), share(south, "Sur", null)).allocationMode,
        )
        assertEquals(
            HarvestAllocationMode.UNALLOCATED,
            harvest(2_000, share(north, "Norte", null), share(south, "Sur", null)).allocationMode,
        )
    }

    private fun share(id: UUID, name: String, grams: Long?) = HarvestShare(
        parcelId = id,
        parcelName = name,
        allocation = if (grams == null) HarvestAllocation.UNALLOCATED else HarvestAllocation.EXACT,
        weightGrams = grams,
    )

    private fun harvest(total: Long, vararg shares: HarvestShare) = Harvest(
        id = UUID.randomUUID(),
        workspaceId = UUID.randomUUID(),
        farmId = null,
        campaignId = null,
        harvestDate = today,
        totalGrams = total,
        shares = shares.toList(),
        collectionMethod = null,
        workerCount = null,
        machineryText = null,
        notes = null,
        version = 1,
    )
}
