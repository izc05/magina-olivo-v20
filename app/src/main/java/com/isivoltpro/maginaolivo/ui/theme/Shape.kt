package com.isivoltpro.maginaolivo.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val MoShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

object MoShape {
    val field = RoundedCornerShape(16.dp)
    val card = RoundedCornerShape(20.dp)
    val cardLarge = RoundedCornerShape(24.dp)
    val hero = RoundedCornerShape(28.dp)
    val pill = RoundedCornerShape(999.dp)
}
