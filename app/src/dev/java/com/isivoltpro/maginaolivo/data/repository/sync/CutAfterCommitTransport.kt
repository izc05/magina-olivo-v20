package com.isivoltpro.maginaolivo.data.repository.sync

import java.util.concurrent.atomic.AtomicInteger

/**
 * DEV-only, deterministic response cut for the single-Pesada drill.
 *
 * The request is forwarded to [inner] untouched, so the server really commits. The response of
 * the FIRST `sync_own_delivery_v2` call is then discarded and reported as
 * [DeliveryRpcError.NETWORK], which is exactly what the worker sees when a connection dies after
 * the commit: the outcome is unknown and recovery must go through `check_sync_operations`.
 *
 * Guarantees, each asserted by `CutAfterCommitTransportTest`:
 *  - it never alters, re-sends or duplicates a request: one forwarded call is one inner call;
 *  - it cuts only a SUCCESSFUL (2xx) response: a rejection is returned as is and does not use up the cut;
 *  - it cuts at most once, so a retry is never cut again;
 *  - every other RPC (the recovery reads, the aggregate read) passes through untouched.
 *
 * Not wired anywhere. It becomes reachable only through the temporary drill activation.
 */
internal class CutAfterCommitTransport(private val inner: DeliveryRpcTransport) : DeliveryRpcTransport {
    private val cuts = AtomicInteger(0)
    val cutCount: Int get() = cuts.get()

    override suspend fun rpc(name: String, bodyJson: String, accessToken: String): DeliveryRpcResponse {
        val response = inner.rpc(name, bodyJson, accessToken)
        if (name == "sync_own_delivery_v2" && response.status in 200..299 && cuts.compareAndSet(0, 1)) {
            throw DeliveryRpcFailure(DeliveryRpcError.NETWORK, status = null, code = "DRILL_CUT_AFTER_COMMIT")
        }
        return response
    }
}
