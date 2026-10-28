package com.isivoltpro.maginaolivo.data.repository

import com.isivoltpro.maginaolivo.feature.account.WorkspaceSession
import java.util.UUID

/** A grant is evidence from a verified receive receipt, never a local owner_user_id. */
internal data class WorkspaceGrant(val issuer: String, val accountId: UUID, val workspaceId: UUID)

internal sealed interface WorkspaceAccessDecision {
    data class Allowed(val workspaceId: UUID) : WorkspaceAccessDecision
    data object SelectionRequired : WorkspaceAccessDecision
    data object Denied : WorkspaceAccessDecision
}

internal object WorkspaceAccessPolicy {
    fun resolve(
        session: WorkspaceSession,
        issuer: String,
        selected: UUID?,
        grants: List<WorkspaceGrant>,
    ): WorkspaceAccessDecision {
        val account = (session as? WorkspaceSession.Authenticated)?.accountId
            ?: return WorkspaceAccessDecision.Denied
        if (issuer.isBlank()) return WorkspaceAccessDecision.Denied
        if (selected == null) return WorkspaceAccessDecision.SelectionRequired
        val claims = grants.filter { it.workspaceId == selected }
        if (claims.isEmpty() || claims.any { it.issuer != issuer || it.accountId != account }) {
            return WorkspaceAccessDecision.Denied
        }
        return WorkspaceAccessDecision.Allowed(selected)
    }
}
