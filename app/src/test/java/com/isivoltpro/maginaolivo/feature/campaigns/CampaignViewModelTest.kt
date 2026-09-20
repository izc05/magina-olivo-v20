package com.isivoltpro.maginaolivo.feature.campaigns

import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.data.local.model.CampaignStatus
import com.isivoltpro.maginaolivo.domain.campaign.Campaign
import com.isivoltpro.maginaolivo.domain.campaign.CampaignPreparationChanges
import com.isivoltpro.maginaolivo.domain.campaign.CampaignRepository
import com.isivoltpro.maginaolivo.domain.campaign.NewCampaign
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CampaignViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val farmId = UUID.randomUUID()
    @Before fun before() = Dispatchers.setMain(dispatcher)
    @After fun after() = Dispatchers.resetMain()

    @Test fun partitionsCurrentAndHistoryAndValidatesCreate() = runTest(dispatcher) {
        val fake = FakeRepository()
        val viewModel = FarmCampaignsViewModel(farmId, fake)
        fake.forFarm.value = listOf(campaign(CampaignStatus.ACTIVE), campaign(CampaignStatus.CLOSED))
        advanceUntilIdle()
        assertFalse(viewModel.state.value.isLoading)
        assertEquals(1, viewModel.state.value.current.size)
        assertEquals(1, viewModel.state.value.history.size)
        viewModel.create(CampaignDraft())
        assertEquals("Escribe un nombre para la campaña", viewModel.state.value.nameError)
        assertNull(fake.created)
        viewModel.create(CampaignDraft("2026/27", LocalDate.parse("2026-10-01")))
        advanceUntilIdle()
        assertEquals("2026/27", fake.created?.name)
    }

    @Test fun detailExposesTransitionsAndConsumesSuccessMessage() = runTest(dispatcher) {
        val fake = FakeRepository(campaign(CampaignStatus.PREPARATION))
        val viewModel = CampaignDetailViewModel(fake.detail.value!!.id, fake)
        advanceUntilIdle()
        viewModel.activate(); advanceUntilIdle()
        assertEquals("activate", fake.lastAction)
        assertEquals("Campaña activada", viewModel.state.value.message)
        viewModel.consumeMessage()
        assertNull(viewModel.state.value.message)
        fake.fail = true
        viewModel.markHarvest(); advanceUntilIdle()
        assertEquals("La operación no se pudo completar", viewModel.state.value.error)
    }

    private fun campaign(status: CampaignStatus) = Campaign(UUID.randomUUID(), UUID.randomUUID(), farmId,
        "Campaña", LocalDate.parse("2026-10-01"), null, status, null, emptyList(), 1)

    private class FakeRepository(initial: Campaign? = null) : CampaignRepository {
        val forFarm = MutableStateFlow<List<Campaign>>(emptyList())
        val detail = MutableStateFlow(initial)
        var created: NewCampaign? = null
        var lastAction: String? = null
        var fail = false
        override fun observeForFarm(farmId: UUID): Flow<List<Campaign>> = forFarm
        override fun observe(id: UUID): Flow<Campaign?> = detail
        override suspend fun create(command: NewCampaign): AppResult<UUID> { created = command; return AppResult.Success(UUID.randomUUID()) }
        override suspend fun updatePreparation(id: UUID, changes: CampaignPreparationChanges) = result("update")
        override suspend fun activate(id: UUID) = result("activate")
        override suspend fun markHarvest(id: UUID) = result("harvest")
        override suspend fun close(id: UUID, endDate: LocalDate) = result("close")
        override suspend fun reopen(id: UUID) = result("reopen")
        override suspend fun archivePreparation(id: UUID) = result("archive")
        private fun result(action: String): AppResult<Unit> { lastAction = action; return if (fail) AppResult.Failure(com.isivoltpro.maginaolivo.core.common.AppError.Conflict(action)) else AppResult.Success(Unit) }
    }
}
