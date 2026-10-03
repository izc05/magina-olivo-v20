package com.isivoltpro.maginaolivo.feature.notebook

import com.isivoltpro.maginaolivo.data.local.model.CampaignStatus
import org.junit.Assert.assertEquals
import org.junit.Test

/** #351 (1): the Cuaderno chip says the campaign's state in words, not only by colour. */
class CampaignChipTextTest {
    @Test fun everyStateIsWritten() {
        assertEquals("Campaña 2026/27 · En marcha", campaignChipText("2026/27", CampaignStatus.ACTIVE))
        assertEquals("Campaña 2026/27 · En recolección", campaignChipText("Campaña 2026/27", CampaignStatus.HARVEST))
        assertEquals("Campaña 2027/28 · En preparación", campaignChipText("2027/28", CampaignStatus.PREPARATION))
        assertEquals("Campaña 2025/26 · Cerrada", campaignChipText("2025/26", CampaignStatus.CLOSED))
        assertEquals("Sin campaña en marcha", campaignChipText(null, null))
    }
}
