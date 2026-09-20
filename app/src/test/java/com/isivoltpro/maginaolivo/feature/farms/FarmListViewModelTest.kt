package com.isivoltpro.maginaolivo.feature.farms

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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FarmListViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val workspaceId = UUID.fromString("10000000-0000-0000-0000-000000000050")

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun bootstrapObservesActiveAndArchivedFarms() = runTest(dispatcher) {
        val repository = FakeFarmRepository()
        val viewModel = FarmListViewModel(
            farmRepository = repository,
            workspaceRepository = FakeWorkspaceRepository(workspaceId),
        )

        repository.active.value = listOf(farm("La Solana"))
        repository.archived.value = listOf(farm("El Portillo", archived = true))
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isLoading)
        assertEquals(listOf("La Solana"), viewModel.state.value.farms.map { it.name })
        assertEquals(listOf("El Portillo"), viewModel.state.value.archivedFarms.map { it.name })
    }

    @Test
    fun createUsesBootstrappedWorkspaceAndPublishesSuccess() = runTest(dispatcher) {
        val repository = FakeFarmRepository()
        val viewModel = FarmListViewModel(
            farmRepository = repository,
            workspaceRepository = FakeWorkspaceRepository(workspaceId),
        )
        advanceUntilIdle()

        viewModel.create(FarmDraft(name = "  La Solana  ", municipality = "Huelma"))
        advanceUntilIdle()

        assertEquals(workspaceId, repository.created?.workspaceId)
        assertEquals("  La Solana  ", repository.created?.name)
        assertEquals("Finca guardada en este dispositivo", viewModel.state.value.message)
        assertFalse(viewModel.state.value.isSaving)
    }

    @Test
    fun missingNameSurfacesFieldErrorWithoutCallingRepository() = runTest(dispatcher) {
        val repository = FakeFarmRepository()
        val viewModel = FarmListViewModel(
            farmRepository = repository,
            workspaceRepository = FakeWorkspaceRepository(workspaceId),
        )
        advanceUntilIdle()

        viewModel.create(FarmDraft(name = "   "))
        advanceUntilIdle()

        assertEquals(null, repository.created)
        assertEquals("Escribe un nombre para la finca", viewModel.state.value.nameError)
        assertTrue(viewModel.state.value.message == null)
    }

    private fun farm(
        name: String,
        archived: Boolean = false,
    ) = Farm(
        id = UUID.nameUUIDFromBytes(name.toByteArray()),
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
        archivedAt = if (archived) java.time.Instant.EPOCH else null,
        version = 1,
    )

    private class FakeWorkspaceRepository(
        private val id: UUID,
    ) : WorkspaceRepository {
        override suspend fun ensureLocalWorkspace(): AppResult<UUID> = AppResult.Success(id)
    }

    private class FakeFarmRepository : FarmRepository {
        val active = MutableStateFlow<List<Farm>>(emptyList())
        val archived = MutableStateFlow<List<Farm>>(emptyList())
        var created: NewFarm? = null

        override fun observeActive(workspaceId: UUID): Flow<List<Farm>> = active

        override fun observeArchived(workspaceId: UUID): Flow<List<Farm>> = archived

        override fun observeById(farmId: UUID): Flow<Farm?> = MutableStateFlow(null)

        override suspend fun create(command: NewFarm): AppResult<UUID> {
            created = command
            return AppResult.Success(UUID.fromString("20000000-0000-0000-0000-000000000050"))
        }

        override suspend fun update(farmId: UUID, changes: FarmChanges): AppResult<Unit> =
            AppResult.Success(Unit)

        override suspend fun archive(farmId: UUID): AppResult<Unit> = AppResult.Success(Unit)

        override suspend fun restore(farmId: UUID): AppResult<Unit> = AppResult.Success(Unit)
    }
}
