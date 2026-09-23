package com.isivoltpro.maginaolivo.feature.activities

import com.isivoltpro.maginaolivo.domain.agenda.ActivityPlanning
import com.isivoltpro.maginaolivo.domain.agenda.Reminder
import com.isivoltpro.maginaolivo.domain.agenda.ReminderKind
import com.isivoltpro.maginaolivo.domain.agenda.ReminderRequest
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlanningInputTest {
    @Test
    fun blankPlanningIsNoPlanning() {
        assertEquals(PlanningInput.Result.Ok(null, emptyList()), PlanningInput().read())
    }

    @Test
    fun farmerWrittenValuesAreRead() {
        val result = PlanningInput(
            time = "8:30", durationHours = "1,5", people = "4", crew = " Cuadrilla ",
            previousDay = true, custom = "2026-11-19 18:00",
        ).read()
        assertEquals(
            PlanningInput.Result.Ok(
                ActivityPlanning(LocalTime.of(8, 30), 90, 4, "Cuadrilla"),
                listOf(
                    ReminderRequest(ReminderKind.PREVIOUS_DAY),
                    ReminderRequest(ReminderKind.CUSTOM, LocalDateTime.parse("2026-11-19T18:00")),
                ),
            ),
            result,
        )
    }

    @Test
    fun remindersNeedNoPlanning() {
        assertEquals(
            PlanningInput.Result.Ok(null, listOf(ReminderRequest(ReminderKind.SAME_DAY))),
            PlanningInput(sameDay = true).read(),
        )
    }

    @Test
    fun mistakesPointAtTheirField() {
        assertEquals(PlanningInput.TIME, (PlanningInput(time = "25").read() as PlanningInput.Result.Invalid).field)
        assertEquals(PlanningInput.DURATION, (PlanningInput(durationHours = "0").read() as PlanningInput.Result.Invalid).field)
        assertEquals(PlanningInput.PEOPLE, (PlanningInput(people = "0").read() as PlanningInput.Result.Invalid).field)
        assertEquals(PlanningInput.CUSTOM, (PlanningInput(custom = "mañana").read() as PlanningInput.Result.Invalid).field)
    }

    @Test
    fun timesAsFarmersWriteThem() {
        assertEquals(LocalTime.of(8, 0), PlanningInput.parseTime("8"))
        assertEquals(LocalTime.of(8, 0), PlanningInput.parseTime("8h"))
        assertEquals(LocalTime.of(7, 45), PlanningInput.parseTime("07.45"))
        assertNull(PlanningInput.parseTime("8:75"))
        assertNull(PlanningInput.parseTime("ocho"))
    }

    @Test
    fun editingStartsFromWhatWasSaved() {
        val input = PlanningInput.of(
            ActivityPlanning(LocalTime.of(8, 0), 90, 3, "Pérez"),
            listOf(
                Reminder(UUID.randomUUID(), ReminderKind.SAME_DAY, Instant.EPOCH, true, null).toRequest(),
                Reminder(UUID.randomUUID(), ReminderKind.CUSTOM, Instant.EPOCH, true, null, LocalDateTime.parse("2026-11-19T18:00")).toRequest(),
            ),
        )
        assertEquals(PlanningInput("08:00", "1,5", "3", "Pérez", previousDay = false, sameDay = true, custom = "2026-11-19 18:00"), input)
    }
}
