package com.isivoltpro.maginaolivo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.isivoltpro.maginaolivo.ui.theme.OlivarColors
import com.isivoltpro.maginaolivo.ui.theme.OlivarDimens

@Composable
fun MoOfflineBanner(
    modifier: Modifier = Modifier,
    text: String = "Sin conexión · tus datos locales siguen disponibles",
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(OlivarColors.Cream200)
            .padding(
                horizontal = OlivarDimens.SpaceMd,
                vertical = OlivarDimens.SpaceSm,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = OlivarColors.Charcoal700,
        )
    }
}
