package com.isivoltpro.maginaolivo.feature.notebook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * #355 — regression: the compact summary must keep the warning the cards it replaced carried.
 *
 * A cost per kilo worked out from half the costs is lower than the real one, so it reads as good
 * news. The old «Coste/kg» card renamed itself «Coste contabilizado/kg» when anything was still
 * unconfirmed; the summary has to do the same, and say so in words as well as in the title.
 */
class CampaignSummaryLabelTest {
    @Test fun unconfirmedCostsAreNamedAsAccountedForAndSaidToBeIncomplete() {
        val (label, note) = campaignCostLabel(incomplete = true)
        assertEquals("Coste contabilizado/kg", label)
        assertEquals("Incompleto", note)
    }

    @Test fun aCompleteCampaignSaysCostPerKiloPlainly() {
        val (label, note) = campaignCostLabel(incomplete = false)
        assertEquals("Coste/kg", label)
        assertNull(note)
    }
}
