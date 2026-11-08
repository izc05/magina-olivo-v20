package com.isivoltpro.maginaolivo.feature.farms

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.isivoltpro.maginaolivo.domain.expense.Money
import com.isivoltpro.maginaolivo.domain.farm.Farm
import com.isivoltpro.maginaolivo.domain.harvest.Weight
import com.isivoltpro.maginaolivo.feature.activities.label
import com.isivoltpro.maginaolivo.ui.components.MoIcons
import com.isivoltpro.maginaolivo.ui.components.MoSecondaryButton
import com.isivoltpro.maginaolivo.ui.theme.MoColors
import com.isivoltpro.maginaolivo.ui.theme.MoShape
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing
import com.isivoltpro.maginaolivo.ui.theme.MoSurfaceTokens
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.util.Locale

private val SPANISH = Locale.forLanguageTag("es-ES")
private val DATE = DateTimeFormatter.ofPattern("d MMM yyyy", SPANISH)
private const val NOT_RECORDED = "Sin indicar"
private const val UNAVAILABLE = "No disponible"

private fun number(value: Double): String = NumberFormat.getNumberInstance(SPANISH).apply { maximumFractionDigits = 2 }.format(value)

@Composable private fun Line(text: String, tag: String? = null) =
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MoColors.current.bodyText,
        modifier = if (tag != null) Modifier.testTag(tag) else Modifier)

@Composable private fun Note(text: String, tag: String? = null, warning: Boolean = false) =
    Text(text, style = MaterialTheme.typography.bodySmall, color = if (warning) MoColors.current.warningText else MoSurfaceTokens.secondaryText,
        modifier = if (tag != null) Modifier.testTag(tag) else Modifier)

/** The one card of the Farm hub: tap the header for the detail, the button for the one action. */
@Composable
private fun FarmHubCard(
    title: String,
    icon: ImageVector,
    tag: String,
    onOpen: () -> Unit,
    action: Pair<String, () -> Unit>? = null,
    actionTag: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(shape = MoShape.card, color = MoSurfaceTokens.cardSurface, border = BorderStroke(1.dp, MoSurfaceTokens.cardStroke), modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(role = Role.Button, onClick = onOpen).padding(horizontal = MoSpacing.md).testTag(tag),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MoSpacing.sm),
            ) {
                Icon(icon, null, Modifier.padding(vertical = 2.dp), tint = MoColors.current.primaryText)
                Text(title, Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleMedium, color = MoColors.current.primaryText)
                Icon(MoIcons.ChevronRight, null, tint = MoSurfaceTokens.secondaryText)
            }
            Column(Modifier.fillMaxWidth().padding(start = MoSpacing.md, end = MoSpacing.md, bottom = MoSpacing.md), verticalArrangement = Arrangement.spacedBy(MoSpacing.xxs), content = {
                content()
                action?.let { (label, run) ->
                    MoSecondaryButton(label, run, Modifier.fillMaxWidth().padding(top = MoSpacing.xs).let { m -> if (actionTag != null) m.testTag(actionTag) else m })
                }
            })
        }
    }
}

/** Each area of the Farm appears once: a short summary, its detail and at most one action. */
@Composable
internal fun FarmDashboardPanel(
    farm: Farm,
    dashboard: FarmDashboard?,
    onOpenSection: (FarmSection) -> Unit,
    onRegisterTreatment: (() -> Unit)?,
    onRegisterIrrigation: (() -> Unit)?,
    onNotebook: () -> Unit,
    onExpenses: (() -> Unit)?,
) {
    Column(Modifier.fillMaxWidth().testTag("farm-dashboard"), verticalArrangement = Arrangement.spacedBy(MoSpacing.sm)) {
        FarmHubCard("Parcelas", MoIcons.Parcels, "farm-section-parcels", { onOpenSection(FarmSection.PARCELS) }) {
            Line(if (farm.parcelCount == 1L) "1 parcela" else "${farm.parcelCount} parcelas")
        }
        FarmHubCard("Tratamientos fitosanitarios", MoIcons.Checklist, "farm-dash-see-treatments", { onOpenSection(FarmSection.TREATMENTS) },
            onRegisterTreatment?.let { "Registrar tratamiento" to it }, "farm-dash-register-treatment") {
            val last = dashboard?.lastTreatment
            if (last == null) Line("Sin tratamientos registrados") else {
                Line("${last.product ?: "Producto sin registrar"} · ${last.date.format(DATE)}", "farm-dash-last-treatment")
                Note("${last.parcels.joinToString(", ").ifEmpty { NOT_RECORDED }} · ${last.quantity?.let { "${number(it)} ${last.unit.orEmpty()}".trim() } ?: NOT_RECORDED}")
            }
            if (dashboard?.campaignName != null) Note("${dashboard.treatmentsInCampaign} en la campaña")
            if ((dashboard?.incompleteTreatments ?: 0) > 0) Note("${dashboard?.incompleteTreatments} con datos por completar", "farm-dash-incomplete", warning = true)
        }
        FarmHubCard("Trabajos agrícolas", MoIcons.Activity, "farm-section-activities", { onOpenSection(FarmSection.ACTIVITIES) }, "Abrir Cuaderno" to onNotebook, "farm-section-notebook") {
            Note("Realizados: ${dashboard?.doneWorks ?: 0} · Previstos: ${dashboard?.pendingWorks ?: 0}")
            val latest = dashboard?.recentWorks?.firstOrNull()
            if (latest == null) Line("Sin trabajos registrados") else Line("${latest.type.label()} · ${latest.date.format(DATE)}${if (latest.done) "" else " · previsto"}")
        }
        FarmHubCard("Riego", MoIcons.Drop, "farm-dash-irrigation", { onOpenSection(FarmSection.IRRIGATIONS) }, onRegisterIrrigation?.let { "Registrar riego" to it }, "farm-dash-register-irrigation") {
            val lastDate = dashboard?.lastIrrigation
            if (lastDate == null) Line("Sin riegos registrados") else {
                Line("Último riego: ${lastDate.format(DATE)}")
                Note("Riegos registrados: ${dashboard.irrigationCount}")
            }
        }
        FarmHubCard("Recolección y campaña", MoIcons.Campaign, "farm-section-campaigns", { onOpenSection(FarmSection.CAMPAIGNS) }) {
            if (dashboard?.campaignName == null) Line("Sin campaña activa") else {
                Line(dashboard.campaignName, "farm-dash-campaign")
                Note("Jornadas: ${dashboard.harvestDays} · Cosechado: ${dashboard.harvestedGrams?.let(Weight::format) ?: UNAVAILABLE}")
                Note("Pesadas: ${dashboard.deliveryCount} · Entregado: ${dashboard.deliveredGrams?.let(Weight::format) ?: UNAVAILABLE}")
                if (dashboard.destinations.isNotEmpty()) Note("Entregas en: ${dashboard.destinations.joinToString(", ")}")
                dashboard.latestFatYieldHundredths?.let { Note("Último rendimiento: ${number(it / 100.0)} %") }
            }
        }
        FarmHubCard("Gastos e ingresos", MoIcons.Document, "farm-dash-money", { onExpenses?.invoke() }) {
            if (dashboard?.money.isNullOrEmpty()) Line("Sin gastos confirmados")
            dashboard?.money?.forEach { money ->
                fun amount(value: Long?) = value?.let { Money.format(it, money.currency) } ?: UNAVAILABLE
                Line("Gastos imputados: ${amount(money.totalMinor)}", "farm-dash-expenses-${money.currency}")
                Note("Jornales ${amount(money.labourMinor)} · Maquinaria ${amount(money.machineryMinor)} · Productos ${amount(money.productsMinor)} · Otros ${amount(money.otherMinor)}")
            }
            if ((dashboard?.draftExpenses ?: 0) > 0) Note("${dashboard?.draftExpenses} en borrador no se suman", "farm-dash-drafts", warning = true)
            Note("Ingresos y balance: Sin datos suficientes", "farm-dash-income")
        }
        FarmHubCard("Documentos", MoIcons.Document, "farm-section-documents", { onOpenSection(FarmSection.DOCUMENTS) }) {
            val count = dashboard?.documentCount ?: 0
            Line(if (count == 0) "Sin documentos" else if (count == 1) "1 documento" else "$count documentos")
            dashboard?.latestDocuments?.takeIf { it.isNotEmpty() }?.let { Note(it.joinToString(", ")) }
        }
    }
}
