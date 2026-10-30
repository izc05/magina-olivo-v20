package com.isivoltpro.maginaolivo.domain.irrigation

import com.isivoltpro.maginaolivo.domain.parcel.IrrigationSystem
import com.isivoltpro.maginaolivo.domain.parcel.Parcel
import com.isivoltpro.maginaolivo.domain.parcel.ParcelAgronomy
import com.isivoltpro.maginaolivo.domain.parcel.ParcelSource
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FarmerIrrigationConfigTest {

    @Test
    fun overnightTimeSlot_calculatesDurationCorrectlyAcrossMidnight() {
        val slot = ScheduledTimeSlot(
            startTime = LocalTime.of(22, 0),
            endTime = LocalTime.of(6, 0),
        )

        assertTrue(slot.isOvernight)
        assertEquals(480, slot.durationMinutes) // 2h before midnight + 6h after = 8h = 480 min
        assertEquals(8.0, slot.durationHours, 0.001)
        assertEquals("22:00 – 06:00 (+1 día)", slot.formattedSlot)
    }

    @Test
    fun daytimeTimeSlot_calculatesDurationCorrectly() {
        val slot = ScheduledTimeSlot(
            startTime = LocalTime.of(18, 0),
            endTime = LocalTime.of(21, 30),
        )

        assertFalse(slot.isOvernight)
        assertEquals(210, slot.durationMinutes) // 3.5h = 210 min
        assertEquals(3.5, slot.durationHours, 0.001)
        assertEquals("18:00 – 21:30", slot.formattedSlot)
    }

    @Test
    fun aforoConfig_calculatedFromEmitters_computesFlowDeterministically() {
        val aforo = AforoConfig(
            measurementMode = FlowMeasurementMode.CALCULATED_FROM_EMITTERS,
            oliveTreeCount = 500,
            emittersPerTree = 2,
            emitterFlowLph = 4.0,
        )

        assertEquals(4000.0, aforo.effectiveFlowRateLph, 0.01)
        assertEquals(4.0, aforo.effectiveFlowRateM3PerHour, 0.01)
    }

    @Test
    fun aforoConfig_directHydrantFlow_supportsM3PerHourAndLitersPerSecond() {
        val aforoM3 = AforoConfig(
            measurementMode = FlowMeasurementMode.DIRECT_HYDRANT_FLOW,
            directFlowRate = 12.5,
            directFlowUnit = FlowUnit.M3_PER_HOUR,
        )
        assertEquals(12.5, aforoM3.effectiveFlowRateM3PerHour, 0.01)
        assertEquals(12500.0, aforoM3.effectiveFlowRateLph, 0.01)

        val aforoLps = AforoConfig(
            measurementMode = FlowMeasurementMode.DIRECT_HYDRANT_FLOW,
            directFlowRate = 5.0,
            directFlowUnit = FlowUnit.LITERS_PER_SECOND,
        )
        // 5.0 L/s * 3.6 = 18.0 m³/h
        assertEquals(18.0, aforoLps.effectiveFlowRateM3PerHour, 0.01)
        assertEquals(18000.0, aforoLps.effectiveFlowRateLph, 0.01)
    }

    @Test
    fun waterTariff_calculatesVariableCostsCorrectly() {
        // 1. Per M3: 0.18 €/m³ (18 cents)
        val tariffM3 = WaterTariff(
            tariffType = WaterTariffType.PER_M3,
            variablePricePerUnitMinor = 18L,
            annualCommunityQuotaMinor = 12000L, // 120 € cuota fija anual
        )
        // 50 m³ * 18 cents = 900 cents = 9.00 €
        assertEquals(900L, tariffM3.calculateEstimatedCostMinor(volumeM3 = 50.0, durationMinutes = 120))

        // 2. Per Hour: 2.50 €/h (250 cents)
        val tariffHour = WaterTariff(
            tariffType = WaterTariffType.PER_HOUR,
            variablePricePerUnitMinor = 250L,
        )
        // 120 min = 2 hours * 250 cents = 500 cents = 5.00 €
        assertEquals(500L, tariffHour.calculateEstimatedCostMinor(volumeM3 = 50.0, durationMinutes = 120))

        // 3. Free (pozo propio)
        val tariffFree = WaterTariff(tariffType = WaterTariffType.FREE)
        assertEquals(0L, tariffFree.calculateEstimatedCostMinor(volumeM3 = 100.0, durationMinutes = 240))
    }

    @Test
    fun weeklyPlan_integratesFarmerProfilesWithOvernightAndPunctualTurns() {
        val parcelId = UUID.randomUUID()
        val parcel = createTestParcel(parcelId, "La Loma", days = setOf(DayOfWeek.TUESDAY))

        val profile = ParcelIrrigationProfile(
            parcelId = parcelId,
            supply = WaterSupplyDetails(
                sourceType = WaterSourceType.IRRIGATION_COMMUNITY,
                communityName = "Comunidad San Isidro",
                sector = "Sector 2",
                hydrant = "H-14",
                intakeOrValve = "Boca 3",
            ),
            aforo = AforoConfig(
                measurementMode = FlowMeasurementMode.CALCULATED_FROM_EMITTERS,
                oliveTreeCount = 500,
                emittersPerTree = 2,
                emitterFlowLph = 4.0, // 4 m³/h
            ),
            weeklySlots = mapOf(
                DayOfWeek.TUESDAY to ScheduledTimeSlot(
                    startTime = LocalTime.of(22, 0),
                    endTime = LocalTime.of(6, 0), // 8 hours overnight = 32 m³
                )
            ),
            tariff = WaterTariff(
                tariffType = WaterTariffType.PER_M3,
                variablePricePerUnitMinor = 20L, // 0.20 €/m³ -> 32 * 20 = 640 cents = 6.40 €
                annualCommunityQuotaMinor = 15000L,
            ),
        )

        val referenceMonday = LocalDate.of(2026, 6, 1) // Monday
        val plan = WeeklyIrrigationCalendar.buildWeeklyPlan(
            parcels = listOf(parcel),
            profiles = mapOf(parcelId to profile),
            referenceMonday = referenceMonday,
        )

        assertEquals(1, plan.totalScheduledTurns)
        assertEquals(32.0, plan.totalEstimatedWeeklyVolumeM3, 0.01)
        assertEquals(640L, plan.totalEstimatedWeeklyCostMinor)

        val tuesdaySchedule = plan.days.first { it.dayOfWeek == DayOfWeek.TUESDAY }
        assertEquals(1, tuesdaySchedule.turns.size)
        val turn = tuesdaySchedule.turns.first()
        assertEquals("La Loma", turn.parcelName)
        assertEquals("Comunidad San Isidro · Sector 2 · Hidrante H-14 · Boca 3", turn.locationDescription)
        assertNotNull(turn.timeSlot)
        assertTrue(turn.timeSlot!!.isOvernight)
        assertEquals(32.0, turn.estimatedVolumeM3!!, 0.01)
        assertEquals(640L, turn.estimatedCostMinor)
    }

    @Test
    fun punctualTurns_areProjectedOntoCorrectDayAndCanBeSuspended() {
        val parcelId = UUID.randomUUID()
        val parcel = createTestParcel(parcelId, "El Valle")

        val referenceMonday = LocalDate.of(2026, 6, 1) // Monday
        val wednesdayDate = referenceMonday.plusDays(2) // Wednesday June 3

        val punctualTurn = PunctualTurn(
            parcelId = parcelId,
            date = wednesdayDate,
            timeSlot = ScheduledTimeSlot(LocalTime.of(18, 0), LocalTime.of(20, 0)), // 2h
            reason = "Aporte extraordinario de socorro",
        )

        val profile = ParcelIrrigationProfile(
            parcelId = parcelId,
            aforo = AforoConfig(
                measurementMode = FlowMeasurementMode.DIRECT_HYDRANT_FLOW,
                directFlowRate = 10.0, // 10 m³/h -> 20 m³
            ),
            punctualTurns = listOf(punctualTurn),
            tariff = WaterTariff(variablePricePerUnitMinor = 15L), // 20 * 15 = 300 cents
        )

        val plan = WeeklyIrrigationCalendar.buildWeeklyPlan(
            parcels = listOf(parcel),
            profiles = mapOf(parcelId to profile),
            referenceMonday = referenceMonday,
        )

        val wednesdaySchedule = plan.days.first { it.dayOfWeek == DayOfWeek.WEDNESDAY }
        assertEquals(1, wednesdaySchedule.turns.size)
        val turn = wednesdaySchedule.turns.first()
        assertTrue(turn.isExtraordinary)
        assertFalse(turn.isCancelled)
        assertEquals("Aporte extraordinario de socorro", turn.reason)
        assertEquals(20.0, turn.estimatedVolumeM3!!, 0.01)
        assertEquals(300L, turn.estimatedCostMinor)
        assertEquals(20.0, plan.totalEstimatedWeeklyVolumeM3, 0.01)
        assertEquals(300L, plan.totalEstimatedWeeklyCostMinor)

        // Now test when the punctual turn is cancelled/suspended:
        val cancelledTurn = punctualTurn.copy(isCancelled = true)
        val planCancelled = WeeklyIrrigationCalendar.buildWeeklyPlan(
            parcels = listOf(parcel),
            profiles = mapOf(parcelId to profile.copy(punctualTurns = listOf(cancelledTurn))),
            referenceMonday = referenceMonday,
        )
        val turnCancelled = planCancelled.days.first { it.dayOfWeek == DayOfWeek.WEDNESDAY }.turns.first()
        assertTrue(turnCancelled.isCancelled)
        assertEquals(0.0, planCancelled.totalEstimatedWeeklyVolumeM3, 0.01)
        assertEquals(0L, planCancelled.totalEstimatedWeeklyCostMinor)
    }

    private fun createTestParcel(
        id: UUID,
        name: String,
        days: Set<DayOfWeek> = emptySet(),
    ): Parcel = Parcel(
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
        cadastralAreaM2 = null,
        managedAreaM2 = null,
        notes = null,
        archivedAt = null,
        version = 1L,
        agronomy = ParcelAgronomy(
            irrigationSystem = IrrigationSystem.DRIP,
            irrigationDays = days,
        ),
    )
}
