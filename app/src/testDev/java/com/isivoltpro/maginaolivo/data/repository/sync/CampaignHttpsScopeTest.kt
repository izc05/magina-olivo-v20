package com.isivoltpro.maginaolivo.data.repository.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CampaignHttpsScopeTest {
    @Test fun campaignScopeAllowsOnlyItsThreeContractedRpcs() {
        val names = HttpsDeliveryRpcTransport.RpcScope.CAMPAIGN_CAS.allowedNames
        assertEquals(setOf("update_own_campaign_v1", "check_sync_operations", "get_own_campaign_head_v1"), names)
        assertFalse("sync_own_delivery_v2" in names)
        assertFalse("get_own_delivery_receive_v1" in names)
    }

    @Test fun existingDeliveryScopeDoesNotGainCampaignWrites() {
        val names = HttpsDeliveryRpcTransport.RpcScope.DELIVERY.allowedNames
        assertTrue("sync_own_delivery_v2" in names)
        assertFalse("update_own_campaign_v1" in names)
        assertFalse("get_own_campaign_head_v1" in names)
    }
}
