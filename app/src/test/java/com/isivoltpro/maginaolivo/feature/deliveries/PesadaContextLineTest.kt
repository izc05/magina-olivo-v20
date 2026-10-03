package com.isivoltpro.maginaolivo.feature.deliveries

import org.junit.Assert.assertEquals
import org.junit.Test

/** #373/#375: the fixed context of a Pesada reads «Salinillas · Campaña 2026-2027». */
class PesadaContextLineTest {
    @Test fun farmAndCampaignAreOneLine() {
        assertEquals("Salinillas · Campaña 2026/27", pesadaContextLine("Salinillas", "2026/27"))
    }

    @Test fun aCampaignAlreadyNamedCampañaIsNotSaidTwice() {
        assertEquals("Salinillas · Campaña 2026-2027", pesadaContextLine("Salinillas", "Campaña 2026-2027"))
        assertEquals("Estacas · campaña de verdeo", pesadaContextLine("Estacas", "campaña de verdeo"))
    }
}
