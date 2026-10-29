package com.isivoltpro.maginaolivo.domain.irrigation

import com.isivoltpro.maginaolivo.data.local.model.ActivityStatus
import com.isivoltpro.maginaolivo.domain.activity.Activity
import com.isivoltpro.maginaolivo.domain.activity.ActivityDetail
import com.isivoltpro.maginaolivo.domain.activity.ActivityParcelTarget
import com.isivoltpro.maginaolivo.domain.activity.ActivityType
import com.isivoltpro.maginaolivo.domain.activity.IrrigationPrice
import com.isivoltpro.maginaolivo.domain.activity.IrrigationPricingBasis
import com.isivoltpro.maginaolivo.domain.parcel.IrrigationSystem
import com.isivoltpro.maginaolivo.domain.parcel.Parcel
import com.isivoltpro.maginaolivo.domain.parcel.ParcelAgronomy
import com.isivoltpro.maginaolivo.domain.parcel.ParcelSource
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IrrigationSummaryTest {

    private fun sampleParcel(
        id: UUID = UUID.randomUUID(),
        name: String,
        system: IrrigationSystem?,
        oliveTrees: Int? = 100,
        areaM2: Double? = 10000.0,
        archivedAt: Instant? = null,
    ) = Parcel(
        id = id,
        workspaceId = UUID.randomUUID(),
        farmId = UUID.randomUUID(),
        displayName = name,
        cadastralReference = null,
        cadastralPolygon = null,
        cadastralParcel = null,
        municipality = null,
        province = null,
        source = ParcelSource.MANUAL,
        geometryGeoJson = null,
        cadastralAreaM2 = areaM2,
        managedAreaM2 = areaM2,
        notes = null,
        archivedAt = archivedAt,
        version = 1L,
        agronomy = ParcelAgronomy(
            oliveTreeCount = oliveTrees,
            irrigationSystem = system,
        ),
    )

    private fun sampleActivity(
        id: UUID = UUID.randomUUID(),
        type: ActivityType = ActivityType.IRRIGATION,
        status: ActivityStatus = ActivityStatus.COMPLETED,
        parcelId: UUID = UUID.randomUUID(),
        volumeM3: Double? = null,
        durationMinutes: Int? = null,
        costMinor: Long? = null,
        estimatedPriceMinor: Long? = null,
    ) = Activity(
        id = id,
        workspaceId = UUID.randomUUID(),
        farmId = null,
        campaignId = null,
        type = type,
        status = status,
        activityDate = LocalDate.of(2026, 6, 15),
        description = "Riego regular",
        notes = null,
        targets = listOf(ActivityParcelTarget(parcelId = parcelId, parcelName = "Test Parcel")),
        detail = if (type == ActivityType.IRRIGATION) {
            ActivityDetail.Irrigation(
                volumeM3 = volumeM3,
                durationMinutes = durationMinutes,
                price = estimatedPriceMinor?.let {
                    IrrigationPrice(
                        basis = IrrigationPricingBasis.PER_M3,
                        priceDate = LocalDate.of(2026, 6, 15),
                        estimatedAmountMinor = it,
                    )
                },
            )
        } else null,
        version = 1L,
        costMinor = costMinor,
    )

    @Test
    fun computeSummary_emptyInputs_returnsCleanZeroedSummary() {
        val summary = IrrigationSummarizer.computeSummary(
            parcels = emptyList(),
            activities = emptyList(),
            annualCommunityFees = emptyList(),
        )

        assertEquals(0, summary.totalParcelsCount)
        assertEquals(0, summary.irrigatedParcelsCount)
        assertEquals(0, summary.drylandParcelsCount)
        assertEquals(0, summary.unspecifiedSystemCount)
        assertEquals(0.0, summary.realStats.totalVolumeM3, 0.001)
        assertEquals(0L, summary.realStats.totalDurationMinutes)
        assertEquals(0.0, summary.realStats.totalDurationHours, 0.001)
        assertEquals(0L, summary.realStats.totalCostMinor)
        assertEquals(0, summary.realStats.completedActivitiesCount)
        assertEquals(0, summary.realStats.plannedActivitiesCount)
        assertEquals(0, summary.realStats.distinctParcelsWatered)
        assertFalse(summary.hasRealData)
        assertEquals(0L, summary.totalAnnualFixedFeesMinor)
        assertEquals(0L, summary.totalEstimatedSeasonCostMinor)
    }

    @Test
    fun computeSummary_categorizesParcelTypesCorrectly() {
        val p1 = sampleParcel(name = "Goteo 1", system = IrrigationSystem.DRIP, oliveTrees = 200, areaM2 = 20000.0)
        val p2 = sampleParcel(name = "Goteo 2", system = IrrigationSystem.DRIP, oliveTrees = 300, areaM2 = 30000.0)
        val p3 = sampleParcel(name = "Aspersión", system = IrrigationSystem.SPRINKLER, oliveTrees = 150, areaM2 = 15000.0)
        val p4 = sampleParcel(name = "Secano", system = IrrigationSystem.DRYLAND, oliveTrees = 400, areaM2 = 40000.0)
        val p5 = sampleParcel(name = "Sin Definir", system = null, oliveTrees = 50, areaM2 = 5000.0)
        val pArchived = sampleParcel(name = "Archivada", system = IrrigationSystem.DRIP, archivedAt = Instant.now())

        val summary = IrrigationSummarizer.computeSummary(
            parcels = listOf(p1, p2, p3, p4, p5, pArchived),
            activities = emptyList(),
        )

        assertEquals(5, summary.totalParcelsCount) // pArchived excluded
        assertEquals(3, summary.irrigatedParcelsCount) // 2 DRIP + 1 SPRINKLER
        assertEquals(1, summary.drylandParcelsCount) // 1 DRYLAND
        assertEquals(1, summary.unspecifiedSystemCount) // 1 null

        val dripBreakdown = summary.breakdownByType.first { it.system == IrrigationSystem.DRIP }
        assertEquals(2, dripBreakdown.parcelCount)
        assertEquals(500, dripBreakdown.totalOliveTrees)
        assertEquals(50000.0, dripBreakdown.totalManagedAreaM2, 0.001)
        assertEquals("Goteo", dripBreakdown.systemLabel)
    }

    @Test
    fun computeSummary_aggregatesCompletedActivitiesAndIgnoresPlannedOrCancelledFromVolume() {
        val parcelIdA = UUID.randomUUID()
        val parcelIdB = UUID.randomUUID()

        val actCompleted1 = sampleActivity(
            status = ActivityStatus.COMPLETED,
            parcelId = parcelIdA,
            volumeM3 = 120.5,
            durationMinutes = 180, // 3 hours
            estimatedPriceMinor = 4500L, // 45.00 €
        )
        val actCompleted2 = sampleActivity(
            status = ActivityStatus.COMPLETED,
            parcelId = parcelIdA,
            volumeM3 = 80.0,
            durationMinutes = 120, // 2 hours
            costMinor = 3000L, // 30.00 € (uses costMinor as fallback when estimatedAmountMinor is null)
        )
        val actCompleted3 = sampleActivity(
            status = ActivityStatus.COMPLETED,
            parcelId = parcelIdB,
            volumeM3 = 50.25,
            durationMinutes = 60, // 1 hour
            estimatedPriceMinor = 1500L,
        )
        val actPlanned = sampleActivity(
            status = ActivityStatus.PLANNED,
            parcelId = parcelIdA,
            volumeM3 = 200.0,
            durationMinutes = 300,
        )
        val actCancelled = sampleActivity(
            status = ActivityStatus.CANCELLED,
            parcelId = parcelIdB,
            volumeM3 = 100.0,
            durationMinutes = 150,
        )
        val nonIrrigationAct = sampleActivity(
            type = ActivityType.PRUNING,
            status = ActivityStatus.COMPLETED,
            parcelId = parcelIdA,
        )

        val summary = IrrigationSummarizer.computeSummary(
            parcels = emptyList(),
            activities = listOf(actCompleted1, actCompleted2, actCompleted3, actPlanned, actCancelled, nonIrrigationAct),
        )

        assertTrue(summary.hasRealData)
        assertEquals(3, summary.realStats.completedActivitiesCount)
        assertEquals(1, summary.realStats.plannedActivitiesCount)
        assertEquals(2, summary.realStats.distinctParcelsWatered)

        // 120.5 + 80.0 + 50.25 = 250.75 m³
        assertEquals(250.75, summary.realStats.totalVolumeM3, 0.001)
        // 180 + 120 + 60 = 360 min = 6.0 hours
        assertEquals(360L, summary.realStats.totalDurationMinutes)
        assertEquals(6.0, summary.realStats.totalDurationHours, 0.001)
        // 4500 + 3000 + 1500 = 9000 cents (90.00 €)
        assertEquals(9000L, summary.realStats.totalCostMinor)
    }

    @Test
    fun computeSummary_includesCommunityFeesInTotalSeasonCostSeparately() {
        val feeSanIsidro = CommunityWaterTariffFee(
            communityName = "Comunidad San Isidro",
            annualFixedFeeMinor = 25000L, // 250.00 € cuota fija anual
            waterPricePerM3Minor = 35L, // 0.35 €/m³
        )
        val feeTorres = CommunityWaterTariffFee(
            communityName = "Comunidad Torres Alta",
            annualFixedFeeMinor = 15000L, // 150.00 €
        )

        val completedAct = sampleActivity(
            status = ActivityStatus.COMPLETED,
            volumeM3 = 100.0,
            durationMinutes = 120,
            estimatedPriceMinor = 3500L, // 35.00 € variable
        )

        val summary = IrrigationSummarizer.computeSummary(
            parcels = emptyList(),
            activities = listOf(completedAct),
            annualCommunityFees = listOf(feeSanIsidro, feeTorres),
        )

        // Cuotas fijas: 25000 + 15000 = 40000 L (400.00 €)
        assertEquals(40000L, summary.totalAnnualFixedFeesMinor)
        // Coste variable de riegos: 3500 L (35.00 €)
        assertEquals(3500L, summary.realStats.totalCostMinor)
        // Coste total de campaña: 40000 + 3500 = 43500 L (435.00 €)
        assertEquals(43500L, summary.totalEstimatedSeasonCostMinor)
    }
}

