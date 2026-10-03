package com.isivoltpro.maginaolivo.feature.expenses

import com.isivoltpro.maginaolivo.data.local.model.CampaignStatus
import com.isivoltpro.maginaolivo.domain.campaign.Campaign
import com.isivoltpro.maginaolivo.domain.ocr.DocumentExtraction
import com.isivoltpro.maginaolivo.domain.ocr.DocumentType
import com.isivoltpro.maginaolivo.domain.ocr.OcrStatus
import com.isivoltpro.maginaolivo.domain.ocr.PurchaseProposal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Owner decision 2026-10-03: explicit «Gasto de recogida» / «Gasto general de finca/parcela». */
class RecollectionChoiceTest {
    private val farm = UUID.randomUUID()
    private val otherFarm = UUID.randomUUID()
    private val running = campaign(farm, "2026/27", CampaignStatus.HARVEST)
    private val closed = campaign(farm, "Campaña 2025/26", CampaignStatus.CLOSED)
    private val options = RelationOptions(campaigns = listOf(closed, running))

    @Test fun runningRecolectionIsPreselectedAndCountsExplicitly() {
        val form = ExpenseForm(date = "2026-11-20", amount = "40", concept = "Gasoil", farmId = farm)
            .withRecollectionPreselected(options)
        assertEquals(running.id, form.campaignId)
        assertEquals(running.id, form.toDraft().first!!.campaignId)
        assertEquals("Gasto de recogida · Campaña 2026/27", "Gasto de recogida · ${options.recollectionCampaignFor(form)!!.choiceLabel()}")
    }

    @Test fun switchingToGeneralLeavesTheExpenseOutsideTheCampaign() {
        val preselected = ExpenseForm(date = "2026-11-20", amount = "40", concept = "Poda", farmId = farm)
            .withRecollectionPreselected(options)
        val general = preselected.copy(campaignId = null)
        assertNull(general.toDraft().first!!.campaignId)
        assertEquals(farm, general.toDraft().first!!.farmId)
        // The choice stays offered so the farmer can switch back.
        assertEquals(running.id, options.recollectionCampaignFor(general)!!.id)
    }

    @Test fun withoutARunningCampaignNothingIsOfferedOrPreselected() {
        val none = RelationOptions(campaigns = listOf(closed, campaign(farm, "2027/28", CampaignStatus.PREPARATION)))
        val form = ExpenseForm(farmId = farm)
        assertNull(form.withRecollectionPreselected(none).campaignId)
        assertNull(none.recollectionCampaignFor(form))
        assertNull(form.withRecollectionPreselected(RelationOptions()).campaignId)
    }

    @Test fun anotherFarmsRecolectionIsNeverOffered() {
        val form = ExpenseForm(farmId = otherFarm)
        assertNull(form.withRecollectionPreselected(options).campaignId)
        assertNull(options.recollectionCampaignFor(form))
        assertNull(options.recollectionCampaignFor(ExpenseForm(farmId = null)))
    }

    @Test fun editingKeepsTheSavedChoiceAndIsNeverReassigned() {
        val savedGeneral = ExpenseForm(farmId = farm, campaignId = null)
        val savedOnClosed = ExpenseForm(farmId = farm, campaignId = closed.id)
        // Preselection is only for a new Cuaderno expense; a saved one keeps what it has.
        assertEquals(closed.id, savedOnClosed.withRecollectionPreselected(options).campaignId)
        assertEquals(closed.id, options.recollectionCampaignFor(savedOnClosed)!!.id)
        // Switching a closed-campaign expense to general and back returns to its own campaign.
        assertEquals(closed.id, options.recollectionCampaignFor(savedOnClosed.copy(campaignId = null), linkedCampaignId = closed.id)!!.id)
        assertNull(savedGeneral.toDraft(requireAmount = false).first?.campaignId)
    }

    @Test fun aJornadaExpenseTakesItsCampaignFromTheDayNotFromTheChoice() {
        val form = ExpenseForm(farmId = farm, harvestId = UUID.randomUUID())
        assertNull(options.recollectionCampaignFor(form))
        assertNull(form.withRecollectionPreselected(options).campaignId)
    }

    @Test fun changingFarmClearsTheCampaignAndDoesNotPreselectTheNewFarm() {
        // The farm picker clears the campaign; the form then holds a new, campaign-free Farm.
        val moved = ExpenseForm(farmId = farm, campaignId = running.id)
            .copy(farmId = otherFarm, parcelId = null, activityId = null, harvestId = null, campaignId = null)
        assertNull(moved.toDraft(requireAmount = false).first?.campaignId)
        assertNull(options.recollectionCampaignFor(moved))
    }

    @Test fun aDocumentReviewKeepsTheFarmAndCampaignItWasTakenFrom() {
        val form = extraction().toReviewForm(farmId = farm, campaignId = running.id)
        assertEquals(farm, form.farmId)
        assertEquals(running.id, form.campaignId)
        val draft = form.copy(concept = "Factura gasoil").toDraft(requireAmount = false).first!!
        assertEquals(farm, draft.farmId)
        assertEquals(running.id, draft.campaignId)
        assertEquals(7_260L, draft.amountMinor)
    }

    @Test fun aDocumentWithoutContextStaysGeneralAndACampaignNeedsItsFarm() {
        assertNull(extraction().toReviewForm().campaignId)
        assertNull(extraction().toReviewForm().farmId)
        assertNull(extraction().toReviewForm(farmId = null, campaignId = running.id).campaignId)
        // From Cuaderno → Gasto the reviewed document starts on the running recolección.
        assertEquals(running.id, extraction().toReviewForm(farmId = farm).withRecollectionPreselected(options).campaignId)
    }

    @Test fun campaignLabelNeverDoublesTheWord() {
        assertEquals("Campaña 2026/27", running.choiceLabel())
        assertEquals("Campaña 2025/26", closed.choiceLabel())
    }

    private fun campaign(farmId: UUID, name: String, status: CampaignStatus) = Campaign(
        id = UUID.randomUUID(), workspaceId = UUID.randomUUID(), farmId = farmId, name = name,
        startDate = LocalDate.of(2026, 10, 1), endDate = null, status = status, notes = null,
        snapshots = emptyList(), version = 1,
    )

    private fun extraction() = DocumentExtraction(
        id = UUID.randomUUID(),
        attachmentId = UUID.randomUUID(),
        documentType = DocumentType.PURCHASE_INVOICE,
        status = OcrStatus.EXTRACTED,
        engine = "test",
        rawText = "…",
        proposal = PurchaseProposal(supplierName = "Gasóleos Mágina", invoiceDate = LocalDate.of(2026, 11, 20), totalMinor = 7_260),
        expenseId = null,
        createdAt = Instant.EPOCH,
        reviewedAt = null,
    )
}
