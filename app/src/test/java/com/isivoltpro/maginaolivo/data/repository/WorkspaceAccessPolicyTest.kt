package com.isivoltpro.maginaolivo.data.repository

import com.isivoltpro.maginaolivo.feature.account.WorkspaceSession
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkspaceAccessPolicyTest {
    private val a = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
    private val c = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc")
    private val one = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val two = UUID.fromString("22222222-2222-2222-2222-222222222222")
    private val issuer = "https://project.example"

    @Test fun oneWorkspaceRequiresExplicitSelection() {
        val grants = listOf(WorkspaceGrant(issuer, a, one))
        assertEquals(WorkspaceAccessDecision.SelectionRequired, WorkspaceAccessPolicy.resolve(WorkspaceSession.Authenticated(a), issuer, null, grants))
        assertEquals(WorkspaceAccessDecision.Allowed(one), WorkspaceAccessPolicy.resolve(WorkspaceSession.Authenticated(a), issuer, one, grants))
    }

    @Test fun multipleWorkspacesNeverFallBackToAnother() {
        val grants = listOf(WorkspaceGrant(issuer, a, one), WorkspaceGrant(issuer, a, two))
        assertEquals(WorkspaceAccessDecision.Allowed(two), WorkspaceAccessPolicy.resolve(WorkspaceSession.Authenticated(a), issuer, two, grants))
        assertEquals(WorkspaceAccessDecision.Denied, WorkspaceAccessPolicy.resolve(WorkspaceSession.Authenticated(a), issuer, UUID.randomUUID(), grants))
    }

    @Test fun switchLogoutExpiryAndUnknownIssuerFailClosed() {
        val grants = listOf(WorkspaceGrant(issuer, a, one))
        for (session in listOf(WorkspaceSession.Authenticated(c), WorkspaceSession.SignedOut, WorkspaceSession.Expired)) {
            assertEquals(WorkspaceAccessDecision.Denied, WorkspaceAccessPolicy.resolve(session, issuer, one, grants))
        }
        assertEquals(WorkspaceAccessDecision.Denied, WorkspaceAccessPolicy.resolve(WorkspaceSession.Authenticated(a), "https://other.example", one, grants))
    }

    @Test fun conflictingClaimsForSameWorkspaceDenyEvenTheOriginalAccount() {
        val grants = listOf(WorkspaceGrant(issuer, a, one), WorkspaceGrant(issuer, c, one))
        assertEquals(WorkspaceAccessDecision.Denied, WorkspaceAccessPolicy.resolve(WorkspaceSession.Authenticated(a), issuer, one, grants))
    }
}
