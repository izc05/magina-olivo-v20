package com.isivoltpro.maginaolivo.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.isivoltpro.maginaolivo.ui.theme.OlivarDimens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoConfirmationSheet(
    title: String,
    body: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    secondaryText: String = "Cancelar",
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = OlivarDimens.SpaceLg,
                    end = OlivarDimens.SpaceLg,
                    bottom = OlivarDimens.SpaceXl,
                ),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = OlivarDimens.SpaceXs),
            )
            Spacer(modifier = Modifier.height(OlivarDimens.SpaceLg))
            MoPrimaryButton(
                text = confirmText,
                onClick = onConfirm,
            )
            Spacer(modifier = Modifier.height(OlivarDimens.SpaceXs))
            MoSecondaryButton(
                text = secondaryText,
                onClick = onDismiss,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoBottomActionSheet(
    title: String,
    actions: List<String>,
    onAction: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = OlivarDimens.SpaceLg,
                    end = OlivarDimens.SpaceLg,
                    bottom = OlivarDimens.SpaceXl,
                ),
            verticalArrangement = Arrangement.spacedBy(OlivarDimens.SpaceXs),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
            )
            actions.forEach { action ->
                MoSecondaryButton(
                    text = action,
                    onClick = { onAction(action) },
                )
            }
        }
    }
}
