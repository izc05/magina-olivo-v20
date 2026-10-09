package com.isivoltpro.maginaolivo.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.isivoltpro.maginaolivo.ui.theme.MoShape
import com.isivoltpro.maginaolivo.ui.theme.MoSize
import com.isivoltpro.maginaolivo.ui.theme.MoSurfaceTokens
import com.isivoltpro.maginaolivo.ui.theme.MoColors

@Composable
fun MoPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = MoSize.buttonHeight),
        shape = MoShape.field,
        colors = ButtonDefaults.buttonColors(
            containerColor = MoColors.current.primaryButton,
            contentColor = MoColors.current.onPrimaryButton,
        ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
            )
            if (trailingIcon != null) {
                Spacer(Modifier.width(8.dp))
                trailingIcon()
            }
        }
    }
}

@Composable
fun MoSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = MoSize.buttonHeight),
        shape = MoShape.field,
        // Secondary actions stay neutral so only the primary CTA reads as dark olive.
        border = BorderStroke(1.dp, MoSurfaceTokens.cardStroke),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = MoSurfaceTokens.cardSurface,
            contentColor = MoColors.current.bodyText,
        ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
            )
            if (trailingIcon != null) {
                Spacer(Modifier.width(8.dp))
                trailingIcon()
            }
        }
    }
}

/**
 * Cancelling, deleting, closing a campaign: soft red, outlined, never the dominant
 * action on a screen. Always paired with a confirmation where the data model asks for one.
 */
@Composable
fun MoDestructiveButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = MoSize.buttonHeight),
        shape = MoShape.field,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = MoSurfaceTokens.cardSurface,
            contentColor = MoColors.current.errorText,
        ),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

/** The quietest action (Cancelar, Volver): text only, warm grey, still a 48dp target. */
@Composable
fun MoTertiaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = MoSize.minTouchTarget),
        colors = ButtonDefaults.textButtonColors(contentColor = MoSurfaceTokens.secondaryText),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}
