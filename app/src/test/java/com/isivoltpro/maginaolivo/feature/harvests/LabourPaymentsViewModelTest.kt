package com.isivoltpro.maginaolivo.feature.harvests

import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.domain.campaign.CampaignRepository
import com.isivoltpro.maginaolivo.domain.expense.*
import com.isivoltpro.maginaolivo.domain.harvest.HarvestRepository
import com.isivoltpro.maginaolivo.domain.labour.*
import java.lang.reflect.Proxy
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.Before
import org.junit.After

@OptIn(ExperimentalCoroutinesApi::class)
class LabourPaymentsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()
    @Test fun repeatedSaveIsIgnoredWhileWritingAndTemporaryLedgerMismatchRecovers() = runTest(dispatcher) {
            val campaignId = UUID.randomUUID()
            val workerId = UUID.randomUUID()
            val day = UUID.randomUUID()
            val date = LocalDate.of(2026, 10, 1)
            val entry = LabourEntry(UUID.randomUUID(), day, workerId, "Juan", 1, LabourUnit.FULL_DAY, null, 1, LabourRateSnapshot(24000, "EUR", date, LabourRateBasis.DAY))
            val posted = Expense(UUID.randomUUID(), UUID.randomUUID(), date, "Jornales", ExpenseCategory.LABOR, 24000, "EUR", ExpenseStatus.POSTED, ExpenseOrigin.DAY_LABOUR, campaignId = campaignId, harvestId = day)
            val payment = LabourPayment(UUID.randomUUID(), workerId, campaignId, date, 10000, "EUR")
            val ledger = MutableStateFlow(listOf(posted))
            val saved = MutableStateFlow(listOf(payment))
            val finished = CompletableDeferred<Unit>()
            var writes = 0
            val labour = object : LabourRepository by unused() {
                override fun observeForCampaign(campaignId: UUID) = flowOf(listOf(entry))
                override fun observePayments(campaignId: UUID) = saved
                override suspend fun recordPayment(payment: LabourPayment): AppResult<UUID> {
                    writes++
                    finished.await()
                    saved.value = saved.value + payment
                    return AppResult.Success(payment.id)
                }
            }
            val campaigns = object : CampaignRepository by unused() { override fun observe(id: UUID) = flowOf<com.isivoltpro.maginaolivo.domain.campaign.Campaign?>(null) }
            val harvests = object : HarvestRepository by unused() { override fun observeForCampaign(campaignId: UUID) = flowOf(emptyList<com.isivoltpro.maginaolivo.domain.harvest.Harvest>()) }
            val expenses = object : ExpenseRepository by unused() { override fun observeAll() = ledger }
            val model = LabourPaymentsViewModel(campaignId, campaigns, labour, harvests, expenses)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.state.collect() }
            runCurrent()
            assertEquals(14000L, model.state.value.accounts.single().balances.single().pendingMinor)
            ledger.value = emptyList()
            runCurrent()
            assertNotNull(model.state.value.readError)
            ledger.value = listOf(posted)
            runCurrent()
            assertNull(model.state.value.readError)
            val next = payment.copy(id = UUID.randomUUID(), amountMinor = 8000)
            model.record(next); model.record(next)
            runCurrent()
            assertTrue(model.state.value.isSaving)
            assertEquals(1, writes)
            finished.complete(Unit)
            runCurrent()
            assertFalse(model.state.value.isSaving)
            assertEquals(6000L, model.state.value.accounts.single().balances.single().pendingMinor)

    }
    private inline fun <reified T : Any> unused(): T = Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ -> error("Unexpected ${method.name}") } as T
}
