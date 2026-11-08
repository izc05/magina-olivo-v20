package com.isivoltpro.maginaolivo.feature.sigpac

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** SIGPAC identity of a parcel. Agregado and zona are part of the official key. */
data class SigpacParcelCode(
    val provincia: Int,
    val municipio: Int,
    val agregado: Int,
    val zona: Int,
    val poligono: Int,
    val parcela: Int,
)

/**
 * One official recinto as FEGA publishes it. Null means the service gave no value, which is not
 * the same as zero. [slope] and [irrigationCoefficient] are kept raw: FEGA states no unit for them.
 */
data class SigpacRecinto(
    val recinto: Int,
    val areaHa: Double?,
    val slope: Double?,
    val irrigationCoefficient: Double?,
    val admissibilityPercent: Double?,
    val incidents: String?,
    val useCode: String?,
    val region: String?,
    /** Longitude, latitude; null when the service sent none or in an unknown coordinate system. */
    val polygons: List<List<List<Pair<Double, Double>>>>?,
    /** Raw FEGA value; its documentation states no unit. */
    val altitude: Double? = null,
)

enum class SigpacError { NETWORK, SERVICE, RATE_LIMITED, RESPONSE }

class SigpacException(val kind: SigpacError, cause: Throwable? = null) : Exception(cause)

/** Read-only access to the FEGA query service; the rest of the app never sees its format. */
interface SigpacClient {
    suspend fun recintos(code: SigpacParcelCode): List<SigpacRecinto>
}

private val RURAL_REFERENCE = Regex("(\\d{2})(\\d{3})[A-Z](\\d{3})(\\d{5})")

/**
 * Only rural 14-character references carry polygon and parcel. Agregado and zona are not part of
 * the reference and are assumed 0; the contour check is what confirms the recintos really match.
 */
fun sigpacCodeFromCadastralReference(reference: String?): SigpacParcelCode? {
    val match = RURAL_REFERENCE.matchEntire(reference?.trim()?.uppercase().orEmpty()) ?: return null
    val (provincia, municipio, poligono, parcela) = match.destructured.toList().map(String::toInt)
    if (provincia !in 1..52 || municipio < 1 || poligono < 1 || parcela < 1) return null
    return SigpacParcelCode(provincia, municipio, 0, 0, poligono, parcela)
}

private const val MAX_RECINTOS = 500

/** Strict: a response about another parcel, or with a repeated recinto, is rejected whole. */
fun parseSigpacRecintos(body: String, code: SigpacParcelCode): List<SigpacRecinto> {
    try {
        val array = JsonParser.parseString(body).asJsonArray
        if (array.size() > MAX_RECINTOS) throw SigpacException(SigpacError.RESPONSE)
        val recintos = array.map { element ->
            val item = element.asJsonObject
            val identity = SigpacParcelCode(item.requiredInt("provincia"), item.requiredInt("municipio"), item.requiredInt("agregado"),
                item.requiredInt("zona"), item.requiredInt("poligono"), item.requiredInt("parcela"))
            if (identity != code) throw SigpacException(SigpacError.RESPONSE)
            val number = item.requiredInt("recinto")
            if (number < 1) throw SigpacException(SigpacError.RESPONSE)
            val area = item.number("superficie")
            if (area != null && area < 0) throw SigpacException(SigpacError.RESPONSE)
            SigpacRecinto(
                recinto = number,
                areaHa = area,
                slope = item.number("pendiente_media"),
                irrigationCoefficient = item.number("coef_regadio"),
                admissibilityPercent = item.number("admisibilidad"),
                incidents = item.text("incidencias"),
                useCode = item.text("uso_sigpac"),
                region = item.text("region"),
                polygons = item.text("wkt")?.takeIf { item.number("srid")?.toInt() in setOf(4258, 4326) }?.let(::parseWktPolygons),
                altitude = item.number("altitud"),
            )
        }
        if (recintos.map { it.recinto }.distinct().size != recintos.size) throw SigpacException(SigpacError.RESPONSE)
        return recintos.sortedBy { it.recinto }
    } catch (known: SigpacException) {
        throw known
    } catch (error: RuntimeException) {
        throw SigpacException(SigpacError.RESPONSE, error)
    }
}

private fun JsonObject.value(name: String): JsonElement? = get(name)?.takeUnless { it.isJsonNull }

/** The documentation shows some codes both as numbers and as quoted numbers. */
private fun JsonObject.number(name: String): Double? = value(name)?.asJsonPrimitive?.let { primitive ->
    (if (primitive.isNumber) primitive.asDouble else primitive.asString.toDouble()).also { require(it.isFinite()) }
}

private fun JsonObject.requiredInt(name: String): Int {
    val number = requireNotNull(number(name))
    require(number % 1.0 == 0.0)
    return number.toInt()
}

private fun JsonObject.text(name: String): String? = value(name)?.asString?.trim()?.takeIf { it.isNotEmpty() }

private val WKT_NUMBER_PAIR = Regex("(-?\\d+(?:\\.\\d+)?)\\s+(-?\\d+(?:\\.\\d+)?)")

/** POLYGON and MULTIPOLYGON only; coordinates stay longitude, latitude as FEGA sends them. */
internal fun parseWktPolygons(wkt: String): List<List<List<Pair<Double, Double>>>>? {
    val text = wkt.trim().uppercase()
    fun rings(polygon: String) = Regex("\\(([^()]+)\\)").findAll(polygon).map { ring ->
        WKT_NUMBER_PAIR.findAll(ring.groupValues[1]).map { it.groupValues[1].toDouble() to it.groupValues[2].toDouble() }.toList()
    }.toList()
    val polygons = when {
        text.startsWith("POLYGON") -> listOf(rings(text))
        text.startsWith("MULTIPOLYGON") -> Regex("\\(\\s*\\([^()]+\\)(?:\\s*,\\s*\\([^()]+\\))*\\s*\\)").findAll(text.removePrefix("MULTIPOLYGON"))
            .map { rings(it.value) }.toList()
        else -> return null
    }
    return polygons.takeIf { all -> all.isNotEmpty() && all.all { rings -> rings.isNotEmpty() && rings.all { it.size >= 4 } } }
}

class OfficialSigpacClient(private val fetch: suspend (String) -> ByteArray = ::download) : SigpacClient {
    override suspend fun recintos(code: SigpacParcelCode): List<SigpacRecinto> {
        val path = with(code) { "$provincia/$municipio/$agregado/$zona/$poligono/$parcela" }
        return parseSigpacRecintos(decodeSigpacBody(fetch("$ENDPOINT/$path.json")), code)
    }

    private companion object {
        const val ENDPOINT = "https://sigpac-hubcloud.es/servicioconsultassigpac/query/recinfoparc"
    }
}

private const val MAX_BYTES = 6 * 1024 * 1024

/** FEGA states every answer is gzip-compressed, even when the client does not ask for it. */
internal fun decodeSigpacBody(bytes: ByteArray): String = try {
    val plain = if (bytes.size > 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()) {
        GZIPInputStream(ByteArrayInputStream(bytes)).use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (output.size() + count > MAX_BYTES) throw SigpacException(SigpacError.RESPONSE)
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
    } else bytes
    String(plain, Charsets.UTF_8)
} catch (error: java.io.IOException) {
    throw SigpacException(SigpacError.RESPONSE, error)
}

private suspend fun download(url: String): ByteArray = withContext(Dispatchers.IO) {
    try {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 15_000
        connection.setRequestProperty("Accept", "application/json")
        try {
            when (val status = connection.responseCode) {
                in 200..299 -> Unit
                429 -> throw SigpacException(SigpacError.RATE_LIMITED)
                else -> throw SigpacException(SigpacError.SERVICE, IllegalStateException("HTTP $status"))
            }
            connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (output.size() + count > MAX_BYTES) throw SigpacException(SigpacError.RESPONSE)
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
        } finally {
            connection.disconnect()
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (known: SigpacException) {
        throw known
    } catch (error: java.io.IOException) {
        throw SigpacException(SigpacError.NETWORK, error)
    }
}
