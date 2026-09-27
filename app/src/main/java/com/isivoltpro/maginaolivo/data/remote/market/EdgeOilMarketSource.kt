package com.isivoltpro.maginaolivo.data.remote.market

import com.isivoltpro.maginaolivo.data.remote.weather.EdgeFunctionHttp
import com.isivoltpro.maginaolivo.domain.market.OilCategory
import com.isivoltpro.maginaolivo.domain.market.OilMarketReading
import com.isivoltpro.maginaolivo.domain.market.OilMarketSeries
import com.isivoltpro.maginaolivo.domain.market.OilObservation
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Phase 20D — the official weekly olive-oil prices, asked of the project's `oil-market` Edge
 * Function over the same plain HTTPS POST and public anon key as the weather (CR-006). The
 * function reads the public sources; the app never parses HTML, PDF or XLSX.
 */
class EdgeOilMarketSource(
    private val functionsUrl: String,
    private val anonKey: String,
    private val timeoutMillis: Int = 10_000,
) : OilMarketJsonSource {
    override suspend fun andaluciaJson(weeks: Int): String = withContext(Dispatchers.IO) {
        val request = JSONObject().put("operation", "series").put("geography", "andalucia").put("weeks", weeks)
        EdgeFunctionHttp.post(functionsUrl, "oil-market", anonKey, request.toString(), timeoutMillis)
    }

    companion object {
        const val JUNTA = "junta-andalucia-observatorio"
    }
}

/** The function's answer as text: the cache keeps it verbatim and re-reads it with [EdgeOilMarketResponse]. */
interface OilMarketJsonSource {
    /** The latest [weeks] of the official Andalucía series; throws on any failure. */
    suspend fun andaluciaJson(weeks: Int): String
}

/**
 * The normalised response contract (docs/07-plans/PHASE20D-OIL-MARKET-CONTRACT.md). Anything
 * incomplete, an unknown category or unit, a €/kg value that does not match its original, or a
 * series from another source makes the whole answer a failed fetch (the cache stays). A price the
 * source did not publish (null / "--") is simply absent — never zero, never filled in.
 */
object EdgeOilMarketResponse {
    fun parse(json: String, expectedSourceId: String): OilMarketReading {
        val body = JSONObject(json)
        val fetchedAt = Instant.parse(body.getString("fetchedAt"))
        val grouped = linkedMapOf<Pair<String, String>, MutableList<OilObservation>>()
        val meta = mutableMapOf<Pair<String, String>, Triple<String, String, String?>>()
        val list = body.getJSONArray("series")
        for (index in 0 until list.length()) {
            val entry = list.getJSONObject(index)
            val sourceId = entry.getString("sourceId")
            require(sourceId == expectedSourceId) { "series from $sourceId where $expectedSourceId was asked" }
            val (geographyCode, geographyName) = geography(entry)
            val category = OilCategory.entries.firstOrNull { it.name == entry.getString("category") }
                ?: throw IllegalArgumentException("unknown category ${entry.getString("category")}")
            val key = sourceId to geographyCode
            meta.getOrPut(key) {
                Triple(entry.optString("sourceName").ifBlank { sourceId }, geographyName, entry.optString("marketStage").ifBlank { null })
            }
            val observations = grouped.getOrPut(key) { mutableListOf() }
            observations += observationsOf(category, entry.getJSONArray("observations"))
        }
        return OilMarketReading(
            series = grouped.map { (key, observations) ->
                val (sourceName, geographyName, stage) = meta.getValue(key)
                OilMarketSeries(key.first, sourceName, key.second, geographyName, stage, observations.sortedBy { it.periodStart })
            },
            fetchedAt = fetchedAt,
        )
    }

    private fun geography(entry: JSONObject): Pair<String, String> {
        val value = entry.get("geography")
        return if (value is JSONObject) {
            value.getString("code") to value.optString("name").ifBlank { value.getString("code") }
        } else {
            val code = value.toString()
            code to (NAMES[code] ?: code)
        }
    }

    private fun observationsOf(category: OilCategory, list: JSONArray): List<OilObservation> =
        (0 until list.length()).mapNotNull { index ->
            val row = list.getJSONObject(index)
            val start = LocalDate.parse(row.getString("periodStart"))
            val end = LocalDate.parse(row.getString("periodEnd"))
            require(!end.isBefore(start)) { "period ends before it starts" }
            val original = decimal(row, "originalValue") ?: return@mapNotNull null // not published that week
            val unit = row.getString("originalUnit")
            val perKg = when (unit) {
                "EUR_PER_KG" -> original
                "EUR_PER_100KG" -> original.movePointLeft(2)
                else -> throw IllegalArgumentException("unknown unit $unit")
            }
            val stated = decimal(row, "valueEurPerKg") ?: throw IllegalArgumentException("€/kg missing")
            require(stated.compareTo(perKg) == 0) { "€/kg $stated does not match $original $unit" }
            OilObservation(category, start, end, stated, original, unit)
        }

    /** Exact decimal from the JSON text (never through a double); null for null or "--". */
    private fun decimal(row: JSONObject, name: String): BigDecimal? {
        if (!row.has(name) || row.isNull(name)) return null
        val text = row.get(name).toString().trim()
        if (text.isEmpty() || text == "--") return null
        return BigDecimal(text.replace(',', '.'))
    }

    private val NAMES = mapOf("ES-AN" to "Andalucía", "ES" to "España")
}
