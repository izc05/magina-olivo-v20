package com.isivoltpro.maginaolivo.ui.reference.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.isivoltpro.maginaolivo.ui.brand.MaginaOlivoWordmark
import com.isivoltpro.maginaolivo.ui.brand.OliveMark
import com.isivoltpro.maginaolivo.ui.components.MoBottomBar
import com.isivoltpro.maginaolivo.ui.components.MoBottomBarItem
import com.isivoltpro.maginaolivo.ui.components.MoSectionHeader
import com.isivoltpro.maginaolivo.ui.theme.MoCream
import com.isivoltpro.maginaolivo.ui.theme.MoOliveDark
import com.isivoltpro.maginaolivo.ui.theme.MoOlivePrimary
import com.isivoltpro.maginaolivo.ui.theme.MoSage
import com.isivoltpro.maginaolivo.ui.theme.MoShape
import com.isivoltpro.maginaolivo.ui.theme.MoSoftGold
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing
import com.isivoltpro.maginaolivo.ui.theme.MoSurfaceSoft
import com.isivoltpro.maginaolivo.ui.theme.MoTextSecondary
import com.isivoltpro.maginaolivo.ui.theme.MoWarmWhite

private val rootItems = listOf(
    MoBottomBarItem("Inicio", "⌂"),
    MoBottomBarItem("Mi Olivar", "♧"),
    MoBottomBarItem("Registrar", "+", isPrimaryAction = true),
    MoBottomBarItem("Calendario", "▦"),
    MoBottomBarItem("Perfil", "○"),
)

@Composable
fun HomeReferenceScreen(
    modifier: Modifier = Modifier,
    selectedRoot: Int = 0,
    onRootSelected: (Int) -> Unit = {},
) {
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("home-reference-root"),
        containerColor = MoCream,
        bottomBar = {
            MoBottomBar(
                items = rootItems,
                selectedIndex = selectedRoot,
                onSelected = onRootSelected,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(horizontal = MoSpacing.screen),
        ) {
            HomeHeader()
            Spacer(Modifier.height(MoSpacing.md))

            Text(
                text = "Buenos días",
                style = MaterialTheme.typography.headlineLarge,
                color = MoOliveDark,
            )
            Text(
                text = "Hoy es un buen día para seguir haciendo grande lo nuestro.",
                style = MaterialTheme.typography.bodyLarge,
                color = MoTextSecondary,
            )

            Spacer(Modifier.height(MoSpacing.md))
            TerritoryHero()

            Spacer(Modifier.height(MoSpacing.md))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MoSpacing.sm),
            ) {
                WeatherSummaryCard(
                    modifier = Modifier.weight(1f),
                )
                CampaignSummaryCard(
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(MoSpacing.md))
            QuickActions()

            Spacer(Modifier.height(MoSpacing.lg))
            OilMarketCard()

            Spacer(Modifier.height(MoSpacing.md))
            CooperativeNewsCard()

            Spacer(Modifier.height(MoSpacing.lg))
        }
    }
}

@Composable
private fun HomeHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = MoSpacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MaginaOlivoWordmark(compact = true)
        Box(contentAlignment = Alignment.TopEnd) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = CircleShape,
                color = MoSurfaceSoft,
                contentColor = MoOliveDark,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("♢", style = MaterialTheme.typography.titleLarge)
                }
            }
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .background(MaterialTheme.colorScheme.error, CircleShape),
            )
        }
    }
}

@Composable
private fun TerritoryHero() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(2.15f),
        shape = MoShape.hero,
        colors = CardDefaults.cardColors(containerColor = MoSage.copy(alpha = 0.40f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Box(Modifier.fillMaxSize()) {
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                drawCircle(
                    color = MoSoftGold.copy(alpha = 0.28f),
                    radius = h * 0.18f,
                    center = Offset(w * 0.76f, h * 0.28f),
                )

                val far = Path().apply {
                    moveTo(0f, h * 0.62f)
                    cubicTo(w * 0.18f, h * 0.48f, w * 0.36f, h * 0.59f, w * 0.52f, h * 0.40f)
                    cubicTo(w * 0.67f, h * 0.22f, w * 0.80f, h * 0.48f, w, h * 0.34f)
                    lineTo(w, h)
                    lineTo(0f, h)
                    close()
                }
                drawPath(far, MoSage)

                val near = Path().apply {
                    moveTo(0f, h * 0.75f)
                    cubicTo(w * 0.20f, h * 0.60f, w * 0.38f, h * 0.78f, w * 0.57f, h * 0.61f)
                    cubicTo(w * 0.76f, h * 0.49f, w * 0.84f, h * 0.70f, w, h * 0.57f)
                    lineTo(w, h)
                    lineTo(0f, h)
                    close()
                }
                drawPath(near, MoOlivePrimary.copy(alpha = 0.82f))

                repeat(11) { index ->
                    val x = w * (0.055f + index * 0.09f)
                    val y = h * (0.75f + (index % 2) * 0.08f)
                    drawOval(
                        color = MoOliveDark.copy(alpha = 0.88f),
                        topLeft = Offset(x, y),
                        size = Size(w * 0.055f, h * 0.11f),
                    )
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(MoSpacing.md),
            ) {
                Text(
                    text = "SIERRA MÁGINA · JAÉN",
                    style = MaterialTheme.typography.labelMedium,
                    color = MoWarmWhite,
                )
                Text(
                    text = "Tu olivar, de un vistazo",
                    style = MaterialTheme.typography.titleLarge,
                    color = MoWarmWhite,
                )
            }
        }
    }
}

@Composable
private fun WeatherSummaryCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = MoShape.card,
        colors = CardDefaults.cardColors(containerColor = MoWarmWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(MoSpacing.md),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("☀ 18°C", style = MaterialTheme.typography.headlineMedium, color = MoOliveDark)
            Text("Cielo despejado", style = MaterialTheme.typography.bodyMedium, color = MoTextSecondary)
            Text("Máx. 24° · Mín. 12°", style = MaterialTheme.typography.labelMedium, color = MoTextSecondary)
            Text("Huelma, Jaén", style = MaterialTheme.typography.labelMedium, color = MoOlivePrimary)
        }
    }
}

@Composable
private fun CampaignSummaryCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = MoShape.card,
        colors = CardDefaults.cardColors(containerColor = MoOlivePrimary),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(MoSpacing.md),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Campaña 2026/27", style = MaterialTheme.typography.titleMedium, color = MoWarmWhite)
            Text("Producción estimada", style = MaterialTheme.typography.labelMedium, color = MoWarmWhite.copy(alpha = 0.78f))
            Text("12.450 kg", style = MaterialTheme.typography.headlineMedium, color = MoWarmWhite)
            Text("↑ 12% · dato de referencia", style = MaterialTheme.typography.labelMedium, color = MoWarmWhite.copy(alpha = 0.86f))
        }
    }
}

@Composable
private fun QuickActions() {
    val actions = listOf(
        "Mis fincas" to "F",
        "Mapa y\nCatastro" to "M",
        "Campaña" to "C",
        "Cosecha" to "O",
        "Gastos" to "€",
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        actions.forEach { (label, symbol) ->
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MoWarmWhite),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = MoSpacing.sm, horizontal = 5.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Surface(
                        modifier = Modifier.size(34.dp),
                        shape = CircleShape,
                        color = MoOlivePrimary.copy(alpha = 0.10f),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(symbol, color = MoOlivePrimary, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MoOliveDark,
                        minLines = 2,
                        maxLines = 2,
                    )
                }
            }
        }
    }
}

@Composable
private fun OilMarketCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MoShape.cardLarge,
        colors = CardDefaults.cardColors(containerColor = MoWarmWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(MoSpacing.md),
            verticalArrangement = Arrangement.spacedBy(MoSpacing.sm),
        ) {
            MoSectionHeader(title = "Mercado del aceite")
            Text(
                text = "Datos de ejemplo para validar composición",
                style = MaterialTheme.typography.labelMedium,
                color = MoTextSecondary,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                MarketColumn("AOVE", "4,32 €/kg", "+2,1%")
                MarketColumn("Virgen", "3,89 €/kg", "+1,3%")
                MarketColumn("Lampante", "3,12 €/kg", "+0,6%")
            }
        }
    }
}

@Composable
private fun MarketColumn(label: String, value: String, trend: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        OliveMark(modifier = Modifier.size(28.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MoTextSecondary)
        Text(value, style = MaterialTheme.typography.titleMedium, color = MoOliveDark)
        Text("↑ $trend", style = MaterialTheme.typography.labelMedium, color = MoOlivePrimary)
    }
}

@Composable
private fun CooperativeNewsCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MoShape.card,
        colors = CardDefaults.cardColors(containerColor = MoWarmWhite),
    ) {
        Row(
            modifier = Modifier.padding(MoSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(MoSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .background(MoSage.copy(alpha = 0.28f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center,
            ) {
                OliveMark(modifier = Modifier.size(48.dp))
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("ACTUALIDAD", style = MaterialTheme.typography.labelMedium, color = MoSoftGold)
                Text("La cooperativa prepara la campaña", style = MaterialTheme.typography.titleMedium, color = MoOliveDark)
                Text(
                    "Avisos y noticias de tu cooperativa de referencia aparecerán aquí.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MoTextSecondary,
                )
            }
        }
    }
}
