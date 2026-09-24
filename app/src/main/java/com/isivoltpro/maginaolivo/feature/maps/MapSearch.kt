package com.isivoltpro.maginaolivo.feature.maps

/** A point the farmer asked the map to show: latitude, longitude (WGS84). */
data class GeoPoint(val latitude: Double, val longitude: Double)

/**
 * Reads the coordinates a farmer is likely to paste: Google Maps decimal pairs
 * ("37.63601, -3.48012"), Spanish decimal commas ("37,636 -3,480") and degrees/minutes/seconds
 * ("37°38'09.6\"N 3°28'48.4\"W"). Returns null for anything else, never a guessed point.
 */
fun parseCoordinates(text: String): GeoPoint? {
    val input = text.trim().replace('’', '\'').replace('″', '"').replace('′', '\'')
    if (input.isEmpty()) return null
    return (parseDms(input) ?: parseDecimal(input))?.takeIf { it.latitude in -90.0..90.0 && it.longitude in -180.0..180.0 }
}

private fun parseDecimal(input: String): GeoPoint? {
    // "37.6, -3.4" / "37.6 -3.4" / "37,6 -3,4" / "37,6; -3,4".
    val numbers = Regex("[-+]?\\d{1,3}(?:[.,]\\d+)?").findAll(input).map { it.value }.toList()
    val cleaned = input.replace(Regex("[-+]?\\d{1,3}(?:[.,]\\d+)?"), "").replace(Regex("[\\s,;]"), "")
    if (numbers.size != 2 || cleaned.isNotEmpty()) return null
    val (latitude, longitude) = numbers.map { it.replace(',', '.').toDoubleOrNull() ?: return null }
    return GeoPoint(latitude, longitude)
}

private fun parseDms(input: String): GeoPoint? {
    val part = Regex("(\\d{1,3})\\s*°\\s*(?:(\\d{1,2})\\s*'\\s*)?(?:(\\d{1,2}(?:[.,]\\d+)?)\\s*\"\\s*)?([NSEWOnsewo])")
    val matches = part.findAll(input).toList()
    if (matches.size != 2) return null
    var latitude: Double? = null
    var longitude: Double? = null
    matches.forEach { match ->
        val (degrees, minutes, seconds, hemisphere) = match.destructured
        val minuteValue = minutes.toDoubleOrNull() ?: 0.0
        val secondValue = seconds.replace(',', '.').toDoubleOrNull() ?: 0.0
        if (minuteValue >= 60 || secondValue >= 60) return null
        val value = degrees.toDouble() + minuteValue / 60 + secondValue / 3600
        when (hemisphere.uppercase()) {
            "N" -> latitude = value
            "S" -> latitude = -value
            "E" -> longitude = value
            // "O" (oeste) is how Spanish sources write West.
            "W", "O" -> longitude = -value
        }
    }
    return if (latitude != null && longitude != null) GeoPoint(latitude!!, longitude!!) else null
}
