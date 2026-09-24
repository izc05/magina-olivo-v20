package com.isivoltpro.maginaolivo.domain.registry

import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.operation.valid.IsValidOp

/** Validation does not repair or simplify the provider's authoritative vertices. */
fun validateParcelGeometry(polygons: List<List<List<Pair<Double, Double>>>>) {
    require(polygons.isNotEmpty() && polygons.size <= 64)
    val factory = GeometryFactory()
    var vertices = 0
    val parts = polygons.map { rings ->
        require(rings.isNotEmpty() && rings.size <= 65)
        val linear = rings.map { ring ->
            vertices += ring.size
            require(vertices <= 20_000)
            require(ring.size >= 4 && ring.first() == ring.last())
            require(ring.all { (x, y) -> x.isFinite() && y.isFinite() && x in -180.0..180.0 && y in -85.0..85.0 })
            factory.createLinearRing(ring.map { Coordinate(it.first, it.second) }.toTypedArray())
        }
        factory.createPolygon(linear.first(), linear.drop(1).toTypedArray())
    }
    val geometry = factory.createMultiPolygon(parts.toTypedArray())
    require(IsValidOp(geometry).isValid && geometry.area > 0) { "Invalid parcel topology" }
}

fun ParcelCandidate.contains(longitude: Double, latitude: Double): Boolean {
    val factory = GeometryFactory()
    return polygons.any { rings ->
        val linear = rings.map { ring -> factory.createLinearRing(ring.map { Coordinate(it.first, it.second) }.toTypedArray()) }
        factory.createPolygon(linear.first(), linear.drop(1).toTypedArray())
            .covers(factory.createPoint(Coordinate(longitude, latitude)))
    }
}
