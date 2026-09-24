package com.isivoltpro.maginaolivo.ui.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.isivoltpro.maginaolivo.ui.theme.MoInk
import com.isivoltpro.maginaolivo.ui.theme.MoOutline
import com.isivoltpro.maginaolivo.ui.theme.MoShape
import com.isivoltpro.maginaolivo.ui.theme.MoSize
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing
import com.isivoltpro.maginaolivo.ui.theme.MoTextSecondary
import com.isivoltpro.maginaolivo.ui.theme.MoWarmWhite

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
        color = MoWarmWhite,
        border = BorderStroke(1.dp, MoOutline),
    ) {
        Column(Modifier.padding(horizontal = MoSpacing.sm, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = MoIconTone.of(icon).tint)
                Text(label, style = MaterialTheme.typography.labelMedium, color = MoTextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(value, style = MaterialTheme.typography.titleMedium, color = MoInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (supportingText != null) {
                Text(supportingText, style = MaterialTheme.typography.labelMedium, color = MoTextSecondary, maxLines = 2)
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
    trailing: (@Composable () -> Unit)? = null,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = MoSize.minTouchTarget)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier),
        shape = MoShape.card,
        color = MoWarmWhite,
        border = BorderStroke(1.dp, MoOutline),
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
                Text(title, style = MaterialTheme.typography.titleSmall, color = MoInk, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MoTextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            trailing?.invoke()
        }
    }
}
