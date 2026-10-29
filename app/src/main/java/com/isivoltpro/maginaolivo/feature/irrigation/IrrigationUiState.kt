package com.isivoltpro.maginaolivo.feature.irrigation

import com.isivoltpro.maginaolivo.domain.activity.Activity
import com.isivoltpro.maginaolivo.domain.irrigation.CalculatedDose
import com.isivoltpro.maginaolivo.domain.irrigation.EmitterType
import com.isivoltpro.maginaolivo.domain.irrigation.IrrigationRealStats
import com.isivoltpro.maginaolivo.domain.irrigation.IrrigationSummary
import com.isivoltpro.maginaolivo.domain.irrigation.WeeklyPlan
import com.isivoltpro.maginaolivo.domain.parcel.Parcel
import java.time.DayOfWeek
import java.util.UUID

/**
 * Navigation tabs inside the Irrigation screen.
 */
enum class IrrigationTab(val label: String) {
    SUMMARY("Resumen y turnos"),
    INSTALLATIONS("Instalaciones"),
    HISTORY("Historial"),
}

/**
 * State for the interactive dose and flow rate calculator.
 */
data class DoseCalculatorState(
    val parcelId: UUID? = null,
    val parcelName: String? = null,
    val oliveTreeCountText: String = "",
    val emittersPerTreeText: String = "",
    val emitterFlowLphText: String = "",
    val emitterType: EmitterType = EmitterType.INTEGRATED,
    val durationMinutesText: String = "",
    val calculatedDose: CalculatedDose? = null,
    val isSimulationMode: Boolean = false,
    val error: String? = null,
)

/**
 * Comprehensive UI state for the Irrigation feature screen.
 */
data class IrrigationUiState(
    val farmId: UUID? = null,
    val farmName: String? = null,
    val farmNotFound: Boolean = false,
    val isFarmArchived: Boolean = false,
    val errorMessage: String? = null,
    val selectedTab: IrrigationTab = IrrigationTab.SUMMARY,
    val selectedDay: DayOfWeek = DayOfWeek.MONDAY,
    val isLoading: Boolean = true,
    val summary: IrrigationSummary = IrrigationSummary(
        realStats = IrrigationRealStats(
            totalVolumeM3 = 0.0,
            totalDurationMinutes = 0L,
            totalDurationHours = 0.0,
            totalCostMinor = 0L,
            completedActivitiesCount = 0,
            plannedActivitiesCount = 0,
            distinctParcelsWatered = 0,
        ),
        breakdownByType = emptyList(),
        totalParcelsCount = 0,
        irrigatedParcelsCount = 0,
        drylandParcelsCount = 0,
        unspecifiedSystemCount = 0,
    ),
    val weeklyPlan: WeeklyPlan = WeeklyPlan(
        days = emptyList(),
        totalScheduledTurns = 0,
        participatingParcelsCount = 0,
        communitiesInPlan = emptySet(),
    ),
    val irrigatedParcels: List<Parcel> = emptyList(),
    val recentActivities: List<Activity> = emptyList(),
    val calculator: DoseCalculatorState = DoseCalculatorState(),
)

