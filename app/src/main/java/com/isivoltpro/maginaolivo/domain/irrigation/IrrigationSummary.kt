package com.isivoltpro.maginaolivo.domain.irrigation

import com.isivoltpro.maginaolivo.data.local.model.ActivityStatus
import com.isivoltpro.maginaolivo.domain.activity.Activity
import com.isivoltpro.maginaolivo.domain.activity.ActivityDetail
import com.isivoltpro.maginaolivo.domain.activity.ActivityType
import com.isivoltpro.maginaolivo.domain.parcel.IrrigationSystem
import com.isivoltpro.maginaolivo.domain.parcel.Parcel
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

/**
 * Breakdown of grove parcels grouped by their agronomic irrigation system.
 */
data class IrrigationTypeBreakdown(
    val system: IrrigationSystem?,
    val parcelCount: Int,
    val totalOliveTrees: Int,
    val totalManagedAreaM2: Double,
) {
    val systemLabel: String
        get() = when (system) {
            IrrigationSystem.DRYLAND -> "Secano"
            IrrigationSystem.DRIP -> "Goteo"
            IrrigationSystem.SPRINKLER -> "Aspersión"
            IrrigationSystem.OTHER -> "Otro"
            null -> "Sin definir"
        }
}

/**
 * Annual community of irrigators fixed quota / tariff fee.
 *
 * Keeps administrative annual fees strictly separated from seasonal operational watering costs.
 */
data class CommunityWaterTariffFee(
    val communityName: String,
    val annualFixedFeeMinor: Long,
    val waterPricePerM3Minor: Long? = null,
    val currency: String = "EUR",
    val notes: String? = null,
) {
    init {
        require(annualFixedFeeMinor >= 0L) { "annualFixedFeeMinor cannot be negative: $annualFixedFeeMinor" }
        require(waterPricePerM3Minor == null || waterPricePerM3Minor >= 0L) {
            "waterPricePerM3Minor cannot be negative: $waterPricePerM3Minor"
        }
    }
}

/**
 * Aggregated actual field measurements collected from recorded Irrigation activities.
 *
 * Excludes cancelled or planned activities from actual delivered volume and cost sums.
 */
data class IrrigationRealStats(
    val totalVolumeM3: Double,
    val totalDurationMinutes: Long,
    val totalDurationHours: Double,
    val totalCostMinor: Long,
    val completedActivitiesCount: Int,
    val plannedActivitiesCount: Int,
    val distinctParcelsWatered: Int,
)

/**
 * Comprehensive irrigation overview for an olive farming holding.
 */
data class IrrigationSummary(
    val realStats: IrrigationRealStats,
    val breakdownByType: List<IrrigationTypeBreakdown>,
    val totalParcelsCount: Int,
    val irrigatedParcelsCount: Int,
    val drylandParcelsCount: Int,
    val unspecifiedSystemCount: Int,
    val annualCommunityFees: List<CommunityWaterTariffFee> = emptyList(),
) {
    /**
     * Total fixed annual dues owed to irrigator communities (in euro cents / minor units).
     */
    val totalAnnualFixedFeesMinor: Long
        get() = annualCommunityFees.sumOf { it.annualFixedFeeMinor }

    /**
     * Combined season total: operational variable irrigation costs plus fixed annual community quotas.
     */
    val totalEstimatedSeasonCostMinor: Long
        get() = realStats.totalCostMinor + totalAnnualFixedFeesMinor

    /**
     * True if the farmer has recorded at least one completed watering event.
     */
    val hasRealData: Boolean
        get() = realStats.completedActivitiesCount > 0
}

/**
 * Pure and deterministic summarizer for olive holding irrigation data (CR-004).
 */
object IrrigationSummarizer {

    /**
     * Computes the complete irrigation summary by aggregating active parcels, recorded activities,
     * and optional community annual quotas.
     */
    fun computeSummary(
        parcels: List<Parcel>,
        activities: List<Activity>,
        annualCommunityFees: List<CommunityWaterTariffFee> = emptyList(),
    ): IrrigationSummary {
        val activeParcels = parcels.filter { it.archivedAt == null }

        // 1. Parcel breakdown by irrigation system
        val breakdown = computeBreakdown(activeParcels)

        val drylandCount = activeParcels.count { it.agronomy.irrigationSystem == IrrigationSystem.DRYLAND }
        val irrigatedCount = activeParcels.count {
            it.agronomy.irrigationSystem != null && it.agronomy.irrigationSystem != IrrigationSystem.DRYLAND
        }
        val unspecifiedCount = activeParcels.count { it.agronomy.irrigationSystem == null }

        // 2. Real stats from completed/planned irrigation activities
        val realStats = computeRealStats(activities)

        return IrrigationSummary(
            realStats = realStats,
            breakdownByType = breakdown,
            totalParcelsCount = activeParcels.size,
            irrigatedParcelsCount = irrigatedCount,
            drylandParcelsCount = drylandCount,
            unspecifiedSystemCount = unspecifiedCount,
            annualCommunityFees = annualCommunityFees,
        )
    }

    private fun computeBreakdown(parcels: List<Parcel>): List<IrrigationTypeBreakdown> {
        val groups = parcels.groupBy { it.agronomy.irrigationSystem }
        val orderedKeys = listOf(
            IrrigationSystem.DRIP,
            IrrigationSystem.SPRINKLER,
            IrrigationSystem.OTHER,
            IrrigationSystem.DRYLAND,
            null,
        )

        return orderedKeys.mapNotNull { systemKey ->
            val matching = groups[systemKey] ?: return@mapNotNull null
            val trees = matching.sumOf { it.agronomy.oliveTreeCount ?: 0 }
            val area = matching.sumOf { it.managedAreaM2 ?: it.cadastralAreaM2 ?: 0.0 }
            IrrigationTypeBreakdown(
                system = systemKey,
                parcelCount = matching.size,
                totalOliveTrees = trees,
                totalManagedAreaM2 = BigDecimal.valueOf(area).setScale(2, RoundingMode.HALF_UP).toDouble(),
            )
        }
    }

    private fun computeRealStats(activities: List<Activity>): IrrigationRealStats {
        val irrigationActivities = activities.filter { it.type == ActivityType.IRRIGATION }

        var totalVolumeM3 = 0.0
        var totalMinutes = 0L
        var totalCostMinor = 0L
        var completedCount = 0
        var plannedCount = 0
        val wateredParcelIds = mutableSetOf<UUID>()

        for (activity in irrigationActivities) {
            when (activity.status) {
                ActivityStatus.COMPLETED -> {
                    completedCount++
                    val detail = activity.detail as? ActivityDetail.Irrigation
                    if (detail != null) {
                        detail.volumeM3?.takeIf { it > 0.0 }?.let { totalVolumeM3 += it }
                        detail.durationMinutes?.takeIf { it > 0 }?.let { totalMinutes += it }
                        val cost = detail.price?.estimatedAmountMinor ?: activity.costMinor
                        cost?.takeIf { it > 0L }?.let { totalCostMinor += it }
                    } else if (activity.costMinor != null && activity.costMinor > 0L) {
                        totalCostMinor += activity.costMinor
                    }

                    activity.targets.forEach { target ->
                        wateredParcelIds.add(target.parcelId)
                    }
                }
                ActivityStatus.PLANNED -> {
                    plannedCount++
                }
                ActivityStatus.DRAFT,
                ActivityStatus.CANCELLED -> {
                    // Ignored in executed stats
                }
            }
        }

        val roundedVolume = BigDecimal.valueOf(totalVolumeM3).setScale(2, RoundingMode.HALF_UP).toDouble()
        val hours = BigDecimal.valueOf(totalMinutes.toDouble() / 60.0).setScale(1, RoundingMode.HALF_UP).toDouble()

        return IrrigationRealStats(
            totalVolumeM3 = roundedVolume,
            totalDurationMinutes = totalMinutes,
            totalDurationHours = hours,
            totalCostMinor = totalCostMinor,
            completedActivitiesCount = completedCount,
            plannedActivitiesCount = plannedCount,
            distinctParcelsWatered = wateredParcelIds.size,
        )
    }
}

