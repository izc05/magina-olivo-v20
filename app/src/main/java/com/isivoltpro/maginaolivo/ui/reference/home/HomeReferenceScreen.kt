package com.isivoltpro.maginaolivo.ui.reference.home

import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.isivoltpro.maginaolivo.R
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
import com.isivoltpro.maginaolivo.ui.theme.MoSoftGoldText
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing
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
    showBottomBar: Boolean = true,
    onOlivarSelected: () -> Unit = {},
    onMapSelected: () -> Unit = {},
    onCampaignSelected: () -> Unit = {},
    onHarvestSelected: () -> Unit = {},
    onExpensesSelected: () -> Unit = {},
    onWeatherSelected: () -> Unit = {},
) {
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("home-reference-root"),
        containerColor = MoCream,
        bottomBar = {
            if (showBottomBar) {
                MoBottomBar(
                    items = rootItems,
                    selectedIndex = selectedRoot,
                    onSelected = onRootSelected,
                )
            }
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
                text = "Tu explotación, al día",
                style = MaterialTheme.typography.headlineLarge,
                color = MoOliveDark,
            )
            Text(
                text = "Tus fincas, campañas y tareas, reunidas en un lugar.",
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
                    onClick = onWeatherSelected,
                )
                CampaignSummaryCard(
                    modifier = Modifier.weight(1f),
                    onClick = onCampaignSelected,
                )
            }

            Spacer(Modifier.height(MoSpacing.md))
            QuickActions(
                onOlivarSelected = onOlivarSelected,
                onMapSelected = onMapSelected,
                onCampaignSelected = onCampaignSelected,
                onHarvestSelected = onHarvestSelected,
                onExpensesSelected = onExpensesSelected,
            )

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
    MaginaOlivoWordmark(modifier = Modifier.padding(top = MoSpacing.sm), compact = true)
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
            Image(
                painter = painterResource(R.drawable.onboarding_welcome_olive_grove),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(Color.Transparent, MoOliveDark.copy(alpha = 0.78f))))

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(MoSpacing.md),
            ) {
                Text(
                    text = "MI OLIVAR",
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
private fun WeatherSummaryCard(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = MoShape.card,
        colors = CardDefaults.cardColors(containerColor = MoWarmWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(MoSpacing.md),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Tiempo y avisos", style = MaterialTheme.typography.titleMedium, color = MoOliveDark)
            Text("Consulta la información de tu zona.", style = MaterialTheme.typography.bodyMedium, color = MoTextSecondary)
        }
    }
}

@Composable
private fun CampaignSummaryCard(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = MoShape.card,
        colors = CardDefaults.cardColors(containerColor = MoOlivePrimary),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(MoSpacing.md),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Campaña", style = MaterialTheme.typography.titleMedium, color = MoWarmWhite)
            Text("Consulta el estado y los registros de tus campañas.", style = MaterialTheme.typography.bodyMedium, color = MoWarmWhite)
        }
    }
}

@Composable
private fun QuickActions(
    onOlivarSelected: () -> Unit,
    onMapSelected: () -> Unit,
    onCampaignSelected: () -> Unit,
    onHarvestSelected: () -> Unit,
    onExpensesSelected: () -> Unit,
) {
    val actions = listOf(
        Triple("Mis fincas", "F", onOlivarSelected),
        Triple("Mapa y\nCatastro", "M", onMapSelected),
        Triple("Campaña", "C", onCampaignSelected),
        Triple("Cosecha", "O", onHarvestSelected),
        Triple("Gastos", "€", onExpensesSelected),
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        actions.forEach { (label, symbol, onClick) ->
            Card(
                onClick = onClick,
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
                text = "Los precios aparecerán aquí cuando exista una fuente conectada y verificable.",
                style = MaterialTheme.typography.bodyMedium,
                color = MoTextSecondary,
            )
        }
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
                Text("ACTUALIDAD", style = MaterialTheme.typography.labelMedium, color = MoSoftGoldText)
                Text("Avisos de tu cooperativa", style = MaterialTheme.typography.titleMedium, color = MoOliveDark)
                Text(
                    "Aquí aparecerán los avisos cuando la cooperativa esté conectada.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MoTextSecondary,
                )
            }
        }
    }
}
