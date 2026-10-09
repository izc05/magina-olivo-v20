package com.isivoltpro.maginaolivo.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.isivoltpro.maginaolivo.ui.theme.MoErrorText
import com.isivoltpro.maginaolivo.ui.theme.MoInfoText
import com.isivoltpro.maginaolivo.ui.theme.MoOliveMid
import com.isivoltpro.maginaolivo.ui.theme.MoSurfaceTokens
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
    /** Optional leading mark (✓ done, clock planned) so states differ by shape, not only by colour. */
    icon: ImageVector? = null,
) {
    val foreground = when (tone) {
        MoStatusTone.Neutral -> MoSurfaceTokens.secondaryText
        MoStatusTone.Success -> MoOliveMid
        MoStatusTone.Info -> MoInfoText
        MoStatusTone.Warning -> MoWarningText
        MoStatusTone.Error -> MoErrorText
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = MoSurfaceTokens.tintedCard(foreground, 0.12f),
        contentColor = foreground,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = Color.Unspecified,
            )
        }
    }
}
