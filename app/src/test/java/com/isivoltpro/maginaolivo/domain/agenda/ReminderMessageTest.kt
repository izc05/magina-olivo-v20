package com.isivoltpro.maginaolivo.domain.agenda

import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderMessageTest {
    private val today = LocalDate.parse("2026-11-19")

    @Test
    fun tomorrowWithHourPlaceAndCrew() {
        val message = ReminderMessage.of(
            description = "Poda de invierno",
            date = today.plusDays(1),
            today = today,
            planning = ActivityPlanning(startTime = LocalTime.of(8, 0), expectedPeopleCount = 4, crewText = "Cuadrilla Pérez"),
            farmName = "La Solana",
            parcelNames = listOf("Norte", "Sur"),
        )
        assertEquals("Poda de invierno", message.title)
        assertEquals("Mañana a las 08:00 · La Solana · Norte, Sur · 4 personas · Cuadrilla Pérez", message.text)
    }

    @Test
    fun onlyWhatIsKnownIsSaid() {
        val message = ReminderMessage.of("Riego", today, today, null, null, emptyList())
        assertEquals("Hoy", message.text)
        val later = ReminderMessage.of("Riego", LocalDate.parse("2026-11-25"), today, ActivityPlanning(expectedPeopleCount = 1), "La Solana", emptyList())
        assertEquals("Miércoles 25 de noviembre · La Solana · 1 persona", later.text)
    }
}
