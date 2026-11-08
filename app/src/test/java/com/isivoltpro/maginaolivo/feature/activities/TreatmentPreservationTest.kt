package com.isivoltpro.maginaolivo.feature.activities

import com.isivoltpro.maginaolivo.domain.activity.ActivityDetail
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TreatmentPreservationTest {
    private val operator = UUID.randomUUID()

    @Test fun editingATreatmentKeepsWhatTheFormDoesNotShow() {
        val stored = ActivityDetail.Phytosanitary(productName = "Viejo", operatorPersonId = operator, authorizationContextSnapshot = "snapshot", efficacyCode = "ALTA")
        val edited = keepUnshownTreatmentData(ActivityDetail.Phytosanitary(productName = "Nuevo"), stored) as ActivityDetail.Phytosanitary
        assertEquals("Nuevo", edited.productName)
        assertEquals(operator, edited.operatorPersonId)
        assertEquals("snapshot", edited.authorizationContextSnapshot)
        assertEquals("ALTA", edited.efficacyCode)
    }

    @Test fun aNewTreatmentHasNothingToKeep() {
        val built = ActivityDetail.Phytosanitary(productName = "Nuevo")
        assertEquals(built, keepUnshownTreatmentData(built, null))
        assertNull(keepUnshownTreatmentData(null, null))
    }
}
