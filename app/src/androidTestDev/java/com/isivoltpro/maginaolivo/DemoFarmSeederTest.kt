package com.isivoltpro.maginaolivo

import androidx.test.platform.app.InstrumentationRegistry
import com.isivoltpro.maginaolivo.app.AppCompositionRoot
import com.isivoltpro.maginaolivo.app.DemoFarmSeeder
import com.isivoltpro.maginaolivo.app.DevTools
import com.isivoltpro.maginaolivo.app.LocalPersistence
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.data.local.model.isRunning
import com.isivoltpro.maginaolivo.domain.analytics.FarmOverview
import com.isivoltpro.maginaolivo.domain.farm.Farm
import com.isivoltpro.maginaolivo.feature.campaigns.CampaignCardSummary
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** #399: the DEV «Finca Demo» adds up to `docs/DEMO-FARM-SCENARIO.md`, once, through the real repositories. */
class DemoFarmSeederTest {
    private val persistence: LocalPersistence = AppCompositionRoot
        .createAndroid(InstrumentationRegistry.getInstrumentation().targetContext, "DEV").localPersistence!!
    private val tools = DevTools.demoFarm(persistence)

    @Test fun loadTwiceThenResetKeepsOneDemoWithTheDocumentedTotals() = runBlocking {
        assertTrue(tools.load() is AppResult.Success)
        assertTrue(tools.load() is AppResult.Success) // idempotent
        val workspace = (persistence.workspaceRepository.ensureLocalWorkspace() as AppResult.Success).value
        val demo = demoFarms(workspace).single()
        assertEquals(3L, demo.parcelCount)
        assertEquals(32_000.0, demo.totalAreaM2!!, 0.5)

        val campaigns = persistence.campaignRepository.observeForFarm(demo.id).first()
        assertEquals(2, campaigns.size)
        val running = campaigns.single { it.status.isRunning }
        assertEquals("Campaña de recogida 2026/27", running.name)

        val deliveries = persistence.deliveryRepository.observeAll().first()
        val harvests = persistence.harvestRepository.observeAll().first()
        val expenses = persistence.expenseRepository.observeAll().first()
        val card = CampaignCardSummary.of(running.id, deliveries, harvests, expenses)
        assertEquals(5_700_000L, card.deliveredGrams)
        assertEquals(3, card.deliveryCount)
        assertEquals(3, card.dayCount)
        assertEquals(listOf("EUR" to 65_000L), card.labour)
        assertEquals(2_082, card.yieldHundredths)

        val season = FarmOverview.of("2026/27", listOf(demo), campaigns, deliveries, expenses)
        assertEquals(144_000L, season.costs.single().amountMinor)      // 650 + 540 + 250
        assertEquals(253L, season.costPerKgMilli)                      // #486: 0,253 €/kg, never 0,25
        assertEquals(118_500L, season.generalCosts.single().amountMinor) // never in recollection
        assertEquals(262_500L, season.totalCosts.single().amountMinor)
        assertEquals(461L, season.totalCostPerKgMilli)                 // 0,461 €/kg
        // Owner on #413: general work and a general cost inside the campaign's dates, never in its ledger.
        val inCampaignDates = { date: java.time.LocalDate -> !date.isBefore(running.startDate) && !date.isAfter(DemoFarmSeeder.LAST_DAY) }
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

    /** Leave the shared E2E database without a running demo campaign. */
    @After fun retireDemo() = runBlocking {
        val workspace = (persistence.workspaceRepository.ensureLocalWorkspace() as AppResult.Success).value
        demoFarms(workspace).forEach { farm ->
            persistence.campaignRepository.observeForFarm(farm.id).first().filter { it.status.isRunning }.forEach {
                persistence.campaignRepository.close(it.id, maxOf(it.startDate, DemoFarmSeeder.LAST_DAY))
            }
            persistence.farmRepository.archive(farm.id)
        }
    }

    private suspend fun demoFarms(workspace: UUID): List<Farm> =
        persistence.farmRepository.observeActive(workspace).first().filter { it.name == DemoFarmSeeder.DEMO_FARM }
}
