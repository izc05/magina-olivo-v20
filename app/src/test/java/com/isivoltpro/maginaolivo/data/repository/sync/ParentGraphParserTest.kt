package com.isivoltpro.maginaolivo.data.repository.sync

import com.isivoltpro.maginaolivo.data.local.model.SyncStatus
import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ParentGraphParserTest {
    private val workspace = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val owner = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
    private val farm = UUID.fromString("22222222-2222-2222-2222-222222222222")
    private val parcel = UUID.fromString("33333333-3333-3333-3333-333333333333")
    private val membership = UUID.fromString("44444444-4444-4444-4444-444444444444")

    @Test fun parsesCompleteGraphKeepingRemoteIdsVersionsAndMillisecondInstants() {
        val graph = parseParentGraph(body(), workspace)
        assertEquals(owner, graph.workspace.ownerUserId)
        assertEquals(3L, graph.workspace.metadata.version)
        assertEquals(3L, graph.workspace.metadata.remoteVersion)
        assertEquals(SyncStatus.SYNCED, graph.farms.single().metadata.syncStatus)
        assertEquals(farm, graph.farms.single().id)
        assertNull(graph.farms.single().coverDocumentId)
        assertEquals(Instant.parse("2026-10-30T08:00:00.123Z"), graph.parcels.single().metadata.createdAt)
        assertEquals(1234.5, graph.parcels.single().managedAreaM2!!, 0.0)
        assertEquals(120, graph.parcels.single().oliveTreeCount)
        assertEquals(membership, graph.memberships.single().id)
        assertNull(graph.memberships.single().validUntil)
    }

    @Test fun rejectsEveryIncompleteOrCrossScopeGraphBeforeRoom() {
        val foreign = UUID.randomUUID()
        listOf(
            body().replace("\"contract_version\":1", "\"contract_version\":2"),
            body().replace("\"farms\":[", "\"extra\":1,\"farms\":["),
            body(farmWorkspace = foreign),
            body(membershipFarm = foreign),
            body(farmStatus = "DELETED"),
            body(version = 0),
            body(validUntil = "\"2025-12-31T00:00:00+00:00\""),
            body().replace("\"name\":\"Finca\"", "\"name\":\" \""),
            body().replace("\"ownerUserId\":\"$owner\"", "\"ownerUserId\":null"),
        ).forEach { invalid ->
            assertThrows(Exception::class.java) { parseParentGraph(invalid, workspace) }
        }
        assertThrows(Exception::class.java) { parseParentGraph(body(), foreign) }
    }

    @Test fun rejectsDuplicateIdsAndDuplicateMembershipPeriods() {
        val twoFarms = body().replace("\"farms\":[{", "\"farms\":[${farmJson()},{")
        assertThrows(Exception::class.java) { parseParentGraph(twoFarms, workspace) }
        val twoPeriods = body().replace("\"memberships\":[{",
            "\"memberships\":[${membershipJson(UUID.randomUUID())},{")
        assertThrows(Exception::class.java) { parseParentGraph(twoPeriods, workspace) }
    }

    private fun farmJson(workspaceId: UUID = workspace, status: String = "ACTIVE", version: Int = 2) =
        """{"id":"$farm","workspaceId":"$workspaceId","name":"Finca","description":null,"municipality":"Bedmar",""" +
            """"province":"Jaén","notes":null,"status":"$status","createdAt":"2026-10-30T08:00:00.123456+00:00",""" +
            """"updatedAt":"2026-10-30T09:00:00+00:00","version":$version}"""

    private fun membershipJson(id: UUID = membership, farmId: UUID = farm, validUntil: String = "null") =
        """{"id":"$id","workspaceId":"$workspace","farmId":"$farmId","parcelId":"$parcel",""" +
            """"validFrom":"2026-01-01T00:00:00+00:00","validUntil":$validUntil,""" +
            """"createdAt":"2026-10-30T08:00:00+00:00","updatedAt":"2026-10-30T08:00:00+00:00","version":1}"""

    private fun body(
        farmWorkspace: UUID = workspace,
        membershipFarm: UUID = farm,
        farmStatus: String = "ACTIVE",
        version: Int = 2,
        validUntil: String = "null",
    ) = """{"contract_version":1,"issuer":"https://project.example/auth/v1","subject":"$owner",""" +
        """"workspace":{"id":"$workspace","name":"Olivar","ownerUserId":"$owner","countryCode":"ES",""" +
        """"timezone":"Europe/Madrid","locale":"es-ES","currency":"EUR","createdAt":"2026-10-30T08:00:00+00:00",""" +
        """"updatedAt":"2026-10-30T08:00:00+00:00","version":3},""" +
        """"farms":[${farmJson(farmWorkspace, farmStatus, version)}],""" +
        """"parcels":[{"id":"$parcel","workspaceId":"$workspace","displayName":"Parcela","cadastralReference":null,""" +
        """"cadastralPolygon":null,"cadastralParcel":null,"municipality":null,"province":null,"source":"MANUAL",""" +
        """"geometryGeoJson":null,"sourceProvider":null,"sourceImportedAt":null,"cadastralAreaM2":null,""" +
        """"managedAreaM2":1234.5,"notes":null,"status":"ACTIVE","oliveTreeCount":120,"variety":"Picual",""" +
        """"irrigationSystem":null,"irrigationNetwork":null,"irrigationSector":null,"irrigationDays":null,""" +
        """"createdAt":"2026-10-30T08:00:00.123999+00:00","updatedAt":"2026-10-30T08:00:00.124+00:00","version":1}],""" +
        """"memberships":[${membershipJson(farmId = membershipFarm, validUntil = validUntil)}]}"""
}
