package com.isivoltpro.maginaolivo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.isivoltpro.maginaolivo.app.LocalPersistence
import com.isivoltpro.maginaolivo.data.local.MaginaOlivoDatabase
import com.isivoltpro.maginaolivo.data.local.model.CampaignStatus
import com.isivoltpro.maginaolivo.domain.campaign.Campaign
import com.isivoltpro.maginaolivo.domain.campaign.CampaignRepository
import com.isivoltpro.maginaolivo.domain.delivery.DeliveryRepository
import com.isivoltpro.maginaolivo.domain.equipment.EquipmentRepository
import com.isivoltpro.maginaolivo.domain.expense.*
import com.isivoltpro.maginaolivo.domain.labour.LabourEntry
import com.isivoltpro.maginaolivo.domain.labour.LabourRepository
import com.isivoltpro.maginaolivo.feature.campaignhub.CampaignOverviewState
import com.isivoltpro.maginaolivo.feature.campaignhub.observeCampaignOverview
import java.lang.reflect.Proxy
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Test

class CampaignOverviewFlowTest {
    @Test fun delayedCrewReadsKeepAmountsUnknownThenPreserveArchivedAndUnassignedCurrencyScope() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), MaginaOlivoDatabase::class.java).build()
        var collection: Job? = null
        try {
            val farm = UiPolishFixtures.farms.first().copy(archivedAt = Instant.parse("2026-10-01T00:00:00Z"))
            val campaign = Campaign(UUID.randomUUID(), farm.workspaceId, farm.id, "Recogida histórica", LocalDate.of(2026, 9, 1), null, CampaignStatus.CLOSED, null, emptyList(), 1)
            val costs = listOf(
                cost(farm.workspaceId, "EUR", 1_000), cost(farm.workspaceId, "USD", 2_000),
                cost(farm.workspaceId, "EUR", 10_000).copy(farmId = farm.id, campaignId = campaign.id),
                cost(UUID.randomUUID(), "EUR", 99_999),
            )
            val subscribed = CompletableDeferred<Unit>()
            val delayed = MutableSharedFlow<List<LabourEntry>>(replay = 1)
            val campaigns = object : CampaignRepository by rejectCalls<CampaignRepository>() {
                override fun observeForFarm(farmId: UUID) = flowOf(listOf(campaign))
            }
            val expenses = object : ExpenseRepository by rejectCalls<ExpenseRepository>() { override fun observeAll() = flowOf(costs) }
            val deliveries = object : DeliveryRepository by rejectCalls<DeliveryRepository>() { override fun observeAll() = flowOf(emptyList<com.isivoltpro.maginaolivo.domain.delivery.Delivery>()) }
            val labour = object : LabourRepository by rejectCalls<LabourRepository>() { override fun observeForCampaign(campaignId: UUID) = flow { subscribed.complete(Unit); emitAll(delayed) } }
            val equipment = object : EquipmentRepository by rejectCalls<EquipmentRepository>() { override fun observeForCampaign(campaignId: UUID) = flowOf(emptyList<com.isivoltpro.maginaolivo.domain.equipment.EquipmentLine>()) }
            val persistence = LocalPersistence(database, rejectCalls(), rejectCalls(), rejectCalls(), campaigns,
                rejectCalls(), rejectCalls(), rejectCalls(), expenses, rejectCalls(), rejectCalls(), deliveries,
                rejectCalls(), labour, equipment, rejectCalls())
            val seen = MutableStateFlow(CampaignOverviewState())
            collection = launch(Dispatchers.Default) {
                observeCampaignOverview(persistence, farm.workspaceId, listOf(farm)).collect { seen.value = it }
            }
            withTimeout(5_000) { subscribed.await() }
            assertTrue(seen.value.isLoading)
            assertTrue(seen.value.overviews.isEmpty())
            delayed.emit(emptyList())
            val ready = withTimeout(5_000) { seen.first { !it.isLoading } }
            assertFalse(ready.isLoading)
            assertEquals(2, ready.unassigned.size)
            val overview = ready.overviews.single()
            assertTrue(overview.farms.single().archived)
            assertEquals(10_000L, overview.costs.single().amountMinor)
            assertEquals(mapOf("EUR" to 1_000L, "USD" to 2_000L), overview.generalCosts.associate { it.currency to it.amountMinor })
        } finally { collection?.cancelAndJoin(); database.close() }
    }

    private fun cost(workspace: UUID, currency: String, amount: Long) = Expense(UUID.randomUUID(), workspace,
        LocalDate.of(2026, 10, 1), "Ficticio", ExpenseCategory.FUEL, amount, currency, ExpenseStatus.POSTED, ExpenseOrigin.MANUAL)

    private inline fun <reified T> rejectCalls(): T = Proxy.newProxyInstance(T::class.java.classLoader,
        arrayOf(T::class.java)) { _, method, _ -> throw AssertionError("Unexpected write/read: ${method.name}") } as T
}
