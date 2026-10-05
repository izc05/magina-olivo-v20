package com.isivoltpro.maginaolivo.feature.maps

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import kotlin.math.abs

/** A pure geographic point used to choose a label position before MapLibre projection. */
internal data class ParcelLabelPoint(val latitude: Double, val longitude: Double)

/**
 * #574 — returns an interior label point for the visually dominant polygon component.
 *
 * This is a lightweight point-on-surface algorithm:
 * 1. choose the largest Polygon component by outer-ring area;
 * 2. use its geometric centroid when that point is truly inside and outside every hole;
 * 3. otherwise scan horizontal strips between vertex Y levels and choose the midpoint of the
 *    widest interior interval. For a valid non-zero-area polygon this fallback is inside.
 *
 * Geometry is never mutated and no topology repair is attempted here.
 */
internal fun parcelLabelPoint(geometry: String): ParcelLabelPoint? = runCatching {
    val root = JsonParser.parseString(geometry).asJsonObject
    val polygons = when (root.get("type")?.asString) {
        "Polygon" -> listOf(parsePolygon(root.getAsJsonArray("coordinates")))
        "MultiPolygon" -> root.getAsJsonArray("coordinates").map { parsePolygon(it.asJsonArray) }
        else -> emptyList()
    }.filter { it.isNotEmpty() && it.first().size >= 4 }

    val polygon = polygons.maxByOrNull { polygonArea(it) } ?: return@runCatching null
    val centroid = ringCentroid(polygon.first())
    val chosen = centroid?.takeIf { polygonContains(polygon, it) } ?: widestInteriorPoint(polygon)
    chosen?.let { ParcelLabelPoint(latitude = it.y, longitude = it.x) }
}.getOrNull()

private data class Point2(val x: Double, val y: Double)

private fun parsePolygon(array: JsonArray): List<List<Point2>> =
    array.mapNotNull { ring ->
        ring.takeIf(JsonElement::isJsonArray)?.asJsonArray?.mapNotNull { position ->
            val values = position.takeIf(JsonElement::isJsonArray)?.asJsonArray ?: return@mapNotNull null
            if (values.size() < 2) return@mapNotNull null
            val x = values[0].takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asDouble ?: return@mapNotNull null
            val y = values[1].takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asDouble ?: return@mapNotNull null
            if (!x.isFinite() || !y.isFinite()) return@mapNotNull null
            Point2(x, y)
        }?.takeIf { it.size >= 4 }
    }

private fun polygonArea(polygon: List<List<Point2>>): Double =
    polygon.firstOrNull()?.let(::ringArea) ?: 0.0

private fun ringArea(ring: List<Point2>): Double {
    if (ring.size < 3) return 0.0
    var twice = 0.0
    for (index in ring.indices) {
        val a = ring[index]
        val b = ring[(index + 1) % ring.size]
        twice += a.x * b.y - b.x * a.y
    }
    return abs(twice) / 2.0
}

private fun ringCentroid(ring: List<Point2>): Point2? {
    if (ring.size < 3) return null
    var crossSum = 0.0
    var xSum = 0.0
    var ySum = 0.0
    for (index in ring.indices) {
        val a = ring[index]
        val b = ring[(index + 1) % ring.size]
        val cross = a.x * b.y - b.x * a.y
        crossSum += cross
        xSum += (a.x + b.x) * cross
        ySum += (a.y + b.y) * cross
    }
    if (abs(crossSum) < EPSILON) return null
    return Point2(xSum / (3.0 * crossSum), ySum / (3.0 * crossSum))
}

private fun polygonContains(polygon: List<List<Point2>>, point: Point2): Boolean {
    val outer = polygon.firstOrNull() ?: return false
    return pointInRing(outer, point) && polygon.drop(1).none { pointInRing(it, point) }
}

private fun pointInRing(ring: List<Point2>, point: Point2): Boolean {
    var inside = false
    var previous = ring.last()
    for (current in ring) {
        val crosses = (current.y > point.y) != (previous.y > point.y)
        if (crosses) {
            val intersectionX = (previous.x - current.x) * (point.y - current.y) /
                (previous.y - current.y) + current.x
            if (point.x < intersectionX) inside = !inside
        }
        previous = current
    }
    return inside
}

/**
 * For each open horizontal strip between vertex Y values, pair edge intersections using the
 * even/odd rule. Holes naturally split the outer interval. The widest interval gives a stable,
 * readable point and its midpoint cannot sit on an edge because Y is between vertex levels.
 */
private fun widestInteriorPoint(polygon: List<List<Point2>>): Point2? {
    val levels = polygon.flatten().map { it.y }.distinct().sorted()
    var best: Pair<Double, Point2>? = null

    for (index in 0 until levels.lastIndex) {
        val low = levels[index]
        val high = levels[index + 1]
        if (high - low <= EPSILON) continue
        val y = (low + high) / 2.0
        val xs = polygon.flatMap { ring -> ringIntersections(ring, y) }.sorted()
        var i = 0
        while (i + 1 < xs.size) {
            val left = xs[i]
            val right = xs[i + 1]
            val width = right - left
            if (width > EPSILON) {
                val candidate = Point2((left + right) / 2.0, y)
                if (polygonContains(polygon, candidate) && (best == null || width > best!!.first)) {
                    best = width to candidate
                }
            }
            i += 2
        }
    }
    return best?.second
}

private fun ringIntersections(ring: List<Point2>, y: Double): List<Double> {
    val xs = mutableListOf<Double>()
    for (index in ring.indices) {
        val a = ring[index]
        val b = ring[(index + 1) % ring.size]
        if (abs(a.y - b.y) <= EPSILON) continue
        val crosses = (a.y <= y && y < b.y) || (b.y <= y && y < a.y)
        if (!crosses) continue
        val ratio = (y - a.y) / (b.y - a.y)
        xs += a.x + ratio * (b.x - a.x)
    }
    return xs
}

private const val EPSILON = 1e-12
