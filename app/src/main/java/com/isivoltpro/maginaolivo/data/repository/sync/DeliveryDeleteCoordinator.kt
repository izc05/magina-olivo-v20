package com.isivoltpro.maginaolivo.data.repository.sync

import androidx.room.withTransaction
import com.isivoltpro.maginaolivo.data.local.MaginaOlivoDatabase
import com.isivoltpro.maginaolivo.data.local.entity.SyncOutboxEntity
import com.isivoltpro.maginaolivo.data.local.model.OutboxOperation
import com.isivoltpro.maginaolivo.data.local.model.OutboxStatus
import com.isivoltpro.maginaolivo.data.local.model.SyncEntityType
import com.isivoltpro.maginaolivo.data.local.model.SyncStatus
import com.isivoltpro.maginaolivo.data.repository.WorkspaceOwnerVerifier
import com.isivoltpro.maginaolivo.data.repository.pgObject
import com.isivoltpro.maginaolivo.data.repository.pgScalar
import com.isivoltpro.maginaolivo.feature.account.AccountRepository
import com.isivoltpro.maginaolivo.feature.account.AuthFailure
import com.isivoltpro.maginaolivo.feature.account.AuthSession
import com.isivoltpro.maginaolivo.feature.accountlink.FarmLinkLedger
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

internal enum class DeliveryDeleteOutcome {
    STAGED,
    /** The Pesada never reached the server and nothing of it was ever prepared: resolved with zero RPC. */
    RESOLVED_NEVER_SENT,
    /**
     * Another operation of this Pesada is not settled yet (PENDING, PROCESSING, FAILED/OUTCOME_UNKNOWN, BLOCKED,
     * an unsettled PREPARE or an unreconciled held receipt): nothing is frozen or sent, one at a time.
     */
    WAITING_SIBLING,
    /** No remote version but something of it was prepared or confirmed: kept for review, never guessed. */
    RETAINED,
    SYNCED,
    ALREADY_CONFIRMED,
    CONFLICT,
    REVIEW_HELD,
    OUTCOME_UNKNOWN,
    CONSENT_REQUIRED,
    NOT_AUTHORIZED,
    OFFLINE,
    REJECTED,
}

/** Typed `last_error_code` values of DELIVERY DELETE operations. BLOCKED ones are never re-sent automatically. */
internal object DeliveryDeleteCodes {
    const val RESOLVED_NEVER_SENT = "DELETE_RESOLVED_NEVER_SENT"
    const val REMOTE_UNKNOWN = "DELETE_REMOTE_UNKNOWN"
    const val RECEIPT_CONFLICT = "DELETE_RECEIPT_CONFLICT"
    const val HEAD_MISMATCH = "DELETE_HEAD_MISMATCH"
    const val CONFLICT_PREFIX = "DELETE_CONFLICT:"
    const val INVALID_PREFIX = "DELETE_INVALID:"
    const val REVIEW_PREFIX = "SYNC_REVIEW:"
    const val OUTCOME_UNKNOWN = "OUTCOME_UNKNOWN"
    /** Set by `quarantineSiblings` when a write receipt of the same Pesada was applied while the delete waited. */
    const val NEEDS_REBASE = "NEEDS_REBASE"
}

/**
 * #808 R1-a: sender of the logical delete of a Pesada, through `delete_own_delivery_v1`,
 * `check_sync_operations` and `get_own_delivery_delete_head_v1` only (never a direct table write).
 * Explicit QA entry point: no composition root, scheduler or worker constructs it.
 *
 * - The local delete is unchanged: it tombstones the Pesada, recomputes its AUTO_DAY locally and leaves one
 *   unbound DELETE marker. [stage] freezes ONCE, into that marker, the account, the farm and an envelope with
 *   the expected remote version; the hash is the server's canonical DELETE hash. It never stages while any
 *   other operation, PREPARE or held receipt of the Pesada is unsettled, and a Pesada the server never saw is
 *   resolved without a request.
 * - [send] needs the fresh server OWNER of the workspace, then the delete, this actor's exact receipt
 *   (DELIVERY, payload version 3, the DELETE hash) and the strict delete HEAD naming this operation at the
 *   receipt's version. Only then, in one local transaction, is the delete confirmed. A v3 WRITE receipt can
 *   never confirm it: a write hash is never the DELETE hash, and the HEAD must report `deleted`.
 * - A lost response stays OUTCOME_UNKNOWN with the SAME UUID and bytes. The retry reads this actor's receipt
 *   first and, only if there is none, re-sends the SAME operation (idempotent on the server).
 * - STALE_VERSION, DELETED and any receipt or HEAD disagreement are BLOCKED with their cause: never rebased,
 *   never retried in a loop, the local tombstone and the marker are kept.
 */
internal class DeliveryDeleteCoordinator(
    private val database: MaginaOlivoDatabase,
    private val account: AccountRepository,
    private val issuer: String,
    private val transport: DeliveryRpcTransport,
    private val ledger: (UUID) -> FarmLinkLedger?,
    private val ownerVerifier: WorkspaceOwnerVerifier?,
    private val now: () -> Instant = Instant::now,
) {
    suspend fun stage(operationId: UUID): DeliveryDeleteOutcome = try {
        if (issuer.isBlank()) DeliveryDeleteOutcome.NOT_AUTHORIZED
        else account.withServerVerifiedIdentity(remote = { Unit }) { userId, _ ->
            database.withTransaction { stageLocked(operationId, userId) }
        }
    } catch (cancel: CancellationException) { throw cancel
    } catch (failure: AuthFailure) {
        if (failure.kind == AuthFailure.Kind.NETWORK) DeliveryDeleteOutcome.OFFLINE else DeliveryDeleteOutcome.NOT_AUTHORIZED
    }

    private suspend fun stageLocked(operationId: UUID, userId: UUID): DeliveryDeleteOutcome {
        val outbox = database.syncOutboxDao()
        val op = outbox.findById(operationId) ?: return DeliveryDeleteOutcome.REJECTED
        val unbound = op.entityType == SyncEntityType.DELIVERY && op.operation == OutboxOperation.DELETE &&
            op.accountId == null && op.payloadJson == null && op.attemptCount == 0 &&
            (op.status == OutboxStatus.PENDING ||
                (op.status == OutboxStatus.BLOCKED && op.lastErrorCode == DeliveryDeleteCodes.NEEDS_REBASE))
        if (!unbound) return DeliveryDeleteOutcome.REJECTED
        val pesada = database.deliveryDao().findById(op.entityId) ?: return DeliveryDeleteOutcome.REJECTED
        if (pesada.metadata.deletedAt == null) return DeliveryDeleteOutcome.REJECTED
        if (unsettled(op)) return DeliveryDeleteOutcome.WAITING_SIBLING
        val workspace = database.workspaceDao().findById(pesada.workspaceId) ?: return DeliveryDeleteOutcome.NOT_AUTHORIZED
        if (workspace.metadata.deletedAt != null || workspace.ownerUserId != userId) return DeliveryDeleteOutcome.NOT_AUTHORIZED
        val farm = database.farmDao().findById(pesada.farmId) ?: return DeliveryDeleteOutcome.REJECTED
        if (farm.workspaceId != pesada.workspaceId) return DeliveryDeleteOutcome.REJECTED
        if (!consented(userId, pesada.workspaceId, pesada.farmId)) return DeliveryDeleteOutcome.CONSENT_REQUIRED
        val at = now().toEpochMilli()
        val remote = pesada.metadata.remoteVersion
        if (remote == null) {
            // Never on the server only when nothing of it was ever prepared or confirmed by this device.
            val touched = database.deliveryDispatchPrepareDao().listForDelivery(pesada.id).isNotEmpty() ||
                outbox.listForEntity(SyncEntityType.DELIVERY, pesada.id).any { it.id != op.id }
            val code = if (touched) DeliveryDeleteCodes.REMOTE_UNKNOWN else DeliveryDeleteCodes.RESOLVED_NEVER_SENT
            return if (outbox.resolveUnstagedDeliveryDelete(op.id, code, at) == 1) {
                if (touched) DeliveryDeleteOutcome.RETAINED else DeliveryDeleteOutcome.RESOLVED_NEVER_SENT
            } else DeliveryDeleteOutcome.REJECTED
        }
        val envelope = DeliveryDeleteEnvelope(issuer, pesada.metadata.version, pesada.id, remote)
        return if (outbox.stageDeliveryDelete(op.id, userId.toString(), pesada.farmId, envelope.json(), at) == 1)
            DeliveryDeleteOutcome.STAGED else DeliveryDeleteOutcome.REJECTED
    }

    /** True while ANY other operation, PREPARE or held receipt of the Pesada has no settled outcome. */
    private suspend fun unsettled(op: SyncOutboxEntity): Boolean =
        database.syncOutboxDao().listForEntity(SyncEntityType.DELIVERY, op.entityId)
            .any { it.id != op.id && it.status != OutboxStatus.CONFIRMED } ||
            database.deliveryDispatchPrepareDao().listForDelivery(op.entityId).any { it.settledAt == null } ||
            database.deliveryHeldReceiptDao().countUnreconciled(op.entityId) > 0

    suspend fun send(operationId: UUID, lease: DeliveryDispatchLease): DeliveryDeleteOutcome {
        lease.requireOpen()
        if (issuer.isBlank()) return DeliveryDeleteOutcome.NOT_AUTHORIZED
        val op = database.syncOutboxDao().findById(operationId) ?: return DeliveryDeleteOutcome.REJECTED
        if (op.entityType != SyncEntityType.DELIVERY || op.operation != OutboxOperation.DELETE) return DeliveryDeleteOutcome.REJECTED
        if (op.status == OutboxStatus.CONFIRMED) return DeliveryDeleteOutcome.ALREADY_CONFIRMED
        val accountId = op.accountId ?: return DeliveryDeleteOutcome.REJECTED
        val payload = op.payloadJson ?: return DeliveryDeleteOutcome.REJECTED
        val recovering = op.status == OutboxStatus.FAILED && op.lastErrorCode == DeliveryDeleteCodes.OUTCOME_UNKNOWN
        if (op.status != OutboxStatus.PENDING && !recovering) return DeliveryDeleteOutcome.REJECTED
        val envelope = parseDeliveryDeleteEnvelope(payload) ?: return DeliveryDeleteOutcome.REJECTED
        val pesada = database.deliveryDao().findById(op.entityId) ?: return DeliveryDeleteOutcome.REJECTED
        if (envelope.issuer != issuer || envelope.deliveryId != pesada.id || op.farmId != pesada.farmId ||
            pesada.metadata.deletedAt == null) return DeliveryDeleteOutcome.REJECTED
        if (unsettled(op)) return DeliveryDeleteOutcome.WAITING_SIBLING
        // Only the account that froze it, signed in now, may send it; the server re-verifies it below.
        if (account.state.value.userId?.toString() != accountId) return DeliveryDeleteOutcome.NOT_AUTHORIZED
        if (!consented(UUID.fromString(accountId), pesada.workspaceId, pesada.farmId)) return DeliveryDeleteOutcome.CONSENT_REQUIRED
        return try {
            lease.withActiveRequest {
                account.withServerVerifiedIdentity(remote = { session ->
                    if (session.userId.toString() != accountId) return@withServerVerifiedIdentity Remote.Unauthorized
                    val verifier = ownerVerifier ?: return@withServerVerifiedIdentity Remote.Unauthorized
                    if (!verifier.isCurrentOwner(session, pesada.workspaceId)) return@withServerVerifiedIdentity Remote.Unauthorized
                    exchange(session, op.id, envelope, recovering)
                }, commit = { userId, result ->
                    if (userId.toString() != accountId) return@withServerVerifiedIdentity DeliveryDeleteOutcome.NOT_AUTHORIZED
                    when (result) {
                        is Remote.Confirmed -> applyDelete(op, result.version)
                        is Remote.Conflict -> block(op, DeliveryDeleteCodes.CONFLICT_PREFIX + result.cause, result.detail,
                            DeliveryDeleteOutcome.CONFLICT)
                        is Remote.Review -> block(op, DeliveryDeleteCodes.REVIEW_PREFIX + result.cause, null, DeliveryDeleteOutcome.REVIEW_HELD)
                        Remote.ReceiptConflict -> block(op, DeliveryDeleteCodes.RECEIPT_CONFLICT, null, DeliveryDeleteOutcome.CONFLICT)
                        Remote.HeadMismatch -> block(op, DeliveryDeleteCodes.HEAD_MISMATCH, null, DeliveryDeleteOutcome.CONFLICT)
                        is Remote.Invalid -> block(op, DeliveryDeleteCodes.INVALID_PREFIX + result.cause, null, DeliveryDeleteOutcome.REJECTED)
                        Remote.Unknown -> {
                            database.syncOutboxDao().markDeliveryDeleteUnknown(op.id, accountId, payload, now().toEpochMilli())
                            DeliveryDeleteOutcome.OUTCOME_UNKNOWN
                        }
                        Remote.Unauthorized -> DeliveryDeleteOutcome.NOT_AUTHORIZED
                    }
                })
            }
        } catch (cancel: CancellationException) { throw cancel
        } catch (failure: AuthFailure) {
            if (failure.kind == AuthFailure.Kind.NETWORK) DeliveryDeleteOutcome.OFFLINE else DeliveryDeleteOutcome.NOT_AUTHORIZED
        }
    }

    /**
     * Under the one verified session: on a retry after an unknown outcome, this actor's receipt FIRST (no second
     * write if the first one was applied); otherwise the delete. Then the receipt and the strict delete HEAD.
     */
    private suspend fun exchange(session: AuthSession, operationId: UUID, envelope: DeliveryDeleteEnvelope, recovering: Boolean): Remote {
        val earlier: JsonObject? = if (recovering) when (val read = receiptOf(session, operationId)) {
            is Read.Row -> read.row
            Read.None -> null
            is Read.Failed -> return read.remote
        } else null
        var ackVersion: Long? = null
        val receipt: JsonObject = if (earlier != null) earlier else {
            val body = """{"p_operation_id":"$operationId","p_delivery_id":"${envelope.deliveryId}","p_expected_version":${envelope.expectedVersion}}"""
            when (val written = call(session, DELETE_RPC, body)) {
                is Answer.Failed -> return written.remote
                is Answer.Ok -> {
                    val ack = written.body
                    val version = ack["remote_version"].number()
                    if (ack["operation_id"].text() != operationId.toString() || ack["entity_id"].text() != envelope.deliveryId.toString() ||
                        ack["status"].text() != "APPLIED" || ack["payload_version"].number() != PAYLOAD_VERSION ||
                        ack["deleted"].bool() != true || ack["idempotent"].bool() == null ||
                        version == null || version <= envelope.expectedVersion) return Remote.Unknown
                    ackVersion = version
                }
            }
            when (val read = receiptOf(session, operationId)) {
                is Read.Row -> read.row
                Read.None -> return Remote.Unknown
                is Read.Failed -> return read.remote
            }
        }
        val version = receipt["remote_version"].number() ?: return Remote.ReceiptConflict
        if (receipt["operation_id"].text() != operationId.toString() || receipt["entity_id"].text() != envelope.deliveryId.toString() ||
            receipt["entity_type"].text() != "DELIVERY" || receipt["payload_version"].number() != PAYLOAD_VERSION ||
            receipt["payload_hash"].text() != envelope.hash || version <= envelope.expectedVersion ||
            (ackVersion != null && ackVersion != version)) return Remote.ReceiptConflict
        val head = when (val read = call(session, HEAD_RPC, """{"p_delivery_id":"${envelope.deliveryId}"}""")) {
            is Answer.Ok -> read.body
            is Answer.Failed -> return read.remote
        }
        if (head["contract_version"].number() != 1L || head["delivery_id"].text() != envelope.deliveryId.toString() ||
            head["deleted"].bool() != true || head["version"].number() != version ||
            head["head_operation_id"].text() != operationId.toString() || head["head_payload_version"].number() != PAYLOAD_VERSION ||
            head["head_payload_hash"].text() != envelope.hash) return Remote.HeadMismatch
        return Remote.Confirmed(version)
    }

    private sealed interface Read {
        data class Row(val row: JsonObject) : Read
        data object None : Read
        data class Failed(val remote: Remote) : Read
    }

    private suspend fun receiptOf(session: AuthSession, operationId: UUID): Read {
        val body = when (val read = call(session, RECEIPT_RPC, """{"p_operation_ids":["$operationId"]}""")) {
            is Answer.Ok -> read.body
            is Answer.Failed -> return Read.Failed(read.remote)
        }
        val rows = body["confirmed_receipts"] as? JsonArray ?: return Read.Failed(Remote.Unknown)
        if (rows.isEmpty()) return Read.None
        return (rows.singleOrNull() as? JsonObject)?.let { Read.Row(it) } ?: Read.Failed(Remote.ReceiptConflict)
    }

    private sealed interface Answer {
        data class Ok(val body: JsonObject) : Answer
        data class Failed(val remote: Remote) : Answer
    }

    /** Raw call: keeps the server's typed token and, for STALE_VERSION, the remote version it reports. */
    private suspend fun call(session: AuthSession, name: String, body: String): Answer {
        val response = try { transport.rpc(name, body, session.accessToken) }
        catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { return Answer.Failed(Remote.Unknown) }
        val parsed = runCatching { Json.parseToJsonElement(response.body) as JsonObject }.getOrNull()
        if (response.status in 200..299) return parsed?.let { Answer.Ok(it) } ?: Answer.Failed(Remote.Unknown)
        val code = parsed?.get("code").text()
        val token = parsed?.get("message").text()?.let { TYPED.matchEntire(it) }
        val kind = token?.groupValues?.get(1)
        val cause = token?.groupValues?.get(2)
        return Answer.Failed(when {
            kind == "review" && cause != null && code in setOf("22023", "42501") -> Remote.Review(cause)
            kind == "conflict" && cause != null && code == "23505" ->
                Remote.Conflict(cause, parsed?.get("details").text()?.takeIf { DETAIL.matches(it) })
            code == "42501" || response.status in setOf(401, 403) -> Remote.Unauthorized
            kind == "invalid" && cause != null -> Remote.Invalid(cause)
            code == "22023" || response.status == 400 -> Remote.Invalid("PAYLOAD")
            else -> Remote.Unknown
        })
    }

    /** ONE transaction: the marker is confirmed and the tombstoned Pesada takes the deleted remote version. */
    private suspend fun applyDelete(op: SyncOutboxEntity, version: Long): DeliveryDeleteOutcome = database.withTransaction {
        val outbox = database.syncOutboxDao()
        val current = outbox.findById(op.id) ?: return@withTransaction DeliveryDeleteOutcome.REJECTED
        if (current.payloadJson != op.payloadJson || current.accountId != op.accountId ||
            current.status !in setOf(OutboxStatus.PENDING, OutboxStatus.FAILED)) return@withTransaction DeliveryDeleteOutcome.REJECTED
        val pesada = database.deliveryDao().findById(op.entityId) ?: return@withTransaction DeliveryDeleteOutcome.REJECTED
        if (pesada.metadata.deletedAt == null || pesada.farmId != op.farmId) return@withTransaction DeliveryDeleteOutcome.REJECTED
        if (outbox.markConfirmed(op.id, now().toEpochMilli()) != 1) return@withTransaction DeliveryDeleteOutcome.REJECTED
        // Only the sync metadata of the tombstone changes: its fields, its parcels and the Jornada are untouched.
        database.deliveryDao().upsert(pesada.copy(metadata = pesada.metadata.copy(
            remoteVersion = maxOf(pesada.metadata.remoteVersion ?: 0L, version),
            syncStatus = SyncStatus.SYNCED, lastSyncedAt = now())))
        DeliveryDeleteOutcome.SYNCED
    }

    private suspend fun block(op: SyncOutboxEntity, code: String, message: String?, outcome: DeliveryDeleteOutcome) =
        if (database.syncOutboxDao().blockDeliveryDelete(op.id, op.accountId!!, op.payloadJson!!, code, message,
                now().toEpochMilli()) == 1) outcome else DeliveryDeleteOutcome.REJECTED

    private fun consented(userId: UUID, workspaceId: UUID, farmId: UUID): Boolean {
        val current = runCatching { ledger(userId) }.getOrNull() ?: return false
        if (current.subject != "$issuer|$userId" || farmId !in current.activeFarmIds || current.latestRevocation(farmId) != null) return false
        return current.consents.any { consent -> consent.farms.any { it.id == farmId && it.workspaceId == workspaceId } }
    }

    private sealed interface Remote {
        data class Confirmed(val version: Long) : Remote
        data class Conflict(val cause: String, val detail: String?) : Remote
        data class Review(val cause: String) : Remote
        data class Invalid(val cause: String) : Remote
        data object ReceiptConflict : Remote
        data object HeadMismatch : Remote
        data object Unknown : Remote
        data object Unauthorized : Remote
    }

    internal companion object {
        const val DELETE_RPC = "delete_own_delivery_v1"
        const val RECEIPT_RPC = "check_sync_operations"
        const val HEAD_RPC = "get_own_delivery_delete_head_v1"
        val NAMES = setOf(DELETE_RPC, RECEIPT_RPC, HEAD_RPC)
        /** The delete receipt belongs to the v3 contract family (R1-a SQL). */
        const val PAYLOAD_VERSION = 3L
        private val TYPED = Regex("^sync (review|denied|invalid|conflict): ([A-Z][A-Z_]{0,63})$")
        private val DETAIL = Regex("^remote_version=[0-9]{1,19}$")
    }
}

private fun kotlinx.serialization.json.JsonElement?.text(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
private fun kotlinx.serialization.json.JsonElement?.number(): Long? = (this as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull
private fun kotlinx.serialization.json.JsonElement?.bool(): Boolean? = (this as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull

/**
 * The server's hash of a Pesada delete (R1-a SQL):
 * sha256(jsonb_build_object('kind','DELETE','deliveryId',id,'expectedVersion',v)::text), keys by length then bytes.
 */
internal fun deliveryDeleteHash(deliveryId: UUID, expectedVersion: Long): String {
    require(expectedVersion >= 1L)
    val value = buildJsonObject { put("kind", "DELETE"); put("deliveryId", deliveryId.toString()); put("expectedVersion", expectedVersion) }
    val text = pgObject(value) { key, element -> pgScalar(element, key != "expectedVersion", false) }
    return MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) }
}

/** Frozen once at [DeliveryDeleteCoordinator.stage]: the bytes of the marker never change afterwards. */
internal data class DeliveryDeleteEnvelope(
    val issuer: String,
    val localVersion: Long,
    val deliveryId: UUID,
    val expectedVersion: Long,
) {
    val hash: String get() = deliveryDeleteHash(deliveryId, expectedVersion)

    fun json(): String = buildJsonObject {
        put("issuer", issuer); put("localVersion", localVersion); put("kind", "DELETE")
        put("deliveryId", deliveryId.toString()); put("expectedVersion", expectedVersion)
    }.toString()
}

internal fun parseDeliveryDeleteEnvelope(payload: String): DeliveryDeleteEnvelope? = runCatching {
    val root = Json.parseToJsonElement(payload) as JsonObject
    require(root.keys == setOf("issuer", "localVersion", "kind", "deliveryId", "expectedVersion"))
    require(root["kind"].text() == "DELETE")
    DeliveryDeleteEnvelope(root["issuer"].text()!!, root["localVersion"].number()!!, UUID.fromString(root["deliveryId"].text()),
        root["expectedVersion"].number()!!).also { it.hash }
}.getOrNull()
