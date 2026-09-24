package com.isivoltpro.maginaolivo.feature.maps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.isivoltpro.maginaolivo.app.LocalPersistence
import com.isivoltpro.maginaolivo.ui.components.MoIcons
import com.isivoltpro.maginaolivo.ui.components.MoLabeledValue
import com.isivoltpro.maginaolivo.ui.components.MoPrimaryButton
import com.isivoltpro.maginaolivo.ui.components.MoSectionCard
import com.isivoltpro.maginaolivo.ui.components.MoTertiaryButton
import com.isivoltpro.maginaolivo.ui.theme.MoCream
import com.isivoltpro.maginaolivo.ui.theme.MoOliveDark
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing
import com.isivoltpro.maginaolivo.ui.theme.MoTextSecondary
import java.text.NumberFormat
import java.util.Locale
import java.util.UUID

/**
 * Phase 18 — every Parcel of a Farm with a saved boundary, drawn from the phone's own copy.
 * Aerial imagery is optional and needs a connection; the boundaries never do.
 */
@Composable
fun FarmMapRoute(farmId: UUID, persistence: LocalPersistence, onOpenParcel: (UUID) -> Unit, onImport: () -> Unit) {
    val flow = remember(farmId) { persistence.parcelRepository.observeActive(farmId) }
    val parcels by flow.collectAsStateWithLifecycle(initialValue = emptyList())
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var imagery by rememberSaveable { mutableStateOf(false) }
    val mapped = remember(parcels) { parcels.mapNotNull { p -> p.geometryGeoJson?.let { MapParcel(p.id.toString(), p.displayName, it) } } }
    Surface(color = MoCream, modifier = Modifier.fillMaxSize().testTag("farm-map-root")) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = MoSpacing.screen),
            verticalArrangement = Arrangement.spacedBy(MoSpacing.xs),
        ) {
            Spacer(Modifier.height(MoSpacing.sm))
            Text("Mapa de la finca", style = MaterialTheme.typography.headlineLarge, color = MoOliveDark)
            Text(
                "${mapped.size} con límites · ${parcels.size - mapped.size} sin límites guardados",
                style = MaterialTheme.typography.bodyMedium,
                color = MoTextSecondary,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MoTertiaryButton(if (imagery) "Quitar foto aérea" else "Foto aérea (con conexión)", { imagery = !imagery })
                MoTertiaryButton("Importar de Catastro", onImport)
            }
            ParcelMap(mapped, Modifier.weight(1f).fillMaxWidth(), imagery, selected, onSelected = { selected = it })
            val parcel = parcels.firstOrNull { it.id.toString() == selected }
            if (parcel != null) {
                MoSectionCard(title = parcel.displayName, icon = MoIcons.Parcels) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MoSpacing.sm)) {
                        MoLabeledValue("Superficie catastral", parcel.cadastralAreaM2?.let(::hectares), Modifier.weight(1f))
                        MoLabeledValue("Superficie gestionada", parcel.managedAreaM2?.let(::hectares), Modifier.weight(1f))
                    }
                    MoPrimaryButton("Abrir parcela", { onOpenParcel(parcel.id) }, Modifier.fillMaxWidth())
                }
            } else {
                Text(
                    if (mapped.isEmpty()) "Importa una parcela de Catastro para ver sus límites." else "Toca una parcela para ver sus datos.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MoTextSecondary,
                )
            }
            Spacer(Modifier.height(MoSpacing.sm))
        }
    }
}

private fun hectares(areaM2: Double): String =
    "${NumberFormat.getNumberInstance(Locale.forLanguageTag("es-ES")).apply { maximumFractionDigits = 2 }.format(areaM2 / 10_000)} ha"
