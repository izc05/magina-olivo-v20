package com.isivoltpro.maginaolivo.account.reservation.transport

import com.isivoltpro.maginaolivo.account.reservation.*
import java.time.Instant
import java.util.UUID
import java.io.IOException
import kotlinx.serialization.json.*

/** Explicit credentials provider only: no app factory or automatic network consumer. */
class ReservationCredentials(val actor:ReservationActor,val accessToken:String,val expiresAt:Instant) {
    override fun toString()="ReservationCredentials(redacted)"
}
class ReservationWireResponse(val status:Int,val body:String) {
    override fun toString()="ReservationWireResponse(status=$status, body=redacted)"
}
fun interface ReservationRpcWire {
    fun post(body:String,bearer:String):ReservationWireResponse
}

/** Draft RPC adapter. No HTTP implementation or endpoint is installed by this class. */
class ReservationRpcTransport(
    private val issuer:String,
    private val remoteWorkspaceId:UUID,
    private val credentials:()->ReservationCredentials?,
    private val wire:ReservationRpcWire,
    private val now:()->Instant=Instant::now,
) : ReservationTransport {
    override fun reserve(scope:ReservationScope,request:ReservationRequest):ReservationResponse {
        require(scope.operationId==request.operationId)
        val credential=credentials() ?: return ReservationResponse.RetrySameOperation
        if(scope.actor.issuer!=issuer || credential.actor!=scope.actor || !credential.expiresAt.isAfter(now()) ||
            credential.accessToken.isBlank() || credential.accessToken.any { it.isISOControl() }) return ReservationResponse.RetrySameOperation
        val body=buildJsonObject {
            put("p_remote_workspace",remoteWorkspaceId.toString())
            put("p_request",buildJsonObject {
                put("payloadVersion",request.payloadVersion);put("operationId",request.operationId.toString())
                put("farmId",request.farmId.toString());put("localWorkspaceId",request.localWorkspaceId.toString())
                put("consentId",request.consentId.toString());put("consentTextVersion",request.consentTextVersion)
                put("manifestSha256",request.manifestSha256);put("aggregateVersion",request.aggregateVersion)
            })
        }.toString()
        val response=wire.post(body,credential.accessToken)
        if(response.status in setOf(400,403,409,422)) return ReservationResponse.Conflict
        if(response.status !in 200..299) return ReservationResponse.RetrySameOperation
        val receipt=try {
            check(response.body.toByteArray(Charsets.UTF_8).size<=65_536)
            val data=Json.parseToJsonElement(response.body).jsonObject
            check(data.keys==setOf("issuer","actorId","operationId","farmId","payloadHash","remoteWorkspaceId","receiptId"))
            fun field(name:String)=data.getValue(name).jsonPrimitive.also { check(it.isString) }.content
            ReservationReceipt(ReservationScope(ReservationActor(field("issuer"),UUID.fromString(field("actorId"))),
                UUID.fromString(field("operationId"))),UUID.fromString(field("farmId")),field("payloadHash"),
                UUID.fromString(field("remoteWorkspaceId")),UUID.fromString(field("receiptId"))).also {
                check(it.scope==scope && it.farmId==request.farmId && it.payloadHash==request.payloadHash() && it.remoteWorkspaceId==remoteWorkspaceId)
            }
        } catch (_:Exception) { throw IOException("reservation_invalid_response") }
        return ReservationResponse.Accepted(receipt)
    }
}
