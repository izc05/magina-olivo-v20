package com.isivoltpro.maginaolivo.ui.reference

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.isivoltpro.maginaolivo.ui.components.MoBottomBarPreview
import com.isivoltpro.maginaolivo.ui.theme.OlivarColors

private enum class ReferenceScreen(
    val label: String,
) {
    Onboarding("Onboarding"),
    Home("Inicio"),
    OliveGrove("Mi Olivar"),
    Farm("Finca"),
    Parcel("Parcela"),
    Campaign("Campaña"),
    Production("Producción"),
    Costs("Costes"),
    Profitability("Rentabilidad"),
    Register("Registrar"),
    Calendar("Calendario"),
    Ocr("Entrega OCR"),
    InvoiceOcr("Factura OCR"),
    Profile("Perfil"),
    States("Estados"),
}

@Composable
fun Phase3GalleryScreen(
    modifier: Modifier = Modifier,
) {
    var selected by remember { mutableStateOf(ReferenceScreen.Home) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("phase3-gallery"),
    ) {
        Text(
            text = "DEV · Galería de diseño",
            style = MaterialTheme.typography.labelLarge,
            color = OlivarColors.Olive700,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )

        ScrollableTabRow(
            selectedTabIndex = selected.ordinal,
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.primary,
            edgePadding = 8.dp,
            divider = {},
        ) {
            ReferenceScreen.entries.forEach { screen ->
                Tab(
                    selected = selected == screen,
                    onClick = { selected = screen },
                    text = {
                        Text(
                            text = screen.label,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    },
                )
            }
        }

        androidx.compose.foundation.layout.Box(
            modifier = Modifier.weight(1f),
        ) {
            when (selected) {
                ReferenceScreen.Onboarding -> ReferenceOnboardingScreen(Modifier.fillMaxSize())
                ReferenceScreen.Home -> ReferenceHomeScreen(Modifier.fillMaxSize())
                ReferenceScreen.OliveGrove -> ReferenceOliveGroveScreen(Modifier.fillMaxSize())
                ReferenceScreen.Farm -> ReferenceFarmScreen(Modifier.fillMaxSize())
                ReferenceScreen.Parcel -> ReferenceParcelScreen(Modifier.fillMaxSize())
                ReferenceScreen.Campaign -> ReferenceCampaignScreen(Modifier.fillMaxSize())
                ReferenceScreen.Production -> ReferenceProductionScreen(Modifier.fillMaxSize())
                ReferenceScreen.Costs -> ReferenceCostsScreen(Modifier.fillMaxSize())
                ReferenceScreen.Profitability -> ReferenceProfitabilityScreen(Modifier.fillMaxSize())
                ReferenceScreen.Register -> ReferenceRegisterScreen(Modifier.fillMaxSize())
                ReferenceScreen.Calendar -> ReferenceCalendarScreen(Modifier.fillMaxSize())
                ReferenceScreen.Ocr -> ReferenceOcrScreen(Modifier.fillMaxSize())
                ReferenceScreen.InvoiceOcr -> ReferenceInvoiceOcrScreen(Modifier.fillMaxSize())
                ReferenceScreen.Profile -> ReferenceProfileScreen(Modifier.fillMaxSize())
                ReferenceScreen.States -> ReferenceStatesScreen(Modifier.fillMaxSize())
            }
        }

        MoBottomBarPreview(
            selected = when (selected) {
                ReferenceScreen.Onboarding,
                ReferenceScreen.Home -> "Inicio"
                ReferenceScreen.Register -> "Registrar"
                ReferenceScreen.Calendar -> "Calendario"
                ReferenceScreen.Profile -> "Perfil"
                else -> "Mi Olivar"
            },
            onSelected = { destination ->
                when (destination) {
                    "Inicio" -> selected = ReferenceScreen.Home
                    "Mi Olivar" -> selected = ReferenceScreen.OliveGrove
                    "Registrar" -> selected = ReferenceScreen.Register
                    "Calendario" -> selected = ReferenceScreen.Calendar
                    "Perfil" -> selected = ReferenceScreen.Profile
                }
            },
        )
    }
}
