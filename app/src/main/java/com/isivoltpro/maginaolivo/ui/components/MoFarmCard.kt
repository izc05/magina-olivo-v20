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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.isivoltpro.maginaolivo.ui.brand.OliveMark
import com.isivoltpro.maginaolivo.ui.theme.MoOliveDark
import com.isivoltpro.maginaolivo.ui.theme.MoOutline
import com.isivoltpro.maginaolivo.ui.theme.MoSage
import com.isivoltpro.maginaolivo.ui.theme.MoShape
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing
import com.isivoltpro.maginaolivo.ui.theme.MoTextSecondary
import com.isivoltpro.maginaolivo.ui.theme.MoWarmWhite
import com.isivoltpro.maginaolivo.ui.theme.MoOliveMid
import com.isivoltpro.maginaolivo.ui.theme.MoOliveTint

@Composable
fun MoFarmCard(
    name: String,
    municipality: String,
    area: String,
    parcels: String,
    campaignStatus: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Card(
        modifier = modifier.then(
            if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
        ),
        shape = MoShape.cardLarge,
        colors = CardDefaults.cardColors(containerColor = MoWarmWhite),
        border = BorderStroke(1.dp, MoOutline),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2.15f),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    modifier = Modifier.matchParentSize(),
                    color = MoSage.copy(alpha = 0.32f),
                ) {}
                OliveMark(modifier = Modifier.size(72.dp))
            }

            Column(
                modifier = Modifier.padding(MoSpacing.md),
                verticalArrangement = Arrangement.spacedBy(MoSpacing.sm),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.titleLarge,
                            color = MoOliveDark,
                        )
                        Text(
                            text = municipality,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MoTextSecondary,
                        )
                    }

                    Surface(
                        shape = CircleShape,
                        color = MoOliveTint,
                        contentColor = MoOliveMid,
                    ) {
                        Text(
                            text = campaignStatus,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(MoSpacing.lg),
                ) {
                    FarmMetric(value = area, label = "Superficie")
                    FarmMetric(value = parcels, label = "Parcelas")
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
            style = MaterialTheme.typography.titleMedium,
            color = MoOliveDark,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MoTextSecondary,
        )
    }
}
