package com.isivoltpro.maginaolivo.feature.irrigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.isivoltpro.maginaolivo.data.local.model.ActivityStatus
import com.isivoltpro.maginaolivo.domain.activity.Activity
import com.isivoltpro.maginaolivo.domain.activity.ActivityRepository
import com.isivoltpro.maginaolivo.domain.activity.ActivityType
import com.isivoltpro.maginaolivo.domain.farm.FarmRepository
import com.isivoltpro.maginaolivo.domain.irrigation.DripInstallation
import com.isivoltpro.maginaolivo.domain.irrigation.EmitterType
import com.isivoltpro.maginaolivo.domain.irrigation.IrrigationSummarizer
import com.isivoltpro.maginaolivo.domain.irrigation.WateringDoseCalculator
import com.isivoltpro.maginaolivo.domain.irrigation.WeeklyIrrigationCalendar
import com.isivoltpro.maginaolivo.domain.parcel.IrrigationSystem
import com.isivoltpro.maginaolivo.domain.parcel.Parcel
import com.isivoltpro.maginaolivo.domain.parcel.ParcelRepository
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class IrrigationViewModel(
    private val farmId: UUID,
    private val parcelRepository: ParcelRepository,
    private val activityRepository: ActivityRepository,
    private val farmRepository: FarmRepository? = null,
    today: LocalDate = LocalDate.now(),
) : ViewModel() {

    private val mutableState = MutableStateFlow(
        IrrigationUiState(
            farmId = farmId,
            selectedDay = today.dayOfWeek,
        )
    )
    val state: StateFlow<IrrigationUiState> = mutableState.asStateFlow()

    init {
        // Load farm name if farmRepository is present
        if (farmRepository != null) {
            viewModelScope.launch {
                farmRepository.observeById(farmId).collect { farm ->
                    if (farm != null) {
                        mutableState.value = mutableState.value.copy(farmName = farm.name)
                    }
                }
            }
        }

        // Observe parcels and activities reactively
        viewModelScope.launch {
            combine(
                parcelRepository.observeActive(farmId),
                activityRepository.observeForFarm(farmId),
            ) { parcels, activities ->
                updateProjection(parcels, activities)
            }.collect { newState ->
                mutableState.value = newState
            }
        }
    }

    private fun updateProjection(
        parcels: List<Parcel>,
        activities: List<Activity>,
    ): IrrigationUiState {
        val weeklyPlan = WeeklyIrrigationCalendar.buildWeeklyPlan(parcels)
        val summary = IrrigationSummarizer.computeSummary(parcels, activities)

        val irrigatedParcels = parcels.filter { parcel ->
            parcel.archivedAt == null &&
                parcel.agronomy.irrigationSystem != null &&
                parcel.agronomy.irrigationSystem != IrrigationSystem.DRYLAND
        }

        val recentIrrigations = activities
            .filter { it.type == ActivityType.IRRIGATION && it.status == ActivityStatus.COMPLETED }
            .sortedByDescending { it.activityDate }

        val currentCalc = mutableState.value.calculator
        val updatedCalc = if (currentCalc.parcelId == null && irrigatedParcels.isNotEmpty()) {
            val first = irrigatedParcels.first()
            val trees = first.agronomy.oliveTreeCount?.toString().orEmpty()
            currentCalc.copy(
                parcelId = first.id,
                parcelName = first.displayName,
                oliveTreeCountText = trees,
            ).let { runCalculation(it) }
        } else {
            currentCalc
        }

        return mutableState.value.copy(
            isLoading = false,
            summary = summary,
            weeklyPlan = weeklyPlan,
            irrigatedParcels = irrigatedParcels,
            recentActivities = recentIrrigations,
            calculator = updatedCalc,
        )
    }

    fun selectTab(tab: IrrigationTab) {
        mutableState.value = mutableState.value.copy(selectedTab = tab)
    }

    fun selectDay(day: DayOfWeek) {
        mutableState.value = mutableState.value.copy(selectedDay = day)
    }

    fun loadParcelForCalculator(parcel: Parcel) {
        val calc = mutableState.value.calculator.copy(
            parcelId = parcel.id,
            parcelName = parcel.displayName,
            oliveTreeCountText = parcel.agronomy.oliveTreeCount?.toString().orEmpty(),
        )
        mutableState.value = mutableState.value.copy(
            calculator = runCalculation(calc),
            selectedTab = IrrigationTab.INSTALLATIONS,
        )
    }

    fun updateTrees(text: String) {
        val filtered = text.filter { it.isDigit() }
        val calc = mutableState.value.calculator.copy(oliveTreeCountText = filtered)
        mutableState.value = mutableState.value.copy(calculator = runCalculation(calc))
    }

    fun updateEmittersPerTree(text: String) {
        val filtered = text.filter { it.isDigit() }
        val calc = mutableState.value.calculator.copy(emittersPerTreeText = filtered)
        mutableState.value = mutableState.value.copy(calculator = runCalculation(calc))
    }

    fun updateFlowLph(text: String) {
        val sanitized = text.replace(',', '.').filter { it.isDigit() || it == '.' }
        val calc = mutableState.value.calculator.copy(emitterFlowLphText = sanitized)
        mutableState.value = mutableState.value.copy(calculator = runCalculation(calc))
    }

    fun updateDuration(text: String) {
        val filtered = text.filter { it.isDigit() }
        val calc = mutableState.value.calculator.copy(durationMinutesText = filtered)
        mutableState.value = mutableState.value.copy(calculator = runCalculation(calc))
    }

    fun updateEmitterType(type: EmitterType) {
        val calc = mutableState.value.calculator.copy(emitterType = type)
        mutableState.value = mutableState.value.copy(calculator = runCalculation(calc))
    }

    fun recalculateDose() {
        val calc = runCalculation(mutableState.value.calculator)
        mutableState.value = mutableState.value.copy(calculator = calc)
    }

    private fun runCalculation(calc: DoseCalculatorState): DoseCalculatorState {
        val trees = calc.oliveTreeCountText.toIntOrNull()
        val emitters = calc.emittersPerTreeText.toIntOrNull()
        val flow = calc.emitterFlowLphText.toDoubleOrNull()
        val minutes = calc.durationMinutesText.toIntOrNull()

        if (trees == null || trees <= 0) {
            return calc.copy(
                calculatedDose = null,
                error = "Introduce el número de olivos",
            )
        }
        if (emitters == null || emitters <= 0) {
            return calc.copy(
                calculatedDose = null,
                error = "Introduce los goteros por olivo",
            )
        }
        if (flow == null || flow <= 0.0) {
            return calc.copy(
                calculatedDose = null,
                error = "Introduce el caudal de cada gotero (L/h)",
            )
        }
        if (minutes == null || minutes <= 0) {
            return calc.copy(
                calculatedDose = null,
                error = "Introduce la duración del riego en minutos",
            )
        }

        return try {
            val installation = DripInstallation(
                oliveTreeCount = trees,
                emittersPerTree = emitters,
                emitterFlowLph = flow,
                emitterType = calc.emitterType,
            )
            val dose = WateringDoseCalculator.calculateDose(installation, durationMinutes = minutes)
            calc.copy(calculatedDose = dose, error = null)
        } catch (ex: Exception) {
            calc.copy(calculatedDose = null, error = "Parámetros de cálculo no válidos")
        }
    }
}

