package com.isivoltpro.maginaolivo.domain.ocr

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PurchaseDocumentParserTest {
    @Test
    fun readsAPlainSpanishInvoice() {
        val proposal = PurchaseDocumentParser.parse(
            """
            Suministros Agrícolas Mágina S.L.
            C/ Mayor 4, 23100 Mancha Real
            CIF: B23456789
            Factura nº F-2026-118
            Fecha: 10/03/2026
            Abono NPK 15-15-15   10 sacos
            Base imponible: 1.060,00 €
            IVA 21%: 222,60 €
            TOTAL A PAGAR: 1.282,60 €
            """.trimIndent(),
        )
        assertEquals("Suministros Agrícolas Mágina S.L.", proposal.supplierName)
        assertEquals("B23456789", proposal.supplierTaxId)
        assertEquals("F-2026-118", proposal.invoiceNumber)
        assertEquals(LocalDate.of(2026, 3, 10), proposal.invoiceDate)
        assertEquals(106_000L, proposal.subtotalMinor)
        assertEquals(22_260L, proposal.taxMinor)
        assertEquals(128_260L, proposal.totalMinor)
        assertEquals("EUR", proposal.currency)
        assertTrue(proposal.hasEssentials)
    }

    @Test
    fun aReceiptWithAShortDateAndAPlainTotal() {
        val proposal = PurchaseDocumentParser.parse(
            """
            ESTACION DE SERVICIO EL OLIVAR
            TICKET 004512
            02-09-26 08:14
            GASOLEO B  45,20 L
            TOTAL 61.02
            """.trimIndent(),
        )
        assertEquals(LocalDate.of(2026, 9, 2), proposal.invoiceDate)
        assertEquals(6_102L, proposal.totalMinor)
        assertEquals("004512", proposal.invoiceNumber)
        assertNull(proposal.currency)
    }

    @Test
    fun nothingIsGuessedFromTextThatDoesNotSayIt() {
        val proposal = PurchaseDocumentParser.parse("Parte de trabajo\nPoda en la parcela 12\n45 olivos")
        assertNull(proposal.totalMinor)
        assertNull(proposal.invoiceDate)
        assertNull(proposal.invoiceNumber)
        assertNull(proposal.supplierTaxId)
        assertFalse(proposal.hasEssentials)
    }

    @Test
    fun anImpossibleDateIsIgnored() {
        val proposal = PurchaseDocumentParser.parse("Fecha: 31/02/2026\nTotal: 10,00 €")
        assertNull(proposal.invoiceDate)
        assertEquals(1_000L, proposal.totalMinor)
    }
}
