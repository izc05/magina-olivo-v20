package com.isivoltpro.maginaolivo.feature.harvests

import com.isivoltpro.maginaolivo.core.common.AppError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class HarvestFinancialErrorTest {
    @Test fun deleteBlockedByPaidLabourExplainsTheRealCause() {
        val message = harvestDeleteErrorMessage(AppError.Validation("amount", "below_paid"))
        assertEquals(
            "No puedes eliminar este día porque dejaría pagos de jornales por encima del coste registrado. Revisa primero los pagos de la campaña.",
            message,
        )
        assertFalse(message.contains("parcel", ignoreCase = true))
        assertFalse(message.contains("kilo", ignoreCase = true))
    }

    @Test fun genericHarvestValidationNeverPretendsAnUpdateWasADelete() {
        val message = harvestErrorMessage(AppError.Validation("amount", "below_paid"))
        assertFalse(message.contains("eliminar", ignoreCase = true))
    }
}
