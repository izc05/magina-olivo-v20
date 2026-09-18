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

enum class MoOcrConfidence {
    High,
    Medium,
    Low,
}

@Composable
fun MoOcrReviewField(
    label: String,
    extractedValue: String,
    confidence: MoOcrConfidence,
    modifier: Modifier = Modifier,
) {
    val tone = when (confidence) {
        MoOcrConfidence.High -> MoStatusTone.Confirmed
        MoOcrConfidence.Medium -> MoStatusTone.Pending
        MoOcrConfidence.Low -> MoStatusTone.Error
    }
    val text = when (confidence) {
        MoOcrConfidence.High -> "Conf. alta"
        MoOcrConfidence.Medium -> "Revisar"
        MoOcrConfidence.Low -> "Conf. baja"
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(OlivarDimens.SpaceMd),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = extractedValue,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = OlivarDimens.Space2Xs),
                )
            }
            MoStatusChip(
                text = text,
                tone = tone,
            )
        }
    }
}

@Composable
fun MoOcrReviewPanel(
    title: String,
    sourceName: String,
    modifier: Modifier = Modifier,
    content: @Composable Column.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(OlivarDimens.SpaceMd),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = sourceName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                MoStatusChip(
                    text = "Por revisar",
                    tone = MoStatusTone.Pending,
                )
            }
            Column(
                modifier = Modifier.padding(top = OlivarDimens.SpaceMd),
                content = content,
            )
        }
    }
}
