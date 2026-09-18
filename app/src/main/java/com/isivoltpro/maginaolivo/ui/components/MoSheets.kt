package com.isivoltpro.maginaolivo.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.isivoltpro.maginaolivo.ui.theme.MoOliveDark
import com.isivoltpro.maginaolivo.ui.theme.MoShape
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing
import com.isivoltpro.maginaolivo.ui.theme.MoTextSecondary
import com.isivoltpro.maginaolivo.ui.theme.MoWarmWhite

@Composable
fun MoConfirmationSheet(
    title: String,
    body: String,
    confirmText: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    cancelText: String = "Cancelar",
    onCancel: () -> Unit = {},
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MoShape.hero,
        color = MoWarmWhite,
        contentColor = MoOliveDark,
    ) {
        Column(
            modifier = Modifier.padding(MoSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(MoSpacing.md),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyLarge,
                color = MoTextSecondary,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MoSpacing.sm),
            ) {
                MoSecondaryButton(
                    text = cancelText,
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                )
                MoPrimaryButton(
                    text = confirmText,
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
fun MoBottomActionSheet(
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MoShape.hero,
        color = MoWarmWhite,
        contentColor = MoOliveDark,
    ) {
        Column(
            modifier = Modifier.padding(MoSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(MoSpacing.md),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
            )
            if (body != null) {
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MoTextSecondary,
                )
            }
            content()
        }
    }
}
