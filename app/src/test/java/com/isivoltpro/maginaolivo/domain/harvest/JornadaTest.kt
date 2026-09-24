package com.isivoltpro.maginaolivo.domain.harvest

import com.isivoltpro.maginaolivo.domain.delivery.Delivery
import com.isivoltpro.maginaolivo.domain.delivery.DeliverySource
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JornadaTest {
    private val day = LocalDate.of(2026, 11, 24)
    private val farm = UUID.randomUUID()
    private val campaign = UUID.randomUUID()
    private val harvest = Harvest(
        id = UUID.randomUUID(), workspaceId = UUID.randomUUID(), farmId = farm, campaignId = campaign,
        harvestDate = day, totalGrams = 5_430_000, shares = emptyList(), collectionMethod = null,
        workerCount = 5, machineryText = null, notes = null, version = 3,
    )

    @Test
    fun onlyItsOwnPesadasCountInWeighingOrder() {
        val pesadas = listOf(
            pesada(1_479_500, "Coop. San Isidro", LocalTime.of(17, 20), harvest.id),
            pesada(999_000, "Coop. San Isidro", LocalTime.of(8, 0), UUID.randomUUID()),
            pesada(1_850_500, "Almazara El Molino", null, harvest.id),
            pesada(2_100_000, "Coop. San Isidro", LocalTime.of(9, 40), harvest.id),
            pesada(500_000, "Sin jornada", LocalTime.of(10, 0), null),
        )
        val jornada = Jornada.of(harvest, pesadas)
        assertTrue(jornada.kilosFromPesadas)
        assertEquals(5_430_000L, jornada.pesadaGrams)
        // An hour-less Pesada goes last in its day; the cooperative of each one is kept.
        assertEquals(listOf(2_100_000L, 1_479_500L, 1_850_500L), jornada.pesadas.map { it.netGrams })
        assertEquals(listOf("Coop. San Isidro", "Almazara El Molino"), jornada.destinations)
    }

    @Test
    fun aJornadaWithoutPesadasSaysSo() {
        val jornada = Jornada.of(harvest, emptyList())
        assertFalse(jornada.kilosFromPesadas)
        assertEquals(0L, jornada.pesadaGrams)
    }

    private fun pesada(net: Long, destination: String, time: LocalTime?, harvestId: UUID?) = Delivery(
        id = UUID.randomUUID(), workspaceId = harvest.workspaceId, farmId = farm, campaignId = campaign,
        deliveryDate = day, destinationOrganizationId = null, destinationName = destination, netGrams = net,
        grossGrams = null, tareGrams = null, deliveryNumber = null, ticketNumber = null,
        source = DeliverySource.MANUAL, shares = emptyList(), notes = null, version = 1,
        harvestId = harvestId, deliveryTime = time,
    )
}
