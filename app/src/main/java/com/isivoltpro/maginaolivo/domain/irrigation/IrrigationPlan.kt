package com.isivoltpro.maginaolivo.domain.irrigation

import java.math.BigDecimal
import java.math.RoundingMode

/** What the farmer types into the calculator. Nothing here is saved. */
data class IrrigationPlanInput(
    val trees: Int,
    val emittersPerTree: Int,
    val emitterFlowLph: Double,
    /** Null until the farmer gives a duration: no volume is derived from an empty field. */
    val durationMinutes: Int?,
    val targetLitersPerTree: Double? = null,
    val irrigationsPerWeek: Int? = null,
    val tariff: WaterTariff? = null,
)

/** Always a theoretical result; a measured watering lives in the Cuaderno. */
data class IrrigationPlan(
    val totalFlowLph: Double,
    val totalFlowM3PerHour: Double,
    val dose: CalculatedDose?,
    val volumeM3: Double?,
    val minutesForTarget: Int?,
    val weeklyVolumeM3: Double?,
    val costPerIrrigationMinor: Long?,
    val weeklyCostMinor: Long?,
)

object IrrigationPlanner {
    const val MAX_TREES = 1_000_000
    const val MAX_EMITTERS = 100
    const val MAX_FLOW_LPH = 1_000.0
    const val MAX_MINUTES = 7 * 24 * 60
    const val MAX_WEEKLY = 21

    /** Null when an input is out of range: no figure is better than an invented one. */
    fun calculate(input: IrrigationPlanInput): IrrigationPlan? {
        val minutes = input.durationMinutes
        val valid = input.trees in 1..MAX_TREES && input.emittersPerTree in 1..MAX_EMITTERS &&
            input.emitterFlowLph > 0.0 && input.emitterFlowLph <= MAX_FLOW_LPH &&
            (minutes == null || minutes in 1..MAX_MINUTES) &&
            (input.irrigationsPerWeek == null || input.irrigationsPerWeek in 1..MAX_WEEKLY) &&
            (input.targetLitersPerTree == null || input.targetLitersPerTree > 0.0)
        if (!valid) return null
        val installation = DripInstallation(input.trees, input.emittersPerTree, input.emitterFlowLph)
        val flowLph = input.trees.toDouble() * input.emittersPerTree * input.emitterFlowLph
        val dose = minutes?.let { WateringDoseCalculator.calculateDose(installation, it) }
        // Exact volume: rounding it first would shift small installations and their cost.
        val exactVolume = minutes?.let { flowLph * it / 60.0 / 1000.0 }
        val cost = if (minutes != null && exactVolume != null) input.tariff?.let { costMinor(it, exactVolume, minutes) } else null
        val weekly = input.irrigationsPerWeek
        return IrrigationPlan(
            totalFlowLph = flowLph,
            totalFlowM3PerHour = round3(flowLph / 1000.0),
            dose = dose,
            volumeM3 = exactVolume?.let(::round3),
            minutesForTarget = input.targetLitersPerTree?.let { WateringDoseCalculator.estimatedDurationMinutes(installation, it) },
            weeklyVolumeM3 = if (weekly != null && exactVolume != null) round3(exactVolume * weekly) else null,
            costPerIrrigationMinor = cost,
            weeklyCostMinor = if (cost != null && weekly != null) cost * weekly else null,
        )
    }

    private fun round3(value: Double): Double = BigDecimal.valueOf(value).setScale(3, RoundingMode.HALF_UP).toDouble()

    private fun costMinor(tariff: WaterTariff, volumeM3: Double, minutes: Int): Long {
        val units = when (tariff.tariffType) {
            WaterTariffType.PER_M3 -> BigDecimal.valueOf(volumeM3)
            WaterTariffType.PER_HOUR -> BigDecimal.valueOf(minutes.toLong()).divide(BigDecimal.valueOf(60), 6, RoundingMode.HALF_UP)
            WaterTariffType.FREE -> return 0L
        }
        return units.multiply(BigDecimal.valueOf(tariff.variablePricePerUnitMinor)).setScale(0, RoundingMode.HALF_UP).toLong()
    }
}
