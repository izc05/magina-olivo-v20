package com.isivoltpro.maginaolivo.feature.deliveries

import com.isivoltpro.maginaolivo.domain.delivery.DeliveryShareInput
import com.isivoltpro.maginaolivo.domain.delivery.YieldDraft
import com.isivoltpro.maginaolivo.domain.ocr.DeliveryTicketProposal
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DeliveryFormTest {
    private val today = LocalDate.of(2026, 12, 2)
    private val farm = UUID.fromString("00000000-0000-0000-0000-0000000000f1")
    private val north = UUID.fromString("00000000-0000-0000-0000-00000000000a")
    private val south = UUID.fromString("00000000-0000-0000-0000-00000000000b")

    private val base = DeliveryForm(
        farmId = farm,
        date = "2026-11-18",
        destinationText = "Cooperativa San Isidro",
        net = "2.850",
        parcelIds = listOf(north, south),
    )

    @Test
    fun aMixedLoadIsTheDefaultAndInventsNoParcelKilos() {
        val (draft, _) = base.toDraft(today)
        assertEquals(listOf(DeliveryShareInput(north, null), DeliveryShareInput(south, null)), draft!!.shares)
        assertEquals(2_850_000L, draft.netGrams)
    }

    @Test
    fun theTicketsWeightsMustAgree() {
        val (draft, errors) = base.copy(gross = "12.340", tare = "9.500").toDraft(today)
        assertNull(draft)
        assertNotNull(errors.net)
        assertNotNull(base.copy(gross = "12.340", tare = "9.490").toDraft(today).first)
    }

    @Test
    fun aTicketProposalOnlyFillsWhatTheTicketSaid() {
        val form = DeliveryTicketProposal(
            organizationName = "Almazara El Molino",
            ticketNumber = "004512",
            deliveryDate = LocalDate.of(2026, 11, 18),
            netGrams = 2_850_000,
        ).toForm(farmId = null, today = today)
        assertEquals("2026-11-18", form.date)
        assertEquals("2850", form.net)
        assertEquals("", form.gross)
        assertEquals("004512", form.ticketNumber)
        assertEquals(emptyList<UUID>(), form.parcelIds)
        // Nothing is saved until the person has chosen the Farm and the Parcels.
        val (draft, errors) = form.toDraft(today)
        assertNull(draft)
        assertNotNull(errors.farm)
    }

    @Test
    fun yieldIsOptionalPerFigureButNotBoth() {
        assertEquals(YieldDraft(null, 2_150, null), YieldForm(fat = "21,5").toDraft(today).first)
        assertNotNull(YieldForm().toDraft(today).second.fat)
        assertNotNull(YieldForm(fat = "veinte").toDraft(today).second.fat)
    }

    @Test
    fun theHourIsOptionalAndReadAsAFarmerWritesIt() {
        assertEquals(LocalTime.of(9, 30), parseHour("9:30"))
        assertEquals(LocalTime.of(9, 30), parseHour("09.30"))
        assertEquals(LocalTime.of(17, 5), parseHour("17h05"))
        assertNull(parseHour("25:00"))
        assertNull(parseHour("mañana"))
        assertNull(base.toDraft(today).first!!.deliveryTime)
        assertEquals(LocalTime.of(13, 5), base.copy(time = "13:05").toDraft(today).first!!.deliveryTime)
        val (draft, errors) = base.copy(time = "a las tres").toDraft(today)
        assertNull(draft)
        assertNotNull(errors.time)
    }

    @Test
    fun aJornadaIsOnlyLinkedWhenChosen() {
        val jornada = UUID.fromString("00000000-0000-0000-0000-0000000000c1")
        assertNull(base.toDraft(today).first!!.harvestId)
        assertEquals(jornada, base.copy(harvestId = jornada).toDraft(today).first!!.harvestId)
        val opened = base.copy(harvestId = jornada, newJornada = true).toDraft(today).first!!
        assertEquals(true, opened.newJornada)
        assertNull(opened.harvestId)
    }

    @Test
    fun theNextPesadaKeepsTheDayAndStartsItsOwnWeighingEmpty() {
        val jornada = UUID.fromString("00000000-0000-0000-0000-0000000000c2")
        val done = base.copy(ticketNumber = "V-101", time = "9:40", gross = "5.000", tare = "2.150", newJornada = true)
        val next = done.nextPesada(jornada)
        assertEquals(farm, next.farmId)
        assertEquals("2026-11-18", next.date)
        assertEquals("Cooperativa San Isidro", next.destinationText)
        assertEquals(listOf(north, south), next.parcelIds)
        assertEquals(jornada, next.harvestId)
        assertEquals(false, next.newJornada)
        assertEquals("", next.net)
        assertEquals("", next.ticketNumber)
        assertEquals("", next.time)
        assertEquals("", next.gross)
    }
}
