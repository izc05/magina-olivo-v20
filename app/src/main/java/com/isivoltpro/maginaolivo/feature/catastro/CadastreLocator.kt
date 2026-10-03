package com.isivoltpro.maginaolivo.feature.catastro

import com.isivoltpro.maginaolivo.domain.registry.RegistryLocation
import java.net.URLEncoder
import java.text.Normalizer
import org.w3c.dom.Element

/**
 * Query string for Catastro's `Consulta_DNPPP` (province + municipality + rural polygon +
 * parcel). Names are sent the way Catastro lists them: upper case, without accents. Returns
 * null when a field is missing or the numbers are not numbers, so nothing is sent blindly.
 * Accents are dropped but Ñ is kept, as in Catastro's own municipality list.
 */
internal fun polygonParcelQuery(province: String, municipality: String, polygon: String, parcel: String): String? {
    val provinceName = placeName(province) ?: return null
    val municipalityName = placeName(municipality) ?: return null
    val polygonNumber = polygon.trim().trimStart('0').ifEmpty { "0" }.takeIf { it.matches(DIGITS) } ?: return null
    val parcelNumber = parcel.trim().trimStart('0').ifEmpty { "0" }.takeIf { it.matches(DIGITS) } ?: return null
    if (polygonNumber == "0" || parcelNumber == "0") return null
    fun encode(value: String) = URLEncoder.encode(value, "UTF-8")
    return "Provincia=${encode(provinceName)}&Municipio=${encode(municipalityName)}" +
        "&Poligono=$polygonNumber&Parcela=$parcelNumber"
}

/**
 * 14-character references in a `Consulta_DNPPP` answer (`pc1` + `pc2` of each `rc`), in the
 * order Catastro gives them. An error answer (`lerr`) means "not found", never a guess.
 */
internal fun parseDnpppReferences(xml: ByteArray): List<String> {
    val root = parseSecureXml(xml)
    if (root.getElementsByTagNameNS("*", "err").length > 0) return emptyList()
    val codes = root.getElementsByTagNameNS("*", "rc")
    return (0 until codes.length).mapNotNull { index ->
        val rc = codes.item(index) as? Element ?: return@mapNotNull null
        val first = rc.child("pc1") ?: return@mapNotNull null
        val second = rc.child("pc2") ?: return@mapNotNull null
        (first + second).uppercase().takeIf { it.matches(REFERENCE) }
    }.distinct()
}

/**
 * Municipality and province of a reference from Catastro's `Consulta_DNPRC`. Catastro writes
 * names in capitals without accents; the province is taken from its official code (with its
 * accents) and the municipality is shown in normal case. Null on an error answer or a missing
 * name: the farmer then types it.
 */
internal fun parseDnprcLocation(xml: ByteArray): RegistryLocation? {
    val root = parseSecureXml(xml)
    if (root.getElementsByTagNameNS("*", "err").length > 0) return null
    val municipality = root.child("nm")?.let(::spanishPlaceCase) ?: return null
    val province = root.child("cp")?.toIntOrNull()?.let(SPANISH_PROVINCES::get)
        ?: root.child("np")?.let(::spanishPlaceCase)
    return RegistryLocation(municipality, province)
}

/** "BEDMAR Y GARCIEZ" → "Bedmar y Garciez"; "VILLANUEVA DEL ARZOBISPO" → "Villanueva del Arzobispo". */
internal fun spanishPlaceCase(name: String): String? {
    val words = name.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (words.isEmpty()) return null
    return words.mapIndexed { index, word ->
        if (index > 0 && word in PARTICLES) word
        else word.split('-').joinToString("-") { part -> part.replaceFirstChar { it.titlecase() } }
    }.joinToString(" ")
}

private val PARTICLES = setOf("y", "e", "de", "del", "la", "las", "el", "los", "en", "a")

/** INE/Catastro province codes and their official names. */
internal val SPANISH_PROVINCES: Map<Int, String> = mapOf(
    1 to "Álava", 2 to "Albacete", 3 to "Alicante", 4 to "Almería", 5 to "Ávila", 6 to "Badajoz",
    7 to "Illes Balears", 8 to "Barcelona", 9 to "Burgos", 10 to "Cáceres", 11 to "Cádiz", 12 to "Castellón",
    13 to "Ciudad Real", 14 to "Córdoba", 15 to "A Coruña", 16 to "Cuenca", 17 to "Girona", 18 to "Granada",
    19 to "Guadalajara", 20 to "Gipuzkoa", 21 to "Huelva", 22 to "Huesca", 23 to "Jaén", 24 to "León",
    25 to "Lleida", 26 to "La Rioja", 27 to "Lugo", 28 to "Madrid", 29 to "Málaga", 30 to "Murcia",
    31 to "Navarra", 32 to "Ourense", 33 to "Asturias", 34 to "Palencia", 35 to "Las Palmas", 36 to "Pontevedra",
    37 to "Salamanca", 38 to "Santa Cruz de Tenerife", 39 to "Cantabria", 40 to "Segovia", 41 to "Sevilla",
    42 to "Soria", 43 to "Tarragona", 44 to "Teruel", 45 to "Toledo", 46 to "Valencia", 47 to "Valladolid",
    48 to "Bizkaia", 49 to "Zamora", 50 to "Zaragoza", 51 to "Ceuta", 52 to "Melilla",
)

private fun Element.child(name: String): String? =
    getElementsByTagNameNS("*", name).item(0)?.textContent?.trim()?.takeIf { it.isNotEmpty() }

private fun placeName(value: String): String? {
    // Accents go, Ñ stays: Catastro writes "BAÑOS DE LA ENCINA", not "BANOS".
    val kept = value.trim().uppercase().replace('Ñ', '\u0000')
    val plain = Normalizer.normalize(kept, Normalizer.Form.NFD).replace(ACCENTS, "").replace('\u0000', 'Ñ')
    return plain.takeIf { it.length in 2..60 && it.all { c -> c.isLetter() || c == ' ' || c == '-' || c == '\'' } }
}

private val ACCENTS = Regex("\\p{Mn}+")
private val DIGITS = Regex("\\d{1,5}")
private val REFERENCE = Regex("[A-Z0-9]{14}")
