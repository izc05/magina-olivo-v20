package com.isivoltpro.maginaolivo

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.isivoltpro.maginaolivo.data.repository.sync.CutAfterCommitTransport
import com.isivoltpro.maginaolivo.data.repository.sync.DeliveryRpcError
import com.isivoltpro.maginaolivo.data.repository.sync.DeliveryRpcFailure
import com.isivoltpro.maginaolivo.data.repository.sync.DeliveryRpcResponse
import com.isivoltpro.maginaolivo.data.repository.sync.DeliveryRpcTransport
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/** Proves the cut with a fake inner transport: no network, no remote write, no Room. */
@RunWith(AndroidJUnit4::class)
class CutAfterCommitTransportTest {

    private class Recording(private val status: Int = 200) : DeliveryRpcTransport {
        val calls = mutableListOf<Triple<String, String, String>>()
        override suspend fun rpc(name: String, bodyJson: String, accessToken: String): DeliveryRpcResponse {
            calls += Triple(name, bodyJson, accessToken)
            return DeliveryRpcResponse(status, "{\"ok\":\"$name\"}")
        }
    }

    private val body = "{\"p_delivery\":{\"id\":\"07db9e31-e48e-4041-8c82-bb875af0c5e4\"}}"

    @Test
    fun theFirstSyncIsForwardedOnceThenItsResponseIsCutAsNetwork() = runBlocking {
        val inner = Recording()
        val cut = CutAfterCommitTransport(inner)
        try {
            cut.rpc("sync_own_delivery_v2", body, "t")
            fail("The first sync must be cut")
        } catch (failure: DeliveryRpcFailure) {
            assertEquals(DeliveryRpcError.NETWORK, failure.error)
            assertTrue(failure.outcomeUnknown)
        }
        assertEquals("The request reached the inner transport exactly once", 1, inner.calls.size)
        assertEquals("Bytes forwarded verbatim", body, inner.calls.single().second)
        assertEquals(1, cut.cutCount)
    }

    @Test
    fun aRetryIsNeverCutAgainAndTheRecoveryReadsPassThrough() = runBlocking {
        val inner = Recording()
        val cut = CutAfterCommitTransport(inner)
        runCatching { cut.rpc("sync_own_delivery_v2", body, "t") }
        assertEquals("{\"ok\":\"check_sync_operations\"}", cut.rpc("check_sync_operations", "{}", "t").body)
        assertEquals("{\"ok\":\"get_own_delivery_v2\"}", cut.rpc("get_own_delivery_v2", "{}", "t").body)
        assertEquals("{\"ok\":\"sync_own_delivery_v2\"}", cut.rpc("sync_own_delivery_v2", body, "t").body)
        assertEquals(1, cut.cutCount)
        assertEquals(listOf("sync_own_delivery_v2", "check_sync_operations", "get_own_delivery_v2", "sync_own_delivery_v2"),
            inner.calls.map { it.first })
    }

    @Test
    fun aNonSuccessfulResponseIsReturnedAsIsAndDoesNotConsumeTheCut() = runBlocking {
        val rejected = CutAfterCommitTransport(Recording(status = 403))
        assertEquals(403, rejected.rpc("sync_own_delivery_v2", body, "t").status)
        assertEquals(0, rejected.cutCount)
        val serverError = CutAfterCommitTransport(Recording(status = 500))
        assertEquals(500, serverError.rpc("sync_own_delivery_v2", body, "t").status)
        assertEquals(0, serverError.cutCount)
    }

    @Test
    fun otherCallsBeforeTheSyncDoNotConsumeTheCut() = runBlocking {
        val inner = Recording()
        val cut = CutAfterCommitTransport(inner)
        cut.rpc("check_sync_operations", "{}", "t")
        assertEquals(0, cut.cutCount)
        assertTrue(runCatching { cut.rpc("sync_own_delivery_v2", body, "t") }.exceptionOrNull() is DeliveryRpcFailure)
        assertEquals(1, cut.cutCount)
    }
}
