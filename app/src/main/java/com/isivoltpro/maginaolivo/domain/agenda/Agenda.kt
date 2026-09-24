package com.isivoltpro.maginaolivo.domain.agenda

import java.time.LocalDate
import java.time.LocalTime

/** Where planned work falls, seen from today. The order is the order shown. */
enum class AgendaBucket {
    /** Still planned, but its day has passed: done, cancelled or moved — the farmer decides. */
    OVERDUE,
    TODAY,
    TOMORROW,
    NEXT_7_DAYS,
    LATER,
}

data class AgendaSection<T>(val bucket: AgendaBucket, val items: List<T>)

object Agenda {
    fun bucketOf(date: LocalDate, today: LocalDate): AgendaBucket = when {
        date.isBefore(today) -> AgendaBucket.OVERDUE
        date == today -> AgendaBucket.TODAY
        date == today.plusDays(1) -> AgendaBucket.TOMORROW
        !date.isAfter(today.plusDays(7)) -> AgendaBucket.NEXT_7_DAYS
        else -> AgendaBucket.LATER
    }

    /**
     * Groups planned work into non-empty sections, in bucket order. Inside a section work
     * runs by day, then by planned hour; work with no hour opens its day.
     */
    fun <T> group(
        items: List<T>,
        today: LocalDate,
        dateOf: (T) -> LocalDate,
        timeOf: (T) -> LocalTime?,
    ): List<AgendaSection<T>> {
        val ordered = items.sortedWith(
            compareBy<T>(dateOf).thenBy(nullsFirst()) { timeOf(it) },
        )
        return ordered.groupBy { bucketOf(dateOf(it), today) }
            .toSortedMap()
            .map { (bucket, sectionItems) -> AgendaSection(bucket, sectionItems) }
    }
}
