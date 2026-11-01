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

    @Test fun receiverScopeAllowsOnlyAuthorizedRead() {
        val names = HttpsDeliveryRpcTransport.RpcScope.CAMPAIGN_RECEIVE.allowedNames
        assertEquals(setOf("get_own_campaign_receive_v1"), names)
        assertFalse("update_own_campaign_v1" in names)
        assertFalse("sync_own_delivery_v2" in names)
    }

    @Test fun workspaceSendScopeAllowsOnlyWorkspaceCreateAndItsReceipt() {
        val names = HttpsDeliveryRpcTransport.RpcScope.WORKSPACE_SEND.allowedNames
        assertEquals(setOf("create_own_workspace", "check_sync_operations"), names)
        listOf("create_reserved_farm", "create_own_parcel", "update_own_campaign_v1", "sync_own_delivery_v2")
            .forEach { assertFalse(it, it in names) }
        HttpsDeliveryRpcTransport.RpcScope.entries.filter { it != HttpsDeliveryRpcTransport.RpcScope.WORKSPACE_SEND }
            .forEach { assertFalse(it.name, "create_own_workspace" in it.allowedNames) }
    }

    @Test fun farmSendScopeAllowsOnlyReservationFarmCreateAndReceipt() {
        val names = HttpsDeliveryRpcTransport.RpcScope.FARM_SEND.allowedNames
        assertEquals(setOf("reserve_local_farm_g1c", "create_reserved_farm", "check_sync_operations"), names)
        listOf("create_own_parcel", "create_own_workspace", "update_own_campaign_v1", "sync_own_delivery_v2")
            .forEach { assertFalse(it, it in names) }
        HttpsDeliveryRpcTransport.RpcScope.entries.filter { it != HttpsDeliveryRpcTransport.RpcScope.FARM_SEND }
            .forEach { assertFalse(it.name, "create_reserved_farm" in it.allowedNames) }
    }
}
