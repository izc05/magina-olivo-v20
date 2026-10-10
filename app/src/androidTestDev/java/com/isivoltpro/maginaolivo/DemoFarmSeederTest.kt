package com.isivoltpro.maginaolivo

import androidx.test.platform.app.InstrumentationRegistry
import com.isivoltpro.maginaolivo.app.AppCompositionRoot
import com.isivoltpro.maginaolivo.app.DemoFarmSeeder
import com.isivoltpro.maginaolivo.app.DevTools
import com.isivoltpro.maginaolivo.app.LocalPersistence
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.data.local.model.isRunning
import com.isivoltpro.maginaolivo.domain.analytics.FarmOverview
import com.isivoltpro.maginaolivo.domain.delivery.PesadaOrigin
import com.isivoltpro.maginaolivo.domain.expense.ExpenseOrigin
import com.isivoltpro.maginaolivo.domain.farm.Farm
import com.isivoltpro.maginaolivo.domain.farm.NewFarm
import com.isivoltpro.maginaolivo.domain.labour.LabourSummary
import com.isivoltpro.maginaolivo.domain.parcel.IrrigationSystem
import com.isivoltpro.maginaolivo.domain.parcel.NewParcel
import com.isivoltpro.maginaolivo.feature.campaigns.CampaignCardSummary
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** #399/#696: the DEV «Finca Demo» adds up to `docs/DEMO-FARM-SCENARIO.md`, once, through the real repositories. */
class DemoFarmSeederTest {
    private val persistence: LocalPersistence = AppCompositionRoot
        .createAndroid(InstrumentationRegistry.getInstrumentation().targetContext, "DEV").localPersistence!!
    private val tools = DevTools.demoFarm(persistence)

    @Test fun loadTwiceThenResetKeepsOneDemoWithTheDocumentedTotals() = runBlocking {
        assertTrue(tools.load() is AppResult.Success)
        assertTrue(tools.load() is AppResult.Success) // idempotent
        val workspace = workspace()
        val demo = demoFarms(workspace).single()
        assertEquals(3L, demo.parcelCount)
        assertEquals(32_000.0, demo.totalAreaM2!!, 0.5)
        assertEquals(320L, demo.oliveTreeCount)

        // #696: grove data, watering and an unmistakably fictitious reference on every Parcel.
        val parcels = persistence.parcelRepository.observeActive(demo.id).first().associateBy { it.displayName }
        assertEquals(setOf("Los Llanos", "La Loma", "El Barranco"), parcels.keys)
        assertEquals(listOf("Hojiblanca", "Picual", "Picual"), parcels.values.mapNotNull { it.agronomy.variety }.sorted())
        assertEquals(setOf("Bedmar", "Garcíez"), parcels.values.mapNotNull { it.municipality }.toSet())
        assertTrue(parcels.values.all { it.cadastralReference?.startsWith("DEMO-") == true })
        assertEquals(IrrigationSystem.DRYLAND, parcels.getValue("La Loma").agronomy.irrigationSystem)
        assertEquals(2, parcels.getValue("Los Llanos").agronomy.irrigationDays.size)

        val campaigns = persistence.campaignRepository.observeForFarm(demo.id).first()
        assertEquals(2, campaigns.size)
        val running = campaigns.single { it.status.isRunning }
        assertEquals("Campaña de recogida 2026/27", running.name)

        val deliveries = persistence.deliveryRepository.observeAll().first()
        val harvests = persistence.harvestRepository.observeAll().first()
        val expenses = persistence.expenseRepository.observeAll().first()
        val card = CampaignCardSummary.of(running.id, deliveries, harvests, expenses)
        assertEquals(6_680_000L, card.deliveredGrams)   // 1.850 + 2.120 + 980 + 1.730 kg
        assertEquals(4, card.deliveryCount)
        assertEquals(3, card.dayCount)                  // two Pesadas of 01-10 share one Jornada
        assertEquals(listOf("EUR" to 73_250L), card.labour)
        assertEquals(2_057, card.yieldHundredths)       // weighted by kilos, never an average of averages
        assertEquals(100, card.yieldCoveragePercent)

        // #696: two Pesadas the same day, each with its own vale, one from the tree and one from the ground.
        val sameDay = deliveries.filter { it.campaignId == running.id && it.deliveryDate == LocalDate.of(2026, 10, 1) }
        assertEquals(listOf("DEMO-002", "DEMO-004"), sameDay.mapNotNull { it.ticketNumber }.sorted())
        assertEquals(setOf(PesadaOrigin.TREE, PesadaOrigin.GROUND), sameDay.mapNotNull { it.origin }.toSet())
        assertEquals(1, sameDay.mapNotNull { it.harvestId }.distinct().size)

        // #696: whole days, a half day and hours, kept apart and never converted into one another.
        val entries = persistence.labourRepository.observeForCampaign(running.id).first()
        val labour = LabourSummary.of(entries)
        assertEquals(10, labour.fullDays)
        assertEquals(1, labour.halfDays)
        assertEquals(300, labour.minutes)
        assertEquals(12, labour.people)
        // The day's posted labour is the exact subtotal of its lines: 245 + 260 + 227,50 €.
        val perDay = expenses.filter { it.campaignId == running.id && it.origin == ExpenseOrigin.DAY_LABOUR }
        assertEquals(listOf(22_750L, 24_500L, 26_000L), perDay.map { it.amountMinor }.sorted())
        // Paid in full, partial and pending all present: 195 + 100 + (130 + 50) €, José still owed.
        assertEquals(47_500L, persistence.labourRepository.observePayments(running.id).first().sumOf { it.amountMinor })

        val season = FarmOverview.of("2026/27", listOf(demo), campaigns, deliveries, expenses)
        assertEquals(152_250L, season.costs.single().amountMinor)       // 732,50 + 540 + 250 €
        assertEquals(228L, season.costPerKgMilli)                       // #486: 0,228 €/kg, never 0,23
        assertEquals(118_500L, season.generalCosts.single().amountMinor) // never in recollection
        assertEquals(270_750L, season.totalCosts.single().amountMinor)
        assertEquals(405L, season.totalCostPerKgMilli)                  // 0,405 €/kg
        // Owner on #413: general work and a general cost inside the campaign's dates, never in its ledger.
        val inCampaignDates = { date: LocalDate -> !date.isBefore(running.startDate) && !date.isAfter(DemoFarmSeeder.LAST_DAY) }
        val generalWork = persistence.activityRepository.observeForFarm(demo.id).first()
            .filter { it.campaignId == null && inCampaignDates(it.activityDate) }
        assertEquals(2, generalWork.size)
        val generalCost = expenses.filter { it.farmId == demo.id && it.campaignId == null && inCampaignDates(it.expenseDate) }
        assertEquals(listOf(9_000L), generalCost.map { it.amountMinor })
        val history = FarmOverview.of("2025/26", listOf(demo), campaigns, deliveries, expenses)
        assertEquals(5_100_000L, history.delivery.deliveredGrams)       // kept apart

        assertTrue(tools.reset() is AppResult.Success)
        val fresh = demoFarms(workspace).single()
        assertTrue(fresh.id != demo.id)
    }

    /** #696: «Eliminar datos de demostración» retires only the demo; real data is untouched. */
    @Test fun removeRetiresOnlyTheDemoAndKeepsRealData() = runBlocking {
        val workspace = workspace()
        val realFarm = (persistence.farmRepository.create(
            NewFarm(workspace, "Finca Real QA ${UUID.randomUUID()}", municipality = "Jaén", province = "Jaén"),
        ) as AppResult.Success).value
        persistence.parcelRepository.create(
            NewParcel(farmId = realFarm, displayName = "Parcela real QA", managedAreaM2 = 5_000.0),
        ).let { assertTrue(it is AppResult.Success) }

        assertTrue(tools.load() is AppResult.Success)
        assertEquals(1, demoFarms(workspace).size)
        assertTrue(tools.remove() is AppResult.Success)

        assertEquals(emptyList<Farm>(), demoFarms(workspace))
        val real = persistence.farmRepository.observeActive(workspace).first().single { it.id == realFarm }
        assertNull(real.archivedAt)
        assertEquals(1L, real.parcelCount)
        // Calling it again with no demo loaded is safe and still changes nothing.
        assertTrue(tools.remove() is AppResult.Success)
        assertEquals(1L, persistence.farmRepository.observeActive(workspace).first().single { it.id == realFarm }.parcelCount)
        persistence.farmRepository.archive(realFarm)
        Unit
    }

    /** Leave the shared E2E database without a running demo campaign. */
    @After fun retireDemo() = runBlocking {
        val workspace = workspace()
        demoFarms(workspace).forEach { farm ->
            persistence.campaignRepository.observeForFarm(farm.id).first().filter { it.status.isRunning }.forEach {
                persistence.campaignRepository.close(it.id, maxOf(it.startDate, DemoFarmSeeder.LAST_DAY))
            }
            persistence.farmRepository.archive(farm.id)
        }
    }

    private suspend fun workspace(): UUID =
        (persistence.workspaceRepository.ensureLocalWorkspace() as AppResult.Success).value

    private suspend fun demoFarms(workspace: UUID): List<Farm> =
        persistence.farmRepository.observeActive(workspace).first().filter { it.name == DemoFarmSeeder.DEMO_FARM }
}
