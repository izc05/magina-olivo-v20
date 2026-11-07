package com.isivoltpro.maginaolivo.feature.activities

import com.isivoltpro.maginaolivo.core.common.AppError
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.domain.activity.ActivityRepository
import java.lang.reflect.Proxy
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class ActivitySavedCallbackTest {
    private val dispatcher = StandardTestDispatcher()
    private val id = UUID.randomUUID()
    private var result: AppResult<UUID> = AppResult.Success(id)
    private var creates = 0
    private var created: com.isivoltpro.maginaolivo.domain.activity.NewActivity? = null
    private val repository = Proxy.newProxyInstance(ActivityRepository::class.java.classLoader,
        arrayOf(ActivityRepository::class.java)) { _, method, args ->
        when {
            method.name.startsWith("observe") -> MutableStateFlow(emptyList<Any>())
            method.name == "create" -> { creates++; created = args!![0] as com.isivoltpro.maginaolivo.domain.activity.NewActivity; result }
            else -> error("Unexpected repository call: ${method.name}")
        }
    } as ActivityRepository
    private val draft = ActivityDraft(activityDate = LocalDate.of(2026, 11, 20), description = "Trabajo sintético", parcelIds = setOf(UUID.randomUUID()))
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { Dispatchers.resetMain() }

    @Test fun newWorkUsesTheExplicitAgriculturalCycle() = runTest(dispatcher) {
        val cycle = UUID.randomUUID()
        FarmActivitiesViewModel(UUID.randomUUID(), repository).create(draft.copy(agriculturalCycleId = cycle))
        advanceUntilIdle()
        assertEquals(cycle, created?.agriculturalCycleId)
        assertEquals(1, creates)
    }

    @Test fun reportsCommittedIdAfterSuccessfulSaveState() = runTest(dispatcher) {
        val vm = FarmActivitiesViewModel(UUID.randomUUID(), repository)
        var saved: UUID? = null
        vm.create(draft, onSaved = { saved = it; assertEquals(1, vm.state.value.saveCount); assertFalse(vm.state.value.isSaving) })
        advanceUntilIdle()
        assertEquals(id, saved)
        assertEquals(1, creates)
    }
    @Test fun persistenceFailureNeverReportsSavedWork() = runTest(dispatcher) {
        result = AppResult.Failure(AppError.NotFound("synthetic_activity"))
        val vm = FarmActivitiesViewModel(UUID.randomUUID(), repository)
        vm.create(draft, onSaved = { fail("Failed transaction cannot request permission") })
        advanceUntilIdle()
        assertEquals(0, vm.state.value.saveCount)
        assertNotNull(vm.state.value.error)
    }
    @Test fun validationFailureNeverWritesOrReportsSavedWork() = runTest(dispatcher) {
        val vm = FarmActivitiesViewModel(UUID.randomUUID(), repository)
        vm.create(draft.copy(description = ""), onSaved = { fail("Invalid form cannot request permission") })
        advanceUntilIdle()
        assertEquals(0, creates)
        assertEquals(0, vm.state.value.saveCount)
    }
    @Test fun existingCallersKeepTheirSaveCountWithoutACallback() = runTest(dispatcher) {
        val vm = FarmActivitiesViewModel(UUID.randomUUID(), repository)
        vm.create(draft)
        advanceUntilIdle()
        assertEquals(1, vm.state.value.saveCount)
    }
}
