package com.isivoltpro.maginaolivo.data.repository.sync

import com.isivoltpro.maginaolivo.data.local.model.FarmStatus
import com.isivoltpro.maginaolivo.data.local.model.RecordStatus
import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** G2.6 2c-2: the v2 parents graph is validated whole before Room (#810 §2/§3, #812). */
class ParentGraphV2ParserTest {
    private val workspace = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val owner = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
    private val live = UUID.fromString("22222222-2222-2222-2222-222222222221")
    private val old = UUID.fromString("22222222-2222-2222-2222-222222222222")
    private val active = UUID.fromString("33333333-3333-3333-3333-333333333331")
    private val archived = UUID.fromString("33333333-3333-3333-3333-333333333332")
    private val m1 = UUID.fromString("44444444-4444-4444-4444-444444444441")
    private val m2 = UUID.fromString("44444444-4444-4444-4444-444444444442")
    private val m3 = UUID.fromString("44444444-4444-4444-4444-444444444443")

    private fun farm(id: UUID, status: String = "ACTIVE", archivedAt: String? = null, ws: UUID = workspace,
        extra: String = "") =
        """{"id":"$id","workspaceId":"$ws","name":"F","description":null,"municipality":null,"province":null,"notes":null,"status":"$status","archivedAt":${archivedAt?.let { "\"$it\"" } ?: "null"},"createdAt":"2026-10-01T08:00:00+00:00","updatedAt":"2026-10-02T08:00:00+00:00","version":2$extra}"""
    private fun parcel(id: UUID, status: String = "ACTIVE", archivedAt: String? = null, geometry: String? = null) =
        """{"id":"$id","workspaceId":"$workspace","displayName":"P","cadastralReference":null,"cadastralPolygon":null,"cadastralParcel":null,"municipality":null,"province":null,"source":"MANUAL","geometryGeoJson":${geometry?.let { "\"$it\"" } ?: "null"},"sourceProvider":null,"sourceImportedAt":null,"cadastralAreaM2":null,"managedAreaM2":1234.5,"notes":null,"status":"$status","oliveTreeCount":120,"variety":"Picual","irrigationSystem":null,"irrigationNetwork":null,"irrigationSector":null,"irrigationDays":null,"archivedAt":${archivedAt?.let { "\"$it\"" } ?: "null"},"createdAt":"2026-10-01T08:00:00+00:00","updatedAt":"2026-10-02T08:00:00+00:00","version":3}"""
    private fun membership(id: UUID, farm: UUID, parcel: UUID, from: String, until: String?, ws: UUID = workspace) =
        """{"id":"$id","workspaceId":"$ws","farmId":"$farm","parcelId":"$parcel","validFrom":"$from","validUntil":${until?.let { "\"$it\"" } ?: "null"},"createdAt":"2026-10-01T08:00:00+00:00","updatedAt":"2026-10-02T08:00:00+00:00","version":1}"""
    private fun body(
        version: Int = 2,
        farms: List<String> = listOf(farm(live), farm(old, "ARCHIVED", "2026-05-01T12:00:00.123456+00:00")),
        parcels: List<String> = listOf(parcel(active), parcel(archived, "ARCHIVED", "2026-03-01T10:00:00.123456789Z")),
        memberships: List<String> = listOf(
            membership(m1, live, active, "2026-01-01T00:00:00.007Z", null),
            membership(m2, old, archived, "2026-01-01T00:00:00Z", "2026-03-01T10:00:00.123456789Z"),
            // Archive is not a cascade: a period may stay open in an archived farm.
            membership(m3, old, active, "2025-01-01T00:00:00Z", "2025-12-31T00:00:00Z"),
        ),
        rootExtra: String = "",
    ) = """{"contract_version":$version,"issuer":"https://project.example/auth/v1","subject":"$owner","workspace":{"id":"$workspace","name":"Olivar","ownerUserId":"$owner","countryCode":"ES","timezone":"Europe/Madrid","locale":"es-ES","currency":"EUR","createdAt":"2026-10-01T08:00:00+00:00","updatedAt":"2026-10-01T08:00:00+00:00","version":1},"farms":[${farms.joinToString(",")}],"parcels":[${parcels.joinToString(",")}],"memberships":[${memberships.joinToString(",")}]$rootExtra}"""

    @Test fun archivedAtBecomesDeletedAtForFarmsAndParcelsWithMillisecondPrecision() {
        val graph = parseParentGraphV2(body(), workspace)
        val oldFarm = graph.farms.single { it.id == old }
        assertEquals(FarmStatus.ARCHIVED, oldFarm.status)
        assertEquals(Instant.parse("2026-05-01T12:00:00.123Z"), oldFarm.metadata.deletedAt)
        assertNull(graph.farms.single { it.id == live }.metadata.deletedAt)
        val parcel = graph.parcels.single { it.id == archived }
        assertEquals(RecordStatus.ARCHIVED, parcel.status)
        assertEquals(Instant.parse("2026-03-01T10:00:00.123Z"), parcel.metadata.deletedAt)
        assertEquals(3L, parcel.metadata.remoteVersion)
        assertNull(graph.parcels.single { it.id == active }.metadata.deletedAt)
        assertEquals(Instant.parse("2026-03-01T10:00:00.123Z"), graph.memberships.single { it.id == m2 }.validUntil)
        assertEquals(3, graph.memberships.size)
    }

    @Test fun anOpenPeriodInAnArchivedFarmIsValid() {
        val graph = parseParentGraphV2(body(memberships = listOf(
            membership(m1, old, active, "2026-01-01T00:00:00Z", null),
            membership(m2, old, archived, "2026-01-01T00:00:00Z", "2026-03-01T10:00:00Z"))), workspace)
        assertEquals(old, graph.memberships.single { it.validUntil == null }.farmId)
    }

    @Test fun rejectsEveryIncoherentOrIncompleteGraphBeforeRoom() {
        val foreign = UUID.randomUUID()
        val open = membership(m1, live, active, "2026-01-01T00:00:00Z", null)
        val closed = membership(m2, old, archived, "2026-01-01T00:00:00Z", "2026-03-01T10:00:00Z")
        listOf(
            "v1 shape" to body(version = 1),
            "extra root key" to body(rootExtra = ",\"extra\":1"),
            "extra farm key" to body(farms = listOf(farm(live, extra = ",\"coverDocumentId\":null"), farm(old, "ARCHIVED", "2026-05-01T12:00:00Z"))),
            "farm ARCHIVED without archivedAt" to body(farms = listOf(farm(live), farm(old, "ARCHIVED", null))),
            "farm ACTIVE with archivedAt" to body(farms = listOf(farm(live, "ACTIVE", "2026-05-01T12:00:00Z"), farm(old, "ARCHIVED", "2026-05-01T12:00:00Z"))),
            "farm of another workspace" to body(farms = listOf(farm(live), farm(old, "ARCHIVED", "2026-05-01T12:00:00Z", ws = foreign))),
            "parcel ARCHIVED without archivedAt" to body(parcels = listOf(parcel(active), parcel(archived, "ARCHIVED", null))),
            "parcel ACTIVE with archivedAt" to body(parcels = listOf(parcel(active, "ACTIVE", "2026-03-01T10:00:00Z"), parcel(archived, "ARCHIVED", "2026-03-01T10:00:00Z"))),
            "unsupported status" to body(parcels = listOf(parcel(active, "DELETED"), parcel(archived, "ARCHIVED", "2026-03-01T10:00:00Z"))),
            "ACTIVE parcel without open period" to body(memberships = listOf(closed)),
            "ARCHIVED parcel with an open period" to body(memberships = listOf(open,
                membership(m2, old, archived, "2026-01-01T00:00:00Z", null))),
            "ACTIVE parcel with two open periods" to body(memberships = listOf(open,
                membership(m3, old, active, "2026-02-01T00:00:00Z", null), closed)),
            "period over a farm outside the graph" to body(memberships = listOf(open,
                membership(m2, foreign, archived, "2026-01-01T00:00:00Z", "2026-03-01T10:00:00Z"))),
            "period of another workspace" to body(memberships = listOf(open,
                membership(m2, old, archived, "2026-01-01T00:00:00Z", "2026-03-01T10:00:00Z", ws = foreign))),
            "period ending before it starts" to body(memberships = listOf(open,
                membership(m2, old, archived, "2026-03-01T10:00:00Z", "2026-01-01T00:00:00Z"))),
            "duplicate membership id" to body(memberships = listOf(open, closed, closed)),
        ).forEach { (label, raw) ->
            assertThrows(label, Exception::class.java) { parseParentGraphV2(raw, workspace) }
        }
        assertThrows(Exception::class.java) { parseParentGraphV2(body(), UUID.randomUUID()) }
    }

    @Test fun rowLimitsAndTheEightMebibyteBoundAreTooLargeNotClipped() {
        val farms = (1..101).map { farm(UUID.randomUUID()) }
        assertThrows(ParentGraphTooLarge::class.java) { parseParentGraphV2(body(farms = farms), workspace) }
        val parcels = (1..1001).map { parcel(UUID.randomUUID()) }
        assertThrows(ParentGraphTooLarge::class.java) { parseParentGraphV2(body(parcels = parcels), workspace) }
        val periods = (1..5001).map { membership(UUID.randomUUID(), live, active, "2026-01-01T00:00:00Z", null) }
        assertThrows(ParentGraphTooLarge::class.java) { parseParentGraphV2(body(memberships = periods), workspace) }
        // ASCII over the bound, and multibyte text under 8 Mi chars but over 8 MiB of UTF-8.
        val ascii = body(parcels = listOf(parcel(active, geometry = "x".repeat(PARENT_GRAPH_V2_MAX_BYTES)),
            parcel(archived, "ARCHIVED", "2026-03-01T10:00:00Z")))
        assertThrows(ParentGraphTooLarge::class.java) { parseParentGraphV2(ascii, workspace) }
        val multibyte = body(parcels = listOf(parcel(active, geometry = "ñ".repeat(PARENT_GRAPH_V2_MAX_BYTES / 2 + 10)),
            parcel(archived, "ARCHIVED", "2026-03-01T10:00:00Z")))
        assertTrue(multibyte.length < PARENT_GRAPH_V2_MAX_BYTES)
        assertThrows(ParentGraphTooLarge::class.java) { parseParentGraphV2(multibyte, workspace) }
    }

    @Test fun utf8LengthCountsWithoutCopying() {
        assertEquals(0, utf8Length(""))
        assertEquals(3, utf8Length("abc"))
        assertEquals(2, utf8Length("ñ"))
        assertEquals(3, utf8Length("€"))
        assertEquals(4, utf8Length("🌿"))
        val sample = "Olivar ñ € 🌿"
        assertEquals(sample.toByteArray(Charsets.UTF_8).size, utf8Length(sample))
    }
}
