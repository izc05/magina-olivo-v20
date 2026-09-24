package com.isivoltpro.maginaolivo.feature.maps

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.isivoltpro.maginaolivo.app.LocalPersistence
import com.isivoltpro.maginaolivo.ui.theme.MoCream
import java.util.UUID

@Composable
fun FarmMapRoute(farmId: UUID, persistence: LocalPersistence, onOpenParcel: (UUID) -> Unit, onImport: () -> Unit) {
    val flow = remember(farmId) { persistence.parcelRepository.observeActive(farmId) }
    val parcels by flow.collectAsStateWithLifecycle(initialValue = emptyList())
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var imagery by rememberSaveable { mutableStateOf(false) }
    val mapped = remember(parcels) { parcels.mapNotNull { p -> p.geometryGeoJson?.let { MapParcel(p.id.toString(), p.displayName, it) } } }
    Surface(color = MoCream) {
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            Text("Mapa de la finca", style = MaterialTheme.typography.headlineMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { imagery = !imagery }) { Text(if (imagery) "Usar mapa local" else "Activar fotografía aérea") }
                TextButton(onClick = onImport) { Text("Importar") }
            }
            Text("${mapped.size} parcelas con límites · ${parcels.size - mapped.size} sin geometría")
            ParcelMap(mapped, Modifier.weight(1f).fillMaxWidth(), imagery, selected, onSelected = { selected = it })
            val parcel = parcels.firstOrNull { it.id.toString() == selected }
            if (parcel != null) {
                Text(parcel.displayName, style = MaterialTheme.typography.titleLarge)
                Text("Catastral: ${parcel.cadastralAreaM2?.let { "$it m²" } ?: "Sin registrar"}")
                Text("Gestionada: ${parcel.managedAreaM2?.let { "$it m²" } ?: "Sin registrar"}")
                Button(onClick = { onOpenParcel(parcel.id) }) { Text("Abrir parcela") }
            } else Text(if (mapped.isEmpty()) "Importa una parcela para ver sus límites." else "Toca un contorno para seleccionar su parcela.")
        }
    }
}
