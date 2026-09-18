package com.isivoltpro.maginaolivo.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

enum class MoSyncState {
    Local,
    Pending,
    Synced,
    Error,
}

@Composable
fun MoSyncStatus(
    state: MoSyncState,
    modifier: Modifier = Modifier,
) {
    val (text, tone) = when (state) {
        MoSyncState.Local -> "Solo local" to MoStatusTone.Offline
        MoSyncState.Pending -> "Pendiente de sincronizar" to MoStatusTone.Pending
        MoSyncState.Synced -> "Sincronizado" to MoStatusTone.Confirmed
        MoSyncState.Error -> "Error de sincronización" to MoStatusTone.Error
    }

    MoStatusChip(
        text = text,
        tone = tone,
        modifier = modifier,
    )
}
