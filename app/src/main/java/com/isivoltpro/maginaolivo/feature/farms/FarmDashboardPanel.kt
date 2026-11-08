package com.isivoltpro.maginaolivo.feature.farms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.isivoltpro.maginaolivo.domain.expense.Money
import com.isivoltpro.maginaolivo.domain.harvest.Weight
import com.isivoltpro.maginaolivo.feature.activities.label
import com.isivoltpro.maginaolivo.ui.components.MoIcons
import com.isivoltpro.maginaolivo.ui.components.MoSectionCard
import com.isivoltpro.maginaolivo.ui.components.MoTertiaryButton
import com.isivoltpro.maginaolivo.ui.theme.MoColors
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing
import com.isivoltpro.maginaolivo.ui.theme.MoSurfaceTokens
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.util.Locale

private val SPANISH = Locale.forLanguageTag("es-ES")
private val DATE = DateTimeFormatter.ofPattern("d MMM yyyy", SPANISH)
private const val NOT_RECORDED = "Sin registrar"

private fun number(value: Double): String = NumberFormat.getNumberInstance(SPANISH).apply { maximumFractionDigits = 2 }.format(value)

@Composable private fun Line(text: String, tag: String? = null) =
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MoColors.current.bodyText,
        modifier = if (tag != null) Modifier.testTag(tag) else Modifier)

@Composable private fun Note(text: String, tag: String? = null, warning: Boolean = false) =
    Text(text, style = MaterialTheme.typography.bodySmall, color = if (warning) MoColors.current.warningText else MoSurfaceTokens.secondaryText,
        modifier = if (tag != null) Modifier.testTag(tag) else Modifier)

/** Short summaries and links only; the detail stays in the Cuaderno, Campaña and Documentos. */
@Composable
internal fun FarmDashboardPanel(
    dashboard: FarmDashboard,
    onOpenSection: (FarmSection) -> Unit,
    onRegister: (() -> Unit)?,
    onCampaign: (() -> Unit)?,
) {
    Column(Modifier.fillMaxWidth().testTag("farm-dashboard"), verticalArrangement = Arrangement.spacedBy(MoSpacing.sm)) {
        MoSectionCard(title = "Tratamientos fitosanitarios", icon = MoIcons.Checklist, modifier = Modifier.testTag("farm-dash-treatments")) {
            val last = dashboard.lastTreatment
            if (last == null) Line("Aún no hay tratamientos registrados.") else {
                Line("Último: ${last.product ?: "Producto sin registrar"} · ${last.date.format(DATE)}", "farm-dash-last-treatment")
                Note("Parcelas: ${last.parcels.joinToString(", ").ifEmpty { NOT_RECORDED }}")
                Note("Superficie: ${last.areaM2?.let { "${number(it / 10_000)} ha" } ?: NOT_RECORDED} · Cantidad: ${last.quantity?.let { "${number(it)} ${last.unit.orEmpty()}".trim() } ?: NOT_RECORDED}")
            }
            if (dashboard.campaignName != null) Note("En la campaña ${dashboard.campaignName}: ${dashboard.treatmentsInCampaign}")
            if (dashboard.incompleteTreatments > 0) Note("${dashboard.incompleteTreatments} con datos por completar", "farm-dash-incomplete", warning = true)
            MoTertiaryButton("Ver tratamientos", { onOpenSection(FarmSection.NOTEBOOK) }, Modifier.fillMaxWidth().testTag("farm-dash-see-treatments"))
            onRegister?.let { MoTertiaryButton("Registrar tratamiento", it, Modifier.fillMaxWidth().testTag("farm-dash-register-treatment")) }
        }
        MoSectionCard(title = "Trabajos agrícolas", icon = MoIcons.Activity, modifier = Modifier.testTag("farm-dash-works")) {
            Note("Realizados: ${dashboard.doneWorks} · Previstos: ${dashboard.pendingWorks}")
            if (dashboard.recentWorks.isEmpty()) Line("Aún no hay trabajos registrados.")
            dashboard.recentWorks.forEach { work ->
                Line("${work.type.label()} · ${work.date.format(DATE)}${if (work.done) "" else " · previsto"}")
                if (work.parcels.isNotEmpty()) Note(work.parcels.joinToString(", "))
            }
            MoTertiaryButton("Abrir Cuaderno", { onOpenSection(FarmSection.NOTEBOOK) }, Modifier.fillMaxWidth().testTag("farm-dash-notebook"))
        }
        MoSectionCard(title = "Recolección y campaña", icon = MoIcons.Campaign, modifier = Modifier.testTag("farm-dash-harvest")) {
            if (dashboard.campaignName == null) Line("Sin campaña activa.") else {
                Line(dashboard.campaignName, "farm-dash-campaign")
                Note("Jornadas: ${dashboard.harvestDays} · Kilos cosechados: ${dashboard.harvestedGrams?.let(Weight::format) ?: "No disponible"}")
                Note("Pesadas: ${dashboard.deliveryCount} · Kilos entregados: ${dashboard.deliveredGrams?.let(Weight::format) ?: "No disponible"}")
                if (dashboard.destinations.isNotEmpty()) Note("Entregas en: ${dashboard.destinations.joinToString(", ")}")
                dashboard.latestFatYieldHundredths?.let { Note("Último rendimiento registrado: ${number(it / 100.0)} %") }
            }
            onCampaign?.let { MoTertiaryButton("Ver campaña", it, Modifier.fillMaxWidth().testTag("farm-dash-open-campaign")) }
        }
        MoSectionCard(title = "Gastos e ingresos", icon = MoIcons.Document, modifier = Modifier.testTag("farm-dash-money")) {
            if (dashboard.money.isEmpty()) Line("Sin gastos confirmados.")
            dashboard.money.forEach { money ->
                fun amount(value: Long?) = value?.let { Money.format(it, money.currency) } ?: "No disponible"
                Line("Gastos imputados: ${amount(money.totalMinor)}", "farm-dash-expenses-${money.currency}")
                Note("Jornales: ${amount(money.labourMinor)} · Maquinaria: ${amount(money.machineryMinor)} · Productos: ${amount(money.productsMinor)}")
            }
            if (dashboard.draftExpenses > 0) Note("${dashboard.draftExpenses} gastos en borrador no se suman.", "farm-dash-drafts", warning = true)
            Note("Ingresos y margen: Sin datos suficientes", "farm-dash-income")
        }
        MoSectionCard(title = "Documentación", icon = MoIcons.Document, modifier = Modifier.testTag("farm-dash-documents")) {
            Line(if (dashboard.documentCount == 0) "Sin documentos de la finca." else "Documentos de la finca: ${dashboard.documentCount}")
            MoTertiaryButton("Ver documentos", { onOpenSection(FarmSection.DOCUMENTS) }, Modifier.fillMaxWidth().testTag("farm-dash-open-documents"))
        }
    }
}
