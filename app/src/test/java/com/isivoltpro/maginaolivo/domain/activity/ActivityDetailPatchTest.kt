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

    @Test fun anotherTypeReplacesEverything() {
        val pruning = ActivityDetail.Pruning("Formación", 3, 6.0, null)
        assertEquals(pruning, ActivityDetailPatch.keepingHidden(stored, pruning))
    }
}
