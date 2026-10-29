package com.isivoltpro.maginaolivo.data.repository.sync

import com.isivoltpro.maginaolivo.feature.account.AuthSession
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

internal data class CampaignCasRemoteAck(val operationId: UUID, val campaignId: UUID, val remoteVersion: Long)
internal data class CampaignCasRemoteReceipt(
    val operationId: UUID, val campaignId: UUID, val accountId: UUID,
    val entityType: String, val payloadHash: String, val payloadVersion: Int, val remoteVersion: Long,
)

/** Reuses the G2.4 transport boundary; this class opens no connection. */
internal class CampaignCasRpcAdapter(private val transport: DeliveryRpcTransport) {
    suspend fun update(session: AuthSession, operationId: UUID, expectedVersion: Long,
        campaign: JsonObject): CampaignCasRemoteAck {
        val body = """{"p_operation_id":"$operationId","p_expected_version":$expectedVersion,"p_campaign":$campaign}"""
        val root = call("update_own_campaign_v1", body, session)
        val ack = CampaignCasRemoteAck(
            UUID.fromString(root.getValue("operation_id").jsonPrimitive.content),
            UUID.fromString(root.getValue("entity_id").jsonPrimitive.content),
            root.getValue("remote_version").jsonPrimitive.long,
        )
        if (ack.operationId != operationId || ack.remoteVersion != expectedVersion + 1 ||
            root.getValue("payload_version").jsonPrimitive.long != 1L ||
            root.getValue("status").jsonPrimitive.content != "APPLIED") throw protocol()
        return ack
    }

    suspend fun receipt(session: AuthSession, operationId: UUID): CampaignCasRemoteReceipt? {
        val root = call("check_sync_operations", """{"p_operation_ids":["$operationId"]}""", session)
        val rows = root["confirmed_receipts"] as? JsonArray ?: throw protocol()
        if (rows.isEmpty()) return null
        if (rows.size != 1) throw protocol()
        val row = rows.single() as? JsonObject ?: throw protocol()
        val found = CampaignCasRemoteReceipt(
            UUID.fromString(row.getValue("operation_id").jsonPrimitive.content),
            UUID.fromString(row.getValue("entity_id").jsonPrimitive.content),
            session.userId,
            row.getValue("entity_type").jsonPrimitive.content,
            row.getValue("payload_hash").jsonPrimitive.content,
            row.getValue("payload_version").jsonPrimitive.long.toInt(),
            row.getValue("remote_version").jsonPrimitive.long,
        )
        if (found.operationId != operationId || found.remoteVersion < 1 ||
            !Regex("^[0-9a-f]{64}$").matches(found.payloadHash)) throw protocol()
        return found
    }

    private suspend fun call(name: String, body: String, session: AuthSession): JsonObject {
        check(name == "update_own_campaign_v1" || name == "check_sync_operations")
        val response = try { transport.rpc(name, body, session.accessToken) }
        catch (cancel: CancellationException) { throw cancel }
        catch (failure: DeliveryRpcFailure) { throw failure }
        catch (_: Exception) { throw DeliveryRpcFailure(DeliveryRpcError.NETWORK) }
        if (response.status !in 200..299) {
            val code = runCatching {
                (Json.parseToJsonElement(response.body) as JsonObject).getValue("code").jsonPrimitive.content
            }.getOrNull()
            val error = when {
                code == "23505" || response.status == 409 -> DeliveryRpcError.CONFLICT
                code == "42501" || response.status in listOf(401,403) -> DeliveryRpcError.FORBIDDEN
                code == "22023" || response.status == 400 -> DeliveryRpcError.INVALID_PAYLOAD
                response.status == 408 || response.status == 429 || response.status >= 500 -> DeliveryRpcError.NETWORK
                else -> DeliveryRpcError.PROTOCOL
            }
            throw DeliveryRpcFailure(error, response.status, code)
        }
        val parsed = runCatching { Json.parseToJsonElement(response.body) }.getOrNull()
        return when (parsed) {
            is JsonObject -> parsed
            is JsonArray -> parsed.singleOrNull() as? JsonObject ?: throw protocol()
            else -> throw protocol()
        }
    }

    private fun protocol() = DeliveryRpcFailure(DeliveryRpcError.PROTOCOL)
}
