package com.isivoltpro.maginaolivo.feature.harvests

import com.isivoltpro.maginaolivo.domain.harvest.Harvest
import com.isivoltpro.maginaolivo.domain.harvest.HarvestAllocation
import com.isivoltpro.maginaolivo.domain.harvest.HarvestShare
import com.isivoltpro.maginaolivo.domain.harvest.HarvestSummary
import com.isivoltpro.maginaolivo.domain.harvest.Weight
import com.isivoltpro.maginaolivo.feature.expenses.DATE_FORMAT
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** #366 — the device case: Estacas and Salinillas both harvest on 3 oct 2026. */
class RecoleccionCopyTest {
    private val estacas = UUID.randomUUID()
    private val salinillas = UUID.randomUUID()
    private val oct3 = LocalDate.of(2026, 10, 3)
    private val p596 = UUID.randomUUID()
    private val p753 = UUID.randomUUID()

    private val estacasDay = day(estacas, "Estacas", oct3, 0, emptyList())
    private val salinillasDay = day(
        salinillas, "Salinillas", oct3, 3_150_000,
        listOf(
            HarvestShare(p596, "Pol. 15 · Parc. 596", HarvestAllocation.UNALLOCATED, null),
            HarvestShare(p753, "Pol. 15 · Parc. 753", HarvestAllocation.UNALLOCATED, null),
        ),
    )

    @Test fun twoFarmsOnTheSameDateAreOneDayNotTwo() {
        assertEquals(1, harvestDayCount(listOf(estacasDay, salinillasDay)))
        assertEquals("En 2 fincas", harvestDaysSpread(listOf(estacasDay, salinillasDay)))
        assertEquals(2, harvestDayCount(listOf(estacasDay, salinillasDay.copy(harvestDate = oct3.plusDays(1)))))
        assertEquals("1 día", harvestDays(1))
        assertEquals("2 días", harvestDays(2))
    }

    @Test fun oneFarmNeedsNoSpreadLine() {
        assertNull(harvestDaysSpread(listOf(salinillasDay, salinillasDay.copy(id = UUID.randomUUID(), harvestDate = oct3.plusDays(1)))))
    }

    @Test fun eachDayRowSaysItsFarm() {
        val date = DATE_FORMAT.format(oct3)
        assertEquals("Estacas · $date", harvestRowTitle(estacasDay))
        assertEquals("Salinillas · $date", harvestRowTitle(salinillasDay))
        assertEquals("$date · Día de recolección", harvestRowTitle(estacasDay.copy(farmName = null)))
    }

    @Test fun aCampaignWithoutPesadasSaysSoInsteadOfAPendingFigure() {
        assertEquals(NO_PESADAS_YET, campaignHarvestHeadline(HarvestSummary.of(listOf(estacasDay))))
        assertEquals(Weight.format(3_150_000), campaignHarvestHeadline(HarvestSummary.of(listOf(salinillasDay))))
    }

    @Test fun unsplitKilosAreSaidPlainlyAndNeverShared() {
        val summary = HarvestSummary.of(listOf(salinillasDay))
        assertEquals(listOf("Sin kg asignados", "Sin kg asignados"), summary.parcels.map(::parcelHarvestText))
        assertEquals("${Weight.format(3_150_000)} pendientes de repartir entre 2 parcelas", unallocatedLine(summary))
        // Fully split: no pending line.
        val split = salinillasDay.copy(
            shares = listOf(
                HarvestShare(p596, "Pol. 15 · Parc. 596", HarvestAllocation.EXACT, 2_000_000),
                HarvestShare(p753, "Pol. 15 · Parc. 753", HarvestAllocation.EXACT, 1_150_000),
            ),
        )
        val splitSummary = HarvestSummary.of(listOf(split))
        assertNull(unallocatedLine(splitSummary))
        assertEquals(listOf(Weight.format(2_000_000), Weight.format(1_150_000)), splitSummary.parcels.map(::parcelHarvestText))
    }

    private fun day(farm: UUID, name: String, date: LocalDate, grams: Long, shares: List<HarvestShare>) = Harvest(
        id = UUID.randomUUID(), workspaceId = UUID.randomUUID(), farmId = farm, campaignId = UUID.randomUUID(),
        harvestDate = date, totalGrams = grams, shares = shares, collectionMethod = null, workerCount = null,
        machineryText = null, notes = null, version = 1, farmName = name, campaignName = "Campaña 2026-2027",
    )
}
