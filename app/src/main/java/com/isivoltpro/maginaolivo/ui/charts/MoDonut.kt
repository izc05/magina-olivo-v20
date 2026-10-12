package com.isivoltpro.maginaolivo.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.isivoltpro.maginaolivo.ui.theme.MoColors
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing
import com.isivoltpro.maginaolivo.ui.theme.MoSurfaceTokens

/**
 * #355 Fase 2, bloque B — a ring for a share-out: parcels of a campaign, categories of a cost.
 *
 * A donut earns its place only where the parts make up a whole. Everything here follows from that:
 *
 * - **Colour is never the only carrier.** Every slice is repeated in the legend with its name, its
 *   figure and its unit, so the chart can be read in black and white, by someone who does not
 *   separate green from brown, or out loud by a screen reader.
 * - **The whole is written in the hole.** A ring says proportions; the middle says how much.
 * - **Nothing is rounded into existence.** A slice too thin to see is still listed in the legend
 *   with its real figure, and no slice is ever enlarged to «make it visible».
 */
data class MoDonutSlice(
    val label: String,
    /** The share itself. Must not be negative; the caller decides what is drawable (see the domain). */
    val value: Long,
    /** Already formatted with its unit — «2.710 kg», «732,50 €». */
    val formatted: String,
    val color: Color,
    /** A slice that is not one of the named parts, drawn dimmer: «Pesadas mezcladas». */
    val muted: Boolean = false,
)

@Composable
fun MoDonut(
    slices: List<MoDonutSlice>,
    centreValue: String,
    centreLabel: String,
    modifier: Modifier = Modifier,
    tag: String = "mo-donut",
) {
    if (slices.isEmpty()) return
    val total = slices.sumOf { it.value }.takeIf { it > 0 } ?: return
    val ring = MoSurfaceTokens.cardStroke
    val summary = slices.joinToString("; ") { "${it.label} ${it.formatted}" }
    Column(
        modifier.fillMaxWidth().testTag(tag),
        verticalArrangement = Arrangement.spacedBy(MoSpacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(
                Modifier.size(DONUT_SIZE).semantics { contentDescription = "$centreLabel $centreValue. $summary" },
            ) {
                val stroke = DONUT_STROKE.toPx()
                val inset = stroke / 2
                val box = Size(size.width - stroke, size.height - stroke)
                // The track keeps the ring readable when one slice is almost the whole thing.
                drawArc(ring, 0f, 360f, false, Offset(inset, inset), box, style = Stroke(stroke))
                var start = -90f
                slices.forEach { slice ->
                    val sweep = 360f * slice.value / total
                    drawArc(
                        color = if (slice.muted) slice.color.copy(alpha = MUTED_ALPHA) else slice.color,
                        startAngle = start + GAP / 2,
                        sweepAngle = (sweep - GAP).coerceAtLeast(0f),
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = box,
                        style = Stroke(stroke),
                    )
                    start += sweep
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    centreValue,
                    style = MaterialTheme.typography.titleLarge,
                    color = MoColors.current.primaryText,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("$tag-centre"),
                )
                Text(
                    centreLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MoColors.current.secondaryText,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Column(
            Modifier.fillMaxWidth().testTag("$tag-legend"),
            verticalArrangement = Arrangement.spacedBy(MoSpacing.xxs),
        ) {
            slices.forEach { slice ->
                Row(
                    Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs),
                ) {
                    // The dot carries the same alpha as its arc, so the legend identifies the
                    // slice it names rather than a brighter cousin of it.
                    Box(
                        Modifier.size(12.dp).background(
                            if (slice.muted) slice.color.copy(alpha = MUTED_ALPHA) else slice.color,
                            CircleShape,
                        ),
                    )
                    Text(
                        slice.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MoColors.current.bodyText,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        slice.formatted,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MoColors.current.primaryText,
                    )
                }
            }
        }
    }
}

private val DONUT_SIZE = 168.dp
private val DONUT_STROKE = 26.dp

/** A hair of background between slices, so two neighbours never read as one. */
private const val GAP = 1.5f

/** How much quieter the unassigned slice is than a named one. */
private const val MUTED_ALPHA = 0.45f
