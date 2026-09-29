package com.isivoltpro.maginaolivo.feature.deliveries

import com.isivoltpro.maginaolivo.domain.organization.Organization
import com.isivoltpro.maginaolivo.domain.delivery.DeliveryShareInput
import com.isivoltpro.maginaolivo.domain.delivery.PesadaOrigin
import com.isivoltpro.maginaolivo.domain.delivery.YieldDraft
import com.isivoltpro.maginaolivo.domain.harvest.HarvestContext
import com.isivoltpro.maginaolivo.domain.harvest.HarvestParcelOption
import com.isivoltpro.maginaolivo.data.local.model.CampaignStatus
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
        origin = PesadaOrigin.TREE,
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
    fun aTicketsHourAndCooperativeFillTheFormOnlyWhenTheyAreClear() {
        // CR-010 note 4: still a proposal the farmer confirms.
        val sanIsidro = Organization(UUID.randomUUID(), "Coop. San Isidro", emptySet())
        val molino = Organization(UUID.randomUUID(), "Almazara El Molino", emptySet())
        val proposal = DeliveryTicketProposal(
            organizationName = "S.C.A. Cooperativa San Isidro",
            deliveryDate = LocalDate.of(2026, 11, 18),
            netGrams = 2_850_000,
            deliveryTime = LocalTime.of(17, 42),
        )
        val form = proposal.toForm(farmId = null, today = today, destinations = listOf(sanIsidro, molino))
        assertEquals(sanIsidro.id, form.destinationOrganizationId)
        assertEquals("", form.destinationText)
        assertEquals("17:42", form.time)
        // No saved match, or more than one: the ticket's text stays typed, to be checked.
        val unknown = proposal.copy(organizationName = "Oleícola Jaén").toForm(null, today, listOf(sanIsidro, molino))
        assertNull(unknown.destinationOrganizationId)
        assertEquals("Oleícola Jaén", unknown.destinationText)
        val twin = Organization(UUID.randomUUID(), "San Isidro Labrador", emptySet())
        assertNull(proposal.toForm(null, today, listOf(sanIsidro, twin)).destinationOrganizationId)
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
    fun theNextPesadaKeepsTheDayAndStartsItsOwnWeighingEmpty() {
        val done = base.copy(ticketNumber = "V-101", time = "9:40", gross = "5.000", tare = "2.150")
        val next = done.nextPesada()
        // CR-010: same Farm and date, so it lands in the same automatic day.
        assertEquals(farm, next.farmId)
        assertEquals("2026-11-18", next.date)
        assertEquals("Cooperativa San Isidro", next.destinationText)
        assertEquals(listOf(north, south), next.parcelIds)
        assertEquals("", next.net)
        assertEquals("", next.ticketNumber)
        assertEquals("", next.time)
        assertEquals("", next.gross)
        // Issue #254: the next load of the day says its own origin (vuelo and suelo differ).
        assertNull(next.origin)
    }

    @Test
    fun everyPesadaSaysWhereItsOlivesWerePicked() {
        val (missing, errors) = base.copy(origin = null).toDraft(today)
        assertNull(missing)
        assertNotNull(errors.origin)
        assertEquals(PesadaOrigin.GROUND, base.copy(origin = PesadaOrigin.GROUND).toDraft(today).first!!.origin)
    }
    @Test
    fun aTicketReadFromAnOpenPesadaKeepsWhatWasTypedAndFillsWhatItRead() {
        val sanIsidro = Organization(UUID.randomUUID(), "Coop. San Isidro", emptySet())
        val typed = base.copy(notes = "Primera del día", splitKnown = true, weights = mapOf(north to "1.000"))
        val merged = typed.withTicket(
            DeliveryTicketProposal(
                organizationName = "S.C.A. San Isidro",
                ticketNumber = "A-17",
                deliveryDate = LocalDate.of(2026, 11, 19),
                netGrams = 3_120_000,
                deliveryTime = LocalTime.of(9, 30),
            ),
            listOf(sanIsidro),
        )
        assertEquals(farm, merged.farmId)
        assertEquals(listOf(north, south), merged.parcelIds)
        assertEquals(PesadaOrigin.TREE, merged.origin)
        assertEquals("Primera del día", merged.notes)
        assertEquals(mapOf(north to "1.000"), merged.weights)
        assertEquals("2026-11-19", merged.date)
        assertEquals(sanIsidro.id, merged.destinationOrganizationId)
        assertEquals("", merged.destinationText)
        assertEquals("A-17", merged.ticketNumber)
        assertEquals("09:30", merged.time)
        assertEquals(3_120_000L, merged.toDraft(today).first!!.netGrams)
    }

    @Test
    fun whatTheTicketDidNotReadStaysAsTyped() {
        val typed = base.copy(gross = "4.000", tare = "1.150", time = "8:15", ticketNumber = "B-2")
        assertEquals(typed, typed.withTicket(DeliveryTicketProposal()))
        assertEquals(typed, typed.withTicket(null))
    }

    /** CR-011 §14: the Cuaderno's Parcel is the Pesada's origin only if it is in that campaign. */
    @Test
    fun theCuadernosParcelIsPreselectedOnlyWhenItIsInThatFarmsCampaign() {
        val contexts = listOf(
            HarvestContext(
                farmId = farm, farmName = "El Cortijo", campaignId = UUID.randomUUID(), campaignName = "2026/27",
                campaignStatus = CampaignStatus.ACTIVE, campaignStart = LocalDate.of(2026, 10, 1),
                parcels = listOf(HarvestParcelOption(north, "Norte")),
            ),
        )
        assertEquals(listOf(north), presetOriginParcels(contexts, farm, north))
        // A Parcel outside the running campaign is never guessed in.
        assertEquals(emptyList<UUID>(), presetOriginParcels(contexts, farm, south))
        // Another Farm, or no Parcel at all: nothing preselected.
        assertEquals(emptyList<UUID>(), presetOriginParcels(contexts, UUID.randomUUID(), north))
        assertEquals(emptyList<UUID>(), presetOriginParcels(contexts, farm, null))
    }
}
