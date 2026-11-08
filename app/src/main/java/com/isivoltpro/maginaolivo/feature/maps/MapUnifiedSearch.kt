package com.isivoltpro.maginaolivo.feature.maps

import java.util.UUID

/** What the farmer typed in the map's single search box. Every outcome maps to an existing service. */
sealed interface MapSearchIntent {
    data object Empty : MapSearchIntent
    data class Coordinates(val point: GeoPoint) : MapSearchIntent
    data class CadastralReference(val reference: String) : MapSearchIntent
    data class PolygonParcel(val polygon: String, val parcel: String, val municipality: String?) : MapSearchIntent
    data class Place(val text: String) : MapSearchIntent
    data class NeedsClarification(val message: String) : MapSearchIntent
}

private val POLYGON_PARCEL = Regex(
    "^\\s*pol(?:[ií]gono)?\\.?\\s*(\\d{1,5})\\s*[,;·/-]?\\s*parc?(?:ela)?\\.?\\s*(\\d{1,5})\\s*[,;·-]?\\s*(.*)$",
    RegexOption.IGNORE_CASE,
)
private val BARE_NUMBERS = Regex("^\\s*\\d+\\s*(?:[,;/\\s]\\s*\\d+\\s*)*$")

/** Deterministic and offline: it never guesses a location, it only chooses which service to ask. */
fun classifyMapSearch(text: String): MapSearchIntent {
    val input = text.trim()
    if (input.isEmpty()) return MapSearchIntent.Empty
    POLYGON_PARCEL.matchEntire(input)?.let { match ->
        val (polygon, parcel, rest) = match.destructured
        return MapSearchIntent.PolygonParcel(polygon, parcel, rest.trim().takeIf { it.isNotEmpty() })
    }
    // Two whole numbers are more likely a polygon and a parcel than a point on the map.
    if (BARE_NUMBERS.matches(input)) {
        return MapSearchIntent.NeedsClarification("Escribe «polígono 5 parcela 12» o unas coordenadas con decimales.")
    }
    parseCoordinates(input)?.let { return MapSearchIntent.Coordinates(it) }
    val compact = input.replace(Regex("[\\s-]"), "").uppercase()
    if ((compact.length == 14 || compact.length == 20) && compact.all { it in 'A'..'Z' || it in '0'..'9' } &&
        compact.any(Char::isDigit) && compact.any(Char::isLetter)
    ) {
        return MapSearchIntent.CadastralReference(compact.take(14))
    }
    return MapSearchIntent.Place(input)
}

data class MapOwnMatch(val id: UUID, val label: String, val isFarm: Boolean)

/** The farmer's own farms and this farm's parcels, matched on the phone without any request. */
fun ownMapMatches(
    query: String,
    farms: List<Pair<UUID, String>>,
    parcels: List<Pair<UUID, String>>,
    limit: Int = 5,
): List<MapOwnMatch> {
    val name = normalizedPlaceName(query)
    if (name.length < 2) return emptyList()
    fun matches(candidate: String) = normalizedPlaceName(candidate).contains(name)
    return (farms.filter { matches(it.second) }.map { MapOwnMatch(it.first, "Finca · ${it.second}", true) } +
        parcels.filter { matches(it.second) }.map { MapOwnMatch(it.first, "Parcela · ${it.second}", false) }).take(limit)
}
