package com.isivoltpro.maginaolivo.feature.harvests

import com.isivoltpro.maginaolivo.domain.harvest.HarvestShareInput
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class HarvestFormTest {
    private val today = LocalDate.of(2026, 11, 20)
    private val farm = UUID.fromString("00000000-0000-0000-0000-0000000000f1")
    private val north = UUID.fromString("00000000-0000-0000-0000-00000000000a")
    private val south = UUID.fromString("00000000-0000-0000-0000-00000000000b")

    private val base = HarvestForm(farmId = farm, date = "2026-11-18", total = "5.000")

    @Test
    fun oneOriginParcelCarriesTheWholeTotal() {
        val (draft, errors) = base.copy(parcelIds = listOf(north)).toDraft(today)
        assertEquals(true, errors.isEmpty)
        assertEquals(listOf(HarvestShareInput(north, 5_000_000)), draft!!.shares)
    }

    @Test
    fun theDefaultForSeveralParcelsIsTheUnknownSplit() {
        val (draft, _) = base.copy(parcelIds = listOf(north, south)).toDraft(today)
        assertEquals(listOf(HarvestShareInput(north, null), HarvestShareInput(south, null)), draft!!.shares)
    }

    @Test
    fun typedKilosAreKeptAndBlankParcelsStayUnknown() {
        val form = base.copy(parcelIds = listOf(north, south), splitKnown = true, weights = mapOf(north to "3.000"))
        val (draft, _) = form.toDraft(today)
        assertEquals(listOf(HarvestShareInput(north, 3_000_000), HarvestShareInput(south, null)), draft!!.shares)
        assertEquals(AllocationPreview(5_000_000, 3_000_000, 1), form.preview())
    }

    @Test
    fun aSplitThatDoesNotAddUpIsExplainedNotSaved() {
        val form = base.copy(
            parcelIds = listOf(north, south),
            splitKnown = true,
            weights = mapOf(north to "3000", south to "1999"),
        )
        val (draft, errors) = form.toDraft(today)
        assertNull(draft)
        assertNotNull(errors.parcels)
    }

    @Test
    fun unreadableFiguresAreErrorsNeverZero() {
        val (draft, errors) = base.copy(total = "unos cinco mil", parcelIds = listOf(north), workers = "tres")
            .toDraft(today)
        assertNull(draft)
        assertNotNull(errors.total)
        assertNotNull(errors.workers)
        val (_, missing) = HarvestForm(date = "ayer").toDraft(today)
        assertNotNull(missing.farm)
        assertNotNull(missing.date)
        assertNotNull(missing.total)
    }

    @Test
    fun aFutureHarvestIsRefused() {
        val (draft, errors) = base.copy(date = "2026-11-21", parcelIds = listOf(north)).toDraft(today)
        assertNull(draft)
        assertNotNull(errors.date)
    }
}
