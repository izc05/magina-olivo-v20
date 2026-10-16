package com.isivoltpro.maginaolivo.account.reservation.transport

import com.isivoltpro.maginaolivo.account.reservation.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.io.File
import java.nio.file.Files
import com.isivoltpro.maginaolivo.account.reservation.persistence.DurableReservationJournal
import java.time.Instant
import java.util.UUID

class ReservationRpcTransportTest {
    private val time=Instant.parse("2026-10-16T00:00:00Z")
    private val actor=ReservationActor("https://issuer-a.example",UUID(0,1))
    private val scope=ReservationScope(actor,UUID(0,3))
    private val request=ReservationRequest(scope.operationId,UUID(0,2),UUID(0,4),UUID(0,5),2,"a".repeat(64),7)
    private val workspace=UUID(0,6)
    private fun credentials()=ReservationCredentials(actor,"synthetic-bearer",time.plusSeconds(60))
    private fun response()=buildJsonObject {
        put("issuer",actor.issuer);put("actorId",actor.userId.toString());put("operationId",scope.operationId.toString())
        put("farmId",request.farmId.toString());put("payloadHash",request.payloadHash())
        put("remoteWorkspaceId",workspace.toString());put("receiptId",UUID(0,7).toString())
    }
    private fun adapter(wire:ReservationRpcWire,credentialProvider:()->ReservationCredentials?={ credentials() })=
        ReservationRpcTransport(actor.issuer,workspace,credentialProvider,wire,{time})

    @Test fun canonicalPayloadContainsNoActorOrBearerAndAcceptedReceiptIsValidated() {
        var calls=0
        val transport=adapter({ body,bearer ->
            calls++;assertEquals("synthetic-bearer",bearer)
            val json=Json.parseToJsonElement(body).jsonObject
            assertEquals(setOf("p_request","p_remote_workspace"),json.keys)
            val payload=json.getValue("p_request").jsonObject
            assertEquals(request.operationId.toString(),payload.getValue("operationId").jsonPrimitive.content)
            assertEquals(2,payload.getValue("consentTextVersion").jsonPrimitive.int)
            assertFalse(body.contains("synthetic-bearer"));assertFalse(body.contains(actor.issuer));assertFalse(payload.containsKey("actorId"))
            ReservationWireResponse(200,response().toString())
        })
        val receipt=(transport.reserve(scope,request) as ReservationResponse.Accepted).receipt
        assertEquals(scope,receipt.scope);assertEquals(request.payloadHash(),receipt.payloadHash);assertEquals(1,calls)
    }
    @Test fun wrongAccountWrongIssuerExpiredOrMissingCredentialsNeverSend() {
        val invalid=listOf(null,credentials().let { ReservationCredentials(actor.copy(userId=UUID(0,9)),it.accessToken,it.expiresAt) },
            ReservationCredentials(actor.copy(issuer="https://other.example"),"token",time.plusSeconds(60)),
            ReservationCredentials(actor,"token",time),ReservationCredentials(actor,"bad\r\nheader",time.plusSeconds(60)))
        invalid.forEach { value ->
            val transport=adapter({ _,_ -> fail("Must not send credentials for another identity");error("unreachable") },{value})
            assertEquals(ReservationResponse.RetrySameOperation,transport.reserve(scope,request))
        }
    }
    @Test fun uncertainResponsesRetryTheSameSerializedRequestAndDuplicateReceiptsRemainEqual() {
        val requests=mutableListOf<String>();var status=503
        val transport=adapter({ body,_ -> requests.add(body);ReservationWireResponse(status,response().toString()) })
        assertEquals(ReservationResponse.RetrySameOperation,transport.reserve(scope,request))
        status=200
        val first=transport.reserve(scope,request)
        assertEquals(first,transport.reserve(scope,request));assertEquals(1,requests.distinct().size)
    }
    @Test fun conflictAndPermissionDenialDoNotBecomeReservations() {
        listOf(403,409,400,422).forEach { status ->
            assertEquals(ReservationResponse.Conflict,adapter({ _,_ -> ReservationWireResponse(status,"private metadata must not escape") }).reserve(scope,request))
        }
    }
    @Test fun malformedOrCrossAccountReceiptFailsClosedWithoutExposingPayload() {
        val wrong=JsonObject(response()+mapOf("actorId" to JsonPrimitive(UUID(0,99).toString())))
        val otherWorkspace=JsonObject(response()+mapOf("remoteWorkspaceId" to JsonPrimitive(UUID(0,98).toString())))
        listOf("not json",wrong.toString(),otherWorkspace.toString(),"x".repeat(65_537)).forEach { body ->
            val error=assertThrows(IOException::class.java) { adapter({ _,_ -> ReservationWireResponse(200,body) }).reserve(scope,request) }
            assertFalse(error.message.orEmpty().contains(body))
        }
    }
    @Test fun lostConnectionRetainsUnknownOutcomeAndCredentialLoggingIsRedacted() {
        assertThrows(IOException::class.java) { adapter({ _,_ -> throw IOException("connection lost") }).reserve(scope,request) }
        assertFalse(credentials().toString().contains("synthetic-bearer"))
        assertFalse(ReservationWireResponse(200,"secret response").toString().contains("secret response"))
    }
    @Test fun committedRemoteResponseLostThenRecoveredAfterJournalReopen() {
        val directory=Files.createTempDirectory("g1c-wire-recovery").toFile()
        try {
            val file=File(directory,"journal")
            val payloads=mutableListOf<String>()
            val transport=adapter({ body,_ ->
                payloads.add(body)
                if(payloads.size==1) throw IOException("response lost after server commit")
                ReservationWireResponse(200,response().toString())
            })
            DurableReservationJournal(file).prepare(scope,request)
            assertThrows(IOException::class.java) { DurableReservationJournal(file).attempt(scope,{actor},transport) }
            assertEquals(ReservationStatus.PENDING,DurableReservationJournal(file).load(scope)?.status)
            val restored=DurableReservationJournal(file).attempt(scope,{actor},transport)
            assertEquals(ReservationStatus.RESERVED_REMOTE,restored.status)
            assertEquals(restored,DurableReservationJournal(file).attempt(scope,{actor},transport))
            assertEquals(2,payloads.size);assertEquals(payloads[0],payloads[1])
            assertFalse(file.readBytes().toString(Charsets.ISO_8859_1).contains("synthetic-bearer"))
        } finally { directory.listFiles()?.forEach { it.delete() };directory.delete() }
    }
}
