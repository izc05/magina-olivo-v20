package com.isivoltpro.maginaolivo.domain.agenda

import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

class AgendaTest {
    private val today = LocalDate.parse("2026-11-20")

    private data class Work(val name: String, val date: LocalDate, val time: LocalTime? = null)

    @Test
    fun bucketsFollowTheCalendar() {
        assertEquals(AgendaBucket.OVERDUE, Agenda.bucketOf(today.minusDays(1), today))
        assertEquals(AgendaBucket.TODAY, Agenda.bucketOf(today, today))
        assertEquals(AgendaBucket.TOMORROW, Agenda.bucketOf(today.plusDays(1), today))
        assertEquals(AgendaBucket.NEXT_7_DAYS, Agenda.bucketOf(today.plusDays(2), today))
        assertEquals(AgendaBucket.NEXT_7_DAYS, Agenda.bucketOf(today.plusDays(7), today))
        assertEquals(AgendaBucket.LATER, Agenda.bucketOf(today.plusDays(8), today))
    }

    @Test
    fun sectionsAreOrderedAndOnlyNonEmpty() {
        val work = listOf(
            Work("later", today.plusDays(30)),
            Work("today-10", today, LocalTime.of(10, 0)),
            Work("overdue", today.minusDays(3)),
            Work("today-any", today),
            Work("today-08", today, LocalTime.of(8, 0)),
        )
        val sections = Agenda.group(work, today, { it.date }, { it.time })
        assertEquals(listOf(AgendaBucket.OVERDUE, AgendaBucket.TODAY, AgendaBucket.LATER), sections.map { it.bucket })
        assertEquals(listOf("today-any", "today-08", "today-10"), sections[1].items.map { it.name })
    }

    @Test
    fun nothingPlannedIsAnEmptyAgenda() {
        assertEquals(emptyList<AgendaSection<Work>>(), Agenda.group(emptyList<Work>(), today, { it.date }, { it.time }))
    }
}
