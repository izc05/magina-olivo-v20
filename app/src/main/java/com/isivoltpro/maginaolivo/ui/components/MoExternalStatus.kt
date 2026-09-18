package com.isivoltpro.maginaolivo.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.isivoltpro.maginaolivo.ui.theme.MoInfo
import com.isivoltpro.maginaolivo.ui.theme.MoInfoText
import com.isivoltpro.maginaolivo.ui.theme.MoOliveDark
import com.isivoltpro.maginaolivo.ui.theme.MoOlivePrimary
import com.isivoltpro.maginaolivo.ui.theme.MoOutline
import com.isivoltpro.maginaolivo.ui.theme.MoSurfaceSoft
import com.isivoltpro.maginaolivo.ui.theme.MoTextSecondary
import com.isivoltpro.maginaolivo.ui.theme.MoWarning
import com.isivoltpro.maginaolivo.ui.theme.MoWarningText
import com.isivoltpro.maginaolivo.ui.theme.MoWarmWhite

enum class MoSyncState {
    Synced,
    Pending,
    Offline,
}

@Composable
fun MoSourceFreshness(
    source: String,
    freshness: String,
    modifier: Modifier = Modifier,
    stale: Boolean = false,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = if (stale) MoWarning.copy(alpha = 0.10f) else MoSurfaceSoft,
        border = BorderStroke(1.dp, if (stale) MoWarning.copy(alpha = 0.45f) else MoOutline),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (stale) "!" else "i",
                style = MaterialTheme.typography.labelLarge,
                color = if (stale) MoWarningText else MoInfoText,
            )
            Column {
                Text(
                    text = source,
                    style = MaterialTheme.typography.labelLarge,
                    color = MoOliveDark,
                )
                Text(
                    text = freshness,
                    style = MaterialTheme.typography.labelMedium,
                    color = MoTextSecondary,
                )
            }
        }
    }
}

@Composable
fun MoOfflineBanner(
    modifier: Modifier = Modifier,
    message: String = "Sin conexión. Tus datos guardados siguen disponibles.",
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MoWarning.copy(alpha = 0.12f),
        contentColor = MoOliveDark,
        border = BorderStroke(1.dp, MoWarning.copy(alpha = 0.35f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("!", color = MoWarningText, style = MaterialTheme.typography.titleMedium)
            Text(message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun MoSyncStatus(
    state: MoSyncState,
    modifier: Modifier = Modifier,
) {
    val (label, tone) = when (state) {
        MoSyncState.Synced -> "Sincronizado" to MoOlivePrimary
        MoSyncState.Pending -> "Pendiente de sincronizar" to MoWarningText
        MoSyncState.Offline -> "Solo en este dispositivo" to MoTextSecondary
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = tone.copy(alpha = 0.10f),
        contentColor = tone,
        border = BorderStroke(1.dp, tone.copy(alpha = 0.28f)),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}
