package com.isivoltpro.maginaolivo.data.repository.sync

import java.io.ByteArrayInputStream
import java.io.InputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** 2c-2 (#812 D2): the real byte bound applies to the stream itself, before any parse. */
class ParentReceiveV2HttpsBoundTest {
    @Test fun readsAtMostTheBoundWhateverContentLengthSays() {
        val limit = 1024
        assertEquals(limit, HttpsDeliveryRpcTransport.readBounded(ByteArrayInputStream(ByteArray(limit) { 'a'.code.toByte() }), limit, 200).length)
        val failure = assertThrows(DeliveryRpcFailure::class.java) {
            HttpsDeliveryRpcTransport.readBounded(ByteArrayInputStream(ByteArray(limit + 1)), limit, 200)
        }
        assertEquals(ParentReceiveV2Coordinator.RESPONSE_TOO_LARGE, failure.reason)
        assertEquals("", HttpsDeliveryRpcTransport.readBounded(null, limit, 204))
    }

    @Test fun anEndlessBodyIsCutRightAfterTheBound() {
        var served = 0L
        val endless = object : InputStream() {
            override fun read(): Int { served++; return 'x'.code }
            override fun read(b: ByteArray, off: Int, len: Int): Int { b.fill('x'.code.toByte(), off, off + len); served += len; return len }
        }
        assertThrows(DeliveryRpcFailure::class.java) { HttpsDeliveryRpcTransport.readBounded(endless, 64 * 1024, 200) }
        assertTrue("read $served bytes", served <= 64 * 1024 + 8192)
    }

    @Test fun onlyTheV2ParentsScopeHasTheEightMebibyteBound() {
        assertEquals(PARENT_GRAPH_V2_MAX_BYTES, HttpsDeliveryRpcTransport.RpcScope.PARENT_RECEIVE_V2.maxResponseBytes)
        assertEquals(setOf(ParentReceiveV2Coordinator.RPC), HttpsDeliveryRpcTransport.RpcScope.PARENT_RECEIVE_V2.allowedNames)
        // #808 R3-b: the Jornada receive scope carries the R2 contract bound (4 MiB), and only its two RPCs.
        assertEquals(HarvestReceiveContract.MAX_BYTES, HttpsDeliveryRpcTransport.RpcScope.HARVEST_RECEIVE.maxResponseBytes)
        assertEquals(HarvestReceiveContract.NAMES, HttpsDeliveryRpcTransport.RpcScope.HARVEST_RECEIVE.allowedNames)
        HttpsDeliveryRpcTransport.RpcScope.entries
            .filter { it != HttpsDeliveryRpcTransport.RpcScope.PARENT_RECEIVE_V2 && it != HttpsDeliveryRpcTransport.RpcScope.HARVEST_RECEIVE }
            .forEach { assertEquals(it.name, HttpsDeliveryRpcTransport.MAX_RESPONSE_BYTES, it.maxResponseBytes) }
    }
}
