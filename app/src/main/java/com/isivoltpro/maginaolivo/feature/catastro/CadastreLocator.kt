package com.isivoltpro.maginaolivo.feature.catastro

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
