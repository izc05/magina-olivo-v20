package com.isivoltpro.maginaolivo.domain.agenda

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

/**
 * How planned work is expected to go (`RC1.2-PRODUCT-LOCK` §9,
 * `DATA-MODEL-RC1.2-ADDENDUM` §6). Every field is optional: planning is a help, never a
 * form to fill. The day is the Activity's own date; this only adds the hour and the crew.
 *
 * When the work is done the same Activity is completed — planning never creates a
 * second record.
 */
data class ActivityPlanning(
    val startTime: LocalTime? = null,
    val expectedDurationMinutes: Int? = null,
    val expectedPeopleCount: Int? = null,
    /** Who is expected: a crew, a contractor, a provider — as the farmer names them. */
    val crewText: String? = null,
) {
    val isEmpty: Boolean
        get() = startTime == null && expectedDurationMinutes == null &&
            expectedPeopleCount == null && crewText.isNullOrBlank()
}

enum class ReminderKind {
    /** The evening before, at [ReminderRules.PREVIOUS_DAY_TIME]. */
    PREVIOUS_DAY,

    /** The same day: an hour before the planned time, or early morning when there is none. */
    SAME_DAY,

    /** A moment the farmer chose. */
    CUSTOM,
}

/** What the farmer asked for. [customAt] is required for, and only read by, CUSTOM. */
data class ReminderRequest(
    val kind: ReminderKind,
    val customAt: LocalDateTime? = null,
)

/** A stored local reminder. [firedAt] is set once the notification was shown. */
data class Reminder(
    val id: UUID,
    val kind: ReminderKind,
    val triggerAt: Instant,
    val enabled: Boolean,
    val firedAt: Instant?,
    /** The chosen local moment, for CUSTOM reminders; read back in the farmer's zone. */
    val customAt: LocalDateTime? = null,
) {
    fun toRequest() = ReminderRequest(kind, customAt.takeIf { kind == ReminderKind.CUSTOM })
}

data class PlanningViolation(val field: String, val code: String)

object ReminderRules {
    val PREVIOUS_DAY_TIME: LocalTime = LocalTime.of(19, 0)
    val SAME_DAY_EARLY_TIME: LocalTime = LocalTime.of(7, 0)
    const val SAME_DAY_LEAD_MINUTES = 60L

    /**
     * The moment a reminder fires for work planned on [date] (at [startTime], if known),
     * read in the farmer's [zone] so a change to or from summer time keeps the wall clock.
     */
    fun triggerAt(date: LocalDate, startTime: LocalTime?, request: ReminderRequest, zone: ZoneId): Instant {
        val local = when (request.kind) {
            ReminderKind.PREVIOUS_DAY -> date.minusDays(1).atTime(PREVIOUS_DAY_TIME)
            ReminderKind.SAME_DAY -> when (startTime) {
                null -> date.atTime(SAME_DAY_EARLY_TIME)
                // Work planned just after midnight still warns on its own day, never the night before.
                else -> maxOf(date.atTime(startTime).minusMinutes(SAME_DAY_LEAD_MINUTES), date.atStartOfDay())
            }
            ReminderKind.CUSTOM -> requireNotNull(request.customAt) { "custom reminder without a time" }
        }
        return local.atZone(zone).toInstant()
    }

    fun validate(planning: ActivityPlanning?, reminders: List<ReminderRequest>): PlanningViolation? {
        if (planning != null) {
            planning.expectedPeopleCount?.let { if (it < 1) return PlanningViolation("expectedPeopleCount", "not_positive") }
            planning.expectedDurationMinutes?.let { if (it <= 0) return PlanningViolation("expectedDurationMinutes", "not_positive") }
        }
        if (reminders.any { it.kind == ReminderKind.CUSTOM && it.customAt == null }) {
            return PlanningViolation("reminders", "custom_without_time")
        }
        val keys = reminders.map { if (it.kind == ReminderKind.CUSTOM) it.kind to it.customAt else it.kind to null }
        if (keys.size != keys.toSet().size) return PlanningViolation("reminders", "duplicate")
        return null
    }
}

/**
 * Rebuilds the device alarms from the stored reminders. Called after every change to
 * planned work, at app start and after a reboot; it is idempotent.
 */
fun interface ReminderReconciler {
    suspend fun reconcile()
}
