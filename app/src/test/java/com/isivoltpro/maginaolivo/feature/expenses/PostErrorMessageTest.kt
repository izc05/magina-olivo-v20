package com.isivoltpro.maginaolivo.feature.expenses

import com.isivoltpro.maginaolivo.core.common.AppError
import org.junit.Assert.assertEquals
import org.junit.Test

/** #456: a DRAFT that cannot be confirmed says why, and goes back to review. */
class PostErrorMessageTest {
    @Test fun aRelationThatNoLongerHoldsAsksForReview() {
        listOf("farmId", "parcelId", "activityId", "campaignId", "harvestId").forEach { field ->
            assertEquals(
                "Este gasto necesita revisar su finca/parcela/trabajo antes de confirmarlo.",
                postErrorMessage(AppError.Validation(field, "not_found")),
            )
        }
    }

    @Test fun aDateAheadIsSaid() {
        assertEquals(
            "La fecha de este gasto es posterior a hoy. Corrígela antes de confirmarlo.",
            postErrorMessage(AppError.Validation("expenseDate", "future_real_expense")),
        )
    }

    @Test fun aClosedCampaignKeepsItsOwnWords() {
        assertEquals(
            "La campaña está cerrada: el coste histórico no se modifica.",
            postErrorMessage(AppError.Validation("campaignId", "campaign_closed")),
        )
    }
}
