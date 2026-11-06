package com.isivoltpro.maginaolivo.data.repository.sync

import com.isivoltpro.maginaolivo.account.reservation.ReservationRequest
import com.isivoltpro.maginaolivo.data.local.entity.FarmEntity
import com.isivoltpro.maginaolivo.data.local.entity.LocalMetadata
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FarmCreateEnvelopeTest {
    private val issuer = "https://project.example"
    private val workspace = UUID.fromString("0d6b8f4e-3c2a-4b1e-9f7a-5c4d3e2f1a0b")
    private val farmId = UUID.fromString("1d6b8f4e-3c2a-4b1e-9f7a-5c4d3e2f1a0b")
    private val reservationOp = UUID.fromString("2d6b8f4e-3c2a-4b1e-9f7a-5c4d3e2f1a0b")
    private val receiptId = UUID.fromString("3d6b8f4e-3c2a-4b1e-9f7a-5c4d3e2f1a0b")
    private val farm = FarmEntity(farmId, workspace, "Finca Jódar \"N\"", null, "Bedmar", "Jaén",
        UUID.randomUUID(), null, metadata = LocalMetadata(Instant.EPOCH, Instant.EPOCH, version = 3))
    private val reservation = ReservationRequest(reservationOp, farmId, workspace, UUID.randomUUID(), 2, "a".repeat(64), 3)

    /** Same literal the g26-farm-send SQL contract test asserts for `jsonb_build_object(...)::text` on PostgreSQL. */
    private val canonical = "{\"farm\": {\"id\": \"1d6b8f4e-3c2a-4b1e-9f7a-5c4d3e2f1a0b\", \"name\": \"Finca Jódar \\\"N\\\"\", " +
        "\"notes\": null, \"status\": \"ACTIVE\", \"province\": \"Jaén\", \"description\": null, " +
        "\"workspaceId\": \"0d6b8f4e-3c2a-4b1e-9f7a-5c4d3e2f1a0b\", \"municipality\": \"Bedmar\", \"coverDocumentId\": null}, " +
        "\"reservationReceiptId\": \"3d6b8f4e-3c2a-4b1e-9f7a-5c4d3e2f1a0b\", " +
        "\"reservationOperationId\": \"2d6b8f4e-3c2a-4b1e-9f7a-5c4d3e2f1a0b\"}"

    @Test fun envelopeRoundTripsAndHashMatchesPostgresCanonicalJsonb() {
        val snapshot = parseFarmCreateEnvelope(buildFarmCreateEnvelope(farm, issuer, workspace, reservation))!!
        assertEquals(issuer, snapshot.issuer)
        assertEquals(3L, snapshot.localVersion)
        assertEquals(farmId, snapshot.farmId)
        assertEquals(workspace, snapshot.workspaceId)
        assertEquals(reservation, snapshot.reservation)
        assertEquals(reservation.payloadHash(), snapshot.reservation.payloadHash())
        // The opaque local cover id is never sent.
        assertEquals(kotlinx.serialization.json.JsonNull, snapshot.farm.getValue("coverDocumentId"))
        val expected = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        assertEquals(expected, farmCreateHash(snapshot, receiptId))
    }

    @Test fun tamperedOrIncompleteEnvelopesAreRejected() {
        val good = buildFarmCreateEnvelope(farm, issuer, workspace, reservation)
        val other = UUID.randomUUID()
        listOf(
            good.replace("\"localVersion\":3", "\"localVersion\":0"),
            good.replace("\"issuer\":\"$issuer\"", "\"issuer\":\"\""),
            good.replace("\"remoteWorkspaceId\":\"$workspace\"", "\"remoteWorkspaceId\":\"$other\""),
            good.replace("\"coverDocumentId\":null", "\"coverDocumentId\":\"$other\""),
            good.replace("\"status\":\"ACTIVE\"", "\"status\":\"DELETED\""),
            good.replace("\"name\":\"Finca Jódar \\\"N\\\"\"", "\"name\":\"\""),
            good.replace("\"manifestSha256\":\"${"a".repeat(64)}\"", "\"manifestSha256\":\"xyz\""),
            good.replace("\"farmId\":\"$farmId\"", "\"farmId\":\"$other\""),
            good.replace("\"notes\":null", "\"notes\":null,\"ownerId\":\"$other\""),
            "[]",
        ).forEach { assertNull(it, parseFarmCreateEnvelope(it)) }
    }

    @Test fun farmValidationMirrorsTheServer() {
        assertTrue(validFarm(farm))
        assertTrue(validFarm(farm.copy(name = "ñ".repeat(200), notes = "x".repeat(10_000))))
        listOf(farm.copy(name = ""), farm.copy(name = "  "), farm.copy(name = "x".repeat(201)),
            farm.copy(municipality = "x".repeat(201)), farm.copy(description = "x".repeat(10_001)),
            farm.copy(notes = "a\u0000b")).forEach { assertFalse(it.toString(), validFarm(it)) }
    }
}
