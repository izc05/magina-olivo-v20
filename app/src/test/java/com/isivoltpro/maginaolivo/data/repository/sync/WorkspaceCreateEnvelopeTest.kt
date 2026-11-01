package com.isivoltpro.maginaolivo.data.repository.sync

import com.isivoltpro.maginaolivo.data.local.entity.LocalMetadata
import com.isivoltpro.maginaolivo.data.local.entity.WorkspaceEntity
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceCreateEnvelopeTest {
    private val issuer = "https://project.example"
    private val id = UUID.fromString("0d6b8f4e-3c2a-4b1e-9f7a-5c4d3e2f1a0b")
    private val row = WorkspaceEntity(id, "Olivar Jódar \"A\"\\", UUID.randomUUID(), "ES", "Europe/Madrid", "es-ES",
        "EUR", LocalMetadata(Instant.EPOCH, Instant.EPOCH))

    /** Same literal the g26-workspace-send SQL contract test asserts for `jsonb::text` on PostgreSQL. */
    private val canonical = "{\"id\": \"0d6b8f4e-3c2a-4b1e-9f7a-5c4d3e2f1a0b\", \"name\": \"Olivar Jódar \\\"A\\\"\\\\\", " +
        "\"locale\": \"es-ES\", \"currency\": \"EUR\", \"timezone\": \"Europe/Madrid\", \"countryCode\": \"ES\"}"

    @Test fun envelopeHashMatchesPostgresCanonicalJsonb() {
        val snapshot = parseWorkspaceCreateEnvelope(buildWorkspaceCreateEnvelope(row, issuer))!!
        assertEquals(issuer, snapshot.issuer)
        assertEquals(1L, snapshot.localVersion)
        assertEquals(id, snapshot.workspaceId)
        assertEquals(setOf("id", "name", "countryCode", "timezone", "locale", "currency"), snapshot.workspace.keys)
        val expected = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        assertEquals(expected, snapshot.hash)
        // Stable: rebuilding from the same row yields the same bytes and hash.
        assertEquals(buildWorkspaceCreateEnvelope(row, issuer), buildWorkspaceCreateEnvelope(row.copy(), issuer))
    }

    @Test fun tamperedOrIncompleteEnvelopesAreRejected() {
        val good = buildWorkspaceCreateEnvelope(row, issuer)
        listOf(
            good.replace("\"localVersion\":1", "\"localVersion\":0"),
            good.replace("\"issuer\":\"$issuer\"", "\"issuer\":\"\""),
            good.replace("\"currency\":\"EUR\"", "\"currency\":\"EUR\",\"ownerUserId\":\"$id\""),
            good.replace("\"currency\":\"EUR\"", "\"currency\":null"),
            good.replace("\"countryCode\":\"ES\"", "\"countryCode\":\"es\""),
            good.replace("\"timezone\":\"Europe/Madrid\"", "\"timezone\":\"Invalid/Timezone\""),
            good.replace("\"id\":\"$id\"", "\"id\":\"not-a-uuid\""),
            good.replace("{\"issuer\"", "{\"extra\":1,\"issuer\""),
            "[]",
        ).forEach { assertNull(it, parseWorkspaceCreateEnvelope(it)) }
    }

    @Test fun draftValidationMirrorsTheServer() {
        val ok = WorkspaceDraft("Mi olivar", "ES", "Europe/Madrid", "es-ES", "EUR")
        assertTrue(validDraft(ok))
        assertTrue(validDraft(ok.copy(name = "ñ".repeat(200))))
        listOf(ok.copy(name = ""), ok.copy(name = "   "), ok.copy(name = "x".repeat(201)),
            ok.copy(countryCode = "ESP"), ok.copy(currency = "eur"), ok.copy(locale = "e"),
            ok.copy(timezone = "Mars/Olympus"), ok.copy(name = "a\u0000b"))
            .forEach { assertFalse(it.toString(), validDraft(it)) }
    }
}
