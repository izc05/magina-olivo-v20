package com.isivoltpro.maginaolivo.domain.irrigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IrrigationPlannerTest {
    private val base = IrrigationPlanInput(trees = 123, emittersPerTree = 4, emitterFlowLph = 4.0, durationMinutes = 90)

    @Test fun calculatesFlowVolumeAndLitersPerTree() {
        val plan = IrrigationPlanner.calculate(base)!!
        assertEquals(1_968.0, plan.totalFlowLph, 0.001)
        assertEquals(1.97, plan.totalFlowM3PerHour, 0.001)
        assertEquals(24.0, plan.dose.litersPerTree, 0.001)
        assertEquals(2.95, plan.dose.theoreticalVolumeM3, 0.001)
    }

    @Test fun findsTheDurationForATargetDose() {
        assertEquals(150, IrrigationPlanner.calculate(base.copy(targetLitersPerTree = 40.0))!!.minutesForTarget)
    }

    @Test fun weeklyFiguresNeedTheNumberOfIrrigations() {
        assertNull(IrrigationPlanner.calculate(base)!!.weeklyVolumeM3)
        assertEquals(8.85, IrrigationPlanner.calculate(base.copy(irrigationsPerWeek = 3))!!.weeklyVolumeM3!!, 0.001)
    }

    @Test fun estimatesCostByVolumeOrTime() {
        val perM3 = WaterTariff(WaterTariffType.PER_M3, variablePricePerUnitMinor = 20)
        assertEquals(59L, IrrigationPlanner.calculate(base.copy(tariff = perM3))!!.costPerIrrigationMinor)
        val perHour = WaterTariff(WaterTariffType.PER_HOUR, variablePricePerUnitMinor = 300)
        val plan = IrrigationPlanner.calculate(base.copy(tariff = perHour, irrigationsPerWeek = 2))!!
        assertEquals(450L, plan.costPerIrrigationMinor)
        assertEquals(900L, plan.weeklyCostMinor)
    }

    @Test fun withoutATariffThereIsNoCost() = assertNull(IrrigationPlanner.calculate(base)!!.costPerIrrigationMinor)

    @Test fun rejectsMissingOrImpossibleInputs() {
        assertNull(IrrigationPlanner.calculate(base.copy(trees = 0)))
        assertNull(IrrigationPlanner.calculate(base.copy(emittersPerTree = 0)))
        assertNull(IrrigationPlanner.calculate(base.copy(emitterFlowLph = 0.0)))
        assertNull(IrrigationPlanner.calculate(base.copy(irrigationsPerWeek = 0)))
        assertNull(IrrigationPlanner.calculate(base.copy(durationMinutes = IrrigationPlanner.MAX_MINUTES + 1)))
    }
}
