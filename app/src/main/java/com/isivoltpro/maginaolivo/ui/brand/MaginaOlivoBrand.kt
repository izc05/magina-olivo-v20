package com.isivoltpro.maginaolivo.ui.brand

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.isivoltpro.maginaolivo.R
import com.isivoltpro.maginaolivo.ui.theme.MoOliveDark
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
) {
    Image(
        painter = painterResource(R.drawable.brand_olive_mark),
        contentDescription = null,
        modifier = modifier.aspectRatio(1f),
    )
}
