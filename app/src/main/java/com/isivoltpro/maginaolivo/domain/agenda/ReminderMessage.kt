package com.isivoltpro.maginaolivo.domain.agenda

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** What a reminder notification says: the work as the farmer wrote it, and when/where. */
data class ReminderMessage(val title: String, val text: String) {
    companion object {
        private val DAY = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale.forLanguageTag("es-ES"))
        private val TIME = DateTimeFormatter.ofPattern("HH:mm")

        fun of(
            description: String,
            date: LocalDate,
            today: LocalDate,
            planning: ActivityPlanning?,
            farmName: String?,
            parcelNames: List<String>,
        ): ReminderMessage {
            val day = when (date) {
                today -> "Hoy"
                today.plusDays(1) -> "Mañana"
                else -> date.format(DAY).replaceFirstChar { it.titlecase(Locale.forLanguageTag("es-ES")) }
            }
            val `when` = planning?.startTime?.let { "$day a las ${it.format(TIME)}" } ?: day
            val place = listOfNotNull(
                farmName,
                parcelNames.takeIf { it.isNotEmpty() }?.joinToString(", "),
            ).joinToString(" · ")
            val crew = listOfNotNull(
                planning?.expectedPeopleCount?.let { if (it == 1) "1 persona" else "$it personas" },
                planning?.crewText?.trim()?.ifEmpty { null },
            ).joinToString(" · ")
            return ReminderMessage(
                title = description,
                text = listOf(`when`, place, crew).filter { it.isNotEmpty() }.joinToString(" · "),
            )
        }
    }
}
