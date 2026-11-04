package com.isivoltpro.maginaolivo.feature.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.isivoltpro.maginaolivo.domain.feed.FeedLocation
import com.isivoltpro.maginaolivo.domain.feed.IneMunicipality
import com.isivoltpro.maginaolivo.domain.feed.SpanishMunicipalityCatalog
import com.isivoltpro.maginaolivo.ui.components.MoCompactListItem
import com.isivoltpro.maginaolivo.ui.components.MoIcons
import com.isivoltpro.maginaolivo.ui.components.MoPrimaryButton
import com.isivoltpro.maginaolivo.ui.components.MoSecondaryButton
import com.isivoltpro.maginaolivo.ui.components.MoTertiaryButton
import com.isivoltpro.maginaolivo.ui.components.MoTextField
import com.isivoltpro.maginaolivo.ui.theme.MoColors
import com.isivoltpro.maginaolivo.ui.theme.MoShape
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing
import com.isivoltpro.maginaolivo.ui.theme.MoSurfaceTokens

/**
 * Reusable visual selector for the farmer's weather municipality across all 50 Spanish provinces,
 * Ceuta and Melilla (official 2026 INE catalog, offline search).
 *
 * Used from:
 * - Inicio (`HomeWeatherHero` -> `home-weather-location-action`)
 * - El tiempo (`WeatherWeekScreen` -> `weather-week-location-action`)
 * - Perfil (`MyProfileSection` -> `profile-municipality`)
 */
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
    val initialResolved = remember(currentLocation) {
        currentLocation?.let {
            SpanishMunicipalityCatalog.resolve(it.municipality, it.province, it.ineCode)
        }
    }
    var municipalityQuery by rememberSaveable { mutableStateOf(currentLocation?.municipality.orEmpty()) }
    var provinceQuery by rememberSaveable {
        mutableStateOf(initialResolved?.provinceName ?: currentLocation?.province.orEmpty())
    }
    var selectedProvinceCode by rememberSaveable {
        mutableStateOf(
            initialResolved?.provinceCode
                ?: SpanishMunicipalityCatalog.resolveProvince(currentLocation?.province)?.code
                ?: "",
        )
    }
    var localError by rememberSaveable { mutableStateOf<String?>(null) }

    val activeProvinceCode = selectedProvinceCode.takeIf { it.isNotBlank() }
        ?: SpanishMunicipalityCatalog.resolveProvince(provinceQuery)?.code

    val results = remember(municipalityQuery, activeProvinceCode) {
        SpanishMunicipalityCatalog.search(
            query = municipalityQuery,
            provinceCode = activeProvinceCode,
            limit = 20,
        )
    }

    val suggestedEntry = remember(farmSuggestion) {
        farmSuggestion?.let {
            SpanishMunicipalityCatalog.resolve(it.municipality, it.province, it.ineCode)
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(MoSpacing.screen)
            .testTag(sheetTestTag),
        verticalArrangement = Arrangement.spacedBy(MoSpacing.sm),
    ) {
        Text(
            "Ubicación meteorológica",
            style = MaterialTheme.typography.titleLarge,
            color = MoColors.current.primaryText,
        )
        Text(
            "Elige tu provincia y municipio (catálogo oficial INE 2026). Se guarda en este teléfono para tu cuenta y espacio de trabajo sin cambiar los datos de tus fincas.",
            style = MaterialTheme.typography.bodySmall,
            color = MoColors.current.secondaryText,
        )

        if (suggestedEntry != null && suggestedEntry.ineCode != initialResolved?.ineCode) {
            Surface(
                shape = MoShape.card,
                color = MoColors.current.infoTint,
                border = BorderStroke(1.dp, MoColors.current.infoText),
                modifier = Modifier.fillMaxWidth().testTag("weather-farm-suggestion"),
            ) {
                Column(
                    Modifier.padding(MoSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(MoSpacing.xs),
                ) {
                    Text(
                        "Sugerencia de tu finca: ${suggestedEntry.displayName} · ${suggestedEntry.provinceName}",
                        style = MaterialTheme.typography.titleSmall,
                        color = MoColors.current.primaryText,
                    )
                    MoSecondaryButton(
                        "Usar municipio de mi finca",
                        onClick = {
                            localError = null
                            onSelectMunicipality(suggestedEntry)
                        },
                        modifier = Modifier.fillMaxWidth().testTag("weather-use-farm-suggestion"),
                        enabled = !isSaving,
                    )
                }
            }
        }

        Text(
            "Provincia",
            style = MaterialTheme.typography.labelLarge,
            color = MoColors.current.primaryText,
        )
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .testTag("weather-province-chips"),
            horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs),
        ) {
            FilterChip(
                selected = selectedProvinceCode.isEmpty() && provinceQuery.isBlank(),
                onClick = {
                    selectedProvinceCode = ""
                    provinceQuery = ""
                    localError = null
                },
                enabled = !isSaving,
                label = { Text("Todas (52)") },
                modifier = Modifier.testTag("weather-province-all"),
            )
            SpanishMunicipalityCatalog.provinces.forEach { province ->
                val isSelected = activeProvinceCode == province.code
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        if (isSelected) {
                            selectedProvinceCode = ""
                            provinceQuery = ""
                        } else {
                            selectedProvinceCode = province.code
                            provinceQuery = province.name
                        }
                        localError = null
                    },
                    enabled = !isSaving,
                    label = { Text(province.name) },
                    modifier = Modifier.testTag("weather-province-${province.code}"),
                )
            }
        }

        MoTextField(
            value = municipalityQuery,
            onValueChange = {
                municipalityQuery = it
                localError = null
            },
            label = "Buscar municipio",
            modifier = Modifier.fillMaxWidth().testTag(municipalityFieldTestTag),
        )
        MoTextField(
            value = provinceQuery,
            onValueChange = {
                provinceQuery = it
                selectedProvinceCode = SpanishMunicipalityCatalog.resolveProvince(it)?.code.orEmpty()
                localError = null
            },
            label = "Provincia (opcional)",
            modifier = Modifier.fillMaxWidth().testTag(provinceFieldTestTag),
        )

        val shownError = errorMessage ?: localError
        shownError?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("profile-error"),
            )
        }

        MoPrimaryButton(
            text = "Guardar",
            onClick = {
                val trimmedMun = municipalityQuery.trim()
                val trimmedProv = provinceQuery.trim()
                if (trimmedMun.isEmpty() && trimmedProv.isEmpty()) {
                    localError = null
                    onClearLocation()
                } else if (onSubmitFreeText != null) {
                    localError = null
                    onSubmitFreeText(municipalityQuery, provinceQuery)
                } else {
                    val resolved = SpanishMunicipalityCatalog.resolve(
                        municipality = trimmedMun,
                        province = trimmedProv.ifEmpty { activeProvinceCode },
                    )
                    if (resolved != null) {
                        localError = null
                        onSelectMunicipality(resolved)
                    } else {
                        localError = when {
                            trimmedMun.isEmpty() -> "Escribe el municipio o elígelo en la lista oficial INE."
                            results.size > 1 -> "Hay varios municipios con ese nombre. Elige el tuyo en la lista inferior indicando su provincia."
                            else -> "No encontramos ese municipio en el directorio oficial del INE. Selecciónalo en la lista."
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().testTag(saveButtonTestTag),
            enabled = !isSaving,
        )

        if (currentLocation != null) {
            MoSecondaryButton(
                text = "Quitar ubicación elegida",
                onClick = {
                    localError = null
                    municipalityQuery = ""
                    provinceQuery = ""
                    selectedProvinceCode = ""
                    onClearLocation()
                },
                modifier = Modifier.fillMaxWidth().testTag("weather-clear-location"),
                enabled = !isSaving,
            )
        }

        Text(
            if (results.isEmpty()) {
                "Ningún municipio del INE coincide con la búsqueda."
            } else {
                "Municipios oficiales INE 2026 (${results.size} mostrados)"
            },
            style = MaterialTheme.typography.labelMedium,
            color = MoSurfaceTokens.secondaryText,
        )

        Column(verticalArrangement = Arrangement.spacedBy(MoSpacing.xxs)) {
            results.forEach { item ->
                MoCompactListItem(
                    title = "${item.displayName} · ${item.provinceName}",
                    subtitle = "Código INE ${item.ineCode}",
                    icon = MoIcons.Location,
                    onClick = {
                        if (!isSaving) {
                            localError = null
                            municipalityQuery = item.displayName
                            provinceQuery = item.provinceName
                            selectedProvinceCode = item.provinceCode
                            onSelectMunicipality(item)
                        }
                    },
                    modifier = Modifier
                        .heightIn(min = 56.dp)
                        .testTag("weather-municipality-option-${item.ineCode}"),
                )
            }
        }

        MoTertiaryButton("Cancelar", onCancel, Modifier.fillMaxWidth())
    }
}

