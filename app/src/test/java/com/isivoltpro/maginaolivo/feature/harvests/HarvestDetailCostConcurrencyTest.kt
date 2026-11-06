package com.isivoltpro.maginaolivo.feature.harvests

import com.isivoltpro.maginaolivo.core.common.AppError
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.core.time.AppClock
import com.isivoltpro.maginaolivo.domain.expense.Expense
import com.isivoltpro.maginaolivo.domain.expense.ExpenseCategory
import com.isivoltpro.maginaolivo.domain.expense.ExpenseDraft
import com.isivoltpro.maginaolivo.domain.expense.ExpenseOrigin
import com.isivoltpro.maginaolivo.domain.expense.ExpenseRepository
import com.isivoltpro.maginaolivo.domain.expense.ExpenseStatus
import com.isivoltpro.maginaolivo.domain.expense.JornadaExpenseKind
import com.isivoltpro.maginaolivo.domain.harvest.Harvest
import com.isivoltpro.maginaolivo.domain.harvest.HarvestContext
import com.isivoltpro.maginaolivo.domain.harvest.HarvestRepository
import java.lang.reflect.Proxy
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HarvestDetailCostConcurrencyTest {
    private val dispatcher = StandardTestDispatcher()
    private val harvestId = UUID.randomUUID()
    private val workspaceId = UUID.randomUUID()
    private val farmId = UUID.randomUUID()
    private val campaignId = UUID.randomUUID()
    private val harvestDate = LocalDate.of(2026, 11, 20)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun twoConsecutiveAddCostCallsOnlyCreateSingleExpense() = runTest(dispatcher) {
        val createGate = CompletableDeferred<Unit>()
        val createCalls = AtomicInteger(0)

        val dummyHarvest = Harvest(
            id = harvestId,
            workspaceId = workspaceId,
            farmId = farmId,
            campaignId = campaignId,
            harvestDate = harvestDate,
            totalGrams = 50_000,
            shares = emptyList(),
            collectionMethod = null,
            workerCount = 2,
            machineryText = null,
            notes = null,
            version = 1,
        )

        val seedExpense = Expense(
            id = UUID.randomUUID(),
            workspaceId = workspaceId,
            expenseDate = harvestDate,
            concept = "Gasto inicial",
            category = ExpenseCategory.FUEL,
            amountMinor = 2000L,
            currency = "EUR",
            status = ExpenseStatus.POSTED,
            origin = ExpenseOrigin.MANUAL,
            farmId = farmId,
            campaignId = campaignId,
            harvestId = harvestId,
        )

        val harvests = object : HarvestRepository by unused() {
            override fun observe(id: UUID): Flow<Harvest?> = flowOf(dummyHarvest)
            override fun observeContexts(): Flow<List<HarvestContext>> = flowOf(emptyList())
        }

        val expenses = object : ExpenseRepository by unused() {
            override fun observeForHarvest(harvestId: UUID): Flow<List<Expense>> = flowOf(listOf(seedExpense))
            override suspend fun create(draft: ExpenseDraft): AppResult<UUID> {
                createCalls.incrementAndGet()
                createGate.await()
                return AppResult.Success(UUID.randomUUID())
            }
        }

        val viewModel = HarvestDetailViewModel(
            harvestId = harvestId,
            harvests = harvests,
            clock = FixedClock,
            expenses = expenses,
        )
        advanceUntilIdle()

        // Estado inicial tras carga de harvest y gastos
        assertFalse(viewModel.state.value.isLoading)
        assertTrue(viewModel.state.value.costsLoaded)
        assertFalse(viewModel.state.value.isSaving)
        assertEquals(0, viewModel.state.value.costSaved)

        // Primera llamada: se inicia el guardado
        viewModel.addCost(
            kind = JornadaExpenseKind.PETROL,
            amountMinor = 3500L,
            concept = "Combustible",
            openAfter = false,
        )

        // isSaving debe ser true de forma inmediata y síncrona antes del primer despacho
        assertTrue("isSaving debe activarse síncronamente al iniciar guardado", viewModel.state.value.isSaving)

        // Segunda llamada inmediata en el mismo tick (antes de que la corrutina empiece a despacharse)
        viewModel.addCost(
            kind = JornadaExpenseKind.PETROL,
            amountMinor = 3500L,
            concept = "Combustible reintento temprano",
            openAfter = false,
        )

        // Avanzar el scheduler hasta el punto de suspensión (createGate.await())
        testScheduler.runCurrent()

        // create() debe haberse invocado exactamente 1 vez y estar suspendido en createGate
        assertEquals(1, createCalls.get())
        assertTrue("isSaving debe permanecer true durante la suspensión", viewModel.state.value.isSaving)

        // Tercera llamada mientras la operación I/O sigue en curso
        viewModel.addCost(
            kind = JornadaExpenseKind.PETROL,
            amountMinor = 3500L,
            concept = "Combustible reintento en curso",
            openAfter = false,
        )

        // Liberar el repositorio para que complete la creación
        createGate.complete(Unit)
        advanceUntilIdle()

        // El estado final debe reflejar exactamente una creación exitosa
        assertFalse(viewModel.state.value.isSaving)
        assertEquals(1, viewModel.state.value.costSaved)
        assertEquals("create() en el repositorio debió invocarse exactamente 1 vez a pesar de múltiples llamadas", 1, createCalls.get())
    }

    @Test
    fun failedAddCostAllowsCorrectionAndRetry() = runTest(dispatcher) {
        val createCalls = AtomicInteger(0)
        var shouldFail = true

        val dummyHarvest = Harvest(
            id = harvestId,
            workspaceId = workspaceId,
            farmId = farmId,
            campaignId = campaignId,
            harvestDate = harvestDate,
            totalGrams = 50_000,
            shares = emptyList(),
            collectionMethod = null,
            workerCount = 2,
            machineryText = null,
            notes = null,
            version = 1,
        )

        val seedExpense = Expense(
            id = UUID.randomUUID(),
            workspaceId = workspaceId,
            expenseDate = harvestDate,
            concept = "Gasto inicial",
            category = ExpenseCategory.FUEL,
            amountMinor = 2000L,
            currency = "EUR",
            status = ExpenseStatus.POSTED,
            origin = ExpenseOrigin.MANUAL,
            farmId = farmId,
            campaignId = campaignId,
            harvestId = harvestId,
        )

        val harvests = object : HarvestRepository by unused() {
            override fun observe(id: UUID): Flow<Harvest?> = flowOf(dummyHarvest)
            override fun observeContexts(): Flow<List<HarvestContext>> = flowOf(emptyList())
        }

        val expenses = object : ExpenseRepository by unused() {
            override fun observeForHarvest(harvestId: UUID): Flow<List<Expense>> = flowOf(listOf(seedExpense))
            override suspend fun create(draft: ExpenseDraft): AppResult<UUID> {
                createCalls.incrementAndGet()
                return if (shouldFail) {
                    AppResult.Failure(AppError.Validation("amount", "invalid_amount"))
                } else {
                    AppResult.Success(UUID.randomUUID())
                }
            }
        }

        val viewModel = HarvestDetailViewModel(
            harvestId = harvestId,
            harvests = harvests,
            clock = FixedClock,
            expenses = expenses,
        )
        advanceUntilIdle()

        // Primer intento que falla
        viewModel.addCost(
            kind = JornadaExpenseKind.PETROL,
            amountMinor = 0L,
            concept = "Fallo",
            openAfter = false,
        )
        advanceUntilIdle()

        // El ViewModel debe liberar isSaving y mostrar el error
        assertFalse("isSaving debe resetearse a false tras fallo", viewModel.state.value.isSaving)
        assertNotNull("costError debe contener mensaje de error", viewModel.state.value.costError)
        assertEquals(0, viewModel.state.value.costSaved)
        assertEquals(1, createCalls.get())

        // Segundo intento: usuario corrige y reintenta
        shouldFail = false
        viewModel.addCost(
            kind = JornadaExpenseKind.PETROL,
            amountMinor = 2500L,
            concept = "Corregido",
            openAfter = false,
        )
        advanceUntilIdle()

        // El segundo intento debe tener éxito
        assertFalse(viewModel.state.value.isSaving)
        assertEquals(1, viewModel.state.value.costSaved)
        assertEquals(2, createCalls.get())
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

