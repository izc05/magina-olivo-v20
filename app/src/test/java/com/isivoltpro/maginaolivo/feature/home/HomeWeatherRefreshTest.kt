package com.isivoltpro.maginaolivo.feature.home

import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.core.time.AppClock
import com.isivoltpro.maginaolivo.domain.activity.ActivityRepository
import com.isivoltpro.maginaolivo.domain.delivery.DeliveryRepository
import com.isivoltpro.maginaolivo.domain.farm.Farm
import com.isivoltpro.maginaolivo.domain.farm.FarmRepository
import com.isivoltpro.maginaolivo.domain.feed.FeedLocation
import com.isivoltpro.maginaolivo.domain.feed.FeedState
import com.isivoltpro.maginaolivo.domain.harvest.HarvestRepository
import com.isivoltpro.maginaolivo.domain.weather.WeatherFeed
import com.isivoltpro.maginaolivo.domain.weather.WeatherNow
import com.isivoltpro.maginaolivo.domain.workspace.WorkspaceRepository
import java.lang.reflect.Proxy
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** #315: entering/resuming Inicio asks the weather feed again; the feed decides if it is stale. */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeWeatherRefreshTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val workspace = UUID.randomUUID()
    private val asked = mutableListOf<FeedLocation>()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun resumingInicioAsksForTheFarmsPlaceAgain() = runTest(dispatcher) {
        val model = model(farms = listOf(farm("Bedmar")))
        backgroundScope.launch(dispatcher) { model.state.collect() }
        assertEquals(listOf(FeedLocation("Bedmar", "Jaén")), asked)
        model.onResumed()
        model.onResumed()
        assertEquals(3, asked.size)
        assertEquals(setOf(FeedLocation("Bedmar", "Jaén").key), asked.map { it.key }.toSet())
    }

    @Test fun withoutAPlaceResumingAsksNothing() = runTest(dispatcher) {
        val model = model(farms = emptyList())
        backgroundScope.launch(dispatcher) { model.state.collect() }
        model.onResumed()
        assertEquals(emptyList<FeedLocation>(), asked)
    }

    private fun model(farms: List<Farm>) = HomeViewModel(
        workspaces = object : WorkspaceRepository {
            override suspend fun ensureLocalWorkspace(): AppResult<UUID> = AppResult.Success(workspace)
        },
        farms = object : FarmRepository by unused() {
            override fun observeActive(workspaceId: UUID): Flow<List<Farm>> = flowOf(farms)
        },
        activities = object : ActivityRepository by unused() {
            override fun observeAgenda() = flowOf(emptyList<com.isivoltpro.maginaolivo.domain.activity.AgendaEntry>())
        },
        harvests = object : HarvestRepository by unused() {
            override fun observeAll() = flowOf(emptyList<com.isivoltpro.maginaolivo.domain.harvest.Harvest>())
            override fun observeContexts() = flowOf(emptyList<com.isivoltpro.maginaolivo.domain.harvest.HarvestContext>())
        },
        deliveries = object : DeliveryRepository by unused() {
            override fun observeAll() = flowOf(emptyList<com.isivoltpro.maginaolivo.domain.delivery.Delivery>())
        },
        clock = object : AppClock {
            override fun nowInstant(): Instant = Instant.parse("2026-11-26T09:00:00Z")
            override fun today(zoneId: ZoneId): LocalDate = LocalDate.of(2026, 11, 26)
        },
        zone = { ZoneOffset.UTC },
        weatherFeed = object : WeatherFeed {
            override fun observe(location: FeedLocation?): Flow<FeedState<WeatherNow>> = flowOf(FeedState.Unavailable)
            override suspend fun refreshIfStale(location: FeedLocation) { asked += location }
            override suspend fun refresh(location: FeedLocation): Boolean = true
        },
    )

    private fun farm(municipality: String) = Farm(
        UUID.randomUUID(), workspace, "La Solana", null, municipality, "Jaén", null, null, 0L, null, null, null, 1L,
    )

    private inline fun <reified T : Any> unused(): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ -> error("Unexpected ${method.name}") } as T
}
