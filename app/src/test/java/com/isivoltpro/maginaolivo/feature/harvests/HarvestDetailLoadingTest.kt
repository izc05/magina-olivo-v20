package com.isivoltpro.maginaolivo.feature.harvests

import com.isivoltpro.maginaolivo.core.time.AppClock
import com.isivoltpro.maginaolivo.domain.harvest.Harvest
import com.isivoltpro.maginaolivo.domain.harvest.HarvestContext
import com.isivoltpro.maginaolivo.domain.harvest.HarvestRepository
import com.isivoltpro.maginaolivo.domain.labour.LabourEntry
import com.isivoltpro.maginaolivo.domain.labour.LabourRepository
import com.isivoltpro.maginaolivo.domain.labour.Worker
import java.lang.reflect.Proxy
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

/**
 * Device check (build 683): Cuaderno → Jornal stayed on a spinner. The day loaded, then the
 * «previous crew» read — started before it and finished after it — wrote back a copy of the
 * state taken before the day arrived, returning the screen to «loading» for good.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HarvestDetailLoadingTest {
    private val dispatcher = StandardTestDispatcher()
    private val harvestId = UUID.randomUUID()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun aSlowPreviousCrewReadNeverUndoesTheLoadedDay() = runTest(dispatcher) {
        val dayArrives = CompletableDeferred<Unit>()
        val crewArrives = CompletableDeferred<Unit>()
        val harvests = object : HarvestRepository by unused() {
            // The day itself is not needed here: «no longer loading» is what the screen waits for.
            override fun observe(id: UUID): Flow<Harvest?> = flow {
                dayArrives.await()
                emit(null)
            }
            override fun observeContexts(): Flow<List<HarvestContext>> = flowOf(emptyList())
        }
        val labour = object : LabourRepository by unused() {
            override fun observeWorkers(): Flow<List<Worker>> = flowOf(emptyList())
            override fun observeForHarvest(harvestId: UUID): Flow<List<LabourEntry>> = flowOf(emptyList())
            override suspend fun previousCrew(harvestId: UUID): List<UUID> {
                crewArrives.await()
                return emptyList()
            }
        }
        val viewModel = HarvestDetailViewModel(harvestId, harvests, FixedClock, labour = labour)
        advanceUntilIdle()

        dayArrives.complete(Unit)
        advanceUntilIdle()
        assertFalse(viewModel.state.value.isLoading)

        crewArrives.complete(Unit)
        advanceUntilIdle()
        assertFalse("the day stays loaded after the previous crew arrives", viewModel.state.value.isLoading)
    }

    private object FixedClock : AppClock {
        override fun nowInstant(): Instant = Instant.parse("2026-11-20T09:00:00Z")
        override fun today(zoneId: ZoneId): LocalDate = LocalDate.of(2026, 11, 20)
    }

    private inline fun <reified T : Any> unused(): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ ->
            error("${T::class.simpleName}.${method.name} is not used by this test")
        } as T
}
