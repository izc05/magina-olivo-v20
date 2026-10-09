package com.isivoltpro.maginaolivo.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.isivoltpro.maginaolivo.ui.brand.OliveMark
import com.isivoltpro.maginaolivo.ui.theme.MoError
import com.isivoltpro.maginaolivo.ui.theme.MoShape
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing
import com.isivoltpro.maginaolivo.ui.theme.MoSurfaceSoft
import com.isivoltpro.maginaolivo.ui.theme.MoSurfaceTokens

/**
 * UI polish v2: an empty state says what is missing and how to get it, compactly.
 * Prefer a specific sentence ("Aún no has registrado cosecha") over a generic "Sin datos".
 */
@Composable
fun MoEmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    actionModifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MoShape.card,
        colors = CardDefaults.cardColors(containerColor = MoSurfaceTokens.cardSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MoSurfaceTokens.cardStroke),
    ) {
        Row(
            modifier = Modifier.padding(MoSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(MoSpacing.sm),
            verticalAlignment = Alignment.Top,
        ) {
            if (icon != null) {
                MoIconBadge(icon)
            } else {
                OliveMark(modifier = Modifier.size(40.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(MoSpacing.xxs), modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MoSurfaceTokens.secondaryText,
                )
                if (actionText != null && onAction != null) {
                    MoSecondaryButton(
                        text = actionText,
                        onClick = onAction,
                        modifier = actionModifier.padding(top = MoSpacing.xxs),
                    )
                }
            }
        }
    }
}

@Composable
fun MoErrorState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    retryText: String = "Reintentar",
    onRetry: (() -> Unit)? = null,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MoShape.cardLarge,
        colors = CardDefaults.cardColors(containerColor = MoSurfaceTokens.cardSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error),
    ) {
        Column(
            modifier = Modifier.padding(MoSpacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MoSpacing.sm),
        ) {
            Surface(
                modifier = Modifier.size(50.dp),
                shape = RoundedCornerShape(16.dp),
                color = MoError.copy(alpha = 0.10f),
                contentColor = MoError,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("!", style = MaterialTheme.typography.headlineMedium)
                }
            }
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MoSurfaceTokens.secondaryText)
            if (onRetry != null) {
                MoSecondaryButton(
                    text = retryText,
                    onClick = onRetry,
                )
            }
        }
    }
}

@Composable
fun MoListSkeleton(
    rows: Int = 3,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MoSpacing.sm),
    ) {
        repeat(rows.coerceAtLeast(1)) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MoShape.card,
                colors = CardDefaults.cardColors(containerColor = MoSurfaceTokens.cardSurface),
            ) {
                Column(
                    modifier = Modifier.padding(MoSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth(0.48f)
                            .height(17.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = MoSurfaceSoft,
                    ) {}
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth(0.78f)
                            .height(13.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = MoSurfaceSoft,
                    ) {}
                    Spacer(Modifier.height(2.dp))
                }
            }
        }
    }
}
