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
import com.isivoltpro.maginaolivo.ui.theme.OlivarColors
import com.isivoltpro.maginaolivo.ui.theme.OlivarDimens

@Composable
fun MoEmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    StateCard(
        title = title,
        body = body,
        modifier = modifier,
        accent = OlivarColors.Olive700,
        actionText = actionText,
        onAction = onAction,
    )
}

@Composable
fun MoErrorState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionText: String? = "Reintentar",
    onAction: (() -> Unit)? = null,
) {
    StateCard(
        title = title,
        body = body,
        modifier = modifier,
        accent = OlivarColors.Error,
        actionText = actionText,
        onAction = onAction,
    )
}

@Composable
private fun StateCard(
    title: String,
    body: String,
    modifier: Modifier,
    accent: androidx.compose.ui.graphics.Color,
    actionText: String?,
    onAction: (() -> Unit)?,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.padding(OlivarDimens.SpaceLg),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = accent,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = OlivarDimens.SpaceXs),
            )
            if (!actionText.isNullOrBlank() && onAction != null) {
                androidx.compose.material3.TextButton(
                    onClick = onAction,
                    modifier = Modifier.padding(top = OlivarDimens.SpaceSm),
                ) {
                    Text(actionText)
                }
            }
        }
    }
}
