package com.isivoltpro.maginaolivo.domain.delivery

import com.isivoltpro.maginaolivo.domain.harvest.HarvestAllocation
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeliveryRulesTest {
    private val today = LocalDate.of(2026, 12, 2)
    private val north = UUID.fromString("00000000-0000-0000-0000-00000000000a")
    private val south = UUID.fromString("00000000-0000-0000-0000-00000000000b")

    private fun draft(net: Long?, vararg shares: Pair<UUID, Long?>) = DeliveryDraft(
        farmId = UUID.randomUUID(),
        deliveryDate = today,
        destinationOrganizationId = null,
        destinationName = "Cooperativa San Isidro",
        netGrams = net,
        shares = shares.map { DeliveryShareInput(it.first, it.second) },
    )

    @Test
    fun aMixedLoadWithoutAKnownSplitIsValid() {
        assertNull(DeliveryRules.validate(draft(2_850_000, north to null, south to null), today))
    }

    @Test
    fun exactParcelKilosMustAddUpToTheDeliveredKilos() {
        assertNull(DeliveryRules.validate(draft(2_850_000, north to 2_000_000, south to 850_000), today))
        assertEquals(
            DeliveryProblem("parcels", "does_not_reconcile"),
            DeliveryRules.validate(draft(2_850_000, north to 2_000_000, south to 800_000), today),
        )
    }

    @Test
    fun grossTareAndNetMustAgreeWhenAllThreeAreGiven() {
        val ok = draft(2_850_000, north to null).copy(grossGrams = 12_340_000, tareGrams = 9_490_000)
        assertNull(DeliveryRules.validate(ok, today))
        assertEquals(
            DeliveryProblem("netGrams", "gross_tare_mismatch"),
            DeliveryRules.validate(ok.copy(tareGrams = 9_500_000), today),
        )
    }

    @Test
    fun aDeliveryNeedsKilosADestinationAParcelAndAPastDate() {
        assertEquals(DeliveryProblem("netGrams", "required"), DeliveryRules.validate(draft(null, north to null), today))
        assertEquals(DeliveryProblem("parcels", "empty"), DeliveryRules.validate(draft(1_000), today))
        assertEquals(
            DeliveryProblem("destination", "required"),
            DeliveryRules.validate(draft(1_000, north to null).copy(destinationName = " "), today),
        )
        assertEquals(
            DeliveryProblem("deliveryDate", "future"),
            DeliveryRules.validate(draft(1_000, north to null).copy(deliveryDate = today.plusDays(1)), today),
        )
    }

    @Test
    fun yieldIsReadAsHundredthsOfAPercent() {
        assertEquals(2_135, Percent.parseHundredths("21,35"))
        assertEquals(2_135, Percent.parseHundredths("21.35 %"))
        assertEquals(1_800, Percent.parseHundredths("18"))
        assertNull(Percent.parseHundredths("veinte"))
        assertNull(Percent.parseHundredths("120"))
        assertNull(Percent.parseHundredths("21,355"))
        assertEquals("21,35 %", Percent.format(2_135))
        assertEquals("18", Percent.editable(1_800))
    }

    @Test
    fun yieldIsWeightedByDeliveredKilosAndShowsItsCoverage() {
        val summary = DeliverySummary.of(
            listOf(
                delivery(1_000_000, fat = 2_000, industrial = 1_600),
                delivery(3_000_000, fat = 2_400, industrial = null),
                delivery(2_000_000, fat = null, industrial = null),
            ),
        )
        assertEquals(6_000_000L, summary.deliveredGrams)
        // (1000 × 20 % + 3000 × 24 %) / 4000 = 23 %; the unanalysed 2000 kg are not in it.
        assertEquals(WeightedYield(2_300, 4_000_000), summary.fatYield)
        assertEquals(66, summary.coveragePercent(summary.fatYield))
        assertEquals(WeightedYield(1_600, 1_000_000), summary.industrialYield)
        assertEquals(16, summary.coveragePercent(summary.industrialYield))
    }

    @Test
    fun withoutAnyAnalysisThereIsNoYieldNotAZeroYield() {
        val summary = DeliverySummary.of(listOf(delivery(1_000_000, fat = null, industrial = null)))
        assertNull(summary.fatYield)
        assertEquals(0, summary.coveragePercent(summary.fatYield))
    }

    @Test
    fun yieldAnalysesAreChecked() {
        assertEquals(DeliveryProblem("yield", "required"), YieldRules.validate(YieldDraft(today, null, null), today))
        assertEquals(DeliveryProblem("yield", "out_of_range"), YieldRules.validate(YieldDraft(today, 0, null), today))
        assertEquals(DeliveryProblem("analysisDate", "future"), YieldRules.validate(YieldDraft(today.plusDays(1), 2_000, null), today))
        assertNull(YieldRules.validate(YieldDraft(null, 2_000, null), today))
    }

    @Test
    fun unallocatedKilosAreWhatNoParcelIsKnownToHaveSent() {
        val delivery = delivery(2_850_000, null, null).copy(
            shares = listOf(
                DeliveryShare(north, "Norte", HarvestAllocation.EXACT, 2_000_000),
                DeliveryShare(south, "Sur", HarvestAllocation.UNALLOCATED, null),
            ),
        )
        assertEquals(850_000L, delivery.unallocatedGrams)
    }

    private fun delivery(net: Long, fat: Int?, industrial: Int?): Delivery {
        val id = UUID.randomUUID()
        return Delivery(
            id = id,
            workspaceId = UUID.randomUUID(),
            farmId = UUID.randomUUID(),
            campaignId = UUID.randomUUID(),
            deliveryDate = today,
            destinationOrganizationId = null,
            destinationName = "Cooperativa",
            netGrams = net,
            grossGrams = null,
            tareGrams = null,
            deliveryNumber = null,
            ticketNumber = null,
            source = DeliverySource.MANUAL,
            shares = emptyList(),
            notes = null,
            version = 1,
            analysis = if (fat == null && industrial == null) null else YieldAnalysis(UUID.randomUUID(), id, today, fat, industrial, null, 1),
        )
    }
}
