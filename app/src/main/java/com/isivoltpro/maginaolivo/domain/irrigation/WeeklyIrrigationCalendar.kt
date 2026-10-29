package com.isivoltpro.maginaolivo.domain.irrigation

import com.isivoltpro.maginaolivo.domain.parcel.IrrigationSystem
import com.isivoltpro.maginaolivo.domain.parcel.Parcel
import java.time.DayOfWeek
import java.util.UUID

/**
 * An item in the weekly irrigation calendar representing a parcel assigned to water on a specific day.
 */
data class ScheduledTurnItem(
    val parcelId: UUID,
    val parcelName: String,
    val sector: String? = null,
    val communityOrNetwork: String? = null,
    val system: IrrigationSystem? = null,
    val oliveTreeCount: Int? = null,
) {
    /**
     * Readable location description combining community/network and sector, e.g. "Comunidad San Isidro · Sector 2".
     */
    val locationDescription: String?
        get() = listOfNotNull(
            communityOrNetwork?.takeIf(String::isNotBlank),
            sector?.takeIf(String::isNotBlank),
        ).takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

/**
 * Irrigation schedule for a specific day of the week.
 */
data class DaySchedule(
    val dayOfWeek: DayOfWeek,
    val turns: List<ScheduledTurnItem>,
) {
    val hasTurns: Boolean get() = turns.isNotEmpty()
    val totalParcels: Int get() = turns.size

    /**
     * Groups this day's scheduled turns by community of irrigators / network.
     * Uses "Sin comunidad asignada" for parcels without community specified.
     */
    val turnsByCommunity: Map<String, List<ScheduledTurnItem>>
        get() = turns.groupBy { it.communityOrNetwork?.takeIf(String::isNotBlank) ?: "Sin comunidad asignada" }
}

/**
 * The complete weekly irrigation plan for an olive holding, spanning Monday through Sunday.
 */
data class WeeklyPlan(
    val days: List<DaySchedule>,
    val totalScheduledTurns: Int,
    val participatingParcelsCount: Int,
    val communitiesInPlan: Set<String>,
)

/**
 * Pure and deterministic weekly irrigation calendar planner (CR-004).
 */
object WeeklyIrrigationCalendar {

    /**
     * Builds the complete 7-day weekly irrigation schedule from active parcels.
     *
     * Archived parcels and dryland parcels are excluded. Parcels without defined
     * irrigation days are omitted from the schedule.
     */
    fun buildWeeklyPlan(parcels: List<Parcel>): WeeklyPlan {
        val eligibleParcels = parcels.filter { parcel ->
            parcel.archivedAt == null &&
                parcel.agronomy.irrigationSystem != null &&
                parcel.agronomy.irrigationSystem != IrrigationSystem.DRYLAND &&
                parcel.agronomy.irrigationDays.isNotEmpty()
        }

        val allCommunities = mutableSetOf<String>()
        val participatingParcelIds = mutableSetOf<UUID>()

        val daysSchedule = DayOfWeek.entries.map { day ->
            val turnsForDay = eligibleParcels
                .filter { parcel -> day in parcel.agronomy.irrigationDays }
                .map { parcel ->
                    participatingParcelIds.add(parcel.id)
                    parcel.agronomy.irrigationNetwork?.takeIf(String::isNotBlank)?.let {
                        allCommunities.add(it)
                    }

                    ScheduledTurnItem(
                        parcelId = parcel.id,
                        parcelName = parcel.displayName,
                        sector = parcel.agronomy.irrigationSector,
                        communityOrNetwork = parcel.agronomy.irrigationNetwork,
                        system = parcel.agronomy.irrigationSystem,
                        oliveTreeCount = parcel.agronomy.oliveTreeCount,
                    )
                }
                .sortedWith(
                    compareBy<ScheduledTurnItem> { it.communityOrNetwork.orEmpty() }
                        .thenBy { it.sector.orEmpty() }
                        .thenBy { it.parcelName }
                )

            DaySchedule(
                dayOfWeek = day,
                turns = turnsForDay,
            )
        }

        val totalTurns = daysSchedule.sumOf { it.turns.size }

        return WeeklyPlan(
            days = daysSchedule,
            totalScheduledTurns = totalTurns,
            participatingParcelsCount = participatingParcelIds.size,
            communitiesInPlan = allCommunities,
        )
    }
}

