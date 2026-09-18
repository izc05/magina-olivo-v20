package com.isivoltpro.maginaolivo.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.isivoltpro.maginaolivo.ui.theme.OlivarColors
import com.isivoltpro.maginaolivo.ui.theme.OlivarDimens

@Composable
fun MoFarmCard(
    name: String,
    campaign: String,
    parcelCount: Int,
    areaHa: String?,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .background(
                    Brush.linearGradient(
                        listOf(
                            OlivarColors.Olive900,
                            OlivarColors.Olive700,
                            OlivarColors.Sage600,
                        ),
                    ),
                )
                .padding(OlivarDimens.SpaceMd),
        ) {
            Column(
                modifier = Modifier.align(Alignment.BottomStart),
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.headlineMedium,
                    color = OlivarColors.White,
                )
                Text(
                    text = campaign,
                    style = MaterialTheme.typography.bodyMedium,
                    color = OlivarColors.Cream100,
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(OlivarDimens.SpaceMd),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "$parcelCount parcelas",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = areaHa?.let { "$it ha" } ?: "Superficie sin registrar",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
