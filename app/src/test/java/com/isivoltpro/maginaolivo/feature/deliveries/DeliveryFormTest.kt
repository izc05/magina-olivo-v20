package com.isivoltpro.maginaolivo.feature.deliveries

import com.isivoltpro.maginaolivo.domain.delivery.DeliveryShareInput
import com.isivoltpro.maginaolivo.domain.delivery.YieldDraft
import com.isivoltpro.maginaolivo.domain.ocr.DeliveryTicketProposal
import java.time.LocalDate
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
}
