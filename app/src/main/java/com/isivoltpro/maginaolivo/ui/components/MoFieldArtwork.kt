package com.isivoltpro.maginaolivo.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics

/**
 * UI polish v2: the placeholder for a Farm without a photo. A soft, illustrated olive
 * grove — sky, Sierra outline, a field with rows of olive trees and parcel lines — in the
 * brand palette, so a new farm looks like a farm instead of an empty box with an icon.
 * Purely decorative: it carries no data and is hidden from accessibility services.
 * [seed] varies the composition slightly so two farms do not look identical.
 */
@Composable
fun MoFieldArtwork(modifier: Modifier = Modifier, seed: Int = 0) {
    Canvas(modifier.clearAndSetSemantics { }) {
        val w = size.width
        val h = size.height
        val shift = ((seed % 7) - 3) / 40f
        drawRect(Brush.verticalGradient(listOf(Color(0xFFF1EEDF), Color(0xFFE6E7D2))))

        // Sierra outline.
        val sierra = Path().apply {
            moveTo(0f, h * 0.46f)
            cubicTo(w * (0.18f + shift), h * 0.30f, w * 0.30f, h * 0.36f, w * 0.44f, h * 0.28f)
            cubicTo(w * 0.58f, h * 0.20f, w * (0.70f + shift), h * 0.34f, w, h * 0.30f)
            lineTo(w, h * 0.56f)
            lineTo(0f, h * 0.56f)
            close()
        }
        drawPath(sierra, Color(0xFFC9CBB0))

        // Field.
        val field = Path().apply {
            moveTo(0f, h * 0.52f)
            cubicTo(w * 0.35f, h * 0.46f, w * 0.65f, h * 0.50f, w, h * 0.47f)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(field, Brush.verticalGradient(listOf(Color(0xFFD7D2B0), Color(0xFFCBC39E)), startY = h * 0.47f, endY = h))

        // Parcel boundaries.
        val boundary = Color(0xFFF7F4E8).copy(alpha = 0.7f)
        drawLine(boundary, Offset(w * (0.42f + shift), h * 0.50f), Offset(w * (0.30f + shift), h), strokeWidth = 2f)
        drawLine(boundary, Offset(w * 0.78f, h * 0.48f), Offset(w * 0.92f, h), strokeWidth = 2f)

        // Rows of olive trees, smaller towards the horizon.
        val rows = 4
        for (r in 0 until rows) {
            val t = r / (rows - 1f)
            val y = h * (0.56f + 0.34f * t * t + 0.06f * t)
            val radius = h * (0.030f + 0.035f * t)
            val count = 9 - r
            for (c in 0 until count) {
                val x = w * ((c + 0.5f + (r % 2) * 0.35f) / count)
                drawOval(Color(0xFF8E8A6A).copy(alpha = 0.35f), Offset(x - radius, y + radius * 0.55f), Size(radius * 2f, radius * 0.6f))
                drawLine(Color(0xFF6E5B45), Offset(x, y + radius * 0.2f), Offset(x, y + radius * 0.8f), strokeWidth = radius * 0.22f)
                drawCircle(Color(0xFF6F8A55), radius, Offset(x, y))
                drawCircle(Color(0xFF8FA36F).copy(alpha = 0.7f), radius * 0.55f, Offset(x - radius * 0.3f, y - radius * 0.3f))
            }
        }
        drawRect(Color(0xFF3E5A32).copy(alpha = 0.05f), style = Stroke(width = 1f))
    }
}
