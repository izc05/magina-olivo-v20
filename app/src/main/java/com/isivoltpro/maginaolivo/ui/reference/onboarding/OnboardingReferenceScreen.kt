package com.isivoltpro.maginaolivo.ui.reference.onboarding

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.isivoltpro.maginaolivo.ui.brand.MaginaOlivoWordmark
import com.isivoltpro.maginaolivo.ui.components.MoPrimaryButton
import com.isivoltpro.maginaolivo.ui.theme.MoCream
import com.isivoltpro.maginaolivo.ui.theme.MoOlivePrimary
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing
import com.isivoltpro.maginaolivo.ui.theme.MoTextSecondary

private data class OnboardingPage(
    val title: String,
    val body: String,
    val kind: OnboardingArtworkKind,
)

private val pages = listOf(
    OnboardingPage(
        title = "Bienvenido a\nMágina Olivo",
        body = "Tu app para gestionar tu olivar de forma clara, sencilla y pensada para el agricultor.",
        kind = OnboardingArtworkKind.Welcome,
    ),
    OnboardingPage(
        title = "Tus fincas y parcelas",
        body = "Organiza tus fincas, parcelas y campañas en un solo lugar, con datos claros y acceso rápido.",
        kind = OnboardingArtworkKind.Farms,
    ),
    OnboardingPage(
        title = "Mapa y Catastro",
        body = "Importa parcelas desde Catastro, dibuja límites y consulta tu terreno de forma visual.",
        kind = OnboardingArtworkKind.Map,
    ),
    OnboardingPage(
        title = "Actividad y campaña",
        body = "Registra actuaciones, fotos, costes, fechas y el seguimiento de tu campaña paso a paso.",
        kind = OnboardingArtworkKind.Activity,
    ),
    OnboardingPage(
        title = "Cosecha, gastos\ny documentos",
        body = "Controla kilos, entregas, rendimientos, gastos y guarda toda tu documentación en orden.",
        kind = OnboardingArtworkKind.Harvest,
    ),
    OnboardingPage(
        title = "Tiempo, mercado\ny alertas",
        body = "Consulta previsión, radar, precios del aceite y avisos útiles para tomar mejores decisiones.",
        kind = OnboardingArtworkKind.Weather,
    ),
)

@Composable
fun OnboardingReferenceScreen(
    modifier: Modifier = Modifier,
    onFinished: () -> Unit = {},
) {
    var pageIndex by remember { mutableIntStateOf(0) }
    val page = pages[pageIndex]
    val isLast = pageIndex == pages.lastIndex

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MoCream)
            .testTag("onboarding-root")
            .padding(horizontal = MoSpacing.screen),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = MoSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            MaginaOlivoWordmark(compact = true)
            TextButton(
                onClick = onFinished,
                modifier = Modifier.testTag("onboarding-skip"),
            ) {
                Text(
                    text = "Saltar",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MoOlivePrimary,
                )
            }
        }

        Spacer(Modifier.height(MoSpacing.lg))

        Text(
            text = page.title,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(MoSpacing.sm))

        Text(
            text = page.body,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodyLarge,
            color = MoTextSecondary,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(MoSpacing.lg))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            OnboardingArtwork(
                kind = page.kind,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("onboarding-artwork-$pageIndex"),
            )
        }

        PageDots(
            pageCount = pages.size,
            selectedPage = pageIndex,
        )

        Spacer(Modifier.height(MoSpacing.md))

        MoPrimaryButton(
            text = if (isLast) "Comenzar" else "Siguiente",
            onClick = {
                if (isLast) {
                    onFinished()
                } else {
                    pageIndex += 1
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("onboarding-primary"),
        )

        Spacer(Modifier.height(MoSpacing.lg))
    }
}

@Composable
private fun PageDots(
    pageCount: Int,
    selectedPage: Int,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(pageCount) { index ->
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(
                        if (index == selectedPage) {
                            MoOlivePrimary
                        } else {
                            MoOlivePrimary.copy(alpha = 0.18f)
                        },
                    )
                    .size(if (index == selectedPage) 10.dp else 8.dp),
            )
        }
    }
}
