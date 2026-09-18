package com.isivoltpro.maginaolivo.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import com.isivoltpro.maginaolivo.ui.theme.MoOliveDark
import com.isivoltpro.maginaolivo.ui.theme.MoOlivePrimary
import com.isivoltpro.maginaolivo.ui.theme.MoOutline
import com.isivoltpro.maginaolivo.ui.theme.MoShape
import com.isivoltpro.maginaolivo.ui.theme.MoSize
import com.isivoltpro.maginaolivo.ui.theme.MoSurfaceSoft
import com.isivoltpro.maginaolivo.ui.theme.MoTextSecondary

@Composable
fun MoSelectField(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MoChoiceField(
        label = label,
        value = value,
        symbol = "⌄",
        onClick = onClick,
        modifier = modifier,
    )
}

@Composable
fun MoDateField(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MoChoiceField(
        label = label,
        value = value,
        symbol = "▦",
        onClick = onClick,
        modifier = modifier,
    )
}

@Composable
private fun MoChoiceField(
    label: String,
    value: String,
    symbol: String,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = MoSize.fieldMinHeight)
            .clickable(
                role = Role.Button,
                onClick = onClick,
            ),
        shape = MoShape.field,
        color = MoSurfaceSoft,
        border = BorderStroke(
            width = androidx.compose.ui.unit.dp(1f),
            color = MoOutline,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = androidx.compose.ui.unit.dp(16f), vertical = androidx.compose.ui.unit.dp(10f)),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(androidx.compose.ui.unit.dp(2f)),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MoTextSecondary,
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MoOliveDark,
                )
            }
            Text(
                text = symbol,
                style = MaterialTheme.typography.titleMedium,
                color = MoOlivePrimary,
            )
        }
    }
}
