package com.isivoltpro.maginaolivo.feature.catastro

import com.isivoltpro.maginaolivo.domain.registry.RegistryLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CadastralMunicipalityCatalogTest {
    @Test fun ruralReferenceUsesCadastralToIneConcordance() {
        assertEquals(RegistryLocation("Bedmar y Garcíez", "Jaén"),
            CadastralMunicipalityCatalog.resolve("23013A01500072"))
    }

    @Test fun unknownOrNonRuralReferenceIsNotGuessed() {
        assertNull(CadastralMunicipalityCatalog.resolve("23013"))
        assertNull(CadastralMunicipalityCatalog.resolve("23902A01500072"))
        assertNull(CadastralMunicipalityCatalog.resolve("not-a-reference"))
    }
}
