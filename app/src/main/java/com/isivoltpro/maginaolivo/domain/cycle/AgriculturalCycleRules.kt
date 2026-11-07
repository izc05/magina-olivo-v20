package com.isivoltpro.maginaolivo.domain.cycle

import java.time.LocalDate

/** Inclusive civil dates. Labels and the date on which a closure is entered are not boundaries. */
data class CycleInterval(val start: LocalDate, val end: LocalDate?) {
    fun contains(date: LocalDate, lastDate: LocalDate = date): Boolean =
        !lastDate.isBefore(date) && !date.isBefore(start) && (end == null || !lastDate.isAfter(end))
}

object AgriculturalCycleRules {
    fun overlaps(a: CycleInterval, b: CycleInterval): Boolean =
        (a.end == null || !b.start.isAfter(a.end)) && (b.end == null || !a.start.isAfter(b.end))

    fun closeError(
        interval: CycleInterval,
        effectiveEnd: LocalDate,
        today: LocalDate,
        lastPhysicalDate: LocalDate? = null,
    ): String? = when {
        interval.end != null -> "already_closed"
        effectiveEnd.isBefore(interval.start) -> "before_start"
        effectiveEnd.isAfter(today) -> "future_end"
        lastPhysicalDate != null && lastPhysicalDate.isAfter(effectiveEnd) -> "records_after_end"
        else -> null
    }
}
