package com.isivoltpro.maginaolivo.domain.registry

/** Public parcel identity and geometry only; no ownership or protected Catastro data. */
data class ParcelCandidate(
    val reference: String,
    val provider: String = "ES_CATASTRO",
    val importedAt: java.time.Instant = java.time.Instant.now(),
    val areaM2: Double?,
    /** Polygons, then rings, then WGS84 longitude/latitude points. Ring 0 is the exterior. */
    val polygons: List<List<List<Pair<Double, Double>>>>,
) {
    val geometryGeoJson: String get() = buildString {
        val multi = polygons.size > 1
        append("{\"type\":\"").append(if (multi) "MultiPolygon" else "Polygon").append("\",\"coordinates\":")
        if (multi) append('[')
        polygons.forEachIndexed { polygonIndex, rings ->
            if (polygonIndex > 0) append(',')
            append('[')
            rings.forEachIndexed { ringIndex, points ->
                if (ringIndex > 0) append(',')
                append('[')
                points.forEachIndexed { pointIndex, point ->
                    if (pointIndex > 0) append(',')
                    append('[').append(point.first).append(',').append(point.second).append(']')
                }
                append(']')
            }
            append(']')
        }
        if (multi) append(']')
        append('}')
    }
}

