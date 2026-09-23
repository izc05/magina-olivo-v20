package com.isivoltpro.maginaolivo.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.isivoltpro.maginaolivo.ui.theme.MoInfo
import com.isivoltpro.maginaolivo.ui.theme.MoInk
import com.isivoltpro.maginaolivo.ui.theme.MoOliveMid
import com.isivoltpro.maginaolivo.ui.theme.MoOlivePrimary
import com.isivoltpro.maginaolivo.ui.theme.MoTextSecondary
import com.isivoltpro.maginaolivo.ui.theme.MoWarmWhite
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val SPANISH = Locale.forLanguageTag("es-ES")
private val MONTH_TITLE = DateTimeFormatter.ofPattern("MMMM yyyy", SPANISH)
private val DAY_DESCRIPTION = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", SPANISH)
private val WEEKDAYS = listOf("L", "M", "X", "J", "V", "S", "D")

fun YearMonth.spanishTitle(): String = format(MONTH_TITLE).replaceFirstChar { it.titlecase(SPANISH) }

/**
 * A real month grid (UI polish v2): Monday first, 48dp day targets, today ringed, the
 * selected day filled, and a dot under days that have planned work. Used by the Calendar
 * and by the date picker sheet. Every day is a labelled, selectable control.
 */
@Composable
fun MoMonthCalendar(
    month: YearMonth,
    selected: LocalDate?,
    today: LocalDate,
    onMonthChange: (YearMonth) -> Unit,
    onDaySelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    markers: Map<LocalDate, Int> = emptyMap(),
    tagPrefix: String = "calendar",
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onMonthChange(month.minusMonths(1)) }, modifier = Modifier.testTag("$tagPrefix-previous")) {
                Icon(MoIcons.ChevronLeft, contentDescription = "Mes anterior", tint = MoInk)
            }
            Text(
                month.spanishTitle(),
                modifier = Modifier.weight(1f).testTag("$tagPrefix-month"),
                style = MaterialTheme.typography.titleMedium,
                color = MoInk,
                textAlign = TextAlign.Center,
            )
            IconButton(onClick = { onMonthChange(month.plusMonths(1)) }, modifier = Modifier.testTag("$tagPrefix-next")) {
                Icon(MoIcons.ChevronRight, contentDescription = "Mes siguiente", tint = MoInk)
            }
        }
        Row(Modifier.fillMaxWidth()) {
            WEEKDAYS.forEach { day ->
                Text(
                    day,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    color = MoTextSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        }
        val first = month.atDay(1)
        val leading = first.dayOfWeek.value - DayOfWeek.MONDAY.value
        val cells: List<LocalDate?> = List(leading) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        if (date != null) {
                            DayCell(
                                date = date,
                                isSelected = date == selected,
                                isToday = date == today,
                                marker = markers[date] ?: 0,
                                onClick = { onDaySelected(date) },
                                tag = "$tagPrefix-day-$date",
                            )
                        }
                    }
                }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun DayCell(date: LocalDate, isSelected: Boolean, isToday: Boolean, marker: Int, onClick: () -> Unit, tag: String) {
    val description = buildString {
        append(date.format(DAY_DESCRIPTION))
        if (isToday) append(", hoy")
        if (marker == 1) append(", 1 trabajo") else if (marker > 1) append(", $marker trabajos")
    }
    Column(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .fillMaxWidth()
            .aspectRatio(1f, matchHeightConstraintsFirst = false)
            .selectable(selected = isSelected, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description }
            .testTag(tag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            modifier = Modifier.size(36.dp),
            shape = CircleShape,
            color = if (isSelected) MoOlivePrimary else Color.Transparent,
            contentColor = if (isSelected) MoWarmWhite else MoInk,
            border = if (isToday && !isSelected) BorderStroke(1.5.dp, MoOliveMid) else null,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected || isToday) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
        Box(
            Modifier
                .padding(top = 2.dp)
                .size(5.dp),
        ) {
            if (marker > 0) Surface(Modifier.size(5.dp), shape = CircleShape, color = if (isSelected) MoOlivePrimary else MoInfo) {}
        }
    }
}
