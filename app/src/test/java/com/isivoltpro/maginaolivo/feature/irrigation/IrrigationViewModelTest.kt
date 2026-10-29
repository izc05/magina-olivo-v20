package com.isivoltpro.maginaolivo.feature.irrigation

import com.isivoltpro.maginaolivo.data.local.model.ActivityStatus
import com.isivoltpro.maginaolivo.data.local.model.FarmStatus
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
import com.isivoltpro.maginaolivo.domain.farm.Farm
import com.isivoltpro.maginaolivo.domain.farm.FarmChanges
import com.isivoltpro.maginaolivo.domain.farm.FarmRepository
import com.isivoltpro.maginaolivo.domain.farm.NewFarm
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
import java.time.Instant
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

    private class FakeFarmRepository(
        private val farmFlow: MutableStateFlow<Farm?>,
    ) : FarmRepository {
        override fun observeActive(workspaceId: UUID): Flow<List<Farm>> = flowOf(emptyList())
        override fun observeArchived(workspaceId: UUID): Flow<List<Farm>> = flowOf(emptyList())
        override fun observeById(farmId: UUID): Flow<Farm?> = farmFlow
        override suspend fun create(command: NewFarm): AppResult<UUID> = AppResult.Success(UUID.randomUUID())
        override suspend fun update(farmId: UUID, changes: FarmChanges): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun archive(farmId: UUID): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun restore(farmId: UUID): AppResult<Unit> = AppResult.Success(Unit)
    }

    private fun sampleFarm(
        id: UUID = farmId,
        name: String = "Finca Huerta del Pozo",
    ) = Farm(
        id = id,
        workspaceId = UUID.randomUUID(),
        name = name,
        description = "Finca con sistema de riego localizado",
        municipality = "Bedmar",
        province = "Jaén",
        notes = null,
        coverDocumentId = null,
        parcelCount = 1L,
        totalAreaM2 = 20000.0,
        activeCampaignName = null,
        archivedAt = null,
        version = 1L,
    )

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
    fun init_withAuthorizedFarm_loadsParcelsAndActivities_computesSummaryAndPlanCorrectly() = runTest {
        val parcelA = sampleParcel(name = "El Cerrajón", system = IrrigationSystem.DRIP, oliveCount = 300)
        val parcelB = sampleParcel(name = "La Loma", system = IrrigationSystem.DRYLAND, oliveCount = 200)
        val activity = sampleActivity(volumeM3 = 120.0, durationMinutes = 120, estimatedCostMinor = 3600L)

        val parcelsFlow = MutableStateFlow(listOf(parcelA, parcelB))
        val activitiesFlow = MutableStateFlow(listOf(activity))
        val farmFlow = MutableStateFlow<Farm?>(sampleFarm())

        val viewModel = IrrigationViewModel(
            farmId = farmId,
            parcelRepository = FakeParcelRepository(parcelsFlow),
            activityRepository = FakeActivityRepository(activitiesFlow),
            farmRepository = FakeFarmRepository(farmFlow),
            today = LocalDate.of(2026, 6, 23), // Martes
        )

        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(false, state.isLoading)
        assertEquals(false, state.farmNotFound)
        assertEquals("Finca Huerta del Pozo", state.farmName)
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

        // Calculator preloaded with first irrigated parcel (clean, no fake defaults)
        assertEquals(parcelA.id, state.calculator.parcelId)
        assertEquals("El Cerrajón", state.calculator.parcelName)
        assertEquals("300", state.calculator.oliveTreeCountText)
        assertNull(state.calculator.calculatedDose)
    }

    @Test
    fun pendingAuthorization_initialStateIsFailClosedWithLoadingAndNoData() = runTest {
        // farmFlow does NOT emit an authorized farm yet
        val farmFlow = MutableStateFlow<Farm?>(null)
        val parcelsFlow = MutableStateFlow(listOf(sampleParcel(name = "Parcela Confidencial")))
        val activitiesFlow = MutableStateFlow(listOf(sampleActivity()))

        val viewModel = IrrigationViewModel(
            farmId = farmId,
            parcelRepository = FakeParcelRepository(parcelsFlow),
            activityRepository = FakeActivityRepository(activitiesFlow),
            farmRepository = FakeFarmRepository(farmFlow),
        )

        // Prior to running or when pending:
        val initialState = viewModel.state.value
        assertEquals(true, initialState.isLoading)
        assertEquals(false, initialState.farmNotFound)
        assertNull(initialState.farmName)
        assertTrue(initialState.irrigatedParcels.isEmpty())
        assertTrue(initialState.recentActivities.isEmpty())
        assertEquals(0.0, initialState.summary.realStats.totalVolumeM3, 0.001)
        assertNull(initialState.calculator.parcelId)

        // Actions attempted while loading are ignored
        viewModel.selectTab(IrrigationTab.INSTALLATIONS)
        assertEquals(IrrigationTab.SUMMARY, viewModel.state.value.selectedTab)

        viewModel.updateTrees("200")
        assertEquals("", viewModel.state.value.calculator.oliveTreeCountText)

        viewModel.loadSimulationExample()
        assertEquals(false, viewModel.state.value.calculator.isSimulationMode)
    }

    @Test
    fun farmInAnotherWorkspaceOrNonExistent_failClosedLocksStateAndPurgesData() = runTest {
        val farmFlow = MutableStateFlow<Farm?>(null) // Repositorio emite null para finca ajena
        val parcelsFlow = MutableStateFlow(listOf(sampleParcel(name = "Parcela Ajena")))
        val activitiesFlow = MutableStateFlow(listOf(sampleActivity(volumeM3 = 500.0)))

        val viewModel = IrrigationViewModel(
            farmId = farmId,
            parcelRepository = FakeParcelRepository(parcelsFlow),
            activityRepository = FakeActivityRepository(activitiesFlow),
            farmRepository = FakeFarmRepository(farmFlow),
        )

        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(true, state.farmNotFound)
        assertEquals(false, state.isLoading)
        assertNull(state.farmName)
        assertEquals("Finca no disponible o no autorizada", state.errorMessage)

        // Verificación estricta: los datos de parcelas y actividades NUNCA se cargaron ni permanecen en memoria
        assertTrue("Las parcelas deben estar vacías ante finca no autorizada", state.irrigatedParcels.isEmpty())
        assertTrue("El historial debe estar vacío ante finca no autorizada", state.recentActivities.isEmpty())
        assertEquals(0.0, state.summary.realStats.totalVolumeM3, 0.001)
        assertEquals(0, state.weeklyPlan.totalScheduledTurns)
        assertNull(state.calculator.parcelId)

        // Operaciones bloqueadas
        viewModel.selectTab(IrrigationTab.HISTORY)
        assertEquals(IrrigationTab.SUMMARY, viewModel.state.value.selectedTab)
        viewModel.loadSimulationExample()
        assertEquals(false, viewModel.state.value.calculator.isSimulationMode)
    }

    @Test
    fun sessionRevocation_clearsProtectedDataImmediately() = runTest {
        val farmFlow = MutableStateFlow<Farm?>(sampleFarm())
        val parcel = sampleParcel(name = "Olivo Guadalquivir", system = IrrigationSystem.DRIP)
        val activity = sampleActivity(volumeM3 = 80.0)

        val viewModel = IrrigationViewModel(
            farmId = farmId,
            parcelRepository = FakeParcelRepository(MutableStateFlow(listOf(parcel))),
            activityRepository = FakeActivityRepository(MutableStateFlow(listOf(activity))),
            farmRepository = FakeFarmRepository(farmFlow),
        )

        advanceUntilIdle()

        // 1. Estado autorizado y poblado
        val authorizedState = viewModel.state.value
        assertEquals(false, authorizedState.farmNotFound)
        assertEquals("Finca Huerta del Pozo", authorizedState.farmName)
        assertEquals(1, authorizedState.irrigatedParcels.size)
        assertEquals(80.0, authorizedState.summary.realStats.totalVolumeM3, 0.001)
        assertNotNull(authorizedState.calculator.parcelId)

        // 2. Revocación de acceso en caliente (logout, cambio de workspace o revocación de permisos)
        farmFlow.value = null
        advanceUntilIdle()

        // 3. Verificar purga inmediata y completa en memoria
        val revokedState = viewModel.state.value
        assertEquals(true, revokedState.farmNotFound)
        assertEquals(false, revokedState.isLoading)
        assertNull(revokedState.farmName)
        assertEquals("Finca no disponible o no autorizada", revokedState.errorMessage)
        assertTrue("Los datos de parcelas deben ser purgados inmediatamente", revokedState.irrigatedParcels.isEmpty())
        assertTrue("El historial debe ser purgado inmediatamente", revokedState.recentActivities.isEmpty())
        assertEquals(0.0, revokedState.summary.realStats.totalVolumeM3, 0.001)
        assertEquals(0, revokedState.weeklyPlan.totalScheduledTurns)
        assertNull(revokedState.calculator.parcelId)
        assertEquals(false, revokedState.calculator.isSimulationMode)
    }

    @Test
    fun sessionRecovery_loadsFreshDataWithoutStaleResiduals() = runTest {
        val farmFlow = MutableStateFlow<Farm?>(null) // Inicia no autorizada
        val parcel = sampleParcel(name = "Haza Nueva", system = IrrigationSystem.DRIP, oliveCount = 350)
        val activity = sampleActivity(volumeM3 = 60.0)

        val viewModel = IrrigationViewModel(
            farmId = farmId,
            parcelRepository = FakeParcelRepository(MutableStateFlow(listOf(parcel))),
            activityRepository = FakeActivityRepository(MutableStateFlow(listOf(activity))),
            farmRepository = FakeFarmRepository(farmFlow),
        )

        advanceUntilIdle()
        assertEquals(true, viewModel.state.value.farmNotFound)

        // Ahora el usuario se autentica o recibe acceso a la finca
        farmFlow.value = sampleFarm(name = "Finca Recuperada")
        advanceUntilIdle()

        val recoveredState = viewModel.state.value
        assertEquals(false, recoveredState.farmNotFound)
        assertEquals(false, recoveredState.isLoading)
        assertEquals("Finca Recuperada", recoveredState.farmName)
        assertNull(recoveredState.errorMessage)
        assertEquals(1, recoveredState.irrigatedParcels.size)
        assertEquals(60.0, recoveredState.summary.realStats.totalVolumeM3, 0.001)
        assertEquals(parcel.id, recoveredState.calculator.parcelId)
    }

    @Test
    fun workspaceSwitchDuringActiveSession_purgesProtectedDataAndBlocksActions() = runTest {
        // Escenario: usuario en Workspace A con Finca A visible
        val farmFlow = MutableStateFlow<Farm?>(sampleFarm(name = "Finca Workspace A"))
        val parcel = sampleParcel(name = "Parcela Workspace A", system = IrrigationSystem.DRIP)
        val activity = sampleActivity(volumeM3 = 100.0)

        val viewModel = IrrigationViewModel(
            farmId = farmId,
            parcelRepository = FakeParcelRepository(MutableStateFlow(listOf(parcel))),
            activityRepository = FakeActivityRepository(MutableStateFlow(listOf(activity))),
            farmRepository = FakeFarmRepository(farmFlow),
        )

        advanceUntilIdle()
        assertEquals("Finca Workspace A", viewModel.state.value.farmName)
        assertEquals(1, viewModel.state.value.irrigatedParcels.size)

        // Cambio de Workspace A -> B durante la sesion activa: el repositorio resuelve el nuevo ambito y emite null
        farmFlow.value = null
        advanceUntilIdle()

        val stateAfterSwitch = viewModel.state.value
        assertEquals(true, stateAfterSwitch.farmNotFound)
        assertNull(stateAfterSwitch.farmName)
        assertTrue("Ningun dato del Workspace A debe quedar accesible", stateAfterSwitch.irrigatedParcels.isEmpty())
        assertTrue("El historial debe estar limpio", stateAfterSwitch.recentActivities.isEmpty())
        assertEquals(0.0, stateAfterSwitch.summary.realStats.totalVolumeM3, 0.001)

        // Intento de interaccion bloqueado
        viewModel.selectTab(IrrigationTab.INSTALLATIONS)
        assertEquals(IrrigationTab.SUMMARY, viewModel.state.value.selectedTab)
    }

    @Test
    fun archivedFarm_allowsReadingHistoryAndComputesMetrics_flagsArchived() = runTest {
        // Contrato: fincas archivadas permiten lectura/historico pero se marcan como archivadas
        val archivedFarm = sampleFarm(name = "Finca Antigua").copy(
            archivedAt = Instant.now(),
        )
        val parcel = sampleParcel(name = "Parcela Historica", system = IrrigationSystem.DRIP)
        val activity = sampleActivity(volumeM3 = 150.0, estimatedCostMinor = 4500L)

        val viewModel = IrrigationViewModel(
            farmId = farmId,
            parcelRepository = FakeParcelRepository(MutableStateFlow(listOf(parcel))),
            activityRepository = FakeActivityRepository(MutableStateFlow(listOf(activity))),
            farmRepository = FakeFarmRepository(MutableStateFlow(archivedFarm)),
        )

        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(false, state.isLoading)
        assertEquals(false, state.farmNotFound)
        assertEquals(true, state.isFarmArchived)
        assertEquals("Finca Antigua", state.farmName)
        assertEquals(150.0, state.summary.realStats.totalVolumeM3, 0.001)
        assertEquals(1, state.recentActivities.size)
    }

    @Test
    fun emptyParcelsAndActivities_initializesCleanZeroedState() = runTest {
        val viewModel = IrrigationViewModel(
            farmId = farmId,
            parcelRepository = FakeParcelRepository(MutableStateFlow(emptyList())),
            activityRepository = FakeActivityRepository(MutableStateFlow(emptyList())),
            farmRepository = FakeFarmRepository(MutableStateFlow(sampleFarm())),
        )

        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(false, state.isLoading)
        assertEquals(false, state.farmNotFound)
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
            farmRepository = FakeFarmRepository(MutableStateFlow(sampleFarm())),
        )

        advanceUntilIdle()
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
            farmRepository = FakeFarmRepository(MutableStateFlow(sampleFarm())),
        )

        advanceUntilIdle()
        viewModel.selectDay(DayOfWeek.FRIDAY)
        assertEquals(DayOfWeek.FRIDAY, viewModel.state.value.selectedDay)
    }

    @Test
    fun loadParcelForCalculator_switchesToInstallationsAndLeavesInputsEmptyWithoutFakeDefaults() = runTest {
        val parcel = sampleParcel(name = "Olivar Nuevo", oliveCount = 550)

        val viewModel = IrrigationViewModel(
            farmId = farmId,
            parcelRepository = FakeParcelRepository(MutableStateFlow(listOf(parcel))),
            activityRepository = FakeActivityRepository(MutableStateFlow(emptyList())),
            farmRepository = FakeFarmRepository(MutableStateFlow(sampleFarm())),
        )

        advanceUntilIdle()

        viewModel.loadParcelForCalculator(parcel)

        val state = viewModel.state.value
        assertEquals(IrrigationTab.INSTALLATIONS, state.selectedTab)
        assertEquals(parcel.id, state.calculator.parcelId)
        assertEquals("Olivar Nuevo", state.calculator.parcelName)
        assertEquals("550", state.calculator.oliveTreeCountText)
        assertEquals("", state.calculator.emittersPerTreeText)
        assertEquals("", state.calculator.emitterFlowLphText)
        assertEquals("", state.calculator.durationMinutesText)
        assertEquals(false, state.calculator.isSimulationMode)
        assertNull(state.calculator.calculatedDose)
    }

    @Test
    fun simulationMode_populatesExampleValuesAndCalculatesDose() = runTest {
        val viewModel = IrrigationViewModel(
            farmId = farmId,
            parcelRepository = FakeParcelRepository(MutableStateFlow(emptyList())),
            activityRepository = FakeActivityRepository(MutableStateFlow(emptyList())),
            farmRepository = FakeFarmRepository(MutableStateFlow(sampleFarm())),
        )

        advanceUntilIdle()

        viewModel.loadSimulationExample()

        val state = viewModel.state.value
        assertEquals(true, state.calculator.isSimulationMode)
        assertEquals("2", state.calculator.emittersPerTreeText)
        assertEquals("4.0", state.calculator.emitterFlowLphText)
        assertEquals("120", state.calculator.durationMinutesText)
        assertNotNull(state.calculator.calculatedDose)

        // Limpiar simulación restablece el estado
        viewModel.clearSimulation()
        val cleared = viewModel.state.value
        assertEquals(false, cleared.calculator.isSimulationMode)
        assertEquals("", cleared.calculator.emittersPerTreeText)
        assertEquals("", cleared.calculator.emitterFlowLphText)
        assertEquals("", cleared.calculator.durationMinutesText)
        assertNull(cleared.calculator.calculatedDose)
    }

    @Test
    fun calculatorFieldUpdates_recalculatesDoseOrSetsError() = runTest {
        val viewModel = IrrigationViewModel(
            farmId = farmId,
            parcelRepository = FakeParcelRepository(MutableStateFlow(emptyList())),
            activityRepository = FakeActivityRepository(MutableStateFlow(emptyList())),
            farmRepository = FakeFarmRepository(MutableStateFlow(sampleFarm())),
        )

        advanceUntilIdle()

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

        // Campo inválido (0 olivos)
        viewModel.updateTrees("0")
        val stateInvalid = viewModel.state.value
        assertNull(stateInvalid.calculator.calculatedDose)
        assertEquals("Introduce valores mayores que cero", stateInvalid.calculator.error)
    }
}
