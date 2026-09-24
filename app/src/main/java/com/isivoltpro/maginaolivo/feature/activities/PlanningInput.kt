package com.isivoltpro.maginaolivo.feature.activities

import com.isivoltpro.maginaolivo.domain.agenda.ActivityPlanning
import com.isivoltpro.maginaolivo.domain.agenda.ReminderKind
import com.isivoltpro.maginaolivo.domain.agenda.ReminderRequest
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** What the "Planificación (opcional)" block holds, as typed. Everything may stay blank. */
data class PlanningInput(
    val time: String = "",
    val durationHours: String = "",
    val people: String = "",
    val crew: String = "",
    val previousDay: Boolean = false,
    val sameDay: Boolean = false,
    val custom: String = "",
) {
    /** The planning and reminders, or the first thing the farmer needs to correct. */
    sealed interface Result {
        data class Ok(val planning: ActivityPlanning?, val reminders: List<ReminderRequest>) : Result
        data class Invalid(val field: String, val message: String) : Result
    }

    fun read(): Result {
        val startTime = if (time.isBlank()) null else parseTime(time) ?: return Result.Invalid(TIME, "Escribe la hora como 8, 8:30 o 08:30")
        val minutes = if (durationHours.isBlank()) {
            null
        } else {
            parseDurationMinutes(durationHours) ?: return Result.Invalid(DURATION, "Escribe la duración en horas, como 3 o 1,5")
        }
        val peopleCount = if (people.isBlank()) {
            null
        } else {
            people.trim().toIntOrNull()?.takeIf { it > 0 } ?: return Result.Invalid(PEOPLE, "Escribe cuántas personas, como 4")
        }
        val customAt = if (custom.isBlank()) {
            null
        } else {
            parseMoment(custom) ?: return Result.Invalid(CUSTOM, "Escribe el aviso como 2026-11-19 18:00")
        }
        val planning = ActivityPlanning(startTime, minutes, peopleCount, crew.trim().ifEmpty { null })
        val reminders = listOfNotNull(
            ReminderRequest(ReminderKind.PREVIOUS_DAY).takeIf { previousDay },
            ReminderRequest(ReminderKind.SAME_DAY).takeIf { sameDay },
            customAt?.let { ReminderRequest(ReminderKind.CUSTOM, it) },
        )
        return Result.Ok(planning.takeUnless { it.isEmpty }, reminders)
    }

    companion object {
        const val TIME = "time"
        const val DURATION = "duration"
        const val PEOPLE = "people"
        const val CUSTOM = "custom"

        private val MOMENT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        private val HOUR = DateTimeFormatter.ofPattern("HH:mm")

        fun of(planning: ActivityPlanning?, reminders: List<ReminderRequest>) = PlanningInput(
            time = planning?.startTime?.format(HOUR).orEmpty(),
            durationHours = planning?.expectedDurationMinutes?.let(::hoursText).orEmpty(),
            people = planning?.expectedPeopleCount?.toString().orEmpty(),
            crew = planning?.crewText.orEmpty(),
            previousDay = reminders.any { it.kind == ReminderKind.PREVIOUS_DAY },
            sameDay = reminders.any { it.kind == ReminderKind.SAME_DAY },
            custom = reminders.firstOrNull { it.kind == ReminderKind.CUSTOM }?.customAt?.format(MOMENT).orEmpty(),
        )

        /** 90 → "1,5": minutes as a farmer writes hours. */
        fun hoursText(minutes: Int): String =
            java.math.BigDecimal(minutes).divide(java.math.BigDecimal(60), 2, java.math.RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString().replace('.', ',')

        /** "8", "8:30", "08.30", "8h" → a time of day. */
        fun parseTime(text: String): LocalTime? {
            val clean = text.trim().lowercase().removeSuffix("h").trim().replace('.', ':')
            val parts = clean.split(':')
            val hour = parts[0].toIntOrNull() ?: return null
            val minute = if (parts.size > 1) parts[1].toIntOrNull() ?: return null else 0
            if (parts.size > 2 || hour !in 0..23 || minute !in 0..59) return null
            return LocalTime.of(hour, minute)
        }

        /** "3", "1,5", "1.5" hours → whole minutes, more than zero. */
        fun parseDurationMinutes(text: String): Int? {
            val hours = text.trim().replace(',', '.').toDoubleOrNull() ?: return null
            val minutes = Math.round(hours * 60).toInt()
            return minutes.takeIf { it > 0 && hours <= 24 * 30 }
        }

        /** "2026-11-19 18:00" → a local moment. */
        fun parseMoment(text: String): LocalDateTime? {
            val parts = text.trim().split(Regex("\\s+"))
            if (parts.size != 2) return null
            val date = runCatching { LocalDate.parse(parts[0]) }.getOrNull() ?: return null
            val time = parseTime(parts[1]) ?: return null
            return date.atTime(time)
        }
    }
}
