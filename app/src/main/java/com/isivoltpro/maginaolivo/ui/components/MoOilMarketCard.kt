package com.isivoltpro.maginaolivo.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.isivoltpro.maginaolivo.ui.theme.OlivarColors
import com.isivoltpro.maginaolivo.ui.theme.OlivarDimens

data class MoMarketPrice(
    val label: String,
    val value: String,
)

@Composable
fun MoOilMarketCard(
    prices: List<MoMarketPrice>,
    source: String,
    updatedText: String,
    modifier: Modifier = Modifier,
    freshness: MoFreshnessState = MoFreshnessState.Fresh,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(OlivarDimens.SpaceMd)) {
            Text("Mercado del aceite", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = OlivarDimens.SpaceMd),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                prices.take(3).forEach { price ->
                    Column {
                        Text(
                            price.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            price.value,
                            style = MaterialTheme.typography.titleLarge,
                            color = OlivarColors.Olive900,
                        )
                    }
                }
            }
            MoSourceFreshness(
                source = source,
                updatedText = updatedText,
                state = freshness,
            )
        }
    }
}
