package com.isivoltpro.maginaolivo.feature.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.isivoltpro.maginaolivo.domain.feed.*
import com.isivoltpro.maginaolivo.ui.components.*
import com.isivoltpro.maginaolivo.ui.theme.*

/** Offline INE selector shared by Inicio, Tiempo and Perfil. */
@Composable
fun WeatherLocationSelectorContent(
    currentLocation: FeedLocation?,
    farmSuggestion: FeedLocation? = null,
    isSaving: Boolean = false,
    errorMessage: String? = null,
    sheetTestTag: String = "weather-location-sheet",
    municipalityFieldTestTag: String = "weather-municipality-field",
    provinceFieldTestTag: String = "weather-province-field",
    saveButtonTestTag: String = "weather-save-location",
    onSelectMunicipality: (IneMunicipality) -> Unit,
    onClearLocation: () -> Unit,
    onSubmitFreeText: ((municipality: String, province: String) -> Unit)? = null,
    onCancel: () -> Unit,
) {
    val initial = remember(currentLocation) {
        currentLocation?.let { SpanishMunicipalityCatalog.resolve(it.municipality, it.province, it.ineCode) }
    }
    var provinceCode by rememberSaveable(currentLocation) {
        mutableStateOf(initial?.provinceCode ?: SpanishMunicipalityCatalog.resolveProvince(currentLocation?.province)?.code)
    }
    var selectedCode by rememberSaveable(currentLocation) { mutableStateOf(initial?.ineCode) }
    val selected = selectedCode?.let(SpanishMunicipalityCatalog::findByCode)
        ?.takeIf { it.provinceCode == provinceCode }
    var localError by rememberSaveable { mutableStateOf<String?>(null) }
    val suggestion = remember(farmSuggestion) {
        farmSuggestion?.let { SpanishMunicipalityCatalog.resolve(it.municipality, it.province, it.ineCode) }
    }
    Column(
        Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState())
            .padding(MoSpacing.screen).testTag(sheetTestTag),
        verticalArrangement = Arrangement.spacedBy(MoSpacing.sm),
    ) {
        Text("Tu municipio para el tiempo", style = MaterialTheme.typography.titleLarge,
            color = MoColors.current.primaryText)
        Text("Elige provincia y pueblo. Puedes cambiarlo cuando quieras.",
            style = MaterialTheme.typography.bodyMedium, color = MoColors.current.secondaryText)
        if (suggestion != null && suggestion.ineCode != selectedCode) {
            MoSecondaryButton("Usar ${suggestion.displayName} de mi finca", {
                provinceCode = suggestion.provinceCode; selectedCode = suggestion.ineCode; localError = null
            }, Modifier.fillMaxWidth().testTag("weather-use-farm-suggestion"), !isSaving)
        }
        ProvinceMunicipalityPicker(
            provinceCode, selected,
            onProvinceSelected = { provinceCode = it; selectedCode = null; localError = null },
            onMunicipalitySelected = { selectedCode = it.ineCode; localError = null },
            enabled = !isSaving, tagPrefix = "weather",
        )
        Text(provinceCode?.let { SpanishMunicipalityCatalog.resolveProvince(it)?.name }.orEmpty(),
            Modifier.testTag(provinceFieldTestTag))
        Text(selected?.displayName.orEmpty(), Modifier.testTag(municipalityFieldTestTag))
        (errorMessage ?: localError)?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("profile-error"))
        }
        MoPrimaryButton("Guardar municipio", {
            if (selected != null) onSelectMunicipality(selected)
            else localError = "Elige primero una provincia y después un municipio."
        }, Modifier.fillMaxWidth().testTag(saveButtonTestTag), !isSaving)
        if (currentLocation != null) MoSecondaryButton("Quitar ubicación elegida", {
            provinceCode = null; selectedCode = null; onClearLocation()
        }, Modifier.fillMaxWidth().testTag("weather-clear-location"), !isSaving)
        MoTertiaryButton("Cancelar", onCancel, Modifier.fillMaxWidth())
    }
}
