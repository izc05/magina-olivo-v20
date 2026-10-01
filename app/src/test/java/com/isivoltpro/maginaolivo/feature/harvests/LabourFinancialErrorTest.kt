package com.isivoltpro.maginaolivo.feature.harvests

import com.isivoltpro.maginaolivo.core.common.AppError
import com.isivoltpro.maginaolivo.feature.expenses.expenseErrorMessage
import org.junit.Assert.*
import org.junit.Test

class LabourFinancialErrorTest {
    @Test fun closedCostAndBelowPaidExplainTheAction() {
        val closed = AppError.Validation("campaign", "campaign_closed")
        val paid = AppError.Validation("amount", "below_paid")
        assertTrue(labourErrorMessage(closed).contains("coste histórico"))
        assertTrue(labourErrorMessage(paid).contains("corregir los pagos"))
        assertTrue(expenseErrorMessage(closed).contains("coste histórico"))
        assertTrue(expenseErrorMessage(paid).contains("corregir los pagos"))
    }
    @Test fun uuidConflictDoesNotClaimCampaignIsClosed() {
        assertFalse(labourErrorMessage(AppError.Conflict("payment_id_conflict")).contains("cerrada"))
    }
}
