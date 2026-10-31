package com.isivoltpro.maginaolivo.data.repository.sync

import com.isivoltpro.maginaolivo.data.local.MaginaOlivoDatabase
import com.isivoltpro.maginaolivo.data.repository.LocalFarmLinkRepository
import java.util.UUID

/**
 * DEV-ONLY composition point for the first single-Pesada drill. It is **prepared and switched
 * off**, which are two different things, and this file keeps them apart on purpose.
 *
 * Prepared: everything the drill needs is assembled here, so activation is one reviewable change
 * rather than a scramble. Switched off: [ENABLED] is a compile-time `false`, the only transport
 * this file can build is [DisabledDeliveryRpcTransport], which opens no connection, and
 * [runSingleDispatch] refuses before touching Room or the lease.
 *
 * Three things this file deliberately does NOT contain:
 *  - the real HTTPS client. It is never named, imported or constructed here. The architecture
 *    test `theDeliveryRpcHttpsClientLivesOnlyInTheDevFlavorAndIsNeverWired` enforces that for
 *    every source set, and `theDeliveryDrillStaysCompiledOffUntilItIsAuthorized` enforces that
 *    [ENABLED] is literally `false` in the source, so flipping it is visible in a diff.
 *  - any user-visible control. There is no button and no DevTools entry: a control that looked
 *    operational while the drill is unauthorized would be a lie to whoever pressed it. The drill
 *    is reachable only from an instrumented test.
 *  - any network permission of its own. Activation needs the owner's authorization to swap the
 *    transport, the DEV credentials in `local.properties`, and the RPCs actually deployed.
 *
 * To activate, in this order: the owner authorizes it; the real transport replaces
 * [DisabledDeliveryRpcTransport] in [transportForDrill]; the architecture allowlist records that
 * change with its date and PR; [ENABLED] becomes true. Until all four happen, this is inert.
 */
internal object DeliveryDrill {

    /**
     * Compile-time off switch. A constant, not a setting, so there is no runtime path and no
     * stored flag that could turn the drill on by accident or by someone else's data.
     */
    const val ENABLED = false

    /** Why the drill cannot run yet. Reported, never silently swallowed. */
    enum class Blocker {
        /** The owner has not authorized sending anything. [ENABLED] is false. */
        NOT_AUTHORIZED,

        /** The dev flavor carries no DEV project URL or publishable key. */
        NO_DEV_CREDENTIALS,

        /** The transport in use refuses by design; the real client is not wired. */
        TRANSPORT_DISABLED,
    }

    /**
     * Everything standing between this code and a real dispatch, as data rather than prose, so a
     * test can assert the list and a human can read it.
     */
    fun blockers(hasDevCredentials: Boolean): List<Blocker> = buildList {
        if (!ENABLED) add(Blocker.NOT_AUTHORIZED)
        if (!hasDevCredentials) add(Blocker.NO_DEV_CREDENTIALS)
        // Always, while [transportForDrill] can only return the refusing transport.
        add(Blocker.TRANSPORT_DISABLED)
    }

    fun isAvailable(hasDevCredentials: Boolean): Boolean = blockers(hasDevCredentials).isEmpty()

    /**
     * The only transport this composition point can produce. Swapping it is the authorized step;
     * until then every call it receives is refused without a socket.
     */
    fun transportForDrill(): DeliveryRpcTransport = DisabledDeliveryRpcTransport

    /** The worker, fully assembled, against the refusing transport. Safe to build and inspect. */
    fun worker(database: MaginaOlivoDatabase, links: LocalFarmLinkRepository): DeliveryDispatchWorker =
        DeliveryDispatchWorker(database, links, DeliveryRpcAdapter(transportForDrill()))

    /**
     * The drill itself: ONE Pesada, by its operation UUID, under a lease the caller already holds.
     * It refuses first and asks questions never: no Room read, no PREPARE, no lease use and no
     * request happen while any blocker stands.
     */
    suspend fun runSingleDispatch(
        database: MaginaOlivoDatabase,
        links: LocalFarmLinkRepository,
        operationId: UUID,
        lease: DeliveryDispatchLease,
        hasDevCredentials: Boolean = false,
    ): DispatchOutcome {
        val blockers = blockers(hasDevCredentials)
        check(blockers.isEmpty()) {
            "El ensayo de pesada no esta autorizado todavia: " + blockers.joinToString(", ")
        }
        return worker(database, links).dispatch(operationId, lease)
    }
}

/**
 * A transport that exists so the drill can be assembled without being able to send. Every call is
 * refused as [DeliveryRpcError.FORBIDDEN] — authority is missing, which is the truth — and no
 * connection is ever opened. FORBIDDEN rather than NETWORK on purpose: NETWORK would mean "the
 * outcome is unknown and the server may have committed", which would be a lie about a request
 * that never left the device.
 */
internal object DisabledDeliveryRpcTransport : DeliveryRpcTransport {
    override suspend fun rpc(name: String, bodyJson: String, accessToken: String): DeliveryRpcResponse =
        throw DeliveryRpcFailure(DeliveryRpcError.FORBIDDEN, status = null, code = "DRILL_DISABLED")
}
