package com.isivoltpro.maginaolivo.feature.farms

import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.domain.farm.Farm
import com.isivoltpro.maginaolivo.domain.farm.FarmChanges
import com.isivoltpro.maginaolivo.domain.farm.FarmCoverRepository
import com.isivoltpro.maginaolivo.domain.farm.FarmRepository
import com.isivoltpro.maginaolivo.domain.farm.NewFarm
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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FarmDetailViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val farmId = UUID.fromString("20000000-0000-0000-0000-000000000060")

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun observesUpdatesAndArchivesWithoutNetwork() = runTest(dispatcher) {
        val repository = FakeFarmRepository(farm("La Solana"))
        val coverRepository = FakeFarmCoverRepository()
        val viewModel = FarmDetailViewModel(farmId, repository, coverRepository)
        advanceUntilIdle()

        assertEquals("La Solana", viewModel.state.value.farm?.name)
        assertFalse(viewModel.state.value.isLoading)

        viewModel.update(FarmDraft(name = "La Solana Norte", notes = "Poda hecha"))
        advanceUntilIdle()
        assertEquals("La Solana Norte", repository.updated?.name)
        assertEquals("Cambios guardados en este dispositivo", viewModel.state.value.message)

        viewModel.archive()
        advanceUntilIdle()
        assertEquals(farmId, repository.archivedId)
        assertEquals("Finca archivada", viewModel.state.value.message)

        viewModel.attachCover("content://test/farm-cover")
        advanceUntilIdle()
        assertEquals("content://test/farm-cover", coverRepository.attachedUri)
    }

    @Test
    fun blankNameDoesNotOverwriteFarm() = runTest(dispatcher) {
        val repository = FakeFarmRepository(farm("La Solana"))
        val viewModel = FarmDetailViewModel(farmId, repository, FakeFarmCoverRepository())
        advanceUntilIdle()

        viewModel.update(FarmDraft(name = "   "))
        advanceUntilIdle()

        assertEquals(null, repository.updated)
        assertEquals("Escribe un nombre para la finca", viewModel.state.value.nameError)
    }

    private fun farm(name: String) = Farm(
        id = farmId,
        workspaceId = UUID.fromString("10000000-0000-0000-0000-000000000060"),
        name = name,
        description = null,
        municipality = "Huelma",
        province = "Jaén",
        notes = null,
        coverDocumentId = null,
        parcelCount = 0,
        totalAreaM2 = null,
        activeCampaignName = null,
        archivedAt = null,
        version = 1,
    )

    private class FakeFarmRepository(initial: Farm) : FarmRepository {
        private val selected = MutableStateFlow<Farm?>(initial)
        var updated: FarmChanges? = null
        var archivedId: UUID? = null

        override fun observeActive(workspaceId: UUID): Flow<List<Farm>> = MutableStateFlow(emptyList())
        override fun observeArchived(workspaceId: UUID): Flow<List<Farm>> = MutableStateFlow(emptyList())
        override fun observeById(farmId: UUID): Flow<Farm?> = selected
        override suspend fun create(command: NewFarm): AppResult<UUID> = error("Not used")

        override suspend fun update(farmId: UUID, changes: FarmChanges): AppResult<Unit> {
            updated = changes
            return AppResult.Success(Unit)
        }

        override suspend fun archive(farmId: UUID): AppResult<Unit> {
            archivedId = farmId
            return AppResult.Success(Unit)
        }

        override suspend fun restore(farmId: UUID): AppResult<Unit> = error("Not used")
    }

    private class FakeFarmCoverRepository : FarmCoverRepository {
        private val cover = MutableStateFlow<String?>(null)
        var attachedUri: String? = null

        override fun observeCoverUri(farmId: UUID): Flow<String?> = cover

        override suspend fun attachCover(farmId: UUID, uri: String): AppResult<Unit> {
            attachedUri = uri
            cover.value = uri
            return AppResult.Success(Unit)
        }
    }
}
