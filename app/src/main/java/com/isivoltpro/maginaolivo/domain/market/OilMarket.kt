package com.isivoltpro.maginaolivo.domain.market

import com.isivoltpro.maginaolivo.domain.feed.FeedState
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import kotlinx.coroutines.flow.Flow

/**
 * Phase 20D — olive-oil market reference (docs/07-plans/PHASE20D-OIL-MARKET-CONTRACT.md).
 * Official observations keep their source, geography, category and period; nothing is averaged
 * across sources, nothing missing is filled in, and a weekly source is never called "hoy".
 */
enum class OilCategory(val label: String) {
    AOVE("AOVE"),
    AOV("Virgen"),
    AOL("Lampante"),
}

enum class TrendDirection { UP, DOWN, FLAT }

/** One observed price of one category in one period, exactly as the source published it. */
data class OilObservation(
    val category: OilCategory,
    val periodStart: LocalDate,
    val periodEnd: LocalDate,
    /** Normalised to €/kg without rounding (e.g. 346.89 €/100 kg → 3.4689). */
    val valueEurPerKg: BigDecimal,
    val originalValue: BigDecimal,
    val originalUnit: String,
)

/** One source + geography: its observations, per category, possibly with gaps. */
data class OilMarketSeries(
    val sourceId: String,
    val sourceName: String,
    val geographyCode: String,
    val geographyName: String,
    val marketStage: String?,
    val observations: List<OilObservation>,
)

/** A category's latest official value and its change from the immediately previous period. */
data class OilTrend(
    val category: OilCategory,
    val latest: OilObservation,
    val previous: OilObservation?,
    val absolute: BigDecimal?,
    val percent: BigDecimal?,
    val direction: TrendDirection?,
)

object OilTrends {
    /**
     * The latest value of [category] and, only when the source published the period right before
     * it, the change (`current - previous`, `(current - previous) / previous · 100`). A gap in the
     * series gives a value without a trend: a missing week is never bridged.
     */
    fun of(series: OilMarketSeries, category: OilCategory): OilTrend? {
        val own = series.observations.filter { it.category == category }.sortedBy { it.periodStart }
        val latest = own.lastOrNull() ?: return null
        val previous = own.getOrNull(own.size - 2)?.takeIf { it.periodEnd.plusDays(1) == latest.periodStart }
        if (previous == null) return OilTrend(category, latest, null, null, null, null)
        val absolute = latest.valueEurPerKg - previous.valueEurPerKg
        val percent = if (previous.valueEurPerKg.signum() == 0) {
            null
        } else {
            absolute.multiply(BigDecimal(100)).divide(previous.valueEurPerKg, 4, RoundingMode.HALF_UP)
        }
        val direction = when (absolute.signum()) {
            1 -> TrendDirection.UP
            -1 -> TrendDirection.DOWN
            else -> TrendDirection.FLAT
        }
        return OilTrend(category, latest, previous, absolute, percent, direction)
    }

    fun all(series: OilMarketSeries): List<OilTrend> = OilCategory.entries.mapNotNull { of(series, it) }

    /** "AOVE ↓ 5,7 % esta semana"; a first value with nothing to compare reads just its price. */
    fun label(trend: OilTrend): String {
        val arrow = when (trend.direction) {
            TrendDirection.UP -> "↑"
            TrendDirection.DOWN -> "↓"
            TrendDirection.FLAT -> "="
            null -> return "${trend.category.label} ${euros(trend.latest.valueEurPerKg)}"
        }
        val change = trend.percent?.abs()?.setScale(1, RoundingMode.HALF_UP)?.toPlainString()?.replace('.', ',')
        return if (trend.direction == TrendDirection.FLAT || change == null) {
            "${trend.category.label} $arrow sin cambio esta semana"
        } else {
            "${trend.category.label} $arrow $change % esta semana"
        }
    }

    /** "3,46 €/kg" — two decimals for reading; the stored value keeps its own precision. */
    fun euros(value: BigDecimal): String = value.setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',') + " €/kg"

    /** ISO week of a period ("semana 38"), as the Junta and MAPA name their weeks. */
    fun week(observation: OilObservation): Int = observation.periodStart.get(WeekFields.ISO.weekOfWeekBasedYear())

    /**
     * A weekly value stays current until the next week would normally be published, plus a
     * margin: 7 days after its period ends, then [marginDays]. Past that it is shown as old.
     */
    fun isOutdated(observation: OilObservation, today: LocalDate, marginDays: Long = 7): Boolean =
        ChronoUnit.DAYS.between(observation.periodEnd, today) > 7 + marginDays
}

/** What the backend returned for one request, with the time it was fetched. */
data class OilMarketReading(val series: List<OilMarketSeries>, val fetchedAt: Instant)

interface OilMarketFeed {
    fun observe(): Flow<FeedState<OilMarketSeries>>

    suspend fun refreshIfStale()
}
