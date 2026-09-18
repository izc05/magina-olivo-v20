package com.isivoltpro.maginaolivo.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.isivoltpro.maginaolivo.ui.theme.OlivarDimens

enum class MoAttachmentState {
    Local,
    UploadPending,
    OcrPending,
    Confirmed,
    Error,
}

@Composable
fun MoAttachmentTile(
    fileName: String,
    typeLabel: String,
    state: MoAttachmentState,
    modifier: Modifier = Modifier,
) {
    val (stateText, tone) = when (state) {
        MoAttachmentState.Local -> "Solo local" to MoStatusTone.Offline
        MoAttachmentState.UploadPending -> "Subida pendiente" to MoStatusTone.Pending
        MoAttachmentState.OcrPending -> "OCR pendiente" to MoStatusTone.Pending
        MoAttachmentState.Confirmed -> "Confirmado" to MoStatusTone.Confirmed
        MoAttachmentState.Error -> "Error" to MoStatusTone.Error
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(OlivarDimens.SpaceMd),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(fileName, style = MaterialTheme.typography.titleMedium)
                Text(
                    typeLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            MoStatusChip(stateText, tone)
        }
    }
}
