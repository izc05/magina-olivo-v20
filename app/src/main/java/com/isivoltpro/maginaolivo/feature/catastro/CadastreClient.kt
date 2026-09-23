package com.isivoltpro.maginaolivo.feature.catastro

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.w3c.dom.Element
import org.w3c.dom.Node

/** Public parcel identity and geometry only; no ownership or protected Catastro data. */
data class CadastralCandidate(
    val reference: String,
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

enum class CadastreError { INVALID_REFERENCE, NOT_FOUND, NETWORK, SERVICE, INVALID_GEOMETRY, RESPONSE }

class CadastreException(val kind: CadastreError, cause: Throwable? = null) : Exception(kind.name, cause)

interface CadastreClient {
    suspend fun findByReference(reference: String): CadastralCandidate
}

/** WFS GetParcel is kept behind this boundary; the rest of the app never sees GML. */
class OfficialCadastreClient : CadastreClient {
    override suspend fun findByReference(reference: String): CadastralCandidate = withContext(Dispatchers.IO) {
        val normalized = reference.trim().uppercase()
        if (!REFERENCE.matches(normalized)) throw CadastreException(CadastreError.INVALID_REFERENCE)
        val url = URL("$ENDPOINT?service=WFS&version=2.0.0&request=GetFeature" +
            "&STOREDQUERIE_ID=GetParcel&refcat=$normalized&srsname=EPSG::4326")
        try {
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("Accept", "application/gml+xml, application/xml, text/xml")
            try {
                if (connection.responseCode !in 200..299) throw CadastreException(CadastreError.SERVICE)
                val body = connection.inputStream.use { input ->
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (output.size() + count > MAX_XML_BYTES) throw CadastreException(CadastreError.RESPONSE)
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray()
                }
                parseCadastralGml(body, normalized)
            } finally {
                connection.disconnect()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (known: CadastreException) {
            throw known
        } catch (error: java.io.IOException) {
            throw CadastreException(CadastreError.NETWORK, error)
        } catch (error: Exception) {
            throw CadastreException(CadastreError.RESPONSE, error)
        }
    }

    companion object {
        private const val ENDPOINT = "https://ovc.catastro.meh.es/INSPIRE/wfsCP.aspx"
        private const val MAX_XML_BYTES = 2_000_000
        private val REFERENCE = Regex("[A-Z0-9]{14}")
    }
}

internal fun parseCadastralGml(xml: ByteArray, expectedReference: String): CadastralCandidate {
    if (xml.isEmpty() || xml.size > 2_000_000) throw CadastreException(CadastreError.RESPONSE)
    // Catastro GML never carries a DTD. Refusing one here is the XXE guard that works on every
    // parser: Android's DocumentBuilderFactory rejects the Xerces feature flags below, which
    // made every real lookup fail on the phone, so those stay best effort for the JVM.
    if (declaresDoctype(xml)) throw CadastreException(CadastreError.RESPONSE)
    val factory = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        listOf(
            "http://apache.org/xml/features/disallow-doctype-decl" to true,
            "http://xml.org/sax/features/external-general-entities" to false,
            "http://xml.org/sax/features/external-parameter-entities" to false,
            "http://apache.org/xml/features/nonvalidating/load-external-dtd" to false,
            XMLConstants.FEATURE_SECURE_PROCESSING to true,
        ).forEach { (feature, value) -> runCatching { setFeature(feature, value) } }
        runCatching { setXIncludeAware(false) }
        isExpandEntityReferences = false
    }
    val root = try {
        factory.newDocumentBuilder().parse(xml.inputStream()).documentElement
    } catch (error: Exception) {
        throw CadastreException(CadastreError.RESPONSE, error)
    }
    if (root.getElementsByTagNameNS("*", "ExceptionReport").length > 0) {
        throw CadastreException(CadastreError.NOT_FOUND)
    }
    val parcels = root.getElementsByTagNameNS(CP_NS, "CadastralParcel")
    var matching: Element? = null
    for (index in 0 until parcels.length) {
        val parcel = parcels.item(index) as? Element ?: continue
        if (parcel.firstText(CP_NS, "nationalCadastralReference")?.uppercase() == expectedReference) {
            matching = parcel
            break
        }
    }
    val parcel = matching ?: throw CadastreException(CadastreError.NOT_FOUND)
    val geometry = parcel.getElementsByTagNameNS(CP_NS, "geometry").item(0) as? Element
        ?: throw CadastreException(CadastreError.INVALID_GEOMETRY)
    val surfaces = geometry.getElementsByTagNameNS(GML_NS, "Surface")
    val polygons = mutableListOf<List<List<Pair<Double, Double>>>>()
    // Catastro currently uses MultiSurface/Surface/PolygonPatch. Polygon is valid GML too.
    val patches = geometry.getElementsByTagNameNS(GML_NS, "PolygonPatch")
    val shapes = if (patches.length > 0) patches else geometry.getElementsByTagNameNS(GML_NS, "Polygon")
    if (shapes.length == 0 || shapes.length > 64) throw CadastreException(CadastreError.INVALID_GEOMETRY)
    for (index in 0 until shapes.length) {
        val shape = shapes.item(index) as? Element ?: continue
        val surface = if (surfaces.length > 0) surfaces.item(0) as? Element else shape
        val srs = surface?.getAttribute("srsName").orEmpty()
        if (srs.isNotEmpty() && !srs.contains("4326")) throw CadastreException(CadastreError.INVALID_GEOMETRY)
        val rings = mutableListOf<List<Pair<Double, Double>>>()
        val exterior = shape.getElementsByTagNameNS(GML_NS, "exterior").item(0) as? Element
            ?: throw CadastreException(CadastreError.INVALID_GEOMETRY)
        rings += parseRing(exterior)
        val interiors = shape.getElementsByTagNameNS(GML_NS, "interior")
        if (interiors.length > 64) throw CadastreException(CadastreError.INVALID_GEOMETRY)
        for (ringIndex in 0 until interiors.length) {
            rings += parseRing(interiors.item(ringIndex) as Element)
        }
        polygons += rings
    }
    val area = parcel.firstText(CP_NS, "areaValue")?.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }
    return CadastralCandidate(expectedReference, area, polygons)
}

private fun declaresDoctype(xml: ByteArray): Boolean =
    String(xml, Charsets.UTF_8).contains("<!DOCTYPE", ignoreCase = true)

private fun parseRing(boundary: Element): List<Pair<Double, Double>> {
    val posList = boundary.getElementsByTagNameNS(GML_NS, "posList").item(0) as? Element
        ?: throw CadastreException(CadastreError.INVALID_GEOMETRY)
    if (posList.getAttribute("srsDimension").let { it.isNotEmpty() && it != "2" }) {
        throw CadastreException(CadastreError.INVALID_GEOMETRY)
    }
    val values = posList.textContent.trim().split(Regex("\\s+"))
    if (values.size < 8 || values.size % 2 != 0 || values.size > 20_000) {
        throw CadastreException(CadastreError.INVALID_GEOMETRY)
    }
    val points = values.chunked(2).map { pair ->
        val latitude = pair[0].toDoubleOrNull()
        val longitude = pair[1].toDoubleOrNull()
        if (latitude == null || longitude == null || !latitude.isFinite() || !longitude.isFinite() ||
            latitude !in SPAIN_LATITUDE || longitude !in SPAIN_LONGITUDE
        ) throw CadastreException(CadastreError.INVALID_GEOMETRY)
        longitude to latitude
    }
    if (points.first() != points.last() || points.toSet().size < 3) {
        throw CadastreException(CadastreError.INVALID_GEOMETRY)
    }
    return points
}

private fun Element.firstText(namespace: String, localName: String): String? =
    getElementsByTagNameNS(namespace, localName).item(0)?.textContent?.trim()?.takeIf(String::isNotEmpty)

// Whole Catastro coverage, Canary Islands, Ceuta and Melilla included. A point outside it
// means swapped axes or the wrong CRS, never a real Spanish parcel.
private val SPAIN_LATITUDE = 27.0..44.5
private val SPAIN_LONGITUDE = -18.5..4.6

private const val CP_NS = "http://inspire.ec.europa.eu/schemas/cp/4.0"
private const val GML_NS = "http://www.opengis.net/gml/3.2"
