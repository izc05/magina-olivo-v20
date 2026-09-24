package com.isivoltpro.maginaolivo.domain.agenda

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderRulesTest {
    private val madrid = ZoneId.of("Europe/Madrid")
    private val day = LocalDate.parse("2026-11-20")

    @Test
    fun previousDayWarnsTheEveningBefore() {
        val at = ReminderRules.triggerAt(day, LocalTime.of(8, 0), ReminderRequest(ReminderKind.PREVIOUS_DAY), madrid)
        assertEquals(Instant.parse("2026-11-19T18:00:00Z"), at)
    }

    @Test
    fun sameDayWarnsAnHourBeforeOrEarlyWithoutAnHour() {
        assertEquals(
            Instant.parse("2026-11-20T08:30:00Z"),
            ReminderRules.triggerAt(day, LocalTime.of(10, 30), ReminderRequest(ReminderKind.SAME_DAY), madrid),
        )
        assertEquals(
            Instant.parse("2026-11-20T06:00:00Z"),
            ReminderRules.triggerAt(day, null, ReminderRequest(ReminderKind.SAME_DAY), madrid),
        )
    }

    @Test
    fun sameDayNeverSlipsIntoTheNightBefore() {
        val at = ReminderRules.triggerAt(day, LocalTime.of(0, 30), ReminderRequest(ReminderKind.SAME_DAY), madrid)
        assertEquals(day.atStartOfDay(madrid).toInstant(), at)
    }

    @Test
    fun theWallClockHoldsAcrossSummerTime() {
        // 2026-03-29 is the spring change in Spain: 19:00 the evening before is still 19:00 local.
        val summer = ReminderRules.triggerAt(LocalDate.parse("2026-03-30"), null, ReminderRequest(ReminderKind.PREVIOUS_DAY), madrid)
        assertEquals(Instant.parse("2026-03-29T17:00:00Z"), summer)
    }

    @Test
    fun customUsesTheChosenMoment() {
        val chosen = LocalDateTime.parse("2026-11-18T12:15:00")
        assertEquals(
            chosen.atZone(madrid).toInstant(),
            ReminderRules.triggerAt(day, null, ReminderRequest(ReminderKind.CUSTOM, chosen), madrid),
        )
    }

    @Test
    fun planningIsOptionalButNeverNonsense() {
        assertNull(ReminderRules.validate(null, emptyList()))
        assertNull(ReminderRules.validate(ActivityPlanning(), emptyList()))
        assertTrue(ActivityPlanning(crewText = "  ").isEmpty)
        assertEquals(
            PlanningViolation("expectedPeopleCount", "not_positive"),
            ReminderRules.validate(ActivityPlanning(expectedPeopleCount = 0), emptyList()),
        )
        assertEquals(
            PlanningViolation("expectedDurationMinutes", "not_positive"),
            ReminderRules.validate(ActivityPlanning(expectedDurationMinutes = -5), emptyList()),
        )
    }

    @Test
    fun remindersMustBeCompleteAndDistinct() {
        assertEquals(
            PlanningViolation("reminders", "custom_without_time"),
            ReminderRules.validate(null, listOf(ReminderRequest(ReminderKind.CUSTOM))),
        )
        assertEquals(
            PlanningViolation("reminders", "duplicate"),
            ReminderRules.validate(null, listOf(ReminderRequest(ReminderKind.SAME_DAY), ReminderRequest(ReminderKind.SAME_DAY))),
        )
        val a = LocalDateTime.parse("2026-11-18T12:00:00")
        assertNull(
            ReminderRules.validate(
                null,
                listOf(
                    ReminderRequest(ReminderKind.PREVIOUS_DAY),
                    ReminderRequest(ReminderKind.CUSTOM, a),
                    ReminderRequest(ReminderKind.CUSTOM, a.plusHours(2)),
                ),
            ),
        )
    }
}
