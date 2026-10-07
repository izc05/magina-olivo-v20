package com.isivoltpro.maginaolivo.feature.campaigns

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.isivoltpro.maginaolivo.domain.delivery.Delivery
import com.isivoltpro.maginaolivo.domain.delivery.DeliverySummary
import com.isivoltpro.maginaolivo.domain.delivery.Percent
import com.isivoltpro.maginaolivo.domain.expense.Expense
import com.isivoltpro.maginaolivo.domain.expense.Money
import com.isivoltpro.maginaolivo.domain.expense.RecollectionBucket
import com.isivoltpro.maginaolivo.domain.expense.RecollectionLedger
import com.isivoltpro.maginaolivo.domain.harvest.Harvest
import com.isivoltpro.maginaolivo.domain.harvest.Weight
import com.isivoltpro.maginaolivo.feature.harvests.harvestDayCount
import com.isivoltpro.maginaolivo.feature.harvests.harvestDays
import com.isivoltpro.maginaolivo.ui.components.MoIcons
import com.isivoltpro.maginaolivo.ui.theme.MoEarthText
import com.isivoltpro.maginaolivo.ui.theme.MoEarthTint
import com.isivoltpro.maginaolivo.ui.theme.MoOliveDark
import com.isivoltpro.maginaolivo.ui.theme.MoOliveTint
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing
import com.isivoltpro.maginaolivo.ui.theme.MoTreatmentText
import com.isivoltpro.maginaolivo.ui.theme.MoTreatmentTint
import com.isivoltpro.maginaolivo.ui.theme.MoWarningText
import com.isivoltpro.maginaolivo.ui.theme.MoWarningTint
import com.isivoltpro.maginaolivo.domain.expense.RecollectionCostCompleteness
import java.util.UUID

/**
 * #246 — what a campaign card says without opening it, read from the same ledgers as the
 * campaign itself (Pesadas, days, posted labour). Nothing is stored or estimated.
 */
data class CampaignCardSummary(
    val deliveredGrams: Long?,
    val deliveryCount: Int,
    val dayCount: Int,
    /** Posted labour per currency; an amount too large to add is null. Empty without jornales. */
    val labour: List<Pair<String, Long?>>,
    val yieldHundredths: Int?,
    /** Codex #404: share of the weighed kilos the yield is measured on; below 100 it is partial. */
    val yieldCoveragePercent: Int? = 100,
    /** #449: false while jornales, machinery or costs of the campaign are still unconfirmed. */
    val costComplete: Boolean = true,
) {
    companion object {
        fun of(
            campaignId: UUID,
            deliveries: List<Delivery>,
            harvests: List<Harvest>,
            expenses: List<Expense>,
            labour: List<com.isivoltpro.maginaolivo.domain.labour.LabourEntry> = emptyList(),
            equipment: List<com.isivoltpro.maginaolivo.domain.equipment.EquipmentLine> = emptyList(),
        ): CampaignCardSummary {
            val own = deliveries.filter { it.campaignId == campaignId }
            val summary = DeliverySummary.of(own)
            val days = harvests.filter { it.campaignId == campaignId }.map { it.id }.toSet()
            return CampaignCardSummary(
                deliveredGrams = summary.deliveredGrams,
                deliveryCount = summary.deliveryCount,
                dayCount = harvestDayCount(harvests.filter { it.campaignId == campaignId }),
                labour = RecollectionLedger.of(campaignId, expenses, deliveries)
                    .filter { it.hasPosted(RecollectionBucket.LABOUR) }
                    .map { it.currency to it.amount(RecollectionBucket.LABOUR) },
                yieldHundredths = summary.fatYield?.hundredths,
                yieldCoveragePercent = summary.coveragePercent(summary.fatYield),
                costComplete = RecollectionCostCompleteness.of(
                    labour.filter { it.harvestId in days },
                    equipment.filter { it.harvestId in days },
                    expenses.filter { it.campaignId == campaignId },
                ).complete,
            )
        }
    }
}

/** The kind of a figure: it picks a soft colour, always with an icon and words beside it. */
enum class CampaignFactKind { PRODUCTION, TIME, COST, YIELD }

data class CampaignFact(val kind: CampaignFactKind, val text: String)

/** «2.545 kg» «3 pesadas» «3 días» «Jornales 650,00 €» «Rend. 20,82 %»; unknown figures are left out. */
internal fun campaignFacts(summary: CampaignCardSummary): List<CampaignFact> = buildList {
    if (summary.deliveryCount == 0) {
        add(CampaignFact(CampaignFactKind.PRODUCTION, "Sin pesadas"))
    } else {
        add(
            CampaignFact(
                CampaignFactKind.PRODUCTION,
                summary.deliveredGrams?.let(Weight::format) ?: "Kilos no disponibles",
            ),
        )
        add(CampaignFact(CampaignFactKind.PRODUCTION, if (summary.deliveryCount == 1) "1 pesada" else "${summary.deliveryCount} pesadas"))
    }
    if (summary.dayCount > 0) add(CampaignFact(CampaignFactKind.TIME, harvestDays(summary.dayCount)))
    if (summary.labour.isNotEmpty()) {
        add(CampaignFact(CampaignFactKind.COST, "Jornales " + summary.labour.joinToString(" · ") { (currency, minor) ->
            minor?.let { Money.format(it, currency) } ?: "importe no disponible ($currency)"
        }))
    }
    // #449: said on the card, never left for the farmer to discover inside the campaign.
    if (!summary.costComplete) add(CampaignFact(CampaignFactKind.COST, "Costes sin confirmar"))
    summary.yieldHundredths?.let { hundredths ->
        val coverage = summary.yieldCoveragePercent
        val suffix = when {
            coverage == null -> " · cobertura no disponible"
            coverage < 100 -> " · $coverage % analizado"
            else -> ""
        }
        add(CampaignFact(CampaignFactKind.YIELD, "Rend. ${Percent.format(hundredths)}$suffix"))
    }
}

private fun CampaignFactKind.icon(): ImageVector = when (this) {
    CampaignFactKind.PRODUCTION -> MoIcons.Delivery
    CampaignFactKind.TIME -> MoIcons.Calendar
    CampaignFactKind.COST -> MoIcons.Euro
    CampaignFactKind.YIELD -> MoIcons.Percent
}

private fun CampaignFactKind.colors(): Pair<Color, Color> = when (this) {
    CampaignFactKind.PRODUCTION -> MoOliveDark to MoOliveTint
    CampaignFactKind.TIME -> MoWarningText to MoWarningTint
    CampaignFactKind.COST -> MoEarthText to MoEarthTint
    CampaignFactKind.YIELD -> MoTreatmentText to MoTreatmentTint
}

/** The card's figures as small tinted chips that wrap on narrow screens. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CampaignFacts(facts: List<CampaignFact>, modifier: Modifier = Modifier) {
    FlowRow(modifier.testTag("campaign-facts"), horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs), verticalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
        facts.forEach { fact ->
            val (text, container) = fact.kind.colors()
            Surface(color = container, shape = MaterialTheme.shapes.small, modifier = Modifier.testTag("campaign-fact-${fact.kind.name.lowercase()}")) {
                Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(fact.kind.icon(), contentDescription = null, tint = text, modifier = Modifier.size(14.dp))
                    Text(fact.text, style = MaterialTheme.typography.labelMedium, color = text)
                }
            }
        }
    }
}
