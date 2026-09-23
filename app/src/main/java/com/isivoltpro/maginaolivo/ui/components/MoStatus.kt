package com.isivoltpro.maginaolivo.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.isivoltpro.maginaolivo.ui.theme.MoErrorText
import com.isivoltpro.maginaolivo.ui.theme.MoInfoText
import com.isivoltpro.maginaolivo.ui.theme.MoOliveMid
import com.isivoltpro.maginaolivo.ui.theme.MoTextSecondary
import com.isivoltpro.maginaolivo.ui.theme.MoWarningText

enum class MoStatusTone {
    Neutral,
    Success,
    Info,
    Warning,
    Error,
}

@Composable
fun MoStatusChip(
    text: String,
    modifier: Modifier = Modifier,
    tone: MoStatusTone = MoStatusTone.Neutral,
) {
    val foreground = when (tone) {
        MoStatusTone.Neutral -> MoTextSecondary
        MoStatusTone.Success -> MoOliveMid
        MoStatusTone.Info -> MoInfoText
        MoStatusTone.Warning -> MoWarningText
        MoStatusTone.Error -> MoErrorText
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = foreground.copy(alpha = 0.12f),
        contentColor = foreground,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = Color.Unspecified,
            )
        }
    }
}
