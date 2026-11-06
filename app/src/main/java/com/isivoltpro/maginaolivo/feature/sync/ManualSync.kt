package com.isivoltpro.maginaolivo.feature.sync

import java.time.Instant
import kotlinx.coroutines.flow.StateFlow

/**
 * Manual synchronization, started only by the user («Sincronizar ahora»). There is no scheduler, no
 * background worker and no automatic trigger: nothing calls [requestSync] except that button.
 *
 * Only the dev flavor provides an implementation; staging and production get none, so the button
 * says synchronization is not available in that build.
 */
interface ManualSync {
    val state: StateFlow<ManualSyncState>

    /** Starts one run unless one is already running. Returns immediately; [state] reports progress. */
    fun requestSync()
}

sealed interface ManualSyncState {
    /** Never run in this process. */
    data object Idle : ManualSyncState

    /** «Sincronizando»: [step] is a short, user-facing description of the current phase. */
    data class Running(val step: String) : ManualSyncState

    /** «Completado»: everything this version can synchronize is confirmed by the server. */
    data class Completed(val at: Instant, val sent: Int, val received: Int, val notes: List<String> = emptyList()) : ManualSyncState

    /** «Pendiente»: the run finished, but some items need the user (consent, review) or another run. */
    data class Pending(val at: Instant, val sent: Int, val received: Int, val reasons: List<String>) : ManualSyncState

    /** «Error»: the run could not be done (offline, no session, server unavailable). Local data is untouched. */
    data class Failed(val at: Instant, val message: String) : ManualSyncState
}
