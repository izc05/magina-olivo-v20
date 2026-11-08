package com.isivoltpro.maginaolivo.domain.irrigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class WateringDoseCalculatorTest {

    @Test
    fun standardInstallation_computesFlowRatesCorrectly() {
        // 500 olivos, 4 goteros/olivo, 4.0 L/h por gotero (total 2000 goteros)
        val installation = DripInstallation(
            oliveTreeCount = 500,
            emittersPerTree = 4,
            emitterFlowLph = 4.0,
            emitterType = EmitterType.PRESSURE_COMPENSATING,
        )

        assertEquals(2000, installation.totalEmitters)
        // 2000 goteros * 4.0 L/h = 8000.0 L/h
        assertEquals(8000.0, installation.totalFlowRateLph, 0.001)
        // 8000.0 L/h / 1000 = 8.0 m³/h
        assertEquals(8.0, installation.totalFlowRateM3PerHour, 0.001)
    }

    @Test
    fun standardDuration_computesVolumeAndDoseAccurately() {
        val installation = DripInstallation(
            oliveTreeCount = 200,
            emittersPerTree = 2,
            emitterFlowLph = 3.5, // 200 * 2 * 3.5 = 1400 L/h
        )

        // Riego de 3 horas (180 minutos)
        val dose = WateringDoseCalculator.calculateDose(installation, durationMinutes = 180)

        assertEquals(180, dose.durationMinutes)
        assertEquals(1.4, dose.totalFlowRateM3PerHour, 0.001)
        // 1400 L/h * 3 h = 4200.0 Litros
        assertEquals(4200.0, dose.theoreticalVolumeLiters, 0.001)
        // 4200 L / 1000 = 4.2 m³
        assertEquals(4.2, dose.theoreticalVolumeM3, 0.001)
        // 4200 L / 200 olivos = 21.0 L/olivo
        assertEquals(21.0, dose.litersPerTree, 0.001)
    }

    @Test
    fun zeroDuration_returnsZeroVolumeAndLiters() {
        val installation = DripInstallation(
            oliveTreeCount = 150,
            emittersPerTree = 3,
            emitterFlowLph = 4.0,
        )

        val dose = WateringDoseCalculator.calculateDose(installation, durationMinutes = 0)

        assertEquals(0, dose.durationMinutes)
        assertEquals(0.0, dose.theoreticalVolumeLiters, 0.001)
        assertEquals(0.0, dose.theoreticalVolumeM3, 0.001)
        assertEquals(0.0, dose.litersPerTree, 0.001)
    }

    @Test
    fun zeroTreesOrEmitters_returnsZeroDoseWithoutDivisionByZero() {
        val emptyInstallation = DripInstallation(
            oliveTreeCount = 0,
            emittersPerTree = 4,
            emitterFlowLph = 4.0,
        )

        val dose = WateringDoseCalculator.calculateDose(emptyInstallation, durationMinutes = 120)

        assertEquals(0.0, dose.theoreticalVolumeLiters, 0.001)
        assertEquals(0.0, dose.theoreticalVolumeM3, 0.001)
        assertEquals(0.0, dose.litersPerTree, 0.001)
    }

    @Test
    fun negativeParameters_throwIllegalArgumentException() {
        assertThrows(IllegalArgumentException::class.java) {
            DripInstallation(oliveTreeCount = -10, emittersPerTree = 2, emitterFlowLph = 4.0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            DripInstallation(oliveTreeCount = 100, emittersPerTree = -1, emitterFlowLph = 4.0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            DripInstallation(oliveTreeCount = 100, emittersPerTree = 2, emitterFlowLph = -2.0)
        }

        val valid = DripInstallation(oliveTreeCount = 100, emittersPerTree = 2, emitterFlowLph = 4.0)
        assertThrows(IllegalArgumentException::class.java) {
            WateringDoseCalculator.calculateDose(valid, durationMinutes = -30)
        }
    }

    @Test
    fun estimatedDurationMinutes_calculatesRequiredTimeForTargetDose() {
        // 2 goteros por olivo a 4.0 L/h = 8.0 L/h por olivo
        val installation = DripInstallation(
            oliveTreeCount = 300,
            emittersPerTree = 2,
            emitterFlowLph = 4.0,
        )

        // Objetivo: aplicar 60 L por olivo -> 60 / 8 = 7.5 horas = 450 minutos
        val minutes = WateringDoseCalculator.estimatedDurationMinutes(
            installation = installation,
            targetLitersPerTree = 60.0,
        )
        assertEquals(450, minutes)

        // Si el objetivo es 0 o negativo -> 0 minutos
        assertEquals(0, WateringDoseCalculator.estimatedDurationMinutes(installation, 0.0))
        assertEquals(0, WateringDoseCalculator.estimatedDurationMinutes(installation, -10.0))
    }
}

