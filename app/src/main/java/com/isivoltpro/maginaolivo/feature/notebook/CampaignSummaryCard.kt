package com.isivoltpro.maginaolivo.feature.notebook

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.isivoltpro.maginaolivo.ui.theme.MoColors
import com.isivoltpro.maginaolivo.ui.theme.MoShape
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing
import com.isivoltpro.maginaolivo.ui.theme.MoSurfaceTokens

/**
 * #355 Fase 2, bloque C — the campaign in five seconds.
 *
 * Four figures used to be four separate cards stacked down the screen: kilos, yield, recollection
 * days and cost per kilo. Read one at a time they are four answers; read together they are the
 * campaign. So they are gathered into one block — the kilos in large type, because that is the
 * question the farmer actually arrives with, and the other three beneath in a single row.
 *
 * Nothing is lost and nothing is repeated: the cards this replaces are removed from the screen,
 * and the campaign's span and its first and last Pesada stay here as the line underneath. The
 * cards that open another screen — Pesadas, Jornales, Maquinaria, Gastos — are not figures, they
 * are doors, and they stay exactly where they were.
 *
 * An unknown figure shows «—». Never a zero: a campaign with no analysis yet has no yield, it
 * does not have a yield of nought.
 */
/**
 * #355 — what the cost per kilo is called in the summary.
 *
 * The cards this block replaces changed their own title when costs were missing: «Coste/kg» became
 * «Coste contabilizado/kg». The summary must not lose that warning, because a cost per kilo worked
 * out from half the costs looks like good news — it is lower than the real one. So when anything
 * is still unconfirmed the figure is labelled as accounted-for and says, underneath, that it is
 * incomplete. The figure itself is untouched: the same cents, from the same ledger.
 */
internal fun campaignCostLabel(incomplete: Boolean): Pair<String, String?> =
    if (incomplete) "Coste contabilizado/kg" to "Incompleto" else "Coste/kg" to null

@Composable
internal fun CampaignSummaryCard(
    weighedKilos: String,
    pesadasLabel: String,
    yieldLabel: String,
    yieldSupport: String,
    recollectionDays: String,
    costPerKg: String,
    /** True while any jornal, machine use or cost of this campaign is still unconfirmed. */
    costIncomplete: Boolean,
    span: String?,
    pesadaDates: String?,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier.fillMaxWidth().testTag("campaign-summary").semantics(mergeDescendants = true) {},
        shape = MoShape.cardLarge,
        color = MoSurfaceTokens.cardSurface,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(MoSpacing.md),
            verticalArrangement = Arrangement.spacedBy(MoSpacing.sm),
        ) {
            Column {
                Text(
                    weighedKilos,
                    style = MaterialTheme.typography.displaySmall,
                    color = MoColors.current.primaryText,
                    modifier = Modifier.testTag("campaign-summary-kilos"),
                )
                Text(
                    pesadasLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MoColors.current.secondaryText,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
                SummaryFigure("Rendimiento", yieldLabel, yieldSupport, Modifier.weight(1f), "campaign-summary-yield")
                SummaryFigure("Días de recolección", recollectionDays, null, Modifier.weight(1f), "campaign-summary-days")
                val (costLabel, costNote) = campaignCostLabel(costIncomplete)
                SummaryFigure(costLabel, costPerKg, costNote, Modifier.weight(1f), "campaign-summary-cost")
            }
            listOfNotNull(span, pesadaDates).takeIf { it.isNotEmpty() }?.let { lines ->
                Text(
                    lines.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MoColors.current.secondaryText,
                    modifier = Modifier.testTag("campaign-summary-span"),
                )
            }
        }
    }
}

/** One of the three small figures. The label is above the value so a long value never clips it. */
@Composable
private fun SummaryFigure(label: String, value: String, support: String?, modifier: Modifier, tag: String) {
    Column(modifier.testTag(tag), verticalArrangement = Arrangement.spacedBy(MoSpacing.xxs)) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MoColors.current.secondaryText,
            textAlign = TextAlign.Start,
        )
        Text(value, style = MaterialTheme.typography.titleMedium, color = MoColors.current.primaryText)
        support?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = MoColors.current.secondaryText)
        }
    }
}
