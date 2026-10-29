package com.isivoltpro.maginaolivo.feature.irrigation

import com.isivoltpro.maginaolivo.data.local.model.ActivityStatus
import com.isivoltpro.maginaolivo.domain.activity.Activity
import com.isivoltpro.maginaolivo.domain.activity.ActivityChanges
import com.isivoltpro.maginaolivo.domain.activity.ActivityDetail
import com.isivoltpro.maginaolivo.domain.activity.ActivityParcelOption
import com.isivoltpro.maginaolivo.domain.activity.ActivityParcelTarget
import com.isivoltpro.maginaolivo.domain.activity.ActivityRepository
import com.isivoltpro.maginaolivo.domain.activity.ActivityType
import com.isivoltpro.maginaolivo.domain.activity.AgendaEntry
import com.isivoltpro.maginaolivo.domain.activity.IrrigationPrice
import com.isivoltpro.maginaolivo.domain.activity.IrrigationPricingBasis
import com.isivoltpro.maginaolivo.domain.activity.NewActivity
import com.isivoltpro.maginaolivo.domain.machinery.MachineOption
import com.isivoltpro.maginaolivo.domain.parcel.IrrigationSystem
import com.isivoltpro.maginaolivo.domain.parcel.NewParcel
import com.isivoltpro.maginaolivo.domain.parcel.Parcel
import com.isivoltpro.maginaolivo.domain.parcel.ParcelAgronomy
import com.isivoltpro.maginaolivo.domain.parcel.ParcelChanges
import com.isivoltpro.maginaolivo.domain.parcel.ParcelMembership
import com.isivoltpro.maginaolivo.domain.parcel.ParcelRepository
import com.isivoltpro.maginaolivo.domain.parcel.ParcelSource
import com.isivoltpro.maginaolivo.domain.parcel.RegistryLink
import com.isivoltpro.maginaolivo.core.common.AppResult
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class IrrigationViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val farmId = UUID.randomUUID()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeParcelRepository(
        private val activeFlow: MutableStateFlow<List<Parcel>>,
    ) : ParcelRepository {
        override fun observeActive(farmId: UUID): Flow<List<Parcel>> = activeFlow
        override fun observeArchived(farmId: UUID): Flow<List<Parcel>> = flowOf(emptyList())
        override fun observeById(parcelId: UUID): Flow<Parcel?> = flowOf(null)
        override suspend fun findActiveByCadastralReference(workspaceId: UUID, reference: String): UUID? = null
        override suspend fun create(command: NewParcel): AppResult<UUID> = AppResult.Success(UUID.randomUUID())
        override suspend fun update(parcelId: UUID, changes: ParcelChanges): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun linkToRegistry(parcelId: UUID, link: RegistryLink): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun archive(parcelId: UUID): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun restore(parcelId: UUID, farmId: UUID): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun membershipHistory(parcelId: UUID): List<ParcelMembership> = emptyList()
    }

    private class FakeActivityRepository(
        private val farmActivitiesFlow: MutableStateFlow<List<Activity>>,
    ) : ActivityRepository {
        override fun observeForFarm(farmId: UUID): Flow<List<Activity>> = farmActivitiesFlow
        override fun observeForParcel(parcelId: UUID): Flow<List<Activity>> = flowOf(emptyList())
        override fun observe(id: UUID): Flow<Activity?> = flowOf(null)
        override fun observeAgenda(): Flow<List<AgendaEntry>> = flowOf(emptyList())
        override fun observeSelectableMachines(): Flow<List<MachineOption>> = flowOf(emptyList())
        override fun observeSelectableParcels(farmId: UUID): Flow<List<ActivityParcelOption>> = flowOf(emptyList())
        override suspend fun create(command: NewActivity): AppResult<UUID> = AppResult.Success(UUID.randomUUID())
        override suspend fun update(id: UUID, changes: ActivityChanges): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun correctCompleted(id: UUID, changes: ActivityChanges): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun plan(id: UUID): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun complete(id: UUID): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun cancel(id: UUID): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun reopen(id: UUID): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun archive(id: UUID): AppResult<Unit> = AppResult.Success(Unit)
    }

    private fun sampleParcel(
        id: UUID = UUID.randomUUID(),
        name: String,
        system: IrrigationSystem? = IrrigationSystem.DRIP,
        network: String? = "Comunidad San Isidro",
        sector: String? = "Sector 1",
        days: Set<DayOfWeek> = setOf(DayOfWeek.TUESDAY),
        oliveCount: Int? = 400,
    ) = Parcel(
        id = id,
        workspaceId = UUID.randomUUID(),
        farmId = farmId,
        displayName = name,
        cadastralReference = null,
        cadastralPolygon = "1",
        cadastralParcel = "2",
        municipality = "Bedmar",
        province = "Jaén",
        source = ParcelSource.MANUAL,
        geometryGeoJson = null,
        cadastralAreaM2 = 20000.0,
        managedAreaM2 = 20000.0,
        notes = null,
        archivedAt = null,
        version = 1L,
        agronomy = ParcelAgronomy(
            oliveTreeCount = oliveCount,
            irrigationSystem = system,
            irrigationNetwork = network,
            irrigationSector = sector,
            irrigationDays = days,
        ),
    )

    private fun sampleActivity(
        id: UUID = UUID.randomUUID(),
        volumeM3: Double = 150.0,
        durationMinutes: Int = 180,
        estimatedCostMinor: Long = 4500L,
    ) = Activity(
        id = id,
        workspaceId = UUID.randomUUID(),
        farmId = farmId,
        campaignId = null,
        type = ActivityType.IRRIGATION,
        status = ActivityStatus.COMPLETED,
        activityDate = LocalDate.of(2026, 6, 20),
        description = "Riego regular de verano",
        notes = null,
        targets = listOf(ActivityParcelTarget(parcelId = UUID.randomUUID(), parcelName = "El Cerrajón")),
        detail = ActivityDetail.Irrigation(
            volumeM3 = volumeM3,
            durationMinutes = durationMinutes,
            price = IrrigationPrice(
                basis = IrrigationPricingBasis.PER_M3,
                priceDate = LocalDate.of(2026, 6, 20),
                estimatedAmountMinor = estimatedCostMinor,
            ),
        ),
        version = 1L,
        costMinor = estimatedCostMinor,
    )

    @Test
    fun init_loadsParcelsAndActivities_computesSummaryAndPlanCorrectly() = runTest {
        val parcelA = sampleParcel(name = "El Cerrajón", system = IrrigationSystem.DRIP, oliveCount = 300)
        val parcelB = sampleParcel(name = "La Loma", system = IrrigationSystem.DRYLAND, oliveCount = 200)

        val activity = sampleActivity(volumeM3 = 120.0, durationMinutes = 120, estimatedCostMinor = 3600L)

        val parcelsFlow = MutableStateFlow(listOf(parcelA, parcelB))
        val activitiesFlow = MutableStateFlow(listOf(activity))

        val viewModel = IrrigationViewModel(
            farmId = farmId,
            parcelRepository = FakeParcelRepository(parcelsFlow),
            activityRepository = FakeActivityRepository(activitiesFlow),
            today = LocalDate.of(2026, 6, 23), // Martes
        )

        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(false, state.isLoading)
        assertEquals(1, state.irrigatedParcels.size)
        assertEquals("El Cerrajón", state.irrigatedParcels[0].displayName)

        // Summary
        assertEquals(120.0, state.summary.realStats.totalVolumeM3, 0.001)
        assertEquals(2.0, state.summary.realStats.totalDurationHours, 0.001)
        assertEquals(3600L, state.summary.realStats.totalCostMinor)
        assertEquals(1, state.summary.realStats.completedActivitiesCount)

        // Weekly Plan
        assertEquals(1, state.weeklyPlan.totalScheduledTurns)
        assertEquals(DayOfWeek.TUESDAY, state.selectedDay)

        // Calculator preloaded with first irrigated parcel
        assertEquals(parcelA.id, state.calculator.parcelId)
        assertEquals("El Cerrajón", state.calculator.parcelName)
        assertEquals("300", state.calculator.oliveTreeCountText)
        assertNotNull(state.calculator.calculatedDose)
    }

    @Test
    fun emptyInputs_initializesCleanZeroedState() = runTest {
        val viewModel = IrrigationViewModel(
            farmId = farmId,
            parcelRepository = FakeParcelRepository(MutableStateFlow(emptyList())),
            activityRepository = FakeActivityRepository(MutableStateFlow(emptyList())),
        )

        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(false, state.isLoading)
        assertTrue(state.irrigatedParcels.isEmpty())
        assertTrue(state.recentActivities.isEmpty())
        assertEquals(0.0, state.summary.realStats.totalVolumeM3, 0.001)
        assertEquals(0L, state.summary.realStats.totalDurationMinutes)
    }

    @Test
    fun selectTab_updatesTabInState() = runTest {
        val viewModel = IrrigationViewModel(
            farmId = farmId,
            parcelRepository = FakeParcelRepository(MutableStateFlow(emptyList())),
            activityRepository = FakeActivityRepository(MutableStateFlow(emptyList())),
        )

        assertEquals(IrrigationTab.SUMMARY, viewModel.state.value.selectedTab)

        viewModel.selectTab(IrrigationTab.INSTALLATIONS)
        assertEquals(IrrigationTab.INSTALLATIONS, viewModel.state.value.selectedTab)

        viewModel.selectTab(IrrigationTab.HISTORY)
        assertEquals(IrrigationTab.HISTORY, viewModel.state.value.selectedTab)
    }

    @Test
    fun selectDay_updatesSelectedDay() = runTest {
        val viewModel = IrrigationViewModel(
            farmId = farmId,
            parcelRepository = FakeParcelRepository(MutableStateFlow(emptyList())),
            activityRepository = FakeActivityRepository(MutableStateFlow(emptyList())),
        )

        viewModel.selectDay(DayOfWeek.FRIDAY)
        assertEquals(DayOfWeek.FRIDAY, viewModel.state.value.selectedDay)
    }

    @Test
    fun loadParcelForCalculator_switchesToInstallationsAndSetsFields() = runTest {
        val parcel = sampleParcel(name = "Olivar Nuevo", oliveCount = 550)

        val viewModel = IrrigationViewModel(
            farmId = farmId,
            parcelRepository = FakeParcelRepository(MutableStateFlow(listOf(parcel))),
            activityRepository = FakeActivityRepository(MutableStateFlow(emptyList())),
        )

        advanceUntilIdle()

        viewModel.loadParcelForCalculator(parcel)

        val state = viewModel.state.value
        assertEquals(IrrigationTab.INSTALLATIONS, state.selectedTab)
        assertEquals(parcel.id, state.calculator.parcelId)
        assertEquals("Olivar Nuevo", state.calculator.parcelName)
        assertEquals("550", state.calculator.oliveTreeCountText)
        assertNotNull(state.calculator.calculatedDose)
    }

    @Test
    fun calculatorFieldUpdates_recalculatesDoseOrSetsError() = runTest {
        val viewModel = IrrigationViewModel(
            farmId = farmId,
            parcelRepository = FakeParcelRepository(MutableStateFlow(emptyList())),
            activityRepository = FakeActivityRepository(MutableStateFlow(emptyList())),
        )

        // 100 olivos, 2 goteros/olivo, 4.0 L/h, 60 min -> 100 * 2 * 4 * 1h = 800 L = 0.8 m³ (8.0 L/olivo)
        viewModel.updateTrees("100")
        viewModel.updateEmittersPerTree("2")
        viewModel.updateFlowLph("4.0")
        viewModel.updateDuration("60")

        val state = viewModel.state.value
        val dose = state.calculator.calculatedDose
        assertNotNull(dose)
        assertEquals(0.8, dose!!.theoreticalVolumeM3, 0.001)
        assertEquals(8.0, dose.litersPerTree, 0.001)
        assertNull(state.calculator.error)

        // Input vacío o no válido -> error
        viewModel.updateTrees("")
        val stateInvalid = viewModel.state.value
        assertNull(stateInvalid.calculator.calculatedDose)
        assertEquals("Introduce el número de olivos", stateInvalid.calculator.error)
    }
}
