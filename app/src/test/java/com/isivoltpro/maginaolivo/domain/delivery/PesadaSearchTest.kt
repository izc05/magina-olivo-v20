package com.isivoltpro.maginaolivo.domain.delivery

import com.isivoltpro.maginaolivo.domain.harvest.HarvestAllocation
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PesadaSearchTest {
    private val farm = UUID.randomUUID()
    private val north = UUID.randomUUID()
    private val south = UUID.randomUUID()
    private val sanIsidro = UUID.randomUUID()

    private val a = pesada("V-45872", "Coop. San Isidro", 24, 2_000_000, fat = 2_150, organization = sanIsidro)
    private val b = pesada("A-77", "Almazara El Molino", 25, 1_000_000)
    private val c = pesada("45990", "Coop. San Isidro", 27, 3_000_000, organization = sanIsidro, time = LocalTime.of(9, 0))

    @Test
    fun aTicketIsFoundByItsDigitsDaysLater() {
        assertEquals(listOf(a), PesadaSearch.filter(listOf(a, b, c), PesadaQuery(text = "45872")))
        assertEquals(listOf(a), PesadaSearch.filter(listOf(a, b, c), PesadaQuery(text = "#45872")))
        assertEquals(listOf(a), PesadaSearch.filter(listOf(a, b, c), PesadaQuery(text = "v 45872")))
        assertEquals(listOf(c, a), PesadaSearch.filter(listOf(a, b, c), PesadaQuery(text = "45")))
        assertTrue(PesadaSearch.filter(listOf(a, b, c), PesadaQuery(text = "99999")).isEmpty())
    }

    @Test
    fun pendingAndCooperativeFiltersCombineAndNewestComesFirst() {
        assertEquals(listOf(c, b), PesadaSearch.filter(listOf(a, b, c), PesadaQuery(status = YieldStatus.PENDING)))
        assertEquals(listOf(a), PesadaSearch.filter(listOf(a, b, c), PesadaQuery(status = YieldStatus.WITH_YIELD)))
        val coop = PesadaSearch.cooperativeKey(a)
        assertEquals(listOf(c), PesadaSearch.filter(listOf(a, b, c), PesadaQuery(status = YieldStatus.PENDING, cooperative = coop)))
        assertEquals(
            listOf(b),
            PesadaSearch.filter(listOf(a, b, c), PesadaQuery(from = LocalDate.of(2026, 11, 25), to = LocalDate.of(2026, 11, 26))),
        )
        assertEquals(listOf(c, b, a), PesadaSearch.filter(listOf(a, b, c), PesadaQuery()))
    }

    @Test
    fun parcelYieldUsesOnlyKnownKilosAndNeverSpreadsAMixedLoad() {
        val single = pesada("1", "Coop", 24, 2_000_000, fat = 2_000, shares = listOf(share(north, "Norte", HarvestAllocation.EXACT, 2_000_000)))
        val exact = pesada(
            "2", "Coop", 24, 3_000_000, fat = 2_400,
            shares = listOf(share(north, "Norte", HarvestAllocation.EXACT, 1_000_000), share(south, "Sur", HarvestAllocation.EXACT, 2_000_000)),
        )
        val mixed = pesada(
            "3", "Coop", 24, 9_000_000, fat = 1_000,
            shares = listOf(share(north, "Norte", HarvestAllocation.UNALLOCATED, null), share(south, "Sur", HarvestAllocation.UNALLOCATED, null)),
        )
        val pending = pesada("4", "Coop", 24, 1_000_000, shares = listOf(share(south, "Sur", HarvestAllocation.EXACT, 1_000_000)))

        val yields = ParcelYield.of(listOf(single, exact, mixed, pending)).associateBy { it.parcelName }
        // Norte: (2.000 kg × 20,00 + 1.000 kg × 24,00) / 3.000 kg = 21,33 %; the mixed 9.000 kg never enter.
        assertEquals(2_133, yields.getValue("Norte").fatYield!!.hundredths)
        assertEquals(3_000_000L, yields.getValue("Norte").attributedGrams)
        assertEquals(100, yields.getValue("Norte").coveragePercent)
        // Sur: 2.000 kg analysed at 24 %, 1.000 kg still pending → coverage 66 %.
        assertEquals(2_400, yields.getValue("Sur").fatYield!!.hundredths)
        assertEquals(66, yields.getValue("Sur").coveragePercent)
        // Only a mixed load: no Parcel yield at all.
        assertTrue(ParcelYield.of(listOf(mixed)).isEmpty())
        assertNull(ParcelYield.of(listOf(pending)).single().fatYield)
    }

    private fun share(parcel: UUID, name: String, allocation: HarvestAllocation, grams: Long?) =
        DeliveryShare(parcel, name, allocation, grams)

    private fun pesada(
        ticket: String,
        cooperative: String,
        day: Int,
        net: Long,
        fat: Int? = null,
        organization: UUID? = null,
        time: LocalTime? = null,
        shares: List<DeliveryShare> = emptyList(),
    ): Delivery {
        val id = UUID.randomUUID()
        return Delivery(
            id = id, workspaceId = UUID.randomUUID(), farmId = farm, campaignId = UUID.randomUUID(),
            deliveryDate = LocalDate.of(2026, 11, day), destinationOrganizationId = organization,
            destinationName = cooperative, netGrams = net, grossGrams = null, tareGrams = null,
            deliveryNumber = null, ticketNumber = ticket, source = DeliverySource.MANUAL, shares = shares,
            notes = null, version = 1,
            analysis = fat?.let { YieldAnalysis(UUID.randomUUID(), id, null, it, null, null, 1) },
            deliveryTime = time,
        )
    }
}
