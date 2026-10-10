package com.isivoltpro.maginaolivo

import androidx.test.platform.app.InstrumentationRegistry
import com.isivoltpro.maginaolivo.app.AppCompositionRoot
import com.isivoltpro.maginaolivo.app.LocalPersistence
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.domain.activity.ActivityType
import com.isivoltpro.maginaolivo.domain.activity.NewActivity
import com.isivoltpro.maginaolivo.domain.analytics.FarmOverview
import com.isivoltpro.maginaolivo.domain.campaign.NewCampaign
import com.isivoltpro.maginaolivo.domain.delivery.DeliveryDraft
import com.isivoltpro.maginaolivo.domain.delivery.DeliveryShareInput
import com.isivoltpro.maginaolivo.domain.delivery.DeliverySummary
import com.isivoltpro.maginaolivo.domain.delivery.PesadaOrigin
import com.isivoltpro.maginaolivo.domain.delivery.YieldDraft
import com.isivoltpro.maginaolivo.domain.equipment.EquipmentDraftLine
import com.isivoltpro.maginaolivo.domain.equipment.EquipmentPriceSnapshot
import com.isivoltpro.maginaolivo.domain.equipment.EquipmentType
import com.isivoltpro.maginaolivo.domain.expense.ExpenseCategory
import com.isivoltpro.maginaolivo.domain.expense.ExpenseDraft
import com.isivoltpro.maginaolivo.domain.expense.ExpenseOrigin
import com.isivoltpro.maginaolivo.domain.expense.RecollectionBucket
import com.isivoltpro.maginaolivo.domain.expense.RecollectionCostCompleteness
import com.isivoltpro.maginaolivo.domain.expense.RecollectionLedger
import com.isivoltpro.maginaolivo.domain.farm.NewFarm
import com.isivoltpro.maginaolivo.domain.harvest.HarvestAllocation
import com.isivoltpro.maginaolivo.domain.labour.CrewDraft
import com.isivoltpro.maginaolivo.domain.labour.LabourLedgerAllocation
import com.isivoltpro.maginaolivo.domain.labour.LabourPayment
import com.isivoltpro.maginaolivo.domain.labour.LabourPaymentState
import com.isivoltpro.maginaolivo.domain.labour.LabourRateBasis
import com.isivoltpro.maginaolivo.domain.labour.LabourRateSnapshot
import com.isivoltpro.maginaolivo.domain.labour.LabourSettlement
import com.isivoltpro.maginaolivo.domain.labour.LabourUnit
import com.isivoltpro.maginaolivo.domain.parcel.NewParcel
import com.isivoltpro.maginaolivo.domain.parcel.ParcelAgronomy
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #696 / #695 A2+A3 — the agricultural circuit end to end, through the app's own repositories:
 * Finca → Parcela → Campaña → Pesadas → Jornales → Maquinaria → Gastos → resultados, plus the
 * Cuaderno outside the campaign.
 *
 * It exists to hold the rules that are easy to break silently: an unknown share is never read as
 * zero kilos, a partial yield is reported as partial, an unpriced jornal is pending and not free,
 * a general cost dated inside the campaign is not absorbed by it, and no cost is counted twice.
 * Every figure asserted here is the exact sum of the records written above it.
 */
class E2EAgriculturalCircuitTest {
    private val persistence: LocalPersistence = AppCompositionRoot
        .createAndroid(InstrumentationRegistry.getInstrumentation().targetContext, "DEV").localPersistence!!

    private val stamp = UUID.randomUUID().toString().take(8)
    private val farmName = "E2E Cortijo $stamp"
    private val day = LocalDate.of(2026, 10, 5)
    private var farmId: UUID? = null

    @Test fun theWholeCircuitAddsUpAndKeepsWhatIsUnknownUnknown() = runBlocking {
        val workspace = (persistence.workspaceRepository.ensureLocalWorkspace() as AppResult.Success).value
        val farm = ok(persistence.farmRepository.create(NewFarm(workspace, farmName, municipality = "Huelma", province = "Jaén")))
        farmId = farm
        val alta = ok(persistence.parcelRepository.create(NewParcel(farm, "Alta $stamp", managedAreaM2 = 10_000.0,
            agronomy = ParcelAgronomy(oliveTreeCount = 100, variety = "Picual"))))
        val baja = ok(persistence.parcelRepository.create(NewParcel(farm, "Baja $stamp", managedAreaM2 = 6_000.0,
            agronomy = ParcelAgronomy(oliveTreeCount = 60, variety = "Picual"))))

        // A3 — the Cuaderno of the season, with no campaign: 40 € of irrigation energy before the
        // recollection starts, and 15 € of fence repair dated *inside* the campaign's days. Neither
        // belongs to the campaign: a record joins a campaign by explicit relation, never by date.
        ok(persistence.expenseRepository.create(ExpenseDraft(
            expenseDate = LocalDate.of(2026, 9, 20), concept = "Energía de riego $stamp",
            category = ExpenseCategory.IRRIGATION, amountMinor = 4_000, farmId = farm,
        )))

        // A2 — the recollection campaign over both parcels.
        val campaign = ok(persistence.campaignRepository.create(
            NewCampaign(farm, "E2E recogida $stamp", day.minusDays(2), setOf(alta, baja), null)))
        ok(persistence.campaignRepository.activate(campaign))
        val fence = ok(persistence.activityRepository.create(NewActivity(
            farmId = farm, type = ActivityType.MAINTENANCE, activityDate = day.plusDays(1),
            description = "Reparación de valla $stamp", parcelIds = setOf(alta), completeImmediately = true,
        )))
        ok(persistence.expenseRepository.create(ExpenseDraft(
            expenseDate = day.plusDays(1), concept = "Valla $stamp",
            category = ExpenseCategory.REPAIR, amountMinor = 1_500, farmId = farm,
        )))

        // Pesada with a known split, and a second one the same day whose split is unknown.
        val exact = ok(persistence.deliveryRepository.create(DeliveryDraft(
            farmId = farm, deliveryDate = day, destinationOrganizationId = null, destinationName = "Cooperativa E2E",
            netGrams = 1_000_000, ticketNumber = "E2E-$stamp-1", origin = PesadaOrigin.TREE,
            shares = listOf(DeliveryShareInput(alta, 600_000), DeliveryShareInput(baja, 400_000)),
        )))
        val unknown = ok(persistence.deliveryRepository.create(DeliveryDraft(
            farmId = farm, deliveryDate = day, destinationOrganizationId = null, destinationName = "Cooperativa E2E",
            netGrams = 800_000, ticketNumber = "E2E-$stamp-2", origin = PesadaOrigin.GROUND,
            shares = listOf(DeliveryShareInput(alta, null), DeliveryShareInput(baja, null)),
        )))
        // The yield of only one Pesada: the other keeps no analysis, and none is invented for it.
        ok(persistence.deliveryRepository.recordYield(exact, YieldDraft(day, 2_000, null, null)))

        val afterPesadas = deliveries(campaign)
        val exactRow = afterPesadas.single { it.id == exact }
        val unknownRow = afterPesadas.single { it.id == unknown }
        assertEquals(0L, exactRow.unallocatedGrams)
        assertEquals(listOf(HarvestAllocation.EXACT, HarvestAllocation.EXACT), exactRow.shares.map { it.allocation })
        // Nothing is shared out by intuition: the whole Pesada stays unattributed, never 0 kg each.
        assertEquals(800_000L, unknownRow.unallocatedGrams)
        assertTrue(unknownRow.shares.all { it.weightGrams == null })
        assertNull(unknownRow.analysis)

        val summary = DeliverySummary.of(afterPesadas)
        assertEquals(1_800_000L, summary.deliveredGrams)
        assertEquals(800_000L, summary.unallocatedGrams)
        assertEquals(2_000, summary.fatYield!!.hundredths)
        // 1.000 of 1.800 kg analysed: the figure is reported as partial, not as the whole campaign.
        assertEquals(55, summary.coveragePercent(summary.fatYield))

        // Both Pesadas of the day joined one single automatic Jornada.
        val jornadas = persistence.harvestRepository.observeForCampaign(campaign).first()
        assertEquals(1, jornadas.size)
        val jornada = jornadas.single()
        assertEquals(1_800_000L, jornada.totalGrams)
        assertTrue(jornada.automatic)

        // Jornales with their agreed price, machinery and one explicit campaign cost.
        val ana = ok(persistence.labourRepository.addWorker("Ana E2E $stamp"))
        val luis = ok(persistence.labourRepository.addWorker("Luis E2E $stamp"))
        ok(persistence.labourRepository.recordCrew(CrewDraft(
            jornada.id, listOf(ana, luis), LabourUnit.FULL_DAY,
            appliedRate = LabourRateSnapshot(6_000, "EUR", day, LabourRateBasis.DAY),
        )))
        ok(persistence.equipmentRepository.replaceForHarvest(jornada.id, listOf(
            EquipmentDraftLine(EquipmentType.TRACTOR, 1, appliedPrice = EquipmentPriceSnapshot(12_000, "EUR", day)),
            EquipmentDraftLine(EquipmentType.TRAILER, 1, appliedPrice = EquipmentPriceSnapshot(6_000, "EUR", day)),
        )))
        ok(persistence.expenseRepository.create(ExpenseDraft(
            expenseDate = day, concept = "Transporte E2E $stamp", category = ExpenseCategory.TRANSPORT,
            amountMinor = 7_000, farmId = farm, campaignId = campaign,
        )))

        // The ledger is the only money: jornales 120 € + maquinaria 180 € + transporte 70 € = 370 €.
        val expenses = persistence.expenseRepository.observeAll().first()
        val ledger = RecollectionLedger.of(campaign, expenses, afterPesadas).single()
        assertEquals("EUR", ledger.currency)
        assertEquals(12_000L, ledger.amount(RecollectionBucket.LABOUR))
        assertEquals(37_000L, ledger.amount())
        // The day's labour expense is posted exactly once, as the subtotal of its own lines.
        val labourExpenses = expenses.filter { it.harvestId == jornada.id && it.origin == ExpenseOrigin.DAY_LABOUR }
        assertEquals(listOf(12_000L), labourExpenses.map { it.amountMinor })

        // The general costs stay apart from the recollection, by relation and not by date.
        val farmRow = persistence.farmRepository.observeById(farm).first()!!
        val campaigns = persistence.campaignRepository.observeForFarm(farm).first()
        val season = FarmOverview.of("2026/27", listOf(farmRow), campaigns, afterPesadas, expenses)
        assertEquals(37_000L, season.costs.single().amountMinor)
        // 40 € + 15 €: the fence cost is dated inside the campaign and still stays out of it.
        assertEquals(5_500L, season.generalCosts.single().amountMinor)
        assertEquals(42_500L, season.totalCosts.single().amountMinor)
        // 370 € over 1.800 kg = 0,206 €/kg, in thousandths and never rounded to cents.
        assertEquals(206L, season.costPerKgMilli)
        assertEquals(236L, season.totalCostPerKgMilli)
        // The general work dated inside the campaign's days belongs to no campaign either.
        assertNull(persistence.activityRepository.observe(fence).first()!!.campaignId)

        // Balances come from the payments: one settled, one partial, and no future payment.
        val entries = persistence.labourRepository.observeForCampaign(campaign).first()
        val confirmed = LabourLedgerAllocation.of(labourExpenses.single(), entries)
        assertNotNull(confirmed)
        assertEquals(12_000L, confirmed!!.sumOf { it.amountMinor })
        ok(persistence.labourRepository.recordPayment(LabourPayment(UUID.randomUUID(), ana, campaign, day, 6_000, "EUR", "E2E")))
        ok(persistence.labourRepository.recordPayment(LabourPayment(UUID.randomUUID(), luis, campaign, day, 2_000, "EUR", "E2E")))
        val payments = persistence.labourRepository.observePayments(campaign).first()
        assertEquals(LabourPaymentState.PAID, LabourSettlement.of(ana, campaign, "EUR", confirmed, payments).state)
        val luisBalance = LabourSettlement.of(luis, campaign, "EUR", confirmed, payments)
        assertEquals(LabourPaymentState.PARTIAL, luisBalance.state)
        assertEquals(4_000L, luisBalance.pendingMinor)

        // #449: a jornal whose price is not known yet is pending, never 0 €. The posted money does
        // not change, and the campaign says out loud that its cost is still incomplete.
        val sinPrecio = ok(persistence.labourRepository.addWorker("Sin precio E2E $stamp"))
        ok(persistence.labourRepository.recordCrew(CrewDraft(jornada.id, listOf(sinPrecio), LabourUnit.FULL_DAY, priceUnknown = true)))
        val afterUnpriced = persistence.expenseRepository.observeAll().first()
        assertEquals(
            listOf(12_000L),
            afterUnpriced.filter { it.harvestId == jornada.id && it.origin == ExpenseOrigin.DAY_LABOUR }.map { it.amountMinor },
        )
        val completeness = RecollectionCostCompleteness.of(
            persistence.labourRepository.observeForCampaign(campaign).first(),
            persistence.equipmentRepository.observeForHarvest(jornada.id).first(),
            afterUnpriced.filter { it.campaignId == campaign },
        )
        assertTrue(RecollectionCostCompleteness.Reason.LABOUR_UNPRICED in completeness.reasons)
        assertTrue(!completeness.complete)

        // Everything above was written through the repositories, so a new composition root over the
        // same local database reads the very same figures: nothing lived only in memory.
        val reopened = AppCompositionRoot
            .createAndroid(InstrumentationRegistry.getInstrumentation().targetContext, "DEV").localPersistence!!
        val reread = reopened.deliveryRepository.observeForCampaign(campaign).first()
        assertEquals(1_800_000L, DeliverySummary.of(reread).deliveredGrams)
        assertEquals(800_000L, DeliverySummary.of(reread).unallocatedGrams)
        assertEquals(
            37_000L,
            RecollectionLedger.of(campaign, reopened.expenseRepository.observeAll().first(), reread).single().amount(),
        )
    }

    /** The shared instrumentation database is left without this test's running campaign or farm. */
    @After fun retireTheCircuit() = runBlocking {
        val farm = farmId ?: return@runBlocking
        persistence.campaignRepository.observeForFarm(farm).first().forEach {
            persistence.campaignRepository.close(it.id, maxOf(it.startDate, day))
        }
        persistence.farmRepository.archive(farm)
        Unit
    }

    private suspend fun deliveries(campaign: UUID) = persistence.deliveryRepository.observeForCampaign(campaign).first()

    private fun <T> ok(result: AppResult<T>): T = when (result) {
        is AppResult.Success -> result.value
        is AppResult.Failure -> throw AssertionError("step failed: ${result.error}")
    }
}
