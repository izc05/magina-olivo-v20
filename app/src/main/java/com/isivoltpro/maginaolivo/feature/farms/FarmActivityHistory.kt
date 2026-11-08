package com.isivoltpro.maginaolivo.feature.farms

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.isivoltpro.maginaolivo.app.LocalPersistence
import com.isivoltpro.maginaolivo.domain.activity.Activity
import com.isivoltpro.maginaolivo.domain.activity.ActivityDetail
import com.isivoltpro.maginaolivo.domain.activity.ActivityType
import com.isivoltpro.maginaolivo.domain.campaign.Campaign
import com.isivoltpro.maginaolivo.feature.activities.label
import com.isivoltpro.maginaolivo.ui.components.MoEmptyState
import com.isivoltpro.maginaolivo.ui.components.MoIcons
import com.isivoltpro.maginaolivo.ui.components.MoPrimaryButton
import com.isivoltpro.maginaolivo.ui.components.MoTertiaryButton
import com.isivoltpro.maginaolivo.ui.components.MoTextField
import com.isivoltpro.maginaolivo.ui.theme.MoColors
import com.isivoltpro.maginaolivo.ui.theme.MoShape
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing
import com.isivoltpro.maginaolivo.ui.theme.MoSurfaceTokens
import java.text.NumberFormat
import java.text.Normalizer
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

private val SPANISH = Locale.forLanguageTag("es-ES")
private val DATE = DateTimeFormatter.ofPattern("d MMM yyyy", SPANISH)

private fun plain(text: String) = Normalizer.normalize(text.trim(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase(SPANISH)

private fun Activity.productName(): String? = (detail as? ActivityDetail.Phytosanitary)?.productName

/** One Activity stays one row however many parcels it covers. */
internal fun filterFarmHistory(
    activities: List<Activity>,
    type: ActivityType,
    campaignId: UUID?,
    parcelId: UUID?,
    query: String,
    newestFirst: Boolean,
    campaignPeriod: CampaignPeriod? = null,
): List<Activity> {
    val text = plain(query)
    val rows = activities.filter { activity ->
        activity.type == type &&
            (campaignId == null || activity.belongsToCampaign(campaignId, campaignPeriod)) &&
            (parcelId == null || activity.targets.any { it.parcelId == parcelId }) &&
            (text.isEmpty() || plain(activity.productName().orEmpty()).contains(text))
    }
    return if (newestFirst) rows.sortedByDescending { it.activityDate } else rows.sortedBy { it.activityDate }
}

private fun number(value: Double) = NumberFormat.getNumberInstance(SPANISH).apply { maximumFractionDigits = 2 }.format(value)

internal fun historyDetailLines(activity: Activity): List<String> {
    val area = activity.targets.takeIf { it.isNotEmpty() && it.all { t -> t.areaAffectedM2 != null } }?.sumOf { it.areaAffectedM2!! }
    val surface = area?.let { "Superficie: ${number(it / 10_000)} ha" }
    val machines = activity.machines.map { it.name }.filter(String::isNotBlank).takeIf { it.isNotEmpty() }?.let { "Maquinaria: ${it.joinToString(", ")}" }
    val detailLines = when (val detail = activity.detail) {
        is ActivityDetail.Phytosanitary -> listOfNotNull(
            detail.productRegistrationNumber?.let { "Registro: $it" },
            detail.doseValue?.let { "Dosis: ${number(it)} ${detail.doseUnit.orEmpty()}".trim() },
            detail.totalQuantity?.let { "Cantidad: ${number(it)} ${detail.unit.orEmpty()}".trim() },
        )
        is ActivityDetail.Irrigation -> listOfNotNull(
            detail.durationMinutes?.let { "Duración: ${it / 60} h ${it % 60} min" },
            detail.volumeM3?.let { "Volumen medido: ${number(it)} m³" },
            detail.sectorText?.let { "Sector: $it" },
        )
        else -> emptyList()
    }
    return detailLines + listOfNotNull(surface, machines)
}

/** The Farm's records of one type, read from the Cuaderno's own data. Rows open the existing detail. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FarmActivityHistoryRoute(
    farmId: UUID,
    type: ActivityType,
    persistence: LocalPersistence,
    onActivitySelected: (UUID) -> Unit,
    onRegister: (() -> Unit)?,
) {
    val activities by remember(farmId) { persistence.activityRepository.observeForFarm(farmId) }.collectAsStateWithLifecycle(emptyList())
    val campaigns by remember(farmId) { persistence.campaignRepository.observeForFarm(farmId) }.collectAsStateWithLifecycle(emptyList())
    FarmActivityHistory(type, activities, campaigns, onActivitySelected, onRegister)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FarmActivityHistory(
    type: ActivityType,
    activities: List<Activity>,
    campaigns: List<Campaign>,
    onActivitySelected: (UUID) -> Unit,
    onRegister: (() -> Unit)?,
) {
    var campaign by rememberSaveable { mutableStateOf<String?>(null) }
    var parcel by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var newest by rememberSaveable { mutableStateOf(true) }
    val treatment = type == ActivityType.PHYTOSANITARY
    val registerLabel = if (treatment) "Registrar tratamiento" else "Registrar riego"
    val ofType = activities.filter { it.type == type }
    Column(Modifier.fillMaxWidth().testTag("farm-history"), verticalArrangement = Arrangement.spacedBy(MoSpacing.sm)) {
        if (ofType.isEmpty()) {
            MoEmptyState(
                if (treatment) "Aún no hay tratamientos en esta finca" else "Aún no hay riegos en esta finca",
                "Lo que registres aparecerá aquí.",
                modifier = Modifier.testTag("farm-history-empty"),
                actionText = onRegister?.let { registerLabel },
                onAction = onRegister,
                actionModifier = Modifier.testTag("farm-history-register"),
                icon = MoIcons.History,
            )
            return
        }
        onRegister?.let { MoPrimaryButton(registerLabel, it, Modifier.fillMaxWidth().testTag("farm-history-register")) }
        if (treatment) MoTextField(query, { query = it }, "Buscar producto", Modifier.fillMaxWidth().testTag("farm-history-search"))
        if (campaigns.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
            campaigns.forEach { option ->
                val key = option.id.toString()
                FilterChip(campaign == key, { campaign = key.takeIf { it != campaign } }, { Text(option.name) }, Modifier.testTag("farm-history-campaign-$key"))
            }
        }
        val parcels = ofType.flatMap { it.targets }.distinctBy { it.parcelId }
        if (parcels.size > 1) FlowRow(horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
            parcels.forEach { option ->
                val key = option.parcelId.toString()
                FilterChip(parcel == key, { parcel = key.takeIf { it != parcel } }, { Text(option.parcelName) }, Modifier.testTag("farm-history-parcel-$key"))
            }
        }
        MoTertiaryButton(if (newest) "Más recientes primero" else "Más antiguos primero", { newest = !newest }, Modifier.testTag("farm-history-sort"))
        val period = campaigns.firstOrNull { it.id.toString() == campaign }?.let { CampaignPeriod(it.startDate, it.endDate) }
        val rows = filterFarmHistory(activities, type, campaign?.let(UUID::fromString), parcel?.let(UUID::fromString), query, newest, period)
        if (rows.isEmpty()) Text("Ningún registro coincide con los filtros.", color = MoSurfaceTokens.secondaryText, modifier = Modifier.testTag("farm-history-no-match"))
        rows.forEach { activity ->
            Surface(shape = MoShape.card, color = MoSurfaceTokens.cardSurface, border = BorderStroke(1.dp, MoSurfaceTokens.cardStroke),
                modifier = Modifier.fillMaxWidth().testTag("farm-history-row")) {
                Column(Modifier.clickable(role = Role.Button) { onActivitySelected(activity.id) }.padding(MoSpacing.md), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(activity.productName() ?: if (treatment) "Producto sin registrar" else type.label(), style = MaterialTheme.typography.titleMedium, color = MoColors.current.primaryText)
                    Text("${activity.activityDate.format(DATE)} · ${activity.targets.joinToString(", ") { it.parcelName }.ifEmpty { "Sin parcela" }}",
                        style = MaterialTheme.typography.bodyMedium, color = MoColors.current.bodyText)
                    historyDetailLines(activity).forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MoSurfaceTokens.secondaryText) }
                }
            }
        }
    }
}
