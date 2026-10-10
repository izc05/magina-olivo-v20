package com.isivoltpro.maginaolivo

import androidx.test.platform.app.InstrumentationRegistry
import com.isivoltpro.maginaolivo.app.AppCompositionRoot
import com.isivoltpro.maginaolivo.app.DemoFarmSeeder
import com.isivoltpro.maginaolivo.app.DevTools
import com.isivoltpro.maginaolivo.app.LocalPersistence
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.data.local.model.ActivityStatus
import com.isivoltpro.maginaolivo.data.local.model.isRunning
import com.isivoltpro.maginaolivo.domain.activity.ActivityDetail
import com.isivoltpro.maginaolivo.domain.activity.ActivityType
import com.isivoltpro.maginaolivo.domain.machinery.MachineCategory
import com.isivoltpro.maginaolivo.domain.machinery.MachineDraft
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

        // #696: every work the contract gives structured fields to carries its typed detail, so the
        // treatment, abonado, poda, suelo, riego, mantenimiento and incidencia blocks all show data.
        val works = persistence.activityRepository.observeForFarm(demo.id).first()
        assertTrue(works.filter { it.type != ActivityType.OBSERVATION }.all { it.detail != null })
        assertEquals(1, works.count { it.detail is ActivityDetail.Incident })
        val treatments = works.mapNotNull { it.detail as? ActivityDetail.Phytosanitary }
        assertEquals(4, treatments.size)                                // three done, one planned
        assertTrue(treatments.all { !it.reason.isNullOrBlank() })
        // An irrigation price is a historical tariff snapshot: its estimate is exactly unit x quantity.
        val irrigations = works.mapNotNull { it.detail as? ActivityDetail.Irrigation }
        assertEquals(5, irrigations.size)
        assertEquals(4, irrigations.count { it.price != null })
        irrigations.mapNotNull { it.price }.forEach { price ->
            assertEquals(Math.round(price.unitPriceMinor!! * price.quantity!!), price.estimatedAmountMinor)
        }
        // La Loma is dryland in the demo, so it is never watered.
        val dryland = parcels.getValue("La Loma").id
        assertTrue(
            works.filter { it.type == ActivityType.IRRIGATION }
                .none { work -> work.targets.any { it.parcelId == dryland } },
        )
        // Planned work ahead of today, with its hour, crew and reminders: Calendario and avisos.
        val plannedWork = works.filter { it.status == ActivityStatus.PLANNED }
        assertEquals(2, plannedWork.size)
        assertTrue(plannedWork.all { it.planning?.startTime != null && it.reminders.isNotEmpty() })
        assertTrue(plannedWork.all { it.activityDate.isAfter(LocalDate.now()) })
        // Maquinaria: the demo machines exist and the works that used them are read from the machine.
        val machines = persistence.machineRepository.observeActive().first().filter { it.name.endsWith("(DEMO)") }
        assertEquals(3, machines.size)
        val tractor = machines.single { it.category == MachineCategory.TRACTOR }
        // Its uses name the works that used it. A reset reuses the machine, so the history may be
        // longer than this seed's two entries; what must hold is that these two are in it.
        val uses = persistence.machineRepository.observeUses(tractor.id).first().map { it.description }
        assertTrue(uses.containsAll(listOf("Labores de suelo (DEMO)", "Abonado (DEMO)")))

        val reset = tools.reset()
        assertTrue("reset: $reset", reset is AppResult.Success)
        val fresh = demoFarms(workspace).single()
        assertTrue(fresh.id != demo.id)
        // The retired one keeps the mark, so a later removal still recognises it as a demo.
        val retired = persistence.farmRepository.observeArchived(workspace).first().single { it.id == demo.id }
        assertTrue(DemoFarmSeeder.isDemo(retired))
    }

    /** #696: «Eliminar datos de demostración» retires only the demo; real data is untouched. */
    @Test fun removeRetiresOnlyTheDemoAndKeepsRealData() = runBlocking {
        val workspace = workspace()
        // Named exactly like the demo and with the same note, but with no mark: it is a real Farm.
        val realFarm = (persistence.farmRepository.create(
            NewFarm(
                workspace, DemoFarmSeeder.DEMO_FARM, description = "la m\u00eda de verdad",
                municipality = "Ja\u00e9n", province = "Ja\u00e9n", notes = DemoFarmSeeder.DEMO_NOTE,
            ),
        ) as AppResult.Success).value
        persistence.parcelRepository.create(
            NewParcel(farmId = realFarm, displayName = "Parcela real QA", managedAreaM2 = 5_000.0),
        ).let { assertTrue(it is AppResult.Success) }

        val realMachine = (persistence.machineRepository.create(
            MachineDraft(name = "Tractor real QA ${UUID.randomUUID()}", category = MachineCategory.TRACTOR),
        ) as AppResult.Success).value

        assertTrue(tools.load() is AppResult.Success)
        assertEquals(1, demoFarms(workspace).size)   // the real Farm sharing the name is not one
        val removed = tools.remove()
        assertTrue("remove: $removed", removed is AppResult.Success)

        // The demo machines leave the pickers by their own mark; a real machine is never touched.
        val machinesLeft = persistence.machineRepository.observeActive().first()
        assertTrue(machinesLeft.none { it.name.endsWith("(DEMO)") })
        assertTrue(machinesLeft.any { it.id == realMachine })

        assertEquals(emptyList<Farm>(), demoFarms(workspace))
        val real = persistence.farmRepository.observeActive(workspace).first().single { it.id == realFarm }
        assertNull(real.archivedAt)                  // same name as the demo, untouched
        assertEquals(DemoFarmSeeder.DEMO_FARM, real.name)
        assertEquals(1L, real.parcelCount)
        // Calling it again with no demo loaded is safe and still changes nothing.
        assertTrue(tools.remove() is AppResult.Success)
        assertEquals(1L, persistence.farmRepository.observeActive(workspace).first().single { it.id == realFarm }.parcelCount)
        persistence.farmRepository.archive(realFarm)
        persistence.machineRepository.archive(realMachine)
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

    /** #696: the demo is recognised by the mark the seeder wrote, never by its name. */
    private suspend fun demoFarms(workspace: UUID): List<Farm> =
        persistence.farmRepository.observeActive(workspace).first().filter(DemoFarmSeeder::isDemo)
}
