package com.isivoltpro.maginaolivo.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.isivoltpro.maginaolivo.ui.theme.OlivarColors
import com.isivoltpro.maginaolivo.ui.theme.OlivarDimens

enum class MoFreshnessState {
    Fresh,
    Stale,
    Offline,
}

@Composable
fun MoSourceFreshness(
    source: String,
    updatedText: String,
    state: MoFreshnessState,
    modifier: Modifier = Modifier,
) {
    val color = when (state) {
        MoFreshnessState.Fresh -> OlivarColors.Success
        MoFreshnessState.Stale -> OlivarColors.Warning
        MoFreshnessState.Offline -> OlivarColors.Charcoal500
    }

    Row(
        modifier = modifier.padding(top = OlivarDimens.SpaceXs),
    ) {
        Text(
            text = "$source · $updatedText",
            style = MaterialTheme.typography.labelMedium,
            color = color,
        )
    }
}
