package com.isivoltpro.maginaolivo.data.repository.sync

import com.isivoltpro.maginaolivo.data.local.model.SyncEntityType
import com.isivoltpro.maginaolivo.data.repository.RecoveredDeliveryReceipt
import com.isivoltpro.maginaolivo.data.repository.RemoteDeliveryState
import com.isivoltpro.maginaolivo.feature.account.AuthSession
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * G2.4 data-plane adapter for the three Delivery RPCs of the #773 integration matrix. This file
 * defines the contract and the parsing; it opens no connection by itself.
 *
 * What it deliberately does NOT do:
 *  - It never derives the account from a response. The server takes the actor from `auth.uid()`
 *    and does not return it, so [AuthSession.userId] is the only source of `accountId`.
 *  - It never recomputes a hash over the JSON it read back. The authorized hash of an operation
 *    is the one `check_sync_operations` reports for that operation; the caller checks it against
 *    the exact persisted UTF-8 bytes.
 *  - It never decides that a dispatch failed. A network error is an UNKNOWN outcome.
 */

internal class DeliveryRpcResponse(val status: Int, val body: String) {
    override fun toString() = "DeliveryRpcResponse(status=$status, body=redacted)"
}

/**
 * One remote procedure call by contracted name. The route, the host and the headers belong to the
 * implementation, not to this contract: the adapter only knows the three names in [DeliveryRpcAdapter.NAMES]
 * and refuses anything else.
 */
internal fun interface DeliveryRpcTransport {
    suspend fun rpc(name: String, bodyJson: String, accessToken: String): DeliveryRpcResponse
}

internal enum class DeliveryRpcError {
    /** 23505 / 409. The server refused a duplicate; the local intent is untouched. */
    CONFLICT,

    /** 42501 / 403 with a valid JWT. Not retryable by itself: authority is missing. */
    FORBIDDEN,

    /** 22023 / 400. The payload was rejected; it is NEVER edited to make it acceptable. */
    INVALID_PAYLOAD,

    /**
     * No answer, a cut connection, a timeout or a 5xx. The outcome of the operation is UNKNOWN:
     * the server may well have committed it, so this is never reported as a failed dispatch.
     */
    NETWORK,

    /** An answer that does not match the contract. Treated as unknown, never as success. */
    PROTOCOL,
}

internal class DeliveryRpcFailure(val error: DeliveryRpcError, val status: Int? = null, val code: String? = null) :
    Exception("DeliveryRpcFailure(error=$error, status=$status, code=$code)") {

    /** True when the server's own record is the only way to learn what happened. */
    val outcomeUnknown: Boolean get() = error == DeliveryRpcError.NETWORK || error == DeliveryRpcError.PROTOCOL
}

/** What `sync_own_delivery_v2` reports. The authorized hash is NOT here: it is read per operation. */
internal class SyncOwnDeliveryResult(
    val operationId: UUID,
    val entityId: UUID,
    val remoteVersion: Long,
    val payloadVersion: Int,
)

internal class DeliveryRpcAdapter(private val transport: DeliveryRpcTransport) {

    /**
     * Sends the Pesada. [payloadJson] is embedded VERBATIM as `p_delivery`, because the server
     * hashes `p_delivery::text` and any reserialization here would change those bytes.
     */
    suspend fun syncOwnDelivery(
        session: AuthSession,
        operationId: UUID,
        payloadJson: String,
    ): SyncOwnDeliveryResult {
        val body = """{"p_operation_id":${quote(operationId.toString())},"p_delivery":$payloadJson}"""
        val root = objectOf(call(SYNC_OWN_DELIVERY_V2, body, session))
        val reported = uuid(root, "operation_id") ?: throw protocol()
        if (reported != operationId) throw protocol()
        return SyncOwnDeliveryResult(
            operationId = reported,
            entityId = uuid(root, "entity_id") ?: throw protocol(),
            remoteVersion = long(root, "remote_version") ?: throw protocol(),
            payloadVersion = (long(root, "payload_version") ?: throw protocol()).toInt(),
        )
    }

    /**
     * The historical receipt of each operation, in batches of at most [BATCH], as the matrix
     * requires. An operation with no receipt is simply absent: that is NOT evidence of failure.
     * `accountId` is filled from the verified session, never from the response.
     */
    suspend fun checkSyncOperations(
        session: AuthSession,
        operationIds: List<UUID>,
    ): Map<UUID, RecoveredDeliveryReceipt> {
        val account = session.userId.toString()
        val receipts = mutableMapOf<UUID, RecoveredDeliveryReceipt>()
        for (batch in operationIds.distinct().chunked(BATCH)) {
            val ids = batch.joinToString(",") { quote(it.toString()) }
            val root = objectOf(call(CHECK_SYNC_OPERATIONS, """{"p_operation_ids":[$ids]}""", session))
            val confirmed = root["confirmed_receipts"] as? JsonArray ?: throw protocol()
            for (element in confirmed) {
                val row = element as? JsonObject ?: throw protocol()
                val operationId = uuid(row, "operation_id") ?: throw protocol()
                val type = text(row, "entity_type") ?: throw protocol()
                receipts[operationId] = RecoveredDeliveryReceipt(
                    operationId = operationId,
                    deliveryId = uuid(row, "entity_id") ?: throw protocol(),
                    // Whatever the server says it was. A type this app does not know is not a
                    // Delivery either, so it maps to null and the guard drops the receipt.
                    entityType = SyncEntityType.entries.firstOrNull { it.name == type }
                        ?: throw DeliveryRpcFailure(DeliveryRpcError.PROTOCOL),
                    accountId = account,
                    remoteVersion = long(row, "remote_version") ?: throw protocol(),
                    payloadHash = text(row, "payload_hash") ?: throw protocol(),
                    payloadVersion = (long(row, "payload_version") ?: throw protocol()).toInt(),
                )
            }
        }
        return receipts
    }

    /**
     * The Delivery's current head. `head_payload_hash` is null when the server cannot point to a
     * single DELIVERY operation of this actor at the current version; the caller must then keep
     * a conflict, never guess an ACK.
     */
    suspend fun getOwnDeliveryV2(session: AuthSession, deliveryId: UUID): Pair<Long?, String?> {
        val root = objectOf(call(GET_OWN_DELIVERY_V2, """{"p_delivery_id":${quote(deliveryId.toString())}}""", session))
        val delivery = root["delivery"] as? JsonObject ?: return null to null
        return long(delivery, "version") to text(root, "head_payload_hash")
    }

    /**
     * Everything the G2.4 reconciliation needs that is not in Room, assembled from the two read
     * RPCs: the historical receipt of THIS operation and the current head of the Pesada.
     */
    suspend fun remoteDeliveryState(
        session: AuthSession,
        operationId: UUID,
        deliveryId: UUID,
    ): RemoteDeliveryState {
        val receipt = checkSyncOperations(session, listOf(operationId))[operationId]
        val (headVersion, headPayloadHash) = getOwnDeliveryV2(session, deliveryId)
        return RemoteDeliveryState(receipt, headVersion, headPayloadHash)
    }

    // ------------------------------------------------------------------------------- internals

    private suspend fun call(name: String, body: String, session: AuthSession): JsonObject {
        check(name in NAMES) { "RPC fuera del contrato G2.4: $name" }
        val response = try {
            transport.rpc(name, body, session.accessToken)
        } catch (failure: DeliveryRpcFailure) {
            throw failure
        } catch (cancellation: CancellationException) {
            // Review #774: cancellation is cooperative control flow, NOT a network error.
            // Swallowing it here would report OUTCOME_UNKNOWN for a request the caller itself
            // cancelled, and would break structured concurrency: the enclosing scope would never
            // learn it was cancelled, while `withActiveRequest` would still be unwound by this
            // throw. It must travel untouched.
            throw cancellation
        } catch (_: Exception) {
            // A cut connection is an unknown outcome, not a failed dispatch.
            throw DeliveryRpcFailure(DeliveryRpcError.NETWORK)
        }
        if (response.status !in 200..299) throw failureFor(response)
        val parsed = runCatching { Json.parseToJsonElement(response.body) }.getOrNull() ?: throw protocol()
        // PostgREST returns a single-row result as a one-element array for some functions.
        return when (parsed) {
            is JsonObject -> parsed
            is JsonArray -> parsed.firstOrNull()?.jsonObject ?: throw protocol()
            else -> throw protocol()
        }
    }

    private fun failureFor(response: DeliveryRpcResponse): DeliveryRpcFailure {
        val code = runCatching {
            (Json.parseToJsonElement(response.body) as? JsonObject)?.get("code")?.jsonPrimitive?.contentOrNull
        }.getOrNull()
        val error = when {
            code == "23505" || response.status == 409 -> DeliveryRpcError.CONFLICT
            code == "42501" || response.status == 403 -> DeliveryRpcError.FORBIDDEN
            code == "22023" || response.status == 400 -> DeliveryRpcError.INVALID_PAYLOAD
            response.status in 500..599 || response.status == 408 || response.status == 429 ->
                DeliveryRpcError.NETWORK
            else -> DeliveryRpcError.PROTOCOL
        }
        return DeliveryRpcFailure(error, response.status, code)
    }

    private fun protocol() = DeliveryRpcFailure(DeliveryRpcError.PROTOCOL)

    private fun objectOf(root: JsonObject) = root

    private fun text(row: JsonObject, key: String): String? =
        (row[key] as? JsonPrimitive)?.takeIf { it != JsonNull }?.contentOrNull

    private fun long(row: JsonObject, key: String): Long? =
        (row[key] as? JsonPrimitive)?.longOrNull

    private fun uuid(row: JsonObject, key: String): UUID? =
        text(row, key)?.let { runCatching { UUID.fromString(it) }.getOrNull() }

    private fun quote(value: String) = Json.encodeToString(JsonPrimitive.serializer(), JsonPrimitive(value))

    companion object {
        const val SYNC_OWN_DELIVERY_V2 = "sync_own_delivery_v2"
        const val CHECK_SYNC_OPERATIONS = "check_sync_operations"
        const val GET_OWN_DELIVERY_V2 = "get_own_delivery_v2"

        /** The matrix caps `check_sync_operations` at 200 operation ids per call. */
        const val BATCH = 200

        val NAMES = setOf(SYNC_OWN_DELIVERY_V2, CHECK_SYNC_OPERATIONS, GET_OWN_DELIVERY_V2)
    }
}
