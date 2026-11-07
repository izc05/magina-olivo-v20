package com.isivoltpro.maginaolivo.data.repository.sync

import com.isivoltpro.maginaolivo.data.local.entity.AgriculturalCycleEntity
import com.isivoltpro.maginaolivo.data.local.entity.AgriculturalCycleRecollectionEntity
import com.isivoltpro.maginaolivo.domain.cycle.CycleSyncView
import com.isivoltpro.maginaolivo.feature.campaignhub.cycleSyncText
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.*
import org.junit.Test

class AgriculturalCycleSyncPayloadTest {
    private val subject = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
    private val workspace = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val farm = UUID.fromString("22222222-2222-2222-2222-222222222222")
    private val old = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val open = UUID.fromString("00000000-0000-0000-0000-000000000002")
    private val campaign = UUID.fromString("00000000-0000-0000-0000-0000000000c1")

    @Test fun stateIsOrderedAndCarriesOnlyHeadersAndRecollectionLinks() {
        val state = buildCycleState(workspace, listOf(
            AgriculturalCycleEntity(open, workspace, farm, "2026/27", LocalDate.parse("2026-02-01")),
            AgriculturalCycleEntity(old, workspace, farm, "2025/26", LocalDate.parse("2025-02-01"), LocalDate.parse("2026-01-31"),
                Instant.parse("2026-02-01T10:00:00.123456789Z"), false, 2),
        ), listOf(AgriculturalCycleRecollectionEntity(campaign, old)))
        assertEquals("""{"workspaceId":"$workspace","cycles":[{"id":"$old","label":"2025/26","startDate":"2025-02-01","effectiveEndDate":"2026-01-31","closedAt":"2026-02-01T10:00:00.123456Z","noRecollection":false},{"id":"$open","label":"2026/27","startDate":"2026-02-01","effectiveEndDate":null,"closedAt":null,"noRecollection":false}],"recollections":[{"campaignId":"$campaign","cycleId":"$old"}]}""",
            state.toString())
    }

    private fun head(extra: String = "", subject: UUID = this.subject, cycles: String? = null, links: String? = null) =
        Json.parseToJsonElement("""{"contract_version":1,"subject":"$subject","farm_id":"$farm","workspace_id":"$workspace","revision":2,
            "cycles":${cycles ?: """[{"id":"$old","label":"2025/26","startDate":"2025-02-01","effectiveEndDate":"2026-01-31","closedAt":"2026-02-01T10:00:00.123456Z","noRecollection":false},{"id":"$open","label":"2026/27","startDate":"2026-02-01","effectiveEndDate":null,"closedAt":null,"noRecollection":false}]"""},
            "recollections":${links ?: """[{"campaignId":"$campaign","cycleId":"$old"}]"""}$extra}""") as JsonObject

    @Test fun strictHeadParserAcceptsTheContractAndRejectsAnythingElse() {
        val parsed = parseRemoteFarmCycles(head(), subject, farm)!!
        assertEquals(2L, parsed.revision)
        assertEquals(old, parsed.recollections[campaign])
        assertEquals(Instant.parse("2026-02-01T10:00:00.123456Z"), parsed.cycles.first().closedAt)
        assertNull(parseRemoteFarmCycles(head(extra = ""","x":1"""), subject, farm))
        assertNull(parseRemoteFarmCycles(head(subject = UUID.randomUUID()), subject, farm))
        assertNull(parseRemoteFarmCycles(head(), subject, UUID.randomUUID()))
        assertNull(parseRemoteFarmCycles(head(links = """[{"campaignId":"$campaign","cycleId":"${UUID.randomUUID()}"}]"""), subject, farm))
        assertNull(parseRemoteFarmCycles(head(cycles = """[{"id":"$old","label":"a","startDate":"2025-02-01","effectiveEndDate":null,"closedAt":null,"noRecollection":false},{"id":"$open","label":"b","startDate":"2026-02-01","effectiveEndDate":null,"closedAt":null,"noRecollection":false}]""", links = "[]"), subject, farm))
        assertNull(parseRemoteFarmCycles(head(cycles = """[{"id":"$old","label":"a","startDate":"2025-02-01","effectiveEndDate":"2026-01-31","closedAt":null,"noRecollection":false}]""", links = "[]"), subject, farm))
    }

    @Test fun panelTextNamesEveryStateAndNeverClaimsSyncWithoutAState() {
        assertEquals("Guardada en este teléfono.", cycleSyncText(null))
        assertTrue(cycleSyncText(CycleSyncView("SYNCED", null)).contains("sincronizada con tu cuenta"))
        assertTrue(cycleSyncText(CycleSyncView("PENDING", "OUTCOME_UNKNOWN")).contains("Pendiente de enviar"))
        assertTrue(cycleSyncText(CycleSyncView("WAITING_PARENTS", null)).contains("recolecciones estén sincronizadas"))
        assertTrue(cycleSyncText(CycleSyncView("CONFLICT", "STALE_REVISION")).contains("No se ha sobrescrito nada"))
        assertTrue(cycleSyncText(CycleSyncView("NOT_AUTHORIZED", null)).contains("propietario"))
    }
}
