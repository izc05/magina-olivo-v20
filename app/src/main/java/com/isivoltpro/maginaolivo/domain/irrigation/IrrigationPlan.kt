package com.isivoltpro.maginaolivo.domain.irrigation

/** What the farmer types into the calculator. Nothing here is saved. */
data class IrrigationPlanInput(
    val trees: Int,
    val emittersPerTree: Int,
    val emitterFlowLph: Double,
    val durationMinutes: Int,
    val targetLitersPerTree: Double? = null,
    val irrigationsPerWeek: Int? = null,
    val tariff: WaterTariff? = null,
)

/** Always a theoretical result; a measured watering lives in the Cuaderno. */
data class IrrigationPlan(
    val totalFlowLph: Double,
    val totalFlowM3PerHour: Double,
    val dose: CalculatedDose,
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

    /** Null when an input is missing or out of range: no figure is better than an invented one. */
    fun calculate(input: IrrigationPlanInput): IrrigationPlan? {
        val valid = input.trees in 1..MAX_TREES && input.emittersPerTree in 1..MAX_EMITTERS &&
            input.emitterFlowLph > 0.0 && input.emitterFlowLph <= MAX_FLOW_LPH &&
            input.durationMinutes in 0..MAX_MINUTES &&
            (input.irrigationsPerWeek == null || input.irrigationsPerWeek in 1..MAX_WEEKLY) &&
            (input.targetLitersPerTree == null || input.targetLitersPerTree > 0.0)
        if (!valid) return null
        val installation = DripInstallation(input.trees, input.emittersPerTree, input.emitterFlowLph)
        val dose = WateringDoseCalculator.calculateDose(installation, input.durationMinutes)
        val cost = input.tariff?.calculateEstimatedCostMinor(dose.theoreticalVolumeM3, input.durationMinutes)
        val weekly = input.irrigationsPerWeek
        return IrrigationPlan(
            totalFlowLph = installation.totalFlowRateLph,
            totalFlowM3PerHour = installation.totalFlowRateM3PerHour,
            dose = dose,
            minutesForTarget = input.targetLitersPerTree?.let { WateringDoseCalculator.estimatedDurationMinutes(installation, it) },
            weeklyVolumeM3 = weekly?.let { DripInstallation.round2(dose.theoreticalVolumeM3 * it) },
            costPerIrrigationMinor = cost,
            weeklyCostMinor = if (cost != null && weekly != null) cost * weekly else null,
        )
    }
}
