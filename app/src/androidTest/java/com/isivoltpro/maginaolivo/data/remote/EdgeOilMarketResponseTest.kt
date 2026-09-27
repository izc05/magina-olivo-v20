package com.isivoltpro.maginaolivo.data.remote

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.isivoltpro.maginaolivo.data.remote.market.EdgeOilMarketResponse
import com.isivoltpro.maginaolivo.domain.market.OilCategory
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Phase 20D — the app reads the `oil-market` contract strictly. Values are the owner's verified
 * Junta de Andalucía weeks 37–38 and MAPA week 38 (docs/06-testing/evidence/phase20d/).
 */
@RunWith(AndroidJUnit4::class)
class EdgeOilMarketResponseTest {
    private val junta = "junta-andalucia-observatorio"

    private fun series(category: String, vararg observations: String, sourceId: String = junta) = """
        {"sourceId":"$sourceId","sourceName":"Observatorio de Precios y Mercados - Junta de Andalucía",
         "geography":{"level":"AUTONOMOUS_COMMUNITY","code":"ES-AN","name":"Andalucía"},
         "marketStage":"ALMAZARA_OR_BODEGA","category":"$category","observations":[${observations.joinToString(",")}]}
    """.trimIndent()

    private fun obs(start: String, end: String, value: String?, unit: String = "EUR_PER_KG", perKg: String? = value) =
        """{"periodStart":"$start","periodEnd":"$end","valueEurPerKg":${perKg ?: "null"},"originalValue":${value ?: "null"},"originalUnit":"$unit"}"""

    private fun body(vararg series: String) =
        """{"provider":"oil-market","fetchedAt":"2026-09-27T17:00:00Z","series":[${series.joinToString(",")}]}"""

    @Test
    fun theVerifiedJuntaWeeksReadAsOneSeriesWithThreeCategories() {
        val reading = EdgeOilMarketResponse.parse(
            body(
                series("AOVE", obs("2026-09-07", "2026-09-13", "3.67"), obs("2026-09-14", "2026-09-20", "3.46")),
                series("AOV", obs("2026-09-07", "2026-09-13", "3.30"), obs("2026-09-14", "2026-09-20", "3.31")),
                series("AOL", obs("2026-09-07", "2026-09-13", "3.19"), obs("2026-09-14", "2026-09-20", "3.15")),
            ),
            junta,
        )
        val andalucia = reading.series.single()
        assertEquals("ES-AN", andalucia.geographyCode)
        assertEquals("Andalucía", andalucia.geographyName)
        assertEquals(6, andalucia.observations.size)
        val aove = andalucia.observations.filter { it.category == OilCategory.AOVE }
        assertEquals(0, BigDecimal("3.46").compareTo(aove.last().valueEurPerKg))
    }

    @Test
    fun eurosPer100KgBecomeEurosPerKgExactlyAndKeepTheirOriginal() {
        // MAPA week 38, España: 346.89 €/100 kg = 3.4689 €/kg.
        val reading = EdgeOilMarketResponse.parse(
            body(series("AOVE", obs("2026-09-14", "2026-09-20", "346.89", "EUR_PER_100KG", "3.4689"), sourceId = "mapa")),
            "mapa",
        )
        val value = reading.series.single().observations.single()
        assertEquals(0, BigDecimal("3.4689").compareTo(value.valueEurPerKg))
        assertEquals(0, BigDecimal("346.89").compareTo(value.originalValue))
        assertEquals("EUR_PER_100KG", value.originalUnit)
    }

    @Test
    fun anUnpublishedWeekIsAbsentNeverZero() {
        val reading = EdgeOilMarketResponse.parse(
            body(series("AOL", obs("2026-09-07", "2026-09-13", null), """{"periodStart":"2026-09-14","periodEnd":"2026-09-20","valueEurPerKg":"--","originalValue":"--","originalUnit":"EUR_PER_KG"}""")),
            junta,
        )
        assertTrue(reading.series.single().observations.isEmpty())
    }

    @Test
    fun anotherSourceAWrongConversionOrAnUnknownCategoryIsAFailedAnswer() {
        assertThrows(IllegalArgumentException::class.java) {
            EdgeOilMarketResponse.parse(body(series("AOVE", obs("2026-09-14", "2026-09-20", "3.46"), sourceId = "poolred")), junta)
        }
        assertThrows(IllegalArgumentException::class.java) {
            EdgeOilMarketResponse.parse(body(series("AOVE", obs("2026-09-14", "2026-09-20", "346.89", "EUR_PER_100KG", "346.89"))), junta)
        }
        assertThrows(IllegalArgumentException::class.java) {
            EdgeOilMarketResponse.parse(body(series("ORUJO", obs("2026-09-14", "2026-09-20", "1.20"))), junta)
        }
        assertThrows(IllegalArgumentException::class.java) {
            EdgeOilMarketResponse.parse(body(series("AOVE", obs("2026-09-14", "2026-09-20", "3.46", "USD_PER_LB"))), junta)
        }
    }
}
