package com.isivoltpro.maginaolivo.feature.deliveries

import com.isivoltpro.maginaolivo.domain.organization.Organization
import com.isivoltpro.maginaolivo.domain.organization.OrganizationRole
import com.isivoltpro.maginaolivo.feature.expenses.ExpenseForm
import com.isivoltpro.maginaolivo.feature.expenses.supplierShown
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Test

/** #451: an editor shows the cooperative/supplier by the name the record was saved with. */
class RecordedOrganizationShownTest {
    private val coop = UUID.randomUUID()
    private val mill = UUID.randomUUID()
    private val renamed = Organization(coop, "S.C.A. San Isidro", setOf(OrganizationRole.COOPERATIVE))
    private val other = Organization(mill, "Almazara La Loma", setOf(OrganizationRole.MILL))

    @Test fun aRenamedCooperativeReadsWithItsSavedNameAndTheCurrentOneAsAHint() {
        val form = DeliveryForm(destinationOrganizationId = coop, recordedDestinationId = coop, recordedDestinationName = "Cooperativa San Isidro")
        assertEquals(DestinationShown("Cooperativa San Isidro", "S.C.A. San Isidro"), form.destinationShown(listOf(renamed, other)))
    }

    @Test fun anArchivedCooperativeStillReadsWithItsSavedName() {
        val form = DeliveryForm(destinationOrganizationId = coop, recordedDestinationId = coop, recordedDestinationName = "Cooperativa San Isidro")
        assertEquals(DestinationShown("Cooperativa San Isidro", null), form.destinationShown(listOf(other)))
    }

    @Test fun anotherCooperativeChosenNowReadsWithItsCurrentName() {
        val form = DeliveryForm(destinationOrganizationId = mill, recordedDestinationId = coop, recordedDestinationName = "Cooperativa San Isidro")
        assertEquals(DestinationShown("Almazara La Loma", null), form.destinationShown(listOf(renamed, other)))
        assertEquals(DestinationShown(null, null), DeliveryForm().destinationShown(listOf(renamed)))
    }

    @Test fun aGastoSupplierFollowsTheSameRule() {
        val form = ExpenseForm(supplierOrganizationId = coop, recordedSupplierId = coop, recordedSupplierName = "Cooperativa San Isidro")
        val shown = form.supplierShown(listOf(renamed))
        assertEquals("Cooperativa San Isidro", shown.name)
        assertEquals("S.C.A. San Isidro", shown.currentName)
        assertEquals("Almazara La Loma", form.copy(supplierOrganizationId = mill).supplierShown(listOf(other)).name)
    }
}
