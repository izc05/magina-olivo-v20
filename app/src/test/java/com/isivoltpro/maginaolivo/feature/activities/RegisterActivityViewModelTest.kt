package com.isivoltpro.maginaolivo.feature.activities

import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.domain.farm.Farm
import com.isivoltpro.maginaolivo.domain.farm.FarmChanges
import com.isivoltpro.maginaolivo.domain.farm.FarmRepository
import com.isivoltpro.maginaolivo.domain.farm.NewFarm
import com.isivoltpro.maginaolivo.domain.workspace.WorkspaceRepository
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RegisterActivityViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val workspaceId = UUID.fromString("10000000-0000-0000-0000-000000000498")
    private val farmA = farm("Finca A", "20000000-0000-0000-0000-000000000498")
    private val farmB = farm("Finca B", "20000000-0000-0000-0000-000000000499")

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun contextualFarmThatDisappearsNeverSwitchesToTheOnlyRemainingFarm() = runTest(dispatcher) {
        val farms = FakeFarmRepository().apply { active.value = listOf(farmA, farmB) }
        val viewModel = RegisterActivityViewModel(farms, FakeWorkspaceRepository(workspaceId))
        advanceUntilIdle()

        viewModel.preselectFarm(farmA.id)
        assertEquals(farmA.id, viewModel.state.value.selectedFarmId)
        assertTrue(viewModel.state.value.contextualFarmAccepted)

        farms.active.value = listOf(farmB)
        advanceUntilIdle()

        assertNull(viewModel.state.value.selectedFarmId)
        assertEquals(farmA.id, viewModel.state.value.contextualFarmId)
        assertFalse(viewModel.state.value.contextualFarmAccepted)
        assertTrue(viewModel.state.value.error.orEmpty().contains("ya no está activa"))
    }

    @Test
    fun globalEntryStillAutoSelectsOneActiveFarm() = runTest(dispatcher) {
        val farms = FakeFarmRepository().apply { active.value = listOf(farmB) }
        val viewModel = RegisterActivityViewModel(farms, FakeWorkspaceRepository(workspaceId))
        advanceUntilIdle()

        assertEquals(farmB.id, viewModel.state.value.selectedFarmId)
        assertNull(viewModel.state.value.contextualFarmId)
        assertNull(viewModel.state.value.error)
    }

    private fun farm(name: String, id: String) = Farm(
        id = UUID.fromString(id),
        workspaceId = workspaceId,
        name = name,
        description = null,
        municipality = null,
        province = null,
        notes = null,
        coverDocumentId = null,
        parcelCount = 0,
        totalAreaM2 = null,
        activeCampaignName = null,
        archivedAt = null,
        version = 1,
    )

    private class FakeWorkspaceRepository(private val id: UUID) : WorkspaceRepository {
        override suspend fun ensureLocalWorkspace(): AppResult<UUID> = AppResult.Success(id)
    }

    private class FakeFarmRepository : FarmRepository {
        val active = MutableStateFlow<List<Farm>>(emptyList())
        override fun observeActive(workspaceId: UUID): Flow<List<Farm>> = active
        override fun observeArchived(workspaceId: UUID): Flow<List<Farm>> = MutableStateFlow(emptyList())
        override fun observeById(farmId: UUID): Flow<Farm?> = MutableStateFlow(null)
        override suspend fun create(command: NewFarm): AppResult<UUID> = AppResult.Success(UUID.randomUUID())
        override suspend fun update(farmId: UUID, changes: FarmChanges): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun archive(farmId: UUID): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun restore(farmId: UUID): AppResult<Unit> = AppResult.Success(Unit)
    }
}
