package com.isivoltpro.maginaolivo.ui.reference.onboarding

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.isivoltpro.maginaolivo.ui.brand.OliveMark
import com.isivoltpro.maginaolivo.R
import com.isivoltpro.maginaolivo.ui.theme.MoArtworkMapBase
import com.isivoltpro.maginaolivo.ui.theme.MoArtworkMapGround
import com.isivoltpro.maginaolivo.ui.theme.MoCream
import com.isivoltpro.maginaolivo.ui.theme.MoOliveDark
import com.isivoltpro.maginaolivo.ui.theme.MoOlivePrimary
import com.isivoltpro.maginaolivo.ui.theme.MoOutline
import com.isivoltpro.maginaolivo.ui.theme.MoSage
import com.isivoltpro.maginaolivo.ui.theme.MoShape
import com.isivoltpro.maginaolivo.ui.theme.MoSoftGold
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing
import com.isivoltpro.maginaolivo.ui.theme.MoSurfaceSoft
import com.isivoltpro.maginaolivo.ui.theme.MoTextSecondary
import com.isivoltpro.maginaolivo.ui.theme.MoWarmWhite

enum class OnboardingArtworkKind {
    Welcome,
    Farms,
    Map,
    Activity,
    Harvest,
    Weather,
}

@Composable
fun OnboardingArtwork(
    kind: OnboardingArtworkKind,
    modifier: Modifier = Modifier,
) {
    when (kind) {
        OnboardingArtworkKind.Welcome -> WelcomeArtwork(modifier)
        OnboardingArtworkKind.Farms -> FarmsArtwork(modifier)
        OnboardingArtworkKind.Map -> MapArtwork(modifier)
        OnboardingArtworkKind.Activity -> ActivityArtwork(modifier)
        OnboardingArtworkKind.Harvest -> HarvestArtwork(modifier)
        OnboardingArtworkKind.Weather -> WeatherArtwork(modifier)
    }
}

@Composable
private fun WelcomeArtwork(modifier: Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.onboarding_welcome_olive_grove),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0.0f to MoCream,
                    0.17f to MoCream.copy(alpha = 0.72f),
                    0.35f to MoCream.copy(alpha = 0.05f),
                    0.68f to MoCream.copy(alpha = 0.05f),
                    1.0f to MoCream,
                ),
            ),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = MoSpacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Más que olivos, nuestra tierra",
                style = MaterialTheme.typography.titleMedium,
                color = MoOliveDark,
            )
        }
    }
}

@Composable
private fun FarmsArtwork(modifier: Modifier) {
    Column(
        modifier = modifier.padding(horizontal = MoSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(MoSpacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(0.92f),
            shape = MoShape.cardLarge,
            colors = CardDefaults.cardColors(containerColor = MoWarmWhite),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Row(
                modifier = Modifier.padding(MoSpacing.md),
                horizontalArrangement = Arrangement.spacedBy(MoSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OlivePhotoPlaceholder(
                    modifier = Modifier.size(90.dp),
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "FINCA",
                        style = MaterialTheme.typography.labelMedium,
                        color = MoOlivePrimary,
                    )
                    Text(
                        text = "La Solana",
                        style = MaterialTheme.typography.titleLarge,
                        color = MoOliveDark,
                    )
                    Text(
                        text = "Huelma, Jaén",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MoTextSecondary,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(MoSpacing.md)) {
                        MetricMini("42,6 ha", "Superficie")
                        MetricMini("3", "Parcelas")
                    }
                }
            }
        }

        listOf(
            Triple("Parcela Norte", "12,4 ha", "Picual"),
            Triple("Parcela Central", "18,6 ha", "Picual"),
            Triple("Parcela Sur", "11,6 ha", "Hojiblanca"),
        ).forEach { item ->
            Card(
                modifier = Modifier.fillMaxWidth(0.78f),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MoWarmWhite),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = MoSpacing.md, vertical = MoSpacing.sm),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(item.first, style = MaterialTheme.typography.titleMedium)
                        Text(item.third, style = MaterialTheme.typography.labelMedium, color = MoTextSecondary)
                    }
                    Text(item.second, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun MapArtwork(modifier: Modifier) {
    Card(
        modifier = modifier.padding(horizontal = MoSpacing.sm, vertical = MoSpacing.xs),
        shape = MoShape.hero,
        colors = CardDefaults.cardColors(containerColor = MoArtworkMapBase),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Box(Modifier.fillMaxSize()) {
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                drawRect(MoArtworkMapGround)
                repeat(9) { row ->
                    repeat(7) { col ->
                        drawCircle(
                            color = if ((row + col) % 3 == 0) MoOliveDark.copy(alpha = 0.65f) else MoOlivePrimary.copy(alpha = 0.55f),
                            radius = 6f,
                            center = Offset(
                                w * (0.08f + col * 0.14f),
                                h * (0.08f + row * 0.105f),
                            ),
                        )
                    }
                }

                val parcel = Path().apply {
                    moveTo(w * 0.26f, h * 0.20f)
                    lineTo(w * 0.65f, h * 0.14f)
                    lineTo(w * 0.79f, h * 0.46f)
                    lineTo(w * 0.62f, h * 0.75f)
                    lineTo(w * 0.28f, h * 0.67f)
                    lineTo(w * 0.17f, h * 0.40f)
                    close()
                }
                drawPath(parcel, MoOlivePrimary.copy(alpha = 0.46f))
                drawPath(
                    path = parcel,
                    color = MoWarmWhite,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 5f),
                )
            }

            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(MoSpacing.md)
                    .fillMaxWidth(0.88f),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MoWarmWhite),
            ) {
                Row(
                    modifier = Modifier.padding(MoSpacing.md),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("Parcela seleccionada", style = MaterialTheme.typography.labelMedium, color = MoTextSecondary)
                        Text("2,34 ha", style = MaterialTheme.typography.titleLarge, color = MoOliveDark)
                        Text("Pol. 12 · Parc. 48", style = MaterialTheme.typography.bodyMedium, color = MoTextSecondary)
                    }
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(MoOlivePrimary.copy(alpha = 0.12f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("⌖", color = MoOlivePrimary, style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityArtwork(modifier: Modifier) {
    Box(
        modifier = modifier.padding(horizontal = MoSpacing.sm),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(0.78f),
            shape = MoShape.cardLarge,
            colors = CardDefaults.cardColors(containerColor = MoWarmWhite),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Column(
                modifier = Modifier.padding(MoSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(MoSpacing.sm),
            ) {
                Text("Campaña 2026/27", style = MaterialTheme.typography.titleLarge)
                ActivityRow("Labores de campo", true)
                ActivityRow("Fotos", true)
                ActivityRow("Costes", false)
                ActivityRow("Fechas", false)
                ActivityRow("Seguimiento", false)
            }
        }

        Card(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .width(116.dp)
                .padding(bottom = MoSpacing.md),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MoWarmWhite),
        ) {
            Column(
                modifier = Modifier.padding(MoSpacing.sm),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("NOV", style = MaterialTheme.typography.labelMedium, color = MoTextSecondary)
                Text("14", style = MaterialTheme.typography.headlineMedium, color = MoOlivePrimary)
                Text("Actuación", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun HarvestArtwork(modifier: Modifier) {
    Column(
        modifier = modifier.padding(horizontal = MoSpacing.xs),
        verticalArrangement = Arrangement.spacedBy(MoSpacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MoSpacing.sm),
        ) {
            OnboardingMetricCard(
                label = "Producción total",
                value = "12.450 kg",
                modifier = Modifier.weight(1f),
            )
            OnboardingMetricCard(
                label = "Rendimiento medio",
                value = "18,3 %",
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MoSpacing.sm),
        ) {
            OnboardingMetricCard(
                label = "Gastos",
                value = "3.120 €",
                modifier = Modifier.weight(1f),
            )
            OnboardingMetricCard(
                label = "Entregas",
                value = "5",
                modifier = Modifier.weight(1f),
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MoShape.card,
            colors = CardDefaults.cardColors(containerColor = MoWarmWhite),
        ) {
            Column(
                modifier = Modifier.padding(MoSpacing.md),
                verticalArrangement = Arrangement.spacedBy(MoSpacing.sm),
            ) {
                Text("Mis documentos", style = MaterialTheme.typography.titleMedium)
                DocumentRow("Albarán cooperativa.pdf")
                DocumentRow("Gastos campaña.xlsx")
                DocumentRow("Contrato finca.pdf")
            }
        }
    }
}

@Composable
private fun WeatherArtwork(modifier: Modifier) {
    Column(
        modifier = modifier.padding(horizontal = MoSpacing.xs),
        verticalArrangement = Arrangement.spacedBy(MoSpacing.sm),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MoShape.cardLarge,
            colors = CardDefaults.cardColors(containerColor = MoWarmWhite),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(MoSpacing.md),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Previsión en tu zona", style = MaterialTheme.typography.titleMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("☀", color = MoSoftGold, style = MaterialTheme.typography.headlineLarge)
                        Text(" 18°C", style = MaterialTheme.typography.headlineLarge, color = MoOliveDark)
                    }
                    Text("Cielo despejado", style = MaterialTheme.typography.bodyMedium, color = MoTextSecondary)
                }
                Text("Huelma\nJaén", style = MaterialTheme.typography.bodyMedium, color = MoTextSecondary)
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MoShape.card,
            colors = CardDefaults.cardColors(containerColor = MoWarmWhite),
        ) {
            Column(
                modifier = Modifier.padding(MoSpacing.md),
                verticalArrangement = Arrangement.spacedBy(MoSpacing.sm),
            ) {
                Text("Mercado del aceite", style = MaterialTheme.typography.titleMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    MarketValue("AOVE", "4,32 €/kg")
                    MarketValue("Virgen", "3,89 €/kg")
                    MarketValue("Lampante", "3,12 €/kg")
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MoShape.card,
            colors = CardDefaults.cardColors(containerColor = MoSurfaceSoft),
        ) {
            Row(
                modifier = Modifier.padding(MoSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MoSpacing.sm),
            ) {
                Text("!", color = MoOlivePrimary, style = MaterialTheme.typography.titleLarge)
                Column {
                    Text("Alertas útiles", style = MaterialTheme.typography.titleMedium)
                    Text("Aviso meteorológico · lluvias previstas", style = MaterialTheme.typography.bodyMedium, color = MoTextSecondary)
                }
            }
        }
    }
}

@Composable
private fun OlivePhotoPlaceholder(modifier: Modifier) {
    Image(
        painter = painterResource(R.drawable.onboarding_welcome_olive_grove),
        contentDescription = null,
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, MoOutline, RoundedCornerShape(16.dp)),
        contentScale = ContentScale.Crop,
    )
}

@Composable
private fun MetricMini(value: String, label: String) {
    Column {
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MoTextSecondary)
    }
}

@Composable
private fun ActivityRow(label: String, complete: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(
                    if (complete) MoOlivePrimary.copy(alpha = 0.16f) else MoSurfaceSoft,
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (complete) {
                Text("✓", color = MoOlivePrimary, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun OnboardingMetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = MoShape.card,
        colors = CardDefaults.cardColors(containerColor = MoWarmWhite),
    ) {
        Column(
            modifier = Modifier.padding(MoSpacing.md),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MoTextSecondary)
            Text(value, style = MaterialTheme.typography.titleLarge, color = MoOliveDark)
        }
    }
}

@Composable
private fun DocumentRow(name: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name, style = MaterialTheme.typography.bodyMedium)
        Text("⋮", color = MoTextSecondary)
    }
}

@Composable
private fun MarketValue(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MoTextSecondary)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}
