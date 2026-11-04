package com.isivoltpro.maginaolivo.feature.home

import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.domain.agenda.ReminderPreferences
import com.isivoltpro.maginaolivo.domain.farm.Farm
import com.isivoltpro.maginaolivo.domain.farm.FarmRepository
import com.isivoltpro.maginaolivo.domain.feed.FeedLocation
import com.isivoltpro.maginaolivo.domain.feed.FeedState
import com.isivoltpro.maginaolivo.domain.profile.ProfileDraft
import com.isivoltpro.maginaolivo.domain.profile.ProfileRepository
import com.isivoltpro.maginaolivo.domain.profile.ProfileSettings
import com.isivoltpro.maginaolivo.domain.weather.WeatherCondition
import com.isivoltpro.maginaolivo.domain.weather.WeatherFeed
import com.isivoltpro.maginaolivo.domain.weather.WeatherNow
import com.isivoltpro.maginaolivo.domain.workspace.WorkspaceRepository
import java.lang.reflect.Proxy
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
class WeatherWeekViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val workspace = UUID.randomUUID()
    private val now = Instant.parse("2026-10-03T10:00:00Z")
    private val sampleWeather = WeatherNow(21, WeatherCondition.CLEAR, 10, 12, now)

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun ambiguousFarmsAreResolvedWhenMunicipalityIsChosenInProfile() = runTest(dispatcher) {
        val profileState = MutableStateFlow(ProfileSettings())
        val viewModel = createViewModel(
            farms = listOf(farm("Bedmar"), farm("Jódar")),
            profileFlow = profileState,
            refreshSuccess = true,
        )
        backgroundScope.launch(dispatcher) { viewModel.state.collect() }

        // Without profile municipality, two different farm municipalities report ambiguity.
        assertNull(viewModel.state.value.location)
        assertTrue(viewModel.state.value.locationAmbiguous)
        assertEquals(FeedState.NoLocation, viewModel.state.value.weather)

        // Once the farmer chooses a municipality in Perfil, ambiguity clears and weather loads.
        profileState.value = ProfileSettings(municipality = "Bedmar", province = "Jaén")
        assertEquals(FeedLocation("Bedmar", "Jaén"), viewModel.state.value.location)
        assertFalse(viewModel.state.value.locationAmbiguous)
        assertTrue(viewModel.state.value.weather is FeedState.Value)
    }

    @Test
    fun singleMunicipalityTakesPrecedenceEvenWhenAnotherFarmHasBlankMunicipality() = runTest(dispatcher) {
        val viewModel = createViewModel(
            farms = listOf(farm("Bedmar"), farm("   ")),
            profileFlow = flowOf(ProfileSettings(municipality = "Úbeda", province = "Jaén")),
        )
        backgroundScope.launch(dispatcher) { viewModel.state.collect() }

        assertEquals(FeedLocation("Bedmar", "Jaén"), viewModel.state.value.location)
        assertFalse(viewModel.state.value.locationAmbiguous)
        assertTrue(viewModel.state.value.weather is FeedState.Value)
    }

    @Test
    fun offlineStaleCacheIsPreservedWhenManualRefreshFails() = runTest(dispatcher) {
        val staleFeedState = FeedState.Value(sampleWeather, "AEMET", now.minusSeconds(7200), stale = true)
        val viewModel = WeatherWeekViewModel(
            workspaces = object : WorkspaceRepository {
                override suspend fun ensureLocalWorkspace(): AppResult<UUID> = AppResult.Success(workspace)
            },
            farms = object : FarmRepository by unused() {
                override fun observeActive(workspaceId: UUID): Flow<List<Farm>> = flowOf(listOf(farm("Bedmar")))
            },
            weatherFeed = object : WeatherFeed {
                override fun observe(location: FeedLocation?): Flow<FeedState<WeatherNow>> = flowOf(staleFeedState)
                override suspend fun refreshIfStale(location: FeedLocation) {}
                override suspend fun refresh(location: FeedLocation): Boolean = false
            },
        )
        backgroundScope.launch(dispatcher) { viewModel.state.collect() }

        viewModel.refresh()
        val state = viewModel.state.value
        assertTrue(state.refreshFailed)
        assertFalse(state.refreshing)
        assertEquals(staleFeedState, state.weather)
    }

    @Test
    fun manualRefreshAndResumeAskTheWeatherFeedAndReportFailuresHonestly() = runTest(dispatcher) {
        val staleCalls = mutableListOf<FeedLocation>()
        var manualCalls = 0
        var shouldSucceed = false
        val viewModel = createViewModel(
            farms = listOf(farm("Bedmar")),
            profileFlow = flowOf(ProfileSettings()),
            refreshSuccess = {
                manualCalls++
                shouldSucceed
            },
            onRefreshIfStale = { staleCalls += it },
        )
        backgroundScope.launch(dispatcher) { viewModel.state.collect() }

        assertEquals(1, staleCalls.size)
        viewModel.onResumed()
        assertEquals(2, staleCalls.size)

        // Manual refresh fails offline: refreshFailed is true while cached weather stays.
        viewModel.refresh()
        assertEquals(1, manualCalls)
        assertFalse(viewModel.state.value.refreshing)
        assertTrue(viewModel.state.value.refreshFailed)
        assertTrue(viewModel.state.value.weather is FeedState.Value)

        // Manual retry succeeds: refreshFailed clears.
        shouldSucceed = true
        viewModel.refresh()
        assertEquals(2, manualCalls)
        assertFalse(viewModel.state.value.refreshing)
        assertFalse(viewModel.state.value.refreshFailed)
    }

    private fun createViewModel(
        farms: List<Farm>,
        profileFlow: Flow<ProfileSettings>,
        refreshSuccess: Boolean = true,
        onRefreshIfStale: (FeedLocation) -> Unit = {},
    ) = createViewModel(farms, profileFlow, { refreshSuccess }, onRefreshIfStale)

    private fun createViewModel(
        farms: List<Farm>,
        profileFlow: Flow<ProfileSettings>,
        refreshSuccess: () -> Boolean,
        onRefreshIfStale: (FeedLocation) -> Unit = {},
    ) = WeatherWeekViewModel(
        workspaces = object : WorkspaceRepository {
            override suspend fun ensureLocalWorkspace(): AppResult<UUID> = AppResult.Success(workspace)
        },
        farms = object : FarmRepository by unused() {
            override fun observeActive(workspaceId: UUID): Flow<List<Farm>> = flowOf(farms)
        },
        weatherFeed = object : WeatherFeed {
            override fun observe(location: FeedLocation?): Flow<FeedState<WeatherNow>> =
                if (location == null) flowOf(FeedState.NoLocation)
                else flowOf(FeedState.Value(sampleWeather, "AEMET", now, stale = false))

            override suspend fun refreshIfStale(location: FeedLocation) = onRefreshIfStale(location)
            override suspend fun refresh(location: FeedLocation): Boolean = refreshSuccess()
        },
        profile = object : ProfileRepository {
            override fun observe(): Flow<ProfileSettings> = profileFlow
            override suspend fun save(draft: ProfileDraft): AppResult<Unit> = AppResult.Success(Unit)
            override suspend fun saveReminders(preferences: ReminderPreferences): AppResult<Unit> = AppResult.Success(Unit)
        },
    )

    private fun farm(municipality: String) = Farm(
        UUID.randomUUID(), workspace, "Finca $municipality", null, municipality, "Jaén", null, null, 0L, null, null, null, 1L,
    )

    private inline fun <reified T : Any> unused(): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ -> error("Unexpected ${method.name}") } as T
}

