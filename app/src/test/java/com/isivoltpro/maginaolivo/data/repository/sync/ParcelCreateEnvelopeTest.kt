package com.isivoltpro.maginaolivo.data.repository.sync

import com.isivoltpro.maginaolivo.data.local.entity.FarmParcelMembershipEntity
import com.isivoltpro.maginaolivo.data.local.entity.LocalMetadata
import com.isivoltpro.maginaolivo.data.local.entity.ParcelEntity
import com.isivoltpro.maginaolivo.data.local.model.RecordStatus
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParcelCreateEnvelopeTest {
    private val workspace = UUID.fromString("0d6b8f4e-3c2a-4b1e-9f7a-5c4d3e2f1a0b")
    private val farm = UUID.fromString("1d6b8f4e-3c2a-4b1e-9f7a-5c4d3e2f1a0b")
    private val parcelId = UUID.fromString("4d6b8f4e-3c2a-4b1e-9f7a-5c4d3e2f1a0b")
    private val membershipId = UUID.fromString("5d6b8f4e-3c2a-4b1e-9f7a-5c4d3e2f1a0b")
    private val meta = LocalMetadata(Instant.EPOCH, Instant.EPOCH, version = 2)
    private val parcel = ParcelEntity(parcelId, workspace, "Parcela \"Olivo\" Jódar", "23050A00100001", "1", "1",
        "Jódar", "Jaén", "CATASTRO", "{\"type\":\"Polygon\",\"coordinates\":[]}", "catastro",
        Instant.parse("2026-01-02T03:04:05.006Z"), 12345.5, 1.0E7, null, RecordStatus.ACTIVE, meta, 120, "Picual",
        "DRIP", null, null, "L,X,V")
    private val membership = FarmParcelMembershipEntity(membershipId, workspace, farm, parcelId,
        Instant.parse("2026-01-01T00:00:00.007Z"), null, meta)

    /** Shared with the g26-parcel-send SQL contract test, which asserts PostgreSQL's `jsonb::text` equals it. */
    private val canonical = javaClass.classLoader!!.getResource("g26-parcel-canonical.txt")!!.readText()

    private fun envelope(p: ParcelEntity = parcel, m: List<FarmParcelMembershipEntity> = listOf(membership)) =
        buildJsonObject { put("issuer", "https://project.example"); put("localVersion", 2); put("parcel", parcelPayload(p, m)) }.toString()

    @Test fun envelopeRoundTripsAndHashMatchesPostgresCanonicalJsonb() {
        val snapshot = parseParcelCreateEnvelope(envelope())!!
        assertEquals(parcelId, snapshot.parcelId)
        assertEquals(workspace, snapshot.workspaceId)
        assertEquals(setOf(farm), snapshot.farmIds)
        assertEquals(2L, snapshot.localVersion)
        // Large areas stay plain decimals, never exponent notation.
        assertEquals("10000000", snapshot.parcel.getValue("managedAreaM2").toString())
        val expected = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        assertEquals(expected, snapshot.hash)
        assertEquals(farm, currentFarm(listOf(membership)))
    }

    @Test fun tamperedOrIncompleteEnvelopesAreRejected() {
        val good = envelope()
        listOf(
            good.replace("\"localVersion\":2", "\"localVersion\":0"),
            good.replace("\"source\":\"CATASTRO\"", "\"source\":\"OTHER\""),
            good.replace("\"irrigationSystem\":\"DRIP\"", "\"irrigationSystem\":\"FLOOD\""),
            good.replace("\"managedAreaM2\":10000000", "\"managedAreaM2\":1.0E7"),
            good.replace("\"managedAreaM2\":10000000", "\"managedAreaM2\":-1"),
            good.replace("\"oliveTreeCount\":120", "\"oliveTreeCount\":\"120\""),
            good.replace("\"validFrom\":\"2026-01-01T00:00:00.007Z\"", "\"validFrom\":\"2026-01-01 00:00:00\""),
            good.replace("\"memberships\":[", "\"memberships\":[],\"x\":["),
            good.replace("\"notes\":null", "\"notes\":null,\"ownerId\":null"),
            "[]",
        ).forEach { assertNull(it, parseParcelCreateEnvelope(it)) }
        assertNotNull(parseParcelCreateEnvelope(good))
    }

    @Test fun aggregateValidationMirrorsTheServer() {
        val other = membership.copy(id = UUID.randomUUID())
        listOf(
            { parcelPayload(parcel, emptyList()) },
            { parcelPayload(parcel.copy(displayName = " "), listOf(membership)) },
            { parcelPayload(parcel.copy(cadastralReference = null), listOf(membership)) },
            { parcelPayload(parcel.copy(variety = "x".repeat(201)), listOf(membership)) },
            { parcelPayload(parcel.copy(cadastralAreaM2 = Double.NaN), listOf(membership)) },
            { parcelPayload(parcel, listOf(membership, other)) },
            { parcelPayload(parcel, listOf(membership, membership)) },
            { parcelPayload(parcel, listOf(membership.copy(validUntil = Instant.parse("2025-01-01T00:00:00Z")))) },
        ).forEach { build -> assertTrue(runCatching { build() }.isFailure) }
        // A closed earlier period plus the current one is valid history.
        val closed = membership.copy(id = UUID.randomUUID(), farmId = UUID.randomUUID(),
            validFrom = Instant.parse("2025-01-01T00:00:00Z"), validUntil = Instant.parse("2026-01-01T00:00:00.007Z"))
        assertEquals(2, parcelPayload(parcel, listOf(closed, membership))["memberships"].toString().split("\"id\"").size - 1)
        assertEquals(farm, currentFarm(listOf(closed, membership)))
    }
}
