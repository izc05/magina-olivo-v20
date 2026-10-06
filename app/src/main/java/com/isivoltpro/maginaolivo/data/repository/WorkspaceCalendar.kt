package com.isivoltpro.maginaolivo.data.repository

import com.isivoltpro.maginaolivo.core.time.AppClock
import com.isivoltpro.maginaolivo.data.local.MaginaOlivoDatabase
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/**
 * Agricultural dates belong to the Workspace calendar, not to the phone's current timezone.
 *
 * This matters around midnight and when a device temporarily uses another timezone: a Pesada,
 * Jornada, completed work, payment or campaign closure must agree on what "today" means.
 */
internal suspend fun MaginaOlivoDatabase.todayForWorkspace(
    workspaceId: UUID,
    clock: AppClock,
    zoneOverride: (() -> ZoneId)? = null,
): LocalDate {
    val zone = zoneOverride?.invoke() ?: run {
        val workspace = workspaceDao().findById(workspaceId)
            ?: throw IllegalStateException("workspace_not_found")
        runCatching { ZoneId.of(workspace.timezone) }
            .getOrElse { throw IllegalStateException("workspace_invalid_timezone", it) }
    }
    return clock.today(zone)
}
