package com.isivoltpro.maginaolivo.feature.catastro

import com.isivoltpro.maginaolivo.domain.registry.validateParcelGeometry
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.locationtech.proj4j.CRSFactory
import org.locationtech.proj4j.CoordinateTransformFactory
import org.locationtech.proj4j.ProjCoordinate
import org.w3c.dom.Element
import org.xml.sax.SAXException

/** INSPIRE CP v4 file/WFS adapter. All returned coordinates are longitude, latitude. */
fun readGmlParcels(xml: ByteArray): List<CadastralCandidate> {
    if (xml.isEmpty() || xml.size > 2_000_000 || xml.any { it == 0.toByte() } ||
        xml.toString(Charsets.ISO_8859_1).contains("<!DOCTYPE", true)
    ) throw CadastreException(CadastreError.RESPONSE)
    val root = try {
        DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isExpandEntityReferences = false
            runCatching { setXIncludeAware(false) }
            listOf(
                XMLConstants.FEATURE_SECURE_PROCESSING to true,
                "http://apache.org/xml/features/disallow-doctype-decl" to true,
                "http://xml.org/sax/features/external-general-entities" to false,
                "http://xml.org/sax/features/external-parameter-entities" to false,
                "http://apache.org/xml/features/nonvalidating/load-external-dtd" to false,
            ).forEach { (name, enabled) -> runCatching { setFeature(name, enabled) } }
        }.newDocumentBuilder().apply {
            setEntityResolver { _, _ -> throw SAXException("External entities refused") }
        }.parse(xml.inputStream()).documentElement
    } catch (error: Exception) { throw CadastreException(CadastreError.RESPONSE, error) }
    if (root.localName == "ExceptionReport" || root.getElementsByTagNameNS("*", "Exception").length > 0) {
        throw CadastreException(CadastreError.NOT_FOUND)
    }
    val nodes = root.getElementsByTagNameNS(CP, "CadastralParcel")
    val parcels = if (root.localName == "CadastralParcel" && root.namespaceURI == CP) listOf(root)
        else (0 until nodes.length).map { nodes.item(it) as Element }
    if (parcels.size > 200) throw CadastreException(CadastreError.RESPONSE)
    return parcels.map { parcel ->
        try {
            val reference = parcel.text(CP, "nationalCadastralReference")?.uppercase()
                ?: throw IllegalArgumentException("Missing identity")
            require(reference.matches(Regex("[A-Z0-9]{14}")))
            val geometry = parcel.getElementsByTagNameNS(CP, "geometry").item(0) as? Element
                ?: throw IllegalArgumentException("Missing geometry")
            val patches = geometry.getElementsByTagNameNS(GML, "PolygonPatch")
            val shapes = if (patches.length > 0) patches else geometry.getElementsByTagNameNS(GML, "Polygon")
            require(shapes.length in 1..64)
            val polygons = (0 until shapes.length).map { i ->
                val shape = shapes.item(i) as Element
                val exterior = shape.getElementsByTagNameNS(GML, "exterior")
                require(exterior.length == 1)
                val holes = shape.getElementsByTagNameNS(GML, "interior")
                require(holes.length <= 64)
                listOf(readRing(exterior.item(0) as Element)) +
                    (0 until holes.length).map { readRing(holes.item(it) as Element) }
            }
            validateParcelGeometry(polygons)
            val area = parcel.text(CP, "areaValue")?.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }
            CadastralCandidate(reference = reference, areaM2 = area, polygons = polygons)
        } catch (error: CadastreException) { throw error }
        catch (error: Exception) { throw CadastreException(CadastreError.INVALID_GEOMETRY, error) }
    }.also { if (it.map { p -> p.reference }.distinct().size != it.size) throw CadastreException(CadastreError.RESPONSE) }
}

private fun readRing(boundary: Element): List<Pair<Double, Double>> {
    val lists = boundary.getElementsByTagNameNS(GML, "posList")
    require(lists.length == 1)
    val positions = lists.item(0) as Element
    var node: Element? = positions
    var crs: String? = null
    while (node != null) {
        val dimension = node.getAttribute("srsDimension")
        require(dimension.isEmpty() || dimension == "2")
        node.getAttribute("srsName").takeIf { it.isNotEmpty() }?.let {
            require(crs == null || crs == it) { "Conflicting CRS" }
            crs = it
        }
        node = node.parentNode as? Element
    }
    val code = crs?.let { Regex("(?:EPSG::?|urn:ogc:def:crs:EPSG::|http://www.opengis.net/def/crs/EPSG/0/)([0-9]+)").matchEntire(it)?.groupValues?.get(1)?.toInt() }
        ?: throw IllegalArgumentException("Missing or unknown CRS")
    require(code in setOf(4326, 4258, 3857, 25829, 25830, 25831, 32628, 32629, 32630, 32631, 4083))
    val values = positions.textContent.trim().split(Regex("\\s+"))
    require(values.size in 8..40_000 && values.size % 2 == 0)
    val transform = if (code == 4326 || code == 4258) null else CRSFactory().let {
        CoordinateTransformFactory().createTransform(it.createFromName("EPSG:$code"), it.createFromName("EPSG:4326"))
    }
    return values.chunked(2).map {
        val a = it[0].toDouble(); val b = it[1].toDouble()
        require(a.isFinite() && b.isFinite())
        val point = if (transform == null) b to a else {
            val output = ProjCoordinate()
            transform.transform(ProjCoordinate(a, b), output)
            output.x to output.y
        }
        require(point.first in -18.5..4.6 && point.second in 27.0..44.5)
        point
    }
}

private fun Element.text(ns: String, name: String): String? =
    getElementsByTagNameNS(ns, name).item(0)?.textContent?.trim()?.takeIf { it.isNotEmpty() }

private const val CP = "http://inspire.ec.europa.eu/schemas/cp/4.0"
private const val GML = "http://www.opengis.net/gml/3.2"
