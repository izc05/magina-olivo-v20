package com.isivoltpro.maginaolivo.ui.brand

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.isivoltpro.maginaolivo.ui.theme.MoOliveDark
import com.isivoltpro.maginaolivo.ui.theme.MoOlivePrimary
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing

@Composable
fun MaginaOlivoWordmark(
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs),
    ) {
        OliveMark(
            modifier = Modifier.size(if (compact) 42.dp else 58.dp),
        )
        Text(
            text = "Mágina\nOlivo",
            color = MoOliveDark,
            style = MaterialTheme.typography.headlineMedium.copy(
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.SemiBold,
                fontSize = if (compact) 23.sp else 29.sp,
                lineHeight = if (compact) 20.sp else 25.sp,
            ),
        )
    }
}

@Composable
fun OliveMark(
    modifier: Modifier = Modifier,
    tint: Color = MoOlivePrimary,
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        drawLine(
            color = tint,
            start = Offset(w * 0.18f, h * 0.46f),
            end = Offset(w * 0.76f, h * 0.28f),
            strokeWidth = w * 0.045f,
        )

        rotate(degrees = -30f, pivot = Offset(w * 0.60f, h * 0.25f)) {
            drawOval(
                color = tint,
                topLeft = Offset(w * 0.52f, h * 0.08f),
                size = Size(w * 0.34f, h * 0.17f),
            )
        }
        rotate(degrees = 15f, pivot = Offset(w * 0.54f, h * 0.43f)) {
            drawOval(
                color = tint.copy(alpha = 0.94f),
                topLeft = Offset(w * 0.40f, h * 0.34f),
                size = Size(w * 0.37f, h * 0.17f),
            )
        }
        rotate(degrees = 48f, pivot = Offset(w * 0.42f, h * 0.59f)) {
            drawOval(
                color = tint.copy(alpha = 0.88f),
                topLeft = Offset(w * 0.30f, h * 0.51f),
                size = Size(w * 0.34f, h * 0.16f),
            )
        }

        drawOval(
            color = tint.copy(alpha = 0.93f),
            topLeft = Offset(w * 0.14f, h * 0.49f),
            size = Size(w * 0.26f, h * 0.36f),
        )
    }
}
