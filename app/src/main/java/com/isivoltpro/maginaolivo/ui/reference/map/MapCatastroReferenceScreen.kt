package com.isivoltpro.maginaolivo.ui.reference.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.isivoltpro.maginaolivo.ui.components.MoPrimaryButton
import com.isivoltpro.maginaolivo.ui.theme.MoCream
import com.isivoltpro.maginaolivo.ui.theme.MoMapBase
import com.isivoltpro.maginaolivo.ui.theme.MoMapContour
import com.isivoltpro.maginaolivo.ui.theme.MoOliveDark
import com.isivoltpro.maginaolivo.ui.theme.MoOlivePrimary
import com.isivoltpro.maginaolivo.ui.theme.MoOutline
import com.isivoltpro.maginaolivo.ui.theme.MoSage
import com.isivoltpro.maginaolivo.ui.theme.MoShape
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing
import com.isivoltpro.maginaolivo.ui.theme.MoSurfaceSoft
import com.isivoltpro.maginaolivo.ui.theme.MoTextSecondary
import com.isivoltpro.maginaolivo.ui.theme.MoWarmWhite

@Composable
fun MapCatastroReferenceScreen(
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("map-catastro-reference-root"),
        containerColor = MoCream,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding(),
        ) {
            Column(
                modifier = Modifier.padding(
                    start = MoSpacing.screen,
                    end = MoSpacing.screen,
                    top = MoSpacing.md,
                ),
            ) {
                Text(
                    text = "Mapa y Catastro",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MoOliveDark,
                )
                Text(
                    text = "Localiza, revisa e incorpora parcelas a tu olivar.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MoTextSecondary,
                )
                Spacer(Modifier.height(MoSpacing.sm))
                SearchReferenceBar()
                Spacer(Modifier.height(MoSpacing.sm))
                MapLegend()
            }

            Spacer(Modifier.height(MoSpacing.sm))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(MoMapBase),
            ) {
                ParcelMapFixture(
                    modifier = Modifier.fillMaxSize(),
                )

                MapControls(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = MoSpacing.md),
                )

                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(MoSpacing.md)
                        .fillMaxWidth(),
                    shape = MoShape.cardLarge,
                    colors = CardDefaults.cardColors(containerColor = MoWarmWhite),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(MoSpacing.md),
                        verticalArrangement = Arrangement.spacedBy(MoSpacing.sm),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top,
                        ) {
                            Column {
                                Text(
                                    text = "Parcela encontrada",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MoTextSecondary,
                                )
                                Text(
                                    text = "Polígono 12 · Parcela 48",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MoOliveDark,
                                )
                                Text(
                                    text = "2,34 ha · Huelma, Jaén",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MoTextSecondary,
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = MoOlivePrimary.copy(alpha = 0.11f),
                            ) {
                                Text(
                                    text = "Catastro",
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MoOlivePrimary,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }

                        Text(
                            text = "Geometría y datos mostrados solo como maqueta visual.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MoTextSecondary,
                        )

                        MoPrimaryButton(
                            text = "Incorporar a una finca",
                            onClick = {},
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchReferenceBar() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MoWarmWhite),
        border = BorderStroke(1.dp, MoOutline),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = MoSpacing.md, vertical = MoSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MoSpacing.sm),
        ) {
            Text(
                text = "⌕",
                style = MaterialTheme.typography.titleLarge,
                color = MoOlivePrimary,
            )
            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = "Buscar por referencia catastral",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MoOliveDark,
                )
                Text(
                    text = "También podrás seleccionar sobre el mapa",
                    style = MaterialTheme.typography.labelMedium,
                    color = MoTextSecondary,
                )
            }
        }
    }
}

@Composable
private fun MapLegend() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MoSpacing.sm),
    ) {
        LegendItem(
            label = "Catastro visible",
            color = MoMapContour,
        )
        LegendItem(
            label = "Seleccionada",
            color = MoOlivePrimary,
        )
        LegendItem(
            label = "Ya guardada",
            color = MoOliveDark,
        )
    }
}

@Composable
private fun LegendItem(
    label: String,
    color: Color,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .background(color, CircleShape),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MoTextSecondary,
        )
    }
}

@Composable
private fun ParcelMapFixture(
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        drawRect(MoMapBase)

        repeat(10) { row ->
            repeat(8) { col ->
                val x = w * (0.04f + col * 0.135f)
                val y = h * (0.05f + row * 0.095f)
                drawCircle(
                    color = if ((row + col) % 3 == 0) {
                        MoOliveDark.copy(alpha = 0.42f)
                    } else {
                        MoOlivePrimary.copy(alpha = 0.30f)
                    },
                    radius = 5.5f,
                    center = Offset(x, y),
                )
            }
        }

        val selected = Path().apply {
            moveTo(w * 0.18f, h * 0.18f)
            lineTo(w * 0.59f, h * 0.13f)
            lineTo(w * 0.73f, h * 0.39f)
            lineTo(w * 0.62f, h * 0.64f)
            lineTo(w * 0.28f, h * 0.59f)
            lineTo(w * 0.13f, h * 0.35f)
            close()
        }
        drawPath(
            path = selected,
            color = MoOlivePrimary.copy(alpha = 0.32f),
        )
        drawPath(
            path = selected,
            color = MoOlivePrimary,
            style = Stroke(width = 5f),
        )

        val saved = Path().apply {
            moveTo(w * 0.60f, h * 0.14f)
            lineTo(w * 0.88f, h * 0.20f)
            lineTo(w * 0.91f, h * 0.48f)
            lineTo(w * 0.74f, h * 0.53f)
            lineTo(w * 0.68f, h * 0.36f)
            close()
        }
        drawPath(
            path = saved,
            color = MoOliveDark.copy(alpha = 0.08f),
        )
        drawPath(
            path = saved,
            color = MoOliveDark,
            style = Stroke(width = 4f),
        )

        repeat(5) { index ->
            val y = h * (0.18f + index * 0.13f)
            drawLine(
                color = MoMapContour.copy(alpha = 0.65f),
                start = Offset(w * 0.03f, y),
                end = Offset(w * 0.95f, y + h * 0.04f),
                strokeWidth = 1.5f,
            )
        }
    }
}

@Composable
private fun MapControls(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(MoSpacing.xs),
    ) {
        MapControl("＋")
        MapControl("−")
        MapControl("◎")
        MapControl("▱")
    }
}

@Composable
private fun MapControl(symbol: String) {
    Surface(
        modifier = Modifier.size(44.dp),
        shape = CircleShape,
        color = MoWarmWhite,
        border = BorderStroke(1.dp, MoOutline),
        shadowElevation = 2.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = symbol,
                style = MaterialTheme.typography.titleMedium,
                color = MoOliveDark,
            )
        }
    }
}
