package com.isivoltpro.maginaolivo.feature.catastro

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

typealias CadastralCandidate = com.isivoltpro.maginaolivo.domain.registry.ParcelCandidate

enum class CadastreError { INVALID_REFERENCE, NOT_FOUND, NETWORK, SERVICE, INVALID_GEOMETRY, RESPONSE }

class CadastreException(val kind: CadastreError, cause: Throwable? = null) : Exception(kind.name, cause)

interface CadastreClient {
    suspend fun findByReference(reference: String): CadastralCandidate
    suspend fun findNear(latitude: Double, longitude: Double): List<CadastralCandidate> = emptyList()
}

/** WFS GetParcel is kept behind this boundary; the rest of the app never sees GML. */
class OfficialCadastreClient : CadastreClient {
    override suspend fun findByReference(reference: String): CadastralCandidate {
        val normalized = reference.trim().uppercase()
        if (!REFERENCE.matches(normalized)) throw CadastreException(CadastreError.INVALID_REFERENCE)
        return parseCadastralGml(fetch("STOREDQUERIE_ID=GetParcel&refcat=$normalized"), normalized)
    }

    override suspend fun findNear(latitude: Double, longitude: Double): List<CadastralCandidate> {
        val bbox = try {
            boundedBbox(latitude, longitude, radiusMeters = 120.0)
        } catch (_: IllegalArgumentException) {
            throw CadastreException(CadastreError.NOT_FOUND)
        }
        return readGmlParcels(fetch("typeNames=CP:CadastralParcel&bbox=${bbox.parameter}&count=200"))
    }

    private suspend fun fetch(query: String): ByteArray = withContext(Dispatchers.IO) {
        val url = URL("$ENDPOINT?service=WFS&version=2.0.0&request=GetFeature&$query&srsname=EPSG::4326")
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
                body
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

internal data class CadastreBbox(
    val south: Double,
    val west: Double,
    val north: Double,
    val east: Double,
    val approximateAreaM2: Double,
) {
    val parameter: String = "$south,$west,$north,$east,urn:ogc:def:crs:EPSG::4326"
}

internal fun boundedBbox(latitude: Double, longitude: Double, radiusMeters: Double): CadastreBbox {
    require(latitude in 27.0..44.5 && longitude in -18.5..4.6)
    require(radiusMeters.isFinite() && radiusMeters in 1.0..500.0)
    val latitudeDelta = radiusMeters / 111_320.0
    val longitudeDelta = latitudeDelta / kotlin.math.cos(Math.toRadians(latitude))
    return CadastreBbox(
        south = latitude - latitudeDelta,
        west = longitude - longitudeDelta,
        north = latitude + latitudeDelta,
        east = longitude + longitudeDelta,
        approximateAreaM2 = 4 * radiusMeters * radiusMeters,
    )
}

internal fun parseCadastralGml(xml: ByteArray, expectedReference: String): CadastralCandidate =
    readGmlParcels(xml).firstOrNull { it.reference == expectedReference }
        ?: throw CadastreException(CadastreError.NOT_FOUND)
