package com.isivoltpro.maginaolivo.domain.activity

import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** #453: the same type keeps what the form never carries; another type keeps nothing. */
class ActivityDetailPatchTest {
    private val date = LocalDate.of(2026, 3, 1)
    private val expense = UUID.randomUUID()
    private val stored = ActivityDetail.Irrigation(180, 240.0, "Sector 3", "Goteo",
        IrrigationPrice(IrrigationPricingBasis.PER_M3, date, 12, 240.0, 2880, "USD", expense, "Factura"))

    @Test fun anIrrigationEditKeepsTheTariffsCurrencyLinkAndNotes() {
        val fromForm = ActivityDetail.Irrigation(200, 300.0, "Sector 3", "Goteo",
            IrrigationPrice(IrrigationPricingBasis.PER_M3, date, 12, 300.0, 3600))
        val price = (ActivityDetailPatch.keepingHidden(stored, fromForm) as ActivityDetail.Irrigation).price!!
        assertEquals("USD", price.currency)
        assertEquals(expense, price.linkedExpenseId)
        assertEquals("Factura", price.notes)
        assertEquals(3600L, price.estimatedAmountMinor)
    }

    @Test fun clearingTheTariffOnTheFormStillClearsIt() {
        val cleared = ActivityDetail.Irrigation(200, 300.0, "Sector 3", "Goteo", price = null)
        assertNull((ActivityDetailPatch.keepingHidden(stored, cleared) as ActivityDetail.Irrigation).price)
    }

    @Test fun anIncidentKeepsWhenItWasResolvedWhileStillResolved() {
        val at = Instant.parse("2026-03-02T10:00:00Z")
        val resolved = ActivityDetail.Incident("Plaga", IncidentSeverity.HIGH, IncidentState.RESOLVED, "Tratado", at)
        val edited = resolved.copy(actionTaken = "Tratado dos veces", resolvedAt = null)
        assertEquals(at, (ActivityDetailPatch.keepingHidden(resolved, edited) as ActivityDetail.Incident).resolvedAt)
        val reopened = edited.copy(state = IncidentState.OPEN)
        assertNull((ActivityDetailPatch.keepingHidden(resolved, reopened) as ActivityDetail.Incident).resolvedAt)
    }

    @Test fun aLegacyPhytosanitaryEditKeepsHiddenCueMetadata() {
        val operatorId = UUID.randomUUID()
        val machineId = UUID.randomUUID()
        val providerId = UUID.randomUUID()
        val fetchedAt = Instant.parse("2026-10-07T12:00:00Z")
        val storedTreatment = ActivityDetail.Phytosanitary(
            productName = "Cobre 50%",
            activeSubstance = "Oxicloruro de cobre",
            reason = "Repilo",
            equipmentText = "Atomizador",
            operatorPersonId = operatorId,
            applicationMachineId = machineId,
            serviceProviderOrganizationId = providerId,
            productRegistrationNumber = "ES-12345",
            productSource = "MAPA_REGFI",
            productSourceVersion = "2026-W41",
            productFetchedAt = fetchedAt,
            authorizationContextSnapshot = """{"crop":"olivo"}""",
            pestProblemCode = "REPILO",
            efficacyCode = "GOOD",
            treatmentObservations = "Sin deriva",
        )
        val legacyForm = ActivityDetail.Phytosanitary(
            productName = "Cobre 50%",
            activeSubstance = "Oxicloruro de cobre",
            reason = "Repilo leve",
            equipmentText = "Atomizador",
        )

        val patched = ActivityDetailPatch.keepingHidden(storedTreatment, legacyForm) as ActivityDetail.Phytosanitary
        assertEquals("Repilo leve", patched.reason)
        assertEquals(operatorId, patched.operatorPersonId)
        assertEquals(machineId, patched.applicationMachineId)
        assertEquals(providerId, patched.serviceProviderOrganizationId)
        assertEquals("ES-12345", patched.productRegistrationNumber)
        assertEquals("MAPA_REGFI", patched.productSource)
        assertEquals("2026-W41", patched.productSourceVersion)
        assertEquals(fetchedAt, patched.productFetchedAt)
        assertEquals("""{"crop":"olivo"}""", patched.authorizationContextSnapshot)
        assertEquals("REPILO", patched.pestProblemCode)
        assertEquals("GOOD", patched.efficacyCode)
        assertEquals("Sin deriva", patched.treatmentObservations)
    }

    @Test fun anotherTypeReplacesEverything() {
        val pruning = ActivityDetail.Pruning("Formación", 3, 6.0, null)
        assertEquals(pruning, ActivityDetailPatch.keepingHidden(stored, pruning))
    }

    @Test fun changingTheProductDoesNotKeepThePreviousAuthorization() {
        val stored = registeredTreatment()
        val incoming = ActivityDetail.Phytosanitary(productName = "Producto B", activeSubstance = "Sustancia B")
        val patched = ActivityDetailPatch.keepingHidden(stored, incoming) as ActivityDetail.Phytosanitary
        assertNull(patched.productRegistrationNumber)
        assertNull(patched.productSource)
        assertNull(patched.productSourceVersion)
        assertNull(patched.productFetchedAt)
        assertNull(patched.authorizationContextSnapshot)
    }

    @Test fun aPartialNewRegistrationDoesNotMixOldSnapshotFields() {
        val stored = registeredTreatment()
        val incoming = stored.copy(productRegistrationNumber = "ES-B", productSource = null,
            productSourceVersion = null, productFetchedAt = null, authorizationContextSnapshot = null)
        val patched = ActivityDetailPatch.keepingHidden(stored, incoming) as ActivityDetail.Phytosanitary
        assertEquals("ES-B", patched.productRegistrationNumber)
        assertNull(patched.productSource)
        assertNull(patched.productSourceVersion)
        assertNull(patched.productFetchedAt)
        assertNull(patched.authorizationContextSnapshot)
    }

    private fun registeredTreatment() = ActivityDetail.Phytosanitary(
        productName = "Producto A", activeSubstance = "Sustancia A",
        productRegistrationNumber = "ES-A", productSource = "MAPA_REGFI",
        productSourceVersion = "version-A", productFetchedAt = Instant.parse("2026-10-07T12:00:00Z"),
        authorizationContextSnapshot = "autorizacion-A",
    )
}
