package com.isivoltpro.maginaolivo.data.repository.sync

import com.isivoltpro.maginaolivo.data.local.entity.FarmParcelMembershipEntity
import com.isivoltpro.maginaolivo.data.local.entity.LocalMetadata
import com.isivoltpro.maginaolivo.data.local.entity.ParcelEntity
import com.isivoltpro.maginaolivo.data.local.entity.SyncOutboxEntity
import com.isivoltpro.maginaolivo.data.local.model.OutboxOperation
import com.isivoltpro.maginaolivo.data.local.model.OutboxStatus
import com.isivoltpro.maginaolivo.data.local.model.RecordStatus
import com.isivoltpro.maginaolivo.data.local.model.SyncEntityType
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParcelMutationEnvelopeTest {
    private val workspace = UUID.fromString("0d6b8f4e-3c2a-4b1e-9f7a-5c4d3e2f1a0b")
    private val farm = UUID.fromString("1d6b8f4e-3c2a-4b1e-9f7a-5c4d3e2f1a0b")
    private val parcelId = UUID.fromString("4d6b8f4e-3c2a-4b1e-9f7a-5c4d3e2f1a0b")
    private val membershipId = UUID.fromString("5d6b8f4e-3c2a-4b1e-9f7a-5c4d3e2f1a0b")
    private val reopenedId = UUID.fromString("6d6b8f4e-3c2a-4b1e-9f7a-5c4d3e2f1a0b")
    private val meta = LocalMetadata(Instant.EPOCH, Instant.EPOCH, version = 2)
    private val parcel = ParcelEntity(parcelId, workspace, "Parcela \"Olivo\" Jódar", "23050A00100001", "1", "1",
        "Jódar", "Jaén", "CATASTRO", "{\"type\":\"Polygon\",\"coordinates\":[]}", "catastro",
        Instant.parse("2026-01-02T03:04:05.006Z"), 12345.5, 1.0E7, null, RecordStatus.ACTIVE, meta, 120, "Picual",
        "DRIP", null, null, "L,X,V")
    private val start = Instant.parse("2026-01-01T00:00:00.007Z")
    private val archivedAt = Instant.parse("2026-03-01T10:00:00.123Z")
    private val restoredAt = Instant.parse("2026-04-01T09:30:00Z")
    private val membership = FarmParcelMembershipEntity(membershipId, workspace, farm, parcelId, start, null, meta)
    private val closed = membership.copy(validUntil = archivedAt)
    private val reopened = FarmParcelMembershipEntity(reopenedId, workspace, farm, parcelId, restoredAt, null, meta)

    /**
     * One line per kind, shared with the g26-parcel-mutation SQL contract test, which asserts each line is
     * already PostgreSQL's canonical `jsonb::text` of the envelope the server hashes.
     */
    private val canonical = javaClass.classLoader!!.getResource("g26-parcel-mutation-canonical.txt")!!.readText()
        .lines().filter { it.isNotBlank() }

    private fun sha(text: String) = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    private fun envelope(kind: ParcelMutationKind, expected: Long, mutation: JsonObject, farmId: UUID = farm) = buildJsonObject {
        put("issuer", "https://project.example"); put("kind", kind.name); put("localVersion", 5)
        put("parcelId", parcelId.toString()); put("workspaceId", workspace.toString()); put("farmId", farmId.toString())
        put("expectedVersion", expected); put("mutation", mutation)
    }.toString()

    private fun marker(operation: OutboxOperation, at: Instant, code: String? = null) = SyncOutboxEntity(
        id = UUID.randomUUID(), entityType = SyncEntityType.PARCEL, entityId = parcelId, operation = operation,
        payloadVersion = 1, createdAt = at, updatedAt = at, lastErrorCode = code)

    @Test fun hashesMatchPostgresCanonicalJsonbForEveryKind() {
        val update = parcelFieldsPayload(parcel, listOf(membership))
        assertEquals(19, update.size)
        // Large areas stay plain decimals and the payload never carries status or memberships.
        assertEquals("10000000", update.getValue("managedAreaM2").toString())
        assertTrue(listOf("id", "workspaceId", "status", "memberships").none { it in update })
        assertEquals(sha(canonical[0]), parcelMutationHash(ParcelMutationKind.UPDATE, parcelId, 3, update))
        assertEquals(sha(canonical[1]), parcelMutationHash(ParcelMutationKind.ARCHIVE, parcelId, 3, archivePayload(closed)))
        assertEquals(sha(canonical[2]), parcelMutationHash(ParcelMutationKind.RESTORE, parcelId, 4, restorePayload(reopened)))
    }

    @Test fun envelopesRoundTripWithTheMembershipTheServerMustReport() {
        val update = parseParcelMutationEnvelope(envelope(ParcelMutationKind.UPDATE, 3, parcelFieldsPayload(parcel, listOf(membership))))!!
        assertEquals(sha(canonical[0]), update.hash)
        assertNull(update.membershipId)
        val archive = parseParcelMutationEnvelope(envelope(ParcelMutationKind.ARCHIVE, 3, archivePayload(closed)))!!
        assertEquals(membershipId, archive.membershipId)
        val restore = parseParcelMutationEnvelope(envelope(ParcelMutationKind.RESTORE, 4, restorePayload(reopened)))!!
        assertEquals(reopenedId, restore.membershipId)
        assertEquals(sha(canonical[2]), restore.hash)
        assertEquals(5L, restore.localVersion)
    }

    @Test fun tamperedOrIncompleteEnvelopesAreRejected() {
        val good = envelope(ParcelMutationKind.UPDATE, 3, parcelFieldsPayload(parcel, listOf(membership)))
        val restore = envelope(ParcelMutationKind.RESTORE, 4, restorePayload(reopened))
        listOf(
            good.replace("\"expectedVersion\":3", "\"expectedVersion\":0"),
            good.replace("\"expectedVersion\":3", "\"expectedVersion\":\"3\""),
            good.replace("\"kind\":\"UPDATE\"", "\"kind\":\"DELETE\""),
            good.replace("\"managedAreaM2\":10000000", "\"managedAreaM2\":1.0E7"),
            good.replace("\"notes\":null", "\"notes\":null,\"status\":\"ARCHIVED\""),
            good.replace("\"localVersion\":5", "\"localVersion\":5,\"x\":1"),
            envelope(ParcelMutationKind.ARCHIVE, 3, buildJsonObject { put("archivedAt", "2026-03-01 10:00:00"); put("closeMembershipId", membershipId.toString()) }),
            envelope(ParcelMutationKind.ARCHIVE, 3, buildJsonObject { put("closeMembershipId", membershipId.toString()) }),
            restore.replace("\"restoredAt\":\"2026-04-01T09:30:00Z\"", "\"restoredAt\":\"2026-04-02T00:00:00Z\""),
            envelope(ParcelMutationKind.RESTORE, 4, restorePayload(reopened), farmId = UUID.randomUUID()),
            "[]",
        ).forEach { assertNull(it, parseParcelMutationEnvelope(it)) }
        assertNotNull(parseParcelMutationEnvelope(good))
        assertNotNull(parseParcelMutationEnvelope(restore))
    }

    @Test fun markersAreClassifiedOnlyFromExactHistoryAndAmbiguityIsHeldForReview() {
        val archive = marker(OutboxOperation.ARCHIVE, archivedAt)
        val legacyDelete = marker(OutboxOperation.DELETE, archivedAt)
        val restore = marker(OutboxOperation.RESTORE, restoredAt)
        val legacyRestore = marker(OutboxOperation.UPDATE, restoredAt)
        val edit = marker(OutboxOperation.UPDATE, Instant.parse("2026-02-01T00:00:00Z"))
        val history = listOf(closed, reopened)
        assertEquals(ParcelMutationPlan.Archive(closed), classifyParcelMarker(archive, history, listOf(archive)))
        assertEquals(ParcelMutationPlan.Archive(closed), classifyParcelMarker(legacyDelete, history, listOf(legacyDelete)))
        assertEquals(ParcelMutationPlan.Restore(reopened), classifyParcelMarker(restore, history, listOf(restore)))
        // Before G2.6 «Restaurar» wrote UPDATE; its exact instant proves which membership it opened.
        assertEquals(ParcelMutationPlan.Restore(reopened), classifyParcelMarker(legacyRestore, history, listOf(legacyRestore)))
        assertEquals(ParcelMutationPlan.Update, classifyParcelMarker(edit, history, listOf(edit)))

        // No evidence, two memberships at the instant, a typed marker without its change, or another
        // marker at the same instant: never guessed.
        assertTrue(classifyParcelMarker(marker(OutboxOperation.DELETE, Instant.parse("2026-02-02T00:00:00Z")), history, emptyList())
            is ParcelMutationPlan.Review)
        val twin = closed.copy(id = UUID.randomUUID(), farmId = UUID.randomUUID())
        assertTrue(classifyParcelMarker(legacyDelete, history + twin, listOf(legacyDelete)) is ParcelMutationPlan.Review)
        assertTrue(classifyParcelMarker(marker(OutboxOperation.RESTORE, archivedAt), history, emptyList()) is ParcelMutationPlan.Review)
        val sibling = marker(OutboxOperation.UPDATE, restoredAt)
        assertTrue(classifyParcelMarker(legacyRestore, history, listOf(legacyRestore, sibling)) is ParcelMutationPlan.Review)
        // A logically deleted membership is not evidence.
        assertTrue(classifyParcelMarker(archive, listOf(closed.copy(metadata = meta.copy(deletedAt = archivedAt))), listOf(archive))
            is ParcelMutationPlan.Review)
        // An explicit decision is honoured, still only on exact evidence for ARCHIVE/RESTORE.
        val resolved = legacyRestore.copy(lastErrorCode = "RESOLVED_REVIEW")
        assertEquals(ParcelMutationPlan.Update, classifyParcelMarker(resolved, history, listOf(resolved, sibling)))
        val resolvedArchive = archive.copy(lastErrorCode = "RESOLVED_REVIEW")
        assertEquals(ParcelMutationPlan.Archive(closed), classifyParcelMarker(resolvedArchive, history,
            listOf(resolvedArchive, marker(OutboxOperation.UPDATE, archivedAt))))
    }

    @Test fun dependencyCausesCarryOnlyTypesStatesAndCounts() {
        fun op(type: SyncEntityType, status: OutboxStatus) = SyncOutboxEntity(id = UUID.randomUUID(), entityType = type,
            entityId = UUID.randomUUID(), operation = OutboxOperation.UPDATE, payloadVersion = 1, status = status,
            createdAt = start, updatedAt = start)
        assertEquals("DELIVERY:BLOCKED=1,DELIVERY:FAILED=2,EXPENSE:PENDING=1", dependencySummary(listOf(
            op(SyncEntityType.EXPENSE, OutboxStatus.PENDING), op(SyncEntityType.DELIVERY, OutboxStatus.FAILED),
            op(SyncEntityType.DELIVERY, OutboxStatus.FAILED), op(SyncEntityType.DELIVERY, OutboxStatus.BLOCKED))))
    }
}
