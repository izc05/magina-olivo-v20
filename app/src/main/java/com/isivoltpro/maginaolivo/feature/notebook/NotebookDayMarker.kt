package com.isivoltpro.maginaolivo.feature.notebook

import com.isivoltpro.maginaolivo.ui.theme.MoSurfaceTokens

import com.isivoltpro.maginaolivo.ui.theme.MoColors

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * The Cuaderno's day line: a small dot, "Hoy · domingo 27 sept" and a hairline, so each
 * day's records read as one block and today stands out from yesterday without a big title.
 */
@Composable
internal fun NotebookDayMarker(date: LocalDate, today: LocalDate, modifier: Modifier = Modifier) {
    val isToday = date == today
    val tint = if (isToday) MoColors.current.actionText else MoColors.current.secondaryText
    Row(
        // One heading for TalkBack ("Hoy · domingo 27 sept"), not three loose pieces.
        modifier.fillMaxWidth().padding(top = MoSpacing.xs).semantics(mergeDescendants = true) { heading() }
            .testTag(if (isToday) "notebook-day-today" else "notebook-day"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs),
    ) {
        Box(Modifier.size(8.dp).background(if (isToday) MoColors.current.actionText else MaterialTheme.colorScheme.secondary, CircleShape))
        Text(
            dayMarkerLabel(date, today),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Medium,
            color = tint,
        )
        Box(Modifier.weight(1f).height(1.dp).background(MoSurfaceTokens.cardStroke))
    }
}

/** "Hoy · domingo 27 sept", "Ayer · sábado 26 sept", "Mañana · …", else "Jueves 24 sept". */
internal fun dayMarkerLabel(date: LocalDate, today: LocalDate): String {
    val day = date.format(if (date.year == today.year) DAY_MARK else DAY_MARK_YEAR)
    return when (date) {
        today -> "Hoy · $day"
        today.minusDays(1) -> "Ayer · $day"
        today.plusDays(1) -> "Mañana · $day"
        else -> day.replaceFirstChar { it.titlecase(SPANISH) }
    }
}

private val DAY_MARK: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d MMM", SPANISH)
private val DAY_MARK_YEAR: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d MMM yyyy", SPANISH)
