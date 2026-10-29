package com.isivoltpro.maginaolivo.data.repository.sync

import com.isivoltpro.maginaolivo.data.local.entity.CampaignEntity
import com.isivoltpro.maginaolivo.data.local.entity.LocalMetadata
import com.isivoltpro.maginaolivo.data.local.model.CampaignStatus
import com.isivoltpro.maginaolivo.data.repository.buildCampaignCasEnvelope
import com.isivoltpro.maginaolivo.data.repository.parseCampaignCasEnvelope
import com.isivoltpro.maginaolivo.feature.account.AuthSession
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CampaignCasRpcAdapterTest {
    private val accountId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
    private val campaignId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc")
    private val operationId = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd")
    private val session = AuthSession(accountId,"a@example.test","access","refresh",Instant.parse("2027-01-01T00:00:00Z"))
    private val row = CampaignEntity(campaignId,
        UUID.fromString("11111111-1111-1111-1111-111111111111"),
        UUID.fromString("22222222-2222-2222-2222-222222222222"),
        "Campaña con áé",LocalDate.parse("2026-01-01"),status=CampaignStatus.PREPARATION,
        metadata=LocalMetadata(Instant.EPOCH,Instant.EPOCH,version=4,remoteVersion=7))

    @Test fun immutableEnvelopeAndCanonicalHash() {
        val text=buildCampaignCasEnvelope(row,"https://project.example",7)
        val parsed=parseCampaignCasEnvelope(text)
        assertNotNull(parsed)
        assertEquals(4L,parsed!!.localVersion)
        assertEquals(7L,parsed.expectedVersion)
        // PostgreSQL 17: sha256(jsonb_build_object('expectedVersion',7,'campaign',...)::text).
        assertEquals("d1e3bc3958cf22bc1f32746a1a46802050cefe67e9740e0684d9088ea9baf7c7",parsed.hash)
        assertTrue(Regex("[0-9a-f]{64}").matches(parsed.hash))
        assertNull(parseCampaignCasEnvelope(text.replace("\"localVersion\":4","\"localVersion\":0")))
        assertNull(parseCampaignCasEnvelope(text.replace("\"campaign\":", "\"extra\":true,\"campaign\":")))
    }

    @Test fun updateCarriesExpectedVersionAndReceiptBindsToSession() = runBlocking {
        val snapshot=parseCampaignCasEnvelope(buildCampaignCasEnvelope(row,"https://project.example",7))!!
        val calls=mutableListOf<Pair<String,String>>()
        val transport=DeliveryRpcTransport { name,body,token ->
            assertEquals("access",token)
            calls+=name to body
            if(name=="update_own_campaign_v1") DeliveryRpcResponse(200,
                """{"operation_id":"$operationId","entity_id":"$campaignId","remote_version":8,"payload_version":1,"status":"APPLIED"}""")
            else DeliveryRpcResponse(200,"""{"confirmed_receipts":[{"operation_id":"$operationId","entity_id":"$campaignId","entity_type":"CAMPAIGN","payload_hash":"${snapshot.hash}","payload_version":1,"remote_version":8}]}""")
        }
        val adapter=CampaignCasRpcAdapter(transport)
        assertEquals(8L,adapter.update(session,operationId,7,snapshot.campaign).remoteVersion)
        val receipt=adapter.receipt(session,operationId)!!
        assertEquals(accountId,receipt.accountId)
        assertEquals(snapshot.hash,receipt.payloadHash)
        assertTrue(calls[0].second.contains("\"p_expected_version\":7"))
        assertEquals(listOf("update_own_campaign_v1","check_sync_operations"),calls.map { it.first })
    }

    @Test fun conflictAndUnknownResponseNeverBecomeAck() = runBlocking {
        val snapshot=parseCampaignCasEnvelope(buildCampaignCasEnvelope(row,"https://project.example",7))!!
        val conflict=CampaignCasRpcAdapter(DeliveryRpcTransport { _,_,_ ->
            DeliveryRpcResponse(409,"""{"code":"23505"}""")
        })
        val failure=runCatching { conflict.update(session,operationId,7,snapshot.campaign) }.exceptionOrNull()
        assertEquals(DeliveryRpcError.CONFLICT,(failure as DeliveryRpcFailure).error)
        val missing=CampaignCasRpcAdapter(DeliveryRpcTransport { _,_,_ ->
            DeliveryRpcResponse(200,"""{"confirmed_receipts":[]}""")
        })
        assertNull(missing.receipt(session,operationId))
    }

    @Test fun authorizedHeadRequiresExactCampaignAndVersion() = runBlocking {
        val valid="""{"contract_version":1,"issuer":"https://project.example/auth/v1","subject":"$accountId","version":9,"campaign":{"id":"$campaignId","workspaceId":"${row.workspaceId}","farmId":"${row.farmId}","name":"Remote","startDate":"2026-01-01","endDate":null,"status":"PREPARATION","notes":null}}"""
        val adapter=CampaignCasRpcAdapter(DeliveryRpcTransport { name,body,token ->
            assertEquals("get_own_campaign_head_v1",name)
            assertEquals("access",token)
            assertTrue(body.contains(campaignId.toString()))
            DeliveryRpcResponse(200,valid)
        })
        assertEquals(9L,adapter.head(session,campaignId).version)
        assertEquals("\"Remote\"",adapter.head(session,campaignId).campaign["name"].toString())
        val foreign=CampaignCasRpcAdapter(DeliveryRpcTransport { _,_,_ ->
            DeliveryRpcResponse(200,valid.replace(campaignId.toString(),operationId.toString()))
        })
        assertEquals(DeliveryRpcError.PROTOCOL,
            (runCatching { foreign.head(session,campaignId) }.exceptionOrNull() as DeliveryRpcFailure).error)
    }
}
