package com.isivoltpro.maginaolivo.data.repository

import com.isivoltpro.maginaolivo.feature.account.AuthSession
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlinx.coroutines.CancellationException

/**
 * #808 Android transport: which payload version a NEW Pesada operation is created with.
 *
 * The choice is made ONCE, by the producer, per operation, and is frozen in the outbox row
 * (`payload_version`) and in its PREPARE record (version + hash). The RPC follows from it without
 * ambiguity: DELIVERY + 2 -> `sync_own_delivery_v2`, DELIVERY + 3 -> `sync_own_delivery_v3`. Nothing
 * ever re-labels an existing operation: an operation already queued, prepared, in flight or with an
 * unknown outcome keeps its UUID, bytes, version and RPC.
 *
 * **Production uses [LegacyDeliveryRoute]**: every Pesada stays on v2 exactly as before. The v3
 * route only exists in isolated tests until a later owner decision wires it.
 */
fun interface DeliveryPayloadRoute {
    /** Version for a new CREATE of a Pesada in [workspaceId], bound to [accountId] (null = local only). */
    suspend fun createVersion(workspaceId: UUID, accountId: String?): Int
}

/** The only route production constructs: v2, unchanged. */
object LegacyDeliveryRoute : DeliveryPayloadRoute {
    override suspend fun createVersion(workspaceId: UUID, accountId: String?) = 2
}

/**
 * v3 for a new CREATE only when the bound account is believed to own the workspace. [ownerHint] may
 * be stale or offline evidence: it only selects the route. Authority is decided later, fresh and by
 * the server, at PREPARE ([WorkspaceOwnerVerifier]); a non-owner's v3 operation is then refused and
 * retained, never redirected to v2.
 */
internal class HistoricalDeliveryRoute(
    private val ownerHint: suspend (workspaceId: UUID, accountId: String) -> Boolean,
) : DeliveryPayloadRoute {
    override suspend fun createVersion(workspaceId: UUID, accountId: String?): Int =
        if (accountId != null && ownerHint(workspaceId, accountId)) 3 else 2
}

/** Fresh, server-verified: the session's subject is the OWNER of [workspaceId] right now. */
fun interface WorkspaceOwnerVerifier {
    suspend fun isCurrentOwner(session: AuthSession, workspaceId: UUID): Boolean
}

/**
 * Reads `list_authorized_workspaces_v1` with the verified session and requires: exact issuer, the
 * same subject, a fresh contract, an unexpired grant, and `role = OWNER` for that workspace.
 * A member with `write` (even with every permission), a G0 farm grant or a farm creator is NOT an
 * owner. A malformed answer is "not an owner"; a transport failure propagates (nothing is sent).
 */
internal class ServerWorkspaceOwnerVerifier(
    private val remote: WorkspaceAuthorizationRemote,
    private val issuerBase: String,
    private val now: () -> Instant = Instant::now,
) : WorkspaceOwnerVerifier {
    override suspend fun isCurrentOwner(session: AuthSession, workspaceId: UUID): Boolean {
        val body = remote.list(session)
        val contract = try {
            WorkspaceAuthorizationContract.parse(body)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: RuntimeException) {
            return false
        }
        val at = now()
        return contract.validFor(issuerBase, session.userId, at) &&
            contract.available(at).any { it.id == workspaceId && it.role == OWNER }
    }

    private companion object {
        const val OWNER = "OWNER"
    }
}

/**
 * `originCreatedAt` of a Pesada: the creation instant of its local record, as Room stores it
 * (millisecond precision). Never the enqueue time, the last edit, or the time of a retry; it is the
 * same value at every recapture of the payload and after a restart.
 */
internal fun deliveryOriginCreatedAt(createdAt: Instant): String = createdAt.truncatedTo(ChronoUnit.MILLIS).toString()

/** #808 retention codes. Both are BLOCKED: never sent again automatically, never deleted. */
internal object DeliveryRetentionCodes {
    /** The v3 receipt matches this device's bytes, but no v3-compatible HEAD read exists to ACK it. */
    const val V3_ACK_AWAITING_HEAD = "V3_ACK_AWAITING_HEAD"

    /** `sync review: <CAUSE>` from the server: kept for explicit review. */
    const val REVIEW_PREFIX = "SYNC_REVIEW:"
}
