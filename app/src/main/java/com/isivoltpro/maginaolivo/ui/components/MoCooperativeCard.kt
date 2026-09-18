package com.isivoltpro.maginaolivo.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
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
fun MoCooperativeCard(
    organizationName: String,
    noticeTitle: String,
    noticeBody: String,
    modifier: Modifier = Modifier,
    source: String? = null,
    updatedText: String? = null,
    freshness: MoFreshnessState = MoFreshnessState.Fresh,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(OlivarDimens.SpaceMd)) {
            Text(
                organizationName,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                noticeTitle,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = OlivarDimens.SpaceSm),
            )
            Text(
                noticeBody,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = OlivarDimens.Space2Xs),
            )
            if (!source.isNullOrBlank() && !updatedText.isNullOrBlank()) {
                MoSourceFreshness(
                    source = source,
                    updatedText = updatedText,
                    state = freshness,
                )
            }
        }
    }
}
