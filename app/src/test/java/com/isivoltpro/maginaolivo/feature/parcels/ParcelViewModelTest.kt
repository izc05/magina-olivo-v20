package com.isivoltpro.maginaolivo.feature.parcels

import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.domain.parcel.NewParcel
import com.isivoltpro.maginaolivo.domain.parcel.Parcel
import com.isivoltpro.maginaolivo.domain.parcel.ParcelChanges
import com.isivoltpro.maginaolivo.domain.parcel.ParcelMembership
import com.isivoltpro.maginaolivo.domain.parcel.ParcelRepository
import com.isivoltpro.maginaolivo.domain.parcel.RegistryLink
import com.isivoltpro.maginaolivo.domain.parcel.ParcelSource
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
class ParcelViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val farmId = UUID.fromString("20000000-0000-0000-0000-000000000070")
    private val parcelId = UUID.fromString("30000000-0000-0000-0000-000000000070")

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun farmParcelsObservesAndCreatesManualParcel() = runTest(dispatcher) {
        val repository = FakeParcelRepository()
        val viewModel = FarmParcelsViewModel(farmId, repository)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isLoading)
        viewModel.create(ParcelDraft(displayName = "Parcela Norte", managedAreaHectares = "1,25"))
        advanceUntilIdle()

        assertEquals("Parcela Norte", repository.created?.displayName)
        assertEquals(12_500.0, repository.created?.managedAreaM2)
        assertEquals(ParcelSource.MANUAL, repository.created?.source)
        assertEquals("Parcela guardada en este dispositivo", viewModel.state.value.message)
    }

    @Test
    fun invalidDraftDoesNotReachRepository() = runTest(dispatcher) {
        val repository = FakeParcelRepository()
        val viewModel = FarmParcelsViewModel(farmId, repository)
        advanceUntilIdle()

        viewModel.create(ParcelDraft(displayName = " ", managedAreaHectares = "-2"))
        advanceUntilIdle()

        assertNull(repository.created)
        assertEquals("Escribe un alias para la parcela", viewModel.state.value.nameError)
    }

    @Test
    fun detailEditsArchivesAndRestoresLocally() = runTest(dispatcher) {
        val repository = FakeParcelRepository(parcel())
        val viewModel = ParcelDetailViewModel(parcelId, repository)
        advanceUntilIdle()

        assertEquals("Parcela Norte", viewModel.state.value.parcel?.displayName)
        viewModel.update(
            ParcelDraft(
                displayName = "Parcela Alta",
                managedAreaHectares = "2",
                geometryGeoJson = "{\"type\":\"Polygon\",\"coordinates\":[]}",
                cadastralAreaM2 = 19_500.0,
            ),
        )
        advanceUntilIdle()
        assertEquals("Parcela Alta", repository.updated?.displayName)
        assertEquals(20_000.0, repository.updated?.managedAreaM2)
        assertEquals("{\"type\":\"Polygon\",\"coordinates\":[]}", repository.updated?.geometryGeoJson)
        assertEquals(19_500.0, repository.updated?.cadastralAreaM2)

        viewModel.archive()
        advanceUntilIdle()
        assertEquals(parcelId, repository.archivedId)

        viewModel.restore()
        advanceUntilIdle()
        assertEquals(farmId, repository.restoredFarmId)
    }

    private fun parcel() = Parcel(
        id = parcelId,
        workspaceId = UUID.fromString("10000000-0000-0000-0000-000000000070"),
        farmId = farmId,
        displayName = "Parcela Norte",
        cadastralReference = null,
        cadastralPolygon = null,
        cadastralParcel = null,
        municipality = "Huelma",
        province = "Jaén",
        source = ParcelSource.MANUAL,
        geometryGeoJson = null,
        cadastralAreaM2 = null,
        managedAreaM2 = 12_500.0,
        notes = null,
        archivedAt = null,
        version = 1,
    )

    private class FakeParcelRepository(initial: Parcel? = null) : ParcelRepository {
        private val selected = MutableStateFlow(initial)
        val active = MutableStateFlow(initial?.let(::listOf) ?: emptyList())
        val archived = MutableStateFlow<List<Parcel>>(emptyList())
        var created: NewParcel? = null
        var updated: ParcelChanges? = null
        var archivedId: UUID? = null
        var restoredFarmId: UUID? = null

        override fun observeActive(farmId: UUID): Flow<List<Parcel>> = active
        override fun observeArchived(farmId: UUID): Flow<List<Parcel>> = archived
        override fun observeById(parcelId: UUID): Flow<Parcel?> = selected
        override suspend fun linkToRegistry(parcelId: UUID, link: RegistryLink): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun findActiveByCadastralReference(workspaceId: UUID, reference: String): UUID? =
            active.value.firstOrNull { it.workspaceId == workspaceId && it.cadastralReference == reference }?.id
        override suspend fun create(command: NewParcel): AppResult<UUID> {
            created = command
            return AppResult.Success(UUID.randomUUID())
        }
        override suspend fun update(parcelId: UUID, changes: ParcelChanges): AppResult<Unit> {
            updated = changes
            return AppResult.Success(Unit)
        }
        override suspend fun archive(parcelId: UUID): AppResult<Unit> {
            archivedId = parcelId
            return AppResult.Success(Unit)
        }
        override suspend fun restore(parcelId: UUID, farmId: UUID): AppResult<Unit> {
            restoredFarmId = farmId
            return AppResult.Success(Unit)
        }
        override suspend fun membershipHistory(parcelId: UUID): List<ParcelMembership> = emptyList()
    }
}
