package com.isivoltpro.maginaolivo.feature.harvests

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.isivoltpro.maginaolivo.domain.expense.*
import com.isivoltpro.maginaolivo.domain.harvest.HarvestContext
import com.isivoltpro.maginaolivo.domain.labour.*
import com.isivoltpro.maginaolivo.feature.expenses.*
import com.isivoltpro.maginaolivo.ui.components.*
import com.isivoltpro.maginaolivo.ui.theme.*
import java.time.LocalDate
import java.util.UUID

/** Global entry only. Campaign/Jornada writers keep their contextual form and immutable scope. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun QuickLabourScreen(
    farmId: UUID, generalDraft: MutableState<ExpenseForm>, options: RelationOptions,
    contexts: List<HarvestContext>, workers: List<Worker>, rates: RecollectionRates?,
    ready: Boolean, isSaving: Boolean, errors: ExpenseFormErrors, error: String?,
    onSaveGeneral: (ExpenseForm) -> Unit,
    onSaveCrew: (UUID, LocalDate, CrewDraft) -> Unit, onAddWorker: (String) -> Unit, onCancel: () -> Unit,
    campaignReady: Boolean = true, currency: String? = rates?.currency ?: "EUR", alreadyRecorded: Set<UUID> = emptySet(),
) {
    var scope by rememberSaveable { mutableStateOf("GENERAL") }
    var selectedCampaignId by rememberSaveable { mutableStateOf<String?>(null) }
    val running = contexts.filter { it.farmId == farmId }
    val campaign = running.singleOrNull()
    val holder = rememberSaveableStateHolder()
    val date = runCatching { LocalDate.parse(generalDraft.value.date) }.getOrNull()
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(top = MoSpacing.sm).testTag("quick-labour-form")) {
        Column(Modifier.fillMaxWidth().padding(horizontal = MoSpacing.screen), verticalArrangement = Arrangement.spacedBy(MoSpacing.xxs)) {
            Text("Añadir jornal", style = MaterialTheme.typography.headlineSmall)
            options.farms.firstOrNull { it.id == farmId }?.let { farm ->
                Text(listOfNotNull(farm.name, options.parcelLabel(generalDraft.value.parcelId, farmId)).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium, color = MoSurfaceTokens.secondaryText,
                    modifier = Modifier.testTag("quick-labour-farm-context"))
            }
            if (ready && running.isNotEmpty()) {
                Text("Asignar a", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
                    FilterChip(scope == "GENERAL", { scope = "GENERAL" },
                        { Text("Fuera de campaña") }, enabled = !isSaving, modifier = Modifier.heightIn(min = 48.dp).testTag("quick-labour-general"))
                    if (campaign != null) FilterChip(scope == "CAMPAIGN" && selectedCampaignId == campaign.campaignId.toString(),
                        { selectedCampaignId = campaign.campaignId.toString(); scope = "CAMPAIGN" },
                        { Text("Campaña de recogida · ${campaign.campaignName}") }, enabled = !isSaving,
                        modifier = Modifier.heightIn(min = 48.dp).testTag("quick-labour-campaign"))
                }
                Text("Los borradores se conservan al cambiar de ámbito.", style = MaterialTheme.typography.bodySmall)
                if (campaign == null) Text("Hay varias campañas operativas. Abre la campaña concreta para registrar su jornal.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            } else if (!ready) Text("Comprobando el contexto local…", style = MaterialTheme.typography.bodySmall)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("quick-labour-error")) }
        }
        Box(Modifier.weight(1f)) {
            if (scope == "GENERAL") {
                // General work cannot silently become a Campaign cost through its linked Activity.
                val generalOptions = options.copy(campaigns = emptyList(), campaignsFor = farmId,
                    activities = options.activities.filter { it.campaignId == null })
                ExpenseEditor("Fuera de campaña", generalDraft.value, generalOptions, errors,
                    isSaving || !ready, "Guardar jornal general", {},
                    { onSaveGeneral(it.copy(category = ExpenseCategory.LABOR, campaignId = null, harvestId = null)) }, onCancel,
                    subtitle = "Mano de obra general: importe y concepto. Persona y horas todavía no disponibles.",
                    farmLocked = true, draftState = generalDraft, categoryLocked = true)
            } else if (campaign != null && ready && selectedCampaignId == campaign.campaignId.toString()) {
                holder.SaveableStateProvider(campaign.campaignId.toString()) {
                    val draftCurrency = rememberSaveable { mutableStateOf(currency.takeIf { campaignReady }) }
                    var lastValidDate by rememberSaveable { mutableStateOf(date?.toString()) }
                    var reviewCurrency by rememberSaveable { mutableStateOf(false) }
                    var currencyResetToken by rememberSaveable { mutableIntStateOf(0) }
                    LaunchedEffect(currency, campaignReady) {
                        if (draftCurrency.value == null && currency != null && campaignReady) draftCurrency.value = currency
                    }
                    LaunchedEffect(date) { if (date != null) lastValidDate = date.toString() }
                    val formDate = date ?: lastValidDate?.let(LocalDate::parse)
                    val currencyChanged = currency != null && draftCurrency.value != null && currency != draftCurrency.value
                    val blocked = isSaving || !campaignReady || date == null || currency == null || currencyChanged
                    Column(Modifier.fillMaxSize()) {
                        MoDateInputField(generalDraft.value.date, { generalDraft.value = generalDraft.value.copy(date = it) },
                            "Fecha", modifier = Modifier.padding(horizontal = MoSpacing.screen).testTag("quick-labour-date"), enabled = !isSaving)
                        if (currencyChanged) {
                            Text("Este borrador está en ${draftCurrency.value}; el día usa $currency. Revisa los importes antes de guardar.",
                                modifier = Modifier.padding(horizontal = MoSpacing.screen), style = MaterialTheme.typography.bodySmall)
                            MoTertiaryButton("Revisar moneda del día", { reviewCurrency = true }, enabled = !isSaving,
                                modifier = Modifier.padding(horizontal = MoSpacing.screen).testTag("quick-labour-review-currency"))
                        } else if (blocked && !isSaving) Text("Comprobando los jornales y la moneda del día…", modifier = Modifier.padding(horizontal = MoSpacing.screen))
                        if (formDate != null && draftCurrency.value != null) LabourSheet(workers, alreadyRecorded, blocked, null,
                            // A draft identity only; the transactional save replaces it with the actual day.
                            UUID(0, 0), campaign.campaignId, formDate, rates,
                            { if (!blocked) onSaveCrew(campaign.campaignId, formDate, it) }, onAddWorker, onCancel,
                            currency = draftCurrency.value!!, title = "Recogida", currencyResetToken = currencyResetToken)
                    }
                    if (reviewCurrency && currencyChanged) AlertDialog(
                        onDismissRequest = { reviewCurrency = false },
                        title = { Text("Revisar importes en $currency") },
                        text = { Text("Se conservarán persona, duración y fecha. Se vaciarán precio y pago inicial para que los confirmes en $currency; no se convertirán importes.") },
                        confirmButton = { TextButton({ draftCurrency.value = currency; currencyResetToken++; reviewCurrency = false },
                            modifier = Modifier.heightIn(min = 48.dp).testTag("quick-labour-confirm-currency")) { Text("Revisar importes") } },
                        dismissButton = { TextButton({ reviewCurrency = false }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancelar") } },
                    )
                }
            } else {
                Column(Modifier.padding(MoSpacing.screen)) {
                    Text("La campaña seleccionada ya no está disponible. El borrador se conserva.")
                    MoSecondaryButton("Fuera de campaña", { scope = "GENERAL" }, modifier = Modifier.testTag("quick-labour-return-general"))
                    MoTertiaryButton("Cancelar", onCancel)
                }
            }
        }
    }
}
