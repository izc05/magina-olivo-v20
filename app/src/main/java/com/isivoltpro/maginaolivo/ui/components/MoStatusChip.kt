package com.isivoltpro.maginaolivo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.isivoltpro.maginaolivo.ui.theme.OlivarColors

enum class MoStatusTone {
    Active,
    Planned,
    Pending,
    Confirmed,
    Error,
    Offline,
    Estimate,
}

@Composable
fun MoStatusChip(
    text: String,
    tone: MoStatusTone,
    modifier: Modifier = Modifier,
) {
    val (background, foreground) = when (tone) {
        MoStatusTone.Active -> OlivarColors.Sage200 to OlivarColors.Olive900
        MoStatusTone.Planned -> Color(0xFFEAF0F4) to OlivarColors.Info
        MoStatusTone.Pending -> Color(0xFFFFF0D9) to OlivarColors.Warning
        MoStatusTone.Confirmed -> Color(0xFFE6F3E6) to OlivarColors.Success
        MoStatusTone.Error -> Color(0xFFF8E6E3) to OlivarColors.Error
        MoStatusTone.Offline -> Color(0xFFECEDE9) to OlivarColors.Charcoal700
        MoStatusTone.Estimate -> Color(0xFFE8EEF5) to OlivarColors.Info
    }

    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = foreground,
        modifier = modifier
            .background(
                color = background,
                shape = RoundedCornerShape(999.dp),
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}
