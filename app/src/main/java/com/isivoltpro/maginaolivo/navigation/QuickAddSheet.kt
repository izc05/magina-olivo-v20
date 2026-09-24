package com.isivoltpro.maginaolivo.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import com.isivoltpro.maginaolivo.app.LocalPersistence
import com.isivoltpro.maginaolivo.ui.components.MoCompactListItem
import com.isivoltpro.maginaolivo.ui.components.MoIcons
import com.isivoltpro.maginaolivo.ui.components.MoStatusChip
import com.isivoltpro.maginaolivo.ui.components.MoStatusTone
import com.isivoltpro.maginaolivo.ui.components.MoTertiaryButton
import com.isivoltpro.maginaolivo.ui.theme.MoInfo
import com.isivoltpro.maginaolivo.ui.theme.MoInfoText
import com.isivoltpro.maginaolivo.ui.theme.MoInk
import com.isivoltpro.maginaolivo.ui.theme.MoOliveMid
import com.isivoltpro.maginaolivo.ui.theme.MoOliveTint
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing
import com.isivoltpro.maginaolivo.ui.theme.MoTextSecondary
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** Where the farmer was when tapping "+": only what the local data actually says. */
data class QuickAddContext(
    val farmId: UUID,
    val farmName: String,
    val campaignName: String? = null,
    val parcelName: String? = null,
)

enum class QuickAddAction(val title: String, val description: String, val tag: String) {
    ACTIVITY("Registrar actuación", "Riego, abono, tratamiento…", "quick-add-activity"),
    HARVEST("Registrar cosecha", "Kilos recogidos en el campo", "quick-add-harvest"),
    DELIVERY("Registrar entrega", "Cooperativa o almazara", "quick-add-delivery"),
    EXPENSE("Gasto o documento", "Facturas, tickets, fotos", "quick-add-expense"),
    PLAN("Planificar trabajo", "Añadir al calendario", "quick-add-plan"),
}

/**
 * "¿Qué quieres registrar?" — UI polish v2. Compact rows with an icon and a line of
 * explanation instead of five identical green buttons; the context of the screen the
 * farmer came from is shown when it exists; Cancelar is a quiet tertiary action.
 * Scrolls when the screen is short, so nothing is ever cut off.
 */
@Composable
fun QuickAddSheet(
    context: QuickAddContext?,
    onAction: (QuickAddAction) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = MoSpacing.screen)
            .padding(bottom = MoSpacing.sm)
            .testTag("register-action-sheet"),
        verticalArrangement = Arrangement.spacedBy(MoSpacing.xs),
    ) {
        Text("¿Qué quieres registrar?", style = MaterialTheme.typography.headlineMedium, color = MoInk)
        if (context == null) {
            Text(
                "Elige qué anotar. Se guarda primero en este teléfono.",
                style = MaterialTheme.typography.bodyMedium,
                color = MoTextSecondary,
            )
        } else {
            QuickAddContextLine(context)
        }
        QuickAddAction.entries.forEach { action ->
            MoCompactListItem(
                title = action.title,
                subtitle = action.description,
                icon = action.icon(),
                iconTint = if (action == QuickAddAction.PLAN) MoInfoText else MoOliveMid,
                iconContainer = if (action == QuickAddAction.PLAN) MoInfo.copy(alpha = 0.14f) else MoOliveTint,
                onClick = { onAction(action) },
                modifier = Modifier.testTag(action.tag),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            MoTertiaryButton("Cancelar", onCancel, modifier = Modifier.testTag("quick-add-cancel"))
        }
    }
}

@Composable
private fun QuickAddContextLine(context: QuickAddContext) {
    Row(
        Modifier.fillMaxWidth().testTag("quick-add-context"),
        horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MoStatusChip("Finca: ${context.farmName}", tone = MoStatusTone.Success)
        context.campaignName?.let { MoStatusChip("Campaña: $it", tone = MoStatusTone.Neutral) }
        context.parcelName?.let {
            Text("Parcela: $it", style = MaterialTheme.typography.labelMedium, color = MoTextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun QuickAddAction.icon(): ImageVector = when (this) {
    QuickAddAction.ACTIVITY -> MoIcons.Activity
    QuickAddAction.HARVEST -> MoIcons.Harvest
    QuickAddAction.DELIVERY -> MoIcons.Delivery
    QuickAddAction.EXPENSE -> MoIcons.Document
    QuickAddAction.PLAN -> MoIcons.Calendar
}

/**
 * Resolves the Quick Add context from the screen on top of the stack, reading local
 * repositories only. No screen with a Farm behind it → no context line.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Composable
fun rememberQuickAddContext(
    persistence: LocalPersistence?,
    route: String?,
    argumentId: String?,
): QuickAddContext? {
    val id = argumentId?.let { runCatching { UUID.fromString(it) }.getOrNull() }
    val flow: Flow<QuickAddContext?> = remember(persistence, route, id) {
        if (persistence == null || id == null) return@remember flowOf(null)
        when (route) {
            AppDestination.FarmPattern -> farmContext(persistence, id, parcelName = null)
            // Design v3: a Farm section screen keeps its Farm as context.
            in farmSectionPatterns -> farmContext(persistence, id, parcelName = null)
            AppDestination.ParcelPattern -> persistence.parcelRepository.observeById(id).flatMapLatest { parcel ->
                val farmId = parcel?.farmId ?: return@flatMapLatest flowOf(null)
                farmContext(persistence, farmId, parcel.displayName)
            }
            AppDestination.CampaignPattern -> persistence.campaignRepository.observe(id).flatMapLatest { campaign ->
                if (campaign == null) return@flatMapLatest flowOf(null)
                farmContext(persistence, campaign.farmId, null).map { it?.copy(campaignName = campaign.name) }
            }
            AppDestination.ActivityPattern -> persistence.activityRepository.observe(id).flatMapLatest { activity ->
                val farmId = activity?.farmId ?: return@flatMapLatest flowOf(null)
                farmContext(persistence, farmId, activity.targets.singleOrNull()?.parcelName)
            }
            else -> flowOf(null)
        }
    }
    val context by flow.collectAsState(initial = null)
    return context
}

private fun farmContext(persistence: LocalPersistence, farmId: UUID, parcelName: String?): Flow<QuickAddContext?> =
    combine(persistence.farmRepository.observeById(farmId), flowOf(parcelName)) { farm, parcel ->
        farm?.takeIf { it.archivedAt == null }?.let {
            QuickAddContext(it.id, it.name, it.activeCampaignName, parcel)
        }
    }

private val farmSectionPatterns = com.isivoltpro.maginaolivo.feature.farms.FarmSection.entries
    .map { AppDestination.farmSectionPattern(it.route) }
    .toSet()
