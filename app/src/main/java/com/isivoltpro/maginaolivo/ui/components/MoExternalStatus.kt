package com.isivoltpro.maginaolivo.ui.components

import com.isivoltpro.maginaolivo.ui.theme.MoColors

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
import com.isivoltpro.maginaolivo.ui.theme.MoSurfaceTokens

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
        color = if (stale) MoColors.current.warningAccent.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MoSurfaceTokens.cardStroke),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (stale) "!" else "i",
                style = MaterialTheme.typography.labelLarge,
                color = if (stale) MoColors.current.warningText else MoColors.current.infoText,
            )
            Column {
                Text(
                    text = source,
                    style = MaterialTheme.typography.labelLarge,
                    color = MoColors.current.primaryText,
                )
                Text(
                    text = freshness,
                    style = MaterialTheme.typography.labelMedium,
                    color = MoSurfaceTokens.secondaryText,
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
        color = MoColors.current.warningAccent.copy(alpha = 0.12f),
        contentColor = MoColors.current.primaryText,
        border = BorderStroke(1.dp, MoSurfaceTokens.cardStroke),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("!", color = MoColors.current.warningText, style = MaterialTheme.typography.titleMedium)
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
        MoSyncState.Synced -> "Sincronizado" to MoColors.current.actionText
        MoSyncState.Pending -> "Pendiente de sincronizar" to MoColors.current.warningText
        MoSyncState.Offline -> "Solo en este dispositivo" to MoSurfaceTokens.secondaryText
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = MoSurfaceTokens.tintedCard(tone, 0.10f),
        contentColor = tone,
        border = BorderStroke(1.dp, MoSurfaceTokens.cardStroke),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}
