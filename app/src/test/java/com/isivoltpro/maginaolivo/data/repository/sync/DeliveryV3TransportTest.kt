package com.isivoltpro.maginaolivo.data.repository.sync

import com.isivoltpro.maginaolivo.data.repository.HistoricalDeliveryRoute
import com.isivoltpro.maginaolivo.data.repository.LegacyDeliveryRoute
import com.isivoltpro.maginaolivo.data.repository.ServerWorkspaceOwnerVerifier
import com.isivoltpro.maginaolivo.data.repository.WorkspaceAuthorizationRemote
import com.isivoltpro.maginaolivo.data.repository.deliveryOriginCreatedAt
import com.isivoltpro.maginaolivo.data.repository.deliveryPayloadHash
import com.isivoltpro.maginaolivo.data.repository.deliveryV3Origin
import com.isivoltpro.maginaolivo.feature.account.AuthSession
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** #808 Android transport: the v3 byte contract, the RPC route, typed review causes and the owner check. */
class DeliveryV3TransportTest {
    private val userId = UUID.fromString("cccccccc-0000-0000-0000-000000000001")
    private val session = AuthSession(userId, "owner@example.test", "access-token", "refresh-token",
        Instant.parse("2099-01-01T00:00:00Z"))
    private val operationId = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000003")
    private val deliveryId = UUID.fromString("11111111-1111-1111-1111-111111111111")

    private val v3 = """{"id":"11111111-1111-1111-1111-111111111111","farmId":"22222222-2222-2222-2222-222222222222",
        |"campaignId":"33333333-3333-3333-3333-333333333333","deliveryDate":"2026-10-14","destinationName":"Coop",
        |"destinationOrganizationId":null,"netGrams":1000,"grossGrams":null,"tareGrams":null,"deliveryNumber":null,
        |"ticketNumber":null,"source":"MANUAL","notes":null,"harvestId":null,"deliveryTime":"09:30","harvestOrigin":"TREE",
        |"baseVersion":null,"parcels":[{"id":"44444444-4444-4444-4444-444444444444",
        |"parcelId":"55555555-5555-5555-5555-555555555555","campaignParcelId":null,"parcelNameAtDelivery":"Norte",
        |"weightGrams":1000,"allocationMode":"EXACT"}],"originCreatedAt":"2026-10-14T18:00:00.123Z"}""".trimMargin().replace("\n", "")

    private fun sha256(text: String) = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    @Test
    fun v3HashIsThePostgresJsonbTextOfTheClosedObjectWithExplicitNulls() {
        // PostgreSQL renders jsonb keys by length, then bytes; nulls stay as null. The server hashes this.
        val expected = """{"id": "11111111-1111-1111-1111-111111111111", "notes": null, """ +
            """"farmId": "22222222-2222-2222-2222-222222222222", "source": "MANUAL", "parcels": [{"id": """ +
            """"44444444-4444-4444-4444-444444444444", "parcelId": "55555555-5555-5555-5555-555555555555", """ +
            """"weightGrams": 1000, "allocationMode": "EXACT", "campaignParcelId": null, "parcelNameAtDelivery": "Norte"}], """ +
            """"netGrams": 1000, "harvestId": null, "tareGrams": null, "campaignId": "33333333-3333-3333-3333-333333333333", """ +
            """"grossGrams": null, "baseVersion": null, "deliveryDate": "2026-10-14", "deliveryTime": "09:30", """ +
            """"ticketNumber": null, "harvestOrigin": "TREE", "deliveryNumber": null, "destinationName": "Coop", """ +
            """"originCreatedAt": "2026-10-14T18:00:00.123Z", "destinationOrganizationId": null}"""
        assertEquals(sha256(expected), deliveryPayloadHash(v3, 3))
    }

    @Test
    fun v3HashRefusesAnythingButTheClosed19KeyObject() {
        val missing = v3.replace(""","notes":null""", "")
        val extra = v3.replace(""""source":"MANUAL"""", """"source":"MANUAL","extra":"x"""")
        val noOrigin = v3.replace(""","originCreatedAt":"2026-10-14T18:00:00.123Z"""", "")
        val numericOrigin = v3.replace(""""2026-10-14T18:00:00.123Z"""", "1")
        val nullId = v3.replace(""""id":"11111111-1111-1111-1111-111111111111"""", """"id":null""")
        listOf(missing, extra, noOrigin, numericOrigin, nullId).forEach { assertNull(it, deliveryPayloadHash(it, 3)) }
        // A v3 object is never a valid v2 snapshot, and the version-2 contract is unchanged.
        assertNull(deliveryPayloadHash(v3, 2))
        assertNull(deliveryPayloadHash(v3, 4))
    }

    @Test
    fun originCreatedAtIsTheLocalCreationAtRoomPrecisionAndIsReadBackIdentically() {
        val created = Instant.parse("2026-10-14T18:00:00.123456789Z")
        assertEquals("2026-10-14T18:00:00.123Z", deliveryOriginCreatedAt(created))
        assertEquals("2026-10-14T18:00:00Z", deliveryOriginCreatedAt(Instant.parse("2026-10-14T18:00:00Z")))
        assertEquals("2026-10-14T18:00:00.123Z", deliveryV3Origin(v3))
        assertNull(deliveryV3Origin("""{"originCreatedAt":"ayer"}"""))
        assertNull(deliveryV3Origin("""{"originCreatedAt":12}"""))
        assertNull(deliveryV3Origin("""{}"""))
    }

    @Test
    fun theRouteIsChosenPerOperationAndProductionStaysOnV2() = runBlocking {
        val ws = UUID.randomUUID()
        assertEquals(2, LegacyDeliveryRoute.createVersion(ws, "account"))
        assertEquals(2, HistoricalDeliveryRoute { _, _ -> true }.createVersion(ws, null))
        assertEquals(2, HistoricalDeliveryRoute { _, _ -> false }.createVersion(ws, "account"))
        assertEquals(3, HistoricalDeliveryRoute { w, a -> w == ws && a == "account" }.createVersion(ws, "account"))
    }

    private class Recorder(private val answer: (String) -> DeliveryRpcResponse) : DeliveryRpcTransport {
        val names = mutableListOf<String>()
        override suspend fun rpc(name: String, bodyJson: String, accessToken: String): DeliveryRpcResponse {
            names += name
            return answer(name)
        }
    }

    private fun written(version: Int) = DeliveryRpcResponse(200,
        """{"success":true,"operation_id":"$operationId","entity_id":"$deliveryId","status":"APPLIED","remote_version":1,"payload_version":$version,"idempotent":false}""")

    @Test
    fun theRpcFollowsTheFrozenVersionAndAV3AnswerMustSayV3() = runBlocking {
        val v3Transport = Recorder { written(3) }
        assertEquals(3, DeliveryRpcAdapter(v3Transport).syncOwnDelivery(session, operationId, v3, 3).payloadVersion)
        assertEquals(listOf("sync_own_delivery_v3"), v3Transport.names)
        val v2Transport = Recorder { written(2) }
        DeliveryRpcAdapter(v2Transport).syncOwnDelivery(session, operationId, "{}")
        assertEquals(listOf("sync_own_delivery_v2"), v2Transport.names)
        // A v3 request answered as anything but v3 is not this contract.
        try {
            DeliveryRpcAdapter(Recorder { written(2) }).syncOwnDelivery(session, operationId, v3, 3)
            fail("Una respuesta v2 a una operacion v3 no se acepta")
        } catch (failure: DeliveryRpcFailure) {
            assertEquals(DeliveryRpcError.PROTOCOL, failure.error)
        }
        for (version in listOf(1, 4)) {
            val none = Recorder { fail("Sin RPC para la version $version"); written(version) }
            try {
                DeliveryRpcAdapter(none).syncOwnDelivery(session, operationId, v3, version)
                fail("Version $version sin RPC")
            } catch (failure: DeliveryRpcFailure) {
                assertEquals(DeliveryRpcError.PROTOCOL, failure.error)
            }
            assertTrue(none.names.isEmpty())
        }
    }

    private suspend fun failureFor(status: Int, code: String, message: String): DeliveryRpcFailure {
        val body = """{"code":"$code","details":null,"hint":null,"message":"$message"}"""
        return try {
            DeliveryRpcAdapter(Recorder { DeliveryRpcResponse(status, body) }).syncOwnDelivery(session, operationId, v3, 3)
            error("unreachable")
        } catch (failure: DeliveryRpcFailure) {
            failure
        }
    }

    @Test
    fun reviewCausesAreTypedForBothSqlStatesAndNeverConfusedWithOtherClasses() = runBlocking {
        for (cause in listOf("FUTURE_DATE", "CAMPAIGN_PERIOD", "DELIVERY_TIME_AMBIGUOUS", "DELIVERY_TIME_NONEXISTENT",
            "ORIGIN_IN_FUTURE", "ORIGIN_INCOHERENT")) {
            val failure = failureFor(400, "22023", "sync review: $cause")
            assertEquals(cause, failure.reviewCause)
            assertEquals(DeliveryRpcError.INVALID_PAYLOAD, failure.error)
        }
        for (cause in listOf("AFTER_ARCHIVE", "NOT_MEMBER_ON_DATE", "ORIGIN_NOT_BEFORE_CLOSURE", "ORIGIN_CLOCK_UNCERTAIN",
            "PARCEL_INCOHERENT")) {
            val failure = failureFor(403, "42501", "sync review: $cause")
            assertEquals(cause, failure.reviewCause)
            assertEquals(DeliveryRpcError.FORBIDDEN, failure.error)
        }
        // Authority and malformed payloads are NOT review: they keep their class and a typed reason.
        failureFor(403, "42501", "sync denied: SCOPE").let { assertNull(it.reviewCause); assertEquals("SCOPE", it.reason) }
        failureFor(400, "22023", "sync invalid: SHAPE").let { assertNull(it.reviewCause); assertEquals("SHAPE", it.reason) }
        failureFor(409, "23505", "sync conflict: RECEIPT_CONFLICT").let {
            assertNull(it.reviewCause); assertEquals(DeliveryRpcError.CONFLICT, it.error)
        }
        // A review word under another SQLSTATE, a legacy message or a malformed token is never review.
        assertNull(failureFor(409, "23505", "sync review: AFTER_ARCHIVE").reviewCause)
        failureFor(403, "42501", "sync denied: parcel does not belong to farm").let { assertNull(it.reviewCause); assertNull(it.reason) }
        assertNull(failureFor(403, "42501", "sync review: after_archive").reviewCause)
        assertNull(failureFor(403, "42501", "xsync review: AFTER_ARCHIVE").reviewCause)
    }

    private fun headAdapter(body: String) = DeliveryRpcAdapter(Recorder { DeliveryRpcResponse(200, body) })
    private val hash = "a".repeat(64)
    private fun headBody(id: UUID = deliveryId, version: String = "2", deleted: String = "false", op: String = "\"$operationId\"",
        pv: String = "3", h: String = "\"$hash\"", contract: String = "1") =
        """{"contract_version":$contract,"delivery_id":"$id","version":$version,"snapshot_format":2,"deleted":$deleted,
           "head_operation_id":$op,"head_payload_version":$pv,"head_payload_hash":$h}"""

    @Test
    fun theV3HeadIsParsedStrictlyAndAPartialOrForeignHeadIsNeverAHead() = runBlocking {
        val full = headAdapter(headBody()).getOwnDeliveryHeadV3(session, deliveryId)
        assertEquals(operationId, full.headOperationId); assertEquals(3, full.headPayloadVersion)
        assertEquals(hash, full.headPayloadHash); assertEquals(2L, full.version); assertFalse(full.deleted)
        val none = headAdapter(headBody(op = "null", pv = "null", h = "null")).getOwnDeliveryHeadV3(session, deliveryId)
        assertNull(none.headOperationId); assertNull(none.headPayloadHash)
        assertTrue(headAdapter(headBody(deleted = "true", op = "null", pv = "null", h = "null")).getOwnDeliveryHeadV3(session, deliveryId).deleted)
        val bad = listOf(
            headBody(id = UUID.randomUUID()), headBody(contract = "2"), headBody(deleted = "\"false\""), headBody(version = "0"),
            headBody(op = "null"), headBody(h = "null"), headBody(pv = "null"), headBody(op = "\"no-uuid\""),
            headBody(h = "\"ABC\""), """{"contract_version":1}""",
        )
        for (body in bad) {
            try {
                headAdapter(body).getOwnDeliveryHeadV3(session, deliveryId)
                fail("Cabecera fuera de contrato aceptada: $body")
            } catch (failure: DeliveryRpcFailure) {
                assertEquals(body, DeliveryRpcError.PROTOCOL, failure.error)
            }
        }
    }

    private val issuer = "https://issuer.test"
    private val now = Instant.parse("2026-11-02T10:00:00Z")
    private fun contract(subject: UUID = userId, workspace: UUID, role: String, permissions: String = """"read","write","manage_members"""",
        verifiedAt: Instant = now, issuerUrl: String = "$issuer/auth/v1") =
        """{"contract_version":1,"issuer":"$issuerUrl","subject":"$subject","verified_at":"$verifiedAt",
           "token_expires_at":"${now.plusSeconds(3600)}","workspaces":[{"id":"$workspace","name":"Olivar","role":"$role",
           "permissions":[$permissions],"grant_version":1,"authorization_expires_at":"${now.plusSeconds(900)}"}]}"""

    @Test
    fun onlyAFreshServerOwnerContractMakesTheSessionAnOwner() = runBlocking {
        val ws = UUID.randomUUID()
        fun verifier(body: String) = ServerWorkspaceOwnerVerifier(WorkspaceAuthorizationRemote { body }, issuer) { now }
        assertTrue(verifier(contract(workspace = ws, role = "OWNER")).isCurrentOwner(session, ws))
        // A member with every permission, another workspace, another subject, a stale contract, another issuer.
        assertFalse(verifier(contract(workspace = ws, role = "MANAGER")).isCurrentOwner(session, ws))
        assertFalse(verifier(contract(workspace = ws, role = "EDITOR", permissions = """"read","write"""")).isCurrentOwner(session, ws))
        assertFalse(verifier(contract(workspace = UUID.randomUUID(), role = "OWNER")).isCurrentOwner(session, ws))
        assertFalse(verifier(contract(subject = UUID.randomUUID(), workspace = ws, role = "OWNER")).isCurrentOwner(session, ws))
        assertFalse(verifier(contract(workspace = ws, role = "OWNER", verifiedAt = now.minusSeconds(3600))).isCurrentOwner(session, ws))
        assertFalse(verifier(contract(workspace = ws, role = "OWNER", issuerUrl = "https://evil.test/auth/v1")).isCurrentOwner(session, ws))
        // A malformed answer is not an owner; it never throws past the verifier.
        assertFalse(verifier("""{"contract_version":2}""").isCurrentOwner(session, ws))
        assertFalse(verifier("no json").isCurrentOwner(session, ws))
    }
}
