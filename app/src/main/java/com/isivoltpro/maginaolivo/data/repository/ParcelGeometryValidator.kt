package com.isivoltpro.maginaolivo.data.repository

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.isivoltpro.maginaolivo.core.common.AppError
import com.isivoltpro.maginaolivo.core.common.AppResult

/**
 * #580 — cheap local integrity gate for parcel GeoJSON.
 *
 * It deliberately validates only the structural GeoJSON contract that Android can prove
 * cheaply. Full topology (self-intersections, holes outside the shell, etc.) belongs to the
 * backend/PostGIS gate before cloud or official registry data is accepted.
 */
internal fun validateParcelGeometryGeoJson(value: String?): AppResult.Failure? {
    val geometry = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val root = runCatching { JsonParser.parseString(geometry) }.getOrNull()
        ?: return invalidGeometry()
    if (!root.isJsonObject) return invalidGeometry()

    val objectRoot = root.asJsonObject
    val type = objectRoot.get("type")
        ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
        ?.asString
        ?: return invalidGeometry()

    if (type != "Polygon" && type != "MultiPolygon") return polygonRequired()

    val coordinates = objectRoot.get("coordinates")
        ?.takeIf(JsonElement::isJsonArray)
        ?.asJsonArray
        ?: return invalidGeometry()

    val valid = when (type) {
        "Polygon" -> validPolygon(coordinates)
        "MultiPolygon" -> coordinates.size() > 0 && coordinates.all { polygon ->
            polygon.isJsonArray && validPolygon(polygon.asJsonArray)
        }
        else -> false
    }
    return if (valid) null else invalidGeometry()
}

private fun validPolygon(polygon: JsonArray): Boolean =
    polygon.size() > 0 && polygon.all { ring -> ring.isJsonArray && validRing(ring.asJsonArray) }

private fun validRing(ring: JsonArray): Boolean {
    if (ring.size() < MIN_RING_POSITIONS) return false
    val positions = ring.map(::position) 
    if (positions.any { it == null }) return false
    val valid = positions.filterNotNull()
    val first = valid.first()
    val last = valid.last()
    return first.longitude == last.longitude && first.latitude == last.latitude
}

private data class Position(val longitude: Double, val latitude: Double)

private fun position(element: JsonElement): Position? {
    if (!element.isJsonArray) return null
    val values = element.asJsonArray
    if (values.size() < 2) return null

    val numeric = values.map { coordinate ->
        if (!coordinate.isJsonPrimitive || !coordinate.asJsonPrimitive.isNumber) return null
        val value = runCatching { coordinate.asDouble }.getOrNull() ?: return null
        if (!value.isFinite()) return null
        value
    }
    val longitude = numeric[0]
    val latitude = numeric[1]
    if (longitude !in -180.0..180.0 || latitude !in -90.0..90.0) return null
    return Position(longitude, latitude)
}

private fun polygonRequired() =
    AppResult.Failure(AppError.Validation("geometry", "polygon_required"))

private fun invalidGeometry() =
    AppResult.Failure(AppError.Validation("geometry", "invalid_geojson"))

private const val MIN_RING_POSITIONS = 4
