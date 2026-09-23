package com.isivoltpro.maginaolivo.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.isivoltpro.maginaolivo.ui.theme.MoInfoText
import com.isivoltpro.maginaolivo.ui.theme.MoInk
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.isivoltpro.maginaolivo.ui.theme.MoOliveDark
import com.isivoltpro.maginaolivo.ui.theme.MoOutline
import com.isivoltpro.maginaolivo.ui.theme.MoShape
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing
import com.isivoltpro.maginaolivo.ui.theme.MoTextSecondary
import com.isivoltpro.maginaolivo.ui.theme.MoWarmWhite
import com.isivoltpro.maginaolivo.ui.theme.MoOliveMid
import com.isivoltpro.maginaolivo.ui.theme.MoOliveTint

/**
 * UI polish v2: a Farm card that reads as a real place — photo (or the olive-grove
 * artwork when there is none), name, location, area, parcels, campaign state and the
 * next planned work when it exists. Values that are not known are said in words.
 */
@Composable
fun MoFarmCard(
    name: String,
    municipality: String,
    area: String,
    parcels: String,
    campaignStatus: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    imageModel: Any? = null,
    nextWork: String? = null,
    campaignActive: Boolean = true,
    artworkSeed: Int = 0,
) {
    Card(
        modifier = modifier.then(
            if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
        ),
        shape = MoShape.card,
        colors = CardDefaults.cardColors(containerColor = MoWarmWhite),
        border = BorderStroke(1.dp, MoOutline),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2.6f),
            ) {
                MoFieldArtwork(Modifier.matchParentSize(), seed = artworkSeed)
                if (imageModel != null) {
                    AsyncImage(
                        model = imageModel,
                        contentDescription = "Fotografía de $name",
                        modifier = Modifier.matchParentSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
                Surface(
                    modifier = Modifier.align(Alignment.TopEnd).padding(MoSpacing.xs),
                    shape = CircleShape,
                    color = if (campaignActive) MoOliveTint else MoWarmWhite,
                    contentColor = if (campaignActive) MoOliveMid else MoTextSecondary,
                ) {
                    Text(
                        text = campaignStatus,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            Column(
                modifier = Modifier.padding(horizontal = MoSpacing.md, vertical = MoSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleLarge.copy(fontFamily = MaterialTheme.typography.headlineMedium.fontFamily),
                    color = MoOliveDark,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(MoIcons.Location, contentDescription = null, tint = MoTextSecondary, modifier = Modifier.size(15.dp))
                    Text(
                        text = municipality,
                        style = MaterialTheme.typography.bodySmall,
                        color = MoTextSecondary,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(MoSpacing.lg),
                ) {
                    FarmMetric(value = area, label = "Superficie")
                    FarmMetric(value = parcels, label = "Parcelas")
                }
                if (nextWork != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(MoIcons.Calendar, contentDescription = null, tint = MoInfoText, modifier = Modifier.size(15.dp))
                        Text(nextWork, style = MaterialTheme.typography.bodySmall, color = MoInfoText)
                    }
                }
            }
        }
    }
}

@Composable
private fun FarmMetric(
    value: String,
    label: String,
) {
    Column {
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = MoInk,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MoTextSecondary,
        )
    }
}
