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

@Composable
fun MoActivityRow(
    title: String,
    dateText: String,
    contextText: String,
    modifier: Modifier = Modifier,
    statusText: String? = null,
    statusTone: MoStatusTone = MoStatusTone.Confirmed,
) {
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
                Text(text = dateText, style = MaterialTheme.typography.labelMedium)
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = contextText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!statusText.isNullOrBlank()) {
                MoStatusChip(text = statusText, tone = statusTone)
            }
        }
    }
}
