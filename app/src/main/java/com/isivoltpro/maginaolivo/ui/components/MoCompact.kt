package com.isivoltpro.maginaolivo.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.isivoltpro.maginaolivo.ui.theme.MoColors
import com.isivoltpro.maginaolivo.ui.theme.MoPalette
import com.isivoltpro.maginaolivo.ui.theme.MoShape
import com.isivoltpro.maginaolivo.ui.theme.MoSize
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing
import com.isivoltpro.maginaolivo.ui.theme.MoSurfaceTokens

/**
 * A small tinted square holding a line icon: the leading mark of compact rows and metrics.
 * Colours default to the icon's family ([MoIconTone]).
 */
@Composable
fun MoIconBadge(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = MoIconTone.of(icon).tint,
    container: Color = MoIconTone.of(icon).container,
    size: Int = 40,
) {
    Surface(modifier = modifier.size(size.dp), shape = RoundedCornerShape(12.dp), color = container, contentColor = tint) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, modifier = Modifier.size((size * 0.55f).dp), tint = tint)
        }
    }
}

/**
 * One compact summary figure. An unknown value is said in words (`emptyValue`), never
 * shown as a zero that was not measured.
 */
@Composable
fun MoSummaryMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    supportingText: String? = null,
) {
    Surface(
        modifier = modifier,
        shape = MoShape.card,
        color = MoSurfaceTokens.cardSurface,
        border = BorderStroke(1.dp, MoSurfaceTokens.cardStroke),
    ) {
        Column(Modifier.padding(horizontal = MoSpacing.sm, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = MoIconTone.of(icon).tint)
                Text(label, style = MaterialTheme.typography.labelMedium, color = MoSurfaceTokens.secondaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(value, style = MaterialTheme.typography.titleMedium, color = MoColors.current.bodyText, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (supportingText != null) {
                Text(supportingText, style = MaterialTheme.typography.labelMedium, color = MoSurfaceTokens.secondaryText, maxLines = 2)
            }
        }
    }
}

/**
 * CR-010 §13 — what a KPI measures, which gives it its accent. The accent follows the figure,
 * never its icon: Pesadas value-gold, jornales olive, machinery earth, costs blue; general
 * campaign figures (days, works) keep the brand's deep olive.
 */
enum class MoKpiKind {
    PESADAS, JORNALES, MAQUINARIA, COSTES, TOTAL, YIELD, CAMPAIGN;

    fun colors(palette: MoPalette): MoToneColors = when (this) {
        PESADAS -> MoIconTone.VALUE.colors(palette)
        JORNALES -> MoIconTone.LABOUR.colors(palette)
        MAQUINARIA -> MoIconTone.LAND.colors(palette)
        COSTES -> MoIconTone.MONEY.colors(palette)
        TOTAL -> MoIconTone.WATER.colors(palette)
        YIELD -> MoToneColors(palette.successText, palette.successTint)
        CAMPAIGN -> MoToneColors(palette.primaryText, palette.actionTint)
    }

    val tint: Color
        @Composable get() = colors(MoColors.current).tint
    val container: Color
        @Composable get() = colors(MoColors.current).container
}

/**
 * CR-010 §13 — the Campaign/Recolección/Cuaderno KPI: the same content as [MoSummaryMetric]
 * with a stronger hierarchy — a larger value, a bigger icon on its kind's tint and a coloured
 * edge per [MoKpiKind]. Colour is never the only cue: the icon and the label always say what
 * the figure is. The value wraps instead of being cut, so large text stays readable.
 */
@Composable
fun MoKpiMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    supportingText: String? = null,
    kind: MoKpiKind,
    onClick: (() -> Unit)? = null,
) {
    val accent = kind
    Surface(
        modifier = modifier.semantics(mergeDescendants = true) {}.heightIn(min = MoSize.minTouchTarget)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier),
        shape = MoShape.card,
        color = MoSurfaceTokens.cardSurface,
        border = BorderStroke(1.dp, MoSurfaceTokens.cardStroke),
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(4.dp).fillMaxHeight().background(accent.tint))
            Column(
                Modifier.padding(horizontal = MoSpacing.sm, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (icon != null) {
                        Box(Modifier.size(28.dp).background(accent.container, CircleShape), contentAlignment = Alignment.Center) {
                            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = accent.tint)
                        }
                    }
                    Text(label, style = MaterialTheme.typography.labelLarge, color = MoSurfaceTokens.secondaryText, maxLines = 2)
                }
                Text(value, style = MaterialTheme.typography.headlineSmall, color = MoColors.current.bodyText)
                if (supportingText != null) {
                    Text(supportingText, style = MaterialTheme.typography.bodySmall, color = MoSurfaceTokens.secondaryText)
                }
            }
        }
    }
}

/** Metrics laid out two per row, so a summary reads at a glance without long scrolls. */
@Composable
fun MoMetricGrid(
    modifier: Modifier = Modifier,
    columns: Int = 2,
    content: List<@Composable (Modifier) -> Unit>,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
        content.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
                row.forEach { cell -> cell(Modifier.weight(1f)) }
                repeat(columns - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

/** A dense, tappable row: icon, title, supporting line and an optional trailing slot. */
@Composable
fun MoCompactListItem(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTint: Color? = null,
    iconContainer: Color? = null,
    onClick: (() -> Unit)? = null,
    /** A pending row (planned, draft) sits on the softer surface so it reads as "not done yet". */
    container: Color = MoSurfaceTokens.cardSurface,
    trailing: (@Composable () -> Unit)? = null,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = MoSize.minTouchTarget)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier),
        shape = MoShape.card,
        color = container,
        border = BorderStroke(1.dp, MoSurfaceTokens.cardStroke),
    ) {
        Row(
            Modifier.padding(horizontal = MoSpacing.sm, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MoSpacing.sm),
        ) {
            if (icon != null) {
                val tone = MoIconTone.of(icon)
                MoIconBadge(icon, tint = iconTint ?: tone.tint, container = iconContainer ?: tone.container)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = MoColors.current.bodyText, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MoSurfaceTokens.secondaryText, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            trailing?.invoke()
        }
    }
}
