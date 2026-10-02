package com.isivoltpro.maginaolivo.feature.harvests

import com.isivoltpro.maginaolivo.core.common.AppError
import com.isivoltpro.maginaolivo.feature.expenses.expenseErrorMessage
import org.junit.Assert.assertTrue
import org.junit.Test

class MachineryFinancialErrorTest {
    @Test fun incompleteHistoricalMachineryExplainsHowToUnblockRental() {
        val error = AppError.Validation("appliedPrice", "confirm_missing_prices")
        assertTrue(expenseErrorMessage(error).contains("Confirma"))
        assertTrue(expenseErrorMessage(error).contains("maquinaria"))
        assertTrue(machineryPriceErrorMessage(error)!!.contains("maquinaria"))
    }
}
