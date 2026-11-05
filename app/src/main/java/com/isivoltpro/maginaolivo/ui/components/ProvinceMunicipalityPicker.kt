package com.isivoltpro.maginaolivo.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.isivoltpro.maginaolivo.domain.feed.IneMunicipality
import com.isivoltpro.maginaolivo.domain.feed.SpanishMunicipalityCatalog
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing

/** Local INE picker shared by weather and farms. Selection is always province scoped. */
@Composable
fun ProvinceMunicipalityPicker(
    provinceCode: String?,
    municipality: IneMunicipality?,
    onProvinceSelected: (String?) -> Unit,
    onMunicipalitySelected: (IneMunicipality) -> Unit,
    enabled: Boolean = true,
    tagPrefix: String,
) {
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    val province = SpanishMunicipalityCatalog.provinces.firstOrNull { it.code == provinceCode }
    Column(verticalArrangement = Arrangement.spacedBy(MoSpacing.sm)) {
        MoSecondaryButton(
            if (province == null) "Elegir provincia" else "Provincia · ${province.name}",
            { query = ""; dialog = "province" },
            Modifier.fillMaxWidth().testTag("$tagPrefix-province"), enabled,
        )
        MoSecondaryButton(
            if (municipality == null) "Elegir municipio" else "Municipio · ${municipality.displayName}",
            { query = ""; dialog = "municipality" },
            Modifier.fillMaxWidth().testTag("$tagPrefix-municipality"), enabled && province != null,
        )
    }
    if (dialog != null) AlertDialog(
        onDismissRequest = { dialog = null },
        title = { Text(if (dialog == "province") "Elige la provincia" else "Elige el municipio") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MoSpacing.sm)) {
                MoTextField(
                    query, { query = it }, if (dialog == "province") "Buscar provincia" else "Buscar municipio",
                    Modifier.fillMaxWidth().testTag("$tagPrefix-search"),
                )
                val entries = if (dialog == "province") emptyList() else
                    SpanishMunicipalityCatalog.search(query, provinceCode, limit = 600)
                LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
                    if (dialog == "province") {
                        item {
                            MoSecondaryButton("Sin ubicación por ahora", {
                                onProvinceSelected(null); dialog = null
                            }, Modifier.fillMaxWidth().testTag("$tagPrefix-province-none"))
                        }
                        items(SpanishMunicipalityCatalog.provinces.filter {
                            normalized(it.name).contains(normalized(query))
                        }, key = { it.code }) { item ->
                            MoSecondaryButton(item.name, {
                                onProvinceSelected(item.code); dialog = null
                            }, Modifier.fillMaxWidth().testTag("$tagPrefix-province-${item.code}"))
                        }
                    } else {
                        items(entries, key = { it.ineCode }) { item ->
                            MoSecondaryButton(item.displayName, {
                                onMunicipalitySelected(item); dialog = null
                            }, Modifier.fillMaxWidth().testTag("$tagPrefix-municipality-${item.ineCode}"))
                        }
                    }
                }
            }
        },
        confirmButton = { MoTertiaryButton("Cerrar", { dialog = null }) },
    )
}

private fun normalized(value: String): String = java.text.Normalizer.normalize(
    value.lowercase(java.util.Locale.ROOT), java.text.Normalizer.Form.NFD,
).replace(Regex("\\p{M}+"), "")
