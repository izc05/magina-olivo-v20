package com.isivoltpro.maginaolivo.feature.sigpac

import com.google.gson.JsonArray
import com.google.gson.JsonParser
import com.isivoltpro.maginaolivo.feature.maps.parcelLabelPoint
import com.isivoltpro.maginaolivo.feature.maps.polygonsCoverInterior
import java.time.Instant

/**
 * Which official recintos can be shown as this parcel's. Those that fall outside the saved contour
 * are kept apart and never counted, so a wrong agregado, zona or municipality cannot add them.
 */
data class SigpacAssessment(
    val matched: List<SigpacRecinto>,
    val outside: List<SigpacRecinto>,
    /** True when there is no saved contour or official geometry to compare against. */
    val unverified: Boolean,
) {
    /** Null if any counted recinto has no official surface: a partial sum would mislead. */
    val totalAreaHa: Double? get() = sigpacAreaSum(matched)
    val useCodes: List<String> get() = matched.groupBy { it.useCode }
        .entries.sortedByDescending { (_, list) -> list.sumOf { it.areaHa ?: 0.0 } }.mapNotNull { it.key }
}

/** Each recinto counts once. */
fun sigpacAreaSum(recintos: List<SigpacRecinto>): Double? {
    val distinct = recintos.distinctBy { it.recinto }
    if (distinct.isEmpty() || distinct.any { it.areaHa == null }) return null
    return distinct.sumOf { it.areaHa!! }
}

fun assessSigpacRecintos(recintos: List<SigpacRecinto>, parcelGeometry: String?): SigpacAssessment {
    val parcel = parcelGeometry?.let(::geoJsonPolygons)
    if (parcel == null || recintos.any { it.polygons == null }) {
        return SigpacAssessment(recintos, emptyList(), unverified = true)
    }
    val (matched, outside) = recintos.partition { overlaps(it.polygons!!, parcel, parcelGeometry) }
    return SigpacAssessment(matched, outside, unverified = false)
}

private fun overlaps(
    recinto: List<List<List<Pair<Double, Double>>>>,
    parcel: List<List<List<Pair<Double, Double>>>>,
    parcelGeometry: String,
): Boolean {
    val recintoPoint = parcelLabelPoint(toGeoJson(recinto)) ?: return false
    if (polygonsCoverInterior(parcel, recintoPoint.longitude, recintoPoint.latitude)) return true
    val parcelPoint = parcelLabelPoint(parcelGeometry) ?: return false
    return polygonsCoverInterior(recinto, parcelPoint.longitude, parcelPoint.latitude)
}

private fun toGeoJson(polygons: List<List<List<Pair<Double, Double>>>>): String {
    val body = polygons.joinToString(",") { rings ->
        rings.joinToString(",", "[", "]") { ring -> ring.joinToString(",", "[", "]") { (x, y) -> "[$x,$y]" } }
    }
    return """{"type":"MultiPolygon","coordinates":[$body]}"""
}

internal fun geoJsonPolygons(geometry: String): List<List<List<Pair<Double, Double>>>>? = runCatching {
    val root = JsonParser.parseString(geometry).asJsonObject
    val coordinates = root.getAsJsonArray("coordinates")
    fun rings(polygon: JsonArray) = polygon.map { ring ->
        ring.asJsonArray.map { position -> position.asJsonArray.let { it[0].asDouble to it[1].asDouble } }
    }
    when (root.get("type").asString) {
        "Polygon" -> listOf(rings(coordinates))
        "MultiPolygon" -> coordinates.map { rings(it.asJsonArray) }
        else -> null
    }
}.getOrNull()

sealed interface SigpacUiState {
    data object NotRequested : SigpacUiState
    data object Loading : SigpacUiState
    data class Available(val assessment: SigpacAssessment, val consultedAt: Instant) : SigpacUiState
    data object NoRecintos : SigpacUiState
    /** A later query failed; [previous] keeps what this screen had already read. */
    data class Unavailable(val error: SigpacError, val previous: Available?) : SigpacUiState
    data object Unsupported : SigpacUiState
}
