package com.isivoltpro.maginaolivo.feature.farms

import com.isivoltpro.maginaolivo.data.local.model.CampaignStatus
import com.isivoltpro.maginaolivo.domain.delivery.Delivery
import com.isivoltpro.maginaolivo.domain.delivery.DeliverySource
import com.isivoltpro.maginaolivo.domain.farm.Farm
import com.isivoltpro.maginaolivo.domain.harvest.HarvestContext
import com.isivoltpro.maginaolivo.domain.harvest.Weight
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** #359/#363/#364: Mis fincas figures are canonical sums; unknown is «—», partial is «≥ N». */
class FarmFiguresTest {
    private val estacas = UUID.randomUUID()
    private val salinillas = UUID.randomUUID()
    private val campaign = UUID.randomUUID()
    private val oldCampaign = UUID.randomUUID()

    @Test fun oliveTreesAreSummedAndAPartialSumNeverLooksWhole() {
        assertEquals("—", oliveTreesLabel(emptyList()))
        assertEquals("—", oliveTreesLabel(listOf(farm(estacas, null, false))))
        assertEquals("82", oliveTreesLabel(listOf(farm(estacas, 82, true))))
        assertEquals("1.282", oliveTreesLabel(listOf(farm(estacas, 82, true), farm(salinillas, 1_200, true))))
        assertEquals("≥ 82", oliveTreesLabel(listOf(farm(estacas, 82, true), farm(salinillas, null, false))))
        assertEquals("≥ 82", oliveTreesLabel(listOf(farm(estacas, 82, false))))
    }

    @Test fun campaignKilosComeOnlyFromThePesadasOfTheRunningCampaign() {
        val kilos = runningCampaignKilos(
            listOf(context(salinillas, campaign)),
            listOf(
                delivery(salinillas, campaign, 2_000_000),
                delivery(salinillas, campaign, 1_150_000),
                // An older campaign of the same Farm never adds to this one.
                delivery(salinillas, oldCampaign, 9_000_000),
                // A Farm without a running campaign has no «Kg campaña» at all.
                delivery(estacas, oldCampaign, 500_000),
            ),
        )
        assertEquals(mapOf(salinillas to 3_150_000L), kilos)
        assertEquals(Weight.format(3_150_000), campaignKilosLabel(kilos[salinillas]))
        assertEquals("—", campaignKilosLabel(kilos[estacas]))
    }

    @Test fun anOverflowingRunningCampaignTotalIsUnknownNeverNegative() {
        val kilos = runningCampaignKilos(
            listOf(context(salinillas, campaign)),
            listOf(
                delivery(salinillas, campaign, Long.MAX_VALUE),
                delivery(salinillas, campaign, 1),
            ),
        )
        assertNull(kilos[salinillas])
    }

    @Test fun aRunningCampaignWithoutPesadasSaysSoNotZero() {
        val kilos = runningCampaignKilos(listOf(context(estacas, campaign)), emptyList())
        assertEquals("Sin pesadas", campaignKilosLabel(kilos[estacas]))
    }

    private fun farm(id: UUID, trees: Long?, complete: Boolean) = Farm(
        id, UUID.randomUUID(), "Finca", null, null, null, null, null, 1, null, null, null, 1,
        oliveTreeCount = trees, oliveTreeCountComplete = complete,
    )

    private fun context(farm: UUID, campaign: UUID) =
        HarvestContext(farm, "Finca", campaign, "2026/27", CampaignStatus.HARVEST, LocalDate.of(2026, 10, 1), emptyList())

    private fun delivery(farm: UUID, campaign: UUID, grams: Long) = Delivery(
        id = UUID.randomUUID(), workspaceId = UUID.randomUUID(), farmId = farm, campaignId = campaign,
        deliveryDate = LocalDate.of(2026, 10, 3), destinationOrganizationId = null, destinationName = "Cooperativa",
        netGrams = grams, grossGrams = null, tareGrams = null, deliveryNumber = null, ticketNumber = null,
        source = DeliverySource.entries.first(), shares = emptyList(), notes = null, version = 1,
    )
}
