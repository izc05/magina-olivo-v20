package com.isivoltpro.maginaolivo.ui.brand

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.isivoltpro.maginaolivo.R

@Composable
fun MaginaOlivoWordmark(
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Image(
        painter = painterResource(R.drawable.brand_official_wordmark),
        contentDescription = "Mágina Olivo",
        contentScale = ContentScale.Fit,
        modifier = modifier
            .height(if (compact) 42.dp else 58.dp)
            .aspectRatio(1301f / 374f),
    )
}

@Composable
fun OliveMark(
    modifier: Modifier = Modifier,
) {
    Image(
        painter = painterResource(R.drawable.brand_official_mark),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier,
    )
}
