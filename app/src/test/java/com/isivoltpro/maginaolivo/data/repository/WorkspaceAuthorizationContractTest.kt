package com.isivoltpro.maginaolivo.data.repository

import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceAuthorizationContractTest {
    private val account = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
    private val workspace = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val now = Instant.parse("2026-10-29T10:00:00Z")
    private val issuer = "https://project.example"

    @Test fun scopedSnapshotExpiresOfflineAndCannotCrossAccounts() {
        val contract = WorkspaceAuthorizationContract.parse(body())
        assertTrue(contract.validFor(issuer, account, now))
        assertEquals(workspace, contract.available(now).single().id)
        assertFalse(contract.validFor(issuer, UUID.randomUUID(), now))
        assertFalse(contract.validFor("https://other.example", account, now))
        assertTrue(contract.available(now.plusSeconds(901)).isEmpty())
        assertFalse(contract.validFor(issuer, account, now.plusSeconds(1080)))
    }

    @Test fun malformedOrUnknownContractFailsClosed() {
        val malformed = body().replace("\"contract_version\":1", "\"contract_version\":2")
        assertTrue(runCatching { WorkspaceAuthorizationContract.parse(malformed) }.isFailure)
        val duplicate = body().replace("}]}", "},${body().substringAfter("\"workspaces\":[").substringBeforeLast("]}")}]}")
        assertTrue(runCatching { WorkspaceAuthorizationContract.parse(duplicate) }.isFailure)
    }

    private fun body() = """{"contract_version":1,"issuer":"$issuer/auth/v1","subject":"$account","verified_at":"$now","token_expires_at":"${now.plusSeconds(3600)}","workspaces":[{"id":"$workspace","name":"A","permissions":["read"],"grant_version":1,"authorization_expires_at":"${now.plusSeconds(900)}"}]}"""
}
