package com.isivoltpro.maginaolivo.domain.feed

import java.time.Duration
import java.time.Instant
import java.text.Normalizer

/**
 * Phase 20A — an external feed on Inicio (weather, oil market, cooperative). Feeds are
 * context, never a dependency: the farm, the campaign and the work always render without
 * them, and a feed value is only ever shown with its source and when it was fetched.
 */
enum class FeedKind(val freshFor: Duration) {
    WEATHER(Duration.ofHours(3)),
    OIL_MARKET(Duration.ofHours(24)),
    COOPERATIVE(Duration.ofHours(24)),
}

/** What Inicio can honestly say about a feed. */
sealed interface FeedState<out T> {
    /** No source is set up for this feed on this build (no provider, no key). */
    data object NotConfigured : FeedState<Nothing>

    /** The feed needs a place and the farms do not give one yet. */
    data object NoLocation : FeedState<Nothing>

    /** Nothing fetched yet and nothing cached (offline, or the source failed). */
    data object Unavailable : FeedState<Nothing>

    /** The last value fetched, with its source and time; `stale` past the feed's window. */
    data class Value<T>(val value: T, val source: String, val fetchedAt: Instant, val stale: Boolean) : FeedState<T>
}

object FeedAge {
    fun isStale(kind: FeedKind, fetchedAt: Instant, now: Instant): Boolean =
        Duration.between(fetchedAt, now) > kind.freshFor

    /** "Actualizado hace 5 min". A time in the future (clock change) reads as "ahora". */
    fun label(fetchedAt: Instant, now: Instant): String {
        val minutes = Duration.between(fetchedAt, now).toMinutes()
        return "Actualizado " + when {
            minutes < 2 -> "ahora"
            minutes < 60 -> "hace $minutes min"
            minutes < 48 * 60 -> "hace ${minutes / 60} h"
            else -> "hace ${minutes / (24 * 60)} días"
        }
    }
}

/**
 * The place a feed is asked about. It comes from the farms (no GPS): their municipality and
 * province when every farm shares one place. `key` is stable across spellings and accents.
 */
data class FeedLocation(val municipality: String, val province: String?) {
    val label: String get() = listOfNotNull(municipality, province).joinToString(", ")
    val key: String get() = listOfNotNull(municipality, province).joinToString("|") { normalize(it) }

    companion object {
        /** The one place of these (municipality, province) pairs, or null when none or several. */
        fun common(places: List<Pair<String?, String?>>): FeedLocation? = places
            .mapNotNull { (municipality, province) ->
                municipality?.trim()?.takeIf { it.isNotEmpty() }?.let { FeedLocation(it, province?.trim()?.takeIf(String::isNotEmpty)) }
            }
            .distinctBy { it.key }
            .singleOrNull()

        private fun normalize(text: String): String =
            Normalizer.normalize(text.trim().lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
    }
}
