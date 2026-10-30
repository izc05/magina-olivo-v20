package com.isivoltpro.maginaolivo.domain.irrigation

import com.isivoltpro.maginaolivo.domain.parcel.IrrigationSystem
import com.isivoltpro.maginaolivo.domain.parcel.Parcel
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.util.UUID

/**
 * An item in the weekly irrigation calendar representing a parcel scheduled to water on a specific day.
 *
 * Fully supports farmer-configured schedules, overnight turns crossing midnight,
 * direct hydrant flow / emitter aforo, and cost estimations (B.5).
 */
data class ScheduledTurnItem(
    val parcelId: UUID,
    val parcelName: String,
    val sector: String? = null,
    val communityOrNetwork: String? = null,
    val system: IrrigationSystem? = null,
    val oliveTreeCount: Int? = null,
    // B.5 Extensions:
    val timeSlot: ScheduledTimeSlot? = null,
    val estimatedVolumeM3: Double? = null,
    val estimatedCostMinor: Long? = null,
    val hydrant: String? = null,
    val intakeOrValve: String? = null,
    val sourceType: WaterSourceType? = null,
    val isExtraordinary: Boolean = false,
    val isCancelled: Boolean = false,
    val punctualTurnId: UUID? = null,
    val reason: String? = null,
) {
    /**
     * Readable location description combining community/network, sector, hydrant and valve intake.
     * E.g. "Comunidad San Isidro · Sector 2 · Hidrante H-14"
     */
    val locationDescription: String?
        get() = listOfNotNull(
            communityOrNetwork?.takeIf(String::isNotBlank),
            sector?.takeIf(String::isNotBlank)?.let { if (it.startsWith("Sector", ignoreCase = true)) it else "Sector $it" },
            hydrant?.takeIf(String::isNotBlank)?.let { if (it.startsWith("Hidrante", ignoreCase = true)) it else "Hidrante $it" },
            intakeOrValve?.takeIf(String::isNotBlank)?.let { if (it.startsWith("Toma", ignoreCase = true) || it.startsWith("Boca", ignoreCase = true)) it else "Toma $it" },
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
    val totalEstimatedWeeklyVolumeM3: Double = 0.0,
    val totalEstimatedWeeklyCostMinor: Long = 0L,
)

/**
 * Pure and deterministic weekly irrigation calendar planner (CR-004 & B.5).
 */
object WeeklyIrrigationCalendar {

    /**
     * Builds the complete 7-day weekly irrigation schedule from active parcels and optional
     * farmer irrigation profiles.
     *
     * Archived parcels and dryland parcels are excluded.
     */
    fun buildWeeklyPlan(
        parcels: List<Parcel>,
        profiles: Map<UUID, ParcelIrrigationProfile> = emptyMap(),
        referenceMonday: LocalDate = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
    ): WeeklyPlan {
        val eligibleParcels = parcels.filter { parcel ->
            parcel.archivedAt == null &&
                parcel.agronomy.irrigationSystem != IrrigationSystem.DRYLAND
        }

        val allCommunities = mutableSetOf<String>()
        val participatingParcelIds = mutableSetOf<UUID>()
        var weeklyTotalVolumeM3 = 0.0
        var weeklyTotalCostMinor = 0L

        val daysSchedule = DayOfWeek.entries.map { day ->
            val dayDate = referenceMonday.plusDays(day.ordinal.toLong())
            val turnsForDay = mutableListOf<ScheduledTurnItem>()

            // 1. Regular weekly planned turns
            for (parcel in eligibleParcels) {
                val profile = profiles[parcel.id]
                val isScheduledToday = if (profile != null && profile.weeklySlots.isNotEmpty()) {
                    day in profile.weeklySlots
                } else {
                    day in parcel.agronomy.irrigationDays
                }

                if (isScheduledToday) {
                    participatingParcelIds.add(parcel.id)
                    val commName = profile?.supply?.communityName?.takeIf(String::isNotBlank)
                        ?: parcel.agronomy.irrigationNetwork?.takeIf(String::isNotBlank)
                    commName?.let { allCommunities.add(it) }

                    val slot = profile?.weeklySlots?.get(day)
                    val estVol = slot?.let { profile.computeVolumeM3(it.durationMinutes) }
                    val estCost = slot?.let { profile.computeCostMinor(it.durationMinutes) }

                    if (estVol != null) weeklyTotalVolumeM3 += estVol
                    if (estCost != null) weeklyTotalCostMinor += estCost

                    turnsForDay.add(
                        ScheduledTurnItem(
                            parcelId = parcel.id,
                            parcelName = parcel.displayName,
                            sector = profile?.supply?.sector ?: parcel.agronomy.irrigationSector,
                            communityOrNetwork = commName,
                            system = parcel.agronomy.irrigationSystem,
                            oliveTreeCount = profile?.aforo?.oliveTreeCount ?: parcel.agronomy.oliveTreeCount,
                            timeSlot = slot,
                            estimatedVolumeM3 = estVol,
                            estimatedCostMinor = estCost,
                            hydrant = profile?.supply?.hydrant,
                            intakeOrValve = profile?.supply?.intakeOrValve,
                            sourceType = profile?.supply?.sourceType,
                            isExtraordinary = false,
                            isCancelled = false,
                        )
                    )
                }
            }

            // 2. Extraordinary / punctual turns for this day
            for (parcel in eligibleParcels) {
                val profile = profiles[parcel.id] ?: continue
                val punctualForDay = profile.punctualTurns.filter { it.date == dayDate }
                for (pt in punctualForDay) {
                    participatingParcelIds.add(parcel.id)
                    val commName = profile.supply.communityName?.takeIf(String::isNotBlank)
                        ?: parcel.agronomy.irrigationNetwork?.takeIf(String::isNotBlank)
                    commName?.let { allCommunities.add(it) }

                    val estVol = profile.computeVolumeM3(pt.timeSlot.durationMinutes)
                    val estCost = profile.computeCostMinor(pt.timeSlot.durationMinutes)

                    if (!pt.isCancelled) {
                        weeklyTotalVolumeM3 += estVol
                        weeklyTotalCostMinor += estCost
                    }

                    turnsForDay.add(
                        ScheduledTurnItem(
                            parcelId = parcel.id,
                            parcelName = parcel.displayName,
                            sector = profile.supply.sector ?: parcel.agronomy.irrigationSector,
                            communityOrNetwork = commName,
                            system = parcel.agronomy.irrigationSystem,
                            oliveTreeCount = profile.aforo.oliveTreeCount,
                            timeSlot = pt.timeSlot,
                            estimatedVolumeM3 = estVol,
                            estimatedCostMinor = estCost,
                            hydrant = profile.supply.hydrant,
                            intakeOrValve = profile.supply.intakeOrValve,
                            sourceType = profile.supply.sourceType,
                            isExtraordinary = true,
                            isCancelled = pt.isCancelled,
                            punctualTurnId = pt.id,
                            reason = pt.reason,
                        )
                    )
                }
            }

            turnsForDay.sortWith(
                compareBy<ScheduledTurnItem> { it.isCancelled }
                    .thenBy { it.timeSlot?.startTime }
                    .thenBy { it.communityOrNetwork.orEmpty() }
                    .thenBy { it.sector.orEmpty() }
                    .thenBy { it.parcelName }
            )

            DaySchedule(
                dayOfWeek = day,
                turns = turnsForDay,
            )
        }

        val totalTurns = daysSchedule.sumOf { it.turns.size }
        val roundedVolume = BigDecimal.valueOf(weeklyTotalVolumeM3).setScale(2, RoundingMode.HALF_UP).toDouble()

        return WeeklyPlan(
            days = daysSchedule,
            totalScheduledTurns = totalTurns,
            participatingParcelsCount = participatingParcelIds.size,
            communitiesInPlan = allCommunities,
            totalEstimatedWeeklyVolumeM3 = roundedVolume,
            totalEstimatedWeeklyCostMinor = weeklyTotalCostMinor,
        )
    }
}
