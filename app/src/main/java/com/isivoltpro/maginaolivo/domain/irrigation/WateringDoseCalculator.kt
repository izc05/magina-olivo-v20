package com.isivoltpro.maginaolivo.domain.irrigation

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Type of emitter / dripper installed in the grove irrigation system.
 */
enum class EmitterType(val label: String) {
    /** Gotero autocompensante: mantiene caudal constante ante variaciones de presión. */
    PRESSURE_COMPENSATING("Autocompensante"),
    /** Gotero turbulento: caudal variable dependiente de presión en pendiente/red. */
    TURBULENT("Turbulento"),
    /** Tubería con gotero integrado / interlínea. */
    INTEGRATED("Integrado"),
    /** Otros sistemas o microaspersión. */
    OTHER("Otro"),
}

/**
 * Physical description of the drip irrigation installation for a parcel, sector, or farm.
 *
 * All parameters must be non-negative.
 */
data class DripInstallation(
    val oliveTreeCount: Int,
    val emittersPerTree: Int,
    val emitterFlowLph: Double,
    val emitterType: EmitterType = EmitterType.INTEGRATED,
) {
    init {
        require(oliveTreeCount >= 0) { "oliveTreeCount cannot be negative: $oliveTreeCount" }
        require(emittersPerTree >= 0) { "emittersPerTree cannot be negative: $emittersPerTree" }
        require(emitterFlowLph >= 0.0) { "emitterFlowLph cannot be negative: $emitterFlowLph" }
    }

    /**
     * Total hourly water flow rate in liters per hour (L/h).
     */
    val totalFlowRateLph: Double
        get() = round2(oliveTreeCount.toDouble() * emittersPerTree * emitterFlowLph)

    /**
     * Total hourly water flow rate in cubic meters per hour (m³/h).
     */
    val totalFlowRateM3PerHour: Double
        get() = round2(totalFlowRateLph / 1000.0)

    /**
     * Total number of active emitters in this installation.
     */
    val totalEmitters: Int
        get() = oliveTreeCount * emittersPerTree

    companion object {
        internal fun round2(value: Double): Double =
            if (value.isNaN() || value.isInfinite()) 0.0
            else BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).toDouble()
    }
}

/**
 * Theoretical calculated dose resulting from applying a watering duration to an installation.
 *
 * Distinguishes theoretical agronomic calculation from actual field meter readings.
 */
data class CalculatedDose(
    val durationMinutes: Int,
    val totalFlowRateM3PerHour: Double,
    val theoreticalVolumeLiters: Double,
    val theoreticalVolumeM3: Double,
    val litersPerTree: Double,
)

/**
 * Pure and deterministic agronomic calculator for olive grove drip irrigation (CR-004).
 */
object WateringDoseCalculator {

    /**
     * Calculates the theoretical delivered water volume and dose per olive tree
     * based on emitter configuration and operating duration.
     *
     * Returns zero values when duration or installation parameters are zero.
     */
    fun calculateDose(installation: DripInstallation, durationMinutes: Int): CalculatedDose {
        require(durationMinutes >= 0) { "durationMinutes cannot be negative: $durationMinutes" }

        if (durationMinutes == 0 || installation.oliveTreeCount == 0 ||
            installation.emittersPerTree == 0 || installation.emitterFlowLph == 0.0
        ) {
            return CalculatedDose(
                durationMinutes = durationMinutes,
                totalFlowRateM3PerHour = installation.totalFlowRateM3PerHour,
                theoreticalVolumeLiters = 0.0,
                theoreticalVolumeM3 = 0.0,
                litersPerTree = 0.0,
            )
        }

        val hours = durationMinutes.toDouble() / 60.0
        val totalVolumeLiters = installation.totalFlowRateLph * hours
        val totalVolumeM3 = totalVolumeLiters / 1000.0
        val litersPerTree = totalVolumeLiters / installation.oliveTreeCount.toDouble()

        return CalculatedDose(
            durationMinutes = durationMinutes,
            totalFlowRateM3PerHour = installation.totalFlowRateM3PerHour,
            theoreticalVolumeLiters = DripInstallation.round2(totalVolumeLiters),
            theoreticalVolumeM3 = DripInstallation.round2(totalVolumeM3),
            litersPerTree = DripInstallation.round2(litersPerTree),
        )
    }

    /**
     * Calculates the required irrigation duration in minutes to deliver a target dose in liters per olive tree.
     *
     * Returns 0 if target is <= 0 or if emitters / flow rate are zero.
     */
    fun estimatedDurationMinutes(
        installation: DripInstallation,
        targetLitersPerTree: Double,
    ): Int {
        if (targetLitersPerTree <= 0.0 || installation.emittersPerTree == 0 || installation.emitterFlowLph <= 0.0) {
            return 0
        }
        val flowPerTreeLph = installation.emittersPerTree * installation.emitterFlowLph
        val hoursNeeded = targetLitersPerTree / flowPerTreeLph
        return BigDecimal.valueOf(hoursNeeded * 60.0).setScale(0, RoundingMode.HALF_UP).toInt()
    }
}

