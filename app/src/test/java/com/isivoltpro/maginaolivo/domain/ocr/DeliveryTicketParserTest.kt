package com.isivoltpro.maginaolivo.domain.ocr

import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeliveryTicketParserTest {
    @Test
    fun readsAPlainWeightTicket() {
        val proposal = DeliveryTicketParser.parse(
            """
            S.C.A. Cooperativa San Isidro
            Ticket nº 004512
            Fecha: 18/11/2026  Hora 17:42
            Socio: 1187
            Matrícula: 1234 BCD
            Peso bruto: 12.340 kg
            Tara: 9.490 kg
            Peso neto: 2.850 kg
            """.trimIndent(),
        )
        assertEquals("S.C.A. Cooperativa San Isidro", proposal.organizationName)
        assertEquals("004512", proposal.ticketNumber)
        assertEquals(LocalDate.of(2026, 11, 18), proposal.deliveryDate)
        assertEquals(12_340_000L, proposal.grossGrams)
        assertEquals(9_490_000L, proposal.tareGrams)
        assertEquals(2_850_000L, proposal.netGrams)
        assertEquals("1187", proposal.memberReference)
        assertEquals(LocalTime.of(17, 42), proposal.deliveryTime)
        assertTrue(proposal.hasEssentials)
        assertFalse(proposal.weightsDisagree)
    }

    @Test
    fun weightsOnOneLineAreReadByTheirLabels() {
        val proposal = DeliveryTicketParser.parse("Almazara El Molino\n02-12-26\nBRUTO 8.100 TARA 6.000 NETO 2.100")
        assertEquals(8_100_000L, proposal.grossGrams)
        assertEquals(6_000_000L, proposal.tareGrams)
        assertEquals(2_100_000L, proposal.netGrams)
        assertEquals(LocalDate.of(2026, 12, 2), proposal.deliveryDate)
    }

    @Test
    fun aMissingNetIsNeverComputedFromGrossAndTare() {
        val proposal = DeliveryTicketParser.parse("Cooperativa\nFecha 18/11/2026\nBruto: 12.340 kg\nTara: 9.490 kg")
        assertNull(proposal.netGrams)
        assertFalse(proposal.hasEssentials)
    }

    @Test
    fun disagreeingWeightsAreFlaggedNotFixed() {
        val proposal = DeliveryTicketParser.parse("Bruto 12.340\nTara 9.490\nNeto 2.800\n18/11/2026")
        assertEquals(2_800_000L, proposal.netGrams)
        assertTrue(proposal.weightsDisagree)
    }

    @Test
    fun unreadableTextProposesNothing() {
        val proposal = DeliveryTicketParser.parse("~~ ## ..")
        assertEquals(DeliveryTicketProposal(), proposal)
    }

    @Test
    fun anHourIsReadOnlyWhereTheTicketSaysSo() {
        // CR-010 note 4: beside the date, or after «hora»; never guessed from a weight.
        assertEquals(LocalTime.of(9, 5), DeliveryTicketParser.parse("Coop. San Isidro\n18/11/2026 09:05\nNeto 2.850").deliveryTime)
        assertEquals(LocalTime.of(13, 30), DeliveryTicketParser.parse("Fecha 18/11/2026\nHora: 13.30\nNeto 2.850").deliveryTime)
        assertNull(DeliveryTicketParser.parse("Fecha 18/11/2026\nNeto 2.850\nTara 9.490").deliveryTime)
        assertNull(DeliveryTicketParser.parse("Neto 12:45 kg").deliveryTime)
    }
}
