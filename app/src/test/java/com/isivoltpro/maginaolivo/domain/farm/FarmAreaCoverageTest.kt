package com.isivoltpro.maginaolivo.domain.farm

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FarmAreaCoverageTest {
    private fun farm(managed: Double?, managedParcels: Long, cadastral: Double?, cadastralParcels: Long, parcels: Long) = Farm(
        id = UUID.randomUUID(), workspaceId = UUID.randomUUID(), name = "Finca", description = null, municipality = null,
        province = null, notes = null, coverDocumentId = null, parcelCount = parcels, managedAreaM2 = managed,
        activeCampaignName = null, archivedAt = null, version = 1,
        managedParcelCount = managedParcels, cadastralAreaM2 = cadastral, cadastralParcelCount = cadastralParcels,
    )

    @Test fun withoutAnyWorkedAreaItSaysSoAndNeverZero() {
        val coverage = FarmAreaCoverage.of(listOf(farm(null, 0, 1_790_965.0, 1, 1)))
        assertNull(coverage.managedAreaM2)
        assertEquals("Sin indicar", coverage.managedLabel)
        assertEquals("Superficie trabajada indicada en 0 de 1 parcela · falta 1", coverage.managedCoverageText)
    }

    @Test fun theCadastralSumStaysApartWithItsCoverageAndSource() {
        val coverage = FarmAreaCoverage.of(listOf(farm(null, 0, 1_790_965.0, 1, 1)))
        assertEquals("Catastral: 179,1 ha · 1 de 1 parcela · según Catastro", coverage.cadastralText)
    }

    @Test fun addsOnlyKnownWorkedAreasAcrossFarms() {
        val coverage = FarmAreaCoverage.of(listOf(farm(15_000.0, 2, 16_000.0, 2, 3), farm(null, 0, null, 0, 2)))
        assertEquals(15_000.0, coverage.managedAreaM2!!, 0.0)
        assertEquals("1,5 ha", coverage.managedLabel)
        assertEquals("Superficie trabajada indicada en 2 de 5 parcelas · faltan 3", coverage.managedCoverageText)
        assertEquals("Catastral: 1,6 ha · 2 de 5 parcelas · según Catastro", coverage.cadastralText)
    }

    @Test fun completeCoverageShowsNothingMissing() {
        assertEquals("Superficie trabajada indicada en 1 de 1 parcela",
            FarmAreaCoverage.of(listOf(farm(1_000.0, 1, null, 0, 1))).managedCoverageText)
    }

    @Test fun noParcelsAndNoCadastralMeanNoNotes() {
        val coverage = FarmAreaCoverage.of(emptyList())
        assertNull(coverage.managedCoverageText)
        assertNull(coverage.cadastralText)
        assertEquals("Sin indicar", coverage.managedLabel)
    }
}
