package com.isivoltpro.maginaolivo.domain.irrigation

import com.isivoltpro.maginaolivo.domain.parcel.IrrigationSystem
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

/**
 * Origin of the water used for parcel irrigation.
 */
enum class WaterSourceType(val label: String) {
    /** Pozo propio o sondeo privado de la explotación. */
    WELL("Pozo propio"),
    /** Concesión directa de captación superficial (río, arroyo, balsa comunal). */
    RIVER_CONCESSION("Concesión río / arroyo"),
    /** Comunidad de regantes formalmente constituida. */
    IRRIGATION_COMMUNITY("Comunidad de regantes"),
}

/**
 * Detailed identification of the water supplier, sector and physical intake point.
 */
data class WaterSupplyDetails(
    val sourceType: WaterSourceType = WaterSourceType.IRRIGATION_COMMUNITY,
    val communityName: String? = null,
    val sector: String? = null,
    val hydrant: String? = null,
    val intakeOrValve: String? = null,
) {
    /**
     * Human-readable supply summary for display on cards and lists.
     * E.g. "Comunidad de Regantes San Isidro · Sector 2 · Hidrante H-14 (Boca 3)"
     */
    val fullSupplyDescription: String
        get() {
            val parts = mutableListOf<String>()
            when (sourceType) {
                WaterSourceType.WELL -> parts.add(sourceType.label)
                WaterSourceType.RIVER_CONCESSION -> parts.add(sourceType.label)
                WaterSourceType.IRRIGATION_COMMUNITY -> {
                    parts.add(communityName?.takeIf(String::isNotBlank) ?: "Comunidad de regantes")
                }
            }
            sector?.takeIf(String::isNotBlank)?.let { parts.add("Sector $it") }
            hydrant?.takeIf(String::isNotBlank)?.let { parts.add("Hidrante $it") }
            intakeOrValve?.takeIf(String::isNotBlank)?.let { parts.add("Toma $it") }
            return parts.joinToString(" · ")
        }
}

/**
 * Flow measurement methodology for the parcel.
 */
enum class FlowMeasurementMode(val label: String) {
    /** Caudal calculado teóricamente a partir del número de goteros y su caudal unitario. */
    CALCULATED_FROM_EMITTERS("Calculado por goteros"),
    /** Caudal medido directamente mediante aforo del hidrante o caudalímetro. */
    DIRECT_HYDRANT_FLOW("Aforo directo de hidrante"),
}

/**
 * Units for direct flow measurement.
 */
enum class FlowUnit(val label: String, val toM3PerHourFactor: Double) {
    M3_PER_HOUR("m³/h", 1.0),
    LITERS_PER_SECOND("L/s", 3.6),
}

/**
 * Installation and flow rate (aforo) characteristics of the parcel.
 */
data class AforoConfig(
    val measurementMode: FlowMeasurementMode = FlowMeasurementMode.CALCULATED_FROM_EMITTERS,
    val oliveTreeCount: Int = 0,
    val emittersPerTree: Int = 0,
    val emitterFlowLph: Double = 0.0,
    val emitterType: EmitterType = EmitterType.INTEGRATED,
    val directFlowRate: Double? = null,
    val directFlowUnit: FlowUnit = FlowUnit.M3_PER_HOUR,
) {
    init {
        require(oliveTreeCount >= 0) { "oliveTreeCount cannot be negative: $oliveTreeCount" }
        require(emittersPerTree >= 0) { "emittersPerTree cannot be negative: $emittersPerTree" }
        require(emitterFlowLph >= 0.0) { "emitterFlowLph cannot be negative: $emitterFlowLph" }
        require(directFlowRate == null || directFlowRate >= 0.0) { "directFlowRate cannot be negative: $directFlowRate" }
    }

    /**
     * Effective flow rate in cubic meters per hour (m³/h).
     */
    val effectiveFlowRateM3PerHour: Double
        get() = when (measurementMode) {
            FlowMeasurementMode.DIRECT_HYDRANT_FLOW -> {
                val rate = directFlowRate ?: 0.0
                round2(rate * directFlowUnit.toM3PerHourFactor)
            }
            FlowMeasurementMode.CALCULATED_FROM_EMITTERS -> {
                val totalLph = oliveTreeCount.toDouble() * emittersPerTree * emitterFlowLph
                round2(totalLph / 1000.0)
            }
        }

    /**
     * Effective flow rate in liters per hour (L/h).
     */
    val effectiveFlowRateLph: Double
        get() = round2(effectiveFlowRateM3PerHour * 1000.0)

    companion object {
        internal fun round2(value: Double): Double =
            if (value.isNaN() || value.isInfinite()) 0.0
            else BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).toDouble()
    }
}

/**
 * Scheduled irrigation time slot with full overnight support (crossing midnight).
 */
data class ScheduledTimeSlot(
    val startTime: LocalTime,
    val endTime: LocalTime,
) {
    /**
     * True if the irrigation period crosses midnight (e.g. 22:00 to 06:00).
     */
    val isOvernight: Boolean
        get() = endTime <= startTime

    /**
     * Total duration of this time slot in minutes.
     * Correctly handles overnight shifts:
     * e.g. 22:00 to 06:00 = 120 min (before midnight) + 360 min (after midnight) = 480 min (8 hours).
     */
    val durationMinutes: Int
        get() = if (isOvernight) {
            val minutesBeforeMidnight = 24 * 60 - (startTime.hour * 60 + startTime.minute)
            val minutesAfterMidnight = endTime.hour * 60 + endTime.minute
            minutesBeforeMidnight + minutesAfterMidnight
        } else {
            (endTime.hour * 60 + endTime.minute) - (startTime.hour * 60 + startTime.minute)
        }

    /**
     * Total duration in decimal hours (e.g. 8.0).
     */
    val durationHours: Double
        get() = BigDecimal.valueOf(durationMinutes.toDouble() / 60.0)
            .setScale(2, RoundingMode.HALF_UP)
            .toDouble()

    /**
     * Formatted string representation for UI cards.
     * E.g. "22:00 – 06:00 (+1 día)" or "18:00 – 21:00".
     */
    val formattedSlot: String
        get() {
            val start = "%02d:%02d".format(startTime.hour, startTime.minute)
            val end = "%02d:%02d".format(endTime.hour, endTime.minute)
            return if (isOvernight) "$start – $end (+1 día)" else "$start – $end"
        }
}

/**
 * Pricing structure for irrigation water.
 */
enum class WaterTariffType(val label: String) {
    /** Tarifa variable cobrada por metro cúbico (€/m³). */
    PER_M3("Por volumen (€/m³)"),
    /** Tarifa variable cobrada por hora de riego (€/h). */
    PER_HOUR("Por tiempo (€/h)"),
    /** Sin coste variable directo (ej. pozo propio amortizado). */
    FREE("Sin coste variable"),
}

/**
 * Pricing rates and community quota configuration.
 *
 * Keeps variable operational watering costs strictly separated from the annual fixed community quota.
 */
data class WaterTariff(
    val tariffType: WaterTariffType = WaterTariffType.PER_M3,
    val variablePricePerUnitMinor: Long = 0L,
    val annualCommunityQuotaMinor: Long = 0L,
    val currency: String = "EUR",
    val quotaIdentifier: String? = null,
) {
    init {
        require(variablePricePerUnitMinor >= 0L) { "variablePricePerUnitMinor cannot be negative: $variablePricePerUnitMinor" }
        require(annualCommunityQuotaMinor >= 0L) { "annualCommunityQuotaMinor cannot be negative: $annualCommunityQuotaMinor" }
    }

    /**
     * Calculates the estimated cost in euro cents (minor units) for a given volume and duration.
     */
    fun calculateEstimatedCostMinor(volumeM3: Double, durationMinutes: Int): Long = when (tariffType) {
        WaterTariffType.PER_M3 -> {
            val cost = volumeM3 * variablePricePerUnitMinor.toDouble()
            cost.toLong().coerceAtLeast(0L)
        }
        WaterTariffType.PER_HOUR -> {
            val hours = durationMinutes.toDouble() / 60.0
            val cost = hours * variablePricePerUnitMinor.toDouble()
            cost.toLong().coerceAtLeast(0L)
        }
        WaterTariffType.FREE -> 0L
    }
}

/**
 * An extraordinary or modified punctual irrigation turn.
 * Does not overwrite the base recurring schedule.
 */
data class PunctualTurn(
    val id: UUID = UUID.randomUUID(),
    val parcelId: UUID,
    val date: LocalDate,
    val timeSlot: ScheduledTimeSlot,
    val reason: String = "Turno extraordinario",
    val isCancelled: Boolean = false,
)

/**
 * Complete farmer-configured irrigation profile for a single parcel.
 */
data class ParcelIrrigationProfile(
    val parcelId: UUID,
    val supply: WaterSupplyDetails = WaterSupplyDetails(),
    val aforo: AforoConfig = AforoConfig(),
    val weeklySlots: Map<DayOfWeek, ScheduledTimeSlot> = emptyMap(),
    val punctualTurns: List<PunctualTurn> = emptyList(),
    val tariff: WaterTariff = WaterTariff(),
) {
    /**
     * Computes the theoretical water delivery volume in m³ for a given duration in minutes.
     */
    fun computeVolumeM3(durationMinutes: Int): Double {
        if (durationMinutes <= 0) return 0.0
        val hours = durationMinutes.toDouble() / 60.0
        return AforoConfig.round2(aforo.effectiveFlowRateM3PerHour * hours)
    }

    /**
     * Computes the estimated cost in euro cents for a given duration in minutes.
     */
    fun computeCostMinor(durationMinutes: Int): Long {
        val volumeM3 = computeVolumeM3(durationMinutes)
        return tariff.calculateEstimatedCostMinor(volumeM3, durationMinutes)
    }
}

